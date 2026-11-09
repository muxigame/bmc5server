package com.muxigame.city;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
/** Dedicated-server probe creates no persistent actor and never touches a resident. */
public final class RuralNativeCheck {
 static String run(ServerPlayer p)throws Exception{var npc=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("customnpcs:customnpc")).create(p.serverLevel());var mob=(Mob)npc;mob.setNoGravity(true);mob.setNoAi(true);mob.setPos(p.getX(),p.getY(),p.getZ());var stats=npc.getClass().getField("stats").get(npc);var cls=stats.getClass();CityEconomy.require(cls.getField("spawnCycle").getInt(stats)==3,"默认复活仍开启");CityEconomy.require(cls.getField("corpseCleanup").getBoolean(stats),"默认尸体清理未开启");var save=(net.minecraft.nbt.CompoundTag)cls.getMethod("save",net.minecraft.nbt.CompoundTag.class).invoke(stats,new net.minecraft.nbt.CompoundTag());CityEconomy.require(save.getInt("NpcDeathPolicyVersion")==1&&save.getInt("NpcCorpseSeconds")==300,"死亡设置未保存");
  mob.setHealth(0);Class<?> owner=npc.getClass();java.lang.reflect.Method method=null;while(owner!=null&&method==null){try{method=owner.getDeclaredMethod("tickDeath");}catch(NoSuchMethodException ignored){owner=owner.getSuperclass();}}CityEconomy.require(method!=null,"找不到尸体更新方法");method.setAccessible(true);method.invoke(npc);npc.getPersistentData().putLong("city_corpse_tick",p.serverLevel().getGameTime()-100000);method.invoke(npc);CityEconomy.require(!npc.isRemoved()&&mob.getHealth()<=0,"有玩家区块错误清尸或复活");
  mob.setPos(p.getX()+64,p.getY(),p.getZ());cls.getField("corpseCleanup").setBoolean(stats,false);method.invoke(npc);CityEconomy.require(!npc.isRemoved(),"关闭清理后仍清尸");cls.getField("corpseCleanup").setBoolean(stats,true);method.invoke(npc);CityEconomy.require(npc.isRemoved(),"无人区块过期尸体未清除");return "死亡原生检查通过：默认不复活、默认清尸、NBT保存、有玩家保留、关闭后保留、无人过期清除，共6项";
 }
}
