package com.muxigame.buildingarchive;

import java.nio.file.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(BuildingArchive.ID)
@PrefixGameTestTemplate(false)
public final class FoundationTests {
    private static BlockPos p(GameTestHelper h,int x,int y,int z){return h.absolutePos(new BlockPos(x,y,z));}
    private static CompoundTag sample(GameTestHelper h){
        for(int y=2;y<=4;y++)for(int z=1;z<=5;z++)for(int x=1;x<=5;x++)h.setBlock(x,y,z,Blocks.AIR);
        for(int z=2;z<=4;z++)for(int x=2;x<=4;x++){
            h.setBlock(x,2,z,Blocks.STONE);
            if(x==2||x==4||z==2||z==4)h.setBlock(x,3,z,Blocks.STONE);
            h.setBlock(x,4,z,Blocks.STONE);
        }
        return ArchiveData.capture(h.getLevel(),p(h,1,2,1),new Vec3i(5,3,5));
    }
    private static void target(GameTestHelper h){for(int y=2;y<=4;y++)for(int z=1;z<=5;z++)for(int x=1;x<=5;x++)h.setBlock(x,y,z,Blocks.DIRT);}
    @GameTest(template="empty") public static void foundationPreservesTerrainAndClearsUpperAir(GameTestHelper h){
        var source=sample(h);var original=source.copy();h.assertTrue(ArchiveFoundation.detectLayers(source)==1,"Closed wall stops foundation before interior air; roof not included");
        var incoming=ArchiveFoundation.prepare(source,1);target(h);
        ArchiveData.place(h.getLevel(),p(h,1,2,1),incoming);
        h.assertBlockPresent(Blocks.DIRT,1,2,1);h.assertBlockPresent(Blocks.STONE,3,2,3);
        h.assertBlockPresent(Blocks.AIR,3,3,3);h.assertBlockPresent(Blocks.AIR,1,3,1);
        h.assertBlockPresent(Blocks.STONE,3,4,3);h.assertTrue(source.equals(original),"Authoritative source unchanged");h.succeed();
    }
    @GameTest(template="empty") public static void foundationPreservesSkippedContainersAndUndo(GameTestHelper h){
        var source=sample(h);target(h);h.setBlock(1,2,1,Blocks.CHEST);
        ChestBlockEntity chest=h.getBlockEntity(new BlockPos(1,2,1));chest.setItem(0,new ItemStack(Items.DIAMOND,7));
        var before=ArchiveData.capture(h.getLevel(),p(h,1,2,1),new Vec3i(5,3,5));
        ArchiveData.place(h.getLevel(),p(h,1,2,1),ArchiveFoundation.prepare(source,1));
        ChestBlockEntity kept=h.getBlockEntity(new BlockPos(1,2,1));h.assertTrue(kept.getItem(0).getCount()==7,"Skipped air must not pre-clear existing inventory");
        ArchiveData.place(h.getLevel(),p(h,1,2,1),before);h.assertBlockPresent(Blocks.DIRT,3,2,3);
        ChestBlockEntity restored=h.getBlockEntity(new BlockPos(1,2,1));h.assertTrue(restored.getItem(0).getCount()==7,"Full backup restores container");h.assertItemEntityNotPresent(Items.DIAMOND);h.succeed();
    }
    @GameTest(template="empty") public static void foundationOffRetainsOriginalAirBehavior(GameTestHelper h){
        var source=sample(h);target(h);ArchiveData.place(h.getLevel(),p(h,1,2,1),ArchiveFoundation.prepare(source,0));
        h.assertBlockPresent(Blocks.AIR,1,2,1);h.assertBlockPresent(Blocks.AIR,3,3,3);h.succeed();
    }
    @GameTest(template="empty") public static void foundationAirVariantsAndRotation(GameTestHelper h){
        var source=sample(h);var palette=source.getList("palette",Tag.TAG_COMPOUND);
        int a=palette.size();for(String name:new String[]{"minecraft:cave_air","minecraft:void_air"}){var state=new CompoundTag();state.putString("Name",name);palette.add(state);}
        int i=0;for(var value:source.getList("blocks",Tag.TAG_COMPOUND)){var b=(CompoundTag)value;if(ArchiveFoundation.air(palette.getCompound(b.getInt("state"))))b.putInt("state",a+(i++%2));}
        var lookup=h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK);
        var transformed=ArchiveData.mirror(ArchiveData.rotate(source,lookup,true),lookup);
        h.assertTrue(ArchiveFoundation.detectLayers(transformed)==1,"Rotation and mirror preserve cutoff");target(h);
        ArchiveData.place(h.getLevel(),p(h,1,2,1),ArchiveFoundation.prepare(transformed,1));
        h.assertBlockPresent(Blocks.DIRT,1,2,1);h.assertTrue(h.getLevel().getBlockState(p(h,3,3,3)).isAir(),"Upper cave/void air clears target");h.succeed();
    }
    @GameTest(template="empty") public static void foundationRejectsScatteredAndNonCubeLayers(GameTestHelper h){
        var source=sample(h);h.setBlock(3,2,3,Blocks.AIR);var hole=ArchiveData.capture(h.getLevel(),p(h,1,2,1),new Vec3i(5,3,5));
        h.assertTrue(ArchiveFoundation.detectLayers(hole)==0,"Enclosed bottom air is not a solid foundation");
        sample(h);for(int z=2;z<=4;z++)h.setBlock(3,2,z,Blocks.AIR);
        var split=ArchiveData.capture(h.getLevel(),p(h,1,2,1),new Vec3i(5,3,5));h.assertTrue(ArchiveFoundation.detectLayers(split)==0,"Disconnected columns rejected");
        sample(h);h.setBlock(3,2,3,Blocks.OAK_SLAB);var slab=ArchiveData.capture(h.getLevel(),p(h,1,2,1),new Vec3i(5,3,5));h.assertTrue(ArchiveFoundation.detectLayers(slab)==0,"Partial cube requires manual cutoff");
        sample(h);h.setBlock(3,2,3,Blocks.WATER);var water=ArchiveData.capture(h.getLevel(),p(h,1,2,1),new Vec3i(5,3,5));h.assertTrue(ArchiveFoundation.detectLayers(water)==0,"Fluid layer is not solid foundation");h.succeed();
    }
    @GameTest(template="empty") public static void foundationBottomPaddingAndManualBounds(GameTestHelper h){
        sample(h);for(int z=1;z<=5;z++)for(int x=1;x<=5;x++)h.setBlock(x,1,z,Blocks.AIR);
        var padded=ArchiveData.capture(h.getLevel(),p(h,1,1,1),new Vec3i(5,4,5));h.assertTrue(ArchiveFoundation.detectLayers(padded)==2,"Leading blank padding included only when followed by foundation");
        for(int invalid:new int[]{-1,5}){boolean rejected=false;try{ArchiveFoundation.prepare(padded,invalid);}catch(IllegalArgumentException e){rejected=true;}h.assertTrue(rejected,"Manual bounds checked before mutation");}
        h.succeed();
    }
    @GameTest(template="empty") public static void foundationGuiActionsCommandPlacementAndDiskUndo(GameTestHelper h) throws Exception {
        sample(h);var player=h.makeMockServerPlayerInLevel();player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);var d=player.server.getCommands().getDispatcher();
        player.setPos(p(h,1,2,1).getX()+.5,p(h,1,2,1).getY(),p(h,1,2,1).getZ()+.5);d.execute("ba pos1",player.createCommandSourceStack());
        player.setPos(p(h,5,4,5).getX()+.5,p(h,5,4,5).getY(),p(h,5,4,5).getZ()+.5);d.execute("ba pos2",player.createCommandSourceStack());
        String name="foundation_test_"+System.nanoTime();d.execute("ba export "+name,player.createCommandSourceStack());
        Path root=player.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("building_archive");byte[] original=Files.readAllBytes(root.resolve(name+".nbt"));
        try{
            var t=BuildingArchive.snapshot(player,"",0);long revision=t.getLong("Revision");h.assertTrue(t.getInt("FoundationLayers")==1&&!t.getBoolean("PreserveFoundation"),"Default off, detected layer published");
            BuildingArchive.action(player,new ArchiveNetwork.Request("foundation_mode","on",revision-1,0));h.assertTrue(!BuildingArchive.snapshot(player,"",0).getBoolean("PreserveFoundation"),"Stale toggle rejected");
            BuildingArchive.action(player,new ArchiveNetwork.Request("foundation_mode","on",revision,0));t=BuildingArchive.snapshot(player,"",0);h.assertTrue(t.getBoolean("PreserveFoundation"),"GUI toggle reaches server");
            BuildingArchive.action(player,new ArchiveNetwork.Request("foundation_top","4",t.getLong("Revision"),0));t=BuildingArchive.snapshot(player,"",0);h.assertTrue(t.getInt("FoundationLayers")==1,"Invalid height rejected");
            BuildingArchive.action(player,new ArchiveNetwork.Request("foundation_top","2",t.getLong("Revision"),0));t=BuildingArchive.snapshot(player,"",0);h.assertTrue(t.getInt("FoundationLayers")==2,"Manual height accepted");
            BuildingArchive.action(player,new ArchiveNetwork.Request("foundation_auto","",t.getLong("Revision"),0));h.assertTrue(BuildingArchive.snapshot(player,"",0).getInt("FoundationLayers")==1,"Automatic reset recomputes layers");
            target(h);player.setPos(p(h,1,2,1).getX()+.5,p(h,1,2,1).getY(),p(h,1,2,1).getZ()+.5);d.execute("ba preview",player.createCommandSourceStack());
            t=BuildingArchive.snapshot(player,"",0);h.assertTrue(t.getInt("Occupied")==59,"Collision preview excludes 16 protected air cells");
            h.assertTrue(d.execute("ba paste replace",player.createCommandSourceStack())==1,"Existing command honors mode");h.assertBlockPresent(Blocks.DIRT,1,2,1);h.assertBlockPresent(Blocks.AIR,3,3,3);
            d.execute("ba undo",player.createCommandSourceStack());h.assertBlockPresent(Blocks.DIRT,3,3,3);h.assertBlockPresent(Blocks.DIRT,3,2,3);
            h.assertTrue(java.util.Arrays.equals(original,Files.readAllBytes(root.resolve(name+".nbt"))),"Mode never rewrites archive");
            try(var paths=Files.list(root.resolve("backups"))){h.assertTrue(paths.anyMatch(f->f.getFileName().toString().startsWith("undo_"+player.getUUID())),"Disk backup written before paste");}
        }finally{Files.deleteIfExists(root.resolve(name+".nbt"));Files.deleteIfExists(root.resolve(name+".analysis.json"));}
        h.succeed();
    }
}
