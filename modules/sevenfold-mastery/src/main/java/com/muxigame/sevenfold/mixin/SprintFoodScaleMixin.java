package com.muxigame.sevenfold.mixin;
import com.muxigame.sevenfold.StarTreeState;
import net.minecraft.client.player.LocalPlayer;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(LocalPlayer.class)
abstract class SprintFoodScaleMixin {
    @ModifyExpressionValue(method="hasEnoughFoodToStartSprinting",at=@At(value="INVOKE",target="Lnet/minecraft/world/food/FoodData;getFoodLevel()I"))
    private int sevenfold$sprint(int food){return StarTreeState.of((LocalPlayer)(Object)this).active()?food/5:food;}
}
