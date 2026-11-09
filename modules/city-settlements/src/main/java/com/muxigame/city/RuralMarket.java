package com.muxigame.city;
import java.util.*;

/** Inventory quotes, atomic integer payments with persistent fractional balances, and explicit external demand. */
public final class RuralMarket {
 public static final String[] NAMES={"小麦","面包","生猪肉","生鸡肉","生羊肉","生牛肉","生兔肉","熟猪肉","熟鸡肉","熟羊肉","熟牛肉","熟兔肉"};
 public static final class Policy {public int target,low=50,high=150,markup=50,discount=60;public boolean enabled=true;}
 public static final class Audit {public long migrationSilver;public long wheatMatured,wheatHarvested,breadEaten,meatEaten,exportWheat,exportBread,exportMeat,externalSilver,tradeSilver;}
 static int baseBuy(int good){return good==0?2:good==1?4:RuralMeat.BUY[(good-2)%5]+(good>=7?1:0);}
 static int baseSell(int good){return good==0?3:good==1?5:RuralMeat.SELL[(good-2)%5]+(good>=7?1:0);}
 static Policy defaults(int good){var p=new Policy();p.target=Math.max(1,(good<2?80:40)/baseBuy(good));if(baseBuy(good)>=10){p.markup=20;p.discount=80;}return p;}
 static Policy policy(RuralRules.Person head,int good){return head.market.computeIfAbsent(good,k->defaults(good));}
 static int count(List<RuralRules.Stock> stock,int good){return good<2?RuralRules.count(stock,good==1):RuralMeat.count(stock,good>=7,(good-2)%5);}
 static int put(List<RuralRules.Stock> stock,int good,int n){return good<2?RuralRules.put(stock,good==1,n):RuralMeat.put(stock,good>=7,(good-2)%5,n);}
 static int take(List<RuralRules.Stock> stock,int good,int n){return good<2?RuralRules.take(stock,good==1,n):RuralMeat.take(stock,good>=7,(good-2)%5,n);}
 static UUID cityAccount(UUID city){return UUID.nameUUIDFromBytes(("city-market:"+city).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 static int buyFromCity(RuralSettlement d,CityData.City c,RuralRules.Family family,List<RuralRules.Stock> target,int good,int wanted,int penalty,Map<UUID,Long> money){String id=good==0?"minecraft:wheat":"minecraft:bread";var quote=c.housing.market.get(id);if(quote==null||quote.stock<=0||wanted<=0)return 0;int price=Math.max(1,(quote.priceCents+50)/100)+penalty;int count=(int)Math.min(Math.min(wanted,Math.min(quote.stock,RuralRules.capacity(target))),RuralRules.balance(family,money)/price);if(count<=0)return 0;UUID seller=cityAccount(c.id);CityEconomy.require(!family.members.contains(seller),"市场账本标识冲突");if(!RuralRules.pay(family,seller,(long)count*price,money))return 0;quote.freshness.ensure(quote.stock);var portion=quote.freshness.take(count);CityEconomy.require(FoodMoves.add(target,good,count,portion.age())==count,"城市市场交割容量不一致");quote.stock-=count;quote.sold+=count;d.state.tradedSilver+=(long)count*price;d.audit(c.id).tradeSilver+=(long)count*price;d.log("家庭向城市市场购买"+id+" x"+count+"，实付"+(long)count*price+"银币");return count;}
 static int quoteTenths(Policy p,int stock,int base){if(!p.enabled||p.target<=0)return base*10;double low=p.target*p.low/100.0,high=p.target*p.high/100.0,factor=1;
  if(stock<low&&low>0)factor+=p.markup/100.0*(1-stock/low);
  if(stock>high)factor-=p.discount/100.0*Math.min(1,(stock-high)/Math.max(1,p.target*2.0-high));
  return Math.max(1,(int)Math.round(base*10*factor));}
 static void validate(Policy p){CityEconomy.require(p!=null&&p.target>=1&&p.target<=40960&&p.low>=1&&p.low<=100&&p.high>=100&&p.high<200&&p.markup>=0&&p.markup<=100&&p.discount>=0&&p.discount<=90,"目标1—40960；缺货1—100%；过量100—199%；加价0—100%；压价0—90%");}
 static int purchaseQuote(RuralSettlement d,RuralRules.Person head,int good,int stock,int base){int quote=quoteTenths(policy(head,good),stock,base);var family=d.state.families.get(head.family);if(d.serverContext!=null&&family!=null){int finite=CityTrade.headQuote(d.serverContext,family.city,good,base,stock);if(finite>0)return finite;}if(good>=2&&family!=null&&RuralFoodAccess.active(d,family.city))quote=FoodMarketRules.bidTenths(quote,RuralFoodAccess.retail(d,head,good,d.stocks(family.id)));return family!=null&&d.state.externalBuyer.contains(family.city)?Math.min(quote,baseSell(good)*9-1):quote;}
 static int buy(RuralSettlement d,RuralRules.Person head,RuralRules.Family buyer,UUID seller,List<RuralRules.Stock> source,List<RuralRules.Stock> target,int good,int wanted,int base,Map<UUID,Long> money){
  if(buyer.members.contains(seller)||RuralRules.balance(buyer,money)<=0)return 0;
  if(d.state.priceRemainders.size()>=8192)throw new IllegalArgumentException("交易尾差容量已满");
  String key=buyer.id+":"+seller+":"+good;int tail=d.state.priceRemainders.getOrDefault(key,0),done=0;long paid=0;
  int n=Math.min(wanted,Math.min(count(source,good),RuralRules.capacity(target)));var policy=policy(head,good);
  for(int i=0;i<n;i++){int cost=tail+purchaseQuote(d,head,good,count(target,good)+done,base);int whole=cost/10;long reserve=good>=2&&d.serverContext!=null?CityTrade.headReserve(d.serverContext,buyer.city):0;if(RuralRules.balance(buyer,money)-reserve<whole||!RuralRules.pay(buyer,seller,whole,money))break;tail=cost%10;paid+=whole;done++;}
  if(done>0){FoodMoves.move(source,target,good,done);if(d.state.priceRemainders.size()<8192||d.state.priceRemainders.containsKey(key))d.state.priceRemainders.put(key,tail);else throw new IllegalStateException("交易尾差容量已满");d.audit(d.state.families.get(buyer.id).city).tradeSilver+=paid;d.state.tradedSilver+=paid;}return done;
 }
 static int reserve(RuralSettlement d,List<RuralRules.Family> families,RuralRules.Family market){int need=0;for(var f:families){int demand=RuralRules.demand(f,d.state.people)*2;int own=f==market?0:RuralRules.count(d.stocks(f.id),false)+RuralRules.count(d.stocks(f.id),true);need+=Math.max(0,demand-own);}return need;}
 static void export(RuralSettlement d,UUID city,List<RuralRules.Family> families,RuralRules.Family market,RuralRules.Person head,Map<UUID,Long> money){
  if(!d.state.externalBuyer.contains(city))return;var stocks=d.stocks(market.id);int protection=reserve(d,families,market);var audit=d.audit(city);
  int cookedTotal=stocks.stream().mapToInt(a->Arrays.stream(a.cooked).sum()).sum(),keepCooked=Math.min(cookedTotal,RuralMeat.reserve(market,d.state.people)),keepRaw=Math.max(0,RuralMeat.reserve(market,d.state.people)-keepCooked);
  for(int good=0;good<12;good++){int n=count(stocks,good);if(good<2){int expected=policy(head,good).target;n=Math.min(Math.max(0,n-expected),Math.max(0,count(stocks,0)+count(stocks,1)-protection));}else if(good<7){int kept=Math.min(n,keepRaw);keepRaw-=kept;n-=kept;}else{int kept=Math.min(n,keepCooked);keepCooked-=kept;n-=kept;}if(n<=0)continue;
   String key="external:"+market.id+":"+good;int value=n*baseSell(good)*9+d.state.priceRemainders.getOrDefault(key,0);int silver=value/10;d.state.priceRemainders.put(key,value%10);take(stocks,good,n);money.merge(head.id,(long)silver,Long::sum);audit.externalSilver+=silver;if(good==0)audit.exportWheat+=n;else if(good==1)audit.exportBread+=n;else audit.exportMeat+=n;
  }
 }
}



