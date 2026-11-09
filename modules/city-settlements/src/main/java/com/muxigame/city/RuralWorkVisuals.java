package com.muxigame.city;

import java.util.*;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;

/** Loaded work actors observe the economy. No production, payments, drops, or economic RNG here. */
public final class RuralWorkVisuals {
 static final int INSPECTIONS_PER_TICK=2,PATHS_PER_TICK=1,PATH_RETRY_TICKS=200;
 static final String HOME_BACKUP="CityActorOriginalReturnsHome";
 private static RuralSettlement.State indexed;
 private static List<UUID> people=List.of();
 private static final Map<UUID,Motion> motions=new HashMap<>();
 private static final Map<UUID,RuralSettlement.Site> sites=new HashMap<>();
 private static final Map<UUID,BlockPos> homes=new HashMap<>();
 private static final Map<UUID,List<Long>> fields=new HashMap<>();
 private static int cursor;private static long nextIndex;
 static long inspections,paths,swings,visits,unloaded,blocked,busy;
 private static final class Motion {long nextPath,nextSwing;int field;BlockPos target,anchor;boolean ownsPath,working;}
 public static void clear(){indexed=null;people=List.of();motions.clear();sites.clear();homes.clear();fields.clear();cursor=0;nextIndex=0;inspections=paths=swings=visits=unloaded=blocked=busy=0;}
 /** Yield the native return goal only while this opt-in layer controls loaded movement. */
 static void nativeHome(Mob mob,boolean managed){
  if(!mob.getClass().getName().startsWith("noppes.npcs."))return;
  try{var ai=mob.getClass().getField("ais").get(mob);var tag=mob.getPersistentData();
   if(managed){if(!tag.contains(HOME_BACKUP))tag.putBoolean(HOME_BACKUP,(boolean)ai.getClass().getMethod("getReturnsHome").invoke(ai));ai.getClass().getMethod("setReturnsHome",boolean.class).invoke(ai,false);}
   else if(tag.contains(HOME_BACKUP)){ai.getClass().getMethod("setReturnsHome",boolean.class).invoke(ai,tag.getBoolean(HOME_BACKUP));tag.remove(HOME_BACKUP);}
  }catch(ReflectiveOperationException ignored){/* Other NPC implementations retain their own AI. */}
 }
 static void release(MinecraftServer s){for(var l:s.getAllLevels())for(var entity:l.getAllEntities())if(entity instanceof Mob mob&&mob.getPersistentData().contains(HOME_BACKUP))nativeHome(mob,false);}
 static void joined(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event){
  if(!(event.getLevel() instanceof ServerLevel l)||!(event.getEntity() instanceof Mob mob)||!mob.getPersistentData().contains(HOME_BACKUP))return;
  var d=RuralSettlement.get(l.getServer());var p=d.state.people.get(mob.getUUID());var f=p==null?null:d.state.families.get(p.family);
  if(p==null||p.dead||p.role==RuralRules.Role.HUNTER||f==null||!d.state.visualCities.contains(f.city)||!d.state.enabled.contains(f.city))nativeHome(mob,false);
 }
 static void enable(MinecraftServer s,UUID id,boolean on){
  CityData.get(s).require(id);var d=RuralSettlement.get(s);
  if(on){CityEconomy.require(d.state.visualCities.size()<128||d.state.visualCities.contains(id),"表现聚落超过128个");d.state.visualCities.add(id);}else{
   d.state.visualCities.remove(id);for(var p:d.state.people.values()){var f=d.state.families.get(p.family);if(f!=null&&id.equals(f.city)){var m=motions.remove(p.id);var c=CityData.get(s).cities.get(id);if(CityEconomy.level(s,c).getEntity(p.id) instanceof Mob mob){nativeHome(mob,false);if(m!=null&&m.ownsPath&&mob.getTarget()==null)mob.getNavigation().stop();}}}
  }nextIndex=0;d.setDirty();
 }
 private static void index(MinecraftServer s,long now){
  var d=RuralSettlement.get(s);if(indexed==d.state&&now<nextIndex)return;
  if(indexed!=d.state){clear();indexed=d.state;}nextIndex=now+200;
  people=d.state.people.values().stream().filter(p->{var f=d.state.families.get(p.family);return f!=null&&d.state.visualCities.contains(f.city);}).map(p->p.id).toList();
  motions.keySet().retainAll(people);sites.clear();homes.clear();fields.clear();
  for(var site:d.state.sites.values())if(site.worker!=null&&d.state.visualCities.contains(site.city))sites.putIfAbsent(site.worker,site);
  for(var c:CityData.get(s).cities())if(d.state.visualCities.contains(c.id)){
   for(var entry:c.buildings.entrySet()){var h=c.housing.houses.get(entry.getKey());if(h==null||h.beds<=0||!h.stage.equals("BUILT"))continue;var b=entry.getValue().bounds();var pos=b.min().offset(Math.min(1,b.sizeX()-1),1,Math.min(1,b.sizeZ()-1));for(var id:h.residents)homes.putIfAbsent(id,pos);}
   var plan=CityMaintenance.get(s).state.cities.get(c.id);if(plan!=null)fields.put(c.id,List.copyOf(plan.baseline.keySet()));
  }
 }
 static BlockPos walkable(ServerLevel l,BlockPos anchor){
  for(int dy:new int[]{0,-1,1,2})for(var direction:Direction.Plane.HORIZONTAL){var p=anchor.relative(direction).offset(0,dy,0);if(!l.hasChunkAt(p)||!l.hasChunkAt(p.below()))continue;
   var floor=l.getBlockState(p.below());if(l.getBlockState(p).getCollisionShape(l,p).isEmpty()&&l.getBlockState(p.above()).getCollisionShape(l,p.above()).isEmpty()&&(floor.is(Blocks.FARMLAND)||floor.isFaceSturdy(l,p.below(),Direction.UP))&&l.getFluidState(p).isEmpty())return p;
  }return null;
 }
 static void cropVisual(ServerLevel l,BlockPos anchor){var b=l.getBlockState(anchor);if(b.is(Blocks.WHEAT)&&b.getBlock() instanceof CropBlock crop&&crop.isMaxAge(b))l.setBlock(anchor,crop.getStateForAge(0),2);}
 static void trample(net.neoforged.neoforge.event.level.BlockEvent.FarmlandTrampleEvent event){if(!(event.getEntity() instanceof Mob mob)||!(event.getLevel() instanceof ServerLevel l))return;var d=RuralSettlement.get(l.getServer());var p=d.state.people.get(mob.getUUID());var f=p==null?null:d.state.families.get(p.family);if(f!=null&&d.state.visualCities.contains(f.city))event.setCanceled(true);}
 private static boolean inRange(MinecraftServer s,CityData.City c,RuralRules.Person p,BlockPos pos){
  if(c.includes(pos))return true;if(p.role!=RuralRules.Role.PROCUREMENT)return false;var t=CityTrade.get(s);var village=CityData.get(s).cities.get(CityRoute.village(t,c.id));if(village==null)return false;
  // Merchants may walk the registered inter-town corridor without changing town/family ownership.
  var a=c.bounds;var b=village.bounds;return pos.getX()>=Math.min(a.min().getX(),b.min().getX())-8&&pos.getX()<=Math.max(a.max().getX(),b.max().getX())+8&&pos.getZ()>=Math.min(a.min().getZ(),b.min().getZ())-8&&pos.getZ()<=Math.max(a.max().getZ(),b.max().getZ())+8&&pos.getY()>=Math.min(a.min().getY(),b.min().getY())-4&&pos.getY()<=Math.max(a.max().getY(),b.max().getY())+4;
 }
 private static BlockPos destination(MinecraftServer s,CityData.City c,RuralRules.Person p,Motion m,boolean working){
  if(!working)return homes.get(p.id);
  var site=sites.get(p.id);if(site==null)return homes.get(p.id);var at=BlockPos.of(site.pos);
  if(p.role==RuralRules.Role.FARMER){if(m.anchor!=null)return m.anchor;var plots=fields.getOrDefault(c.id,List.of());if(!plots.isEmpty()){
   // At most20 crop candidates per visit; never a full plot scan per actor/tick.
   for(int n=0;n<Math.min(20,plots.size());n++){var candidate=BlockPos.of(plots.get(Math.floorMod(m.field++,plots.size())));if(candidate.distSqr(at)<=32*32){m.anchor=candidate;return candidate;}}
  }}
  if(p.role==RuralRules.Role.PROCUREMENT){var t=CityTrade.get(s);var b=t.state.businesses.values().stream().filter(v->p.id.equals(v.worker)&&v.kind.equals("procurement")).findFirst().orElse(null);if(b!=null&&b.phase==1){UUID village=CityRoute.village(t,c.id);var head=t.head(village);if(head!=null)return BlockPos.of(head.site);}}
  if(p.role==RuralRules.Role.ARTISAN)for(var h:c.housing.houses.values())if(!h.damaged.isEmpty())return BlockPos.of(h.damaged.iterator().next());
  return at;
 }
 public static void tick(MinecraftServer s){
  var d=RuralSettlement.get(s);if(d.state.visualCities.isEmpty()){if(indexed!=null){release(s);clear();}return;}long now=s.overworld().getGameTime();index(s,now);int count=people.size(),issued=0;
  for(int n=0;n<Math.min(INSPECTIONS_PER_TICK,count);n++){
   inspections++;var id=people.get(Math.floorMod(cursor++,count));var p=d.state.people.get(id);if(p==null)continue;var family=d.state.families.get(p.family);if(family==null)continue;
   var c=CityData.get(s).cities.get(family.city);if(c==null)continue;var l=CityEconomy.level(s,c);var entity=l.getEntity(id);if(!(entity instanceof Mob mob)||!mob.isAlive()){unloaded++;continue;}
   if(p.dead||p.role==RuralRules.Role.HUNTER||!d.state.visualCities.contains(family.city)||!d.state.enabled.contains(family.city)||mob.isNoAi()||mob.getTarget()!=null||mob.isPassenger()||!inRange(s,c,p,mob.blockPosition())){nativeHome(mob,false);var old=motions.remove(id);if(old!=null&&old.ownsPath&&mob.getTarget()==null)mob.getNavigation().stop();busy++;continue;}
   nativeHome(mob,true);
   // Reading the last settled status avoids changing night/overtime decisions in WorkHours.active.
   boolean working=p.workStatus.equals("工作中")&&RuralSuccession.capable(p);var m=motions.computeIfAbsent(id,k->new Motion());if(working!=m.working){m.working=working;m.anchor=null;}var anchor=destination(s,c,p,m,working);if(anchor==null)continue;
   if(!l.hasChunkAt(anchor)){unloaded++;continue;}var target=walkable(l,anchor);if(target==null){if(p.role==RuralRules.Role.FARMER)m.anchor=null;blocked++;continue;}
   if(mob.distanceToSqr(target.getX()+.5,target.getY(),target.getZ()+.5)<2.25){
    if(m.ownsPath){mob.getNavigation().stop();m.ownsPath=false;}mob.getLookControl().setLookAt(anchor.getX()+.5,anchor.getY()+.5,anchor.getZ()+.5);
    if(working&&now>=m.nextSwing){mob.swing(InteractionHand.MAIN_HAND);m.nextSwing=now+40;swings++;visits++;if(p.role==RuralRules.Role.FARMER){cropVisual(l,anchor);m.anchor=null;}}continue;
   }
   if(now<m.nextPath||issued>=PATHS_PER_TICK)continue;
   if(!mob.getNavigation().isDone()&&!m.ownsPath){busy++;continue;}
   if(m.ownsPath&&!Objects.equals(m.target,target))mob.getNavigation().stop();m.target=target;m.nextPath=now+PATH_RETRY_TICKS+Math.floorMod(id.hashCode(),80);m.ownsPath=true;issued++;paths++;
   mob.getNavigation().moveTo(target.getX()+.5,target.getY(),target.getZ()+.5,.65);
  }
 }
 static Map<String,Object> status(MinecraftServer s,UUID city){var d=RuralSettlement.get(s);var c=CityData.get(s).require(city);var l=CityEconomy.level(s,c);return Map.of("city",city,"enabled",d.state.visualCities.contains(city),"registered",c.residents.size(),"loaded",c.residents.stream().filter(id->l.getEntity(id)!=null).count(),"inspections",inspections,"paths",paths,"swings",swings,"unloadedSkips",unloaded,"blockedSkips",blocked,"busySkips",busy);}
 static void register(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher){
  dispatcher.register(Commands.literal("cityactors").requires(v->v.hasPermission(2))
   .then(Commands.literal("skins").then(Commands.argument("city",StringArgumentType.word()).executes(ctx->{var id=UUID.fromString(StringArgumentType.getString(ctx,"city"));int n=CityActorSkins.applyLoaded(ctx.getSource().getServer(),id);ctx.getSource().sendSuccess(()->Component.literal("已为"+n+"名已加载NPC应用职业皮肤；人物、家庭和资产保持"),false);return n;})))
   .then(Commands.literal("enable").then(Commands.argument("city",StringArgumentType.word()).then(Commands.argument("on",BoolArgumentType.bool()).executes(ctx->{var id=UUID.fromString(StringArgumentType.getString(ctx,"city"));boolean on=BoolArgumentType.getBool(ctx,"on");enable(ctx.getSource().getServer(),id,on);ctx.getSource().sendSuccess(()->Component.literal(on?"已启用附近NPC工作表现，经济继续按数据结算":"已关闭工作表现，经济继续运行"),false);return 1;}))))
   .then(Commands.literal("status").then(Commands.argument("city",StringArgumentType.word()).executes(ctx->{var id=UUID.fromString(StringArgumentType.getString(ctx,"city"));var value=status(ctx.getSource().getServer(),id);ctx.getSource().sendSuccess(()->Component.literal(CityEconomy.JSON.toJson(value)),false);return 1;}))));
 }
}
