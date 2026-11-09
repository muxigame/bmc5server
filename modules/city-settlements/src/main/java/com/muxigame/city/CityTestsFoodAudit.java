package com.muxigame.city;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CityMod.ID) @PrefixGameTestTemplate(false)
public class CityTestsFoodAudit {
 @GameTest(template="empty") public static void publicCookedMeatConsumptionHasOneQuantityDebit(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){CityTestsModel.configure(f);f.city.works.resources.put("minecraft:cooked_beef",4);long meat=f.t.meat(f.s),eaten=f.t.meatEaten(f.s),money=f.t.money(f.s);int got=CityWorksFinance.publicMeals(f.s,f.city,3);h.assertTrue(got==3&&f.city.works.resources.get("minecraft:cooked_beef")==1&&f.t.meat(f.s)+f.t.meatEaten(f.s)==meat+eaten&&f.t.money(f.s)==money,"Existing emergency cooked-meat meals debit one real stock and one consumption ledger");}h.succeed();}
 @GameTest(template="empty") public static void temporaryWorkIndexCannotStaleAStorageOwnershipChange(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){CityTestsModel.configure(f);var homes=f.r.state.families.values().stream().filter(v->v.city.equals(f.city.id)).limit(2).toList();var a=homes.get(0);var b=homes.get(1);int beforeA=f.r.stocks(a.id).size(),beforeB=f.r.stocks(b.id).size();RuralSettlement.workStep(f.s,f.r);var box=f.r.state.boxes.values().stream().filter(v->a.id.equals(v.stock.family)).findFirst().orElseThrow();box.stock.family=b.id;h.assertTrue(f.r.stocks(a.id).size()==beforeA-1&&f.r.stocks(b.id).size()==beforeB+1,"Work-loop index expires before any same-tick storage ownership input; no cross-tick cache");}h.succeed();}
}
