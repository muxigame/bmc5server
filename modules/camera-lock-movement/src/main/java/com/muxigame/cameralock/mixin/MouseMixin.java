package com.muxigame.cameralock.mixin;
import com.muxigame.cameralock.CombatCamera;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(MouseHandler.class)
public class MouseMixin {
    @Redirect(method="turnPlayer",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void cameraLock$turn(LocalPlayer player,double x,double y){
        if(CombatCamera.active())CombatCamera.mouse(x,y);else player.turn(x,y);
    }
}
