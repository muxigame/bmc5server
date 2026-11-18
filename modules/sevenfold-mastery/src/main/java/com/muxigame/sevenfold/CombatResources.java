package com.muxigame.sevenfold;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.bus.api.EventPriority;
import com.muxigame.zaluokeforging.api.*;

/** All resource changes and prices are authoritative here, never in the HUD. */
public final class CombatResources {
    public static final String CLOCK_KEY="SevenfoldResourceClock";
    private static final Map<UUID,Runtime> RUNTIMES=new HashMap<>();
    private static final class Runtime {
        final ResourceClock clock;
        DamageSource absorbed;
        int selectedSlot=-1;
        Runtime(ServerPlayer p){clock=ResourceClock.load(p.getPersistentData().getCompound(CLOCK_KEY),p.level().getGameTime());}
    }
    private static Runtime runtime(ServerPlayer p){return RUNTIMES.computeIfAbsent(p.getUUID(),id->new Runtime(p));}
    public static void checkpoint(ServerPlayer p){var r=RUNTIMES.get(p.getUUID());if(r!=null)p.getPersistentData().put(CLOCK_KEY,r.clock.save(p.level().getGameTime()));}
    public static void forget(ServerPlayer p){checkpoint(p);RUNTIMES.remove(p.getUUID());}
    public static void clear(){RUNTIMES.clear();}
    public static WarProfession profession(ServerPlayer p){return StarTreeState.of(p).profession();}
    public static boolean spend(ServerPlayer p,Mastery type,int amount){
        if(amount<=0||!p.isAlive()||p.isSpectator())return false;
        amount*=type==Mastery.FOCUS?1:CombatPools.of(p).scale();
        return spendExact(p,type,amount);
    }
    private static boolean spendExact(ServerPlayer p,Mastery type,int amount){
        var pool=CombatPools.of(p);if(pool.value(type)<amount)return false;
        pool.with(type,pool.value(type)-amount).store(p);runtime(p).clock.action(p.level().getGameTime());SevenfoldMastery.sync(p);return true;
    }
    public static void register(){
        NeoForge.EVENT_BUS.addListener(CombatResources::tick);
        NeoForge.EVENT_BUS.addListener(CombatResources::penalty);
        NeoForge.EVENT_BUS.addListener(CombatResources::shieldWindow);
        NeoForge.EVENT_BUS.addListener(CombatResources::outcome);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW,CombatResources::damage);
        NeoForge.EVENT_BUS.addListener(CombatResources::damaged);
        NeoForge.EVENT_BUS.addListener((AttackEntityEvent e)->{if(e.getEntity() instanceof ServerPlayer p)runtime(p).clock.action(p.level().getGameTime());});
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock e)->activity(e));
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem e)->activity(e));
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract e)->activity(e));
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteractSpecific e)->activity(e));
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.LeftClickBlock e)->activity(e));
    }
    private static void activity(PlayerInteractEvent e){if(e.getEntity() instanceof ServerPlayer p)runtime(p).clock.action(p.level().getGameTime());}
    private static void tick(PlayerTickEvent.Post e){
        if(!(e.getEntity() instanceof ServerPlayer p)||!p.isAlive()||p.isSpectator())return;
        CombatSlots.reconcile(p);var r=runtime(p);long now=p.level().getGameTime();
        boolean busy=p.isSprinting()||p.isShiftKeyDown()||p.isUsingItem()||p.swinging||p.getDeltaMovement().y>.15
            ||com.muxigame.zaluokeforging.combat.CombatOutcomes.activeGuard(p)
            ||r.selectedSlot>=0&&r.selectedSlot!=p.getInventory().selected;
        r.selectedSlot=p.getInventory().selected;
        var old=CombatPools.of(p);var next=r.clock.tick(old,now,busy,false);
        if(p.tickCount%20==0)checkpoint(p);
        if(!old.equals(next)){next.store(p);SevenfoldMastery.sync(p);}
    }
    private static void penalty(WeaponPenaltyEvent e){
        if(e.waived())return;
        var p=e.player();var profession=profession(p);
        if(e.penalty()==WeaponPenaltyEvent.Penalty.GIANT_HANDLING&&profession==WarProfession.BERSERKER&&spend(p,Mastery.MIGHT,2))e.waive();
        if(e.penalty()==WeaponPenaltyEvent.Penalty.OFFHAND_STRIKE&&profession==WarProfession.WARDEN&&spend(p,Mastery.STAMINA,2))e.waive();
    }
    private static void shieldWindow(ShieldWindowEvent e){
        if(profession(e.player())==WarProfession.KNIGHT&&e.extraTicks()<4&&spend(e.player(),Mastery.RESILIENCE,2))e.setExtraTicks(4);
    }
    private static void focus(ServerPlayer p,int amount){
        var pool=CombatPools.of(p);pool.with(Mastery.FOCUS,pool.focus()+amount).store(p);
        runtime(p).clock.focus(p.level().getGameTime());SevenfoldMastery.sync(p);
    }
    private static void outcome(WeaponCombatEvent e){
        if(e.kind()==WeaponCombatEvent.Kind.HIT&&e.actor() instanceof ServerPlayer p&&e.target()!=null&&!p.isAlliedTo(e.target())){
            runtime(p).clock.hit(p.level().getGameTime());focus(p,e.targetWasStunned()?3:1);
        }else if(e.kind()==WeaponCombatEvent.Kind.BLOCK&&e.actor() instanceof ServerPlayer p){
            var r=runtime(p);
            r.clock.combat(p.level().getGameTime());focus(p,2);
        }else if(e.kind()==WeaponCombatEvent.Kind.STUN&&e.actor() instanceof ServerPlayer p){
            var pool=CombatPools.of(p);pool.with(Mastery.FOCUS,0).store(p);SevenfoldMastery.sync(p);
        }
    }
    public static boolean shieldHeld(ServerPlayer p){return p.getMainHandItem().canPerformAction(ItemAbilities.SHIELD_BLOCK)||p.getOffhandItem().canPerformAction(ItemAbilities.SHIELD_BLOCK);}
    public static float attackMultiplier(ServerPlayer p){return profession(p)==WarProfession.BRAWLER?1+CombatPools.of(p).focus()*.02f:1;}
    private static void damage(LivingDamageEvent.Pre e){
        if(e.getNewDamage()<=0)return;
        if(e.getSource().getEntity() instanceof ServerPlayer attacker&&e.getEntity()!=attacker)
            e.setNewDamage(e.getNewDamage()*attackMultiplier(attacker));
        if(e.getEntity() instanceof ServerPlayer p&&profession(p)==WarProfession.KNIGHT&&shieldHeld(p)&&!e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)){
            int available=CombatPools.of(p).resilience();int cost=Math.min(available,(int)Math.ceil(e.getNewDamage()));
            if(cost>0&&spendExact(p,Mastery.RESILIENCE,cost)){
                float absorbed=Math.min(e.getNewDamage(),cost);
                com.muxigame.zaluokeforging.combat.CombatOutcomes.recordResourceAbsorption(e,absorbed);
                e.setNewDamage(Math.max(0,e.getNewDamage()-cost));runtime(p).absorbed=e.getSource();
            }
        }
    }
    private static void damaged(LivingDamageEvent.Post e){
        if(!(e.getEntity() instanceof ServerPlayer p))return;
        var r=runtime(p);boolean absorbed=r.absorbed==e.getSource();r.absorbed=null;
        if(e.getNewDamage()<=0&&!absorbed)return;
        r.clock.hurt(p.level().getGameTime());
        boolean defended=e.getBlockedDamage()>0;
        if(!defended&&e.getSource().getEntity() instanceof LivingEntity){var pool=CombatPools.of(p);pool.with(Mastery.FOCUS,pool.focus()-2).store(p);SevenfoldMastery.sync(p);}
    }
}
