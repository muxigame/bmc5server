package com.muxigame.city;

import java.util.*;
import net.minecraft.server.MinecraftServer;

/** Daily local job search and household preparation. No new actors, money, jobs or property. */
public final class FamilyLivelihood {
 public static final class Home {public UUID processor;public long day=-1;public double credit;public boolean secondary,preferPrepared;public String status="待评估";public long cooked;}
 static void paid(MinecraftServer s,UUID person){CityTrade.get(s).state.staffIncomeAt.put(person,RuralSettlement.get(s).state.simulationTicks);CityTrade.get(s).setDirty();}
 static void unpaid(MinecraftServer s,CityData.City c,RuralRules.Person p,long now){var t=CityTrade.get(s);var d=RuralSettlement.get(s);var helper=t.state.businesses.values().stream().filter(b->b.city.equals(c.id)&&b.kind.equals("supplier_helper")&&p.id.equals(b.worker)).findFirst().orElse(null);if(helper==null||p.dead||p.role!=RuralRules.Role.SUPPLIER)return;long last=t.state.staffIncomeAt.computeIfAbsent(p.id,k->now);var f=d.state.families.get(p.family);int stock=RuralRules.count(d.stocks(f.id),false)+RuralRules.count(d.stocks(f.id),true);if(!DataCityRules.exit(now,last,stock,RuralRules.demand(f,d.state.people),t.familyMoney(s,f),t.quote(s,c.id,1,now)))return;for(var site:d.state.sites.values())if(p.id.equals(site.worker)){site.worker=null;site.vacantSince=now+1;}for(var home:c.housing.houses.values())home.workers.remove(p.id);RuralSuccession.setRole(p,RuralRules.Role.NONE);helper.status="连续五日无工资且缺口粮，已离职求职";p.workStatus=helper.status;d.log(p.name+helper.status);}
 static boolean home(CityData.City c,RuralRules.Family f){return c.housing.houses.values().stream().anyMatch(h->h.stage.equals("BUILT")&&CityWorks.health(h)>0&&h.beds>0&&h.residents.stream().anyMatch(f.members::contains));}
 // A surviving registered household stove works independently of residential shell health.
 static boolean stove(MinecraftServer s,RuralRules.Family f){var d=RuralSettlement.get(s);return d.state.sites.values().stream().anyMatch(v->{var p=d.state.people.get(v.worker);return v.city.equals(f.city)&&v.kind.equals("cook")&&f.members.contains(v.worker)&&p!=null&&!p.dead&&p.age.ordinal()>=2&&RuralTools.available(s,v);});}
 static int priority(String kind){return switch(kind){case "artisan","farmer"->0;case "cook"->1;case "hunter","baker","supplier"->2;default->3;};}
 static boolean seek(MinecraftServer s,CityData.City c,RuralRules.Person p){
  var d=RuralSettlement.get(s);var t=CityTrade.get(s);
  var choices=new ArrayList<>(d.state.sites.values().stream().filter(v->v.city.equals(c.id)&&(RuralTools.available(s,v)||v.kind.equals("artisan")&&c.works.emergency))
   .filter(v->p.role!=RuralRules.Role.HUNTER||!v.kind.equals("hunter")&&!v.kind.equals("cook"))
   .filter(v->{var old=d.state.people.get(v.worker);return old==null||old.dead;}).toList());
  Collections.shuffle(choices,new Random(p.id.getLeastSignificantBits()^Math.floorDiv(d.state.simulationTicks,24000)));
  choices.sort(Comparator.comparingInt(v->priority(v.kind)));
  for(var site:choices){var workplace=CityWorks.at(c,site.pos);if(workplace!=null){workplace.workers.removeIf(id->!CityWorks.living(s,c,id));if(workplace.workers.size()>=workplace.workerLimit&&!workplace.workers.contains(p.id))continue;}var business=t.state.businesses.values().stream().filter(b->b.city.equals(c.id)&&b.site==site.pos).findFirst().orElse(null);
   // An abandoned business is not free capital for the next employee.
   if(business!=null&&(business.closing||!p.id.equals(business.owner)&&!business.kind.equals("supplier_helper")))continue;
   if(business!=null&&business.kind.equals("supplier_helper")){var employer=t.employer(business);if(employer==null||!t.usable(s,employer)||CityEconomy.get(s).balance(employer.account)<8)continue;boolean demand=false;for(int g=2;g<4;g++)if(t.quote(s,c.id,g,d.state.simulationTicks)>=4&&employer.stock[g].count+employer.listed[g].count<t.state.config.supplierCap)demand=true;if(!demand)continue;}
   if(site.kind.equals("artisan")){var b=c.buildings.values().stream().filter(v->v.bounds().contains(net.minecraft.core.BlockPos.of(site.pos))).findFirst();if(b.isEmpty())continue;try{RuralSuccession.employ(s,c,p.id,b.get().id());}catch(IllegalArgumentException ex){continue;}}
   else {if(site.kind.equals("cook")&&d.state.boxes.values().stream().noneMatch(b->p.family.equals(b.stock.family)&&b.site==site.pos))continue;
    var role=switch(site.kind){case "farmer"->RuralRules.Role.FARMER;case "cook"->RuralRules.Role.COOK;case "hunter"->RuralRules.Role.HUNTER;case "baker"->RuralRules.Role.BAKER;case "supplier"->RuralRules.Role.SUPPLIER;default->RuralRules.Role.NONE;};if(role==RuralRules.Role.NONE)continue;
    for(var old:d.state.sites.values())if(p.id.equals(old.worker)){old.worker=null;old.vacantSince=d.state.simulationTicks+1;}for(var old:c.housing.houses.values())old.workers.remove(p.id);RuralSuccession.setRole(p,role);site.worker=p.id;if(workplace!=null)workplace.workers.add(p.id);site.vacantSince=0;
   }
   if(business!=null)business.worker=p.id;
   p.workStatus="本地求职成功，私产保持原归属";d.log(p.name+"本地就任"+site.kind);return true;
  }return false;
 }
 static void daily(MinecraftServer s,CityData.City c){var t=CityTrade.get(s);if(!t.state.towns.containsKey(c.id))return;var d=RuralSettlement.get(s);long day=Math.floorDiv(d.state.simulationTicks,24000);
  for(var f:d.state.families.values()){if(!c.id.equals(f.city))continue;var h=t.state.households.computeIfAbsent(f.id,k->new Home());if(h.day==day)continue;h.day=day;FamilyBudget.daily(s,f,day);CityOpportunities.daily(s,c,f);
   for(var p:RuralRules.members(f,d.state.people))unpaid(s,c,p,d.state.simulationTicks);
   for(var p:RuralRules.members(f,d.state.people))if(RuralSuccession.capable(p)&&(p.role==RuralRules.Role.NONE||p.role==RuralRules.Role.VAGRANT))seek(s,c,p);
   if(!CityTrade.urban(s,c.id)){h.processor=null;h.status="沿用农村农妇加工";continue;}
   boolean housed=home(c,f);if(!housed&&!stove(s,f)){h.processor=null;h.status="住房和可用炉灶均缺失，购买成品";continue;}
   var people=RuralRules.members(f,d.state.people).stream().filter(p->p.age.ordinal()>=2).toList();
   var chosen=people.stream().filter(p->housed&&(p.role==RuralRules.Role.NONE||p.role==RuralRules.Role.COOK)||!housed&&p.role==RuralRules.Role.COOK&&d.state.sites.values().stream().anyMatch(v->p.id.equals(v.worker)&&v.kind.equals("cook")&&RuralTools.available(s,v))).findFirst().orElse(people.stream().min(Comparator.comparingLong(p->p.totalWorkingTicks)).orElse(null));
   h.processor=chosen==null?null:chosen.id;h.secondary=chosen!=null&&chosen.role!=RuralRules.Role.NONE&&chosen.role!=RuralRules.Role.COOK;
   h.preferPrepared=CityOpportunities.buyPrepared(s,f,h.secondary);h.status=chosen==null?"无人能够加工":h.preferPrepared?"优先买成品，缺货时保留自炊":h.secondary?"需要自炊时兼职家务，原岗位及加工均半速":"家庭加工";
  }t.setDirty();d.setDirty();
 }
 static Home household(MinecraftServer s,RuralRules.Person p){var h=CityTrade.get(s).state.households.get(p.family);return h!=null&&p.id.equals(h.processor)?h:null;}
 static boolean cookingNeeded(MinecraftServer s,RuralRules.Person p){var h=household(s,p);if(h==null)return false;var d=RuralSettlement.get(s);var f=d.state.families.get(p.family);var stores=d.stocks(f.id);int ready=RuralRules.count(stores,true)+stores.stream().mapToInt(v->Arrays.stream(v.cooked).sum()).sum();if(ready>=Math.max(RuralRules.demand(f,d.state.people)*2,Math.min(30,CityTrade.mealTarget(f,d))))return false;if(h.preferPrepared&&RuralRules.capacity(stores)>=Math.max(1,RuralRules.demand(f,d.state.people)-ready)&&CityTrade.get(s).stock(s,f.city,1)>=Math.max(1,RuralRules.demand(f,d.state.people)-ready)&&CityTrade.get(s).familyMoney(s,f)>=CityTrade.get(s).quote(s,f.city,1,d.state.simulationTicks)*Math.max(0,RuralRules.demand(f,d.state.people)-ready))return false;return RuralRules.count(stores,false)+stores.stream().mapToInt(v->Arrays.stream(v.raw).sum()).sum()>0;}
 static double workFactor(MinecraftServer s,RuralRules.Person p){var h=household(s,p);return h!=null&&h.secondary&&cookingNeeded(s,p)?.5:1;}
 static void work(MinecraftServer s,RuralRules.Person p){var h=household(s,p);if(h!=null&&cookingNeeded(s,p))h.credit=Math.min(30,h.credit+HouseholdCooking.URBAN_RATE*RuralRules.efficiency(p.trainedHours)*(h.secondary?.5:1)*20/36000.0);}
 static boolean processor(MinecraftServer s,RuralRules.Family f){var h=CityTrade.get(s).state.households.get(f.id);var p=h==null?null:RuralSettlement.get(s).state.people.get(h.processor);return p!=null&&!p.dead&&p.age.ordinal()>=2&&(home(CityData.get(s).require(f.city),f)||stove(s,f));}
 static void cook(MinecraftServer s,RuralRules.Family f){var t=CityTrade.get(s);var h=t.state.households.get(f.id);if(h==null||!processor(s,f))return;int limit=(int)h.credit;var d=RuralSettlement.get(s);int n=RuralRules.cook(d.stocks(f.id),limit,CityTrade.mealTarget(f,d),false);h.credit-=n;h.cooked+=n;d.state.breadProduced+=n;int meat=RuralMeat.cook(d.stocks(f.id),(int)(h.credit*30/HouseholdCooking.URBAN_RATE),CityTrade.mealTarget(f,d));h.credit-=meat*HouseholdCooking.URBAN_RATE/30;d.state.meatCooked+=meat;}
}
