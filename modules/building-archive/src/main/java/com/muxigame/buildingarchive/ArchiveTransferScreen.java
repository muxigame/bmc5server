package com.muxigame.buildingarchive;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** File browsing stays inside Minecraft: no external window, focus theft or shell needed. */
public final class ArchiveTransferScreen extends Screen {
    private CompoundTag data;private boolean local=true,busy;private Path folder=Path.of(System.getProperty("user.home"),"Downloads"),selected;
    private String message="选择文件后点击上传；ZIP内的建筑将逐个导入。",newName="";private int page,wait,received;
    private EditBox pathBox,nameBox;private UUID id;private byte[] payload;private String transferName;
    private final ArrayDeque<Entry> queue=new ArrayDeque<>();private byte[] incoming;private int completed;
    private record Entry(String name,byte[] bytes){}
    public ArchiveTransferScreen(CompoundTag data){super(Component.literal("建筑导入导出器"));this.data=data;if(!data.getBoolean("Allowed"))message=data.getString("Message");}
    private int left(){return (width-Math.min(500,width-20))/2;}private int span(){return Math.min(500,width-20);}
    private Button button(String text,int x,int y,int w,Runnable run){var b=addRenderableWidget(Button.builder(Component.literal(font.width(text)>w-8?font.plainSubstrByWidth(text,w-18)+"…":text),v->run.run()).bounds(x,y,w,18).build());b.setTooltip(Tooltip.create(Component.literal(text)));b.active=!busy;return b;}
    private void send(String op,String name,int offset,int total,byte[] bytes){PacketDistributor.sendToServer(new ArchiveNetwork.Transfer(op,name,id==null?new UUID(0,0):id,offset,total,bytes));}
    private void listing(int page){send("list","",page,0,new byte[0]);}
    public void update(CompoundTag t){data=t;if(!t.getString("Message").isEmpty())message=t.getString("Message");init();}
    @Override protected void init(){
        clearWidgets();int x=left(),w=span();
        button("上传本地建筑",x,28,w/2-2,()->{local=true;init();});button("下载已记录建筑",x+w/2+2,28,w/2-2,()->{local=false;listing(0);init();});
        if(!data.getBoolean("Allowed"))return;
        if(!local){
            var list=data.getList("Files",Tag.TAG_STRING);for(int i=0;i<list.size();i++){String name=list.getString(i);button("下载："+name,x,60+i*20,w,()->download(name));}
            button("上一页",x,166,70,()->listing(data.getInt("Page")-1));button("下一页",x+74,166,70,()->listing(data.getInt("Page")+1));button("刷新",x+148,166,60,()->listing(data.getInt("Page")));button("复制导出目录",x+212,166,Math.min(120,w-212),()->{minecraft.keyboardHandler.setClipboard(exportFolder().toAbsolutePath().toString());message="导出目录已复制，可粘贴到文件资源管理器地址栏。";});
            return;
        }
        pathBox=addRenderableWidget(new EditBox(font,x,52,w-62,18,Component.literal("本地文件或文件夹路径")));pathBox.setMaxLength(2048);pathBox.setValue(folder.toString());pathBox.setEditable(!busy);
        button("打开",x+w-58,52,58,()->navigate(pathBox.getValue()));
        button("下载目录",x,76,80,()->navigate(Path.of(System.getProperty("user.home"),"Downloads").toString()));
        button("桌面",x+84,76,60,()->navigate(Path.of(System.getProperty("user.home"),"Desktop").toString()));
        button("上级目录",x+148,76,80,()->{if(folder.getParent()!=null)navigate(folder.getParent().toString());});
        int rows=Math.max(1,Math.min(5,(height-214)/20));List<Path> files=List.of();
        try(var stream=Files.list(folder)){files=stream.filter(p->Files.isDirectory(p)||supported(p)).sorted(Comparator.<Path,Boolean>comparing(p->!Files.isDirectory(p)).thenComparing(p->p.getFileName().toString())).toList();}
        catch(Exception e){message="无法打开此目录，可在上方粘贴文件的完整路径。";}
        int maxPage=Math.max(0,(files.size()-1)/rows);page=Math.max(0,Math.min(page,maxPage));
        for(int i=0;i<rows&&page*rows+i<files.size();i++){Path p=files.get(page*rows+i);button((p.equals(selected)?"✓ ":"")+(Files.isDirectory(p)?"[文件夹] ":"")+p.getFileName(),x,100+i*20,w,()->{if(Files.isDirectory(p))navigate(p.toString());else{selected=p;newName=base(p.getFileName().toString());init();}});}
        int y=100+rows*20;
        button("上一页",x,y,70,()->{page--;init();});button("下一页",x+74,y,70,()->{page++;init();});
        nameBox=addRenderableWidget(new EditBox(font,x+68,y+24,w-68,18,Component.literal("导入名称")));nameBox.setMaxLength(64);nameBox.setValue(newName);nameBox.setEditable(!busy);nameBox.setResponder(v->newName=v);
        var upload=button("上传选中文件到当前世界",x,y+48,w,()->upload());upload.active=!busy&&selected!=null;
    }
    private static boolean supported(Path p){String n=p.getFileName().toString().toLowerCase(Locale.ROOT);return Files.isRegularFile(p)&&(n.endsWith(".nbt")||n.endsWith(".zip"));}
    private static String base(String s){return s.replaceFirst("(?i)\\.(nbt|zip)$","");}
    private void navigate(String text){try{Path p=Path.of(text.strip().replaceAll("^\"|\"$","")).toAbsolutePath().normalize();if(Files.isDirectory(p)){folder=p;selected=null;newName="";}else if(supported(p)){folder=p.getParent();selected=p;newName=base(p.getFileName().toString());}else throw new IOException();page=0;init();}catch(Exception e){message="找不到该文件或目录，仅支持NBT和建筑作品ZIP。";}}
    private void upload(){
        try{queue.clear();completed=0;for(var e:ArchiveTransferFiles.read(selected,newName))queue.add(new Entry(e.name(),e.bytes()));busy=true;nextUpload();}
        catch(Exception e){queue.clear();busy=false;message="未上传："+e.getMessage();}init();
    }
    private void nextUpload(){var e=queue.remove();id=UUID.randomUUID();transferName=e.name;payload=e.bytes;wait=1200;sendUpload(0);}
    private void sendUpload(int offset){int end=Math.min(offset+ArchiveTransfer.CHUNK,payload.length);send("upload",transferName,offset,payload.length,Arrays.copyOfRange(payload,offset,end));message="正在上传："+transferName+" "+(end*100/payload.length)+"%";}
    private void download(String name){id=UUID.randomUUID();transferName=name;incoming=null;received=0;busy=true;wait=1200;message="正在下载："+name;send("download",name,0,0,new byte[0]);init();}
    public void receive(ArchiveNetwork.Transfer p){
        if(!busy||!p.id().equals(id))return;wait=1200;
        try{
            switch(p.op()){
                case "error" -> throw new IOException(p.name());
                case "ack" -> {if(payload==null||p.offset()<=0||p.offset()>=payload.length)throw new IOException("上传响应异常");sendUpload(p.offset());}
                case "done" -> {completed++;if(!queue.isEmpty()){nextUpload();return;}busy=false;payload=null;message="已上传 "+completed+" 份建筑。用建筑档案仪刷新列表即可放置。";listing(0);init();}
                case "data" -> {
                    if(!p.name().equals(transferName)||p.total()<1||p.total()>ArchiveTransfer.MAX_FILE||p.offset()!=received||p.bytes().length==0||p.bytes().length>p.total()-received)throw new IOException("下载数据异常");
                    if(incoming==null)incoming=new byte[p.total()];if(incoming.length!=p.total())throw new IOException("下载长度改变");
                    System.arraycopy(p.bytes(),0,incoming,received,p.bytes().length);received+=p.bytes().length;message="正在下载："+transferName+" "+(received*100/incoming.length)+"%";
                    if(received<incoming.length)send("next",transferName,received,incoming.length,new byte[0]);
                    else{Path dir=exportFolder();String name=transferName;for(int n=2;Files.exists(dir.resolve(name+".nbt"))||Files.exists(dir.resolve(name+".analysis.json"));n++)name=transferName.substring(0,Math.min(transferName.length(),50))+"_"+n;
                        ArchiveTransfer.save(dir,name,incoming);incoming=null;busy=false;message="已保存：建筑导出/"+name+".nbt（附方块统计JSON）";init();}
                }
                default -> throw new IOException("未知响应");
            }
        }catch(Exception e){send("cancel","",0,0,new byte[0]);busy=false;queue.clear();payload=null;incoming=null;message="传输未完成："+e.getMessage()+(completed>0?"；此前已成功 "+completed+" 份。":"");init();}
    }
    private Path exportFolder(){return minecraft.gameDirectory.toPath().resolve("建筑导出");}
    @Override public void tick(){if(busy&&--wait<=0){send("cancel","",0,0,new byte[0]);busy=false;queue.clear();payload=null;incoming=null;message="传输超时，请重试。";init();}}
    @Override public void onClose(){if(busy)send("cancel","",0,0,new byte[0]);queue.clear();payload=null;incoming=null;super.onClose();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){g.fill(0,0,width,height,0xe0182532);super.render(g,mx,my,dt);int x=left();g.drawString(font,title,x,12,0xffe7d6ab,false);
        if(local&&nameBox!=null)g.drawString(font,"导入名称",x,nameBox.getY()+5,0xffdddddd,false);
        if(!local){g.drawWordWrap(font,Component.literal("下载后在游戏目录的「建筑导出」文件夹。直接发送NBT给朋友即可。"),x,190,span(),0xffbbdddd);}
        g.drawWordWrap(font,Component.literal(message),x,height-34,span(),0xffffdd88);
    }
}
