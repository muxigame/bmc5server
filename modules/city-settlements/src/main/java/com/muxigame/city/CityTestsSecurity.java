package com.muxigame.city;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CityMod.ID)
@PrefixGameTestTemplate(false)
public final class CityTestsSecurity {
    @GameTest(template="empty") public static void livingEditorMergesRuntimeAndDisjointEdits(GameTestHelper h){
        var e=new CityEconomy();var d=new CityData();var c=d.create(UUID.randomUUID(),"编辑测试","minecraft:overworld",CityBounds.between(BlockPos.ZERO,new BlockPos(2,2,2)));var id=UUID.randomUUID();c.residents.add(id);var p=CityLiving.defaults(c.id,id);e.state.living.put(id,p);
        var base=CityLivingEditor.settings(CityLivingEditor.draft(e,c,id));var edited=base.deepCopy();edited.getAsJsonObject("profile").addProperty("guardPower",21);
        p.lastTick=500;p.nextTick=600;p.income=30;p.basket.getFirst().status="已购买";
        var current=CityLivingEditor.settings(CityLivingEditor.draft(e,c,id));h.assertTrue(base.equals(current),"Runtime payments/schedules/item status do not invalidate settings");
        current.getAsJsonObject("profile").addProperty("need",35);
        var merged=CityLivingEditor.merge(base,current,edited).getAsJsonObject().getAsJsonObject("profile");h.assertTrue(merged.get("guardPower").getAsInt()==21&&merged.get("need").getAsInt()==35,"Manual guard edit preserves autonomous need growth");
        current.getAsJsonObject("profile").addProperty("guardPower",25);rejects(h,()->CityLivingEditor.merge(base,current,edited));h.succeed();
    }
    @GameTest(template="empty") public static void patrolUsesExistingLawLedger(GameTestHelper h){
        var server=h.getLevel().getServer();UUID id=UUID.randomUUID();var law=CityLaw.get(server);var record=law.record(id);record.rules.addProperty("security",20);
        h.assertTrue(CityLaw.patrol(server,id,80,true,2,2,1)==22,"Paid patrol restores gradually, not a reset to target");
        h.assertTrue(CityLaw.patrol(server,id,80,false,2,2,2)==22,"Absent guards cannot recover incident losses");
        h.assertTrue(CityLaw.patrol(server,id,10,false,2,2,3)==20,"Insufficient service causes bounded decay");
        record.rules.addProperty("enabled",false);h.assertTrue(CityLaw.patrol(server,id,80,true,2,2,4)==20,"Existing law disable remains authoritative");law.records.remove(id);law.setDirty();h.succeed();
    }
    @GameTest(template="empty") public static void guardCannotDoubleWork(GameTestHelper h){
        var e=new CityEconomy();var profile=CityLiving.defaults(UUID.randomUUID(),UUID.randomUUID());profile.guard=true;e.state.living.put(profile.npc,profile);var job=new CityEconomy.Job();job.worker=profile.npc;job.enabled=true;e.state.production.add(job);
        rejects(h,()->CitySecurity.validate(e.state));job.enabled=false;CitySecurity.validate(e.state);h.succeed();
    }
    static void rejects(GameTestHelper h,Runnable r){boolean rejected=false;try{r.run();}catch(IllegalArgumentException ex){rejected=true;}h.assertTrue(rejected,"Invalid operation must reject");}
    @GameTest(template="empty") public static void guardWagesAndCoverage(GameTestHelper h){
        var e=new CityEconomy();UUID npc=UUID.randomUUID(),city=UUID.randomUUID();var life=CityLiving.defaults(city,npc);life.enabled=true;life.guard=true;e.state.living.put(npc,life);e.adjust("treasury",5,0,"test");e.adjust(CityEconomy.person(npc),0,0,"test");var p=new CitySecurity.Policy();p.payer="treasury";
        CitySecurity.pay(e,p,npc,city,1);h.assertTrue(e.balance("treasury")==2&&e.balance(CityEconomy.person(npc))==3&&life.income==3,"Guard wage is real transfer and actual income");
        rejects(h,()->CitySecurity.pay(e,p,npc,city,2));h.assertTrue(e.balance("treasury")==2&&life.income==3,"Unpaid guard cannot create wages");p.payer=CityEconomy.person(npc);rejects(h,()->CitySecurity.pay(e,p,npc,city,3));
        h.assertTrue(CitySecurity.coverage(20,8,5)==50&&CitySecurity.coverage(20,16,5)==25,"Larger population dilutes same force");h.assertTrue(CitySecurity.order(40,100,15,0)>CitySecurity.order(40,0,15,0),"Coverage improves public order");h.succeed();
    }
    @GameTest(template="empty") public static void securityPreservesGrievances(GameTestHelper h){
        var e=new CityEconomy();var data=new CityData();var c=data.create(UUID.randomUUID(),"治安测试","minecraft:overworld",CityBounds.between(BlockPos.ZERO,new BlockPos(2,2,2)));UUID id=UUID.randomUUID();c.residents.add(id);var v=CityLiving.defaults(c.id,id);v.enabled=true;v.lastTick=1;v.contribution=-3;e.state.living.put(id,v);e.adjust(CityEconomy.person(id),10,0,"test");
        int grievance=CityLiving.stability(e,c);var p=new CitySecurity.Policy();p.enabled=true;p.lastTick=1;p.order=100;e.state.security.put(c.id,p);
        h.assertTrue(CityLiving.stability(e,c)==grievance&&CitySecurity.riskIndex(e,c)>grievance,"Guards reduce incident risk without erasing grievances");
        p.order=0;p.repression=100;h.assertTrue(CitySecurity.riskIndex(e,c)>=20,"Natural repression/deprivation cannot spiral into instant high risk");var policy=new CitySociety.Policy();policy.externalPressure=80;e.state.society.put(c.id,policy);h.assertTrue(CitySecurity.riskIndex(e,c)<20,"External shocks can override protection");h.succeed();
    }
    @GameTest(template="empty") public static void roundRobinFairnessAndBounds(GameTestHelper h){
        List<long[]> tasks=new ArrayList<>();for(int i=0;i<600;i++)tasks.add(new long[]{0,0});int cursor=0;
        for(int tick=0;tick<600;tick++){final int now=tick;var b=CityEconomy.batch(tasks,cursor,1,t->now>=t[0],t->{t[0]=now+200;t[1]++;});cursor=b.cursor();h.assertTrue(b.count()<=1,"Hard per-tick budget");}
        h.assertTrue(tasks.stream().allMatch(t->t[1]==1),"Later tasks cannot starve behind frequently due earlier cities");
        var b=CityEconomy.batch(tasks,0,2,t->true,t->{});h.assertTrue(b.count()==2,"Production budget exactly two when backlogged");h.succeed();
    }
    @GameTest(template="empty") public static void smallMoneyDeltaAndImportCache(GameTestHelper h){
        var e=new CityEconomy();for(int i=0;i<10000;i++)e.state.accounts.put("account:"+i,100L);e.state.accounts.put("a",10000L);e.state.accounts.put("b",10000L);var accounts=e.state.accounts;
        long before=System.nanoTime();for(int i=0;i<1000;i++)e.transfer(i%2==0?"a":"b",i%2==0?"b":"a",1,CityEconomy.Purpose.TRANSFER,"benchmark",i);long delta=System.nanoTime()-before;
        h.assertTrue(accounts==e.state.accounts&&e.balance("a")==10000&&e.state.accounts.size()==10002,"Transactions update only existing map entries, no global map clone");
        long base=System.nanoTime(),checksum=0;for(int i=0;i<1000;i++){var copied=new TreeMap<>(accounts);checksum+=copied.size();}long copying=System.nanoTime()-base;
        org.slf4j.LoggerFactory.getLogger("CityPerf").info("10002 accounts / 1000 transfers: delta={}ms; old-size map-copy-only baseline={}ms; checksum={}",delta/1e6,copying/1e6,checksum);
        UUID city=UUID.randomUUID();var offer=new CityEconomy.Offer();offer.city=city;offer.item="minecraft:bread";offer.enabled=true;offer.buyPrice=3;e.state.merchants.add(offer);h.assertTrue(e.offers(city,"minecraft:bread",1).size()==1,"Quote cache builds");e.state=new CityEconomy.State();h.assertTrue(e.offers(city,"minecraft:bread",1).isEmpty(),"Imported state invalidates index");h.succeed();
    }
    @GameTest(template="empty") public static void securityPersistenceAndPausedWorld(GameTestHelper h){
        var e=new CityEconomy();UUID city=UUID.randomUUID();var p=new CitySecurity.Policy();e.state.security.put(city,p);p.wage=7;p.baseOrder=33;
        var restored=CityEconomy.load(e.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());h.assertTrue(restored.state.security.get(city).wage==7,"Security configuration persists");var legacy=e.export();legacy.remove("security");h.assertTrue(CityEconomy.decode(legacy).security.isEmpty(),"Legacy worlds have security disabled");
        var data=CityData.get(h.getLevel().getServer());var c=data.create(UUID.randomUUID(),"无人治安",h.getLevel().dimension().location().toString(),CityBounds.between(h.absolutePos(BlockPos.ZERO),h.absolutePos(new BlockPos(2,2,2))));
        p.lastTick=99;p.order=100;p.activeGuards=4;p.coverage=100;CitySecurity.run(h.getLevel().getServer(),e,c.id,p,100);h.assertTrue(p.lastTick==0&&p.activeGuards==0&&p.coverage==0&&p.lastCost==0,"Unloaded/empty city cannot keep claiming active coverage");data.cities.remove(c.id);data.setDirty();h.succeed();
    }
}
