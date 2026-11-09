package com.muxigame.city;

/** Small shared decisions. No Minecraft classes; also compiled into the local web laboratory. */
public final class DataCityRules {
 private DataCityRules(){}
 public static final long DAY=24000;
 public static int foodReserve(int meal,int days){return (int)Math.ceil(Math.max(0,meal)*Math.max(0,days)*DAY/36000.0);}
 public static boolean improvementRoom(int staples,int free,int meal){return free>0&&staples>=meal*2;}
 public static boolean improvementRoom(int staples,int free,int meal,int wheat){return improvementRoom(staples,free,meal)||free==0&&wheat>0&&staples>meal*2;}
 public static boolean exit(long now,long lastIncome,int food,int meal,long money,int price){
  return now-lastIncome>=5*DAY && food+Math.max(0,money)/Math.max(1,price)<foodReserve(meal,3);
 }
 public static double factor(int training,double health,boolean secondary){
  return (training>=9?1:1-Math.pow(.5,Math.max(0,training)+1))*Math.clamp(health,0,1)*(secondary?.5:1);
 }
 public static String phase(int population,int hungry,int employed,int food,int meal,long treasury){
  if(population==0)return "空城";
  if(hungry*3>=population)return "衰退";
  if(food<foodReserve(meal,1)||treasury==0)return "承压";
  if(employed*4>=population*3&&food>=foodReserve(meal,3))return "繁荣";
  return "发展";
 }
}
