package com.muxigame.sevenfold.mixin;
import com.muxigame.sevenfold.RpgNumbers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(targets="net.minecraft.world.effect.PoisonMobEffect")
abstract class PoisonScaleMixin {
    @WrapOperation(method="applyEffectTick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean sevenfold$poison(LivingEntity entity,DamageSource source,float amount,Operation<Boolean> original){
        if(!RpgNumbers.active(entity))return original.call(entity,source,amount);
        float scaled=Math.min(amount*5,Math.max(0,entity.getHealth()-5));return scaled>0&&RpgNumbers.scaledDamage(entity,source,scaled);
    }
}
