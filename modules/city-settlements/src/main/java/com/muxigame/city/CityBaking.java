package com.muxigame.city;
import java.util.*;
import net.minecraft.server.MinecraftServer;

/** One saved forecast per local operating day. Targets are orders of intent, never free output. */
public final class CityBaking {
 static void plan(MinecraftServer s,UUID city){var t=CityTrade.get(s);var d=RuralSettlement.get(s);var town=t.state.towns.get(city);long day=Math.floorDiv(d.state.simulationTicks,24000);if(town.breadPlanDay==day)return;town.breadPlanDay=day;var bakers=t.state.businesses.values().stream().filter(b->b.city.equals(city)&&b.kind.equals("baker")&&t.usable(s,b)).sorted(Comparator.comparing(b->b.id)).toList();if(bakers.isEmpty())return;
  double daily=0;int deficit=0;for(var f:d.state.families.values())if(f.city.equals(city)){var home=t.state.households.get(f.id);int demand=RuralRules.demand(f,d.state.people);double self=home!=null&&FamilyLivelihood.processor(s,f)&&!home.preferPrepared?HouseholdCooking.cityDaily(home):0;daily+=Math.max(0,demand*2/3.0-self);int ready=RuralCooking.ready(d.stocks(f.id));deficit+=Math.max(0,DataCityRules.foodReserve(demand,3)-ready);}
  int existing=t.stock(s,city,1)+bakers.stream().mapToInt(b->b.stock[1].count).sum(),wanted=Math.max(0,(int)Math.ceil(daily)+deficit+CityRelief.forecast(s,city)-existing);int share=wanted/bakers.size(),remainder=wanted%bakers.size();for(int i=0;i<bakers.size();i++){var b=bakers.get(i);int extra=Math.floorMod(i-(int)Math.floorMod(day,bakers.size()),bakers.size())<remainder?1:0;double variation=.9+new Random(b.id.getLeastSignificantBits()^day).nextDouble()*.2;b.breadRemaining=Math.min(80,(int)Math.round((share+extra)*variation));b.breadPlanned=b.breadRemaining;b.breadDay=day;}t.setDirty();
 }
}
