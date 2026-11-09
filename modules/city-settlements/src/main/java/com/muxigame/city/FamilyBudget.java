package com.muxigame.city;
import java.util.*;
import net.minecraft.server.MinecraftServer;

/** One family cash budget; transfers within a family are not earnings. */
public final class FamilyBudget {
 public static final class Budget {public long day=-1,opening,spent,used;public double surplus,allowance;}
 static void validate(Map<UUID,Budget> values){CityEconomy.require(values!=null&&values.size()<=4096,"家庭预算记录过多");for(var p:values.values())CityEconomy.require(p!=null&&p.day>=-1&&p.opening>=0&&p.spent>=0&&p.used>=0&&Double.isFinite(p.surplus)&&Double.isFinite(p.allowance)&&p.allowance>=0,"家庭预算数值无效");}
 static long owned(MinecraftServer s,RuralRules.Family f){var t=CityTrade.get(s);var e=CityEconomy.get(s);var accounts=new HashSet<String>();for(var id:f.members)accounts.add(CityEconomy.person(id));for(var b:t.state.businesses.values())if(f.members.contains(b.owner))accounts.add(b.account);return accounts.stream().mapToLong(e::balance).sum();}
 static Budget policy(MinecraftServer s,RuralRules.Family f){return CityTrade.get(s).state.familyBudgets.computeIfAbsent(f.id,k->new Budget());}
 static void daily(MinecraftServer s,RuralRules.Family f,long day){var p=policy(s,f);if(p.day==day)return;long cash=owned(s,f);if(p.day>=0)p.surplus=(p.surplus*9+cash-p.opening+p.spent)/10;p.allowance+=Math.max(0,p.surplus*.75);p.opening=cash;p.spent=0;p.used=0;p.day=day;}
 static long processing(MinecraftServer s,RuralRules.Family f){if(!CityTrade.urban(s,f.city)||!CityOpportunities.bakery(s,f.city))return 0;var d=RuralSettlement.get(s);var h=CityTrade.get(s).state.households.get(f.id);int needed=Math.max(0,DataCityRules.foodReserve(RuralRules.demand(f,d.state.people),3)-RuralCooking.ready(d.stocks(f.id)));double home=h!=null&&FamilyLivelihood.processor(s,f)?3*HouseholdCooking.cityDaily(h):0;return (long)Math.ceil(Math.max(0,needed-home))*CityTrade.get(s).state.config.bakeFee;}
 static long reserve(MinecraftServer s,RuralRules.Family f){var d=RuralSettlement.get(s);var t=CityTrade.get(s);var stores=d.stocks(f.id);int needed=Math.max(0,DataCityRules.foodReserve(RuralRules.demand(f,d.state.people),3)-RuralCooking.ready(stores));if(!FamilyLivelihood.processor(s,f))return needed*(long)t.quote(s,f.city,1,d.state.simulationTicks);int raw=RuralRules.count(stores,false);int missing=Math.max(0,needed-raw);return (missing>0?missing*(long)t.quote(s,f.city,0,d.state.simulationTicks):0)+processing(s,f);}
 static long spare(MinecraftServer s,RuralRules.Family f,UUID person){var p=policy(s,f);long limit=Math.max(0,(long)Math.floor(p.allowance));return Math.max(0,Math.min(limit,Math.min(CityEconomy.get(s).balance(CityEconomy.person(person)),CityTrade.get(s).familyMoney(s,f)-reserve(s,f))));}
 static void spent(MinecraftServer s,RuralRules.Family f,long amount){spent(s,f,amount,amount);}
 static void spent(MinecraftServer s,RuralRules.Family f,long amount,long external){var p=policy(s,f);p.used+=Math.max(0,amount);p.allowance=Math.max(0,p.allowance-Math.max(0,amount));p.spent+=Math.max(0,external);}
}

