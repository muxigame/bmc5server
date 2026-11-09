package com.muxigame.city;
import java.util.*;
import com.mojang.brigadier.arguments.*;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
public final class RuralControl {
 static void register(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("rural").requires(s->s.hasPermission(2))
  .then(Commands.literal("speed").then(Commands.argument("multiplier",IntegerArgumentType.integer(1,SimulationClock.MAX_SPEED)).executes(c->{var d=RuralSettlement.get(c.getSource().getServer());d.state.speed=IntegerArgumentType.getInteger(c,"multiplier");d.setDirty();c.getSource().sendSuccess(()->Component.literal("农村模拟速度 "+d.state.speed+"倍，生产/进食/饥饿/作息同步"),false);return 1;})))
  .then(Commands.literal("buyer").then(Commands.argument("enabled",BoolArgumentType.bool()).executes(c->{var p=c.getSource().getPlayerOrException();var city=RuralTools.city(p.serverLevel(),p.blockPosition());CityEconomy.require(city!=null,"站在村内操作");var d=RuralSettlement.get(p.server);if(BoolArgumentType.getBool(c,"enabled"))d.state.externalBuyer.add(city.id);else d.state.externalBuyer.remove(city.id);d.setDirty();return 1;})))
  .then(Commands.literal("deathcheck").executes(c->{try{String result=RuralNativeCheck.run(c.getSource().getPlayerOrException());c.getSource().sendSuccess(()->Component.literal(result),false);return 1;}catch(Exception ex){c.getSource().sendFailure(Component.literal(ex.toString()));return 0;}}))
  .then(Commands.literal("settings").executes(c->{var p=c.getSource().getPlayerOrException();var d=RuralSettlement.get(p.server);UUID id=RuralTools.selected.get(p.getUUID());if(id==null){var city=RuralTools.city(p.serverLevel(),p.blockPosition());id=d.state.people.values().stream().filter(n->n.role==RuralRules.Role.MERCHANT&&city!=null&&d.state.families.get(n.family).city.equals(city.id)).findFirst().orElseThrow().id;RuralTools.selected.put(p.getUUID(),id);}snapshot(p,id,0,"");return 1;})));
 }
 static void request(ServerPlayer p,CityNetwork.Request r){try{UUID id=UUID.fromString(r.city());WorkHours.access(p,id);var d=RuralSettlement.get(p.server);var person=d.state.people.get(id);CityEconomy.require(person!=null,"请先登记农村居民");int good=r.x();CityEconomy.require(good>=0&&good<12,"未知货品");String message="";
  if(r.op().equals("marketsave")){CityEconomy.require(person.role==RuralRules.Role.MERCHANT&&r.revision()==person.marketRevision,"请选择头人并重新打开设置");var cfg=CityEconomy.JSON.fromJson(r.value(),RuralMarket.Policy.class);RuralMarket.validate(cfg);person.market.put(good,cfg);person.marketRevision++;message="库存预期和收购浮动已保存";}
  if(r.op().equals("marketreset")){person.market.put(good,RuralMarket.defaults(good));person.marketRevision++;message="已恢复此货品默认";}
  if(r.op().equals("simconfig")){CityEconomy.require(r.y()>=1&&r.y()<=SimulationClock.MAX_SPEED,"速度1—100倍");d.state.speed=r.y();UUID city=d.state.families.get(person.family).city;if(r.surface())d.state.externalBuyer.add(city);else d.state.externalBuyer.remove(city);message="模拟设置已保存；无限城商外部注资独立记录";}
  d.setDirty();snapshot(p,id,good,message);
 }catch(Exception ex){p.sendSystemMessage(Component.literal("[农村设置] "+ex.getMessage()));}}
 static void snapshot(ServerPlayer p,UUID id,int good,String message){var d=RuralSettlement.get(p.server);var person=d.state.people.get(id);var t=new CompoundTag();t.putBoolean("RuralSettings",true);t.putString("Npc",id.toString());t.putLong("Revision",person.marketRevision);t.putInt("Good",good);t.putString("Policy",CityEconomy.JSON.toJson(RuralMarket.policy(person,good)));t.putBoolean("Merchant",person.role==RuralRules.Role.MERCHANT);t.putInt("Speed",d.state.speed);t.putBoolean("Buyer",d.state.externalBuyer.contains(d.state.families.get(person.family).city));t.putInt("Stock",RuralMarket.count(d.stocks(person.family),good));t.putInt("Quote",RuralMarket.purchaseQuote(d,person,good,t.getInt("Stock"),RuralMarket.baseBuy(good)));t.putString("Message",message);PacketDistributor.sendToPlayer(p,new CityNetwork.Snapshot(t));}
}
