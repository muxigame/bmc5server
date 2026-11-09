package com.muxigame.city;

import java.util.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CityMod.ID) @PrefixGameTestTemplate(false)
public class CityTestsMaterialImports {
 static final String ITEM="minecraft:oak_planks";
 static void setup(CityTestsScale.Fixture f){CityTestsModel.configure(f);for(var m:f.village.housing.market.values())m.stock=m.cap;var a=f.village.housing.market.get(ITEM);a.priceCents=101;a.stock=0;a.cap=64;var b=f.city.housing.market.get(ITEM);b.stock=64;f.village.housing.materialImportSegment=-1;}
 @GameTest(template="empty") public static void actualWholesalePaysRotatingSuppliersAndConservesBothCities(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){
  setup(f);long money=f.t.money(f.s);var score=CityTestsLongRun.materialScore(f);var sellers=f.t.state.businesses.values().stream().filter(b->b.city.equals(f.city.id)&&b.kind.equals("supplier")).sorted(Comparator.comparing(b->b.id)).toList();long a=f.e.balance(sellers.get(0).account),b=f.e.balance(sellers.get(1).account);var source=f.city.housing.market.get(ITEM);var dest=f.village.housing.market.get(ITEM);
  CityMaterialImports.step(f.s,0);h.assertTrue(source.stock==48&&dest.stock==16&&f.e.balance(sellers.get(0).account)==a+8,"First batch transfers real16, pays first supplier8 with96cent tail at101cent retail");CityMaterialImports.step(f.s,0);h.assertTrue(dest.stock==16,"Same segment cannot repeat");CityMaterialImports.step(f.s,3000);h.assertTrue(source.stock==32&&dest.stock==32&&f.e.balance(sellers.get(1).account)==b+8,"Next batch goes to next supplier");
  h.assertTrue(f.t.money(f.s)==money&&CityTestsLongRun.materialScore(f).equals(score),"Currency and each city's imported/exported material ledger reconcile");
 }h.succeed();}
 @GameTest(template="empty") public static void ruralFreeSupplyStopsWithoutDeletingExistingAssets(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){
  setup(f);var m=f.village.housing.market.get(ITEM);m.stock=7;long supplied=m.supplied;CityHousing.advanceSupply(f.village.housing,2400);h.assertTrue(m.stock==7&&m.supplied==supplied,"Retain original7, no additional free rural materials");var c=f.city.housing.market.get(ITEM);c.stock=0;CityHousing.advanceSupply(f.city.housing,1200);h.assertTrue(c.stock>0,"Existing temporary urban production source remains explicit");
 }h.succeed();}
 @GameTest(template="empty") public static void missingMoneyStockOrWorkersCannotFabricateMaterials(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){
  setup(f);var head=f.t.liveHead(f.s,f.village.id);f.e.state.accounts.put(head.account,0L);CityMaterialImports.step(f.s,0);h.assertTrue(f.village.housing.market.get(ITEM).stock==0,"No money cannot import");f.e.state.accounts.put(head.account,1000L);f.city.housing.market.get(ITEM).stock=0;CityMaterialImports.step(f.s,3000);h.assertTrue(f.village.housing.market.get(ITEM).stock==0,"No source stock cannot import");f.city.housing.market.get(ITEM).stock=64;for(var b:f.t.state.businesses.values())if(b.city.equals(f.city.id)&&b.kind.equals("supplier"))b.closing=true;CityMaterialImports.step(f.s,6000);h.assertTrue(f.village.housing.market.get(ITEM).stock==0,"No usable city supplier cannot import");
 }h.succeed();}
 @GameTest(template="empty") public static void saveRetainsCursorAndVendorSpecificFractionalInvoices(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){
  setup(f);CityMaterialImports.step(f.s,0);String saved=CityEconomy.JSON.toJson(f.village.housing);f.village.housing=CityEconomy.JSON.fromJson(saved,CityHousing.State.class);long cash=f.t.money(f.s);CityMaterialImports.step(f.s,0);h.assertTrue(f.village.housing.market.get(ITEM).stock==16&&f.village.housing.imported.get(ITEM)==16&&f.village.housing.materialImportCursor==1&&f.t.money(f.s)==cash,"Reload preserves stock, import ledger and batch cursor without repeat");h.assertTrue(f.village.housing.tails.entrySet().stream().anyMatch(e->e.getKey().startsWith("material-import|")&&e.getValue()==96),"Fractional invoice belongs to its actual vendor");
 }h.succeed();}
 @GameTest(template="empty") public static void numericQuotesHaveOneConsistentProcurementBasis(GameTestHelper h){h.assertTrue(RuralMarket.baseBuy(0)==2&&RuralMarket.baseSell(0)==3&&CityMaterialImports.cents(40)==22,"Wheat reference2; grain retail base unchanged; material wholesale55percent");h.succeed();}
}





