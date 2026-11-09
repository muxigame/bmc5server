package com.muxigame.city;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
@GameTestHolder(CityMod.ID) @PrefixGameTestTemplate(false)
public class CityTestsFiscalNumbers {
 @GameTest(template="empty") public static void earlyIssuePreservesExistingFoodBudget(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){CityTestsModel.configure(f);var p=CityCurrency.policy(f.s,f.city.id);p.populationMint=false;p.issuing=false;f.e.state.accounts.put(CityEconomy.treasury(f.city.id),560L);long total=f.t.money(f.s);p.clock=24000;CityCurrency.settle(f.s);h.assertTrue(p.issued==80&&f.e.balance(CityEconomy.treasury(f.city.id))==640&&f.t.money(f.s)==total+80,"Four payroll days starts issue below new five-day line, capped two per adult");h.assertTrue(CityRelief.budget(f.s,f.city.id)==220,"Food still reserves three payroll days, leaving real money for purchasing");}h.succeed();}
 @GameTest(template="empty") public static void merchantProfitTaxTransfersFifteenWithoutNewMoney(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){CityTestsModel.configure(f);var b=f.t.state.businesses.values().stream().filter(v->v.kind.equals("procurement")).findFirst().orElseThrow();b.expenseCents=0;b.profitCents=0;long total=f.t.money(f.s),cash=f.e.balance(b.account),pub=f.e.balance(CityEconomy.treasury(b.city)),tax=b.tax;f.t.profit(f.s,b,100,0);h.assertTrue(f.e.balance(b.account)==cash-15&&f.e.balance(CityEconomy.treasury(b.city))==pub+15&&b.tax==tax+15&&f.t.money(f.s)==total,"Fifteen percent of100 realized profit transfers existing coins; it is not turnover or minting");}h.succeed();}
}

