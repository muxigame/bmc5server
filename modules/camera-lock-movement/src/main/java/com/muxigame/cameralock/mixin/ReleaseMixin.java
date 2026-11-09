package com.muxigame.cameralock.mixin;
import com.muxigame.cameralock.CombatCamera;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(MultiPlayerGameMode.class)
public class ReleaseMixin {
    @Inject(method="useItemOn",at=@At("HEAD"))
    private void cameraLock$block(net.minecraft.client.player.LocalPlayer player,net.minecraft.world.InteractionHand hand,
        net.minecraft.world.phys.BlockHitResult hit,CallbackInfoReturnable<?> ci){
        com.muxigame.cameralock.InteractionPicking.faceBlock(hit);
    }
    @Inject(method="releaseUsingItem",at=@At("HEAD"))
    private void cameraLock$release(CallbackInfo ci){CombatCamera.beforeUse();}
    @Inject(method="useItem",at=@At("HEAD"))
    private void cameraLock$use(net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand,CallbackInfoReturnable<?> ci){CombatCamera.beforeUse(hand);}
}
