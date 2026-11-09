package com.muxigame.city;

import java.util.*;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Read-only readiness checks and links to existing editors. No new economic settlement rules. */
public final class CityNpcSetup {
 static final Set<String> OPS=Set.of("careerstatus","careerhomes","careerstores","careerfunds","careerrun","careerhouse","careerreg","careerboxbind","careercycle","careermaint","careerback");
 static boolean handles(String op){return OPS.contains(op);}
 record Check(String key,String title,int level,String detail,String action){}
 record Inspection(List<Check> checks,UUID family,long familyCash,CityTrade.Business business){}
 static List<RuralSettlement.Box> stores(MinecraftServer s,UUID family){return RuralSettlement.get(s).state.boxes.values().stream().filter(b->b.present&&b.linked&&Objects.equals(b.stock.family,family)).toList();}
 static long familyCash(MinecraftServer s,RuralRules.Family f){if(f==null)return 0;var e=CityEconomy.get(s);return f.members.stream().mapToLong(id->e.balance(CityEconomy.person(id))).sum();}
 static CityTrade.Business business(MinecraftServer s,UUID npc){if(npc==null)return null;return CityTrade.get(s).state.businesses.values().stream().filter(b->npc.equals(b.worker)&&!b.kind.equals("supplier_helper")).findFirst().orElse(null);}
 static boolean assets(CityTrade.Business b){return b!=null&&(Arrays.stream(b.stock).anyMatch(v->v.count>0)||Arrays.stream(b.listed).anyMatch(v->v.count>0)||Arrays.stream(b.cargo).anyMatch(v->v.count>0)||Arrays.stream(b.meatCargo).anyMatch(v->v.count>0)||Arrays.stream(b.meatListed).anyMatch(v->v.count>0));}
 static Inspection inspect(MinecraftServer s,CityData.City c,UUID npc,CityNpcDesigner.Template template){
  var checks=new ArrayList<Check>();if(c==null){for(String[] v:new String[][]{{"home","住房"},{"store","家庭粮箱"},{"cash","经营与生活资金"},{"run","聚落运行"}})checks.add(new Check(v[0],v[1],0,"先在职业设计页选择聚落，再检查这些条件。","选择聚落"));return new Inspection(checks,null,0,null);}
  var d=RuralSettlement.get(s);var person=npc==null?null:d.state.people.get(npc);var family=person==null?null:d.state.families.get(person.family);boolean enrolled=family!=null&&c.id.equals(family.city);long cash=enrolled?familyCash(s,family):0;var business=business(s,npc);
  var home=!enrolled?null:c.housing.houses.entrySet().stream().filter(v->v.getValue().stage.equals("BUILT")&&CityWorks.health(v.getValue())>0&&v.getValue().beds>0&&v.getValue().residents.stream().anyMatch(family.members::contains)).findFirst().orElse(null);
  String homeName=home==null?"":Optional.ofNullable(c.buildings.get(home.getKey())).map(CityRegistry.Building::name).orElse("已登记住房");
  checks.add(new Check("home","住房",!enrolled?0:home==null?1:3,!enrolled?"新建后再安排住户；模板不会复制原家庭的房产。":home==null?"家庭尚无可用住房。选一栋有床位的已建成房屋，再登记住户。":"家庭已入住「"+homeName+"」；按现有家庭住房规则检查。","住房设置"));
  var boxes=enrolled?stores(s,family.id):List.<RuralSettlement.Box>of();int wheat=boxes.stream().mapToInt(b->b.stock.wheat).sum(),bread=boxes.stream().mapToInt(b->b.stock.bread).sum(),meat=boxes.stream().mapToInt(b->RuralMeat.total(List.of(b.stock))).sum();
  checks.add(new Check("store","家庭粮箱",!enrolled?0:boxes.isEmpty()?1:wheat+bread+meat==0?2:3,!enrolled?"先创建或选中NPC，再关联其工作点和专用NPC储藏箱。":boxes.isEmpty()?"家庭没有已关联的NPC粮箱。普通箱子不计入，请选择专用NPC储藏箱。":wheat+bread+meat==0?"已关联"+boxes.size()+"个粮箱，但其中没有食物；请实际存入粮食。":"已关联"+boxes.size()+"箱：小麦"+wheat+"、面包"+bread+"、肉食"+meat+"。","粮箱设置"));
  int moneyLevel;String moneyText;
  if(!enrolled){moneyLevel=0;moneyText="创建后查看真实账户；职业模板不附带银币，也不会自动补钱。";}
  else if(business!=null){long balance=CityEconomy.get(s).balance(business.account);moneyLevel=balance>0?3:assets(business)?2:1;moneyText="经营账户现金"+balance+"银币；家庭现金"+cash+"银币。"+(balance>0?"这里只检查余额，不保证足够每一笔采购。":assets(business)?"仍有存货或在途货物，零现金不等于没有资产。":"尚无可用经营现金，请查看现有账户设置。");}
  else if(Set.of(RuralRules.Role.PROCUREMENT,RuralRules.Role.BAKER,RuralRules.Role.SUPPLIER).contains(template.role)&&!CityTrade.get(s).state.businesses.values().stream().anyMatch(b->Objects.equals(b.worker,npc)&&b.kind.equals("supplier_helper"))){moneyLevel=1;moneyText="尚未登记对应经营账户；请先完成本城经济和岗位登记。家庭现金"+cash+"银币。";}
  else{moneyLevel=cash>0?3:2;moneyText="本岗位不要求单独经营账户；家庭可用现金"+cash+"银币。"+(cash==0?"零现金时请检查储粮和领薪情况；不会自动赠款。":"家庭其他成员的钱也已计入。");}
  checks.add(new Check("cash","经营与生活资金",moneyLevel,moneyText,"查看账户"));
  boolean enabled=d.state.enabled.contains(c.id);checks.add(new Check("run","聚落运行",enabled?c.works.enabled?3:2:1,"居民/主粮循环："+(enabled?"运行":"暂停或未启用")+"；城市维护："+(c.works.enabled?"开启":"暂停")+"。进入设置只查看，不会自动启动。","运行设置"));
  return new Inspection(checks,enrolled?family.id:null,cash,business);
 }
 static RuralSettlement.Site site(MinecraftServer s,CityData.City c,UUID npc){return RuralSettlement.get(s).state.sites.values().stream().filter(v->c.id.equals(v.city)&&Objects.equals(v.worker,npc)).findFirst().orElse(null);}
 static boolean canBind(MinecraftServer s,CityData.City c,UUID npc,RuralSettlement.Box box){var d=RuralSettlement.get(s);var person=d.state.people.get(npc);var site=site(s,c,npc);return person!=null&&!person.dead&&person.family!=null&&d.state.families.containsKey(person.family)&&c.id.equals(d.state.families.get(person.family).city)&&box.present&&site!=null&&c.id.equals(box.city)&&BlockPos.of(box.pos).distSqr(BlockPos.of(site.pos))<=1024&&(person.family.equals(box.stock.family)||box.stock.family==null&&box.stock.free()==10);}
 static void bind(ServerPlayer p,CityData.City c,UUID npc,long pos){CityService.checkEditor(p,c,c.revision);var d=RuralSettlement.get(p.server);var box=d.state.boxes.get(RuralSettlement.key(c.id,pos));CityEconomy.require(box!=null&&canBind(p.server,c,npc,box),"请选择本家庭粮箱，或完全空闲且未归他人的NPC储藏箱");var at=BlockPos.of(pos);CityEconomy.require(at.distSqr(p.blockPosition())<=4096&&p.serverLevel().hasChunkAt(at)&&p.serverLevel().getBlockEntity(at) instanceof RuralBlocks.Link,"请靠近粮箱后刷新，不会强制加载区块");var work=site(p.server,c,npc);RuralTools.selected.put(p.getUUID(),npc);RuralTools.selectedSite.put(p.getUUID(),BlockPos.of(work.pos));RuralTools.wandBlock(p,at,false);CityEconomy.require(Objects.equals(box.stock.family,d.state.people.get(npc).family)&&box.linked&&box.site==work.pos,"关联未完成，请检查粮箱与工作点");}
 static void snapshot(ServerPlayer p,CityNpcDesigner.Session session,CityData.City c,CityNpcDesigner.Template template,int page,String message){
  var t=new CompoundTag();t.putBoolean("NpcSetup",true);t.putString("Template",CityEconomy.JSON.toJson(template));t.putString("City",c==null?"":c.id.toString());t.putString("CityName",c==null?"未选择聚落":c.name);t.putString("Npc",session.npc()==null?"":session.npc().toString());t.putString("Name",template.name);t.putLong("Token",session.token());t.putInt("Page",page);t.putString("Message",message);t.putBoolean("CanEditMoney",p.hasPermissions(2));
  var result=inspect(p.server,c,session.npc(),template);var checks=new ListTag();for(var check:result.checks){var n=new CompoundTag();n.putString("Key",check.key);n.putString("Title",check.title);n.putInt("Level",check.level);n.putString("Detail",check.detail);n.putString("Action",check.action);checks.add(n);}t.put("Checks",checks);
  var houses=new ListTag();var boxes=new ListTag();var accounts=new ListTag();
  if(c!=null){for(var entry:c.housing.houses.entrySet()){var b=c.buildings.get(entry.getKey());if(b==null)continue;var house=entry.getValue();var n=new CompoundTag();n.putString("Id",b.id().toString());n.putString("Name",b.name());n.putString("Detail",house.stage.equals("BUILT")?"床位"+house.residents.size()+"/"+house.beds+"；房屋剩余方块"+CityWorks.health(house):"尚未建成，先完成房屋设置");houses.add(n);}
   for(var box:RuralSettlement.get(p.server).state.boxes.values())if(c.id.equals(box.city)){var n=new CompoundTag();n.putString("Pos",Long.toString(box.pos));n.putString("Name","NPC储藏箱 · "+BlockPos.of(box.pos).toShortString());boolean own=result.family!=null&&result.family.equals(box.stock.family);n.putString("Detail",!box.present?"箱子已损毁，等待修复":own?"属于本家庭；食物"+(box.stock.wheat+box.stock.bread+RuralMeat.total(List.of(box.stock))):box.stock.family!=null?"已归其他家庭，不在此转交":box.stock.free()<10?"未归属但箱中有物品，请先清空再关联":"空闲；可关联到本NPC工作点");n.putBoolean("CanBind",session.npc()!=null&&canBind(p.server,c,session.npc(),box)&&p.serverLevel().hasChunkAt(BlockPos.of(box.pos))&&BlockPos.of(box.pos).distSqr(p.blockPosition())<=4096);boxes.add(n);}
   var e=CityEconomy.get(p.server);var entries=new LinkedHashMap<String,String>();if(result.business!=null)entries.put(result.business.account,"本NPC经营账户");if(session.npc()!=null)entries.putIfAbsent(CityEconomy.person(session.npc()),"NPC个人账户");var f=result.family==null?null:RuralSettlement.get(p.server).state.families.get(result.family);if(f!=null)for(UUID id:f.members){var person=RuralSettlement.get(p.server).state.people.get(id);entries.putIfAbsent(CityEconomy.person(id),"家庭成员 · "+(person==null?"未登记":person.name));}entries.putIfAbsent(CityEconomy.treasury(c.id),"本城市政账户");for(var entry:entries.entrySet()){var n=new CompoundTag();n.putString("Id",entry.getKey());n.putString("Name",entry.getValue());n.putLong("Balance",e.balance(entry.getKey()));accounts.add(n);}
  }t.put("Houses",houses);t.put("Boxes",boxes);t.put("Accounts",accounts);PacketDistributor.sendToPlayer(p,new CityNetwork.Snapshot(t));
 }
 static void request(ServerPlayer p,CityNetwork.Request r){CityNpcDesigner.Session session=null;CityData.City c=null;boolean authorized=false;CityNpcDesigner.Template template=CityNpcDesigner.defaults(RuralRules.Role.FARMER);try{session=CityNpcDesigner.session(p,r.revision());var value=JsonParser.parseString(r.value()).getAsJsonObject();template=CityEconomy.JSON.fromJson(value.get("template"),CityNpcDesigner.Template.class);CityNpcDesigner.validate(template);String target=value.has("target")?value.get("target").getAsString():"";if(!r.city().isEmpty()){c=CityData.get(p.server).require(UUID.fromString(r.city()));CityService.checkEditor(p,c,c.revision);authorized=true;if(session.npc()!=null)CityEconomy.require(c.residents.contains(session.npc())&&CityNpcBridge.home(CityData.get(p.server),session.npc())==c,"NPC所属聚落已变化，请重新选择");}int page=Math.clamp(r.state(),0,4);String message="";
   switch(r.op()){
    case "careerback"->{CityNpcDesigner.snapshot(p,session,c,template,"");return;}
    case "careerhomes"->page=1;case "careerstores"->page=2;case "careerfunds"->page=3;case "careerrun"->page=4;
    case "careerhouse"->{CityEconomy.require(c!=null,"先选择聚落");UUID house=UUID.fromString(target);CityEconomy.require(c.buildings.containsKey(house)&&c.housing.houses.containsKey(house),"房屋已变化，请刷新");CityHousing.snapshot(p,c,house,1,"已定位所选NPC；点击住户条目完成入住，或修改房屋床位。配置后可返回职业检查。");return;}
    case "careerreg"->{CityEconomy.require(c!=null,"先选择聚落");CityMod.openCareerRegistry(p,c);return;}
    case "careerboxbind"->{CityEconomy.require(c!=null&&session.npc()!=null,"先创建或选中NPC");bind(p,c,session.npc(),Long.parseLong(target));page=2;message="已通过现有储存魔杖的绑定流程关联；箱内物品和银币未新增。";}
    case "careercycle"->{CityEconomy.require(c!=null&&session.npc()!=null&&RuralSettlement.get(p.server).state.people.containsKey(session.npc()),"先创建或选中已登记NPC");RuralTools.selected.put(p.getUUID(),session.npc());RuralTools.snapshot(p,session.npc(),"这里只打开原居民循环设置；请确认后自行启用。城市维护为独立开关。");return;}
    case "careermaint"->{CityEconomy.require(c!=null,"先选择聚落");CityWorksControl.snapshot(p,c,0,"这里只打开维护设置，不会切换任何开关。居民循环为独立设置。");return;}
    case "careerstatus"->{}default->throw new IllegalArgumentException("未知运行条件操作");
   }snapshot(p,session,c,template,page,message);
  }catch(RuntimeException ex){if(session!=null){try{CityNpcDesigner.validate(template);}catch(RuntimeException ignored){template=CityNpcDesigner.defaults(RuralRules.Role.FARMER);}snapshot(p,session,authorized?c:null,template,0,"未执行："+ex.getMessage());}else CityNpcDesigner.say(p,ex.getMessage());}
 }
}
