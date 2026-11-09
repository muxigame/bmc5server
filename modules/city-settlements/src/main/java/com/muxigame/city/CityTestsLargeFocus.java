package com.muxigame.city;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

/** A separately selectable suite for the final resize and legacy-coordinate corrections. */
@GameTestHolder("city_large_area_focus")
@PrefixGameTestTemplate(false)
public final class CityTestsLargeFocus {
    @GameTest(template="empty") public static void shrinkingClipsPendingTerrainAndKeepsReloadValid(GameTestHelper h){
        var d=new CityData();var a=new BlockPos(8000000,64,8000000);var c=d.create(UUID.randomUUID(),"缩小测试",h.getLevel().dimension().location().toString(),CityBounds.between(a,a.offset(4095,127,4095)));c.works.finalized=true;c.works.deferredTerrain=true;c.works.unknownTerrain.add(new long[]{c.bounds.min().asLong(),c.bounds.max().asLong()});String stone=CityMaintenance.encode(Blocks.STONE.defaultBlockState());var inside=a.offset(1,1,1);var outside=a.offset(3000,1,3000);c.works.terrain.put(inside.asLong(),stone);c.works.terrain.put(outside.asLong(),stone);
        var next=CityBounds.between(a,a.offset(255,127,255));CityService.resizeAreas(c,next);c.bounds=next;d.changed(c);
        h.assertTrue(c.works.terrain.containsKey(inside.asLong())&&!c.works.terrain.containsKey(outside.asLong()),"Released territory no longer leaves stale maintenance targets outside the city");
        var copy=CityData.load(d.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess()).require(c.id);h.assertTrue(copy.bounds.equals(next)&&copy.works.unknownTerrain.size()==1&&!CityWorks.recorded(copy,inside),"Pending terrain is clipped and remains valid after save/load");h.succeed();
    }
    @GameTest(template="empty") public static void shrinkingCannotDiscardCommittedWork(GameTestHelper h){
        var a=new BlockPos(8100000,64,8100000);var c=new CityData.City(UUID.randomUUID(),UUID.randomUUID(),"保护施工","minecraft:overworld",CityBounds.between(a,a.offset(31,15,31)));String state=CityMaintenance.encode(Blocks.STONE.defaultBlockState());c.works.pending.put(a.offset(30,1,30).asLong(),new CityWorks.Projection(state,state,"已付费施工"));var before=CityBlueprints.export(c);boolean rejected=false;try{CityService.resizeAreas(c,CityBounds.between(a,a.offset(7,7,7)));}catch(IllegalArgumentException e){rejected=true;}
        h.assertTrue(rejected&&before.equals(CityBlueprints.export(c)),"Invalid shrink leaves geometry, paid task and all metadata unchanged");h.succeed();
    }
    @GameTest(template="empty") public static void legacyOutOfWorldHeightDoesNotIndexChunkSections(GameTestHelper h){
        var a=h.absolutePos(new BlockPos(1,1,1));int y=h.getLevel().getMaxBuildHeight()+2;var box=CityBounds.between(new BlockPos(a.getX(),y,a.getZ()),new BlockPos(a.getX()+1,y+3,a.getZ()+1));var cursor=new CityScanCursor(List.of(box));int[] count={0};int work=cursor.step(h.getLevel(),8,p->count[0]++,air->count[0]++);
        h.assertTrue(cursor.done()&&cursor.lostChunk&&count[0]==0&&work<=8,"Unavailable legacy height is never claimed scanned and never accesses an invalid section");h.succeed();
    }
    @GameTest(template="empty") public static void paletteMapMatchesReferenceThroughMixedEditsAndClipping(GameTestHelper h){
        var actual=new CityBlockStore();var reference=new HashMap<Long,String>();var random=new Random(20261109);
        for(int i=0;i<12000;i++){long key=new BlockPos(random.nextInt(96)-48,random.nextInt(32)-16,random.nextInt(96)-48).asLong();if(i%4==0){h.assertTrue(Objects.equals(actual.remove(key),reference.remove(key)),"Removal returns the same old value");}else{String value="state"+random.nextInt(7);h.assertTrue(Objects.equals(actual.put(key,value),reference.put(key,value)),"Insertion returns the same old value");}}
        var bounds=CityBounds.between(new BlockPos(-17,-4,-19),new BlockPos(23,9,26));actual.retainWithin(bounds);reference.keySet().removeIf(p->!bounds.contains(BlockPos.of(p)));h.assertTrue(actual.equals(reference)&&actual.inside(bounds)&&new HashSet<>(actual.states()).equals(new HashSet<>(reference.values())),"Partial sections and negative coordinates clip exactly like the reference map");
        var hole=CityBounds.between(new BlockPos(-3,0,-2),new BlockPos(5,5,8));actual.removeWithin(hole);reference.keySet().removeIf(p->hole.contains(BlockPos.of(p)));var adapter=new CityBlockStore.Adapter();var json=adapter.serialize(actual,null,null);var loaded=adapter.deserialize(json,null,null);h.assertTrue(loaded.equals(reference)&&adapter.serialize(loaded,null,null).equals(json),"Clipping, palette compression and canonical reload preserve every entry");h.succeed();
    }
    @GameTest(template="empty") public static void networkSnapshotPreservesBillionCellCounters(GameTestHelper h){
        var data=new CompoundTag();data.putLong("Volume",4294967296L);data.putLong("Unloaded",2147483648L);var buffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());try{CityNetwork.Snapshot.CODEC.encode(buffer,new CityNetwork.Snapshot(data));var result=CityNetwork.Snapshot.CODEC.decode(buffer).data();h.assertTrue(result.getLong("Volume")==4294967296L&&result.getLong("Unloaded")==2147483648L,"Actual packet codec preserves64-bit counters used by unchanged city screens");}finally{buffer.release();}h.succeed();
    }
    @GameTest(template="empty") public static void giantCutKeepsIndexedMembershipAndLongCount(GameTestHelper h){
        var d=new CityData();var a=new BlockPos(8200000,64,8200000);var c=d.create(UUID.randomUUID(),"最终巨大城区",h.getLevel().dimension().location().toString(),CityBounds.between(a,a.offset(4095,255,4095)));var hole=CityBounds.between(a.offset(1024,0,1024),a.offset(3071,255,3071));long changed=CityService.area(d,c,h.getLevel(),c.owner,hole,false);h.assertTrue(changed==1073741824L&&!c.includes(hole.min())&&c.includes(a)&&CityAreas.volume(c)==3221225472L,"Final indexed representation handles a billion-cell cut without point expansion");CityService.undo(d,c,h.getLevel(),c.owner);h.assertTrue(CityAreas.volume(c)==4294967296L&&c.includes(hole.max()),"Final undo preserves full city identity and volume");h.succeed();
    }
}
