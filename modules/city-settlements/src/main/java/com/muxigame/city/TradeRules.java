package com.muxigame.city;

/** Bounded four-good market. All durations are simulation ticks, not wall-clock time. */
public final class TradeRules {
 public static final String[] IDS={"minecraft:wheat","minecraft:bread","minecraft:painting","minecraft:book"};
 public static final String[] NAMES={"小麦","面包","画","书"};
 public static final int[] BASE={3,5,20,10};
 public static final double[] TARGET={5,1.5,.25,.5};
 public static final int DAY=24000, STEP=600;
 public static final class Price {public long emptySince=-1,richSince=-1;public int lastStock;}
 public static final class Lot {
  public FoodAge.State freshness=new FoodAge.State();public transient FoodAge.Portion removedAge=FoodAge.Portion.FRESH;
  public int count;public long cents;
  public void add(int n,long cost){if(n<0||cost<0||count+n>1000000)throw new IllegalArgumentException("库存/成本越界");freshness.ensure(count);freshness.add(n,0);count+=n;cents+=cost;}
  public long take(int n){if(n<0||n>count)throw new IllegalArgumentException("库存不足");freshness.ensure(count);removedAge=freshness.take(n);long cost=n==count?cents:cents*n/count;count-=n;cents-=cost;return cost;}
  public void receive(int n,long cost,FoodAge.Portion age){if(n<0||cost<0||count+n>1000000)throw new IllegalArgumentException("库存/成本越界");freshness.ensure(count);freshness.add(n,age.age());count+=n;cents+=cost;}
  public double unit(){return count==0?0:cents/(100.0*count);}
 }
 public static Lot[] lots(){return new Lot[]{new Lot(),new Lot(),new Lot(),new Lot()};}
 public static void move(Lot from,Lot to,int n){long cost=from.take(n);to.receive(n,cost,from.removedAge);}
 public static double factor(int stock,int target,long emptyTicks,long richTicks){
  double r=stock/(double)Math.max(1,target);
  if(stock==0)return Math.min(5,2+Math.min(30,Math.max(0,emptyTicks)/DAY)*.1);
  if(r>=2)return Math.max(.1,.5-Math.min(4,Math.max(0,richTicks)/DAY)*.1);
  return r<=1?2-r:1-.5*(r-1);
 }
 public static int quote(int base,int stock,int target,long emptyTicks,long richTicks){return Math.max(1,(int)Math.floor(base*factor(stock,target,emptyTicks,richTicks)+.5));}
 public static void observe(Price p,int stock,int target,long now){if(stock==0){if(p.emptySince<0)p.emptySince=now;}else p.emptySince=-1;if(stock>=2*Math.max(1,target)){if(p.richSince<0)p.richSince=now;}else p.richSince=-1;p.lastStock=stock;}
 public static int quote(Price p,int base,int stock,int target,long now){observe(p,stock,target,now);return quote(base,stock,target,p.emptySince<0?0:now-p.emptySince,p.richSince<0?0:now-p.richSince);}
 public static double estimate(int random0to2000){return .9+Math.clamp(random0to2000,0,2000)/10000.0;}
 public static int capacity(Lot[] lots){int used=0;for(var lot:lots)used+=lot.count;return Math.max(0,384-used);}
 public static int[] supplierOutput(int workers,long segment,int paintingRoom,int bookRoom){int[] result=new int[2];for(int n=0;n<Math.clamp(workers,0,2);n++){int kind=(int)Math.floorMod(segment+n,2);if(kind==0&&paintingRoom<=0)kind=1;if(kind==1&&bookRoom<=0)kind=0;if(kind==0&&paintingRoom>0){result[0]++;paintingRoom--;}else if(kind==1&&bookRoom>0){result[1]++;bookRoom--;}}return result;}
 public static int plan(int stock,int target,int base,int villagePrice,double perception,int capacity,long silver){return plan(stock,target,base,villagePrice,perception,capacity,silver,0);}
 public static int plan(int stock,int target,int base,int villagePrice,double perception,int capacity,long silver,long richTicks){
  int max=(int)Math.min(Math.min(192,capacity),silver/Math.max(1,villagePrice));int n=0;
  while(n<max&&quote(base,stock+n,target,0,Math.max(0,richTicks))*perception>=villagePrice*1.5)n++;
  return n;
 }
}

