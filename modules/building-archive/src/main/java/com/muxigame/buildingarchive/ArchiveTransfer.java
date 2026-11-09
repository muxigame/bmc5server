package com.muxigame.buildingarchive;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;

/** Bounded, acknowledged transfers. The client never supplies a server path. */
public final class ArchiveTransfer {
    public static final int CHUNK=24000, MAX_FILE=8*1024*1024;
    private static final Map<UUID,Pending> ACTIVE=new HashMap<>();
    static final class Pending {
        final UUID id; final String name; final byte[] bytes; final boolean upload;
        int offset; long expires=System.currentTimeMillis()+60000;
        Pending(UUID id,String name,int total,boolean upload){this.id=id;this.name=name;this.bytes=new byte[total];this.upload=upload;}
    }
    public static boolean allowed(ServerPlayer p){return p.hasPermissions(2)||(p.server.isSingleplayerOwner(p.getGameProfile())&&p.isCreative());}
    static Path root(ServerPlayer p){return p.server.getWorldPath(LevelResource.ROOT).resolve("building_archive");}
    public static void clear(UUID player){ACTIVE.remove(player);}
    public static void clearAll(){ACTIVE.clear();}
    public static void expire(){ACTIVE.values().removeIf(v->v.expires<System.currentTimeMillis());}
    static CompoundTag decode(byte[] bytes) throws IOException {
        if(bytes.length==0||bytes.length>MAX_FILE)throw new IllegalArgumentException("每个建筑文件最多8 MB。");
        var data=NbtIo.readCompressed(new ByteArrayInputStream(bytes),NbtAccounter.create(ArchiveData.MAX_BYTES));
        ArchiveData.validate(data);return data;
    }
    static byte[] encode(CompoundTag data) throws IOException {
        ArchiveData.validate(data);var out=new ByteArrayOutputStream();NbtIo.writeCompressed(data,out);
        if(out.size()>MAX_FILE)throw new IllegalArgumentException("建筑超过8 MB，暂不能在线传输。");return out.toByteArray();
    }
    static void save(Path folder,String name,byte[] bytes) throws IOException {
        var data=decode(bytes);Path target=ArchiveData.file(folder,name);
        // Never overwrite either a building or its handwritten analysis.
        Path report=folder.resolve(name+".analysis.json");
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)||Files.exists(report,LinkOption.NOFOLLOW_LINKS))throw new IllegalArgumentException("同名建筑已存在，请修改名称后重试。");
        String json=new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(ArchiveData.analysis(data));
        ArchiveData.writeNew(target,data);
        try{Files.writeString(report,json,StandardOpenOption.CREATE_NEW);}catch(IOException e){Files.deleteIfExists(target);throw e;}
    }
    public static CompoundTag listing(ServerPlayer p,String message,int page){
        CompoundTag t=new CompoundTag();t.putBoolean("Transfer",true);t.putBoolean("Allowed",allowed(p));t.putString("Message",message);
        ListTag names=new ListTag();int pages=1;
        if(allowed(p))try{Files.createDirectories(root(p));try(var files=Files.list(root(p))){
            var list=files.filter(f->Files.isRegularFile(f,LinkOption.NOFOLLOW_LINKS)&&f.getFileName().toString().endsWith(".nbt")).map(f->f.getFileName().toString().replaceFirst("\\.nbt$","")).filter(n->n.matches("[\\p{L}\\p{N}_-]{1,64}")).sorted().toList();
            pages=Math.max(1,(list.size()+4)/5);page=Math.max(0,Math.min(page,pages-1));list.stream().skip(page*5L).limit(5).forEach(n->names.add(StringTag.valueOf(n)));
        }}catch(IOException e){t.putString("Message","读取服务器档案失败。");}
        else t.putString("Message","联机导入导出需要二级管理员权限；单人创造模式可直接使用。");
        t.put("Files",names);t.putInt("Page",page);t.putInt("Pages",pages);return t;
    }
    public static void panel(ServerPlayer p){PacketDistributor.sendToPlayer(p,new ArchiveNetwork.Snapshot(listing(p,"选择本地文件上传，或点右侧建筑下载。",0)));}
    private static ArchiveNetwork.Transfer packet(String op,Pending s,int offset,byte[] bytes){return new ArchiveNetwork.Transfer(op,s.name,s.id,offset,s.bytes.length,bytes);}
    static void append(Pending s,ArchiveNetwork.Transfer p){
        if(!s.upload||!s.id.equals(p.id())||!s.name.equals(p.name())||s.bytes.length!=p.total()||s.offset!=p.offset()||p.bytes().length==0||p.bytes().length>CHUNK||p.bytes().length>s.bytes.length-s.offset)throw new IllegalArgumentException("传输顺序或大小不正确，请重新上传。");
        System.arraycopy(p.bytes(),0,s.bytes,s.offset,p.bytes().length);s.offset+=p.bytes().length;s.expires=System.currentTimeMillis()+60000;
    }
    public static void handle(ServerPlayer player,ArchiveNetwork.Transfer p){handle(player,p,reply->PacketDistributor.sendToPlayer(player,reply));}
    static void handle(ServerPlayer player,ArchiveNetwork.Transfer p,java.util.function.Consumer<ArchiveNetwork.Transfer> reply){
        try{
            if(!allowed(player))throw new IllegalArgumentException("只有管理员可以传输服务器建筑文件。");
            expire();UUID owner=player.getUUID();
            if(p.op().equals("list")){PacketDistributor.sendToPlayer(player,new ArchiveNetwork.Snapshot(listing(player,"",p.offset())));return;}
            if(p.op().equals("cancel")){var s=ACTIVE.get(owner);if(s!=null&&s.id.equals(p.id()))ACTIVE.remove(owner);return;}
            if(p.op().equals("upload")){
                Pending s=ACTIVE.get(owner);
                if(p.offset()==0){
                    if(s!=null)throw new IllegalArgumentException("已有传输正在进行，请等待或取消。");
                    ArchiveData.file(root(player),p.name());
                    if(p.total()<1||p.total()>MAX_FILE||ACTIVE.size()>=16)throw new IllegalArgumentException("文件过大或服务器传输繁忙，请稍后再试。");
                    s=new Pending(p.id(),p.name(),p.total(),true);ACTIVE.put(owner,s);
                }
                if(s==null)throw new IllegalArgumentException("上传已超时，请重新选择文件。");append(s,p);
                if(s.offset==s.bytes.length){save(root(player),s.name,s.bytes);ACTIVE.remove(owner);reply.accept(packet("done",s,s.offset,new byte[0]));}
                else reply.accept(packet("ack",s,s.offset,new byte[0]));
                return;
            }
            if(p.op().equals("download")){
                if(ACTIVE.containsKey(owner)||ACTIVE.size()>=16)throw new IllegalArgumentException("已有传输正在进行或服务器繁忙。");
                byte[] bytes=encode(ArchiveData.read(ArchiveData.file(root(player),p.name())));var s=new Pending(p.id(),p.name(),bytes.length,false);System.arraycopy(bytes,0,s.bytes,0,bytes.length);ACTIVE.put(owner,s);sendChunk(player,s,reply);return;
            }
            if(p.op().equals("next")){
                var s=ACTIVE.get(owner);if(s==null||s.upload||!s.id.equals(p.id())||s.offset!=p.offset())throw new IllegalArgumentException("下载已过期，请重新下载。");sendChunk(player,s,reply);return;
            }
            throw new IllegalArgumentException("未知传输操作。");
        }catch(Exception e){var s=ACTIVE.get(player.getUUID());if(s!=null&&s.id.equals(p.id()))ACTIVE.remove(player.getUUID());String msg=e instanceof IllegalArgumentException?e.getMessage():"文件读取或写入失败，请检查格式和目录权限。";reply.accept(new ArchiveNetwork.Transfer("error",msg,p.id(),0,0,new byte[0]));}
    }
    private static void sendChunk(ServerPlayer player,Pending s,java.util.function.Consumer<ArchiveNetwork.Transfer> reply){int start=s.offset;s.offset=Math.min(start+CHUNK,s.bytes.length);s.expires=System.currentTimeMillis()+60000;reply.accept(packet("data",s,start,Arrays.copyOfRange(s.bytes,start,s.offset)));if(s.offset==s.bytes.length)ACTIVE.remove(player.getUUID());}
}
