package com.muxigame.city;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CityMod.ID)
@PrefixGameTestTemplate(false)
public final class CityTests {
    @GameTest(template="empty") public static void npcCityTransferIsAtomic(GameTestHelper h){
        CityData d=new CityData();var p=h.makeMockServerPlayerInLevel();p.setGameMode(GameType.CREATIVE);UUID npc=UUID.randomUUID();BlockPos pos=h.absolutePos(new BlockPos(1,2,1));
        var a=d.create(p.getUUID(),"甲",h.getLevel().dimension().location().toString(),CityBounds.between(pos,pos));var b=d.create(p.getUUID(),"乙",a.dimension,CityBounds.between(pos.east(2),pos.east(2)));
        CityNpcBridge.assign(d,p,npc,a.id.toString(),a.revision,"",0);long old=a.revision;
        reject(h,()->CityNpcBridge.assign(d,p,npc,b.id.toString(),b.revision,"",0),"Concurrent home change rejected");
        reject(h,()->CityNpcBridge.assign(d,p,npc,b.id.toString(),b.revision+1,a.id.toString(),old),"Stale destination rejected");
        h.assertTrue(a.residents.contains(npc)&&b.residents.isEmpty(),"Failed transfer leaves both cities intact");
        CityNpcBridge.assign(d,p,npc,b.id.toString(),b.revision,a.id.toString(),old);h.assertTrue(!a.residents.contains(npc)&&b.residents.contains(npc),"Transfer updates single source of truth");
        CityNpcBridge.assign(d,p,npc,"",0,b.id.toString(),b.revision);h.assertTrue(CityNpcBridge.home(d,npc)==null,"Unbind removes membership");
        var other=d.create(UUID.randomUUID(),"他人",a.dimension,CityBounds.between(pos.east(4),pos.east(4)));other.residents.add(npc);
        reject(h,()->CityNpcBridge.assign(d,p,npc,a.id.toString(),a.revision,other.id.toString(),other.revision),"Cannot steal resident of another owner");h.assertTrue(other.residents.contains(npc),"Denied transfer preserves owner");h.succeed();
    }
    @GameTest(template="empty") public static void separateWandDrafts(GameTestHelper h)throws Exception{
        var p=h.makeMockServerPlayerInLevel();var city=CityMod.class.getDeclaredMethod("session",net.minecraft.server.level.ServerPlayer.class);city.setAccessible(true);var building=CityMod.class.getDeclaredMethod("buildingDraft",net.minecraft.server.level.ServerPlayer.class);building.setAccessible(true);
        Object c=city.invoke(null,p),b=building.invoke(null,p);h.assertTrue(c!=b,"City and building draft objects differ");
        BlockPos start=h.absolutePos(new BlockPos(1,2,1));for(Object draft:List.of(c,b)){var a=draft.getClass().getDeclaredField("a");a.setAccessible(true);var end=draft.getClass().getDeclaredField("b");end.setAccessible(true);a.set(draft,draft==c?start:start.east());end.set(draft,draft==c?start:start.east());}
        var select=CityMod.class.getDeclaredMethod("selection",net.minecraft.server.level.ServerPlayer.class,c.getClass());select.setAccessible(true);
        CityBounds cb=(CityBounds)select.invoke(null,p,c),bb=(CityBounds)select.invoke(null,p,b);h.assertTrue(cb.min().getX()==start.getX()&&bb.min().getX()==start.getX()+1,"Separate selections preserve coordinates");
        var field=b.getClass().getDeclaredField("a");field.setAccessible(true);field.set(b,null);h.assertTrue(((CityBounds)select.invoke(null,p,c)).equals(cb),"Clearing building draft preserves city draft");h.succeed();
    }
    @GameTest(template="empty") public static void registryRoundTripAndMigration(GameTestHelper h){
        CityData d=new CityData();var c=city(d,h);BlockPos pos=c.bounds.min();
        CityRegistry.building(d,c,"小屋","住宅",CityBounds.between(pos,pos));
        h.getLevel().setBlock(pos,Blocks.CHEST.defaultBlockState(),3);var chest=(ChestBlockEntity)h.getLevel().getBlockEntity(pos);chest.setItem(0,new ItemStack(Items.APPLE,11));
        CityRegistry.warehouse(d,c,h.getLevel(),"粮仓",pos);c.residents.add(UUID.randomUUID());
        var loaded=CityData.load(d.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess()).require(c.id);
        h.assertTrue(loaded.buildings.equals(c.buildings)&&loaded.warehouses.equals(c.warehouses)&&loaded.residents.equals(c.residents),"All registrations survive save/load");
        var json=CityBlueprints.export(c);json.getAsJsonObject("registry").getAsJsonArray("buildings").get(0).getAsJsonObject().addProperty("name","改名");
        CityBlueprints.apply(d,c,h.getLevel(),json,c.revision);h.assertTrue(c.buildings.values().iterator().next().name().equals("改名"),"JSON edits building metadata");
        h.assertTrue(chest.getItem(0).getCount()==11,"Binding and import do not consume inventory");
        CompoundTag old=d.save(new CompoundTag(),h.getLevel().registryAccess());old.putInt("Schema",1);old.getList("Cities",10).getCompound(0).remove("Registry");old.getList("Cities",10).getCompound(0).remove("RegistryUtf8");var migrated=CityData.load(old,h.getLevel().registryAccess()).require(c.id);h.assertTrue(migrated.buildings.isEmpty()&&migrated.residents.equals(c.residents),"Schema 1 keeps existing city and residents");h.succeed();
    }
    @GameTest(template="empty") public static void registryValidationAndAtomicImport(GameTestHelper h){
        CityData d=new CityData();var c=city(d,h);BlockPos pos=c.bounds.min();CityRegistry.building(d,c,"小屋","住宅",CityBounds.between(pos,pos));
        reject(h,()->CityRegistry.building(d,c,"重叠","住宅",CityBounds.between(pos,pos)),"Duplicate building rejected");
        reject(h,()->CityRegistry.building(d,c,"越界","住宅",CityBounds.between(c.bounds.max().above(),c.bounds.max().above())),"Outside city rejected");
        c.excluded.add(pos.east().asLong());reject(h,()->CityRegistry.building(d,c,"排除","住宅",CityBounds.between(pos.east(),pos.east())),"Excluded space rejected");
        reject(h,()->CityRegistry.warehouse(d,c,h.getLevel(),"假容器",pos),"Non-container rejected");
        var before=CityBlueprints.export(c);var bad=before.deepCopy();bad.addProperty("name","不得应用");bad.getAsJsonObject("registry").getAsJsonArray("buildings").get(0).getAsJsonObject().addProperty("type","非法用途");
        reject(h,()->CityBlueprints.apply(d,c,h.getLevel(),bad,c.revision),"Invalid registry rejected atomically");h.assertTrue(CityBlueprints.export(c).equals(before),"Invalid import leaves every field intact");
        reject(h,()->CityRegistry.validateBounds(c,CityBounds.between(pos.east(),c.bounds.max())),"Resize cannot orphan buildings");h.succeed();
    }
    @GameTest(template="empty") public static void warehouseStatusAndNpcIdentity(GameTestHelper h){
        CityData d=new CityData();var c=city(d,h);BlockPos pos=c.bounds.min();var player=h.makeMockServerPlayerInLevel();
        h.getLevel().setBlock(pos,Blocks.CHEST.defaultBlockState(),3);CityRegistry.warehouse(d,c,h.getLevel(),"仓库",pos);
        reject(h,()->CityRegistry.warehouse(d,c,h.getLevel(),"重复",pos),"Duplicate storage rejected");
        h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),3);CompoundTag tag=new CompoundTag();CityRegistry.snapshot(player,c,tag);h.assertTrue(tag.getList("Warehouses",10).getCompound(0).getString("Detail").contains("已移除"),"Missing container retains registration with explicit status");
        reject(h,()->CityRegistry.bind(d,c,player,UUID.randomUUID()),"Missing NPC cannot be bound through gameplay");h.assertTrue(c.residents.isEmpty(),"Rejected binding does not change residents");h.succeed();
    }
    @GameTest(template="empty") public static void deleteCityPreservesWorldAndOtherCities(GameTestHelper h){
        CityData d=new CityData();var player=h.makeMockServerPlayerInLevel();player.setGameMode(GameType.SURVIVAL);
        BlockPos pos=h.absolutePos(new BlockPos(1,2,1));h.getLevel().setBlock(pos,Blocks.STONE.defaultBlockState(),3);
        var c=d.create(player.getUUID(),"删除测试",h.getLevel().dimension().location().toString(),CityBounds.between(pos,pos));c.residents.add(UUID.randomUUID());
        var other=d.create(player.getUUID(),"保留",c.dimension,CityBounds.between(pos.east(3),pos.east(3)));
        CityService.membership(d,c,player.getUUID(),List.of(pos),false);CityService.scan(c);
        reject(h,()->CityService.delete(d,c,player,c.revision-1,c.name),"Stale delete rejected");
        reject(h,()->CityService.delete(d,c,player,c.revision,"错误城市"),"Wrong confirmation rejected");
        h.assertTrue(d.cities.containsKey(c.id),"Invalid deletion leaves city intact");
        CityService.delete(d,c,player,c.revision,c.name);
        h.assertTrue(!d.cities.containsKey(c.id)&&d.require(other.id)==other,"Only requested city removed");
        h.assertTrue(!CityService.scanning(c.id)&&CityService.statistics(c.id)==null,"Scan and cached statistics removed");
        reject(h,()->CityService.undo(d,c,h.getLevel(),player.getUUID()),"Deleted city undo removed");
        h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.STONE),"Real world building untouched");
        var loaded=CityData.load(d.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());h.assertTrue(loaded.cities.size()==1&&loaded.cities.containsKey(other.id),"Deletion survives save/load");
        d.create(player.getUUID(),"重新使用原区域",c.dimension,CityBounds.between(pos,pos));h.succeed();
    }
    @GameTest(template="empty") public static void deletionRequiresOwner(GameTestHelper h){
        var player=h.makeMockServerPlayerInLevel();player.setGameMode(GameType.SURVIVAL);CityData d=new CityData();var c=city(d,h);
        reject(h,()->CityService.delete(d,c,player,c.revision,c.name),"Unrelated survival player cannot delete");h.assertTrue(d.cities.containsKey(c.id),"Permission rejection preserves city");h.succeed();
    }
    private static void reject(GameTestHelper h,Runnable action,String why){boolean bad=false;try{action.run();}catch(IllegalArgumentException e){bad=true;}h.assertTrue(bad,why);}
    private static CityData.City city(CityData d,GameTestHelper h){return d.create(UUID.randomUUID(),"测试城",h.getLevel().dimension().location().toString(),CityBounds.between(h.absolutePos(new BlockPos(1,2,1)),h.absolutePos(new BlockPos(3,3,3))));}
    @GameTest(template="empty") public static void boundsInclusiveAndOverflow(GameTestHelper h){
        CityBounds b=CityBounds.between(new BlockPos(-3,-4,-5),new BlockPos(-5,-6,-7));h.assertTrue(b.volume()==27,"Inclusive reversed negative coordinates");
        Set<BlockPos> positions=new HashSet<>();for(int i=0;i<b.volume();i++){h.assertTrue(b.contains(b.at(i)),"Indexed point within bounds");positions.add(b.at(i));}h.assertTrue(positions.size()==27,"No duplicate coordinates");
        reject(h,()->CityBounds.between(new BlockPos(Integer.MIN_VALUE,0,0),new BlockPos(Integer.MAX_VALUE,0,0)),"Overflow rejected");
        h.assertTrue(CityBounds.between(BlockPos.ZERO,new BlockPos(127,127,127)).volumeLong()==2097152L,"Large city volume allowed");
        reject(h,()->CityBounds.between(BlockPos.ZERO,new BlockPos(127,127,127)).validateBuilding(),"Single-building operation remains bounded");
        h.assertTrue(new CityBounds(BlockPos.ZERO,new BlockPos(128,0,0)).volumeLong()==129L,"City axes are not capped at128");h.succeed();
    }
    @GameTest(template="empty") public static void overlapAndDimensions(GameTestHelper h){
        CityData d=new CityData();CityData.City c=city(d,h);
        reject(h,()->d.create(UUID.randomUUID(),"冲突",c.dimension,c.bounds),"Same-dimension overlap");
        d.create(UUID.randomUUID(),"另一个维度","minecraft:the_nether",c.bounds);h.assertTrue(d.cities().size()==2,"Dimension separation");
        reject(h,()->CityData.validName("\n"),"Empty name");h.succeed();
    }
    @GameTest(template="empty") public static void membershipUndoAndConcurrency(GameTestHelper h){
        CityData d=new CityData();CityData.City c=city(d,h);BlockPos p=c.bounds.min();UUID actor=UUID.randomUUID();
        var worldBefore=h.getLevel().getBlockState(p);CityService.membership(d,c,actor,List.of(p),false);h.assertTrue(!c.includes(p),"Excluded");h.assertTrue(h.getLevel().getBlockState(p).equals(worldBefore),"Membership leaves world unchanged");
        CityService.undo(d,c,h.getLevel(),actor);h.assertTrue(c.includes(p),"Undo membership");
        CityService.membership(d,c,actor,List.of(p),false);d.changed(c);reject(h,()->CityService.undo(d,c,h.getLevel(),actor),"Reject stale undo");
        reject(h,()->CityService.membership(d,c,actor,List.of(p,c.bounds.max().above()),false),"Atomic bounds validation");h.assertTrue(c.includes(c.bounds.max()),"No partial invalid edit");h.succeed();
    }
    @GameTest(template="empty") public static void persistenceRoundTrip(GameTestHelper h){
        CityData d=new CityData();CityData.City c=city(d,h);c.excluded.add(c.bounds.min().asLong());UUID npc=UUID.randomUUID();c.residents.add(npc);d.changed(c);
        CompoundTag tag=d.save(new CompoundTag(),h.getLevel().registryAccess());CityData restored=CityData.load(tag,h.getLevel().registryAccess());CityData.City result=restored.require(c.id);
        h.assertTrue(!result.includes(c.bounds.min())&&result.residents().contains(npc)&&result.revision==c.revision&&result.owner.equals(c.owner),"Persistence retains ownership, mask, residents, revision");
        tag.putInt("Schema",999);reject(h,()->CityData.load(tag,h.getLevel().registryAccess()),"Future schema rejected");h.succeed();
    }
    @GameTest(template="empty") public static void permissionsAndRevision(GameTestHelper h){
        var p=h.makeMockServerPlayerInLevel();p.setGameMode(GameType.CREATIVE);CityData d=new CityData();CityData.City c=city(d,h);
        reject(h,()->CityService.checkEditor(p,c,c.revision),"Other creative player cannot edit");
        CityData.City own=new CityData.City(UUID.randomUUID(),p.getUUID(),"本人",c.dimension,c.bounds);CityService.checkEditor(p,own,0);
        reject(h,()->CityService.checkEditor(p,own,1),"Stale request rejected");
        var survival=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"survival-test"),net.minecraft.server.level.ClientInformation.createDefault()){@Override public boolean isCreative(){return false;}};
        CityData.City survivalCity=new CityData.City(UUID.randomUUID(),survival.getUUID(),"生存",c.dimension,c.bounds);
        reject(h,()->CityService.checkEditor(survival,survivalCity,0),"Survival ownership does not grant access to planning tools");h.assertTrue(!CityService.editor(survival),"Survival ownership does not grant world editing");h.succeed();
    }
    @GameTest(template="empty") public static void blockReplaceAndUndo(GameTestHelper h){
        CityData d=new CityData();CityData.City c=city(d,h);BlockPos p=c.bounds.min();UUID actor=UUID.randomUUID();h.getLevel().setBlock(p,Blocks.STONE.defaultBlockState(),3);
        CityService.replace(d,c,h.getLevel(),actor,p,Block.getId(Blocks.STONE.defaultBlockState()),"minecraft:gold_block");h.assertTrue(h.getLevel().getBlockState(p).is(Blocks.GOLD_BLOCK),"World edit applied");
        CityService.undo(d,c,h.getLevel(),actor);h.assertTrue(h.getLevel().getBlockState(p).is(Blocks.STONE),"World edit undo");
        reject(h,()->CityService.replace(d,c,h.getLevel(),actor,p,Block.getId(Blocks.DIRT.defaultBlockState()),"minecraft:gold_block"),"Stale world snapshot rejected");
        reject(h,()->CityService.replace(d,c,h.getLevel(),actor,p,Block.getId(Blocks.STONE.defaultBlockState()),"missing:unknown"),"Unknown registry entry rejected");h.succeed();
    }
    @GameTest(template="empty") public static void containersAndPhysicsProtected(GameTestHelper h){
        CityData d=new CityData();CityData.City c=city(d,h);BlockPos p=c.bounds.min();h.getLevel().setBlock(p,Blocks.CHEST.defaultBlockState(),3);
        ChestBlockEntity chest=(ChestBlockEntity)h.getLevel().getBlockEntity(p);chest.setItem(0,new ItemStack(Items.DIAMOND,7));
        reject(h,()->CityService.replace(d,c,h.getLevel(),UUID.randomUUID(),p,Block.getId(h.getLevel().getBlockState(p)),"minecraft:stone"),"Container edit denied");h.assertTrue(chest.getItem(0).getCount()==7,"Contents preserved");
        h.getLevel().setBlock(p,Blocks.STONE.defaultBlockState(),3);int id=Block.getId(Blocks.STONE.defaultBlockState());
        for(String block:List.of("minecraft:sand","minecraft:water","minecraft:oak_door","minecraft:chest"))reject(h,()->CityService.replace(d,c,h.getLevel(),UUID.randomUUID(),p,id,block),"Unsafe replacement denied "+block);h.succeed();
    }
    @GameTest(template="empty") public static void worldUndoConflict(GameTestHelper h){
        CityData d=new CityData();CityData.City c=city(d,h);BlockPos p=c.bounds.min();UUID actor=UUID.randomUUID();h.getLevel().setBlock(p,Blocks.STONE.defaultBlockState(),3);
        CityService.replace(d,c,h.getLevel(),actor,p,Block.getId(Blocks.STONE.defaultBlockState()),"minecraft:gold_block");h.getLevel().setBlock(p,Blocks.DIAMOND_BLOCK.defaultBlockState(),3);
        reject(h,()->CityService.undo(d,c,h.getLevel(),actor),"Do not overwrite later world change");h.assertTrue(h.getLevel().getBlockState(p).is(Blocks.DIAMOND_BLOCK),"World conflict preserved");h.succeed();
    }
    @GameTest(template="empty") public static void statisticsAndNpcApi(GameTestHelper h){
        CityData d=CityData.get(h.getLevel().getServer());CityData.City c=city(d,h);BlockPos p=c.bounds.min();UUID npc=UUID.randomUUID();c.residents.add(npc);
        try{
            for(int i=0;i<c.bounds.volume();i++)h.getLevel().setBlock(c.bounds.at(i),Blocks.AIR.defaultBlockState(),2);
            h.getLevel().setBlock(p,Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(p.east(),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(p.south(),Blocks.OAK_PLANKS.defaultBlockState(),2);
            c.excluded.add(p.east().asLong());CityService.scan(c);for(int i=0;i<10&&CityService.scanning(c.id);i++)CityService.tick(h.getLevel().getServer());
            var stats=CityService.statistics(c.id);h.assertTrue(stats!=null&&stats.nonAir()==2&&stats.excluded()==1&&stats.blocks().get("minecraft:stone")==1&&stats.complete(),"Exact counts exclude air and excluded stone");
            h.assertTrue(CityApi.at(h.getLevel(),p).orElseThrow().id().equals(c.id)&&CityApi.at(h.getLevel(),p.east()).isEmpty(),"Position API respects exclusions");
            h.assertTrue(CityApi.home(h.getLevel().getServer(),npc).orElseThrow().id().equals(c.id),"NPC home API");
        }finally{d.cities.remove(c.id);d.setDirty();}h.succeed();
    }
    @GameTest(template="empty") public static void areaExpansionAndUndo(GameTestHelper h){
        CityData d=new CityData();CityData.City c=city(d,h);CityBounds old=c.bounds;UUID actor=UUID.randomUUID();BlockPos target=old.max().east(3);var state=h.getLevel().getBlockState(target);
        long n=CityService.area(d,c,h.getLevel(),actor,CityBounds.between(target,target),true);h.assertTrue(n==1&&c.includes(target)&&!c.includes(target.west()),"Annex preserves unselected gap");h.assertTrue(h.getLevel().getBlockState(target).equals(state),"No world edit");
        CityService.undo(d,c,h.getLevel(),actor);h.assertTrue(c.bounds.equals(old)&&c.excluded.isEmpty(),"Undo restores original mask and bounds");
        d.create(UUID.randomUUID(),"邻城",c.dimension,CityBounds.between(target,target));reject(h,()->CityService.area(d,c,h.getLevel(),actor,CityBounds.between(target,target),true),"Neighbor overlap denied");h.assertTrue(c.bounds.equals(old),"Rejected annex atomic");
        CityService.area(d,c,h.getLevel(),actor,old,false);h.assertTrue(CityAreas.volume(c)==0,"Whole volume excluded without point expansion");CityService.undo(d,c,h.getLevel(),actor);h.assertTrue(CityAreas.volume(c)==old.volumeLong(),"Whole volume undo");h.succeed();
    }
    @GameTest(template="empty") public static void blueprintValidation(GameTestHelper h){
        CityData d=new CityData();CityData.City c=city(d,h);var j=CityBlueprints.export(c);j.addProperty("name","数据城");j.getAsJsonArray("excluded").add(CityBlueprints.pos(c.bounds.min()));CityBlueprints.apply(d,c,h.getLevel(),j,0);h.assertTrue(c.name.equals("数据城")&&!c.includes(c.bounds.min())&&c.revision==1,"JSON applies all fields atomically");
        reject(h,()->CityBlueprints.apply(d,c,h.getLevel(),j,0),"Stale import denied");var invalid=CityBlueprints.export(c);invalid.addProperty("name","不可保存");invalid.getAsJsonArray("excluded").add(CityBlueprints.pos(c.bounds.max().above()));reject(h,()->CityBlueprints.apply(d,c,h.getLevel(),invalid,1),"Invalid mask denied");h.assertTrue(c.name.equals("数据城")&&c.revision==1,"No partial JSON mutation");h.succeed();
    }
    @GameTest(template="empty") public static void groundCornersIncludeRoofAndDecorations(GameTestHelper h){
        var level=h.getLevel();BlockPos a=h.absolutePos(new BlockPos(1,2,1)),b=h.absolutePos(new BlockPos(3,2,3));CityBounds floor=CitySelection.manual(level,a,b,a.getY(),a.getY());
        for(int i=0;i<floor.volume();i++){BlockPos p=floor.at(i);for(int y=p.getY();y<level.getMaxBuildHeight();y++)level.setBlock(new BlockPos(p.getX(),y,p.getZ()),Blocks.AIR.defaultBlockState(),2);level.setBlock(p,Blocks.STONE.defaultBlockState(),2);}
        BlockPos roof=floor.min().offset(1,8,1),decoration=floor.max().above(10);level.setBlock(roof,Blocks.GLASS.defaultBlockState(),2);level.setBlock(decoration,Blocks.OAK_LEAVES.defaultBlockState(),2);
        CityBounds auto=CitySelection.automatic(level,b,a);h.assertTrue(auto.min().getY()==a.getY()&&auto.max().getY()==decoration.getY()&&auto.contains(roof),"Ground corners include roof, transparent blocks and decorations");
        level.setBlock(decoration,Blocks.AIR.defaultBlockState(),2);CityBounds refreshed=CitySelection.automatic(level,a,b);h.assertTrue(refreshed.max().getY()==roof.getY(),"Refresh uses current building height");
        CityData d=new CityData();var c=d.create(UUID.randomUUID(),"自动高度",level.dimension().location().toString(),refreshed);h.assertTrue(c.includes(roof),"Registered city includes roof");CityService.area(d,c,level,c.owner,refreshed,false);h.assertTrue(!c.includes(roof),"Area stamp shares resolved height");CityService.undo(d,c,level,c.owner);h.assertTrue(c.includes(roof),"Height-aware area undo");h.succeed();
    }
    @GameTest(template="empty") public static void manualHeightAndWorldLimits(GameTestHelper h){
        var level=h.getLevel();BlockPos a=h.absolutePos(new BlockPos(1,3,1)),b=h.absolutePos(new BlockPos(3,3,3));CityBounds manual=CitySelection.manual(level,a,b,a.getY()-2,a.getY()+5);
        h.assertTrue(manual.min().getY()==a.getY()-2&&manual.max().getY()==a.getY()+5&&manual.volume()==72,"Manual includes basement and chosen upper floor");
        reject(h,()->CitySelection.manual(level,a,b,5,4),"Inverted height rejected");reject(h,()->CitySelection.manual(level,a,b,level.getMinBuildHeight()-1,level.getMinBuildHeight()),"Below world rejected");h.assertTrue(CitySelection.manual(level,a,b,a.getY(),a.getY()+128).max().getY()==a.getY()+128,"City height remains valid within actual world bounds");reject(h,()->CitySelection.manual(level,a,b,a.getY(),level.getMaxBuildHeight()),"Actual world height remains enforced");h.succeed();
    }
    @GameTest(template="empty") public static void autoHeightIncludesTallRoofWithoutClipping(GameTestHelper h){
        var level=h.getLevel();BlockPos a=h.absolutePos(new BlockPos(1,2,1));level.setBlock(a.above(128),Blocks.STONE.defaultBlockState(),2);
        try{h.assertTrue(CitySelection.automatic(level,a,a).max().getY()>=a.getY()+128,"High roof is included rather than clipped by the retired128 cap");}finally{level.setBlock(a.above(128),Blocks.AIR.defaultBlockState(),2);}h.succeed();
    }
    @GameTest(template="empty") public static void autoHeightDoesNotLoadChunks(GameTestHelper h){
        var level=h.getLevel();BlockPos a=new BlockPos(1244567,70,1244567);h.assertTrue(!level.hasChunkAt(a),"Initial missing chunk");reject(h,()->CitySelection.automatic(level,a,a.east(16)),"Auto requires all chunks loaded");h.assertTrue(!level.hasChunkAt(a)&&!level.hasChunkAt(a.east(16)),"No chunk generated by height detection");CitySelection.manual(level,a,a,70,80);h.assertTrue(!level.hasChunkAt(a),"Manual fallback also avoids loading");h.succeed();
    }
    @GameTest(template="empty") public static void overlayReflectsExactMembership(GameTestHelper h){
        CityData d=new CityData();CityData.City c=city(d,h);BlockPos hole=c.bounds.min();c.excluded.add(hole.asLong());
        var runs=CityOverlay.runs(c,hole);java.util.Set<Long> seen=new java.util.HashSet<>();int excluded=0;
        for(var tag:runs){var t=(net.minecraft.nbt.CompoundTag)tag;CityBounds b=new CityBounds(BlockPos.of(t.getLong("Min")),BlockPos.of(t.getLong("Max")));for(int i=0;i<b.volume();i++){BlockPos p=b.at(i);h.assertTrue(seen.add(p.asLong()),"Overlay never duplicates spaces");h.assertTrue(c.includes(p)==t.getBoolean("Included"),"Overlay exactly matches membership, including holes");if(!t.getBoolean("Included"))excluded++;}}
        h.assertTrue(seen.size()==c.bounds.volume()&&excluded==1,"All included and excluded spaces represented");h.assertTrue(CityOverlay.runs(c,hole.offset(500,0,500)).isEmpty(),"Far cities omit local detail");h.succeed();
    }
    @GameTest(template="empty") public static void overlayBudget(GameTestHelper h){
        CityData d=new CityData();BlockPos a=h.absolutePos(new BlockPos(1,1,1));var c=d.create(UUID.randomUUID(),"显示预算",h.getLevel().dimension().location().toString(),CityBounds.between(a,a.offset(31,31,31)));
        for(int i=0;i<c.bounds.volume();i++){var p=c.bounds.at(i);if((p.getY()&1)==0)c.excluded.add(p.asLong());}
        var runs=CityOverlay.runs(c,a.offset(16,16,16));h.assertTrue(runs.size()<=4913,"Worst case bounded to 17 cubed spaces");int count=0;for(var tag:runs){var t=(net.minecraft.nbt.CompoundTag)tag;count+=new CityBounds(BlockPos.of(t.getLong("Min")),BlockPos.of(t.getLong("Max"))).volume();}h.assertTrue(count==4913,"Nearby detail cube complete");h.succeed();
    }
    @GameTest(template="empty") public static void unloadedChunksNotGenerated(GameTestHelper h){
        CityData d=CityData.get(h.getLevel().getServer());BlockPos p=new BlockPos(1234567,70,1234567);CityData.City c=d.create(UUID.randomUUID(),"远端",h.getLevel().dimension().location().toString(),CityBounds.between(p,p));
        try{h.assertTrue(!h.getLevel().hasChunkAt(p),"Chunk initially unloaded");CityService.scan(c);for(int i=0;i<10&&CityService.scanning(c.id);i++)CityService.tick(h.getLevel().getServer());
            var stats=CityService.statistics(c.id);h.assertTrue(stats!=null&&stats.unloaded()==1&&!stats.complete(),"Incomplete snapshot explicit");h.assertTrue(!h.getLevel().hasChunkAt(p),"Scan did not generate chunk");
        }finally{d.cities.remove(c.id);d.setDirty();}h.succeed();
    }
}
