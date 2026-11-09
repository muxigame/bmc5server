package com.muxigame.city;

import java.util.*;

/** Real inventory and real family payments, never a grant or an unlimited emergency vendor. */
public final class RuralFoodAccess {
 static boolean active(RuralSettlement d,UUID city){return d.serverContext!=null&&CityCurrency.model(d.serverContext,city);}
 static int retail(RuralSettlement d,RuralRules.Person head,int good,List<RuralRules.Stock> warehouse){int base=RuralMarket.baseSell(good);if(good<2||!active(d,d.state.families.get(head.family).city))return base;return FoodMarketRules.retail(base,RuralMarket.count(warehouse,good),Math.max(RuralMarket.policy(head,good).target,CityTrade.population(d.serverContext,d.state.families.get(head.family).city)/10));}
 static int buyMeat(RuralSettlement d,RuralRules.Person head,RuralRules.Family home,List<RuralRules.Stock> own,List<RuralRules.Stock> market,int wanted,Map<UUID,Long> money,boolean canCook){
  int done=0;var goods=new ArrayList<Integer>();for(int g=canCook?2:7;g<12;g++)if(RuralMarket.count(market,g)>0)goods.add(g);goods.sort(Comparator.comparingInt(g->retail(d,head,g,market)));
  for(int g:goods){int q=retail(d,head,g,market);int n=(int)Math.min(Math.min(wanted-done,RuralCooking.available(d,head,g,market)),Math.min(RuralRules.capacity(own),RuralRules.balance(home,money)/q));if(n<=0)continue;if(!RuralRules.pay(home,head.id,n*(long)q,money))continue;FoodMoves.move(market,own,g,n);d.audit(home.city).tradeSilver+=n*(long)q;d.state.meatBought+=n;d.state.tradedSilver+=n*(long)q;done+=n;if(done>=wanted)break;}return done;
 }
 static void emergency(RuralSettlement d,RuralRules.Person head,RuralRules.Family home,List<RuralRules.Stock> own,List<RuralRules.Stock> market,Map<UUID,Long> money,boolean canCook){if(!active(d,home.city)||home.members.contains(head.id))return;int need=RuralRules.demand(home,d.state.people),ready=RuralRules.count(own,true)+own.stream().mapToInt(s->Arrays.stream(s.cooked).sum()).sum();if(canCook)ready+=RuralRules.count(own,false)+own.stream().mapToInt(s->Arrays.stream(s.raw).sum()).sum();int missing=Math.max(0,need*2-ready);if(missing>0)buyMeat(d,head,home,own,market,missing,money,canCook);}
}
