package com.muxigame.cameralock.mixin;
import com.muxigame.cameralock.CombatCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setRotation(float yaw,float pitch,float roll);
    @Shadow protected abstract void setPosition(Vec3 position);
    @Inject(method="setup",at=@At("TAIL"))
    private void cameraLock$setup(BlockGetter level,Entity entity,boolean detached,boolean reverse,float partial,CallbackInfo ci){
        if(!CombatCamera.active() || entity!=Minecraft.getInstance().player)return;
        CombatCamera.updateCamera();float yaw=CombatCamera.yaw(),pitch=CombatCamera.pitch();
        setRotation(yaw,pitch,0);
        Vec3 eye=entity.getPosition(partial).add(0,entity.getEyeHeight()+.65,0);
        double fov=Minecraft.getInstance().options.fov().get();
        double aspect=(double)Minecraft.getInstance().getWindow().getWidth()/Minecraft.getInstance().getWindow().getHeight();
        double maxSide=2*4*Math.tan(Math.toRadians(fov/2))*aspect*.15;
        double side=Math.clamp(CombatCamera.lateral(),-maxSide,maxSide);
        double r=Math.toRadians(yaw);
        Vec3 desired=eye.subtract(Vec3.directionFromRotation(pitch,yaw).scale(4))
            .add(Math.cos(r)*side,0,Math.sin(r)*side);
        // Sweep the camera's near-plane corners, including the raised pivot, against walls/ceilings.
        Vec3 anchor=entity.getPosition(partial).add(0,entity.getEyeHeight(),0);
        double fraction=1;
        for(double x:new double[]{-.15,.15})for(double y:new double[]{-.15,.15})for(double z:new double[]{-.15,.15}){
            Vec3 offset=new Vec3(x,y,z);
            var hit=level.clip(new ClipContext(anchor.add(offset),desired.add(offset),ClipContext.Block.VISUAL,ClipContext.Fluid.NONE,entity));
            if(hit.getType()!=HitResult.Type.MISS)fraction=Math.min(fraction,Math.max(0,anchor.add(offset).distanceTo(hit.getLocation())/anchor.distanceTo(desired)-.035));
        }
        setPosition(anchor.lerp(desired,fraction));
    }
}
