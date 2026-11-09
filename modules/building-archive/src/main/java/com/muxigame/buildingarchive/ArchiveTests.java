package com.muxigame.buildingarchive;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.*;
import net.neoforged.neoforge.gametest.*;
import java.nio.file.*;

@GameTestHolder(BuildingArchive.ID)
@PrefixGameTestTemplate(false)
public final class ArchiveTests {
    private static BlockPos p(GameTestHelper h,int x,int y,int z){return h.absolutePos(new BlockPos(x,y,z));}
    private static CompoundTag one(GameTestHelper h){h.setBlock(1,2,1,Blocks.STONE);return ArchiveData.capture(h.getLevel(),p(h,1,2,1),new Vec3i(1,1,1));}
    private static void reject(GameTestHelper h,Runnable action,String message) {
        boolean failed=false;try{action.run();}catch(IllegalArgumentException e){failed=true;}h.assertTrue(failed,message);
    }
    @GameTest(template="empty") public static void blocksAndStatesRoundTrip(GameTestHelper h) {
        var state=Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.EAST).setValue(StairBlock.HALF,Half.TOP).setValue(StairBlock.WATERLOGGED,true);
        h.getLevel().setBlock(p(h,1,2,1),state,2|16);h.setBlock(2,2,1,Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,Direction.Axis.X));
        var tag=ArchiveData.capture(h.getLevel(),p(h,1,2,1),new Vec3i(2,1,1));
        ArchiveData.place(h.getLevel(),p(h,1,3,1),tag);
        h.assertTrue(h.getLevel().getBlockState(p(h,1,3,1)).equals(state),"Stair facing/half/waterlogging restored");
        h.assertTrue(h.getLevel().getBlockState(p(h,2,3,1)).getValue(RotatedPillarBlock.AXIS)==Direction.Axis.X,"Log axis restored");h.succeed();
    }
    @GameTest(template="empty") public static void containerContentsRoundTrip(GameTestHelper h) {
        h.setBlock(1,2,1,Blocks.CHEST);ChestBlockEntity chest=h.getBlockEntity(new BlockPos(1,2,1));chest.setItem(3,new ItemStack(Items.DIAMOND,7));
        var tag=ArchiveData.capture(h.getLevel(),p(h,1,2,1),new Vec3i(1,1,1));ArchiveData.place(h.getLevel(),p(h,1,3,1),tag);
        ChestBlockEntity copy=h.getBlockEntity(new BlockPos(1,3,1));h.assertTrue(copy.getItem(3).is(Items.DIAMOND)&&copy.getItem(3).getCount()==7,"Chest payload restored");h.succeed();
    }
    @GameTest(template="empty") public static void airOverwriteAndUndo(GameTestHelper h) {
        h.setBlock(1,3,1,Blocks.AIR);h.setBlock(2,3,1,Blocks.STONE);h.setBlock(1,2,1,Blocks.CHEST);
        ChestBlockEntity chest=h.getBlockEntity(new BlockPos(1,2,1));chest.setItem(0,new ItemStack(Items.DIAMOND,5));
        var before=ArchiveData.capture(h.getLevel(),p(h,1,2,1),new Vec3i(2,1,1));
        var incoming=ArchiveData.capture(h.getLevel(),p(h,1,3,1),new Vec3i(2,1,1));
        h.assertTrue(ArchiveData.occupied(h.getLevel(),p(h,1,2,1),incoming)>0,"Occupied target detected");
        ArchiveData.place(h.getLevel(),p(h,1,2,1),incoming);h.assertBlockPresent(Blocks.AIR,1,2,1);h.assertBlockPresent(Blocks.STONE,2,2,1);
        h.assertItemEntityNotPresent(Items.DIAMOND);ArchiveData.place(h.getLevel(),p(h,1,2,1),before);
        ChestBlockEntity restored=h.getBlockEntity(new BlockPos(1,2,1));h.assertTrue(restored.getItem(0).getCount()==5,"Undo restores contents without dropped duplicates");h.succeed();
    }
    @GameTest(template="empty") public static void analysisCountsAndLayers(GameTestHelper h) {
        h.setBlock(1,2,1,Blocks.STONE);h.setBlock(2,2,1,Blocks.STONE);h.setBlock(1,3,1,Blocks.OAK_PLANKS);h.setBlock(2,3,1,Blocks.AIR);
        var report=ArchiveData.analysis(ArchiveData.capture(h.getLevel(),p(h,1,2,1),new Vec3i(2,2,1)));
        h.assertTrue(report.get("nonAir").getAsInt()==3&&report.get("air").getAsInt()==1,"Air separated from material counts");
        h.assertTrue(report.getAsJsonObject("blocks").get("minecraft:stone").getAsInt()==2,"Exact stone quantity");
        h.assertTrue(report.getAsJsonObject("layers").getAsJsonObject("1").get("minecraft:oak_planks").getAsInt()==1,"Per-layer distribution");h.succeed();
    }
    @GameTest(template="empty") public static void compressedFileRoundTrip(GameTestHelper h) throws Exception {
        Path dir=Files.createTempDirectory("building-archive-test-");Path file=ArchiveData.file(dir,"roundtrip");
        try {var before=one(h);ArchiveData.writeNew(file,before);var after=ArchiveData.read(file);h.assertTrue(before.equals(after),"Compressed NBT roundtrip exact");
            boolean duplicate=false;try{ArchiveData.writeNew(file,before);}catch(IllegalArgumentException e){duplicate=true;}h.assertTrue(duplicate,"Cannot overwrite named archive");
        }finally{Files.deleteIfExists(file);Files.deleteIfExists(dir);}h.succeed();
    }
    @GameTest(template="empty") public static void unknownBlockRejected(GameTestHelper h) {
        var tag=one(h);tag.getList("palette",Tag.TAG_COMPOUND).getCompound(0).putString("Name","missing_mod:wall");
        reject(h,()->ArchiveData.place(h.getLevel(),p(h,1,2,1),tag),"Missing blocks rejected before mutation");h.assertBlockPresent(Blocks.STONE,1,2,1);h.succeed();
    }
    @GameTest(template="empty") public static void invalidStateRejected(GameTestHelper h) {
        var tag=one(h);CompoundTag props=new CompoundTag();props.putString("bogus","true");tag.getList("palette",Tag.TAG_COMPOUND).getCompound(0).put("Properties",props);
        reject(h,()->ArchiveData.validate(tag),"Invalid properties rejected");h.succeed();
    }
    @GameTest(template="empty") public static void corruptPositionRejected(GameTestHelper h) {
        var tag=one(h);ListTag pos=new ListTag();pos.add(IntTag.valueOf(999));pos.add(IntTag.valueOf(0));pos.add(IntTag.valueOf(0));tag.getList("blocks",Tag.TAG_COMPOUND).getCompound(0).put("pos",pos);
        reject(h,()->ArchiveData.validate(tag),"Out of bounds entries rejected");h.succeed();
    }
    @GameTest(template="empty") public static void duplicateAndIndexRejected(GameTestHelper h) {
        var tag=one(h);var blocks=tag.getList("blocks",Tag.TAG_COMPOUND);blocks.add(blocks.getCompound(0).copy());
        reject(h,()->ArchiveData.validate(tag),"Duplicate positions rejected");blocks.remove(1);blocks.getCompound(0).putInt("state",99);
        reject(h,()->ArchiveData.validate(tag),"Invalid palette index rejected");h.succeed();
    }
    @GameTest(template="empty") public static void boundsAndVersionChecks(GameTestHelper h) {
        var b=ArchiveData.bounds(new BlockPos(5,5,5),new BlockPos(3,3,3));h.assertTrue(b.size().equals(new Vec3i(3,3,3)),"Corners inclusive and reversible");
        reject(h,()->ArchiveData.bounds(BlockPos.ZERO,new BlockPos(100,100,100)),"Volume cap");
        reject(h,()->ArchiveData.bounds(new BlockPos(Integer.MIN_VALUE,0,0),new BlockPos(Integer.MAX_VALUE,0,0)),"Integer overflow rejected");
        var tag=one(h);tag.putInt("DataVersion",1);reject(h,()->ArchiveData.validate(tag),"Version mismatch rejected");h.succeed();
    }
    @GameTest(template="empty") public static void pathTraversalRejected(GameTestHelper h) throws Exception {
        Path root=Files.createTempDirectory("archive-path-test-");
        try {for(String name:new String[]{"../escape","C:/bad","a/b","..",""}){boolean bad=false;try{ArchiveData.file(root,name);}catch(IllegalArgumentException e){bad=true;}h.assertTrue(bad,"Reject path "+name);}}
        finally{Files.deleteIfExists(root);}h.succeed();
    }
    @GameTest(template="empty") public static void worldBoundsRejected(GameTestHelper h) {
        reject(h,()->ArchiveData.checkArea(h.getLevel(),new BlockPos(0,h.getLevel().getMaxBuildHeight(),0),new Vec3i(1,1,1)),"Build height rejected");
        reject(h,()->ArchiveData.checkArea(h.getLevel(),new BlockPos(29999999,70,29999999),new Vec3i(2,2,2)),"World border rejected");h.succeed();
    }
    @GameTest(template="empty") public static void commandExportLoadPasteUndo(GameTestHelper h) throws Exception {
        var player=h.makeMockServerPlayerInLevel();player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        h.setBlock(1,2,1,Blocks.CHISELED_STONE_BRICKS);BlockPos source=p(h,1,2,1),dest=p(h,1,3,1);
        player.setPos(source.getX()+.5,source.getY(),source.getZ()+.5);
        var dispatcher=h.getLevel().getServer().getCommands().getDispatcher();var context=player.createCommandSourceStack();
        h.assertTrue(dispatcher.execute("ba pos1",context)==1,"First corner command");h.assertTrue(dispatcher.execute("ba pos2",context)==1,"Second corner command");
        String name="test_"+java.util.UUID.randomUUID().toString().replace("-","");
        h.assertTrue(dispatcher.execute("ba export "+name,context)==1,"Export command");
        h.assertTrue(dispatcher.execute("ba load "+name,context)==1,"Load command");
        player.setPos(dest.getX()+.5,dest.getY(),dest.getZ()+.5);context=player.createCommandSourceStack();
        h.assertTrue(dispatcher.execute("ba preview",context)==1,"Preview command");
        h.setBlock(1,3,1,Blocks.GOLD_BLOCK);
        h.assertTrue(dispatcher.execute("ba paste",context)==0,"Changed destination must reject overwrite without replace");h.assertBlockPresent(Blocks.GOLD_BLOCK,1,3,1);
        h.assertTrue(dispatcher.execute("ba paste replace",context)==1,"Explicit replacement");h.assertBlockPresent(Blocks.CHISELED_STONE_BRICKS,1,3,1);
        h.assertTrue(dispatcher.execute("ba undo",context)==1,"Undo command");h.assertBlockPresent(Blocks.GOLD_BLOCK,1,3,1);
        Path root=h.getLevel().getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("building_archive");
        var report=com.google.gson.JsonParser.parseString(Files.readString(root.resolve(name+".analysis.json"))).getAsJsonObject();
        h.assertTrue(report.getAsJsonObject("blocks").get("minecraft:chiseled_stone_bricks").getAsInt()==1,"Readable report written beside NBT");
        Files.createDirectories(Path.of("../../reports"));Files.copy(root.resolve(name+".analysis.json"),Path.of("../../reports/example.analysis.json"),StandardCopyOption.REPLACE_EXISTING);
        h.succeed();
    }
    @GameTest(template="empty") public static void wandSelectsContainerWithoutOpening(GameTestHelper h) throws Exception {
        var player=h.makeMockServerPlayerInLevel();player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        ItemStack wand=new ItemStack(BuildingArchive.TOOL.get());player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,wand);
        h.setBlock(1,2,1,Blocks.CHEST);BlockPos pos=p(h,1,2,1);
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.UP,pos,false);
        var context=new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
        var originalMenu=player.containerMenu;
        h.assertTrue(wand.getItem().onItemUseFirst(wand,context).consumesAction(),"Tool takes priority over chest opening");
        player.setShiftKeyDown(true);wand.getItem().onItemUseFirst(wand,context);player.setShiftKeyDown(false);
        h.assertTrue(player.containerMenu==originalMenu,"Selection does not open chest");
        int result=h.getLevel().getServer().getCommands().getDispatcher().execute("ba export wand_"+System.nanoTime(),player.createCommandSourceStack());
        h.assertTrue(result==1,"Both tool corner selections reach export command");h.succeed();
    }
    @GameTest(template="empty") public static void survivalPlayerDenied(GameTestHelper h) throws Exception {
        var player=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"archive-survival"),net.minecraft.server.level.ClientInformation.createDefault());
        h.assertTrue(!player.isCreative()&&!player.hasPermissions(2),"Test uses a real unprivileged survival player");
        var dispatcher=h.getLevel().getServer().getCommands().getDispatcher();
        var parse=dispatcher.parse("ba give",player.createCommandSourceStack());
        h.assertTrue(!parse.getExceptions().isEmpty()||parse.getReader().canRead(),"Non-OP survival player cannot access archive commands");h.succeed();
    }
    @GameTest(template="empty") public static void guidedSelectionAndRevision(GameTestHelper h) throws Exception {
        var player=h.makeMockServerPlayerInLevel();player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        var wand=new ItemStack(BuildingArchive.TOOL.get());player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,wand);
        for(int x=1;x<=2;x++){BlockPos pos=p(h,x,2,1);h.setBlock(x,2,1,Blocks.STONE);var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.UP,pos,false);wand.getItem().onItemUseFirst(wand,new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,hit));}
        var before=BuildingArchive.snapshot(player,"",0);h.assertTrue(before.getBoolean("Valid"),"Two normal clicks finish selection without sneak");long revision=before.getLong("Revision");
        BuildingArchive.action(player,new ArchiveNetwork.Request("height",""+p(h,1,2,1).getY()+","+p(h,1,3,1).getY(),revision,0));
        var adjusted=BuildingArchive.snapshot(player,"",0);h.assertTrue(adjusted.getInt("High")-adjusted.getInt("Low")==1,"GUI height applied");
        BuildingArchive.action(player,new ArchiveNetwork.Request("select","",revision,0));h.assertTrue(BuildingArchive.snapshot(player,"",0).getBoolean("Selected"),"Stale request cannot reset a newer selection");
        String name="中文建筑_"+System.nanoTime();BuildingArchive.action(player,new ArchiveNetwork.Request("export",name,adjusted.getLong("Revision"),0));var saved=BuildingArchive.snapshot(player,"",0);h.assertTrue(saved.getBoolean("Loaded")&&saved.getString("Name").equals(name),"Chinese GUI export reloads archive");h.assertTrue(saved.getInt("NonAir")==2,"GUI material summary counts actual blocks");
        BuildingArchive.action(player,new ArchiveNetwork.Request("select","",saved.getLong("Revision"),0));var cleared=BuildingArchive.snapshot(player,"",0);h.assertTrue(!cleared.getBoolean("Selected")&&!cleared.getBoolean("Loaded"),"New selection clears all old corners");
        BuildingArchive.action(player,new ArchiveNetwork.Request("load",name,cleared.getLong("Revision"),0));h.assertTrue(BuildingArchive.snapshot(player,"",0).getBoolean("Loaded"),"GUI archive loading");h.succeed();
    }
    @GameTest(template="empty") public static void unicodeNamesStillRejectTraversal(GameTestHelper h) throws Exception {
        Path dir=Files.createTempDirectory("ba-unicode");h.assertTrue(ArchiveData.file(dir,"石木小屋_一号").getParent().equals(dir),"Chinese filenames stay within root");
        for(String name:new String[]{"../小屋","小屋/逃逸","小屋\\逃逸","CON:","房屋.nbt"}){boolean bad=false;try{ArchiveData.file(dir,name);}catch(IllegalArgumentException e){bad=true;}h.assertTrue(bad,"Reject unsafe unicode name");}Files.deleteIfExists(dir);h.succeed();
    }
    @GameTest(template="empty") public static void rotatedStructureAndContainer(GameTestHelper h){
        h.setBlock(1,2,1,Blocks.OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING,Direction.NORTH));
        h.setBlock(2,2,1,Blocks.CHEST);((net.minecraft.world.level.block.entity.ChestBlockEntity)h.getBlockEntity(new BlockPos(2,2,1))).setItem(0,new ItemStack(Items.DIAMOND,7));
        var original=ArchiveData.capture(h.getLevel(),p(h,1,2,1),new Vec3i(2,1,3));var lookup=h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK);
        var rotated=ArchiveData.rotate(original,lookup,true);h.assertTrue(ArchiveData.validate(rotated).equals(new Vec3i(3,1,2)),"Rotation swaps rectangular footprint");
        ArchiveData.place(h.getLevel(),p(h,1,4,1),rotated);h.assertTrue(h.getBlockState(new BlockPos(3,4,1)).getValue(net.minecraft.world.level.block.StairBlock.FACING)==Direction.EAST,"Stair facing rotates with geometry");
        var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)h.getBlockEntity(new BlockPos(3,4,2));h.assertTrue(chest.getItem(0).is(Items.DIAMOND)&&chest.getItem(0).getCount()==7,"Rotated chest retains inventory");
        var restored=ArchiveData.rotate(rotated,lookup,false);h.assertTrue(restored.equals(original),"Opposite rotation restores exact structure NBT");
        for(int n=0;n<3;n++)rotated=ArchiveData.rotate(rotated,lookup,true);h.assertTrue(rotated.equals(original),"Four turns restore original");h.succeed();
    }
    @GameTest(template="empty") public static void mirrorStatesAndInventory(GameTestHelper h) {
        var stair=Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.EAST).setValue(StairBlock.HALF,Half.TOP);
        h.getLevel().setBlock(p(h,1,2,1),stair,2|16);
        h.setBlock(2,2,1,Blocks.CHEST);((ChestBlockEntity)h.getBlockEntity(new BlockPos(2,2,1))).setItem(0,new ItemStack(Items.DIAMOND,7));
        var original=ArchiveData.capture(h.getLevel(),p(h,1,2,1),new Vec3i(3,2,2));var lookup=h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK);
        var mirrored=ArchiveData.mirror(original,lookup);
        h.assertTrue(ArchiveData.validate(mirrored).equals(new Vec3i(3,2,2)),"Mirror preserves dimensions and height");
        h.assertTrue(ArchiveData.mirror(mirrored,lookup).equals(original),"Two mirrors restore exact NBT including inventories");
        ArchiveData.place(h.getLevel(),p(h,1,4,1),mirrored);
        h.assertTrue(h.getBlockState(new BlockPos(3,4,1)).getValue(StairBlock.FACING)==Direction.WEST,"East stair reflected west");
        h.assertTrue(h.getBlockState(new BlockPos(3,4,1)).getValue(StairBlock.HALF)==Half.TOP,"No vertical mirroring");
        var chest=(ChestBlockEntity)h.getBlockEntity(new BlockPos(2,4,1));h.assertTrue(chest.getItem(0).getCount()==7,"Mirrored chest inventory intact");
        h.assertBlockPresent(Blocks.AIR,1,4,1);h.assertBlockPresent(Blocks.OAK_STAIRS,1,2,1);h.succeed();
    }
    @GameTest(template="empty") public static void mirrorPreviewPasteUndo(GameTestHelper h) throws Exception {
        var player=h.makeMockServerPlayerInLevel();player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);var d=player.server.getCommands().getDispatcher();
        h.setBlock(1,2,1,Blocks.GOLD_BLOCK);h.setBlock(2,2,1,Blocks.STONE);
        var at=p(h,1,2,1);player.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);d.execute("ba pos1",player.createCommandSourceStack());player.setPos(at.getX()+1.5,at.getY(),at.getZ()+.5);d.execute("ba pos2",player.createCommandSourceStack());d.execute("ba export mirror_"+System.nanoTime(),player.createCommandSourceStack());
        player.setPos(at.getX()+.5,at.getY()+2,at.getZ()+.5);d.execute("ba preview",player.createCommandSourceStack());var old=BuildingArchive.snapshot(player,"",0);gui(player,"mirror");var now=BuildingArchive.snapshot(player,"",0);
        h.assertTrue(now.getLong("Revision")>old.getLong("Revision")&&now.getString("Anchor").equals(old.getString("Anchor")),"Mirror updates preview revision without moving anchor");
        BuildingArchive.action(player,new ArchiveNetwork.Request("mirror","",old.getLong("Revision"),0));gui(player,"paste");
        h.assertBlockPresent(Blocks.STONE,1,4,1);h.assertBlockPresent(Blocks.GOLD_BLOCK,2,4,1);gui(player,"undo");h.assertBlockPresent(Blocks.AIR,1,4,1);h.assertBlockPresent(Blocks.AIR,2,4,1);h.succeed();
    }
    private static void gui(net.minecraft.server.level.ServerPlayer p,String action) throws Exception {BuildingArchive.action(p,new ArchiveNetwork.Request(action,"",BuildingArchive.snapshot(p,"",0).getLong("Revision"),0));}
    @GameTest(template="empty") public static void rotationPreviewMatchesPaste(GameTestHelper h) throws Exception {
        var player=h.makeMockServerPlayerInLevel();player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);var d=player.server.getCommands().getDispatcher();h.setBlock(1,2,1,Blocks.GOLD_BLOCK);h.setBlock(2,2,1,Blocks.STONE);
        var at=p(h,1,2,1);player.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);d.execute("ba pos1",player.createCommandSourceStack());player.setPos(at.getX()+1.5,at.getY(),at.getZ()+.5);d.execute("ba pos2",player.createCommandSourceStack());d.execute("ba export rotation_"+System.nanoTime(),player.createCommandSourceStack());
        player.setPos(at.getX()+.5,at.getY()+2,at.getZ()+.5);d.execute("ba preview",player.createCommandSourceStack());var old=BuildingArchive.snapshot(player,"",0);gui(player,"rotate_right");var now=BuildingArchive.snapshot(player,"",0);
        h.assertTrue(now.getInt("Rotation")==90&&now.getCompound("Projection").getIntArray("Size")[2]==2,"GUI projection and angle updated");h.assertTrue(now.getString("Anchor").equals(old.getString("Anchor")),"Rotation holds minimum corner fixed");h.assertBlockPresent(Blocks.AIR,1,4,1);
        BuildingArchive.action(player,new ArchiveNetwork.Request("rotate_right","",old.getLong("Revision"),0));h.assertTrue(BuildingArchive.snapshot(player,"",0).getInt("Rotation")==90,"Stale rotation rejected");gui(player,"paste");h.assertBlockPresent(Blocks.GOLD_BLOCK,1,4,1);h.assertBlockPresent(Blocks.STONE,1,4,2);gui(player,"undo");h.assertBlockPresent(Blocks.AIR,1,4,1);h.succeed();
    }
    @GameTest(template="empty") public static void deletePreviewBackupAndUndo(GameTestHelper h) throws Exception {
        var player=h.makeMockServerPlayerInLevel();player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);var dispatcher=player.server.getCommands().getDispatcher();
        h.setBlock(1,2,1,Blocks.CHEST);var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)h.getBlockEntity(new BlockPos(1,2,1));chest.setItem(0,new ItemStack(Items.DIAMOND,4));h.setBlock(2,2,1,Blocks.GOLD_BLOCK);
        player.setPos(p(h,1,2,1).getX()+.5,p(h,1,2,1).getY(),p(h,1,2,1).getZ()+.5);dispatcher.execute("ba pos1",player.createCommandSourceStack());dispatcher.execute("ba pos2",player.createCommandSourceStack());
        gui(player,"delete_confirm");h.assertBlockPresent(Blocks.CHEST,1,2,1);gui(player,"delete_preview");h.assertTrue(BuildingArchive.snapshot(player,"",0).getBoolean("DeletePreview"),"Server arms red preview");h.assertBlockPresent(Blocks.CHEST,1,2,1);
        gui(player,"delete_confirm");h.assertBlockPresent(Blocks.AIR,1,2,1);h.assertBlockPresent(Blocks.GOLD_BLOCK,2,2,1);h.assertTrue(BuildingArchive.snapshot(player,"",0).getBoolean("Undo"),"Delete records undo");h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(p(h,1,2,1))).isEmpty(),"Deletion does not duplicate container drops");
        var backupDir=player.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("building_archive/backups");try(var files=Files.list(backupDir)){h.assertTrue(files.anyMatch(f->f.getFileName().toString().startsWith("delete_"+player.getUUID())),"Disk backup exists before deletion");}
        gui(player,"undo");h.assertBlockPresent(Blocks.CHEST,1,2,1);h.assertTrue(((net.minecraft.world.level.block.entity.ChestBlockEntity)h.getBlockEntity(new BlockPos(1,2,1))).getItem(0).getCount()==4,"Undo restores container contents");h.succeed();
    }
    @GameTest(template="empty") public static void changedDeletePreviewRejected(GameTestHelper h) throws Exception {
        var player=h.makeMockServerPlayerInLevel();player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);var dispatcher=player.server.getCommands().getDispatcher();var at=p(h,1,2,1);h.setBlock(1,2,1,Blocks.STONE);player.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);dispatcher.execute("ba pos1",player.createCommandSourceStack());dispatcher.execute("ba pos2",player.createCommandSourceStack());
        gui(player,"delete_preview");h.setBlock(1,2,1,Blocks.GOLD_BLOCK);gui(player,"delete_confirm");h.assertBlockPresent(Blocks.GOLD_BLOCK,1,2,1);h.assertTrue(!BuildingArchive.snapshot(player,"",0).getBoolean("DeletePreview"),"Changed world invalidates confirmation");gui(player,"delete_preview");gui(player,"cancel");gui(player,"delete_confirm");h.assertBlockPresent(Blocks.GOLD_BLOCK,1,2,1);h.succeed();
    }
    @GameTest(template="empty") public static void deleteRecordedArchiveKeepsWorld(GameTestHelper h) throws Exception {
        var player=h.makeMockServerPlayerInLevel();player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);var d=player.server.getCommands().getDispatcher();var at=p(h,1,2,1);h.setBlock(1,2,1,Blocks.GOLD_BLOCK);player.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);d.execute("ba pos1",player.createCommandSourceStack());d.execute("ba pos2",player.createCommandSourceStack());String name="删除记录_"+System.nanoTime();BuildingArchive.action(player,new ArchiveNetwork.Request("export",name,BuildingArchive.snapshot(player,"",0).getLong("Revision"),0));
        Path root=player.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("building_archive");byte[] original=Files.readAllBytes(root.resolve(name+".nbt"));var before=BuildingArchive.snapshot(player,"",0);
        BuildingArchive.action(player,new ArchiveNetwork.Request("delete_archive","../escape",before.getLong("Revision"),0));h.assertTrue(Files.exists(root.resolve(name+".nbt")),"Invalid name cannot remove archive");
        BuildingArchive.action(player,new ArchiveNetwork.Request("delete_archive",name,before.getLong("Revision")-1,0));h.assertTrue(Files.exists(root.resolve(name+".nbt")),"Stale delete rejected");
        BuildingArchive.action(player,new ArchiveNetwork.Request("delete_archive",name,before.getLong("Revision"),0));h.assertBlockPresent(Blocks.GOLD_BLOCK,1,2,1);h.assertTrue(!Files.exists(root.resolve(name+".nbt"))&&!Files.exists(root.resolve(name+".analysis.json")),"Record and report removed from list directory");h.assertTrue(!BuildingArchive.snapshot(player,"",0).getBoolean("Loaded"),"Loaded record invalidated");
        try(var files=Files.walk(root.resolve("deleted"))){var backup=files.filter(f->f.getFileName().toString().equals(name+".nbt")).findFirst().orElseThrow();h.assertTrue(java.util.Arrays.equals(original,Files.readAllBytes(backup)),"Exact archive bytes backed up");h.assertTrue(Files.exists(backup.resolveSibling(name+".analysis.json")),"Analysis backed up alongside archive");}h.succeed();
    }
    @GameTest(template="empty") public static void renameRecordedArchive(GameTestHelper h) throws Exception {
        var player=h.makeMockServerPlayerInLevel();player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);var d=player.server.getCommands().getDispatcher();var at=p(h,1,2,1);h.setBlock(1,2,1,Blocks.GOLD_BLOCK);player.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);d.execute("ba pos1",player.createCommandSourceStack());d.execute("ba pos2",player.createCommandSourceStack());String old="old_"+System.nanoTime(),renamed="新名称_"+System.nanoTime();d.execute("ba export "+old,player.createCommandSourceStack());d.execute("ba preview",player.createCommandSourceStack());
        Path root=player.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("building_archive");byte[] original=Files.readAllBytes(root.resolve(old+".nbt")),report=Files.readAllBytes(root.resolve(old+".analysis.json"));var before=BuildingArchive.snapshot(player,"",0);
        BuildingArchive.action(player,new ArchiveNetwork.Request("rename_archive",old+"\n../bad",before.getLong("Revision"),0));h.assertTrue(Files.exists(root.resolve(old+".nbt")),"Invalid name rejected");
        BuildingArchive.action(player,new ArchiveNetwork.Request("rename_archive",old+"\n"+renamed,before.getLong("Revision")-1,0));h.assertTrue(Files.exists(root.resolve(old+".nbt")),"Stale rename rejected");
        BuildingArchive.action(player,new ArchiveNetwork.Request("rename_archive",old+"\n"+renamed,before.getLong("Revision"),0));var after=BuildingArchive.snapshot(player,"",0);h.assertTrue(after.getString("Name").equals(renamed)&&after.getString("Anchor").equals(before.getString("Anchor")),"Loaded name updates without losing projection");h.assertTrue(!Files.exists(root.resolve(old+".nbt"))&&java.util.Arrays.equals(original,Files.readAllBytes(root.resolve(renamed+".nbt")))&&java.util.Arrays.equals(report,Files.readAllBytes(root.resolve(renamed+".analysis.json"))),"Both files renamed without changing bytes");h.assertBlockPresent(Blocks.GOLD_BLOCK,1,2,1);
        Files.write(root.resolve(old+".analysis.json"),report);boolean rejected=false;try{BuildingArchive.renameArchive(player,renamed,old);}catch(IllegalArgumentException e){rejected=true;}h.assertTrue(rejected&&Files.exists(root.resolve(renamed+".nbt")),"Existing report prevents overwrite");BuildingArchive.renameArchive(player,renamed,renamed);h.succeed();
    }
    @GameTest(template="empty") public static void projectionGeometryExcludesAir(GameTestHelper h){
        h.setBlock(1,2,1,Blocks.STONE);h.setBlock(2,2,1,Blocks.STONE);h.setBlock(3,2,1,Blocks.AIR);h.setBlock(3,3,1,Blocks.OAK_PLANKS);
        var tag=ArchiveData.capture(h.getLevel(),p(h,1,2,1),new Vec3i(3,2,1));int[] runs=ArchiveData.projectionRuns(tag);h.assertTrue(runs.length==12,"Two occupied rows only; no air outline");h.assertTrue(runs[0]==0&&runs[3]==2&&runs[6]==2&&runs[7]==1,"Projection matches position, roof height and horizontal span");h.succeed();
    }
    @GameTest(template="empty") public static void remoteRayAndSafeOffsets(GameTestHelper h){
        var player=h.makeMockServerPlayerInLevel();BlockPos target=p(h,1,2,1);h.setBlock(1,2,1,Blocks.STONE);for(int clear=3;clear<=22;clear++)h.setBlock(1,clear,1,Blocks.AIR);player.setPos(target.getX()+.5,target.getY()+16,target.getZ()+.5);player.setXRot(90);player.setYRot(0);
        h.assertTrue(BuildingArchive.remoteTarget(player).equals(target.above()),"Ray locates surface beyond normal reach: got "+BuildingArchive.remoteTarget(player)+" expected "+target.above()+" eye "+player.getEyePosition()+" look "+player.getLookAngle());h.assertTrue(BuildingArchive.shiftedAnchor(player,target,"0,5,0").equals(target.above(5)),"Five block height adjustment");
        reject(h,()->BuildingArchive.shiftedAnchor(player,target,"0,100,0"),"Reject oversized offset");reject(h,()->BuildingArchive.shiftedAnchor(player,target,"1,1,0"),"Reject diagonal offset");reject(h,()->BuildingArchive.shiftedAnchor(player,target.offset(120,0,0),"1,0,0"),"Reject projection too far away");player.setXRot(-90);reject(h,()->BuildingArchive.remoteTarget(player),"Aiming into sky does not invent a location");h.succeed();
    }
    @GameTest(template="empty") public static void remotePreviewMovesWithoutWorldMutation(GameTestHelper h) throws Exception {
        var player=h.makeMockServerPlayerInLevel();player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);BlockPos target=p(h,1,2,1);h.setBlock(1,2,1,Blocks.STONE);player.setPos(target.getX()+.5,target.getY(),target.getZ()+.5);var dispatcher=player.server.getCommands().getDispatcher();dispatcher.execute("ba pos1",player.createCommandSourceStack());dispatcher.execute("ba pos2",player.createCommandSourceStack());dispatcher.execute("ba export remote_"+System.nanoTime(),player.createCommandSourceStack());
        for(int clear=3;clear<=22;clear++)h.setBlock(1,clear,1,Blocks.AIR);player.setPos(target.getX()+.5,target.getY()+16,target.getZ()+.5);player.setXRot(90);var before=BuildingArchive.snapshot(player,"",0);BuildingArchive.action(player,new ArchiveNetwork.Request("aim","",before.getLong("Revision"),0));var preview=BuildingArchive.snapshot(player,"",0);h.assertTrue(preview.getBoolean("Preview")&&preview.getInt("AnchorY")==target.getY()+1,"Remote packet produces projection: "+preview+" expected Y "+(target.getY()+1));
        h.assertBlockPresent(Blocks.AIR,1,3,1);BuildingArchive.action(player,new ArchiveNetwork.Request("move","0,5,0",preview.getLong("Revision"),0));var moved=BuildingArchive.snapshot(player,"",0);h.assertTrue(moved.getInt("AnchorY")==target.getY()+6,"Move changes only pending anchor");h.assertBlockPresent(Blocks.AIR,1,3,1);h.assertBlockPresent(Blocks.STONE,1,2,1);
        BuildingArchive.action(player,new ArchiveNetwork.Request("cancel","",moved.getLong("Revision"),0));h.assertTrue(!BuildingArchive.snapshot(player,"",0).getCompound("Projection").getBoolean("Visible"),"Cancel clears projection");h.succeed();
    }
    @GameTest(template="empty") public static void switchBuildingAfterPlacement(GameTestHelper h) throws Exception {
        var player=h.makeMockServerPlayerInLevel();player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        var d=player.server.getCommands().getDispatcher();var a=p(h,1,2,1);var b=p(h,2,2,1);h.setBlock(1,2,1,Blocks.STONE);h.setBlock(2,2,1,Blocks.GOLD_BLOCK);
        String first="switch_a_"+System.nanoTime(),second="switch_b_"+System.nanoTime();
        player.setPos(a.getX(),a.getY(),a.getZ());d.execute("ba pos1",player.createCommandSourceStack());d.execute("ba pos2",player.createCommandSourceStack());d.execute("ba export "+first,player.createCommandSourceStack());
        player.setPos(b.getX(),b.getY(),b.getZ());d.execute("ba pos1",player.createCommandSourceStack());d.execute("ba pos2",player.createCommandSourceStack());d.execute("ba export "+second,player.createCommandSourceStack());
        d.execute("ba load "+first,player.createCommandSourceStack());player.setPos(a.getX(),a.getY()+2,a.getZ());d.execute("ba preview",player.createCommandSourceStack());gui(player,"paste");
        var placed=BuildingArchive.snapshot(player,"",0);h.assertTrue(!placed.getBoolean("Loaded")&&!placed.getBoolean("Preview")&&placed.getBoolean("Undo"),"Paste leaves library ready while retaining undo");h.assertBlockPresent(Blocks.STONE,1,4,1);
        BuildingArchive.action(player,new ArchiveNetwork.Request("load",second,placed.getLong("Revision"),0));h.assertTrue(BuildingArchive.snapshot(player,"",0).getString("Name").equals(second),"Different record loads after paste");
        player.setPos(b.getX(),b.getY()+2,b.getZ());d.execute("ba preview",player.createCommandSourceStack());gui(player,"rotate_right");gui(player,"library");
        var list=BuildingArchive.snapshot(player,"",0);h.assertTrue(!list.getBoolean("Loaded")&&!list.getCompound("Projection").getBoolean("Visible")&&list.getBoolean("Undo"),"Switch clears projection but preserves undo");h.assertBlockPresent(Blocks.AIR,2,4,1);
        BuildingArchive.action(player,new ArchiveNetwork.Request("load",second,list.getLong("Revision"),0));d.execute("ba preview",player.createCommandSourceStack());gui(player,"paste");h.assertBlockPresent(Blocks.GOLD_BLOCK,2,4,1);
        gui(player,"undo");h.assertBlockPresent(Blocks.AIR,2,4,1);h.assertBlockPresent(Blocks.STONE,1,4,1);h.succeed();
    }
}

