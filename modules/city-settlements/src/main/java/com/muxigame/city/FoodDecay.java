package com.muxigame.city;
import java.util.*;
import net.minecraft.server.MinecraftServer;

/** Opt-in model adapter: food clocks belong to data cities, not loaded blocks or the sun. */
public final class FoodDecay {
 public static final class Loss {public long grain,meat,costCents;}
 static long grain(MinecraftServer s){return CityTrade.get(s).state.decay.values().stream().mapToLong(v->v.grain).sum();}
 static long meat(MinecraftServer s){return CityTrade.get(s).state.decay.values().stream().mapToLong(v->v.meat).sum();}
 static int age(FoodAge.State age,int count,int good,long dt){age.ensure(count);return age.advance(dt,FoodAge.period(good));}
 static int lot(TradeRules.Lot lot,int good,long dt,Loss loss){int n=age(lot.freshness,lot.count,good,dt);if(n>0){long cost=n==lot.count?lot.cents:lot.cents*n/lot.count;lot.count-=n;lot.cents-=cost;loss.costCents+=cost;}return n;}
 static void step(MinecraftServer s,long now){var t=CityTrade.get(s);var d=RuralSettlement.get(s);boolean changed=false;for(var town:t.state.towns.values()){
  if(!town.model||!RuralSettlement.active(s,town.city))continue;if(!town.foodStarted){town.foodStarted=true;town.foodClock=now;continue;}long dt=(now-town.foodClock)/600*600;if(dt<=0)continue;town.foodClock+=dt;changed=true;var loss=t.state.decay.computeIfAbsent(town.city,k->new Loss());
  for(var b:d.state.boxes.values())if(b.present&&b.city.equals(town.city))for(int g=0;g<12;g++){int n=age(b.stock.ages[g],FoodMoves.count(b.stock,g),g,dt);FoodMoves.set(b.stock,g,FoodMoves.count(b.stock,g)-n);if(g<2)loss.grain+=n;else loss.meat+=n;}
  for(var p:d.state.people.values())if(d.state.families.containsKey(p.family)&&d.state.families.get(p.family).city.equals(town.city)){int n=age(p.pendingAge,p.pendingWheat,0,dt);p.pendingWheat-=n;loss.grain+=n;}
  for(var b:t.state.businesses.values())if(b.city.equals(town.city)){for(var lots:List.of(b.stock,b.listed,b.cargo))for(int g=0;g<2;g++)loss.grain+=lot(lots[g],g,dt,loss);for(var lots:List.of(b.meatCargo,b.meatListed))for(int g=0;g<10;g++)loss.meat+=lot(lots[g],g+2,dt,loss);}
  var city=CityData.get(s).require(town.city);for(int g=0;g<12;g++){String id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(NpcStorage.item(g)).toString();int count=city.works.resources.getOrDefault(id,0);var a=city.works.foodAges.computeIfAbsent(id,k->new FoodAge.State());int n=age(a,count,g,dt);if(n>0){city.works.resources.put(id,count-n);if(g<2)loss.grain+=n;else loss.meat+=n;}var m=city.housing.market.get(id);if(m!=null){int spoiled=age(m.freshness,m.stock,g,dt);m.stock-=spoiled;if(g<2)loss.grain+=spoiled;else loss.meat+=spoiled;}}
 }if(!changed)return;CityRoute.pricesChanged();NpcStorage.refresh(s);t.setDirty();d.setDirty();CityData.get(s).setDirty();}
}
