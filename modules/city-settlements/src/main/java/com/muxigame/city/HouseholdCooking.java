package com.muxigame.city;
import java.util.*;

/** Shared time accounting: credit is bread-equivalent; meat keeps its existing work rate. */
public final class HouseholdCooking {
 static final double URBAN_RATE=10.5,RURAL_COOK_RATE=13.5,OLD_RATE=30;
 static double ruralRate(RuralRules.Person p){return p.role==RuralRules.Role.COOK?RURAL_COOK_RATE:OLD_RATE;}
 static int penalty(RuralSettlement d,RuralRules.Family f){if(!d.workers(f,RuralRules.Role.MERCHANT).isEmpty())return 0;return !d.workers(f,RuralRules.Role.COOK).isEmpty()||!d.workers(f,RuralRules.Role.FARMER).isEmpty()?1:0;}
 static double cityDaily(FamilyLivelihood.Home h){return URBAN_RATE/3*(h.secondary?.5:1);}
 static String name(net.minecraft.server.MinecraftServer s,RuralRules.Person p){return p.role==RuralRules.Role.COOK?(CityTrade.urban(s,RuralSettlement.get(s).state.families.get(p.family).city)?"家庭主妇":"农妇"):switch(p.role){case NONE->"居民";case FARMER->"农夫";case MERCHANT->"头人";case HUNTER->"猎户";case ARTISAN->"工匠";case VAGRANT->"流民";case PROCUREMENT->"采购商";case BAKER->"烘焙师";case SUPPLIER->"供货工人";default->p.role.name();};}
}
