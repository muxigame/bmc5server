package com.muxigame.sevenfold;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.*;

/** Absolute health changes scale; durations, amplifiers and percentages do not. */
public final class RpgNumbers {
    private static final ThreadLocal<Boolean> SCALED_HEAL=ThreadLocal.withInitial(()->false);
    private static final ThreadLocal<Boolean> SCALED_DAMAGE=ThreadLocal.withInitial(()->false);
    public static boolean active(LivingEntity e){return e instanceof Player p?StarTreeState.of(p).active():com.muxigame.zaluokeforging.api.RpgCombatScale.isScaledNpc(e);}
    public static void scaledHeal(LivingEntity p,float amount){boolean old=SCALED_HEAL.get();SCALED_HEAL.set(true);try{p.heal(amount);}finally{SCALED_HEAL.set(old);}}
    public static boolean scaledDamage(LivingEntity p,DamageSource source,float amount){boolean old=SCALED_DAMAGE.get();SCALED_DAMAGE.set(true);try{return p.hurt(source,amount);}finally{SCALED_DAMAGE.set(old);}}
    public static void register(){
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,(LivingHealEvent e)->{if(active(e.getEntity())&&!SCALED_HEAL.get()&&!com.muxigame.zaluokeforging.api.RpgCombatScale.healingIsScaled())e.setAmount(e.getAmount()*5);});
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH,(LivingIncomingDamageEvent e)->{
            // Environmental and potion damage must preserve their old fraction of max HP.
            var source=e.getSource();
            boolean potion=source.is(DamageTypes.MAGIC)||source.is(DamageTypes.INDIRECT_MAGIC);
            boolean scaledNpc=source.getEntity() instanceof LivingEntity caster&&com.muxigame.zaluokeforging.api.RpgCombatScale.isScaledNpc(caster);
            if(!SCALED_DAMAGE.get()&&active(e.getEntity())&&(source.getEntity()==null||potion&&!scaledNpc)&&!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))e.setAmount(e.getAmount()*5);
        });
    }
    public static void effects(LivingEntity p,boolean grant){
        if(p.level().isClientSide)return;
        var absorption=p.getEffect(MobEffects.ABSORPTION);var health=p.getEffect(MobEffects.HEALTH_BOOST);
        float extraAbs=active(p)&&absorption!=null?16*(absorption.getAmplifier()+1):0;
        boolean changed=set(p.getAttribute(Attributes.MAX_ABSORPTION),"potion_absorption",extraAbs);
        set(p.getAttribute(Attributes.MAX_HEALTH),"potion_health",p instanceof Player&&active(p)&&health!=null?16*(health.getAmplifier()+1):0);
        if(grant&&changed&&extraAbs>0)p.setAbsorptionAmount(Math.max(p.getAbsorptionAmount(),20*(absorption.getAmplifier()+1)));
        if(p.getAbsorptionAmount()>p.getMaxAbsorption())p.setAbsorptionAmount(p.getMaxAbsorption());
        if(p.getHealth()>p.getMaxHealth())p.setHealth(p.getMaxHealth());
    }
    private static boolean set(AttributeInstance a,String key,double amount){if(a==null)return false;var id=ResourceLocation.fromNamespaceAndPath(SevenfoldMastery.ID,key);var old=a.getModifier(id);if(old!=null&&old.amount()==amount||old==null&&amount==0)return false;a.removeModifier(id);if(amount>0)a.addTransientModifier(new AttributeModifier(id,amount,AttributeModifier.Operation.ADD_VALUE));return true;}
}
