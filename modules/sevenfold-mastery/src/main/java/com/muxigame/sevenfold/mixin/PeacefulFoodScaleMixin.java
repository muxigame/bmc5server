package com.muxigame.sevenfold.mixin;
import com.muxigame.sevenfold.StarTreeState;
import net.minecraft.world.entity.player.Player;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(Player.class)
abstract class PeacefulFoodScaleMixin {
    @ModifyExpressionValue(method="aiStep",at=@At(value="INVOKE",target="Lnet/minecraft/world/food/FoodData;getSaturationLevel()F",ordinal=0))
    private float sevenfold$threshold(float value){return StarTreeState.of((Player)(Object)this).active()?value/5:value;}
    @ModifyArg(method="aiStep",at=@At(value="INVOKE",target="Lnet/minecraft/world/food/FoodData;setFoodLevel(I)V"),index=0)
    private int sevenfold$peacefulFood(int next){var p=(Player)(Object)this;int old=p.getFoodData().getFoodLevel();return StarTreeState.of(p).active()?old+(next-old)*5:next;}
    @ModifyArg(method="aiStep",at=@At(value="INVOKE",target="Lnet/minecraft/world/food/FoodData;setSaturation(F)V"),index=0)
    private float sevenfold$peacefulSaturation(float next){var p=(Player)(Object)this;float old=p.getFoodData().getSaturationLevel();return StarTreeState.of(p).active()?old+(next-old)*5:next;}
}
