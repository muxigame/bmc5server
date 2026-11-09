package com.muxigame.city;

import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CityMod.ID) @PrefixGameTestTemplate(false)
public class CityTestsPopulationMint {
 @GameTest(template="empty") public static void richCityMintsOncePerCompletedDayIntoTreasury(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){
  CityTestsModel.configure(f);var p=CityCurrency.policy(f.s,f.city.id);long total=f.t.money(f.s),before=f.e.balance(CityEconomy.treasury(f.city.id));long privateCash=f.r.state.people.keySet().stream().mapToLong(id->f.e.balance(CityEconomy.person(id))).sum();int population=CityTrade.population(f.s,f.city.id);
  p.clock=23999;CityCurrency.settle(f.s);h.assertTrue(p.minted==0,"Incomplete day cannot mint");p.clock=24000;CityCurrency.settle(f.s);
  h.assertTrue(population==40&&p.minted==40&&p.issued==40&&f.t.money(f.s)==total+40&&f.e.balance(CityEconomy.treasury(f.city.id))==before+40,"Rich city still mints exactly one per living resident, into treasury");
  h.assertTrue(privateCash==f.r.state.people.keySet().stream().mapToLong(id->f.e.balance(CityEconomy.person(id))).sum(),"Mint is not a private wallet grant");
  f.t.state=CityEconomy.JSON.fromJson(CityEconomy.JSON.toJson(f.t.state),CityTrade.State.class);CityCurrency.settle(f.s);h.assertTrue(CityCurrency.minted(f.s)==40&&f.t.money(f.s)==total+40,"Save/reload cannot replay completed day");
 }h.succeed();}
 @GameTest(template="empty") public static void populationIncludesChildrenButNotInfantsOrDead(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){
  CityTestsModel.configure(f);var people=f.r.state.people.values().stream().filter(p->f.r.state.families.get(p.family).city.equals(f.city.id)).toList();people.get(0).age=RuralRules.Age.CHILD;people.get(1).age=RuralRules.Age.INFANT;people.get(2).dead=true;var p=CityCurrency.policy(f.s,f.city.id);p.clock=24000;CityCurrency.settle(f.s);h.assertTrue(p.minted==38&&p.days.getFirst().population==38,"One child counts, infant and dead do not");
  people.get(3).dead=true;p.clock=48000;CityCurrency.settle(f.s);h.assertTrue(p.minted==75,"Next day reflects changed living population, no historical replay");
 }h.succeed();}
 @GameTest(template="empty") public static void oldIssueAndPopulationMintRemainSeparatelyAudited(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){
  CityTestsModel.configure(f);var p=CityCurrency.policy(f.s,f.city.id);f.e.state.accounts.put(CityEconomy.treasury(f.city.id),0L);long total=f.t.money(f.s);p.clock=24000;CityCurrency.settle(f.s);
  h.assertTrue(p.minted==40&&p.issued==120&&f.t.money(f.s)==total+120,"Legacy fiscal issue80 plus newly authorized mint40, both explicit and distinguishable");
  var rural=CityCurrency.policy(f.s,f.village.id);rural.clock=24000;CityCurrency.settle(f.s);h.assertTrue(rural.minted==0&&!rural.enabled,"Rural headman cannot mint");
 }h.succeed();}
 @GameTest(template="empty") public static void migrationEnablesNewRuleAndRetainsFiscalCursor(GameTestHelper h){
  var p=CityEconomy.JSON.fromJson("{\"enabled\":true,\"clock\":24000,\"lastDay\":0,\"issued\":17}",CityCurrency.Policy.class);
  h.assertTrue(p.populationMint&&p.lastDay==0&&p.issued==17&&p.minted==0,"Old policy adopts population rule without resetting day cursor, assets or past issuance");h.succeed();
 }
 @GameTest(template="empty") public static void accountLimitCannotOverflowOrCreateHiddenMoney(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){
  CityTestsModel.configure(f);var p=CityCurrency.policy(f.s,f.city.id);f.e.state.accounts.put(CityEconomy.treasury(f.city.id),CityEconomy.LIMIT-3);long total=f.t.money(f.s);p.clock=24000;CityCurrency.settle(f.s);
  h.assertTrue(p.minted==3&&f.e.balance(CityEconomy.treasury(f.city.id))==CityEconomy.LIMIT&&f.t.money(f.s)==total+3,"At account cap only recorded real balance increase is minted");
 }h.succeed();}
}
