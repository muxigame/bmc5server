package com.muxigame.city;

import java.util.*;
import java.nio.file.Files;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CityMod.ID)
@PrefixGameTestTemplate(false)
public final class CityTestsLargeAreas {
    private static CityBounds large(int x,int z){return new CityBounds(new BlockPos(x,64,z),new BlockPos(x+4095,191,z+4095));}
    private static void reject(GameTestHelper h,Runnable task,String message){boolean rejected=false;try{task.run();}catch(RuntimeException e){rejected=true;}h.assertTrue(rejected,message);}
    @GameTest(template="empty") public static void giantVolumeUsesLongWithoutAirAllocation(GameTestHelper h){
        var b=large(-4096,-4096);h.assertTrue(b.volumeLong()==2147483648L,"4096-square at128-high does not overflow int");
        h.assertTrue(b.at(b.volumeLong()-1).equals(b.max()),"Long cursor reaches final coordinate exactly");
        var d=new CityData();var c=d.create(UUID.randomUUID(),"巨大城市",h.getLevel().dimension().location().toString(),b);
        h.assertTrue(c.includes(b.min())&&c.includes(b.max())&&c.excluded.isEmpty()&&c.areas==null,"A giant rectangle uses two corners, not billions of records");
        reject(h,b::validateBuilding,"Single building work keeps independent bounded validation");h.succeed();
    }
    @GameTest(template="empty") public static void distantAnnexAndBillionCellCutAreSparseAndUndoable(GameTestHelper h){
        var d=new CityData();var origin=new BlockPos(5000000,64,5000000);var old=CityBounds.between(origin,origin.offset(31,7,31));var owner=UUID.randomUUID();var c=d.create(owner,"可扩张",h.getLevel().dimension().location().toString(),old);
        var annex=new CityBounds(origin.offset(4096,0,4096),origin.offset(8191,127,8191));long n=CityService.area(d,c,h.getLevel(),owner,annex,true);
        h.assertTrue(n==2147483648L&&c.areas.size()==2&&c.excluded.isEmpty(),"Huge annex stores two regions and exact changed count");
        h.assertTrue(c.includes(annex.max())&&!c.includes(origin.offset(2048,0,2048)),"Annex does not fill the unselected gap");
        CityService.undo(d,c,h.getLevel(),owner);h.assertTrue(c.bounds.equals(old)&&c.areas==null,"Undo restores exact prior representation");
        CityService.area(d,c,h.getLevel(),owner,annex,true);long before=CityAreas.volume(c);
        CityService.area(d,c,h.getLevel(),owner,annex,false);h.assertTrue(CityAreas.volume(c)==old.volumeLong()&&!c.includes(annex.min()),"Billion-cell exclusion does not expand a point mask");
        CityService.undo(d,c,h.getLevel(),owner);h.assertTrue(CityAreas.volume(c)==before&&c.includes(annex.max()),"Undo cut restores annex");h.succeed();
    }
    @GameTest(template="empty") public static void pointEditsAcrossSparseHolesPreserveLegacyExclusions(GameTestHelper h){
        var d=new CityData();var b=large(4000000,4000000);var owner=UUID.randomUUID();var c=d.create(owner,"混合编辑",h.getLevel().dimension().location().toString(),b);var p=b.min().offset(8,2,8);c.excluded.add(p.asLong());
        var hole=CityBounds.between(p.offset(-4,-2,-4),p.offset(4,4,4));CityService.area(d,c,h.getLevel(),owner,hole,false);
        CityService.membership(d,c,owner,List.of(p,p),true);h.assertTrue(c.includes(p)&&!c.includes(p.east()),"A single included point does not refill the whole hole");
        CityService.undo(d,c,h.getLevel(),owner);h.assertTrue(!c.includes(p)&&!c.includes(p.east()),"Point undo restores sparse exclusion and original point mask");h.succeed();
    }
    @GameTest(template="empty") public static void giantSchema4AndLegacySchema3RoundTrip(GameTestHelper h)throws Exception{
        var d=new CityData();var b=large(3000000,3000000);var c=d.create(UUID.randomUUID(),"保存大城",h.getLevel().dimension().location().toString(),b);var npc=UUID.randomUUID();c.residents.add(npc);
        CityService.area(d,c,h.getLevel(),c.owner,CityBounds.between(b.min().offset(100,0,100),b.min().offset(999,127,999)),false);
        var tag=d.save(new CompoundTag(),h.getLevel().registryAccess());var file=Files.createTempFile("city-large-schema4-",".nbt");try{NbtIo.writeCompressed(tag,file);var read=NbtIo.readCompressed(file,NbtAccounter.unlimitedHeap());var restored=CityData.load(read,h.getLevel().registryAccess()).require(c.id);h.assertTrue(restored.owner.equals(c.owner)&&restored.residents.contains(npc)&&CityAreas.volume(restored)==CityAreas.volume(c)&&restored.areas.equals(c.areas),"Actual compressed disk roundtrip preserves identity, residents and sparse geometry");}finally{Files.deleteIfExists(file);}
        var legacy=new CityData();var old=legacy.create(UUID.randomUUID(),"旧城",c.dimension,CityBounds.between(b.min(),b.min().offset(7,7,7)));old.excluded.add(old.bounds.min().asLong());var oldTag=legacy.save(new CompoundTag(),h.getLevel().registryAccess());oldTag.putInt("Schema",3);var migrated=CityData.load(oldTag,h.getLevel().registryAccess()).require(old.id);h.assertTrue(migrated.areas==null&&!migrated.includes(old.bounds.min())&&migrated.includes(old.bounds.max()),"Schema3 geometry is read without changing ownership");h.succeed();
    }
    @GameTest(template="empty") public static void giantJsonImportIsAtomicAndKeepsSparseShape(GameTestHelper h){
        var d=new CityData();var c=d.create(UUID.randomUUID(),"JSON大城",h.getLevel().dimension().location().toString(),large(2000000,2000000));var hole=CityBounds.between(c.bounds.min().offset(1,0,1),c.bounds.min().offset(1000,127,1000));CityService.area(d,c,h.getLevel(),c.owner,hole,false);var json=CityBlueprints.export(c);
        CityBlueprints.apply(d,c,h.getLevel(),json,c.revision);h.assertTrue(!c.includes(hole.min())&&c.includes(c.bounds.max()),"JSON preserves excluded regions");
        var before=CityBlueprints.export(c);var corrupt=before.deepCopy();corrupt.getAsJsonArray("areas").add(corrupt.getAsJsonArray("areas").get(0).deepCopy());reject(h,()->CityBlueprints.apply(d,c,h.getLevel(),corrupt,c.revision),"Overlapping region records rejected");h.assertTrue(before.equals(CityBlueprints.export(c)),"Invalid shape import leaves every field untouched");h.succeed();
    }
    @GameTest(template="empty") public static void originalTwoCornerWorkflowCreatesGiantCityWithoutLoadingIt(GameTestHelper h)throws Exception{
        var p=h.makeMockServerPlayerInLevel();p.setGameMode(GameType.CREATIVE);var a=new BlockPos(12000000,70,12000000);var b=a.offset(4095,0,4095);var level=h.getLevel();var knownBefore=CityLoadedChunks.boxes(level,large(12000000,12000000)).size();
        var point=CityMod.class.getDeclaredMethod("point",net.minecraft.server.level.ServerPlayer.class,BlockPos.class,boolean.class);point.setAccessible(true);point.invoke(null,p,a,false);point.invoke(null,p,b,true);
        var selection=CityMod.class.getDeclaredMethod("selection",net.minecraft.server.level.ServerPlayer.class);selection.setAccessible(true);var bounds=(CityBounds)selection.invoke(null,p);
        h.assertTrue(bounds.min().equals(a)&&bounds.max().getX()==b.getX()&&bounds.max().getY()==level.getMaxBuildHeight()-1,"Same two-corner automatic selection covers future roofs without new controls");
        var create=CityMod.class.getDeclaredMethod("create",net.minecraft.server.level.ServerPlayer.class,String.class);create.setAccessible(true);var data=CityData.get(p.server);var ids=new HashSet<>(data.cities.keySet());try{create.invoke(null,p,"原操作巨大城市");var created=data.cities.values().stream().filter(c->!ids.contains(c.id)).findFirst().orElseThrow();h.assertTrue(created.bounds.equals(bounds)&&created.owner.equals(p.getUUID()),"Existing creation method creates one whole city");h.assertTrue(CityLoadedChunks.boxes(level,bounds).size()==knownBefore&&level.getChunkSource().getChunkNow(b.getX()>>4,b.getZ()>>4)==null,"Founding does not generate or load remote chunks");}finally{for(UUID id:new HashSet<>(data.cities.keySet()))if(!ids.contains(id)){data.cities.remove(id);CityService.forget(id);}data.setDirty();for(String field:List.of("SESSIONS","BUILDING_DRAFTS")){var f=CityMod.class.getDeclaredField(field);f.setAccessible(true);((Map<?,?>)f.get(null)).remove(p.getUUID());}}h.succeed();
    }
    @GameTest(template="empty") public static void unloadedGiantStatisticsFinishWithoutVisitingItsVolume(GameTestHelper h){
        var server=h.getLevel().getServer();var data=CityData.get(server);var b=large(13000000,13000000);var c=data.create(UUID.randomUUID(),"远方统计",h.getLevel().dimension().location().toString(),b);
        try{CityService.scan(c);for(int i=0;i<128&&CityService.scanning(c.id);i++)CityService.tick(server);var result=CityService.statistics(c.id);h.assertTrue(result!=null&&!CityService.scanning(c.id)&&result.nonAir()==0&&result.unloaded()==2147483648L,"Unloaded status is exact, never reported as scanned empty terrain");h.assertTrue(h.getLevel().getChunkSource().getChunkNow(b.min().getX()>>4,b.min().getZ()>>4)==null,"Statistics acquires no chunks");}finally{data.cities.remove(c.id);CityService.forget(c.id);data.setDirty();}h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=1200) public static void millionBlockPalettePersistsCompactlyAndPreservesProperties(GameTestHelper h){
        String stone=CityMaintenance.encode(Blocks.STONE_BRICKS.defaultBlockState()),glass=CityMaintenance.encode(Blocks.GLASS.defaultBlockState());var map=new CityBlockStore();
        for(int z=-512;z<512;z++)for(int x=-512;x<512;x++)map.put(new BlockPos(x,64,z).asLong(),stone);map.compact();long changed=new BlockPos(-5,64,-8).asLong();map.put(changed,glass);
        var adapter=new CityBlockStore.Adapter();var json=adapter.serialize(map,null,null);String text=json.toString();h.assertTrue(map.size()==1048576&&map.storedSections()==4096&&text.length()<2000000,"A million flat blocks use compact section layers, not a million JSON key strings");
        var restored=adapter.deserialize(json,null,null);h.assertTrue(restored.size()==map.size()&&glass.equals(restored.get(changed))&&stone.equals(restored.get(new BlockPos(511,64,511).asLong())),"Palette and negative positions preserve exact state");h.assertTrue(adapter.serialize(restored,null,null).equals(json),"Compact save is stable after loading");
        var iterator=restored.keySet().iterator();var key=iterator.next();iterator.remove();h.assertTrue(restored.size()==1048575&&!restored.containsKey(key),"Map iterator removal remains compatible with existing repair code");h.succeed();
    }
    @GameTest(template="empty") public static void legacyBaselinePaletteAndCorruptRunsAreValidated(GameTestHelper h){
        String value=CityMaintenance.encode(Blocks.OAK_STAIRS.defaultBlockState());long key=new BlockPos(-17,-1,-33).asLong();var old=new JsonObject();old.addProperty(Long.toString(key),value);var adapter=new CityBlockStore.Adapter();var loaded=adapter.deserialize(old,null,null);h.assertTrue(value.equals(loaded.get(key)),"Original plain maps remain readable with full state properties");
        var bad=adapter.serialize(loaded,null,null).getAsJsonObject();bad.getAsJsonArray("sections").get(0).getAsJsonObject().getAsJsonArray("layers").get(0).getAsJsonArray().set(0,new JsonPrimitive(16));reject(h,()->adapter.deserialize(bad,null,null),"Malformed layer cannot inject out-of-section blocks");h.succeed();
    }
    @GameTest(template="empty") public static void loadedCursorHonorsPerTickBudget(GameTestHelper h){
        var a=h.absolutePos(new BlockPos(1,1,1));var box=CityBounds.between(a,a.offset(6,6,6));var cursor=new CityScanCursor(List.of(box));int[] visits={0};int used=cursor.step(h.getLevel(),7,p->visits[0]++,air->{});
        h.assertTrue(used<=7&&visits[0]<=7&&!cursor.done(),"A request cannot exceed its global operation budget");while(!cursor.done())cursor.step(h.getLevel(),7,p->visits[0]++,air->{});h.assertTrue(cursor.work<=343,"Progress completes without a hidden full-region pass");h.succeed();
    }
    @GameTest(template="empty") public static void expansionDoesNotRebaselinePreviouslyKnownAirAndUndoRestoresPendingWork(GameTestHelper h){
        var d=new CityData();var owner=UUID.randomUUID();var old=CityBounds.between(new BlockPos(6000000,64,6000000),new BlockPos(6000015,80,6000015));var c=d.create(owner,"旧维护城扩张",h.getLevel().dimension().location().toString(),old);c.works.finalized=true;
        var annex=CityBounds.between(old.min().east(16),old.max().east(4096));CityService.area(d,c,h.getLevel(),owner,annex,true);
        h.assertTrue(CityWorks.recorded(c,old.min())&&!CityWorks.recorded(c,annex.min()),"Expansion queues only new land, preserving the old air baseline");
        var saved=CityData.load(d.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess()).require(c.id);h.assertTrue(CityWorks.recorded(saved,old.min())&&!CityWorks.recorded(saved,annex.min()),"Pending terrain survives save/load without changing known land");
        CityService.undo(d,c,h.getLevel(),owner);h.assertTrue(c.bounds.equals(old)&&c.works.unknownTerrain.isEmpty()&&!c.works.deferredTerrain,"Undo restores maintenance metadata as well as territory");h.succeed();
    }
    @GameTest(template="empty") public static void giantFinalizeLabelsUnloadedTerrainAndSurvivesReload(GameTestHelper h){
        var server=h.getLevel().getServer();var data=CityData.get(server);var c=data.create(UUID.randomUUID(),"巨大城市定稿",h.getLevel().dimension().location().toString(),large(14000000,14000000));
        try{CityWorks.beginFinalize(server,c);CityWorks.capture(server);h.assertTrue(c.works.finalized&&c.works.deferredTerrain&&c.works.terrain.isEmpty()&&!c.works.unknownTerrain.isEmpty(),"A distant city is not claimed to have a verified terrain baseline");var saved=CityData.load(data.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess()).require(c.id);h.assertTrue(saved.works.deferredTerrain&&!CityWorks.recorded(saved,c.bounds.min()),"Pending territory persists for later loaded-only capture");h.assertTrue(h.getLevel().getChunkSource().getChunkNow(c.bounds.min().getX()>>4,c.bounds.min().getZ()>>4)==null,"Finalization never force-loads the giant city");}finally{data.cities.remove(c.id);CityMaintenance.get(server).state.cities.remove(c.id);CityEconomy.get(server).state.simulation.remove(c.id);CityService.forget(c.id);data.setDirty();}h.succeed();
    }
    @GameTest(template="empty") public static void longThinTerritoryAlsoSkipsUnloadedSpace(GameTestHelper h){
        var b=CityBounds.between(new BlockPos(15000000,70,15000000),new BlockPos(15131071,70,15000000));h.assertTrue(b.volumeLong()==131072&&!b.smallSelection(),"A small volume with a huge span is still a large territory");
        h.assertTrue(CityLoadedChunks.scanBoxes(h.getLevel(),b).isEmpty(),"Long narrow roads do not walk8192 missing chunks");var server=h.getLevel().getServer();var data=CityData.get(server);var c=data.create(UUID.randomUUID(),"细长城区",h.getLevel().dimension().location().toString(),b);
        try{CityWorks.beginFinalize(server,c);CityWorks.capture(server);h.assertTrue(c.works.finalized&&c.works.deferredTerrain&&!CityWorks.recorded(c,b.max()),"Same finalization operation accepts a long narrow city without loading it");}finally{data.cities.remove(c.id);CityMaintenance.get(server).state.cities.remove(c.id);CityEconomy.get(server).state.simulation.remove(c.id);CityService.forget(c.id);data.setDirty();}h.succeed();
    }
}
