package com.muxigame.cameralock.mixin;

import com.muxigame.cameralock.InteractionPicking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class PickingMixin {
    @Inject(method="pick(F)V",at=@At("TAIL"))
    private void cameraLock$pick(float partial,CallbackInfo ci){InteractionPicking.refresh(partial);}
    @ModifyArg(method="renderLevel",at=@At(value="INVOKE",
        target="Lnet/minecraft/client/renderer/GameRenderer;resetProjectionMatrix(Lorg/joml/Matrix4f;)V"),index=0)
    private Matrix4f cameraLock$frame(Matrix4f matrix){
        InteractionPicking.frame(matrix,Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
        return matrix;
    }
}
