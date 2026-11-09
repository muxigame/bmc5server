package com.muxigame.city;

/** Pure data decisions shared by game adapters and headless validation. No world/entity access. */
public final class FoodMarketRules {
 private FoodMarketRules(){}
 public static int retail(int base,int stock,int target){return TradeRules.quote(base,stock,Math.max(1,target),0,0);}
 public static int bidTenths(int desired,int retail){return Math.max(1,Math.min(desired,retail*8));}
 public static int shipment(int cityPrice,int villagePrice,double perception,int source,int capacity,long money){if(villagePrice*perception<cityPrice*1.25||cityPrice<=0)return 0;return (int)Math.min(Math.min(Math.max(0,source),Math.min(192,Math.max(0,capacity))),Math.max(0,money)/cityPrice);}
 public static boolean buyPrepared(int wheat,int bread,int opportunityPerLoaf,boolean secondary){return bread<=wheat+Math.max(0,opportunityPerLoaf)&&(secondary||bread<=wheat);}
}
