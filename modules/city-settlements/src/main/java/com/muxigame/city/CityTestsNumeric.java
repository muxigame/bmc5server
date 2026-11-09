package com.muxigame.city;

import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.gametest.*;

/** Full existing settlement/trade rules, accelerated in memory; never a live-world observation. */
@GameTestHolder(CityMod.ID) @PrefixGameTestTemplate(false)
public class CityTestsNumeric {
 static final class NumericFixture implements AutoCloseable {
  final MinecraftServer server;
  final RuralSettlement rural; final CityTrade trade; final CityEconomy economy; final CityMaintenance maintenance;
  final RuralSettlement.State oldRural; final CityTrade.State oldTrade; final CityEconomy.State oldEconomy; final CityMaintenance.State oldMaintenance;
  final LinkedHashMap<UUID,CityData.City> oldCities;
  final List<CityEconomy.Entry> oldJournal;
  final CityData.City city,village;
  final List<RuralRules.Person> cityPeople=new ArrayList<>();
  final List<Map<String,Object>> days=new ArrayList<>();
  final Map<String,Object> checkpoints=new LinkedHashMap<>();
  final Map<String,Object> initialFamilies=new LinkedHashMap<>();
  final Map<String,Long> initialBusinesses=new LinkedHashMap<>();
  boolean conserved=true;
  final long initialCash,initialGrain,initialMeat,initialGoods;
  final long started=System.nanoTime();

