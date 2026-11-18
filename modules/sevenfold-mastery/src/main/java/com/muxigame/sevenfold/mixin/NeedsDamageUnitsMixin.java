package com.muxigame.sevenfold.mixin;
import com.muxigame.sevenfold.RpgNumbers;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.injection.ModifyArg;
/** Body condition uses its own 0..100 scores; preserve injury probability on a larger HP scale. */
@Pseudo @Mixin(targets="com.muxigame.realneeds.NeedsWorld",remap=false)
abstract class NeedsDamageUnitsMixin {
    @ModifyExpressionValue(method="hurt",at=@At(value="INVOKE",target="Lnet/neoforged/neoforge/event/entity/living/LivingDamageEvent$Post;getNewDamage()F"))
    private static float sevenfold$bodyUnits(float damage,LivingDamageEvent.Post event){return RpgNumbers.active(event.getEntity())?damage/5:damage;}
    @ModifyExpressionValue(method="second",at=@At(value="INVOKE",target="Lnet/minecraft/world/food/FoodData;getFoodLevel()I"))
    private static int sevenfold$foodUnits(int food,@Local(argsOnly=true) ServerPlayer p){return RpgNumbers.active(p)?food/5:food;}
    @ModifyArg(method="consumed",at=@At(value="INVOKE",target="Lnet/minecraft/world/food/FoodData;setFoodLevel(I)V"),index=0)
    private static int sevenfold$vomit(int next,@Local(argsOnly=true) ServerPlayer p){int current=p.getFoodData().getFoodLevel();return RpgNumbers.active(p)?Math.max(0,current-(current-next)*5):next;}
}
