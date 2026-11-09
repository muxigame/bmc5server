package com.muxigame.city;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Normal authoring entry. Registers existing cities; never calls the sample-world bootstrap. */
public final class CityTownSetup {
 static final class Form {long revision,partnerRevision;boolean urban;String partner="";}
 static void access(ServerPlayer p,CityData.City city,long revision){CityService.checkEditor(p,city,revision);}
 static void apply(ServerPlayer p,CityNetwork.Request request){
  var cities=CityData.get(p.server);var city=cities.require(UUID.fromString(request.city()));access(p,city,request.revision());
  var trade=CityTrade.get(p.server);var form=CityEconomy.JSON.fromJson(request.value(),Form.class);
  CityEconomy.require(form!=null&&form.partner!=null&&form.partner.length()<=36,"登记内容无效");
  CityEconomy.require(form.revision==trade.state.registrationRevision,"城村登记已变化，请刷新后重试");
  CityEconomy.require(!trade.state.run.running&&!trade.state.modelRun.running,"自动观察运行期间不能改变城村登记");
  var existing=trade.state.towns.get(city.id);boolean register=request.op().equals("townregister");
  CityEconomy.require(register||request.op().equals("townlink"),"未知城村登记操作");
  if(register){CityEconomy.require(existing==null,"本聚落已登记；不重复初始化或改变类型");CityEconomy.require(trade.state.towns.size()<128,"经济聚落已达128个上限");}
  else {CityEconomy.require(existing!=null,"请先登记本城类型");CityEconomy.require(existing.urban==form.urban,"不能借对接更改已有城村类型");CityEconomy.require(!form.partner.isEmpty(),"请选择已登记的对接地");}
  CityTrade.Town peer=null;
  if(!form.partner.isEmpty()){
   UUID peerId=UUID.fromString(form.partner);CityEconomy.require(!peerId.equals(city.id),"不能与自身对接");
   var peerCity=cities.require(peerId);access(p,peerCity,form.partnerRevision);peer=trade.state.towns.get(peerId);
   CityEconomy.require(peer!=null&&peer.urban!=form.urban,"对接地须已登记，且必须是一城一村");
   CityEconomy.require(peerCity.dimension.equals(city.dimension),"对接地必须在同一维度");
   CityEconomy.require((existing==null||existing.partner==null||existing.partner.equals(peerId))&&(peer.partner==null||peer.partner.equals(city.id)),"已有其他对接地；不可覆盖已有贸易路线");
   for(var town:trade.state.towns.values()){
    CityEconomy.require(!Objects.equals(town.partner,city.id)||town.city.equals(peerId),"本城已被其他贸易路线使用");
    CityEconomy.require(!Objects.equals(town.partner,peerId)||town.city.equals(city.id),"对接地已被其他贸易路线使用");
   }
  }
  // All permission, version and link validation completes before any persistent write.
  if(register){existing=new CityTrade.Town();existing.city=city.id;existing.urban=form.urban;existing.model=true;existing.standardMultiplier=form.urban?trade.state.config.cityMultiplier:1;trade.state.towns.put(city.id,existing);}
  if(peer!=null){existing.partner=peer.city;peer.partner=city.id;}
  trade.state.registrationRevision++;trade.setDirty();
 }
 static CompoundTag view(ServerPlayer p,CityData.City city,String message){
  var trade=CityTrade.get(p.server);var out=new CompoundTag();out.putBoolean("TownSetup",true);out.putString("City",city.id.toString());out.putString("Name",city.name);out.putLong("CityRevision",city.revision);out.putLong("RegistrationRevision",trade.state.registrationRevision);out.putString("Message",message);
  var current=trade.state.towns.get(city.id);out.putBoolean("Registered",current!=null);out.putBoolean("Urban",current!=null&&current.urban);out.putString("Partner",current==null||current.partner==null?"":current.partner.toString());
  var list=new ListTag();for(var c:CityData.get(p.server).cities())if(!c.id.equals(city.id)&&c.dimension.equals(city.dimension)&&(p.hasPermissions(2)||p.getUUID().equals(c.owner))){var town=trade.state.towns.get(c.id);if(town==null)continue;var row=new CompoundTag();row.putString("Id",c.id.toString());row.putString("Name",c.name);row.putLong("Revision",c.revision);row.putBoolean("Urban",town.urban);row.putBoolean("Available",town.partner==null||city.id.equals(town.partner));list.add(row);}out.put("Peers",list);return out;
 }
 static void request(ServerPlayer p,CityNetwork.Request request){
  try {var city=CityData.get(p.server).require(UUID.fromString(request.city()));access(p,city,request.op().equals("townview")?city.revision:request.revision());String message="登记不生成居民、钱粮或地形，也不会启动模拟。";
   if(!request.op().equals("townview")){apply(p,request);message=request.op().equals("townregister")?"已登记经济类型；请配置真实岗位、住房、粮箱与经营资金。":"已对接一城一村；原资金与库存保持。";}
   PacketDistributor.sendToPlayer(p,new CityNetwork.Snapshot(view(p,city,message)));
  }catch(RuntimeException ex){var failure=new CompoundTag();failure.putBoolean("TownSetup",true);failure.putBoolean("Failed",true);failure.putString("Message","未执行："+(ex.getMessage()==null?"登记失败":ex.getMessage()));PacketDistributor.sendToPlayer(p,new CityNetwork.Snapshot(failure));}
 }
}
