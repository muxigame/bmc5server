package com.muxigame.buildingarchive;

import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(BuildingArchive.ID) @PrefixGameTestTemplate(false)
public final class TerrainSearchTests {
    static <T> List<TerrainSearchIndex.Entry<T>> finish(TerrainSearchIndex.Query<T> query){while(!query.done())query.step(4096,Long.MAX_VALUE);return query.results();}
    @GameTest(template="empty") public static void sixtyThousandEntriesStayBatchedAndCancelOldText(GameTestHelper h){
        var index=new TerrainSearchIndex<Integer>();for(int i=0;i<60_000;i++)index.add("pack:block_"+i,"整合包石砖 "+i,i,i%3==0);index.finish();
        var old=index.query("整合包",false);h.assertTrue(old.step(127,Long.MAX_VALUE)==127&&old.scanned()==127&&!old.done()&&old.results().isEmpty(),"Large registry search yields after its bounded work and never publishes partial/stale results");
        old.cancel();h.assertTrue(old.step(4096,Long.MAX_VALUE)==0&&!old.done(),"Typing new text cancels remaining old work");
        var newest=index.query("pack:block_59999",false);int frames=0;while(!newest.done()){h.assertTrue(newest.step(4096,Long.MAX_VALUE)<=4096,"Per-frame scan cap");frames++;}
        h.assertTrue(frames==15&&newest.results().size()==1&&newest.results().getFirst().value()==59999,"Latest exact ID eventually resolves through 15 bounded slices without old results");h.succeed();
    }
    @GameTest(template="empty") public static void chineseIdsAndFullWidthInputMatchStableNames(GameTestHelper h){
        var index=new TerrainSearchIndex<Integer>();index.add("pack:iron_b", "精钢石砖",2,true);index.add("pack:iron_a","精钢石砖",1,true);index.add("pack:wood","橡木板",3,true);index.finish();
        var names=finish(index.query("石砖",false));var ids=finish(index.query("ＰＡＣＫ：ＩＲＯＮ",false));
        h.assertTrue(names.size()==2&&ids.size()==2&&names.getFirst().id().equals("pack:iron_a")&&names.equals(ids),"Chinese label, case-insensitive IDs and full-width input share deterministic cached ordering");
        boolean immutable=false;try{names.clear();}catch(UnsupportedOperationException e){immutable=true;}h.assertTrue(immutable,"Published results cannot mutate cached catalogue");h.succeed();
    }
    @GameTest(template="empty") public static void deadlineAndZeroBudgetDoNotStartUnboundedWork(GameTestHelper h){
        var index=new TerrainSearchIndex<Integer>();index.add("minecraft:stone","石头",1,true);index.finish();var query=index.query("",false);
        h.assertTrue(query.step(100,Long.MIN_VALUE)==0&&query.step(0,Long.MAX_VALUE)==0&&query.scanned()==0,"Expired frame or zero quota does no scan");
        boolean rejected=false;try{query.step(-1,Long.MAX_VALUE);}catch(IllegalArgumentException e){rejected=true;}h.assertTrue(rejected&&finish(query).size()==1,"Invalid budgets rejected; deferred work still completes");h.succeed();
    }
    @GameTest(template="empty") public static void targetEligibilityDoesNotHideSourceMachines(GameTestHelper h){
        var index=new TerrainSearchIndex<Integer>();index.add("minecraft:chest","箱子",1,false);index.add("minecraft:stone","石头",2,true);index.add("minecraft:oak_door","橡木门",3,false);index.finish();
        h.assertTrue(finish(index.query("",false)).size()==3&&finish(index.query("",true)).size()==1,"Source may select machines, targets continue excluding unsafe blocks");
        h.assertTrue(!TerrainTool.usableTarget(Blocks.CHEST)&&!TerrainTool.usableTarget(Blocks.OAK_DOOR)&&!TerrainTool.usableTarget(Blocks.WATER)&&TerrainTool.usableTarget(Blocks.STONE),"Actual server eligibility policy remains unchanged");h.succeed();
    }
    @GameTest(template="empty") public static void actualRegistryCatalogueBuildIsIncrementalAndReusable(GameTestHelper h){
        var catalog=TerrainClientCatalog.current();int steps=0;while(!catalog.index.ready()){int visited=catalog.advance(7,Long.MAX_VALUE);h.assertTrue(visited<=7,"Index preparation reads only its entry quota");steps++;}
        h.assertTrue(catalog.visited()==catalog.total()&&catalog.advance(7,Long.MAX_VALUE)==0&&TerrainClientCatalog.current()==catalog,"Same registry reuses the completed cache, never re-reads every block on text input");
        var target=finish(catalog.index.query("minecraft:chest",true));var source=finish(catalog.index.query("minecraft:chest",false));h.assertTrue(target.isEmpty()&&!source.isEmpty(),"Actual registry uses the same target/source safety distinction");h.succeed();
    }
    @GameTest(template="empty") public static void indexCannotBeMutatedDuringQueries(GameTestHelper h){
        var index=new TerrainSearchIndex<Integer>();index.add("pack:a","甲",1,true);boolean early=false;try{index.query("",false);}catch(IllegalStateException e){early=true;}index.finish();boolean late=false;try{index.add("pack:b","乙",2,true);}catch(IllegalStateException e){late=true;}h.assertTrue(early&&late&&finish(index.query("",false)).size()==1,"Unfinished index cannot be queried and frozen index cannot invalidate an active iterator");h.succeed();
    }
}
