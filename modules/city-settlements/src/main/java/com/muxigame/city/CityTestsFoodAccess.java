package com.muxigame.city;

import java.util.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CityMod.ID) @PrefixGameTestTemplate(false)
public class CityTestsFoodAccess {
 @GameTest(template="empty") public static void netSettlementReservesTaxBeforeAnyMutation(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){
  CityTestsModel.configure(f);var b=f.t.state.businesses.values().stream().filter(x->x.kind.equals("procurement")).findFirst().orElseThrow();var head=f.t.head(f.village.id);var stock=f.r.stocks(f.r.state.people.get(head.worker).family);for(var x:stock){x.wheat=x.bread=0;RuralMeat.clear(x);}RuralMeat.put(stock,false,0,750);b.cargo[0].add(20,2000);b.perception=1;f.e.state.accounts.put(b.account,101L);long money=f.t.money(f.s);CityRoute.exchange(f.s,b,head);h.assertTrue(b.cargo[0].count==20&&RuralRules.count(stock,false)==0&&RuralMeat.total(stock)==750&&f.t.money(f.s)==money,"Unaffordable post-net profit tax rejects entire trade before any transfer");
 }h.succeed();}
 @GameTest(template="empty") public static void starvingFamilyCanBuyRealSurplusCookedMeat(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){
  CityTestsModel.configure(f);var head=f.t.head(f.village.id);var hp=f.r.state.people.get(head.worker);var family=f.r.state.families.values().stream().filter(v->v.city.equals(f.village.id)&&!v.members.contains(hp.id)).findFirst().orElseThrow();var own=f.r.stocks(family.id);var stock=f.r.stocks(hp.family);
  for(var x:own){x.wheat=x.bread=0;RuralMeat.clear(x);}for(var x:stock){x.wheat=x.bread=0;RuralMeat.clear(x);}RuralMeat.put(stock,true,1,40);long total=f.t.money(f.s);var money=new HashMap<UUID,Long>();for(var p:f.r.state.people.values())money.put(p.id,f.e.balance(CityEconomy.person(p.id)));long before=RuralRules.balance(family,money),personTotal=money.values().stream().mapToLong(Long::longValue).sum();
  RuralFoodAccess.emergency(f.r,hp,family,own,stock,money,false);int bought=RuralMeat.total(own);h.assertTrue(bought>0&&RuralMeat.total(stock)==40-bought,"Hungry customers can actually buy existing edible meat");h.assertTrue(money.values().stream().mapToLong(Long::longValue).sum()==personTotal,"Private food payments conserve person balances");h.assertTrue(RuralRules.balance(family,money)<before,"No free food or money");
 }h.succeed();}
 @GameTest(template="empty") public static void fullMeatWarehouseAcceptsPaidGrainThroughAtomicBackhaul(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){
  CityTestsModel.configure(f);var b=f.t.state.businesses.values().stream().filter(x->x.kind.equals("procurement")).findFirst().orElseThrow();var head=f.t.head(f.village.id);var hp=f.r.state.people.get(head.worker);var stock=f.r.stocks(hp.family);for(var x:stock){x.wheat=x.bread=0;RuralMeat.clear(x);}RuralMeat.put(stock,false,0,750);b.cargo[0].add(20,2000);b.perception=1;long money=f.t.money(f.s),grain=f.t.grain(f.s),meat=f.t.meat(f.s);
  CityRoute.exchange(f.s,b,head);h.assertTrue(RuralRules.count(stock,false)==20&&RuralMeat.total(stock)==730&&b.meatCargo[0].count==20,"20 actual grain replaces20 actual meat in full750warehouse");h.assertTrue(f.t.money(f.s)==money&&f.t.grain(f.s)==grain&&f.t.meat(f.s)==meat,"Net settlement conserves inventory and money, including in-transit meat");
 }h.succeed();}
 @GameTest(template="empty") public static void noMoneyCannotBuyAndNoTradeCannotBorrowWarehouseSpace(GameTestHelper h){h.assertTrue(FoodMarketRules.shipment(2,6,1,200,192,80)==40,"Finite wallet limits reverse imports");h.assertTrue(FoodMarketRules.shipment(6,2,1,200,192,1000)==0,"No guaranteed profitable trip");h.assertTrue(FoodMarketRules.bidTenths(100,4)==32,"Shop never offers more than realistic resale proceeds");h.succeed();}
}
