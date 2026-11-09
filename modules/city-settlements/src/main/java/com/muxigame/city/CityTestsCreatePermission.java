package com.muxigame.city;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.*;

/** Compiled regression fixtures; executing them requires an explicitly authorized GameTest server. */
@GameTestHolder(CityMod.ID) @PrefixGameTestTemplate(false)
public final class CityTestsCreatePermission {
 static final String PREFIX="NP1030-auth-";
 static final class Player extends ServerPlayer {
  boolean creative,operator;
  Player(GameTestHelper h){super(h.getLevel().getServer(),h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"create-auth"),net.minecraft.server.level.ClientInformation.createDefault());}
  @Override public boolean hasPermissions(int level){return operator;}
  @Override public boolean isCreative(){return creative;}
 }
 static Map<UUID,Object> sessions()throws Exception{var field=CityMod.class.getDeclaredField("SESSIONS");field.setAccessible(true);return (Map<UUID,Object>)field.get(null);}
 static void field(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
 static Object selection(Player p,GameTestHelper h)throws Exception{var method=CityMod.class.getDeclaredMethod("session",ServerPlayer.class);method.setAccessible(true);var value=method.invoke(null,p);var min=h.absolutePos(new BlockPos(1,1,1));field(value,"a",min);field(value,"b",min.offset(1,1,1));field(value,"bottom",min.getY());field(value,"top",min.getY()+1);return value;}
 static CityData.City create(Player p)throws Exception{var method=CityMod.class.getDeclaredMethod("create",ServerPlayer.class,String.class);method.setAccessible(true);try{return (CityData.City)method.invoke(null,p,PREFIX+p.getUUID().toString().substring(0,8));}catch(InvocationTargetException e){if(e.getCause() instanceof Exception error)throw error;throw e;}}
 static String cityState(GameTestHelper h){return CityData.get(h.getLevel().getServer()).save(new CompoundTag(),h.getLevel().registryAccess()).toString();}
 static void cleanup(Player p)throws Exception{var data=CityData.get(p.server);for(var c:new ArrayList<>(data.cities()))if(c.owner.equals(p.getUUID())&&c.name.startsWith(PREFIX)){CityService.invalidate(c);data.cities.remove(c.id);}sessions().remove(p.getUUID());}
 @GameTest(template="empty") public static void deniedToolHolderCannotCreateSessionOrCity(GameTestHelper h)throws Exception{var p=new Player(h);p.getInventory().setItem(0,new ItemStack(CityMod.SURVEYOR.get()));String before=cityState(h),personal=p.getPersistentData().toString();try{boolean denied=false;try{create(p);}catch(IllegalArgumentException e){denied=e.getMessage().contains("权限");}h.assertTrue(denied&&!sessions().containsKey(p.getUUID())&&before.equals(cityState(h))&&personal.equals(p.getPersistentData().toString()),"Permission rejects before selection/session creation, city commit, active-city persistent tag or scan");}finally{cleanup(p);}h.succeed();}
 @GameTest(template="empty") public static void revokedOpWithValidStaleSelectionCannotCommit(GameTestHelper h)throws Exception{var p=new Player(h);p.operator=true;p.getInventory().setItem(0,new ItemStack(CityMod.SURVEYOR.get()));var selected=selection(p,h);var a=selected.getClass().getDeclaredField("preview");a.setAccessible(true);String before=cityState(h),personal=p.getPersistentData().toString();p.operator=false;try{boolean denied=false;try{create(p);}catch(IllegalArgumentException e){denied=e.getMessage().contains("权限");}h.assertTrue(denied&&before.equals(cityState(h))&&personal.equals(p.getPersistentData().toString())&&a.get(selected)==null,"Revoked OP holding the tool with valid corners cannot commit or even mutate selection preview");}finally{cleanup(p);}h.succeed();}
 @GameTest(template="empty") public static void legitimateCreativeAndSurvivalOpKeepCreateAccess(GameTestHelper h)throws Exception{for(boolean creative:new boolean[]{true,false}){var p=new Player(h);p.creative=creative;p.operator=!creative;p.getInventory().setItem(0,new ItemStack(CityMod.SURVEYOR.get()));selection(p,h);try{var city=create(p);h.assertTrue(city.owner.equals(p.getUUID())&&city.residents.isEmpty()&&p.getPersistentData().getString("CitySettlementsActive").equals(city.id.toString()),"Creative non-OP and survival OP both retain normal successful creation");}finally{cleanup(p);}}h.succeed();}
}
