package com.muxigame.city;
import java.util.*;

/** Use real private harvest before market purchases, without forecasting unripe crops. */
public final class RuralStaples {
 static boolean grower(RuralSettlement d,RuralRules.Family f){if(!d.workers(f,RuralRules.Role.FARMER).isEmpty())return true;return !d.workers(f,RuralRules.Role.COOK).isEmpty()&&RuralRules.members(f,d.state.people).stream().noneMatch(p->Set.of(RuralRules.Role.FARMER,RuralRules.Role.HUNTER,RuralRules.Role.MERCHANT,RuralRules.Role.ARTISAN,RuralRules.Role.VAGRANT).contains(p.role));}
 static int deficit(RuralSettlement d,RuralRules.Family f,List<RuralRules.Stock> stocks){return Math.max(0,DataCityRules.foodReserve(RuralRules.demand(f,d.state.people),3)-RuralRules.count(stocks,false)-RuralRules.count(stocks,true));}
 static int collect(RuralSettlement d,RuralRules.Family f,List<RuralRules.Stock> stocks){int done=0;for(var p:RuralRules.members(f,d.state.people)){int n=Math.min(p.pendingWheat,RuralRules.capacity(stocks));if(n<=0)continue;var part=RuralStalls.take(p,n);CityEconomy.require(FoodMoves.add(stocks,0,n,part.age())==n,"Mature private harvest capacity changed");done+=n;}d.state.wheatProduced+=done;d.audit(f.city).wheatHarvested+=done;return done;}
}
