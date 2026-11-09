package com.muxigame.city;
import java.util.*;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CityMod.ID) @PrefixGameTestTemplate(false)
public final class CityTestsSimulation {
    @GameTest(template="empty") public static void pendingShadowsCannotBeForgotten(GameTestHelper h){
        var before=new CityEconomy.State();var p=new CitySimulation.Policy();var s=new CitySimulation.Stock();s.pending=true;UUID city=UUID.randomUUID(),warehouse=UUID.randomUUID();p.stocks.put(warehouse,s);before.simulation.put(city,p);
        reject(h,()->CitySimulation.transition(before,new CityEconomy.State()));var next=new CityEconomy.State();next.simulation.put(city,p);CitySimulation.transition(before,next);h.succeed();
    }
    static void reject(GameTestHelper h,Runnable run){boolean failed=false;try{run.run();}catch(IllegalArgumentException ex){failed=true;}h.assertTrue(failed,"Invalid operation rejected without mutation");}
    @GameTest(template="empty") public static void freightAtomicityAndMoney(GameTestHelper h){
        var e=new CityEconomy();UUID merchant=UUID.randomUUID();e.adjust("business",10,0,"test");e.adjust(CityEconomy.person(merchant),0,0,"test");var source=new SimpleContainer(new ItemStack(Items.BREAD,64));var target=new SimpleContainer(1);target.setItem(0,new ItemStack(Items.STONE,64));
        reject(h,()->CityLogistics.move(e,source,target,"minecraft:bread",32,"business",merchant,2,1));h.assertTrue(source.getItem(0).getCount()==64&&e.balance("business")==10,"Full destination cannot consume stock or fees");target.clearContent();
        reject(h,()->CityLogistics.move(e,source,target,"minecraft:bread",32,"business",merchant,11,1));h.assertTrue(source.getItem(0).getCount()==64&&target.isEmpty(),"Unpaid fee cannot deliver");
        CityLogistics.move(e,source,target,"minecraft:bread",32,"business",merchant,2,1);h.assertTrue(source.getItem(0).getCount()==32&&target.getItem(0).getCount()==32&&e.balance("business")==8&&e.balance(CityEconomy.person(merchant))==2,"Freight conserves goods and money");h.succeed();
    }
    @GameTest(template="empty") public static void shadowProjectionSurvivesReplayAndConflicts(GameTestHelper h){
        var e=new CityEconomy();var p=new CitySimulation.Policy();UUID city=UUID.randomUUID(),wid=UUID.randomUUID();e.state.simulation.put(city,p);var stock=new CitySimulation.Stock();p.stocks.put(wid,stock);var physical=new SimpleContainer(27);physical.setItem(0,new ItemStack(Items.BREAD,64));CitySimulation.capture(e,stock,physical);
        var shadow=new CitySimulation.Shadow(stock,e);var slots=new CityEconomy.Slots(shadow);slots.remove("minecraft:bread",20);slots.commit();h.assertTrue(stock.pending&&stock.goods.get("minecraft:bread")==44&&physical.getItem(0).getCount()==64,"Background changes never touch unloaded physical contents");
        var restored=CityEconomy.load(e.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());var copy=restored.state.simulation.get(city).stocks.get(wid);CitySimulation.reconcile(restored,copy,physical);CitySimulation.reconcile(restored,copy,physical);h.assertTrue(physical.getItem(0).getCount()==44&&!copy.pending,"Reload and repeated materialization cannot duplicate goods");
        // A crash may have saved the chunk projection before its acknowledgement: recognize desired contents.
        CitySimulation.reconcile(e,stock,physical);h.assertTrue(!stock.pending&&physical.getItem(0).getCount()==44,"Already-written projection is acknowledged once");
        stock.pending=true;stock.goods.put("minecraft:bread",30);physical.setItem(0,new ItemStack(Items.BREAD,40));reject(h,()->CitySimulation.reconcile(e,stock,physical));h.assertTrue(physical.getItem(0).getCount()==40&&stock.pending&&stock.blocked,"Conflicting physical edit is preserved and quarantined");h.succeed();
    }
    @GameTest(template="empty") public static void specialGoodsAndLegacyState(GameTestHelper h){
        var e=new CityEconomy();var stock=new CitySimulation.Stock();var box=new SimpleContainer(27);var named=new ItemStack(Items.BREAD);named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("keep me"));box.setItem(0,named);CitySimulation.capture(e,stock,box);h.assertTrue(stock.blocked&&box.getItem(0).has(net.minecraft.core.component.DataComponents.CUSTOM_NAME),"Named goods cannot be stripped into generic virtual stock");
        var legacy=e.export();legacy.remove("simulation");legacy.remove("logistics");var restored=CityEconomy.decode(legacy);h.assertTrue(restored.simulation.isEmpty()&&restored.logistics.isEmpty(),"Old worlds opt out by default");var policy=new CityLogistics.Policy();policy.enabled=true;e.state.logistics.put(UUID.randomUUID(),policy);reject(h,()->CityLogistics.validate(e.state));h.succeed();
    }
    @GameTest(template="empty") public static void unloadedEconomyMigrationAndDataEdits(GameTestHelper h){
        var server=h.getLevel().getServer();var l=h.getLevel();var data=CityData.get(server);var e=CityEconomy.get(server);UUID npc=UUID.randomUUID(),bid=UUID.randomUUID(),wid=UUID.randomUUID();var pos=new BlockPos(20000000,-60,20000000);var c=data.create(UUID.randomUUID(),"后台测试",l.dimension().location().toString(),CityBounds.between(pos.below(),pos.offset(3,2,3)));String business="business:"+c.id;var originalIds=new HashSet<>(e.state.accounts.keySet());
        try{
            c.residents.add(npc);c.buildings.put(bid,new CityRegistry.Building(bid,"厨房","工坊",c.bounds));c.warehouses.put(wid,new CityRegistry.Warehouse(wid,"仓",pos));e.adjust(business,1000,0,"test");e.adjust(CityEconomy.person(npc),10,0,"test");e.state.warehouseOwners.put(wid,business);
            var policy=new CitySimulation.Policy();policy.enabled=true;policy.emigrateAfter=2;e.state.simulation.put(c.id,policy);var person=new CitySimulation.Person();person.present=true;person.pos=pos.asLong();policy.people.put(npc,person);var stock=new CitySimulation.Stock();stock.physical.put("city_settlements:rice",128);stock.goods.putAll(stock.physical);policy.stocks.put(wid,stock);
            var facility=new CitySimulation.Facility();facility.valid=true;facility.stamp=CitySimulation.stamp(c,c.buildings.get(bid));facility.blocks.put("minecraft:smoker",1);policy.facilities.put(bid,facility);
            var job=new CityEconomy.Job();job.id=UUID.randomUUID();job.city=c.id;job.worker=npc;job.building=bid;job.warehouse=wid;job.employer=business;job.wage=3;job.inputs.put("city_settlements:rice",1);job.outputs.put("city_settlements:cooked_rice",1);job.requiredBlocks.put("minecraft:smoker",1);e.state.production.add(job);
            var offer=new CityEconomy.Offer();offer.id=UUID.randomUUID();offer.city=c.id;offer.npc=npc;offer.warehouse=wid;offer.item="city_settlements:cooked_rice";offer.buyPrice=3;offer.enabled=true;e.state.merchants.add(offer);
            var life=CityLiving.defaults(c.id,npc);life.enabled=true;life.basket.clear();var need=new CityLiving.Need();need.item="city_settlements:cooked_rice";life.basket.add(need);e.state.living.put(npc,life);e.invalidateIndexes();
            h.assertTrue(!l.hasChunkAt(pos),"Fixture is genuinely outside loaded chunks");
            for(int cycle=0;cycle<120;cycle++){e.produce(server,job);CityLiving.cycle(server,e,life,cycle*2400L);}
            h.assertTrue(!l.hasChunkAt(pos)&&l.getEntity(npc)==null,"120 cycles create neither entities nor chunk tickets");h.assertTrue(life.need==31&&life.lastCost==3&&stock.goods.get("city_settlements:rice")==8&&policy.offlineProductions==120&&policy.offlineConsumptions==120,"Consumption, needs, finite inputs and counters run in background");h.assertTrue(e.balance(business)==1000&&e.balance(CityEconomy.person(npc))==10,"Background wages and consumption conserve money");
            var json=e.export();var edit=new JsonObject();edit.addProperty("city",c.id.toString());edit.addProperty("warehouse",wid.toString());edit.add("expected",CityEconomy.JSON.toJsonTree(stock.goods));edit.add("desired",CityEconomy.JSON.toJsonTree(Map.of("city_settlements:rice",7)));var edits=new JsonArray();edits.add(edit);json.add("stockEdits",edits);CityEconomyAccess.apply(server.createCommandSourceStack(),json,e.state.revision);policy=e.state.simulation.get(c.id);h.assertTrue(policy.stocks.get(wid).goods.get("city_settlements:rice")==7,"Data import edits the new authoritative shadow, not discarded pre-import state");
            life=e.state.living.get(npc);life.lastEssentials=false;CitySimulation.consumed(server,e,life);CitySimulation.consumed(server,e,life);CitySimulation.retire(e,data,c,policy,npc);h.assertTrue(!c.residents.contains(npc)&&!e.state.living.containsKey(npc)&&policy.people.get(npc).retired&&e.balance(CityEconomy.person(npc))==10,"Chronic deprivation migrates citizen, retains money and archives configuration");
            var entity=net.minecraft.world.entity.EntityType.ZOMBIE.create(l);entity.setUUID(npc);entity.setPos(h.absolutePos(BlockPos.ZERO).getCenter());h.assertTrue(CitySimulation.archiveRetired(e,entity)&&!policy.people.get(npc).archivedEntity.isBlank(),"Retired resident is archived and suppressed on entity reload");
            h.succeed();
        }finally{e.state.simulation.remove(c.id);e.state.logistics.remove(c.id);e.state.production.removeIf(j->j.city.equals(c.id));e.state.merchants.removeIf(o->o.city.equals(c.id));e.state.living.remove(npc);e.state.warehouseOwners.remove(wid);e.state.accounts.keySet().removeIf(k->!originalIds.contains(k));e.invalidateIndexes();data.cities.remove(c.id);data.setDirty();}
    }
}
