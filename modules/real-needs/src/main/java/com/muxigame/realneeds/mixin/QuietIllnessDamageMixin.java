package com.muxigame.realneeds.mixin;

import com.muxigame.realneeds.NeedsIllness;
import com.muxigame.realneeds.NeedsNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exclude only internal illness from presentation, leaving the damage pipeline intact. */
@Mixin(LivingEntity.class)
public abstract class QuietIllnessDamageMixin {
    @Shadow protected abstract void playHurtSound(DamageSource source);

    @Inject(method="hurt",at=@At("RETURN"))
    private void realNeeds$health(DamageSource source,float damage,CallbackInfoReturnable<Boolean> cir){
        // FIFO ordering puts this before vanilla's next health packet. Its duplicate
        // value then cannot manufacture local hurtTime. Real attack packets and any
        // real-hit animation already in progress are untouched, even in the same tick.
        if(source.is(NeedsIllness.DAMAGE)&&cir.getReturnValue()&&(Object)this instanceof ServerPlayer p
            &&p.connection!=null&&p.connection.hasChannel(NeedsNetwork.QuietHealth.TYPE))
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,new NeedsNetwork.QuietHealth(p.getId(),p.getHealth()));
    }

    @Redirect(method="hurt",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/WalkAnimationState;setSpeed(F)V"))
    private void realNeeds$walk(WalkAnimationState walk,float speed,DamageSource source,float damage){
        if(!source.is(NeedsIllness.DAMAGE))walk.setSpeed(speed);
    }
    @Redirect(method="hurt",at=@At(value="FIELD",target="Lnet/minecraft/world/entity/LivingEntity;hurtDuration:I",opcode=181))
    private void realNeeds$duration(LivingEntity entity,int ticks,DamageSource source,float damage){
        if(!source.is(NeedsIllness.DAMAGE))entity.hurtDuration=ticks;
    }
    @Redirect(method="hurt",at=@At(value="FIELD",target="Lnet/minecraft/world/entity/LivingEntity;hurtTime:I",opcode=181))
    private void realNeeds$animation(LivingEntity entity,int ticks,DamageSource source,float damage){
        if(!source.is(NeedsIllness.DAMAGE))entity.hurtTime=ticks;
    }
    @Redirect(method="hurt",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;broadcastDamageEvent(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;)V"))
    private void realNeeds$event(Level level,Entity entity,DamageSource source){
        if(!source.is(NeedsIllness.DAMAGE))level.broadcastDamageEvent(entity,source);
    }
    @Redirect(method="hurt",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;playHurtSound(Lnet/minecraft/world/damagesource/DamageSource;)V"))
    private void realNeeds$sound(LivingEntity entity,DamageSource source){
        if(!source.is(NeedsIllness.DAMAGE))playHurtSound(source);
    }
}
