package com.muxigame.city;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CityMod.ID)
@PrefixGameTestTemplate(false)
public final class CityTestsLiving {
    private static void rejects(GameTestHelper h,Runnable action){boolean rejected=false;try{action.run();}catch(RuntimeException ex){rejected=true;}h.assertTrue(rejected,"Invalid operation must reject");}
    private static CityData.City city(CityData data){return data.create(UUID.randomUUID(),"生活测试","minecraft:overworld",CityBounds.between(BlockPos.ZERO,new BlockPos(3,3,3)));}
    @GameTest(template="empty") public static void incomeDrivenGrowth(GameTestHelper h){
        var low=CityLiving.defaults(UUID.randomUUID(),UUID.randomUUID());var rich=CityLiving.defaults(low.city,UUID.randomUUID());
        for(int i=1;i<=120;i++){low.income=0;rich.income=60;CityLiving.finish(low,35,true,3,true,3,i);CityLiving.finish(rich,35,true,3,true,3,i);}
        h.assertTrue(low.need==30&&rich.need==50&&rich.level>=1,"Zero income stagnates; high income expands desires without requiring every good");
        var worker=CityLiving.defaults(low.city,UUID.randomUUID());for(int i=1;i<=119;i++){worker.income=3;CityLiving.finish(worker,90,true,50,true,3,i);}h.assertTrue(worker.need==30,"Low income growth extremely slow, never automatically falls");worker.income=3;CityLiving.finish(worker,90,true,50,true,3,120);h.assertTrue(worker.need==31,"Low-income demand increases only at configured interval");h.succeed();
    }
    @GameTest(template="empty") public static void broadGoodsAtomicAndScarcity(GameTestHelper h){
        var e=new CityEconomy();e.adjust("resident",20,0,"test");e.adjust("shop",0,0,"test");var stock=new SimpleContainer(new ItemStack(Items.BREAD,2),new ItemStack(Items.LEATHER,1));
        e.consumeGoods(stock,"resident","shop","minecraft:leather",1,7,1);h.assertTrue(stock.getItem(1).isEmpty()&&e.balance("resident")==13&&e.balance("shop")==7,"Nonfood consumption uses real goods and conserves money");
        long rev=e.state.revision;rejects(h,()->e.consumeGoods(stock,"resident","shop","minecraft:book",1,4,2));h.assertTrue(e.state.revision==rev&&e.balance("resident")==13,"Missing luxury cannot charge");
        rejects(h,()->e.consumeGoods(stock,"resident","shop","minecraft:bread",2,9,3));h.assertTrue(stock.getItem(0).getCount()==2,"Unaffordable purchase does not consume");h.succeed();
    }
    @GameTest(template="empty") public static void naturalPressureBounded(GameTestHelper h){
        var e=new CityEconomy();var c=city(new CityData());
        for(int i=0;i<10;i++){UUID id=UUID.randomUUID();c.residents.add(id);var p=CityLiving.defaults(c.id,id);p.enabled=true;p.need=100;p.maxNeed=100;p.distributor=i==0;e.state.living.put(id,p);e.adjust(CityEconomy.person(id),i==0?10000:1,0,"test");for(int n=1;n<=300;n++)CityLiving.finish(p,0,false,300,false,0,n);}
        int natural=CityLiving.stability(e,c);h.assertTrue(natural>=20&&natural<40,"Extreme deprivation and wealth concentration only reach moderate natural risk");
        var policy=new CitySociety.Policy();policy.externalPressure=30;e.state.society.put(c.id,policy);h.assertTrue(CityLiving.stability(e,c)<20,"External intervention can reach high risk");h.succeed();
    }
    @GameTest(template="empty") public static void distributionConservesAndRecruits(GameTestHelper h){
        var e=new CityEconomy();var c=city(new CityData());UUID warehouse=UUID.randomUUID();c.warehouses.put(warehouse,new CityRegistry.Warehouse(warehouse,"仓库",BlockPos.ZERO));
        e.state.warehouseOwners.put(warehouse,"business");e.adjust("business",1000,0,"test");
        for(int i=0;i<2;i++){UUID id=UUID.randomUUID();c.residents.add(id);var p=CityLiving.defaults(c.id,id);p.enabled=true;p.distributor=i==0;e.state.living.put(id,p);e.adjust(CityEconomy.person(id),i==0?10:100,0,"test");}
        var policy=new CitySociety.Policy();policy.autoRecruit=true;policy.recruitWealth=100;policy.sharePercent=50;policy.reserve=900;e.state.businessNet.put("business",400L);
        CitySociety.distribute(e,c,policy,1);h.assertTrue(e.balance("business")==900&&policy.lastDistributed==100&&e.state.living.values().stream().allMatch(p->p.distributor&&p.income==50),"Reserve respected; eligible wealth recruits and real distribution counts as income");
        h.assertTrue(e.state.accounts.values().stream().mapToLong(Long::longValue).sum()==1110,"No currency created");CitySociety.distribute(e,c,policy,2);h.assertTrue(e.balance("business")==900&&policy.lastDistributed==0,"Same surplus cannot be distributed twice");h.succeed();
    }
    @GameTest(template="empty") public static void legacyMigrationAndRoundTrip(GameTestHelper h){
        var e=new CityEconomy();UUID id=UUID.randomUUID();var p=CityLiving.defaults(UUID.randomUUID(),id);e.adjust(CityEconomy.person(id),120,0,"test");e.state.living.put(id,p);p.enabled=true;p.income=9;p.affordableCycles=5;
        var copy=CityEconomy.load(e.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());h.assertTrue(copy.state.living.get(id).income==9&&copy.state.living.get(id).affordableCycles==5,"Growth and actual income persist");
        var legacy=e.export();legacy.remove("living");legacy.remove("society");legacy.remove("businessNet");h.assertTrue(CityEconomy.decode(legacy).living.isEmpty(),"Old economies opt out without changing their demands");p.basket.add(p.basket.getFirst());rejects(h,()->CityEconomy.validate(e.state));h.succeed();
    }
    @GameTest(template="empty") public static void editorScopeAndHistory(GameTestHelper h){
        var e=new CityEconomy();var c=city(new CityData());UUID id=UUID.randomUUID();c.residents.add(id);e.adjust(CityEconomy.person(id),10,0,"test");var p=CityLiving.defaults(c.id,id);p.income=8;e.state.living.put(id,p);
        var input=CityLivingEditor.draft(e,c,id);input.getAsJsonObject("profile").addProperty("income",999);input.getAsJsonObject("profile").addProperty("need",45);input.addProperty("accounts","forged");
        var next=CityEconomy.decode(CityLivingEditor.candidate(e,c,id,input,100));h.assertTrue(next.living.get(id).income==8&&next.living.get(id).need==45&&next.accounts.get(CityEconomy.person(id))==10,"UI changes demand, not measurements or money");
        input.getAsJsonObject("profile").addProperty("need",1.5);rejects(h,()->CityLivingEditor.candidate(e,c,id,input,100));input.getAsJsonObject("profile").addProperty("npc",UUID.randomUUID().toString());rejects(h,()->CityLivingEditor.candidate(e,c,id,input,100));h.succeed();
    }
}
