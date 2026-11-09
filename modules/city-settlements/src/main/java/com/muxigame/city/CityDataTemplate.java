package com.muxigame.city;

import com.google.gson.*;
import java.util.*;
import java.nio.file.*;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Atomic, bounded data bootstrap. New people start with zero; budget/materials move from an existing city. */
public final class CityDataTemplate {
 public static final class Job {public int resident;public long pos;public String kind="farmer";}
 public static final class Store {public int resident;public long pos,site;public int wheat,bread;}
 public static final class Home {public UUID building;public List<Integer> residents=new ArrayList<>();public int owner=-1;}
 public static final class Template {public String name="数据聚落",dimension="minecraft:overworld";public int[] min,max;public UUID source;public long budget,personalSilver,headCapital;public boolean urban;public List<String> residents=new ArrayList<>();public JsonObject registry;public Map<String,Integer> materials=new LinkedHashMap<>();public List<List<Integer>> families=new ArrayList<>();public List<Job> jobs=new ArrayList<>();public List<Store> stores=new ArrayList<>();public List<Home> homes=new ArrayList<>();public List<Long> fields=new ArrayList<>(),forest=new ArrayList<>();}
 static CityData.City create(MinecraftServer s,UUID owner,Template in){
  CityEconomy.require(!CityTrade.get(s).state.run.running,"观察运行期间不能创建新城或人口");
  CityEconomy.require(in!=null&&in.min!=null&&in.max!=null&&in.min.length==3&&in.max.length==3&&in.residents!=null&&in.residents.size()<=32&&in.materials!=null&&in.materials.size()<=64&&in.budget>=0&&in.budget<=10000000,"模板容量或预算无效");
  var data=CityData.get(s);var rural=RuralSettlement.get(s);var e=CityEconomy.get(s);
  CityEconomy.require(data.cities.size()<128&&rural.state.people.size()+in.residents.size()<=512,"城市或人口上限");
  var bounds=new CityBounds(new BlockPos(in.min[0],in.min[1],in.min[2]),new BlockPos(in.max[0],in.max[1],in.max[2]));
  var c=new CityData.City(UUID.randomUUID(),owner,in.name,in.dimension,bounds);var level=CityEconomy.level(s,c);bounds.validateWorld(level);data.checkBounds(null,c.dimension,bounds);
  CityData.City source=in.source==null?null:data.require(in.source);
  CityEconomy.require((source!=null||in.budget==0&&in.materials.isEmpty())&&(source==null||e.balance(CityEconomy.treasury(source.id))>=in.budget),"预算必须由现有市政账户实际划转");
  for(var entry:in.materials.entrySet())CityEconomy.require(source!=null&&entry.getValue()!=null&&entry.getValue()>=0&&CityEconomy.item(entry.getKey())!=net.minecraft.world.item.Items.AIR&&source.works.resources.getOrDefault(entry.getKey(),0)>=entry.getValue(),"初始物资须来自现有公共仓库");
  for(String name:in.residents)CityData.validName(name);
  CityEconomy.require(in.personalSilver>=0&&in.personalSilver<=1000&&in.headCapital>=0&&in.headCapital<=1000000&&in.budget>=in.personalSilver*in.residents.size()+in.headCapital,"居民储蓄及头人经营款须包括在实际划入预算内");
  if(in.registry!=null)CityRegistry.read(c,in.registry);
  CityEconomy.require(in.jobs.size()<=32&&in.stores.size()<=128&&in.fields.size()<=640&&in.forest.size()<=128&&in.homes.size()<=128&&rural.state.boxes.size()+in.stores.size()<=4096,"模板岗位/储藏/田地/林地超限");
  var familyMembers=new HashSet<Integer>();for(var group:in.families){CityEconomy.require(!group.isEmpty()&&group.size()<=10,"家庭人数1至10");for(int id:group)CityEconomy.require(id>=0&&id<in.residents.size()&&familyMembers.add(id),"家庭成员重复或越界");}
  var positions=new HashSet<Long>();var employees=new HashSet<Integer>();for(var job:in.jobs){CityEconomy.require(job.resident>=0&&job.resident<in.residents.size()&&employees.add(job.resident)&&c.includes(BlockPos.of(job.pos))&&positions.add(job.pos)&&Set.of("farmer","cook","artisan","hunter","merchant").contains(job.kind),"岗位越界、职业无效或重复");var house=CityWorks.at(c,job.pos);CityEconomy.require(house!=null&&house.baseline.containsKey(job.pos),"工作方块须登记于建筑蓝图");}
  CityEconomy.require(in.headCapital==0||in.jobs.stream().filter(j->j.kind.equals("merchant")).count()==1,"头人经营款需要唯一的本地头人");
  int wheat=0,bread=0;for(var box:in.stores){CityEconomy.require(box.resident>=0&&box.resident<in.residents.size()&&c.includes(BlockPos.of(box.pos))&&positions.add(box.pos)&&box.wheat>=0&&box.bread>=0&&box.wheat+box.bread<=10&&in.jobs.stream().anyMatch(j->j.pos==box.site),"箱子越界/重复/超过10份或缺关联工作点");wheat+=box.wheat;bread+=box.bread;}
  CityEconomy.require(in.materials.getOrDefault("minecraft:wheat",0)>=wheat&&in.materials.getOrDefault("minecraft:bread",0)>=bread,"箱内口粮须从划入物资中扣除");
  for(var home:in.homes){var house=c.housing.houses.get(home.building);CityEconomy.require(house!=null&&home.residents.size()<=house.beds&&home.owner>=-1&&home.owner<in.residents.size(),"住房容量/所有者无效");for(int id:home.residents)CityEconomy.require(id>=0&&id<in.residents.size(),"住户编号越界");}
  for(long pos:in.fields)CityEconomy.require(c.includes(BlockPos.of(pos)),"农田越界");for(long pos:in.forest)CityEconomy.require(c.includes(BlockPos.of(pos)),"林地越界");
  // Template blocks are planned work, not free completed construction.
  for(var h:c.housing.houses.values()){h.damaged.addAll(h.baseline.keySet());h.stage="PLANNED";h.workers.clear();h.residents.clear();h.owner=null;h.personal=false;h.materials.clear();h.production=false;}
  c.works=new CityWorks.Plan();c.works.virtual=true;c.works.finalized=true;c.works.enabled=false;
  // Everything above is validation/detached data. Commit has no world/chunk/entity operations.
  data.cities.put(c.id,c);
  if(in.budget>0)CityEconomy.require(CityTrade.pay(s,CityEconomy.treasury(source.id),CityEconomy.treasury(c.id),in.budget,"DATA_CITY_BUDGET"),"预算划转失败");
  for(var entry:in.materials.entrySet()){source.works.resources.merge(entry.getKey(),-entry.getValue(),Integer::sum);c.works.resources.put(entry.getKey(),entry.getValue());}
  var ids=new ArrayList<UUID>();for(String name:in.residents){UUID id=UUID.randomUUID();ids.add(id);c.residents.add(id);e.state.accounts.put(CityEconomy.person(id),0L);var p=rural.enroll(s,c,id,name);p.role=RuralRules.Role.NONE;if(in.personalSilver>0)CityEconomy.require(CityTrade.pay(s,CityEconomy.treasury(c.id),CityEconomy.person(id),in.personalSilver,"DATA_CITY_SAVINGS"),"居民储蓄划转失败");}
  for(var group:in.families){var family=rural.state.families.get(rural.state.people.get(ids.get(group.getFirst())).family);for(int index:group){var p=rural.state.people.get(ids.get(index));if(p.family.equals(family.id))continue;var old=rural.state.families.get(p.family);old.members.remove(p.id);rural.state.families.remove(old.id);RuralRules.admit(family,p,rural.state.people);}}
  for(var job:in.jobs){var p=rural.state.people.get(ids.get(job.resident));var site=new RuralSettlement.Site();site.city=c.id;site.pos=job.pos;site.worker=p.id;site.kind=job.kind;site.expectedBlock=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(CityMaintenance.decode(CityWorks.at(c,job.pos).baseline.get(job.pos)).getBlock()).toString();site.present=false;rural.state.sites.put(RuralSettlement.key(c.id,job.pos),site);p.role=switch(job.kind){case "farmer"->RuralRules.Role.FARMER;case "cook"->RuralRules.Role.COOK;case "artisan"->RuralRules.Role.ARTISAN;case "hunter"->RuralRules.Role.HUNTER;default->RuralRules.Role.MERCHANT;};CityWorks.at(c,job.pos).workers.add(p.id);if(p.role==RuralRules.Role.ARTISAN){var w=new CityMaintenance.Worker();w.city=c.id;w.enabled=true;w.artisan=true;w.role="工匠";CityMaintenance.get(s).state.workers.put(p.id,w);}}
  for(var spec:in.stores){var box=new RuralSettlement.Box();box.city=c.id;box.pos=spec.pos;box.site=spec.site;box.linked=true;box.stock.family=rural.state.people.get(ids.get(spec.resident)).family;box.stock.wheat=spec.wheat;box.stock.bread=spec.bread;rural.state.boxes.put(RuralSettlement.key(c.id,box.pos),box);c.works.resources.merge("minecraft:wheat",-spec.wheat,Integer::sum);c.works.resources.merge("minecraft:bread",-spec.bread,Integer::sum);}
  for(var spec:in.homes){var house=c.housing.houses.get(spec.building);for(int index:spec.residents)house.residents.add(ids.get(index));if(spec.owner>=0){house.personal=true;house.owner=ids.get(spec.owner);}}
  var plan=new CityMaintenance.Plan();plan.enabled=true;for(long pos:in.fields)plan.baseline.put(pos,CityMaintenance.encode(net.minecraft.world.level.block.Blocks.WHEAT.defaultBlockState()));CityMaintenance.get(s).state.cities.put(c.id,plan);
  for(long pos:in.forest){var bp=BlockPos.of(pos);var cell=new HunterForest.Cell();cell.dimension=c.dimension;cell.x=bp.getX()>>4;cell.z=bp.getZ()>>4;cell.stand=pos;cell.usable=true;cell.canopy=4;cell.logs=1;rural.state.forest.put(HunterForest.key(c.dimension,cell.x,cell.z),cell);}
  var town=new CityTrade.Town();town.city=c.id;town.urban=in.urban;town.localOnly=true;CityTrade.get(s).state.towns.put(c.id,town);
  for(var job:in.jobs)if(job.kind.equals("merchant")){var b=new CityTrade.Business();b.id=UUID.randomUUID();b.city=c.id;b.worker=b.owner=ids.get(job.resident);b.site=job.pos;b.kind="head";b.account=CityEconomy.person(b.owner);b.lastIncome=rural.state.simulationTicks;CityTrade.get(s).state.businesses.put(b.id,b);if(in.headCapital>0)CityEconomy.require(CityTrade.pay(s,CityEconomy.treasury(c.id),b.account,in.headCapital,"DATA_CITY_HEAD_CAPITAL"),"头人经营款划转失败");}
  data.setDirty();rural.setDirty();e.setDirty();CityMaintenance.get(s).setDirty();CityTrade.get(s).setDirty();return c;
 }
 static void register(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> d){
 d.register(Commands.literal("citydata").requires(v->v.hasPermission(2)).then(Commands.literal("run").then(Commands.argument("id",StringArgumentType.word()).then(Commands.argument("enabled",com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(ctx->{var s=ctx.getSource().getServer();var c=CityData.get(s).require(UUID.fromString(StringArgumentType.getString(ctx,"id")));CityEconomy.require(c.works.virtual&&!CityTrade.get(s).state.run.running,"仅独立纯数据城市可切换，封闭观察中不可修改");boolean on=com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx,"enabled");c.works.enabled=on;if(on){var r=RuralSettlement.get(s);r.state.enabled.add(c.id);c.works.clock=r.state.simulationTicks;c.works.day=r.state.simulationDay;c.works.nextDecay=c.works.clock+72000;c.works.nextSegment=c.works.clock+3000;c.works.nextMeal=c.works.clock+36000;}else RuralSettlement.get(s).state.enabled.remove(c.id);CityData.get(s).setDirty();RuralSettlement.get(s).setDirty();ctx.getSource().sendSuccess(()->Component.literal(on?"数据城市已启动":"数据城市已暂停"),true);return 1;})))));
 d.register(Commands.literal("citydata").requires(v->v.hasPermission(2)).then(Commands.literal("import").then(Commands.argument("file",StringArgumentType.word()).executes(ctx->{
  String name=StringArgumentType.getString(ctx,"file");CityEconomy.require(name.matches("[A-Za-z0-9_-]{1,48}\\.json"),"文件名仅用英文数字、横线下划线，必须.json");var s=ctx.getSource().getServer();var path=s.getWorldPath(LevelResource.ROOT).resolve("city_settlements/data-templates").resolve(name);
  try{CityEconomy.require(Files.isRegularFile(path)&&!Files.isSymbolicLink(path)&&Files.size(path)<=1048576,"模板不存在或超过1MiB");var in=CityEconomy.JSON.fromJson(Files.readString(path),Template.class);var c=create(s,ctx.getSource().getEntity()==null?new UUID(0,0):ctx.getSource().getEntity().getUUID(),in);ctx.getSource().sendSuccess(()->Component.literal("纯数据聚落已登记，尚未启动或投影："+c.id),true);return 1;}catch(java.io.IOException ex){throw new IllegalArgumentException("读取模板失败",ex);}
 }))));}
}
