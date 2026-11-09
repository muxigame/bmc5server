package com.muxigame.city;
import java.util.*;

/** Forecast only time already earned; never creates cooking credit, food or a future wage. */
public final class RuralCooking {
 static int ready(List<RuralRules.Stock> stocks){return RuralRules.count(stocks,true)+stocks.stream().mapToInt(v->Arrays.stream(v.cooked).sum()).sum();}
 static int householdReserve(RuralSettlement d,RuralRules.Family family){int need=RuralRules.demand(family,d.state.people),recovery=RuralRules.members(family,d.state.people).stream().mapToInt(p->(int)Math.ceil(Math.max(0,p.maximum()-p.nourishment))).sum();return Math.max(need*2,need+recovery);}
 static int available(RuralSettlement d,RuralRules.Person head,int good,List<RuralRules.Stock> stocks){int count=RuralMarket.count(stocks,good);var family=d.state.families.get(head.family);if(good!=1&&good<7||family==null||!RuralFoodAccess.active(d,family.city))return count;return Math.min(count,Math.max(0,ready(stocks)-householdReserve(d,family)));}
 static int breadWanted(RuralSettlement d,CityData.City city,RuralRules.Family family,List<RuralRules.Stock> stocks){int missing=Math.max(0,RuralRules.demand(family,d.state.people)-ready(stocks));if(missing==0||!RuralFoodAccess.active(d,city.id))return missing;var cooks=d.workers(family,RuralRules.Role.COOK);var processors=cooks.isEmpty()?d.workers(family,RuralRules.Role.FARMER):cooks;int credit=0;for(var p:processors)credit+=(int)(p.cookingCredit+HouseholdCooking.ruralRate(p)*RuralRules.efficiency(p.trainedHours)*p.workingTicks/36000.0*CityWorks.workerEfficiency(d.serverContext,city,p.id)/10000.0);int raw=RuralRules.count(stocks,false)+(cooks.isEmpty()?0:stocks.stream().mapToInt(v->Arrays.stream(v.raw).sum()).sum());return Math.max(0,missing-Math.min(raw,credit));}
}
