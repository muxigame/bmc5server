package com.muxigame.city;
import java.util.*;
import net.minecraft.server.MinecraftServer;

/** Owners can take local food out of their own inventory; goods in transit are unavailable. */
public final class OwnerFood {
 static int draw(MinecraftServer s,RuralRules.Family family){if(!CityCurrency.model(s,family.city))return 0;var d=RuralSettlement.get(s);var t=CityTrade.get(s);var stores=d.stocks(family.id);boolean cook=FamilyLivelihood.processor(s,family);int ready=RuralRules.count(stores,true)+stores.stream().mapToInt(v->Arrays.stream(v.cooked).sum()).sum();if(cook)ready+=RuralRules.count(stores,false)+stores.stream().mapToInt(v->Arrays.stream(v.raw).sum()).sum();int wanted=Math.max(0,CityTrade.mealTarget(family,d)-ready),done=0;
  for(var b:t.state.businesses.values()){if(!family.city.equals(b.city)||!family.members.contains(b.owner))continue;for(int good:new int[]{1,0,7,8,9,10,11,2,3,4,5,6}){if(!cook&&(good==0||good>=2&&good<7))continue;var lots=good<2?List.of(b.listed[good],b.stock[good]):List.of(b.meatListed[good-2]);for(var lot:lots){int n=Math.min(wanted-done,Math.min(lot.count,RuralRules.capacity(stores)));if(n<=0)continue;long cost=lot.take(n);CityEconomy.require(FoodMoves.add(stores,good,n,lot.removedAge.age())==n,"Owner food draw capacity changed");b.foodDrawn+=n;b.foodDrawCostCents+=cost;done+=n;CityEconomy.get(s).log(d.state.simulationTicks,"OWNER_FOOD_DRAW",b.account,CityEconomy.person(b.owner),0,"实物自用x"+n+"，账面成本分="+cost+"；不记销售或现金收入");if(done>=wanted){CityRoute.pricesChanged();t.setDirty();d.setDirty();return done;}}}
  }if(done>0){CityRoute.pricesChanged();t.setDirty();d.setDirty();}return done;
 }
}
