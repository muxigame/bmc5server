package com.muxigame.buildingarchive;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.*;
import java.nio.file.*;

@GameTestHolder(BuildingArchive.ID) @PrefixGameTestTemplate(false)
public final class TerrainTests {
    static ServerPlayer player(GameTestHelper h){var p=h.makeMockServerPlayerInLevel();p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);return p;}
    static void select(GameTestHelper h,ServerPlayer p,int x,int y,int z){TerrainTool.select(p,h.absolutePos(new BlockPos(1,2,1)));TerrainTool.select(p,h.absolutePos(new BlockPos(x,y,z)));}
    static void action(ServerPlayer p,String op,String value){TerrainTool.handle(p,new ArchiveNetwork.Request(op,value,TerrainTool.state(p).revision,0));}
    @GameTest(template="empty") public static void replaceBackupAndUndo(GameTestHelper h) throws Exception{
        var p=player(h);h.setBlock(1,2,1,Blocks.STONE);h.setBlock(2,2,1,Blocks.DIRT);h.setBlock(3,2,1,Blocks.STONE);select(h,p,3,2,1);
        action(p,"terrain_target","minecraft:oak_planks");action(p,"terrain_preview","");h.assertTrue(TerrainTool.snapshot(p,"").getInt("Count")==2,"Only matching stone selected");h.assertBlockPresent(Blocks.STONE,1,2,1);
        action(p,"terrain_apply","");h.assertBlockPresent(Blocks.OAK_PLANKS,1,2,1);h.assertBlockPresent(Blocks.DIRT,2,2,1);
        Path backups=p.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("building_archive/backups");try(var files=Files.list(backups)){h.assertTrue(files.anyMatch(f->f.getFileName().toString().startsWith("terrain_"+p.getUUID())),"Disk backup before edit");}
        h.setBlock(3,2,1,Blocks.GOLD_BLOCK);int skipped=TerrainTool.undo(p);h.assertTrue(skipped==1,"Undo skips later edits");h.assertBlockPresent(Blocks.STONE,1,2,1);h.assertBlockPresent(Blocks.GOLD_BLOCK,3,2,1);h.succeed();
    }
    @GameTest(template="empty") public static void fillAirPreservesWaterAndContainers(GameTestHelper h){
        var p=player(h);h.setBlock(1,2,1,Blocks.AIR);h.setBlock(2,2,1,Blocks.WATER);h.setBlock(3,2,1,Blocks.CHEST);((ChestBlockEntity)h.getBlockEntity(new BlockPos(3,2,1))).setItem(0,new ItemStack(Items.DIAMOND,5));select(h,p,3,2,1);
        action(p,"terrain_mode","fill");action(p,"terrain_preview","");h.assertTrue(TerrainTool.snapshot(p,"").getInt("Count")==1,"Fill only air");action(p,"terrain_apply","");h.assertBlockPresent(Blocks.DIRT,1,2,1);h.assertBlockPresent(Blocks.WATER,2,2,1);h.assertTrue(((ChestBlockEntity)h.getBlockEntity(new BlockPos(3,2,1))).getItem(0).getCount()==5,"Container contents unchanged");
        action(p,"terrain_mode","replace");action(p,"terrain_source","minecraft:chest");action(p,"terrain_preview","");h.assertTrue(TerrainTool.snapshot(p,"").getInt("Count")==0,"Replacement skips containers");h.succeed();
    }
    @GameTest(template="empty") public static void cleanClutterAndRestore(GameTestHelper h){
        var p=player(h);h.setBlock(1,1,1,Blocks.DIRT);h.setBlock(2,1,1,Blocks.STONE);h.setBlock(1,2,1,Blocks.SHORT_GRASS);h.setBlock(2,2,1,Blocks.SNOW);h.setBlock(3,2,1,Blocks.OAK_LOG);select(h,p,3,2,1);action(p,"terrain_mode","clean");action(p,"terrain_preview","");h.assertTrue(TerrainTool.snapshot(p,"").getInt("Count")==2,"Plants and thin snow selected");action(p,"terrain_apply","");h.assertBlockPresent(Blocks.AIR,1,2,1);h.assertBlockPresent(Blocks.AIR,2,2,1);h.assertBlockPresent(Blocks.OAK_LOG,3,2,1);action(p,"terrain_undo","");h.assertBlockPresent(Blocks.SHORT_GRASS,1,2,1);h.assertBlockPresent(Blocks.SNOW,2,2,1);h.succeed();
    }
    @GameTest(template="empty") public static void changedAndStaleTerrainRejected(GameTestHelper h){
        var p=player(h);h.setBlock(1,2,1,Blocks.STONE);select(h,p,1,2,1);long old=TerrainTool.state(p).revision;action(p,"terrain_preview","");TerrainTool.handle(p,new ArchiveNetwork.Request("terrain_apply","",old,0));h.assertBlockPresent(Blocks.STONE,1,2,1);
        h.setBlock(1,2,1,Blocks.GOLD_BLOCK);action(p,"terrain_apply","");h.assertBlockPresent(Blocks.GOLD_BLOCK,1,2,1);
        action(p,"terrain_target","minecraft:chest");h.assertTrue(TerrainTool.state(p).target.equals("minecraft:dirt"),"Cannot fill containers");action(p,"terrain_target","minecraft:oak_door");h.assertTrue(TerrainTool.state(p).target.equals("minecraft:dirt"),"Cannot fill incomplete doors");
        TerrainTool.state(p).b=TerrainTool.state(p).a.offset(128,0,0);action(p,"terrain_preview","");h.assertTrue(TerrainTool.state(p).pending==null,"Oversized area rejected");h.succeed();
    }
    @GameTest(template="empty") public static void terrainPermissionAndExpiry(GameTestHelper h){
        var p=player(h);h.setBlock(1,2,1,Blocks.STONE);select(h,p,1,2,1);action(p,"terrain_preview","");TerrainTool.state(p).expires=p.level().getGameTime()-1;action(p,"terrain_apply","");h.assertBlockPresent(Blocks.STONE,1,2,1);
        var survival=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"terrain-survival"),net.minecraft.server.level.ClientInformation.createDefault());TerrainTool.select(survival,h.absolutePos(new BlockPos(1,2,1)));TerrainTool.select(survival,h.absolutePos(new BlockPos(1,2,1)));action(survival,"terrain_preview","");h.assertTrue(TerrainTool.state(survival).pending==null,"Unprivileged player denied");h.succeed();
    }
}


