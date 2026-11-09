package com.muxigame.city;

import java.util.*;

/** Pure batched food durability. Groups have at most64units; inventory transfers preserve mean age. */
public final class FoodAge {
 public static final long SCALE=1_000_000_000_000L;
 public record Portion(int count,double age) {public static final Portion FRESH=new Portion(0,0);}
 public static final class Group {public int count;public long age;Group(){}Group(int n,double p){count=n;age=Math.round(p*SCALE);}}
 public static final class State {
  public List<Group> groups=new ArrayList<>();
  public int quantity(){return groups.stream().mapToInt(g->g.count).sum();}
  public void ensure(int actual){int delta=actual-quantity();if(delta>0)add(delta,0);else if(delta<0)take(-delta);}
  public void add(int n,double age){if(n<0||n>1000000||!Double.isFinite(age)||age<0||age>=1.00000001)throw new IllegalArgumentException("Food cohort out of range");age=Math.min(Math.nextDown(1.0),age);for(var g:groups)if(g.count<64&&n>0){int a=Math.min(n,64-g.count);g.age=Math.round((g.age*(double)g.count+age*SCALE*a)/(g.count+a));g.count+=a;n-=a;}while(n>0){int a=Math.min(64,n);groups.add(new Group(a,age));n-=a;}}
  public Portion take(int n){if(n<0||n>quantity())throw new IllegalArgumentException("Food removal exceeds real quantity");int count=n;double age=0;groups.sort(Comparator.comparingLong((Group g)->g.age).reversed());for(var g:groups){int a=Math.min(n,g.count);age+=a*(g.age/(double)SCALE);g.count-=a;n-=a;if(n==0)break;}groups.removeIf(g->g.count==0);return new Portion(count,count==0?0:age/count);}
  public int advance(long ticks,long period){if(ticks<0||period<=0)throw new IllegalArgumentException("Food clock invalid");int spoiled=0;for(var g:groups){double left=ticks;while(g.count>0&&left>0){double rate=1+(Math.ceil(g.count/8.0)-1)*.2,required=(1-g.age/(double)SCALE)*period/rate;if(left+1e-8<required){g.age=Math.min(SCALE-1,Math.round(g.age+left*rate/period*SCALE));break;}left-=required;g.age=0;g.count--;spoiled++;}}groups.removeIf(g->g.count==0);return spoiled;}
  public State copy(){var s=new State();for(var g:groups)s.groups.add(new Group(g.count,g.age/(double)SCALE));return s;}
 }
 public static State[] states(int count){var a=new State[count];for(int i=0;i<count;i++)a[i]=new State();return a;}
 public static long period(int good){return 24000L*(good==0?8:good==1?3:good<7?3:5);}
 public static Portion combine(List<Portion> portions){int n=0;double age=0;for(var p:portions){n+=p.count;age+=p.age*p.count;}return new Portion(n,n==0?0:age/n);}
}
