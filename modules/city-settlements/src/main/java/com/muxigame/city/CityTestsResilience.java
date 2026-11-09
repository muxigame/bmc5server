package com.muxigame.city;

import java.util.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CityMod.ID) @PrefixGameTestTemplate(false)
public class CityTestsResilience {
 @GameTest(template="empty",timeoutTicks=200) public static void existingPopulationCompletesAllThreeShocksWithoutRefill(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){
  for(var c:List.of(f.city,f.village)){c.housing.testSupply=false;c.works.enabled=false;f.r.state.enabled.remove(c.id);}
  CityRiskLab.start(f.s,f.city.id,f.village.id);var report=f.run(30);var r=f.t.state.riskRun;
  try{java.nio.file.Files.writeString(java.nio.file.Path.of("risk098-automated.json"),CityEconomy.JSON.toJson(r));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
  h.assertTrue(r.done&&r.failure.isEmpty()&&f.conserved,"Three shocks complete without new money or commodity accounting errors");
  h.assertTrue(r.destroyedWorkpoints==9&&r.craftedWorkbenches==9&&r.craftPlanks==36,"Nine replacement benches use36 existing planks, no external materials");
  h.assertTrue(r.damagedSites.entrySet().stream().allMatch(e->e.getValue().stream().noneMatch(p->CityWorks.at(CityData.get(f.s).require(e.getKey()),p).damaged.contains(p))),"All damaged artisan workpoints recover from finite stock");
  h.assertTrue(r.originalPeople.size()==100&&f.r.state.people.size()==100,"No replacement population or resurrection added");
 }h.succeed();}
 @GameTest(template="empty",timeoutTicks=200) public static void fiveEmployersPayTheirOwnFiveHelpers(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){
  long money=f.t.money(f.s);f.t.linkStaff();var helpers=f.t.state.businesses.values().stream().filter(b->b.kind.equals("supplier_helper")).toList();
  h.assertTrue(helpers.size()==5&&helpers.stream().map(b->b.employer).filter(Objects::nonNull).distinct().count()==5,"Legacy helpers migrate to five distinct employers");
  var before=new HashMap<UUID,Long>();for(var helper:helpers)before.put(helper.worker,f.e.balance(CityEconomy.person(helper.worker)));
  for(var b:f.t.state.businesses.values())if(b.kind.equals("supplier"))f.t.production(f.s,b,0);
  for(var helper:helpers)h.assertTrue(f.e.balance(CityEconomy.person(helper.worker))-before.get(helper.worker)==4,"Each helper receives one actual wage from own employer");
  h.assertTrue(f.t.money(f.s)==money,"Staff repair never changes total money");
 }h.succeed();}
 @GameTest(template="empty") public static void staffLinksSurviveReloadReorderAndEmployerClosure(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){
  f.t.linkStaff();var original=new HashMap<UUID,UUID>();for(var b:f.t.state.businesses.values())if(b.kind.equals("supplier_helper"))original.put(b.id,b.employer);
  f.t.state=CityEconomy.JSON.fromJson(CityEconomy.JSON.toJson(f.t.state),CityTrade.State.class);var reversed=new ArrayList<>(f.t.state.businesses.values());Collections.reverse(reversed);f.t.state.businesses.clear();for(var b:reversed)f.t.state.businesses.put(b.id,b);
  var employer=f.t.state.businesses.get(original.values().iterator().next());employer.closing=true;f.t.linkStaff();
  h.assertTrue(original.entrySet().stream().allMatch(e->e.getValue().equals(f.t.state.businesses.get(e.getKey()).employer)),"Reorder/reload/closure retain employer links and property ownership");
 }h.succeed();}
 @GameTest(template="empty") public static void emergencyDoesNotEndAfterFirstWorkshopRepair(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){
  for(var c:List.of(f.city,f.village)){
   var workshops=c.housing.houses.entrySet().stream().filter(e->CityWorks.workshop(c,e.getKey())).toList();for(var e:workshops)e.getValue().damaged.addAll(e.getValue().baseline.keySet());
   CityWorks.updateEmergency(f.s,c);h.assertTrue(c.works.emergency,"All artisan workshops destroyed enters emergency");workshops.getFirst().getValue().damaged.clear();CityWorks.updateEmergency(f.s,c);
   h.assertTrue(c.works.emergency,"One recovered workshop does not withdraw rescue access from the rest");for(var e:workshops)e.getValue().damaged.clear();CityWorks.updateEmergency(f.s,c);h.assertTrue(!c.works.emergency,"Recovery finishes after all affected workshops recover");
  }
 }h.succeed();}
 @GameTest(template="empty") public static void losingAllArtisansTriggersRescueWithOtherIndustriesIntact(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){
  for(var c:List.of(f.city,f.village)){for(var e:c.housing.houses.entrySet())if(CityWorks.artisanWorkshop(f.s,c,e.getKey()))e.getValue().damaged.addAll(e.getValue().baseline.keySet());CityWorks.updateEmergency(f.s,c);h.assertTrue(c.works.emergency,"Healthy farms/shops cannot conceal destruction of all artisan workplaces");}
 }h.succeed();}
 @GameTest(template="empty") public static void threeDayReserveCountsFoodOnceAcrossFamilyAccounts(GameTestHelper h){try(var f=new CityTestsNumeric.NumericFixture(h)){
  var family=f.rural.state.families.values().stream().filter(v->v.city.equals(f.village.id)).findFirst().orElseThrow();var stocks=f.rural.stocks(family.id);for(var stock:stocks){stock.wheat=stock.bread=0;Arrays.fill(stock.raw,0);Arrays.fill(stock.cooked,0);}var a=family.members.getFirst();var b=family.members.get(1);var home=new FamilyLivelihood.Home();home.processor=b;f.trade.state.households.put(family.id,home);h.assertTrue(FamilyLivelihood.processor(f.server,family),"Fixture has an actual usable home processor before raw grain counts as food");int price=f.trade.quote(f.server,f.village.id,0,0);
  h.assertTrue(CityWorksFinance.protectedFood(f.server,a)==8L*price,"Two adults need eight portions for three simulation days, not three meals");
  stocks.getFirst().bread=3;stocks.getFirst().cooked[0]=2;stocks.getFirst().wheat=1;
  h.assertTrue(CityWorksFinance.protectedFood(f.server,a)==2L*price,"Bread/cooked meat/processable wheat count once against reserve");
  f.economy.state.accounts.put(CityEconomy.person(a),5L);f.economy.state.accounts.put(CityEconomy.person(b),100L);
  h.assertTrue(CityWorksFinance.spendable(f.server,CityEconomy.person(a))==5,"Shared family reserve is not redundantly locked on each member");
  long total=f.trade.money(f.server);long available=CityWorksFinance.spendable(f.server,CityEconomy.person(b));CityWorksFinance.transfer(f.economy,CityEconomy.person(b),"test:materials",available,0,"TEST_SPEND");
  h.assertTrue(f.trade.familyMoney(f.server,family)==2L*price,"Maintenance leaves the actual family food buffer intact");h.assertTrue(f.economy.state.accounts.values().stream().mapToLong(Long::longValue).sum()==total,"Reserve locks no new money");
 }h.succeed();}
}
