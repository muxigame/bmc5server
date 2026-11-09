package com.muxigame.cameralock;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.*;
import org.joml.Matrix4f;

public final class InteractionPicking {
    private static final Minecraft MC=Minecraft.getInstance();
    private static Matrix4f projection;
    private static Object level;
    public static InteractionRay.Result result;
    public static void frame(Matrix4f matrix,float partial) {
        projection=new Matrix4f(matrix);
        level=MC.level;
        refresh(partial);
    }
    public static void refresh(float partial) {
        if(!CombatCamera.active() || CombatCamera.locked() || MC.getCameraEntity()!=MC.player) {
            result=null;return;
        }
        var camera=MC.gameRenderer.getMainCamera();
        if(!camera.isInitialized() || camera.getEntity()!=MC.player)return;
        Vec3 origin=camera.getPosition();
        Vec3 direction=Vec3.directionFromRotation(camera.getXRot(),camera.getYRot());
        if(projection!=null && level==MC.level) {
            // Undo the actual frame projection, including dynamic FOV, view bob and hurt tilt.
            var ray=InteractionRay.screenRay(projection,camera.rotation(),origin);
            origin=ray.origin();direction=ray.direction();
        }
        result=InteractionRay.pick(MC.level,MC.player,MC.player.getEyePosition(partial),origin,direction,
            MC.player.blockInteractionRange(),MC.player.entityInteractionRange());
        result=InteractionRay.assistInteraction(MC.level,MC.player,MC.player.getEyePosition(partial),origin,direction,MC.player.blockInteractionRange(),result);
        MC.hitResult=result.hit();
        MC.crosshairPickEntity=MC.hitResult instanceof EntityHitResult hit?hit.getEntity():null;
    }
    public static void faceBlock(BlockHitResult hit) {
        if(!CombatCamera.active() || CombatCamera.locked() || hit.getType()!=HitResult.Type.BLOCK)return;
        Vec3 d=hit.getLocation().subtract(MC.player.getEyePosition());
        MC.player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));
        MC.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));
        MC.player.connection.send(new ServerboundMovePlayerPacket.Rot(MC.player.getYRot(),MC.player.getXRot(),MC.player.onGround()));
    }
}


