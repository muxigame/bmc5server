package com.muxigame.city;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.entity.living.*;
/** Loaded actors display the persisted rural state; healing never overrides hunger. */
public final class RuralProjection {
 static final ResourceLocation SLOW=ResourceLocation.fromNamespaceAndPath(CityMod.ID,"rural_starvation");
 static void tick(MinecraftServer server){if(server.getTickCount()%20!=0)return;var d=RuralSettlement.get(server);for(var p:d.state.people.values()){var f=d.state.families.get(p.family);if(f==null)continue;var home=CityData.get(server).cities.get(f.city);if(home==null||!d.state.enabled.contains(f.city)&&!home.works.finalized)continue;var c=CityData.get(server).cities.get(f.city);if(c==null)continue;var l=CityEconomy.level(server,c);if(!(l.getEntity(p.id) instanceof LivingEntity entity))continue;try{var stats=entity.getClass().getField("stats").get(entity);stats.getClass().getField("spawnCycle").setInt(stats,3);}catch(ReflectiveOperationException ignored){}var speed=entity.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null){if(p.hunger()>=2&&!speed.hasModifier(SLOW))speed.addTransientModifier(new AttributeModifier(SLOW,-.25,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));else if(p.hunger()<2)speed.removeModifier(SLOW);}if(p.dead&&entity.isAlive()){entity.hurt(l.damageSources().starve(),Float.MAX_VALUE);}else if(p.hunger()>=2&&entity.getHealth()>entity.getMaxHealth()/2)entity.setHealth(entity.getMaxHealth()/2);}}
 static void heal(LivingHealEvent event){if(event.getEntity().level().isClientSide)return;var d=RuralSettlement.get(event.getEntity().getServer());var p=d.state.people.get(event.getEntity().getUUID());if(p!=null&&d.state.enabled.contains(d.state.families.get(p.family).city)&&p.hunger()>0)event.setCanceled(true);}
 static void death(LivingDeathEvent event){if(event.getEntity().level().isClientSide)return;var d=RuralSettlement.get(event.getEntity().getServer());var p=d.state.people.get(event.getEntity().getUUID());if(p!=null){p.dead=true;d.setDirty();}}
}
