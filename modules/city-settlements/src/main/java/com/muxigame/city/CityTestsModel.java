package com.muxigame.city;

import java.util.*;
import java.nio.file.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CityMod.ID) @PrefixGameTestTemplate(false)
public class CityTestsModel {
 static void configure(CityTestsScale.Fixture f){CityModel.configure(f.s,f.city,true);CityModel.configure(f.s,f.village,false);}
 @GameTest(template="empty",timeoutTicks=200) public static void independentNinetyDayNewModelReportsAttritionWithoutReset(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){
  configure(f);var result=f.run(90);result.put("issued",CityCurrency.issued(f.s));result.put("retired",CityCurrency.retired(f.s));result.put("jointTransactions",f.t.state.jointTransactions);result.put("currencyDays",f.t.state.currencies);try{Files.writeString(Path.of("model099-90day.json"),CityEconomy.JSON.toJson(result));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
  h.assertTrue(f.conserved,"Long-run issue/retirement and all goods remain audited; deaths are reported rather than reset");h.assertTrue(f.r.state.boxes.values().stream().allMatch(b->b.stock.wheat+b.stock.bread+RuralMeat.total(List.of(b.stock))<=10),"Long run does not exceed real box capacity");
 }h.succeed();}
 @GameTest(template="empty",timeoutTicks=200) public static void newModelHundredResidentsThirtyDaysAudited(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){
  configure(f);for(var c:List.of(f.city,f.village)){c.works.enabled=false;f.r.state.enabled.remove(c.id);}
  try{Files.writeString(Path.of("model099-setup.json"),CityEconomy.JSON.toJson(f.setup()));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
  CityModelLab.start(f.s,f.city.id,f.village.id);f.run(30);var run=f.t.state.modelRun;
  try{Files.writeString(Path.of("model099-automated.json"),CityEconomy.JSON.toJson(run));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
  h.assertTrue(run.done&&run.failure.isEmpty()&&f.conserved,"Actual new-model rules conserve money adjusted for explicit issue/retirement and all goods");
  h.assertTrue(run.samples.getLast().get("living").equals(100L),"Normal fresh100resident model: inspect all deaths rather than resetting them");
  h.assertTrue(CityCurrency.policy(f.s,f.city.id).days.size()==30,"One fiscal settlement per completed local day");
 }h.succeed();}
 @GameTest(template="empty") public static void fullHeadWarehouseTradesProtectedMeatForGrainWithZeroCash(GameTestHelper h){try(var f=new CityTestsNumeric.NumericFixture(h)){
  var head=f.trade.head(f.village.id);var hp=f.rural.state.people.get(head.worker);var market=f.rural.state.families.get(hp.family);var home=f.rural.state.families.values().stream().filter(x->x.city.equals(f.village.id)&&f.rural.workers(x,RuralRules.Role.HUNTER).size()>0).findFirst().orElseThrow();
  f.trade.state.towns.get(f.village.id).model=true;var stores=f.rural.stocks(home.id);var warehouse=f.rural.stocks(market.id);for(var stock:new ArrayList<>(f.rural.state.boxes.values()))if(stock.stock.family.equals(home.id)||stock.stock.family.equals(market.id)){stock.stock.wheat=stock.stock.bread=0;Arrays.fill(stock.stock.raw,0);Arrays.fill(stock.stock.cooked,0);}RuralRules.put(warehouse,false,147);RuralMeat.put(warehouse,false,3,3);RuralMeat.put(stores,false,3,3);
  for(var id:home.members)f.economy.state.accounts.put(CityEconomy.person(id),0L);for(var id:market.members)f.economy.state.accounts.put(CityEconomy.person(id),0L);var p=RuralMarket.policy(hp,5);p.target=1;p.markup=0;p.discount=60;hp.wheatPrice=3;
  var money=new HashMap<UUID,Long>();for(var person:f.rural.state.people.values())money.put(person.id,f.economy.balance(CityEconomy.person(person.id)));long total=money.values().stream().mapToLong(Long::longValue).sum();
  int traded=CityJointTrade.provision(f.rural,hp,market,home,stores,warehouse,true,3,money);
  h.assertTrue(traded==8&&RuralRules.count(stores,false)==8&&RuralMeat.total(stores)==0,"Three beef at8 buy8wheat at3: traded="+traded+" ownWheat="+RuralRules.count(stores,false)+" ownMeat="+RuralMeat.total(stores)+" warehouse="+RuralRules.count(warehouse,false)+" reserve="+CityTrade.headReserve(f.server,home.city)+" quote="+RuralMarket.purchaseQuote(f.rural,hp,5,RuralMeat.count(warehouse,false,3),20)+" model="+CityCurrency.model(f.server,home.city));
  h.assertTrue(RuralRules.capacity(warehouse)==5&&RuralMeat.total(warehouse)==6&&money.values().stream().mapToLong(Long::longValue).sum()==total,"No fabricated space, food or coins");
  int again=CityJointTrade.provision(f.rural,hp,market,home,stores,warehouse,true,3,money);h.assertTrue(again==0&&RuralRules.count(stores,false)==8,"Repeated calls cannot duplicate provisioning");
 }h.succeed();}
 @GameTest(template="empty") public static void independentMaterialVendorsKeepIndependentFractionalBalances(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){
  configure(f);var vendors=f.t.state.businesses.values().stream().filter(b->b.kind.equals("supplier")).toList();var a=vendors.get(0);var b=vendors.get(1);f.city.housing.market.get("minecraft:oak_planks").supplier=a.id;f.city.housing.market.get("minecraft:glass").supplier=b.id;
  var house=f.city.housing.houses.values().iterator().next();house.personal=false;long ca=f.e.balance(a.account),cb=f.e.balance(b.account),money=f.t.money(f.s);
  CityHousing.purchase(f.city,house,"minecraft:oak_planks",3,f.e);CityHousing.purchase(f.city,house,"minecraft:glass",2,f.e);h.assertTrue(f.e.balance(a.account)==ca&&f.e.balance(b.account)==cb,"Fractional invoices remain owed to their real vendor");
  CityHousing.purchase(f.city,house,"minecraft:oak_planks",2,f.e);CityHousing.purchase(f.city,house,"minecraft:glass",2,f.e);
  h.assertTrue(f.e.balance(a.account)==ca+1&&f.e.balance(b.account)==cb+1&&f.t.money(f.s)==money,"No cross-vendor penny stealing or minted purchase payments");
 }h.succeed();}
 @GameTest(template="empty") public static void equalPriceListingsRotateAcrossFiveSuppliers(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){
  configure(f);for(var b:f.t.state.businesses.values())if(b.city.equals(f.city.id))b.listed[0]=new TradeRules.Lot();var vendors=f.t.state.businesses.values().stream().filter(b->b.kind.equals("supplier")).toList();for(var b:vendors)b.listed[0].add(1,0);
  var home=f.r.state.families.values().stream().filter(x->x.city.equals(f.city.id)).findFirst().orElseThrow();long money=f.t.money(f.s);for(int n=0;n<5;n++)h.assertTrue(f.t.buyMarket(f.s,home,f.city.id,0,1,true)==1,"One real purchase");
  h.assertTrue(vendors.stream().allMatch(b->b.sold[0]==1)&&f.t.money(f.s)==money,"Each equally priced eligible seller takes its turn; no extra demand invented");
 }h.succeed();}
 @GameTest(template="empty") public static void issuanceIsCappedAndCannotRepeatAfterReload(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){
  configure(f);var p=CityCurrency.policy(f.s,f.city.id);p.populationMint=false;f.e.state.accounts.put(CityEconomy.treasury(f.city.id),100L);long money=f.t.money(f.s);p.clock=24000;CityCurrency.settle(f.s);
  h.assertTrue(p.issued==80&&p.retired==0&&f.t.money(f.s)==money+80&&p.days.getFirst().payroll==140,"40 adults issue at most80; five urban artisans cost140 per day and issue is explicit");
  f.t.state=CityEconomy.JSON.fromJson(CityEconomy.JSON.toJson(f.t.state),CityTrade.State.class);CityCurrency.settle(f.s);h.assertTrue(CityCurrency.policy(f.s,f.city.id).issued==80&&f.t.money(f.s)==money+80,"Reload does not re-issue same day");
 }h.succeed();}
 @GameTest(template="empty") public static void retirementUsesOnlyRealTaxAndNeverAgesPrivateWallets(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){
  configure(f);var p=CityCurrency.policy(f.s,f.city.id);p.populationMint=false;var supplier=f.t.state.businesses.values().stream().filter(b->b.kind.equals("supplier")).findFirst().orElseThrow();long money=f.t.money(f.s);long tax=f.t.tax(f.s,supplier.account,f.city.id,800,5,"unit-real-tax");h.assertTrue(tax==40&&f.t.money(f.s)==money,"Actual40tax only transfers money");
  long people=f.r.state.people.keySet().stream().mapToLong(id->f.e.balance(CityEconomy.person(id))).sum();p.clock=24000;CityCurrency.settle(f.s);h.assertTrue(p.retired==4&&p.issued==0&&f.t.money(f.s)==money-4,"Retirement10percent of realtax and capped0.1peradult");
  h.assertTrue(f.r.state.people.keySet().stream().mapToLong(id->f.e.balance(CityEconomy.person(id))).sum()==people,"Private wallets do not expire");
 }h.succeed();}
 @GameTest(template="empty") public static void unregisteredRuralCurrencyAndInsufficientWarehouseNeverMint(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){
  configure(f);var p=CityCurrency.policy(f.s,f.village.id);p.clock=30*24000;long money=f.t.money(f.s);CityCurrency.settle(f.s);h.assertTrue(!p.enabled&&p.issued==0&&p.retired==0&&f.t.money(f.s)==money,"Rural headman has no currency-creation authority");
 }h.succeed();}
}
