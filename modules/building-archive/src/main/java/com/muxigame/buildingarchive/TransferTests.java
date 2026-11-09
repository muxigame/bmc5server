package com.muxigame.buildingarchive;

import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(BuildingArchive.ID)
@PrefixGameTestTemplate(false)
public final class TransferTests {
    @GameTest(template="empty") public static void transferWireCodec(GameTestHelper h){
        var b=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try{byte[] bytes=new byte[ArchiveTransfer.CHUNK];new Random(9).nextBytes(bytes);var p=packet("upload","中文建筑",UUID.randomUUID(),0,bytes.length,bytes);ArchiveNetwork.Transfer.CODEC.encode(b,p);var copy=ArchiveNetwork.Transfer.CODEC.decode(b);h.assertTrue(copy.id().equals(p.id())&&copy.name().equals(p.name())&&Arrays.equals(copy.bytes(),bytes),"Packet wire format preserves Chinese names and binary bytes");
            b.clear();ArchiveNetwork.Transfer.CODEC.encode(b,packet("upload","bad",UUID.randomUUID(),0,ArchiveTransfer.CHUNK+1,new byte[ArchiveTransfer.CHUNK+1]));boolean rejected=false;try{ArchiveNetwork.Transfer.CODEC.decode(b);}catch(RuntimeException e){rejected=true;}h.assertTrue(rejected,"Wire rejects oversized chunk before handler");
        }finally{b.release();}h.succeed();
    }
    @GameTest(template="empty") public static void localWorksZipAndUnsafePaths(GameTestHelper h) throws Exception {
        Path dir=Files.createTempDirectory("archive-zip-");Path file=dir.resolve("朋友作品.zip");byte[] bytes=ArchiveTransfer.encode(sample(h));
        try{
            try(var zip=new java.util.zip.ZipOutputStream(Files.newOutputStream(file))){zip.putNextEntry(new java.util.zip.ZipEntry("小屋.nbt"));zip.write(bytes);zip.closeEntry();zip.putNextEntry(new java.util.zip.ZipEntry("小屋.analysis.json"));zip.write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();}
            var read=ArchiveTransferFiles.read(file,"朋友作品");h.assertTrue(read.size()==1&&read.getFirst().name().equals("小屋")&&Arrays.equals(bytes,read.getFirst().bytes()),"Works ZIP preserves original building name and NBT without extracting files");
            h.assertTrue(ArchiveTransferFiles.read(file,"重命名").getFirst().name().equals("重命名"),"Explicit import rename honored");
            try(var zip=new java.util.zip.ZipOutputStream(Files.newOutputStream(file))){zip.putNextEntry(new java.util.zip.ZipEntry("../escape.nbt"));zip.write(bytes);zip.closeEntry();}
            boolean rejected=false;try{ArchiveTransferFiles.read(file,"朋友作品");}catch(java.io.IOException e){rejected=true;}h.assertTrue(rejected&&!Files.exists(dir.getParent().resolve("escape.nbt")),"ZIP traversal rejected, no extraction");
        }finally{Files.deleteIfExists(file);Files.deleteIfExists(dir);}h.succeed();
    }
    private static CompoundTag sample(GameTestHelper h){h.setBlock(1,2,1,Blocks.CHEST);((ChestBlockEntity)h.getBlockEntity(new BlockPos(1,2,1))).setItem(0,new ItemStack(Items.DIAMOND,9));return ArchiveData.capture(h.getLevel(),h.absolutePos(new BlockPos(1,2,1)),new Vec3i(1,1,1));}
    private static ArchiveNetwork.Transfer packet(String op,String name,UUID id,int offset,int total,byte[] bytes){return new ArchiveNetwork.Transfer(op,name,id,offset,total,bytes);}
    @GameTest(template="empty") public static void uploadDownloadAndPlace(GameTestHelper h) throws Exception {
        var player=h.makeMockServerPlayerInLevel();player.server.getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(player.getGameProfile(),2,false));String name="上传测试_"+System.nanoTime();Path folder=ArchiveTransfer.root(player);var replies=new ArrayList<ArchiveNetwork.Transfer>();
        try{
            byte[] bytes=ArchiveTransfer.encode(sample(h));UUID id=UUID.randomUUID();int middle=bytes.length/2;
            ArchiveTransfer.handle(player,packet("upload",name,id,0,bytes.length,Arrays.copyOfRange(bytes,0,middle)),replies::add);
            h.assertTrue(replies.getLast().op().equals("ack")&&!Files.exists(folder.resolve(name+".nbt")),"Incomplete transfer never creates a building: "+replies.getLast());
            ArchiveTransfer.handle(player,packet("upload",name,id,middle,bytes.length,Arrays.copyOfRange(bytes,middle,bytes.length)),replies::add);
            h.assertTrue(replies.getLast().op().equals("done")&&Files.exists(folder.resolve(name+".analysis.json")),"Admin upload saves NBT and analysis");
            ArchiveTransfer.handle(player,packet("download",name,UUID.randomUUID(),0,0,new byte[0]),replies::add);var download=replies.getLast();h.assertTrue(download.op().equals("data"),"Admin can download saved file");
            var restored=ArchiveTransfer.decode(download.bytes());ArchiveData.place(h.getLevel(),h.absolutePos(new BlockPos(1,4,1)),restored);
            h.assertTrue(((ChestBlockEntity)h.getBlockEntity(new BlockPos(1,4,1))).getItem(0).getCount()==9,"Downloaded building can be imported with contents intact");
            byte[] old=Files.readAllBytes(folder.resolve(name+".nbt"));ArchiveTransfer.handle(player,packet("upload",name,UUID.randomUUID(),0,bytes.length,bytes),replies::add);
            h.assertTrue(replies.getLast().op().equals("error")&&Arrays.equals(old,Files.readAllBytes(folder.resolve(name+".nbt"))),"Duplicate names cannot overwrite");
        }finally{ArchiveTransfer.clear(player.getUUID());player.server.getPlayerList().deop(player.getGameProfile());Files.deleteIfExists(folder.resolve(name+".nbt"));Files.deleteIfExists(folder.resolve(name+".analysis.json"));}h.succeed();
    }
    @GameTest(template="empty") public static void nonAdminCreativeDenied(GameTestHelper h) throws Exception {
        var p=h.makeMockServerPlayerInLevel();p.server.getPlayerList().deop(p.getGameProfile());p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);var replies=new ArrayList<ArchiveNetwork.Transfer>();String name="无权限_"+System.nanoTime();byte[] bytes=ArchiveTransfer.encode(sample(h));
        h.assertTrue(!ArchiveTransfer.allowed(p),"Creative alone does not authorize server uploads");ArchiveTransfer.handle(p,packet("upload",name,UUID.randomUUID(),0,bytes.length,bytes),replies::add);
        h.assertTrue(replies.getLast().op().equals("error")&&!Files.exists(ArchiveTransfer.root(p).resolve(name+".nbt")),"Unauthorized packet rejected before file write");h.succeed();
    }
    @GameTest(template="empty") public static void sequenceAndSizeGuards(GameTestHelper h){
        var id=UUID.randomUUID();var pending=new ArchiveTransfer.Pending(id,"测试",4,true);ArchiveTransfer.append(pending,packet("upload","测试",id,0,4,new byte[]{1,2}));
        for(var bad:List.of(packet("upload","测试",id,0,4,new byte[]{3}),packet("upload","测试",UUID.randomUUID(),2,4,new byte[]{3}),packet("upload","测试",id,2,4,new byte[]{3,4,5}))){boolean failed=false;try{ArchiveTransfer.append(pending,bad);}catch(IllegalArgumentException e){failed=true;}h.assertTrue(failed,"Reject replay, wrong ID and overflow");}
        h.assertTrue(pending.offset==2,"Invalid packets leave buffered bytes unchanged");ArchiveTransfer.append(pending,packet("upload","测试",id,2,4,new byte[]{3,4}));h.assertTrue(Arrays.equals(pending.bytes,new byte[]{1,2,3,4}),"Ordered bytes reconstructed");h.succeed();
    }
    @GameTest(template="empty") public static void malformedUploadAndPathRejected(GameTestHelper h) throws Exception {
        Path dir=Files.createTempDirectory("archive-transfer-");try{
            for(String name:List.of("../escape","bad/name","")){boolean rejected=false;try{ArchiveTransfer.save(dir,name,ArchiveTransfer.encode(sample(h)));}catch(IllegalArgumentException e){rejected=true;}h.assertTrue(rejected,"Unsafe upload names rejected");}
            boolean bad=false;try{ArchiveTransfer.save(dir,"坏文件",new byte[]{1,2,3});}catch(Exception e){bad=true;}h.assertTrue(bad&&!Files.exists(dir.resolve("坏文件.nbt")),"Malformed NBT leaves no target");
            var invalid=sample(h);invalid.putInt("DataVersion",1);var out=new java.io.ByteArrayOutputStream();NbtIo.writeCompressed(invalid,out);bad=false;try{ArchiveTransfer.save(dir,"旧版本",out.toByteArray());}catch(IllegalArgumentException e){bad=true;}h.assertTrue(bad&&!Files.exists(dir.resolve("旧版本.nbt")),"Incompatible data rejected before disk mutation");
        }finally{Files.deleteIfExists(dir);}h.succeed();
    }
    @GameTest(template="empty") public static void multichunkDownloadAndCancel(GameTestHelper h) throws Exception {
        var p=h.makeMockServerPlayerInLevel();p.server.getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(p.getGameProfile(),2,false));var data=sample(h);byte[] noise=new byte[80000];new Random(314).nextBytes(noise);data.putByteArray("TestPadding",noise);byte[] bytes=ArchiveTransfer.encode(data);String name="分段测试_"+System.nanoTime();Path folder=ArchiveTransfer.root(p);var replies=new ArrayList<ArchiveNetwork.Transfer>();
        try{
            ArchiveTransfer.save(folder,name,bytes);UUID id=UUID.randomUUID();ArchiveTransfer.handle(p,packet("download",name,id,0,0,new byte[0]),replies::add);var out=new java.io.ByteArrayOutputStream();
            while(true){var chunk=replies.getLast();h.assertTrue(chunk.op().equals("data")&&chunk.bytes().length<=ArchiveTransfer.CHUNK&&chunk.offset()==out.size(),"Download chunks ordered and bounded: "+chunk);out.write(chunk.bytes());if(out.size()==chunk.total())break;ArchiveTransfer.handle(p,packet("next",name,id,out.size(),0,new byte[0]),replies::add);}
            h.assertTrue(ArchiveTransfer.decode(out.toByteArray()).equals(data),"Full multichunk download preserves exact NBT");
            id=UUID.randomUUID();ArchiveTransfer.handle(p,packet("upload",name+"取消",id,0,bytes.length,Arrays.copyOf(bytes,100)),replies::add);ArchiveTransfer.handle(p,packet("cancel","",id,0,0,new byte[0]),replies::add);ArchiveTransfer.handle(p,packet("upload",name+"取消",id,100,bytes.length,new byte[]{1}),replies::add);h.assertTrue(replies.getLast().op().equals("error")&&!Files.exists(folder.resolve(name+"取消.nbt")),"Cancelled upload creates no file");
        }finally{ArchiveTransfer.clear(p.getUUID());p.server.getPlayerList().deop(p.getGameProfile());Files.deleteIfExists(folder.resolve(name+".nbt"));Files.deleteIfExists(folder.resolve(name+".analysis.json"));}h.succeed();
    }
}
