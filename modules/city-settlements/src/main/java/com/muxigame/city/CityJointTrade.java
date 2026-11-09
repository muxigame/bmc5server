package com.muxigame.city;

import java.util.*;

/** One atomic sale-and-provision transaction. No inventory space or money is granted. */
public final class CityJointTrade {
 static RuralRules.Stock copy(RuralRules.Stock v){var s=new RuralRules.Stock();s.family=v.family;s.ordinarySlots=v.ordinarySlots;s.wheat=v.wheat;s.bread=v.bread;s.raw=v.raw.clone();s.cooked=v.cooked.clone();for(int g=0;g<12;g++)s.ages[g]=v.ages[g].copy();return s;}
 static void commit(List<RuralRules.Stock> real,List<RuralRules.Stock> draft){for(int i=0;i<real.size();i++){var a=real.get(i);var b=draft.get(i);a.wheat=b.wheat;a.bread=b.bread;System.arraycopy(b.raw,0,a.raw,0,5);System.arraycopy(b.cooked,0,a.cooked,0,5);for(int g=0;g<12;g++)a.ages[g]=b.ages[g].copy();}}
 static int provision(RuralSettlement d,RuralRules.Person head,RuralRules.Family market,RuralRules.Family home,List<RuralRules.Stock> stores,List<RuralRules.Stock> warehouse,boolean wheat,int price,Map<UUID,Long> money){
  if(d.serverContext==null||!CityCurrency.model(d.serverContext,home.city)||home.id.equals(market.id)||price<=0)return 0;
  int deficit=Math.max(0,DataCityRules.foodReserve(RuralRules.demand(home,d.state.people),3)-RuralRules.count(stores,true)-(wheat?RuralRules.count(stores,false):0));long cash=RuralRules.balance(home,money);
  if(deficit==0||cash>=(long)deficit*price||RuralMeat.total(stores)==0)return 0;
  int good=wheat?0:1,available=RuralCooking.available(d,head,good,warehouse);if(available==0)return 0;
  int[] chosen=new int[10],best=null;Map<String,Integer> tails=new LinkedHashMap<>(),bestTails=null;long value=0,bestValue=0,bestNet=0;int sold=0,bestGrain=0;
  UUID seller=home.members.getFirst();long reserve=CityTrade.headReserve(d.serverContext,home.city);
  // Normal households hold <=30 food. Bound exceptional administrator-sized stores to a few stacks per cycle.
  outer:for(int type=0;type<10;type++)for(int n=0;n<RuralMarket.count(stores,type+2);n++){
   if(sold>=192)break outer;String key=market.id+":"+seller+":"+(type+2);int old=tails.getOrDefault(key,d.state.priceRemainders.getOrDefault(key,0));
   int cost=old+RuralMarket.purchaseQuote(d,head,type+2,RuralMarket.count(warehouse,type+2)+chosen[type],RuralMarket.baseBuy(type+2));value+=cost/10;tails.put(key,cost%10);chosen[type]++;sold++;
   int grain=(int)Math.min(Math.min(deficit,available),Math.min(RuralRules.capacity(stores)+sold,(cash+value)/price));long net=value-(long)grain*price;
   if(grain<=bestGrain||RuralRules.capacity(warehouse)+grain<sold||net>Math.max(0,RuralRules.balance(market,money)-reserve))continue;
   bestGrain=grain;best=chosen.clone();bestTails=new LinkedHashMap<>(tails);bestValue=value;bestNet=net;if(grain==deficit)break outer;
  }
  if(best==null)return 0;
  var homeDraft=stores.stream().map(CityJointTrade::copy).toList();var headDraft=warehouse.stream().map(CityJointTrade::copy).toList();int meat=0;var portions=new ArrayList<FoodAge.Portion>();
  for(int type=0;type<10;type++){meat+=best[type];var portion=FoodMoves.remove(homeDraft,type+2,best[type]);if(portion.count()!=best[type])return 0;portions.add(portion);}
  var grain=FoodMoves.remove(headDraft,good,bestGrain);if(grain.count()!=bestGrain||FoodMoves.add(homeDraft,good,bestGrain,grain.age())!=bestGrain)return 0;
  for(int type=0;type<10;type++)if(FoodMoves.add(headDraft,type+2,best[type],portions.get(type).age())!=best[type])return 0;
  var moneyDraft=new HashMap<>(money);if(bestNet>=0){if(!RuralRules.pay(market,seller,bestNet,moneyDraft))return 0;}else if(!RuralRules.pay(home,head.id,-bestNet,moneyDraft))return 0;
  long newKeys=bestTails.keySet().stream().filter(k->!d.state.priceRemainders.containsKey(k)).count();if(d.state.priceRemainders.size()+newKeys>8192)return 0;
  commit(stores,homeDraft);commit(warehouse,headDraft);money.clear();money.putAll(moneyDraft);d.state.priceRemainders.putAll(bestTails);
  var t=CityTrade.get(d.serverContext);t.state.jointTransactions++;t.state.jointMeat+=meat;t.state.jointGrain+=bestGrain;d.state.meatSold+=meat;d.state.tradedSilver+=Math.abs(bestNet);d.audit(home.city).tradeSilver+=Math.abs(bestNet);
  CityEconomy.get(d.serverContext).log(d.state.simulationTicks,"RURAL_JOINT_SETTLEMENT",CityEconomy.person(head.id),CityEconomy.person(seller),Math.abs(bestNet),"肉"+meat+"/主粮"+bestGrain+"；肉款"+bestValue+"；净额"+bestNet);
  d.log("缺粮家庭联合成交：肉"+meat+"换主粮"+bestGrain+"，净收款"+bestNet);t.setDirty();return bestGrain;
 }
}
