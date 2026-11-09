package com.muxigame.city;

import java.util.*;
import java.nio.file.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

/** Initial data only. Existing economic rules, original world never reassigned. */
@GameTestHolder(CityMod.ID) @PrefixGameTestTemplate(false)
public class CityTestsProductivityProfile {
 record Group(String label,boolean urban,List<String> names,List<String> jobs){}
 static final class Profile {
  final CityTestsScale.Fixture f;final List<Group> groups=new ArrayList<>();final Map<String,RuralRules.Person> people=new LinkedHashMap<>();
  final Map<UUID,Long> initialOwned=new LinkedHashMap<>();final List<Map<String,Object>> familyInfo=new ArrayList<>();final Set<UUID> occupied=new HashSet<>();
  Profile(CityTestsScale.Fixture f){this.f=f;for(var p:f.r.state.people.values()){people.put(p.name,p);initialOwned.put(p.id,f.e.balance(CityEconomy.person(p.id)));}
   for(var b:f.t.state.businesses.values())if(!b.account.startsWith("person:")){long n=f.e.balance(b.account);initialOwned.merge(b.owner,n,Long::sum);f.e.state.accounts.put(b.account,0L);f.e.state.accounts.merge(CityEconomy.person(b.owner),n,Long::sum);}
   var oldForest=new LinkedHashMap<>(f.r.state.forest);f.r.state=new RuralSettlement.State();f.r.state.forest.putAll(oldForest);for(var p:people.values()){p.family=null;p.role=RuralRules.Role.NONE;f.r.state.people.put(p.id,p);}
   f.t.state=new CityTrade.State();var town=new CityTrade.Town();town.city=f.city.id;town.urban=true;town.partner=f.village.id;town.localOnly=true;f.t.state.towns.put(town.city,town);var rural=new CityTrade.Town();rural.city=f.village.id;rural.partner=f.city.id;rural.localOnly=true;f.t.state.towns.put(rural.city,rural);f.t.state.run.city=f.city.id;f.t.state.run.village=f.village.id;
   f.m.state.workers.clear();f.m.state.cities.clear();
   for(var c:f.towns){c.residents.clear();f.r.state.enabled.add(c.id);var remove=c.buildings.values().stream().filter(b->b.type().equals("工坊")).map(CityRegistry.Building::id).toList();for(var id:remove){c.buildings.remove(id);c.housing.houses.remove(id);}for(var h:c.housing.houses.values()){h.residents.clear();h.workers.clear();h.progress.clear();}}
   buildGroups();for(var g:groups)place(g);
   for(var c:f.towns)CityModel.configure(f.s,c,c==f.city);
   f.t.linkStaff();for(var b:f.t.state.businesses.values())if(b.kind.equals("supplier_helper")){var employer=f.t.employer(b);CityEconomy.require(employer!=null&&f.r.state.people.get(employer.owner).family.equals(f.r.state.people.get(b.worker).family),"Family workshop helper belongs to its own family employer");}
   f.initialRoles.clear();for(var p:f.r.state.people.values())f.initialRoles.put(p.id,p.role.name());
   CityEconomy.require(f.r.state.people.size()==100&&f.city.residents.size()==20&&f.village.residents.size()==80,"100 adults,20urban80rural");
   CityEconomy.require(f.r.state.families.size()==34&&f.t.money(f.s)==f.initialCash&&f.t.grain(f.s)==f.initialGrain,"34households, original30000money1500food");
   var owned=new HashMap<UUID,Long>();for(var p:people.values())owned.put(p.id,f.e.balance(CityEconomy.person(p.id)));for(var b:f.t.state.businesses.values())if(!b.account.startsWith("person:"))owned.merge(b.owner,f.e.balance(b.account),Long::sum);CityEconomy.require(owned.equals(initialOwned),"Every existing person's initial financial wealth retained");
   CityEconomy.require(f.m.state.cities.values().stream().mapToInt(p->p.baseline.size()).sum()==460,"460existing plots, no land grant for additional farmers");
  }
  void add(String label,boolean urban,List<String> names,String...jobs){groups.add(new Group(label,urban,names,List.of(jobs)));}
  void buildGroups(){var used=new HashSet<String>();
   var cityGroups=List.of(
    new Group("家庭工坊1",true,List.of("城市2","城市4","城市1","城市3"),List.of("supplier","supplier_helper","cook","")),
    new Group("家庭工坊2",true,List.of("城市10","城市12","城市9","城市11"),List.of("supplier","supplier_helper","cook","")),
    new Group("采购商",true,List.of("城市25","城市26","城市27"),List.of("procurement","cook","")),
    new Group("烘焙",true,List.of("城市5","城市6","城市8"),List.of("baker","cook","")),
    new Group("城市维修1",true,List.of("农村28夫","农村28妻","城市7"),List.of("artisan","cook","")),
    new Group("城市维修2",true,List.of("农村29夫","农村29妻","城市15"),List.of("artisan","cook","")));
   cityGroups.forEach(g->used.addAll(g.names));var extra=people.keySet().stream().filter(n->n.startsWith("城市")&&!used.contains(n)).toList();CityEconomy.require(extra.size()==24,"24existing urban adults join rural households");int at=0;
   for(int i=1;i<=25;i++){if(i==16||i==17)continue;add("农户"+i,false,List.of("农村"+i+"夫","农村"+i+"妻",extra.get(at++)),"farmer","cook","");}
   for(int i=16;i<=17;i++)add("猎户"+i,false,List.of("农村"+i+"夫","农村"+i+"妻"),"hunter","cook");
   for(int i=26;i<=27;i++)add("农村维修"+i,false,List.of("农村"+i+"夫","农村"+i+"妻"),"artisan","cook");
   add("头人",false,List.of("农村30夫","农村30妻",extra.get(at)),"merchant","cook","");groups.addAll(cityGroups);
   var unique=new HashSet<String>();for(var g:groups)for(String n:g.names)CityEconomy.require(people.containsKey(n)&&unique.add(n),"Each existing person assigned exactly once");CityEconomy.require(unique.size()==100,"All100retained");
  }
  void place(Group g){var c=g.urban?f.city:f.village;var family=new RuralRules.Family();family.id=UUID.nameUUIDFromBytes(("productivity-profile:"+g.label).getBytes(java.nio.charset.StandardCharsets.UTF_8));family.city=c.id;
   for(String name:g.names){var p=people.get(name);family.members.add(p.id);p.family=family.id;c.residents.add(p.id);var living=f.e.state.living.get(p.id);if(living!=null)living.city=c.id;}
   f.r.state.families.put(family.id,family);var home=c.housing.houses.entrySet().stream().filter(e->!occupied.contains(e.getKey())&&e.getValue().beds>0&&family.members.contains(e.getValue().owner)).findFirst().orElseThrow(()->new IllegalStateException("No existing family-owned home for "+g.label));occupied.add(home.getKey());home.getValue().beds=Math.max(home.getValue().beds,family.members.size());home.getValue().residents.addAll(family.members);var pos=c.buildings.get(home.getKey()).bounds().min();
   for(int i=0;i<g.names.size();i++){String kind=g.jobs.get(i);if(kind.isEmpty())continue;var p=people.get(g.names.get(i));var station=pos.offset(10+i*4,0,4);f.job(c,p,station,kind);if(Set.of("supplier","supplier_helper","procurement","baker","merchant").contains(kind)){var b=f.business(c,p,station,kind.equals("merchant")?"head":kind);if(kind.equals("merchant"))b.account=CityEconomy.person(p.id);else{long capital=kind.equals("supplier")||kind.equals("procurement")?1000:kind.equals("baker")?500:0;CityEconomy.require(f.e.balance(CityEconomy.person(p.id))>=capital,"Existing owner funds startup without grant");f.e.state.accounts.merge(CityEconomy.person(p.id),-capital,Long::sum);f.e.state.accounts.put(b.account,capital);}}
    if(kind.equals("farmer"))f.fields(c,station.offset(0,0,6));
   }
   boolean head=g.label.equals("头人");f.boxes(c,family,pos.offset(10,0,8),head?75:3,0);RuralRules.put(f.r.stocks(family.id),true,head?170:10);RuralRules.put(f.r.stocks(family.id),false,head?340:20);
   familyInfo.add(Map.of("id",family.id,"label",g.label,"urban",g.urban,"members",g.names,"initialJobs",g.jobs,"initialOwnedCash",family.members.stream().mapToLong(initialOwned::get).sum(),"home",home.getKey()));
  }
  Map<String,Object> metadata(){return Map.of("profile","commercial_preindustrial_productivity_v1","adults",100,"urban",20,"rural",80,"households",34,"families",familyInfo,"initialPersonOwnedCash",initialOwned,"plots",460,"changes",List.of("Occupations, settlement membership and adult household groups changed in independent initial data only","Initial per-person financial wealth,100identities,30000money1500food and460plots retained","50existing residential houses and their owners retained;34occupied, beds adjusted to3or4 without changing recorded building blocks","Operating workplace template95to70; private active storage222to174; idle housing retained","All farming now rural12hour workday; previous8urban farmers had8hour workdays","No changed prices,wages,consumption probability,spoilage,trade capacity or economic mechanism"));}
 }
 @GameTest(template="empty",timeoutTicks=200) public static void productivityBasedHundredAdults(GameTestHelper h)throws Exception{
  if(!Boolean.getBoolean("city.profile.enabled")){h.succeed();return;}int days=Integer.getInteger("city.profile.days",500);if(days<30||days>1000)throw new IllegalArgumentException("30..1000days");
  try(var f=new CityTestsScale.Fixture(h,1,3)){var profile=new Profile(f);f.s.overworld().random.setSeed(2026110401L);for(var c:f.towns){CityHousing.validate(c,c.housing);CityWorks.validate(c.works,c);}
   Files.writeString(Path.of("productivity-profile-setup.json"),CityEconomy.JSON.toJson(Map.of("profile",profile.metadata(),"setup",f.setup())));var materials=CityTestsLongRun.materialScore(f);AutoCloseable audit=null;try{var cls=Class.forName("com.muxigame.city.CityTestsCashAudit");audit=(AutoCloseable)cls.getDeclaredConstructor(CityTestsScale.Fixture.class).newInstance(f);}catch(ClassNotFoundException ignored){}
   Map<String,Object> result;try{result=f.run(days);if(audit!=null)result.put("cashAudit",audit.getClass().getDeclaredMethod("finish").invoke(audit));}finally{if(audit!=null)audit.close();}
   result.put("profile",profile.metadata());result.put("days",days);result.put("merchantsPerCity",1);result.put("version","0.10.13");var residual=CityTestsLongRun.materialScore(f);residual.replaceAll((key,value)->value-materials.getOrDefault(key,0L));result.put("materialResidual",residual);Files.writeString(Path.of("productivity-profile-100-"+days+".json"),CityEconomy.JSON.toJson(result));
   h.assertTrue(f.conserved&&residual.values().stream().allMatch(v->v==0),"Everyday money/food/meat/goods and materials conserve; report attrition honestly");h.assertTrue(f.r.state.boxes.values().stream().allMatch(b->b.stock.free()>=0&&b.stock.wheat+b.stock.bread+RuralMeat.total(List.of(b.stock))<=10),"Existing10food perbox cap retained");
   var reloaded=CityData.load(CityData.get(f.s).save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());h.assertTrue(reloaded.cities.size()==2,"Profile still strictly saves/reloads");
  }h.succeed();
 }
}
