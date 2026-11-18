package com.muxigame.sevenfold.mixin;
import com.muxigame.sevenfold.RpgNumbers;
import net.minecraft.server.level.ServerPlayer;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
@Pseudo @Mixin(targets="com.muxigame.realneeds.NeedsIllness",remap=false)
abstract class NeedsIllnessUnitsMixin {
    @ModifyExpressionValue(method="second",at=@At(value="INVOKE",target="Lnet/minecraft/world/food/FoodData;getFoodLevel()I"))
    private static int sevenfold$foodUnits(int food,@Local(argsOnly=true) ServerPlayer p){return RpgNumbers.active(p)?food/5:food;}
}
