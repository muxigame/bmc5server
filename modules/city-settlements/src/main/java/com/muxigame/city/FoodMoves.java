package com.muxigame.city;
import java.util.*;

/** Numeric inventory adapter, shared by household, market and travel transactions. */
public final class FoodMoves {
 static int count(RuralRules.Stock s,int g){return g==0?s.wheat:g==1?s.bread:g<7?s.raw[g-2]:s.cooked[g-7];}
 static void set(RuralRules.Stock s,int g,int n){if(g==0)s.wheat=n;else if(g==1)s.bread=n;else if(g<7)s.raw[g-2]=n;else s.cooked[g-7]=n;}
 static FoodAge.Portion remove(List<RuralRules.Stock> stocks,int good,int wanted){var removed=new ArrayList<FoodAge.Portion>();for(var s:stocks){int before=count(s,good),n=Math.min(wanted,before);s.ages[good].ensure(before);removed.add(s.ages[good].take(n));set(s,good,before-n);wanted-=n;if(wanted==0)break;}return FoodAge.combine(removed);}
 static int add(List<RuralRules.Stock> stocks,int good,int wanted,double age){int left=wanted;for(var s:stocks){int before=count(s,good),n=Math.min(left,s.free());s.ages[good].ensure(before);s.ages[good].add(n,age);set(s,good,before+n);left-=n;if(left==0)break;}return wanted-left;}
 static int move(List<RuralRules.Stock> from,List<RuralRules.Stock> to,int good,int wanted){int available=from.stream().mapToInt(s->count(s,good)).sum(),room=to.stream().mapToInt(RuralRules.Stock::free).sum(),n=Math.min(wanted,Math.min(available,room));var p=remove(from,good,n);if(add(to,good,p.count(),p.age())!=n)throw new IllegalStateException("Food capacity changed during numeric transfer");return n;}
}
