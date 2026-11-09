package com.muxigame.city;

import java.util.*;
import java.nio.file.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Explicit administrator fault injection into an existing data-only fixture. No new people or funding. */
public final class CityRiskLab {
 public static final class Run {
  public UUID city,village;public boolean running,done;public String stage="未启动",failure="",startedAt="",endedAt="";
  public long startClock,startGame,lastSample,initialMoney,initialGrain,initialMeat,initialGoods,initialHarvest,initialEaten,initialMeatProduced,initialMeatEaten,initialSupplierInput;
  public Set<UUID> originalPeople=new LinkedHashSet<>(),originalDead=new LinkedHashSet<>();
  public Map<UUID,Set<Long>> damagedSites=new LinkedHashMap<>();public Map<UUID,Long> pauseClockShift=new LinkedHashMap<>();
  public int destroyedWorkpoints,craftedWorkbenches,craftPlanks;
  public List<Map<String,Object>> samples=new ArrayList<>();
 }
 static Run run(MinecraftServer s){return CityTrade.get(s).state.riskRun;}
 static boolean scope(Run r,UUID city){return Objects.equals(r.city,city)||Objects.equals(r.village,city);}
 static double cropFactor(MinecraftServer s,UUID city){var r=run(s);return r.running&&scope(r,city)&&r.stage.equals("减产30%")?.7:1;}
 static boolean tradeBlocked(MinecraftServer s,UUID city){var r=run(s);return r.running&&scope(r,city)&&r.stage.equals("贸易中断");}
 static String stage(long elapsed){long day=elapsed/24000;return day<2?"基线":day<7?"减产30%":day<12?"减产恢复":day<15?"贸易中断":day<20?"贸易恢复":"工作点恢复";}
 static void register(RegisterCommandsEvent event){event.getDispatcher().register(Commands.literal("cityrisk").requires(s->s.hasPermission(2))
  .then(Commands.literal("start").then(Commands.argument("city",StringArgumentType.word()).then(Commands.argument("village",StringArgumentType.word()).executes(ctx->{try{start(ctx.getSource().getServer(),UUID.fromString(StringArgumentType.getString(ctx,"city")),UUID.fromString(StringArgumentType.getString(ctx,"village")));ctx.getSource().sendSuccess(()->Component.literal("现有人物资产的30日抗风险测试已开始，不重置、不补款、不复活"),false);return 1;}catch(Exception e){ctx.getSource().sendFailure(Component.literal(e.getMessage()));return 0;}}))))
  .then(Commands.literal("status").executes(ctx->{write(ctx.getSource().getServer());var r=run(ctx.getSource().getServer());ctx.getSource().sendSuccess(()->Component.literal(r.stage+"；运行="+r.running+"；完成="+r.done+"；"+r.failure),false);return 1;})));
 }
 static void start(MinecraftServer s,UUID cityId,UUID villageId){var t=CityTrade.get(s);var r=t.state.riskRun;var d=RuralSettlement.get(s);
  CityEconomy.require(!r.running&&!r.done&&!t.state.run.running,"已有测试不可重开或覆盖");
  var city=CityData.get(s).require(cityId);var village=CityData.get(s).require(villageId);
  CityEconomy.require(city.name.equals("五倍测试·城市")&&village.name.equals("五倍测试·农村")&&city.works.virtual&&village.works.virtual,"只允许指定五倍纯数据测试城村，不操作原村");
  CityEconomy.require(!d.state.enabled.contains(cityId)&&!d.state.enabled.contains(villageId),"先保持测试场暂停");
  r.city=cityId;r.village=villageId;
  for(var c:List.of(city,village)){var targets=new LinkedHashSet<Long>();for(var entry:c.housing.houses.entrySet())if(CityWorks.artisanWorkshop(s,c,entry.getKey()))for(var block:entry.getValue().baseline.entrySet())if(CityWorks.state(block.getValue()).is(Blocks.CRAFTING_TABLE))targets.add(block.getKey());
   CityEconomy.require(!targets.isEmpty()&&c.works.resources.getOrDefault("minecraft:oak_planks",0)>=4*targets.size(),"缺少现有木板或工匠工作点，不能凭空准备重建物资");r.damagedSites.put(c.id,targets);
   CityEconomy.require(!c.housing.testSupply,"测试供货须保持关闭");
  }
  r.originalPeople.addAll(city.residents);r.originalPeople.addAll(village.residents);for(var id:r.originalPeople)if(d.state.people.get(id).dead)r.originalDead.add(id);
  r.initialMoney=t.money(s);r.initialGrain=t.grain(s);r.initialMeat=t.meat(s);r.initialGoods=t.goods(s);r.initialHarvest=t.harvested(s);r.initialEaten=t.eaten(s);r.initialMeatProduced=t.meatProduced(s);r.initialMeatEaten=t.meatEaten(s);r.initialSupplierInput=t.state.run.supplierInput;
  r.startClock=d.state.simulationTicks;r.startGame=s.overworld().getGameTime();r.startedAt=java.time.OffsetDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).toString();
  // Paused cities do not accrue centuries of business/blocked-site time while other cities run.
  for(var c:List.of(city,village)){long shift=r.startClock-c.works.clock;r.pauseClockShift.put(c.id,shift);c.works.clock+=shift;c.works.day=d.state.simulationDay;c.works.nextDecay+=shift;c.works.nextMeal+=shift;c.works.nextSegment+=shift;for(var segment:c.works.queue)segment.day+=shift;
   for(var b:t.state.businesses.values())if(b.city.equals(c.id)){if(b.lastIncome>=0)b.lastIncome+=shift;if(b.arrive>0)b.arrive+=shift;if(b.nextTrip>0)b.nextTrip+=shift;}
   for(var id:c.residents)if(t.state.staffIncomeAt.containsKey(id))t.state.staffIncomeAt.computeIfPresent(id,(k,v)->v+shift);
   for(var site:d.state.sites.values())if(site.city.equals(c.id)){if(site.blockedSince>0)site.blockedSince+=shift;if(site.vacantSince>0)site.vacantSince+=shift;}
   c.works.enabled=true;d.state.enabled.add(c.id);
  }
  t.linkStaff();d.state.speed=30;r.running=true;r.stage="基线";r.lastSample=r.startClock;sample(s);write(s);t.setDirty();d.setDirty();CityData.get(s).setDirty();
 }
 static void damage(MinecraftServer s){var r=run(s);for(var entry:r.damagedSites.entrySet())CityEconomy.require(CityData.get(s).require(entry.getKey()).works.resources.getOrDefault("minecraft:oak_planks",0)>=4*entry.getValue().size(),"现有木板不足，灾害注入停止，不补材料");for(var entry:r.damagedSites.entrySet()){var c=CityData.get(s).require(entry.getKey());int count=entry.getValue().size();int planks=c.works.resources.getOrDefault("minecraft:oak_planks",0);
   c.works.resources.put("minecraft:oak_planks",planks-4*count);c.works.resources.merge("minecraft:crafting_table",count,Integer::sum);r.craftPlanks+=4*count;r.craftedWorkbenches+=count;
   for(long pos:entry.getValue()){var h=CityWorks.at(c,pos);if(h==null||h.damaged.contains(pos))continue;var old=CityWorks.dataAt(s,c,pos);h.damaged.add(pos);c.works.damageSince.put(pos,c.works.clock);CityWorks.schedule(c,pos,Blocks.AIR.defaultBlockState(),old,"RISK_WORKPLACE_DAMAGE");r.destroyedWorkpoints++;}
   CityWorks.updateEmergency(s,c);
  }CityData.get(s).setDirty();
 }
 static void beforeStep(MinecraftServer s){var r=run(s);if(!r.running)return;long elapsed=RuralSettlement.get(s).state.simulationTicks-r.startClock;String next=stage(elapsed);if(!r.stage.equals(next)){r.stage=next;try{if(next.equals("工作点恢复"))damage(s);}catch(IllegalArgumentException ex){r.failure=ex.getMessage();r.running=false;pause(s);}sample(s);write(s);} }
 static void afterStep(MinecraftServer s){var r=run(s);if(!r.running)return;long now=RuralSettlement.get(s).state.simulationTicks;if(now-r.lastSample>=24000){r.lastSample=now;sample(s);write(s);}if(now-r.startClock>=30L*24000){r.running=false;r.done=true;r.stage="完成";r.endedAt=java.time.OffsetDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).toString();pause(s);sample(s);write(s);}CityTrade.get(s).setDirty();}
 static void pause(MinecraftServer s){var r=run(s);for(var id:List.of(r.city,r.village)){var c=CityData.get(s).require(id);c.works.enabled=false;RuralSettlement.get(s).state.enabled.remove(id);}CityData.get(s).setDirty();RuralSettlement.get(s).setDirty();}
 static void sample(MinecraftServer s){var r=run(s);var t=CityTrade.get(s);var d=RuralSettlement.get(s);var e=CityEconomy.get(s);var row=new LinkedHashMap<String,Object>();
  row.put("stage",r.stage);row.put("days",(d.state.simulationTicks-r.startClock)/24000.0);row.put("gameTick",s.overworld().getGameTime());row.put("simulationTicks",d.state.simulationTicks);row.put("money",t.money(s));row.put("moneyError",t.money(s)-r.initialMoney);row.put("grain",t.grain(s));row.put("grainError",t.grain(s)-r.initialGrain-(t.harvested(s)-r.initialHarvest)+(t.eaten(s)-r.initialEaten));row.put("meatError",t.meat(s)-r.initialMeat-(t.meatProduced(s)-r.initialMeatProduced)+(t.meatEaten(s)-r.initialMeatEaten));row.put("goodsError",t.goods(s)-r.initialGoods-(t.state.run.supplierInput-r.initialSupplierInput));
  row.put("living",r.originalPeople.stream().map(d.state.people::get).filter(p->!p.dead).count());row.put("newDeaths",r.originalPeople.stream().filter(id->!r.originalDead.contains(id)&&d.state.people.get(id).dead).map(id->d.state.people.get(id).name).toList());row.put("deadRemainDead",r.originalDead.stream().allMatch(id->d.state.people.get(id).dead));
  row.put("people",r.originalPeople.stream().map(d.state.people::get).map(p->{var info=new LinkedHashMap<String,Object>();info.put("id",p.id);info.put("name",p.name);info.put("role",p.role);info.put("dead",p.dead);info.put("hunger",p.hunger());info.put("nourishment",p.nourishment);info.put("cash",e.balance(CityEconomy.person(p.id)));info.put("incomeAt",t.state.staffIncomeAt.get(p.id));info.put("hunts",p.hunts);info.put("emptyHunts",p.emptyHunts);info.put("meatProduced",p.meatProduced);return info;}).toList());
  row.put("families",d.state.families.values().stream().filter(f->scope(r,f.city)).map(f->{var info=new LinkedHashMap<String,Object>();info.put("id",f.id);info.put("names",f.members.stream().map(id->d.state.people.get(id).name).toList());info.put("cash",t.familyMoney(s,f));info.put("wheat",RuralRules.count(d.stocks(f.id),false));info.put("bread",RuralRules.count(d.stocks(f.id),true));info.put("meat",RuralMeat.total(d.stocks(f.id)));info.put("protectedFood",CityWorksFinance.protectedFood(s,f.members.getFirst()));return info;}).toList());
  row.put("businesses",t.state.businesses.values().stream().filter(b->scope(r,b.city)).map(b->CityEconomy.JSON.fromJson(CityEconomy.JSON.toJson(b),CityTrade.Business.class)).toList());row.put("markets",List.of(r.city,r.village).stream().map(id->Map.of("city",id,"prices",java.util.stream.IntStream.range(0,4).map(g->t.quote(s,id,g,d.state.simulationTicks)).toArray(),"stock",java.util.stream.IntStream.range(0,4).map(g->t.stock(s,id,g)).toArray())).toList());
  row.put("works",List.of(r.city,r.village).stream().map(id->{var c=CityData.get(s).require(id);return Map.of("city",id,"emergency",c.works.emergency,"repaired",c.works.repaired,"wages",c.works.wages,"resources",new TreeMap<>(c.works.resources),"materialsConsumed",new TreeMap<>(c.works.materialsConsumed),"remainingDamagedTestSites",r.damagedSites.get(id).stream().filter(p->CityWorks.at(c,p).damaged.contains(p)).count());}).toList());
  row.put("allBoxesWithinTen",d.state.boxes.values().stream().filter(b->scope(r,b.city)).allMatch(b->b.stock.wheat+b.stock.bread+RuralMeat.total(List.of(b.stock))<=10));
  r.samples.add(row);if(r.samples.size()>64)r.samples.removeFirst();if(((Number)row.get("moneyError")).longValue()!=0||((Number)row.get("grainError")).longValue()!=0||((Number)row.get("meatError")).longValue()!=0||((Number)row.get("goodsError")).longValue()!=0){r.failure="物资或资金对账异常，已暂停保留现场";r.running=false;pause(s);}
 }
 static void write(MinecraftServer s){try{var path=s.getWorldPath(LevelResource.ROOT).resolve("city_settlements/risk098/latest.json");Files.createDirectories(path.getParent());Files.writeString(path,CityEconomy.JSON.toJson(run(s)));}catch(Exception e){org.slf4j.LoggerFactory.getLogger("CityRiskLab").warn("抗风险报告写入失败",e);}}
}
