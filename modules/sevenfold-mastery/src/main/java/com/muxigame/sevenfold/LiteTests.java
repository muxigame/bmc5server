package com.muxigame.sevenfold;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
@GameTestHolder("sevenfold_lite_checks") @PrefixGameTestTemplate(false)
public final class LiteTests {
    @GameTest(template="empty") public static void onlyFourNodesAndEmptyInteriors(GameTestHelper h){
        h.assertTrue(BuildVariant.LITE,"Lite mode enabled");var s=new StarTreeState(0).toggle(StarNode.CRYING,-1);int visible=0;
        for(var n:StarNode.values())if(s.visible(n))visible++;
        h.assertTrue(visible==4&&s.count(StarNode.CRYING)==10,"Only Departure and three main constellations");
        for(var n:StarNode.BASES){s=s.toggle(n,-1);for(int i=0;i<10;i++)h.assertTrue(!s.canToggle(n,i)&&s.toggle(n,i).equals(s),"Empty interiors reject server requests");h.assertTrue(s.count(n)==0&&!s.isComplete(n),"No invented completion");}
        h.assertTrue(s.profession()==WarProfession.NONE&&s.healthBonus()==80,"No hidden profession or star bonuses");h.succeed();
    }
    @GameTest(template="empty") public static void forgedAndOldProgressCannotRestoreBranches(GameTestHelper h){
        var s=new StarTreeState(new int[]{1023,1023,0,0,1023,0,0,0},19);
        h.assertTrue(s.count(StarNode.WARRIOR)==0&&!s.visible(StarNode.BERSERKER)&&s.profession()==WarProfession.NONE,"Old/full masks cannot restore removed contents");
        h.assertTrue(StarTreeState.load(s.save()).equals(s),"Lite state round-trips");
        h.assertTrue(StarTreeState.KEY.equals("SevenfoldStarTreeLite"),"Separate save key protects full-edition progress");h.succeed();
    }
}
