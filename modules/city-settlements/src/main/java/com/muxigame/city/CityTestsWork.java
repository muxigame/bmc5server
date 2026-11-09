package com.muxigame.city;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
@GameTestHolder(CityMod.ID) @PrefixGameTestTemplate(false)
public final class CityTestsWork {
    @GameTest(template="empty") public static void localTradePreservesPricePriority(GameTestHelper h){
        var far=new CityEconomy.Offer();far.buyPrice=2;var near=new CityEconomy.Offer();near.buyPrice=2;var expensive=new CityEconomy.Offer();expensive.buyPrice=3;
        var sorted=CityEconomy.rankOffers(List.of(far,expensive,near),o->o==far?100:o==near?10:1);
        h.assertTrue(sorted.equals(List.of(near,far,expensive)),"Same-price goods bought locally while lower prices retain priority");h.succeed();
    }
    @GameTest(template="empty") public static void riceVisualHarvestHasNoLootOrMoney(GameTestHelper h){
        var l=h.getLevel();var p=h.absolutePos(new BlockPos(1,2,1));l.setBlock(p.below(),Blocks.FARMLAND.defaultBlockState(),3);l.setBlock(p,CityRice.CROP.get().getStateForAge(7),3);
        var box=new net.minecraft.world.phys.AABB(p).inflate(2);int before=l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,box).size();
        h.assertTrue(CityWork.harvest(l,p),"Mature rice harvested");h.assertTrue(CityRice.CROP.get().getAge(l.getBlockState(p))==0,"Replanted immediately");h.assertTrue(!CityWork.harvest(l,p),"Cannot harvest seedling");h.assertTrue(l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,box).size()==before,"No dropped item entities");h.succeed();
    }
    @GameTest(template="empty") public static void sourceRulesAreExplicitAndBounded(GameTestHelper h){
        var j=new CityEconomy.Job();j.resourceSource="wheat";j.outputs.put("minecraft:wheat",4);j.requiredBlocks.put("minecraft:wheat",4);j.periodTicks=1200;CityWork.validateSource(j);
        j.periodTicks=200;reject(h,()->CityWork.validateSource(j));j.periodTicks=1200;j.outputs.put("minecraft:diamond",1);reject(h,()->CityWork.validateSource(j));j.outputs.remove("minecraft:diamond");j.requiredBlocks.clear();reject(h,()->CityWork.validateSource(j));h.succeed();
    }
    @GameTest(template="empty") public static void seedlingProductionIndependentOfAnimation(GameTestHelper h){
        var d=new CityData();var l=h.getLevel();var p=h.absolutePos(new BlockPos(1,2,1));var c=d.create(UUID.randomUUID(),"周期麦田","minecraft:overworld",CityBounds.between(p.below(),p.offset(2,1,2)));
        l.setBlock(p,Blocks.BARREL.defaultBlockState(),3);l.setBlock(p.east().below(),Blocks.FARMLAND.defaultBlockState(),3);l.setBlock(p.east(),Blocks.WHEAT.defaultBlockState(),3);
        CityRegistry.warehouse(d,c,l,"仓",p);CityRegistry.building(d,c,"田","农田",c.bounds);
        var e=new CityEconomy();var j=new CityEconomy.Job();j.id=UUID.randomUUID();j.city=c.id;j.building=c.buildings.keySet().iterator().next();j.warehouse=c.warehouses.keySet().iterator().next();j.worker=UUID.randomUUID();j.employer="farm";j.resourceSource="wheat";j.outputs.put("minecraft:wheat",1);j.requiredBlocks.put("minecraft:wheat",1);j.wage=3;
        e.adjust("farm",10,0,"test");e.adjust(CityEconomy.person(j.worker),0,0,"test");e.state.warehouseOwners.put(j.warehouse,"farm");e.state.production.add(j);CityEconomy.validate(e.state);e.productionAt(l,c,j);
        h.assertTrue(CityEconomy.warehouse(l,c,j.warehouse).getItem(0).is(net.minecraft.world.item.Items.WHEAT)&&e.balance("farm")==7,"Cycle pays once and stocks wheat without visual harvest");
        l.setBlock(p.east(),Blocks.AIR.defaultBlockState(),3);reject(h,()->e.productionAt(l,c,j));h.assertTrue(e.balance("farm")==7,"Destroyed facility stops output and wage");h.assertTrue(CityEconomy.decode(e.export()).production.getFirst().resourceSource.equals("wheat"),"Source persists");h.succeed();
    }
    private static void reject(GameTestHelper h,Runnable r){boolean failed=false;try{r.run();}catch(IllegalArgumentException ex){failed=true;}h.assertTrue(failed,"Bad configuration rejected");}
}
