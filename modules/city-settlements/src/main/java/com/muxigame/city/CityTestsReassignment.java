package com.muxigame.city;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CityMod.ID) @PrefixGameTestTemplate(false)
public class CityTestsReassignment {
 @GameTest(template="empty") public static void voluntaryArtisanTransferHasOneBuildingAndSurvivesActualReload(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){CityTestsModel.configure(f);var hunter=f.r.state.people.values().stream().filter(p->p.role==RuralRules.Role.HUNTER).findFirst().orElseThrow();var old=f.r.state.people.values().stream().filter(p->p.role==RuralRules.Role.ARTISAN&&f.village.residents.contains(p.id)).findFirst().orElseThrow();old.dead=true;hunter.nourishment=19;var family=f.r.state.families.get(hunter.family);for(var stock:f.r.stocks(family.id)){stock.wheat=stock.bread=0;RuralMeat.clear(stock);}for(var id:family.members)f.e.state.accounts.put(CityEconomy.person(id),0L);long money=f.t.money(f.s);CityOpportunities.daily(f.s,f.village,family);h.assertTrue(hunter.role==RuralRules.Role.ARTISAN&&f.r.state.sites.values().stream().filter(v->hunter.id.equals(v.worker)).count()==1&&f.village.housing.houses.values().stream().filter(v->v.workers.contains(hunter.id)).count()==1,"Voluntary reassignment releases the prior building as well as its old workpoint");var encoded=CityData.get(f.s).save(new CompoundTag(),h.getLevel().registryAccess());var restored=CityData.load(encoded,h.getLevel().registryAccess());h.assertTrue(restored.cities.size()==2&&restored.require(f.village.id).housing.houses.values().stream().filter(v->v.workers.contains(hunter.id)).count()==1&&f.t.money(f.s)==money,"The exact serialized city reloads under strict unique-worker validation, without losing towns or assets");}h.succeed();}
}
