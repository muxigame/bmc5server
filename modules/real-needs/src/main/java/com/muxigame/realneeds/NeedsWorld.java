package com.muxigame.realneeds;

import java.util.*;
import static com.muxigame.realneeds.PhysiologyRule.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.tags.*;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Thin world/event adapter: no dependency on CustomNPCs classes and no combat replacement. */
public final class NeedsWorld {
    private record Broken(ServerPlayer player,ServerLevel level,BlockPos pos,BlockState before){}
    private static final Set<Broken> BROKEN=new HashSet<>();
    private static final TagKey<Biome> POLLUTED=TagKey.create(Registries.BIOME,id("polluted"));
    private static final TagKey<Biome> COLD=TagKey.create(Registries.BIOME,id("cold"));
    private static final TagKey<Biome> DAMP=TagKey.create(Registries.BIOME,id("damp"));
    private static final TagKey<Block> FIRE=TagKey.create(Registries.BLOCK,id("heat_sources"));
    private static final TagKey<Block> POLLUTION=TagKey.create(Registries.BLOCK,id("pollution_sources"));
    private static final TagKey<EntityType<?>> NPCS=TagKey.create(Registries.ENTITY_TYPE,id("npcs"));
    private static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath(RealNeeds.ID,name);}
    public static void register(){
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,NeedsWorld::hurt);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,NeedsWorld::death);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,NeedsWorld::breaking);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,NeedsWorld::attack);
    }
    private static boolean enabled(ServerPlayer p){return RealNeeds.participates(p)&&NeedsRules.get(p.server).enabled;}
    private static void attack(AttackEntityEvent e){if(e.getEntity() instanceof ServerPlayer p&&enabled(p)&&e.getTarget() instanceof LivingEntity){var s=NeedsState.of(p);NeedsPhysiology.combat(s,NeedsRules.get(p.server));s.store(p);}}
    private static void breaking(BlockEvent.BreakEvent e){if(e.getPlayer() instanceof ServerPlayer p&&enabled(p)&&e.getLevel() instanceof ServerLevel level&&!e.getState().isAir())BROKEN.add(new Broken(p,level,e.getPos().immutable(),e.getState()));}
    public static void finishBreaks(){
        for(var b:BROKEN){var p=b.player;if(enabled(p)&&p.isAlive()&&p.level()==b.level&&b.level.hasChunkAt(b.pos)&&!b.level.getBlockState(b.pos).equals(b.before)){
            var s=NeedsState.of(p);NeedsPhysiology.block(s,NeedsRules.get(p.server));s.store(p);
        }}BROKEN.clear();
    }
    public static void stop(){BROKEN.clear();}
    public static boolean npc(LivingEntity target){
        if(target.getType().is(NPCS))return true;
        for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())if(c.getName().equals("noppes.npcs.entity.EntityNPCInterface"))return true;
        return false;
    }
    private static void death(LivingDeathEvent e){
        var victim=e.getEntity();
        if(victim instanceof ServerPlayer p&&enabled(p)){var pending=NeedsIllness.pending(p);var s=pending==null?NeedsState.of(p):pending;NeedsPhysiology.death(s,NeedsRules.get(p.server));s.store(p);RealNeeds.sync(p,false);}
        var killer=e.getSource().getEntity() instanceof ServerPlayer p?p:victim.getKillCredit();
        if(killer instanceof ServerPlayer p&&enabled(p)&&victim!=p){
            var s=NeedsState.of(p);boolean allied=victim.isAlliedTo(p)||victim instanceof TamableAnimal tame&&tame.isOwnedBy(p);
            boolean hostile=!allied&&(victim instanceof Enemy||victim instanceof Mob mob&&mob.getTarget()==p);
            NeedsPhysiology.kill(s,NeedsRules.get(p.server),npc(victim),hostile,allied);s.store(p);RealNeeds.sync(p,false);
        }
    }
    private static void hurt(LivingDamageEvent.Post e){
        if(!(e.getEntity() instanceof ServerPlayer p)||!enabled(p)||e.getNewDamage()<=0)return;
        if(NeedsIllness.pending(p)!=null)return;
        var source=e.getSource();var s=NeedsState.of(p);var r=NeedsRules.get(p.server);
        boolean poison=p.hasEffect(MobEffects.POISON)&&source.is(DamageTypes.MAGIC)&&source.getEntity()==null;
        var injury=NeedsPhysiology.Injury.TORSO;
        if(source.is(DamageTypes.FALL))injury=NeedsPhysiology.Injury.FALL;
        else if(source.is(DamageTypeTags.IS_FIRE)||source.is(DamageTypeTags.IS_EXPLOSION)||source.is(DamageTypes.FREEZE))injury=NeedsPhysiology.Injury.AREA;
        else if(source.is(DamageTypeTags.IS_PROJECTILE)&&source.getDirectEntity()!=null){
            var hit=source.getDirectEntity().position().subtract(p.position());double height=hit.y/Math.max(.1,p.getBbHeight());
            double right=hit.x*Math.cos(Math.toRadians(p.yBodyRot))+hit.z*Math.sin(Math.toRadians(p.yBodyRot));
            injury=height>=.8?NeedsPhysiology.Injury.HEAD:height<.45?(right>=0?NeedsPhysiology.Injury.RIGHT_LEG:NeedsPhysiology.Injury.LEFT_LEG):Math.abs(right)>.25?(right>=0?NeedsPhysiology.Injury.RIGHT_ARM:NeedsPhysiology.Injury.LEFT_ARM):NeedsPhysiology.Injury.TORSO;
        }else if(source.getEntity() instanceof LivingEntity){int roll=p.getRandom().nextInt(100);injury=roll<10?NeedsPhysiology.Injury.HEAD:roll<20?NeedsPhysiology.Injury.LEFT_ARM:roll<30?NeedsPhysiology.Injury.RIGHT_ARM:roll<43?NeedsPhysiology.Injury.LEFT_LEG:roll<55?NeedsPhysiology.Injury.RIGHT_LEG:NeedsPhysiology.Injury.TORSO;}
        NeedsPhysiology.damage(s,r,e.getNewDamage(),injury,poison,source.getEntity() instanceof LivingEntity);
        trauma(s,r,source,injury,e.getNewDamage());
        String detail=source.typeHolder().unwrapKey().map(k->k.location().toString()).orElse("unknown");
        check(p,s,DiseaseCheckEvent.Exposure.DAMAGE,detail);s.store(p);RealNeeds.sync(p,false);
    }
    public static void check(ServerPlayer p,NeedsState s,DiseaseCheckEvent.Exposure exposure,String detail){
        double chance=NeedsPhysiology.diseaseChance(s,NeedsRules.get(p.server)),roll=p.getRandom().nextDouble()*100;
        var event=new DiseaseCheckEvent(p,s,exposure,detail,chance,roll);s.diseaseCheck(exposure.name().toLowerCase(Locale.ROOT),detail,chance,event.passed());
        // Save before dispatch so a future disease add-on may safely read the current player state.
        if(event.passed())NeedsIllness.infect(s,NeedsRules.get(p.server),exposure,detail);
        s.store(p);NeoForge.EVENT_BUS.post(event);s.store(p);
    }
    private static void trauma(NeedsState s,NeedsRules r,net.minecraft.world.damagesource.DamageSource source,NeedsPhysiology.Injury injury,float amount){
        if(!NeedsIllness.enabled(r))return;
        if(source.is(DamageTypes.FREEZE)){var type=AilmentType.HYPOTHERMIA;double old=s.ailments().severity(type,AilmentState.Site.WHOLE);s.ailments().set(type,AilmentState.Site.WHOLE,Math.min(100,old==0?type.rule(r,"initial"):old+type.rule(r,"gain")));return;}
        boolean fire=source.is(DamageTypeTags.IS_FIRE),fall=source.is(DamageTypes.FALL),blast=source.is(DamageTypeTags.IS_EXPLOSION);
        boolean physical=fire||fall||blast||source.is(DamageTypeTags.IS_PROJECTILE)||source.is(DamageTypes.CACTUS)||source.is(DamageTypes.SWEET_BERRY_BUSH)||source.getEntity() instanceof LivingEntity&&!source.is(DamageTypes.MAGIC)&&!source.is(DamageTypes.INDIRECT_MAGIC)&&!source.is(DamageTypes.WITHER);
        if(!physical)return;
        var attacker=source.getEntity() instanceof LivingEntity entity?entity:null;var weapon=attacker==null?null:attacker.getMainHandItem().getItem();
        boolean sharp=source.is(DamageTypeTags.IS_PROJECTILE)||weapon instanceof net.minecraft.world.item.SwordItem||weapon instanceof net.minecraft.world.item.AxeItem||weapon instanceof net.minecraft.world.item.TridentItem;
        boolean scratch=source.is(DamageTypes.CACTUS)||source.is(DamageTypes.SWEET_BERRY_BUSH);
        var sites=injury==NeedsPhysiology.Injury.FALL?List.of(AilmentState.Site.LEFT_LEG,AilmentState.Site.RIGHT_LEG):injury==NeedsPhysiology.Injury.AREA?Arrays.stream(AilmentState.Site.values()).filter(site->site!=AilmentState.Site.WHOLE).toList():List.of(AilmentState.Site.valueOf(injury.name()));
        for(var site:sites){double share=amount/sites.size();
            var type=fire?AilmentType.BURN:scratch?AilmentType.SCRATCH:sharp?(amount>=r.health("health.laceration_hit")?AilmentType.LACERATION:AilmentType.SCRATCH):AilmentType.BRUISE;
            NeedsIllness.trauma(s,r,type,site,share);
            if(!fire&&!scratch&&!sharp&&amount>=r.health(fall?"health.fall_fracture_hit":"health.fracture_hit"))NeedsIllness.trauma(s,r,AilmentType.FRACTURE,site,share);
        }
    }
    public static void second(ServerPlayer p,NeedsState s,NeedsRules r){
        var level=p.serverLevel();var biome=level.getBiome(p.blockPosition());boolean cold=biome.is(COLD)||biome.value().getBaseTemperature()<r.value(COLD_BIOME_LIMIT);
        boolean damp=biome.is(DAMP)||p.isInWaterOrRain();boolean polluted=biome.is(POLLUTED),fire=false;
        var center=p.position().add(0,p.getBbHeight()/2,0);var base=p.blockPosition();
        int radius=(int)Math.ceil(Math.max(r.value(FIRE_RADIUS),r.value(POLLUTION_RADIUS)));
        for(var pos:BlockPos.betweenClosed(base.offset(-radius,-radius,-radius),base.offset(radius,radius,radius)))if(level.hasChunkAt(pos)){
            double distance=pos.distToCenterSqr(center.x,center.y,center.z);
            var block=level.getBlockState(pos);if(distance<=Math.pow(r.value(FIRE_RADIUS),2)&&block.is(FIRE)&&(!block.hasProperty(BlockStateProperties.LIT)||block.getValue(BlockStateProperties.LIT)))fire=true;
            if(distance<=Math.pow(r.value(POLLUTION_RADIUS),2)&&block.is(POLLUTION))polluted=true;
        }
        boolean fighting=!level.getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(r.value(COMBAT_RADIUS)),mob->mob.isAlive()&&!mob.isNoAi()&&mob.getTarget()==p).isEmpty();
        int food=p.getFoodData().getFoodLevel();boolean sick=p.hasEffect(MobEffects.POISON)||p.hasEffect(MobEffects.WITHER)||NeedsIllness.enabled(r)&&(s.ailments().maximum(AilmentType.BACTERIAL)>=r.health("health.severe")||s.ailments().maximum(AilmentType.VIRAL)>=r.health("health.severe"));
        double target=r.value(TEMPERATURE_BASE)+(cold?r.value(COLD_CORRECTION):biome.value().getBaseTemperature()>=r.value(HOT_BIOME_LIMIT)?r.value(HOT_CORRECTION):0)+(p.isInWater()?r.value(WATER_CORRECTION):damp?r.value(DAMP_CORRECTION):0)+(food==0?r.value(EMPTY_FOOD_CORRECTION):food<=r.value(LOW_FOOD_LIMIT)?r.value(LOW_FOOD_CORRECTION):0)+(s.get(Need.WATER)<=r.value(LOW_WATER_LIMIT)?r.value(LOW_WATER_CORRECTION):0);
        target+=NeedsIllness.fever(s,r);
        var conditions=new NeedsPhysiology.Conditions(fighting,p.isSleeping(),food,sick,polluted,cold,damp,fire,target);
        if(fighting)NeedsPhysiology.combat(s,r);
        var checks=NeedsPhysiology.second(s,r,conditions);
        for(var exposure:checks)check(p,s,exposure,biome.unwrapKey().map(k->k.location().toString()).orElse("unknown"));
    }
    public static void consumed(ServerPlayer p,NeedsState s,ItemStack stack,NeedsRules r){
        if(stack.getFoodProperties(p)==null)return;
        String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        boolean vomit=NeedsPhysiology.food(s,r,id,stack.is(NeedsFood.tag("high_fat")));
        if(vomit){
            var hunger=p.getFoodData();hunger.setSaturation(0);hunger.setFoodLevel(Math.max(0,hunger.getFoodLevel()-(int)Math.round(r.value(PhysiologyRule.VOMIT_FOOD))));
            p.addEffect(new MobEffectInstance(MobEffects.CONFUSION,r.seconds(VOMIT_SECONDS)*20,0));
            var pos=p.getEyePosition().add(p.getLookAngle().scale(.3));p.serverLevel().sendParticles(ParticleTypes.ITEM_SLIME,pos.x,pos.y-.2,pos.z,18,.15,.12,.15,.03);
            p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.PLAYER_BURP,SoundSource.PLAYERS,.7f,.5f);
            p.displayClientMessage(net.minecraft.network.chat.Component.literal("已经吃腻了……一阵反胃！"),true);
        }
        if(stack.is(NeedsFood.tag("unsafe_food")))check(p,s,DiseaseCheckEvent.Exposure.UNSAFE_FOOD,(stack.is(NeedsFood.tag("parasite_risk"))?"parasite:":"")+id);
    }
}
