package com.muxigame.sevenfold.mixin;
import com.muxigame.sevenfold.RpgNumbers;
import net.minecraft.world.entity.*;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntity.class)
abstract class EffectScaleMixin {
    @Inject(method="onEffectAdded",at=@At("RETURN"))
    private void sevenfold$added(MobEffectInstance effect,Entity source,CallbackInfo ci){RpgNumbers.effects((LivingEntity)(Object)this,true);}
    @Inject(method="onEffectUpdated",at=@At("RETURN"))
    private void sevenfold$updated(MobEffectInstance effect,boolean force,Entity source,CallbackInfo ci){RpgNumbers.effects((LivingEntity)(Object)this,true);}
    @Inject(method="onEffectRemoved",at=@At("RETURN"))
    private void sevenfold$removed(MobEffectInstance effect,CallbackInfo ci){RpgNumbers.effects((LivingEntity)(Object)this,false);}
}
