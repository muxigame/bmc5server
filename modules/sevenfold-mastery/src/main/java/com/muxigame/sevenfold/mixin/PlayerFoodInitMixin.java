package com.muxigame.sevenfold.mixin;
import com.muxigame.sevenfold.RpgFoodData;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Player.class)
abstract class PlayerFoodInitMixin {
    @Inject(method="<init>",at=@At("RETURN"))
    private void sevenfold$food(CallbackInfo ci){RpgFoodData.install((Player)(Object)this);}
}