  NumericFixture(GameTestHelper h) {this(h,true);}
  NumericFixture(GameTestHelper h,boolean twoMerchants) {
   server=h.getLevel().getServer(); rural=RuralSettlement.get(server);trade=CityTrade.get(server);economy=CityEconomy.get(server);maintenance=CityMaintenance.get(server);
   oldRural=rural.state;oldTrade=trade.state;oldEconomy=economy.state;oldMaintenance=maintenance.state;
   oldCities=new LinkedHashMap<>(CityData.get(server).cities);oldJournal=new ArrayList<>(economy.journal);
   rural.state=new RuralSettlement.State();trade.state=new CityTrade.State();economy.state=new CityEconomy.State();maintenance.state=new CityMaintenance.State();
   CityData.get(server).cities.clear();
   city=town("自动数值·城",250000,true);village=town("自动数值·村",251000,false);
   trade.state.run.city=city.id;trade.state.run.village=village.id;
   String[] kinds={"procurement","supplier",twoMerchants?"procurement":"","supplier_helper","baker","urban_farmer","artisan",""};
   for(int i=0;i<8;i++)cityPeople.add(person(city,"城民"+(i+1)));
   for(int f=0;f<4;f++) {
    var family=family(cityPeople.get(f*2),cityPeople.get(f*2+1));
    home(city,family,new BlockPos(250000+f*32,90,250000));
    for(int j=0;j<2;j++) {
     int i=f*2+j;var p=cityPeople.get(i);String kind=kinds[i];if(kind.isEmpty())continue;
     var station=new BlockPos(250000+i*16,90,250032);job(city,p,station,kind);
     if(kind.equals("artisan")) {var w=new CityMaintenance.Worker();w.city=city.id;w.enabled=w.artisan=true;w.role="工匠";maintenance.state.workers.put(p.id,w);continue;}
     var b=business(city,p,station,kind);
     if(kind.equals("procurement"))fund(b.account,1000);
     else if(kind.equals("baker"))fund(b.account,500);
     else if(kind.equals("supplier"))fund(b.account,1000);
     else if(kind.equals("urban_farmer")){b.account=CityEconomy.person(p.id);b.listed[0].add(40,0);}
    }
    boxes(city,family,3,20,250000+f*32);
   }
   for(int f=0;f<6;f++) {
    var husband=person(village,"村民"+(f+1)+"夫");var wife=person(village,"村民"+(f+1)+"妻");
    wife.female=true;husband.spouse=wife.id;wife.spouse=husband.id;
    var family=family(husband,wife);int x=251000+(f<3?f*16:f==3?0:f==4?96:64);int z=f==3||f==4?250032:250000;
    home(village,family,new BlockPos(x,90,z));
    String kind=f<3?"farmer":f<5?"hunter":"merchant";
    var station=new BlockPos(x,90,z+16);job(village,husband,station,kind);job(village,wife,station.offset(8,0,0),"cook");
    boxes(village,family,f==5?15:3,20,x,z+8);
    if(f<3)fields(village,251000+f*24);
    if(f>=3&&f<5){var cell=new HunterForest.Cell();cell.dimension=village.dimension;cell.stand=station.offset(0,0,32).asLong();cell.x=BlockPos.of(cell.stand).getX()>>4;cell.z=BlockPos.of(cell.stand).getZ()>>4;cell.usable=true;cell.canopy=4;cell.logs=1;rural.state.forest.put(HunterForest.key(cell.dimension,cell.x,cell.z),cell);}
    if(f==5){var b=business(village,husband,station,"head");b.account=CityEconomy.person(husband.id);fund(b.account,500);RuralRules.put(rural.stocks(family.id),false,60);}
   }
   if(!twoMerchants){var former=cityPeople.get(2);var old=business(city,former,new BlockPos(250032,90,250032),"procurement");old.closing=true;old.status="对照：原企业停业，1000原资产保留";fund(old.account,1000);}
   fund(CityEconomy.treasury(city.id),1000);
   initialCash=trade.money(server);initialGrain=trade.grain(server);initialMeat=trade.meat(server);initialGoods=trade.goods(server);
   initialFamilies.putAll(familyCash());for(var b:trade.state.businesses.values())initialBusinesses.put(b.id.toString(),economy.balance(b.account));
  }
  void fund(String account,long n){economy.state.accounts.merge(account,n,Long::sum);}
  CityData.City town(String name,int x,boolean urban){var c=CityData.get(server).create(UUID.randomUUID(),name,"minecraft:overworld",CityBounds.between(new BlockPos(x,88,250000),new BlockPos(x+127,95,250127)));c.works.virtual=true;c.works.finalized=true;c.works.enabled=true;var town=new CityTrade.Town();town.city=c.id;town.urban=urban;town.localOnly=true;trade.state.towns.put(c.id,town);rural.state.enabled.add(c.id);return c;}
  static UUID fixed(String name){return UUID.nameUUIDFromBytes(("city-numeric-fixture:"+name).getBytes(StandardCharsets.UTF_8));}
  RuralRules.Person person(CityData.City c,String name){UUID id=fixed(name);c.residents.add(id);economy.state.accounts.put(CityEconomy.person(id),50L);var p=rural.enroll(server,c,id,name);p.trainedHours=10;return p;}
  RuralRules.Family family(RuralRules.Person a,RuralRules.Person b){var f=rural.state.families.get(a.family);var old=rural.state.families.remove(b.family);old.members.remove(b.id);RuralRules.admit(f,b,rural.state.people);return f;}
  void home(CityData.City c,RuralRules.Family f,BlockPos pos){UUID id=UUID.randomUUID();c.buildings.put(id,new CityRegistry.Building(id,"住宅","住宅",CityBounds.between(pos,pos.offset(6,3,6))));var h=CityHousing.house(c,id);h.configured=true;h.personal=true;h.owner=f.members.getFirst();h.beds=2;h.residents.addAll(f.members);h.baseline.put(pos.asLong(),CityMaintenance.encode(Blocks.OAK_PLANKS.defaultBlockState()));}
  void job(CityData.City c,RuralRules.Person p,BlockPos pos,String kind){var block=switch(kind){case "farmer","urban_farmer"->RuralBlocks.SIFTER.get();case "cook"->RuralBlocks.STOVE.get();case "hunter"->RuralBlocks.HUNTER.get();case "merchant"->RuralBlocks.DESK.get();case "procurement"->RuralBlocks.GUILD.get();case "baker"->RuralBlocks.OVEN.get();case "artisan"->Blocks.CRAFTING_TABLE;default->RuralBlocks.SUPPLY.get();};var site=new RuralSettlement.Site();site.city=c.id;site.worker=p.id;site.pos=pos.asLong();site.kind=kind.equals("urban_farmer")?"farmer":kind.equals("supplier_helper")?"supplier":kind;site.expectedBlock=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString();rural.state.sites.put(RuralSettlement.key(c.id,site.pos),site);p.role=switch(kind){case "farmer","urban_farmer"->RuralRules.Role.FARMER;case "cook"->RuralRules.Role.COOK;case "hunter"->RuralRules.Role.HUNTER;case "merchant"->RuralRules.Role.MERCHANT;case "procurement"->RuralRules.Role.PROCUREMENT;case "baker"->RuralRules.Role.BAKER;case "artisan"->RuralRules.Role.ARTISAN;default->RuralRules.Role.SUPPLIER;};UUID id=UUID.randomUUID();c.buildings.put(id,new CityRegistry.Building(id,kind,"工坊",CityBounds.between(pos,pos)));var h=CityHousing.house(c,id);h.configured=true;h.personal=true;h.owner=p.id;h.workerLimit=1;h.workers.add(p.id);h.baseline.put(pos.asLong(),CityMaintenance.encode(block.defaultBlockState()));if(kind.equals("urban_farmer"))fields(c,pos.getX());}
  void fields(CityData.City c,int x){var plan=maintenance.state.cities.computeIfAbsent(c.id,k->new CityMaintenance.Plan());for(int n=0;n<20;n++)plan.baseline.put(new BlockPos(x+n,89,250080).asLong(),CityMaintenance.encode(Blocks.WHEAT.defaultBlockState()));}
  CityTrade.Business business(CityData.City c,RuralRules.Person p,BlockPos pos,String kind){var b=new CityTrade.Business();b.id=fixed(p.name+":"+kind);b.city=c.id;b.worker=b.owner=p.id;b.site=pos.asLong();b.kind=kind;b.account="trade:"+b.id;b.lastIncome=0;trade.state.businesses.put(b.id,b);return b;}
  void boxes(CityData.City c,RuralRules.Family f,int count,int initial,int x){boxes(c,f,count,initial,x,250016);}
  void boxes(CityData.City c,RuralRules.Family f,int count,int initial,int x,int z){long site=rural.state.sites.values().stream().filter(v->f.members.contains(v.worker)).findFirst().orElseThrow().pos;for(int n=0;n<count;n++){var b=new RuralSettlement.Box();b.city=c.id;b.pos=new BlockPos(x+n,90,z).asLong();b.site=site;b.linked=true;b.stock.family=f.id;rural.state.boxes.put(RuralSettlement.key(c.id,b.pos),b);}RuralRules.put(rural.stocks(f.id),true,initial/2);RuralRules.put(rural.stocks(f.id),false,initial-initial/2);}
  Map<String,Object> familyCash(){var rows=new LinkedHashMap<String,Object>();for(var f:rural.state.families.values()){var accounts=new LinkedHashSet<String>();for(var id:f.members)accounts.add(CityEconomy.person(id));for(var b:trade.state.businesses.values())if(f.members.contains(b.owner))accounts.add(b.account);long total=accounts.stream().mapToLong(economy::balance).sum();var row=new LinkedHashMap<String,Object>();row.put("members",f.members.stream().map(rural.state.people::get).map(p->p.name).toList());row.put("personalCash",trade.familyMoney(server,f));row.put("allOwnedCash",total);row.put("wheat",RuralRules.count(rural.stocks(f.id),false));row.put("bread",RuralRules.count(rural.stocks(f.id),true));row.put("cookedMeat",rural.stocks(f.id).stream().mapToInt(s->Arrays.stream(s.cooked).sum()).sum());row.put("nourishment",RuralRules.members(f,rural.state.people).stream().mapToDouble(p->p.nourishment).sum());rows.put(f.members.stream().map(rural.state.people::get).map(p->p.name).findFirst().orElseThrow(),row);}return rows;}
  Map<String,Object> businessCash(){var rows=new LinkedHashMap<String,Object>();for(var b:trade.state.businesses.values()){var row=new LinkedHashMap<String,Object>();long cash=economy.balance(b.account);row.put("kind",b.kind);row.put("owner",rural.state.people.get(b.owner).name);row.put("cash",cash);row.put("cashChange",cash-initialBusinesses.getOrDefault(b.id.toString(),0L));row.put("sales",b.sales);row.put("purchases",b.purchases);row.put("wages",b.wages);row.put("tax",b.tax);row.put("closing",b.closing);row.put("sold",b.sold);if(b.kind.equals("baker")){row.put("breadSold",b.sold[1]);row.put("actualBreadSaleUnitPrice",b.sold[1]==0?null:b.sales/(double)b.sold[1]);}rows.put(b.id.toString(),row);}return rows;}
  Map<String,Object> run(int count) {
   for(long tick=20;tick<=count*24000L;tick+=20){
    rural.state.simulationTicks=tick;rural.state.simulationDay=tick;
    for(var c:List.of(city,village)){c.works.clock=tick;c.works.day=tick;FamilyLivelihood.daily(server,c);}
    RuralSettlement.workStep(server,rural);
    if(tick%1200==0){rural.state.minutes++;for(var c:List.of(city,village))rural.cycle(server,c,rural.state.minutes%30==0,rural.state.minutes%60==0);}
    if(tick%3000==0)for(var p:cityPeople)if(p.role==RuralRules.Role.ARTISAN)CityWorks.worker(server,city,p.id,tick);
    CityTrade.step(server);
    if(tick%24000==0){var row=new LinkedHashMap<String,Object>();row.put("day",tick/24000);row.put("cash",trade.money(server));row.put("grain",trade.grain(server));row.put("grainError",trade.grain(server)-initialGrain-trade.harvested(server)+trade.eaten(server));row.put("goodsError",trade.goods(server)-initialGoods-trade.state.run.supplierInput);row.put("meatError",trade.meat(server)-initialMeat-trade.meatProduced(server)+trade.meatEaten(server));row.put("deaths",rural.state.people.values().stream().filter(p->p.dead).count());row.put("breadQuote",trade.quote(server,city.id,1,tick));row.put("grainQuote",trade.quote(server,city.id,0,tick));conserved&=trade.money(server)==initialCash&&((Number)row.get("grainError")).longValue()==0&&((Number)row.get("goodsError")).longValue()==0&&((Number)row.get("meatError")).longValue()==0;days.add(row);if(days.size()>64)days.removeFirst();if(tick/24000==15||tick/24000==30||tick/24000==60||tick/24000==120){var checkpoint=new LinkedHashMap<String,Object>();checkpoint.put("families",familyCash());checkpoint.put("businessCash",businessCash());checkpoints.put(Long.toString(tick/24000),checkpoint);}}
   }
   var report=new LinkedHashMap<String,Object>();report.put("kind","automated_actual_rules_not_live_server");report.put("scope","Healthy registered houses, real private work/food/commerce/standby wages; no decay, rent, spoilage or new relief subsystem");report.put("simulationDays",count);report.put("wallSeconds",(System.nanoTime()-started)/1e9);report.put("config",trade.state.config);report.put("initialMoney",initialCash);report.put("finalMoney",trade.money(server));report.put("initialGrain",initialGrain);report.put("finalGrain",trade.grain(server));report.put("allDaysConserved",conserved);report.put("initialFamilies",initialFamilies);report.put("initialBusinessCash",initialBusinesses);report.put("checkpoints",checkpoints);report.put("finalFamilies",familyCash());report.put("finalBusinessCash",businessCash());report.put("days",days);report.put("people",rural.state.people.values());report.put("businesses",trade.state.businesses.values());report.put("allBoxesWithinTen",rural.state.boxes.values().stream().allMatch(b->b.stock.wheat+b.stock.bread+RuralMeat.total(List.of(b.stock))<=10));report.put("housingProductionEnabled",java.util.stream.Stream.of(city,village).flatMap(c->c.housing.houses.values().stream()).anyMatch(h->h.production));return report;
  }
  public void close(){rural.state=oldRural;trade.state=oldTrade;economy.state=oldEconomy;maintenance.state=oldMaintenance;CityData.get(server).cities.clear();CityData.get(server).cities.putAll(oldCities);economy.journal.clear();economy.journal.addAll(oldJournal);}
 }
 @GameTest(template="empty", timeoutTicks=200) public static void finiteThirtyDaysUsesActualRulesAndConservesAccounts(GameTestHelper h){
  try(var f=new NumericFixture(h)){
   var report=f.run(30);
   try{var path=Path.of("numeric-economy-validation.json");Files.writeString(path,CityEconomy.JSON.toJson(report));}catch(java.io.IOException e){throw new IllegalStateException(e);}
   h.assertTrue(f.initialCash==6000&&f.trade.money(f.server)==f.initialCash,"Finite 6000 silver stays finite after30days");
   h.assertTrue(f.days.stream().allMatch(d->((Number)d.get("grainError")).longValue()==0&&((Number)d.get("goodsError")).longValue()==0&&((Number)d.get("meatError")).longValue()==0),"Actual harvest/cooking/trading/eating conserve grain, meat and goods");
   h.assertTrue((Boolean)report.get("allBoxesWithinTen"),"Dedicated food boxes never exceed10");
   long deaths=f.rural.state.people.values().stream().filter(p->p.dead).count();
   h.assertTrue(deaths==0,"Normal finite20-person fixture new deaths="+deaths+"; inspect numeric-economy-validation.json");
  }h.succeed();
 }
 @GameTest(template="empty", timeoutTicks=200) public static void oneMerchantComparisonKeepsSamePeopleAndCapital(GameTestHelper h){
  try(var f=new NumericFixture(h,false)){
   var report=f.run(30);
   try{Files.writeString(Path.of("numeric-economy-one-merchant.json"),CityEconomy.JSON.toJson(report));}catch(java.io.IOException e){throw new IllegalStateException(e);}
   h.assertTrue(f.initialCash==6000&&f.trade.money(f.server)==6000,"One-merchant comparison retains20people/6000silver, former owner's1000 stays in closed original enterprise");
   h.assertTrue(f.days.stream().allMatch(d->((Number)d.get("grainError")).longValue()==0&&((Number)d.get("goodsError")).longValue()==0&&((Number)d.get("meatError")).longValue()==0),"All commodities conserved");
   long deaths=f.rural.state.people.values().stream().filter(p->p.dead).count();
   h.assertTrue(deaths==0,"One-merchant normal fixture new deaths="+deaths+"; inspect numeric-economy-one-merchant.json");
  }h.succeed();
 }
 @GameTest(template="empty", timeoutTicks=200) public static void finiteOneHundredTwentyDaysReportsEconomicAttrition(GameTestHelper h){
  try(var f=new NumericFixture(h)){
   var report=f.run(120);
   try{Files.writeString(Path.of("numeric-economy-120days.json"),CityEconomy.JSON.toJson(report));}catch(java.io.IOException e){throw new IllegalStateException(e);}
   h.assertTrue(f.conserved,"120 days conserve all cash and food/goods flows, including deaths and exits");
   h.assertTrue((Boolean)report.get("allBoxesWithinTen"),"Long run does not bypass physical food caps");
   h.assertTrue(!(Boolean)report.get("housingProductionEnabled"),"Registered fixture houses never generate free products");
  }h.succeed();
 }
}
