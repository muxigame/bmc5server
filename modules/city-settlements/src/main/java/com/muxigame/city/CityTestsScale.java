package com.muxigame.city;

import java.util.*;
import java.nio.file.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.gametest.*;

/** Test-only fixture/export. Not included in the released JAR. Production rules stay unchanged. */
@GameTestHolder(CityMod.ID) @PrefixGameTestTemplate(false)
public class CityTestsScale {
 static final class Fixture implements AutoCloseable {
  final CityTestsNumeric.NumericFixture guard;
  final net.minecraft.server.MinecraftServer s;
  final RuralSettlement r;final CityTrade t;final CityEconomy e;final CityMaintenance m;
  final CityData.City city,village;final List<CityData.City> towns=new ArrayList<>();
  final List<Map<String,Object>> daily=new ArrayList<>();
  final long initialCash,initialGrain,initialMeat,initialGoods;
  final int merchants;final Map<UUID,String> initialRoles=new LinkedHashMap<>();
  boolean conserved=true;long maxStepNanos;final long started=System.nanoTime();
  Fixture(GameTestHelper h){this(h,1);} Fixture(GameTestHelper h,int copies){this(h,copies,10);} Fixture(GameTestHelper h,int copies,int merchants){
   if(merchants<1||merchants>10)throw new IllegalArgumentException("Independent merchant count must be 1..10 per forty city residents");this.merchants=merchants;
   guard=new CityTestsNumeric.NumericFixture(h);s=guard.server;r=guard.rural;t=guard.trade;e=guard.economy;m=guard.maintenance;
   r.state=new RuralSettlement.State();t.state=new CityTrade.State();e.state=new CityEconomy.State();m.state=new CityMaintenance.State();CityData.get(s).cities.clear();
   city=town("五倍测试·城市",30000,true);village=town("五倍测试·农村",30200,false);
   t.state.run.city=city.id;t.state.run.village=village.id;
   seed(city,village,30000,"");
   for(int n=1;n<copies;n++){int x=30000+n*4096;String prefix="副本"+(n+1)+"·";seed(town(prefix+"城市",x,true),town(prefix+"农村",x+200,false),x,prefix);}
   for(var p:r.state.people.values())initialRoles.put(p.id,p.role.name());
   initialCash=t.money(s);initialGrain=t.grain(s);initialMeat=t.meat(s);initialGoods=t.goods(s);
  }
  void seed(CityData.City city,CityData.City village,int baseX,String prefix){towns.add(city);towns.add(village);t.state.towns.get(city.id).partner=village.id;
   String[] roles={"procurement","supplier","procurement","supplier_helper","baker","urban_farmer","artisan",""};int merchantIndex=0;
   for(int f=0;f<20;f++){
    var a=person(city,prefix+"城市"+(f*2+1));var b=person(city,prefix+"城市"+(f*2+2));var family=guard.family(a,b);
    var pos=new BlockPos(baseX+(f%5)*24,90,30000+(f/5)*24);home(city,family,pos);
    for(int j=0;j<2;j++){
     var p=j==0?a:b;String kind=roles[(f*2+j)%8];boolean converted=false;if(kind.equals("procurement")){int index=merchantIndex++;if(index*merchants/10==(index+1)*merchants/10){fund(CityEconomy.person(p.id),1000);converted=true;kind=roles[(f*2+j+1)%8].equals("supplier_helper")?"urban_farmer":"cook";}}if(kind.isEmpty())continue;
     var station=pos.offset(10+j*4,0,4);job(city,p,station,kind);
     if(kind.equals("artisan")||kind.equals("cook"))continue;var business=business(city,p,station,kind);
     if(kind.equals("procurement"))fund(business.account,1000);
     if(kind.equals("supplier"))fund(business.account,1000);
     if(kind.equals("baker"))fund(business.account,500);
     if(kind.equals("urban_farmer")){business.account=CityEconomy.person(p.id);if(!converted)business.listed[0].add(40,0);fields(city,station.offset(0,0,6));}
    }
    boxes(city,family,pos.offset(10,0,8),3,20);
   }
   for(int f=0;f<30;f++){
    var a=person(village,prefix+"农村"+(f+1)+"夫");var b=person(village,prefix+"农村"+(f+1)+"妻");b.female=true;a.spouse=b.id;b.spouse=a.id;a.marriage=b.marriage=RuralRules.Marriage.FACTUAL;
    var family=guard.family(a,b);var pos=new BlockPos(baseX+200+(f%5)*24,90,30000+(f/5)*20);home(village,family,pos);
    String kind=f<15?"farmer":f<25?"hunter":f<29?"artisan":"merchant";
    var station=pos.offset(10,0,4);job(village,a,station,kind);job(village,b,pos.offset(14,0,4),"cook");
    boxes(village,family,pos.offset(10,0,8),f==29?75:3,20);
    if(kind.equals("farmer"))fields(village,pos.offset(10,0,10));
    if(kind.equals("hunter")){
     double angle=2*Math.PI*(f-15)/10;var stand=new BlockPos(baseX+264+(int)Math.round(160*Math.cos(angle)),90,30064+(int)Math.round(160*Math.sin(angle)));
     var cell=new HunterForest.Cell();cell.dimension=village.dimension;cell.x=stand.getX()>>4;cell.z=stand.getZ()>>4;cell.stand=stand.asLong();cell.usable=true;cell.canopy=4;cell.logs=1;r.state.forest.put(HunterForest.key(cell.dimension,cell.x,cell.z),cell);a.huntRadius=256;a.huntingSpot=stand.asLong();
    }
    if(kind.equals("merchant")){var business=business(village,a,station,"head");business.account=CityEconomy.person(a.id);fund(business.account,2500);RuralRules.put(r.stocks(family.id),false,300);}
   }
   fund(CityEconomy.treasury(city.id),5000);
   for(var c:List.of(city,village)){
    CityHousing.seedCatalog(c.housing);c.housing.testSupply=true;c.housing.constructionSupplyOnly=true;
    for(var material:c.housing.market.values()){material.stock=640;material.cap=640;material.supply=20;}
    c.works.resources.put("minecraft:oak_planks",1280);
    for(int x=0;x<16;x++){long p=c.bounds.min().offset(x,2,127).asLong();c.works.terrain.put(p,CityMaintenance.encode(Blocks.DIRT.defaultBlockState()));if(x<8)c.works.terrainDamage.add(p);}
    for(int x=0;x<3;x++)c.works.illegal.put(c.bounds.min().offset(x,5,126).asLong(),CityMaintenance.encode(Blocks.OAK_PLANKS.defaultBlockState()));
    var pos=c.bounds.min().offset(110,2,110);UUID id=UUID.nameUUIDFromBytes((c.name+":planned").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    c.buildings.put(id,new CityRegistry.Building(id,"待建公共仓库","其他",CityBounds.between(pos,pos.offset(3,0,3))));var house=CityHousing.house(c,id);house.configured=true;house.stage="PLANNED";house.extra=true;
    for(int x=0;x<4;x++)for(int z=0;z<4;z++){long p=pos.offset(x,0,z).asLong();house.baseline.put(p,CityMaintenance.encode(Blocks.OAK_PLANKS.defaultBlockState()));house.damaged.add(p);}
   }
  }
  void fund(String account,long n){e.state.accounts.merge(account,n,Long::sum);}
  CityData.City town(String name,int x,boolean urban){var c=CityData.get(s).create(UUID.randomUUID(),name,"minecraft:overworld",CityBounds.between(new BlockPos(x,88,30000),new BlockPos(x+127,95,30127)));c.works.virtual=true;c.works.finalized=true;c.works.enabled=true;var town=new CityTrade.Town();town.city=c.id;town.urban=urban;town.localOnly=true;t.state.towns.put(c.id,town);r.state.enabled.add(c.id);return c;}
  RuralRules.Person person(CityData.City c,String name){UUID id=UUID.nameUUIDFromBytes(("scale100:"+name).getBytes(java.nio.charset.StandardCharsets.UTF_8));c.residents.add(id);e.state.accounts.put(CityEconomy.person(id),50L);var p=r.enroll(s,c,id,name);p.trainedHours=10;return p;}
  void home(CityData.City c,RuralRules.Family f,BlockPos pos){UUID id=UUID.nameUUIDFromBytes((f.id+":home").getBytes(java.nio.charset.StandardCharsets.UTF_8));c.buildings.put(id,new CityRegistry.Building(id,"住宅","住宅",CityBounds.between(pos,pos.offset(7,3,7))));var h=CityHousing.house(c,id);h.configured=true;h.personal=true;h.owner=f.members.getFirst();h.beds=2;h.residents.addAll(f.members);for(int x=0;x<8;x++)for(int z=0;z<8;z++)for(int y:new int[]{0,3})h.baseline.put(pos.offset(x,y,z).asLong(),CityMaintenance.encode(Blocks.OAK_PLANKS.defaultBlockState()));}
  void job(CityData.City c,RuralRules.Person p,BlockPos pos,String kind){
   var block=switch(kind){case "farmer","urban_farmer"->RuralBlocks.SIFTER.get();case "cook"->RuralBlocks.STOVE.get();case "hunter"->RuralBlocks.HUNTER.get();case "merchant"->RuralBlocks.DESK.get();case "procurement"->RuralBlocks.GUILD.get();case "baker"->RuralBlocks.OVEN.get();case "artisan"->Blocks.CRAFTING_TABLE;default->RuralBlocks.SUPPLY.get();};
   var site=new RuralSettlement.Site();site.city=c.id;site.worker=p.id;site.pos=pos.asLong();site.kind=kind.equals("urban_farmer")?"farmer":kind.equals("supplier_helper")?"supplier":kind;site.expectedBlock=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString();r.state.sites.put(RuralSettlement.key(c.id,site.pos),site);
   p.role=switch(kind){case "farmer","urban_farmer"->RuralRules.Role.FARMER;case "cook"->RuralRules.Role.COOK;case "hunter"->RuralRules.Role.HUNTER;case "merchant"->RuralRules.Role.MERCHANT;case "procurement"->RuralRules.Role.PROCUREMENT;case "baker"->RuralRules.Role.BAKER;case "artisan"->RuralRules.Role.ARTISAN;default->RuralRules.Role.SUPPLIER;};
   UUID id=UUID.nameUUIDFromBytes((p.id+":job").getBytes(java.nio.charset.StandardCharsets.UTF_8));c.buildings.put(id,new CityRegistry.Building(id,kind,"工坊",CityBounds.between(pos,pos)));var h=CityHousing.house(c,id);h.configured=true;h.personal=true;h.owner=p.id;h.workerLimit=1;h.workers.add(p.id);h.baseline.put(pos.asLong(),CityMaintenance.encode(block.defaultBlockState()));
   if(kind.equals("artisan")){var w=new CityMaintenance.Worker();w.city=c.id;w.enabled=w.artisan=true;w.role="工匠";m.state.workers.put(p.id,w);}
  }
  CityTrade.Business business(CityData.City c,RuralRules.Person p,BlockPos pos,String kind){var b=new CityTrade.Business();b.id=UUID.nameUUIDFromBytes((p.id+":"+kind).getBytes(java.nio.charset.StandardCharsets.UTF_8));b.city=c.id;b.owner=b.worker=p.id;b.site=pos.asLong();b.kind=kind;b.account="trade:"+b.id;b.lastIncome=0;t.state.businesses.put(b.id,b);return b;}
  void boxes(CityData.City c,RuralRules.Family family,BlockPos pos,int count,int initial){long site=r.state.sites.values().stream().filter(v->family.members.contains(v.worker)).findFirst().orElseThrow().pos;for(int n=0;n<count;n++){var b=new RuralSettlement.Box();b.city=c.id;b.pos=pos.offset(n%4,0,n/4).asLong();b.site=site;b.linked=true;b.stock.family=family.id;r.state.boxes.put(RuralSettlement.key(c.id,b.pos),b);}RuralRules.put(r.stocks(family.id),true,initial/2);RuralRules.put(r.stocks(family.id),false,initial-initial/2);}
  void fields(CityData.City c,BlockPos pos){var plan=m.state.cities.computeIfAbsent(c.id,k->new CityMaintenance.Plan());plan.enabled=true;for(int n=0;n<20;n++)plan.baseline.put(pos.offset(n%5,0,n/5).asLong(),CityMaintenance.encode(Blocks.WHEAT.defaultBlockState()));}
  Map<String,Object> setup(){var out=new LinkedHashMap<String,Object>();out.put("kind","isolated_one_time_scale100_fixture");out.put("people",r.state.people.size());out.put("budget",initialCash);out.put("grain",initialGrain);out.put("cities",towns.stream().map(c->Map.of("id",c.id,"owner",c.owner,"name",c.name,"dimension",c.dimension,"min",new int[]{c.bounds.min().getX(),c.bounds.min().getY(),c.bounds.min().getZ()},"max",new int[]{c.bounds.max().getX(),c.bounds.max().getY(),c.bounds.max().getZ()},"residents",c.residents,"registry",CityRegistry.export(c))).toList());out.put("rural",r.state);out.put("trade",t.state);out.put("economy",e.state);out.put("maintenance",m.state);return out;}
  Map<String,Object> sample(int day){var out=new LinkedHashMap<String,Object>();out.put("day",day);out.put("grainMarkets",towns.stream().map(c->{var p=t.state.towns.get(c.id).prices[0];int stock=t.stock(s,c.id,0),target=t.target(s,c.id,0),base=t.base(c.id,0);long now=r.state.simulationTicks,rich=p.richSince<0?0:now-p.richSince,empty=p.emptySince<0?0:now-p.emptySince;return Map.of("town",c.name,"urban",t.state.towns.get(c.id).urban,"stock",stock,"target",target,"actualQuote",TradeRules.quote(base,stock,target,empty,rich),"plannerQuoteBeforeCargo",TradeRules.quote(base,stock,target,0,0),"richTicks",rich);}).toList());out.put("merchants",t.state.businesses.values().stream().filter(b->b.kind.equals("procurement")).map(b->{var owner=r.state.people.get(b.owner);var family=r.state.families.get(owner.family);var row=new LinkedHashMap<String,Object>();row.put("name",owner.name);row.put("dead",owner.dead);row.put("role",owner.role);row.put("familyCash",t.familyMoney(s,family));row.put("businessCash",e.balance(b.account));row.put("wheat",RuralRules.count(r.stocks(family.id),false));row.put("bread",RuralRules.count(r.stocks(family.id),true));row.put("meat",RuralMeat.total(r.stocks(family.id)));row.put("nourishment",family.members.stream().map(r.state.people::get).mapToDouble(p->p.nourishment).sum());row.put("familyDeaths",family.members.stream().map(r.state.people::get).filter(p->p.dead).count());row.put("familyHungry",family.members.stream().map(r.state.people::get).filter(p->!p.dead&&p.hunger()>0).count());row.put("stock",Arrays.stream(b.stock).mapToInt(l->l.count).sum());row.put("listed",Arrays.stream(b.listed).mapToInt(l->l.count).sum());row.put("cargo",Arrays.stream(b.cargo).mapToInt(l->l.count).sum());row.put("sales",b.sales);row.put("purchases",b.purchases);row.put("foodDrawn",b.foodDrawn);row.put("closing",b.closing);row.put("phase",b.phase);row.put("status",b.status);row.put("planned",b.planned);row.put("perception",b.perception);row.put("ownerCash",e.balance(CityEconomy.person(b.owner)));row.put("departedDay",b.departedDay);return row;}).toList());out.put("money",t.money(s));out.put("issued",CityCurrency.issued(s));out.put("retired",CityCurrency.retired(s));out.put("grain",t.grain(s));out.put("harvested",t.harvested(s));out.put("eaten",t.eaten(s));out.put("meatProduced",t.meatProduced(s));out.put("meatEaten",t.meatEaten(s));out.put("spoiledGrain",FoodDecay.grain(s));out.put("spoiledMeat",FoodDecay.meat(s));out.put("meat",t.meat(s));out.put("goods",t.goods(s));out.put("grainError",t.grain(s)-initialGrain-t.harvested(s)+t.eaten(s)+FoodDecay.grain(s));out.put("meatError",t.meat(s)-initialMeat-t.meatProduced(s)+t.meatEaten(s)+FoodDecay.meat(s));out.put("goodsError",t.goods(s)-initialGoods-t.state.run.supplierInput);out.put("deaths",r.state.people.values().stream().filter(p->p.dead).count());out.put("hungry",r.state.people.values().stream().filter(p->!p.dead&&p.hunger()>0).count());out.put("repaired",towns.stream().mapToLong(c->c.works.repaired).sum());out.put("constructed",towns.stream().mapToLong(c->c.works.constructed).sum());out.put("demolished",towns.stream().mapToLong(c->c.works.demolished).sum());out.put("damage",towns.stream().flatMap(c->c.housing.houses.values().stream()).mapToInt(h->h.damaged.size()).sum());return out;}
  Map<String,Object> run(){return run(60);} Map<String,Object> run(int totalDays){for(long tick=20;tick<=totalDays*24000L;tick+=20){long started=System.nanoTime();r.state.simulationTicks=tick;r.state.simulationDay=tick;CityRiskLab.beforeStep(s);CityCurrency.advance(s,20);for(var c:towns){c.works.clock=tick;c.works.day=tick;FamilyLivelihood.daily(s,c);CityWorks.updateEmergency(s,c);if(c.works.emergency&&!c.works.emergencyAnnounced){RuralSuccession.emergencyNow(s,c);c.works.emergencyAnnounced=true;}if(!c.works.emergency)c.works.emergencyAnnounced=false;CityHousing.advanceSupply(c.housing,20);if(tick%72000==0)CityWorks.decay(s,c);}
    RuralSettlement.workStep(s,r);if(tick%1200==0){r.state.minutes++;for(var c:towns)r.cycle(s,c,r.state.minutes%30==0,r.state.minutes%60==0);}
    if(tick%3000==0)for(var w:m.state.workers.entrySet())CityWorks.worker(s,CityData.get(s).require(w.getValue().city),w.getKey(),tick);CityTrade.step(s);CityCurrency.settle(s);CityRiskLab.afterStep(s);CityModelLab.tick(s);maxStepNanos=Math.max(maxStepNanos,System.nanoTime()-started);
    if(tick%24000==0){var row=sample((int)(tick/24000));daily.add(row);conserved&=((Number)row.get("money")).longValue()==initialCash+CityCurrency.issued(s)-CityCurrency.retired(s)&&((Number)row.get("grainError")).longValue()==0&&((Number)row.get("meatError")).longValue()==0&&((Number)row.get("goodsError")).longValue()==0;}}
   var out=new LinkedHashMap<String,Object>();out.put("kind","automated_multiple_pairs_actual_rules_not_live_server");out.put("population",r.state.people.size());out.put("towns",towns.size());out.put("initialMoney",initialCash);out.put("merchantsPerCity",merchants);out.put("initialRoles",initialRoles);out.put("initialGrain",initialGrain);out.put("allDaysConserved",conserved);out.put("daily",daily);out.put("people",r.state.people);out.put("businesses",t.state.businesses);out.put("accounts",e.state.accounts);out.put("households",guard.familyCash());out.put("maxStepMs",maxStepNanos/1e6);out.put("wallSeconds",(System.nanoTime()-started)/1e9);return out;}
  public void close(){guard.close();}
 }
 @GameTest(template="empty",timeoutTicks=200) public static void hundredPeopleSixtyDaysWithMaintenance(GameTestHelper h){try(var f=new Fixture(h)){
  try{Files.writeString(Path.of("scale100-setup.json"),CityEconomy.JSON.toJson(f.setup()));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
  h.assertTrue(f.r.state.people.size()==100&&f.initialCash==30000&&f.initialGrain==1500,"Exactly fivefold population/assets, no recurring money/food inputs");
  for(var c:List.of(f.city,f.village)){CityWorks.validate(c.works,c);CityHousing.validate(c,c.housing);for(var b:c.buildings.values())CityRegistry.inside(c,b.bounds());}
  var report=f.run();try{Files.writeString(Path.of("scale100-automated.json"),CityEconomy.JSON.toJson(report));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
  h.assertTrue(f.conserved,"100person60day: all balances and grain/meat/consumer goods must conserve; deaths reported separately");
  h.assertTrue(f.r.state.boxes.values().stream().allMatch(b->b.stock.wheat+b.stock.bread+RuralMeat.total(List.of(b.stock))<=10),"Every dedicated box respects ten-food limit");
 }h.succeed();}
 @GameTest(template="empty",timeoutTicks=200) public static void hundredPeopleAllWorkshopsDestroyedRecoverWithoutNewFunds(GameTestHelper h){try(var f=new Fixture(h)){
  int missing=0;for(var c:List.of(f.city,f.village))for(var entry:c.housing.houses.entrySet())if(CityWorks.workshop(c,entry.getKey())){entry.getValue().damaged.addAll(entry.getValue().baseline.keySet());missing+=entry.getValue().baseline.size();for(var target:entry.getValue().baseline.values()){String item=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(CityMaintenance.decode(target).getBlock().asItem()).toString();c.works.resources.merge(item,1,Integer::sum);}}
  h.assertTrue(missing==95,"All95 registered workplaces are destroyed in this independent fault fixture");
  var report=f.run(5);report.put("initialRebuildItems",missing);report.put("materialSource","Explicit finite95workstation items registered before independent fault run; not added to live scenario");try{Files.writeString(Path.of("scale100-workshop-recovery.json"),CityEconomy.JSON.toJson(report));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
  h.assertTrue(f.conserved,"Emergency repair does not create money, staple food, meat or consumer goods");
  h.assertTrue(java.util.stream.Stream.of(f.city,f.village).flatMap(c->c.housing.houses.entrySet().stream().filter(v->CityWorks.workshop(c,v.getKey()))).allMatch(v->v.getValue().damaged.isEmpty()),"Real workers and finite public resources restore all destroyed workplaces");
  h.assertTrue(f.r.state.people.values().stream().noneMatch(p->p.dead),"Emergency recovery finishes before hunger kills workers");
 }h.succeed();}
 @GameTest(template="empty",timeoutTicks=200) public static void missingWorkplaceMaterialsMustNotBeCreatedByRepair(GameTestHelper h){try(var f=new Fixture(h)){
  for(var c:List.of(f.city,f.village))for(var entry:c.housing.houses.entrySet())if(CityWorks.workshop(c,entry.getKey()))entry.getValue().damaged.addAll(entry.getValue().baseline.keySet());
  for(var c:List.of(f.city,f.village))c.works.resources.put("minecraft:oak_planks",0);
  var report=f.run(1);try{Files.writeString(Path.of("scale100-workshop-no-materials.json"),CityEconomy.JSON.toJson(report));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
  long missing=java.util.stream.Stream.of(f.city,f.village).flatMap(c->c.housing.houses.entrySet().stream().filter(v->CityWorks.workshop(c,v.getKey()))).mapToLong(v->v.getValue().damaged.size()).sum();
  h.assertTrue(missing==95&&f.conserved,"No rebuilding95workstations out of air when warehouse lacks workstations and the actual planks needed for the newly supported vanilla workbench recipe");
 }h.succeed();}
}
