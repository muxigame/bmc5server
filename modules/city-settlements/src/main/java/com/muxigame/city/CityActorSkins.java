package com.muxigame.city;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;

/** Local original vanilla-UV textures. Explicit application never changes identity or economy. */
public final class CityActorSkins {
 static String skin(MinecraftServer s,RuralRules.Person p){
  String sex=p.female?"f":"m",name;
  if(p.age==RuralRules.Age.CHILD)name="child_"+sex+"_a";
  else name=switch(p.role){
   case FARMER->p.female?"rural_cook_f_b":"rural_farmer_m_"+(Math.floorMod(p.id.hashCode(),2)==0?"a":"b");
   case COOK->{var f=RuralSettlement.get(s).state.families.get(p.family);yield !p.female?"baker_m_a":f!=null&&CityTrade.urban(s,f.city)?"town_homemaker_f_a":"rural_cook_f_a";}
   case HUNTER->"hunter_"+sex+"_a";
   case MERCHANT->"headman_"+sex+"_a";
   case PROCUREMENT->"procurement_"+sex+"_a";
   case BAKER->"baker_"+sex+"_a";
   case ARTISAN->"artisan_"+sex+"_a";
   case SUPPLIER->(CityTrade.get(s).state.businesses.values().stream().anyMatch(b->p.id.equals(b.worker)&&b.kind.equals("supplier_helper"))?"supplier_helper_":"supplier_")+sex+"_a";
   case VAGRANT->"vagrant_"+sex+"_a";
   default->"resident_"+sex+"_a";
  };return "city_settlements:textures/entity/npc/"+name+".png";
 }
 static boolean apply(MinecraftServer s,RuralRules.Person p,LivingEntity entity){
  if(!entity.getClass().getName().startsWith("noppes.npcs."))return false;
  try{var display=entity.getClass().getField("display").get(entity);display.getClass().getMethod("setSkinTexture",String.class).invoke(display,skin(s,p));display.getClass().getMethod("setSize",int.class).invoke(display,p.age==RuralRules.Age.CHILD?3:5);return true;}
  catch(ReflectiveOperationException ex){return false;}
 }
 static int applyLoaded(MinecraftServer s,UUID id){var c=CityData.get(s).require(id);var d=RuralSettlement.get(s);var l=CityEconomy.level(s,c);int count=0;for(UUID npc:c.residents){var p=d.state.people.get(npc);var e=l.getEntity(npc);if(p!=null&&!p.dead&&e instanceof LivingEntity living&&apply(s,p,living))count++;}return count;}
}
