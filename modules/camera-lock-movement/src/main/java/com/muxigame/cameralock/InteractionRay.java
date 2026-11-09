package com.muxigame.cameralock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Camera chooses the point; the player's eyes still enforce reach and visibility. */
public final class InteractionRay {
    /** Third-person-only assistance: choose the real interactive block, never its enlarged visual bounds. */
    public static Result assistInteraction(Level level,Entity player,Vec3 eye,Vec3 origin,Vec3 direction,
                                      double reach,Result original) {
        if(original.hit() instanceof EntityHitResult)return original;

        BlockPos anchor=original.preview()==null?null:original.preview().getBlockPos();
        if(anchor!=null && supports(level.getBlockState(anchor)))
            return original;
        Vec3 end=origin.add(direction.normalize().scale(origin.distanceTo(eye)+reach+1));
        BlockHitResult best=null;
        double score=Double.MAX_VALUE;
        // Scan near the screen ray, including misses beside doors/chests against open sky.
        var candidates=new java.util.HashSet<BlockPos>();
        Vec3 unit=direction.normalize();
        for(double t=0;t<=origin.distanceTo(end);t+=.5) {
            BlockPos sample=BlockPos.containing(origin.add(unit.scale(t)));
            for(BlockPos p:BlockPos.betweenClosed(sample.offset(-1,-1,-1),sample.offset(1,1,1)))
                candidates.add(p.immutable());
        }
        for(BlockPos pos:candidates) {
            var state=level.getBlockState(pos);
            if(!supports(state))continue;
            var shape=state.getShape(level,pos);
            if(shape.isEmpty())continue;
            AABB bounds=shape.bounds().move(pos);
            if(bounds.inflate(.22).clip(origin,end).isEmpty())continue;
            Vec3 center=bounds.getCenter();
            // Both the camera and player's eyes must see the actual button, not just its padding.
            Vec3 to=center.subtract(origin);
            var cameraHit=level.clip(new ClipContext(origin,center,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
            if(cameraHit.getType()!=HitResult.Type.BLOCK || !cameraHit.getBlockPos().equals(pos))continue;
            if(eye.distanceToSqr(cameraHit.getLocation())>reach*reach)continue;
            var eyeHit=level.clip(new ClipContext(eye,center,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
            if(eyeHit.getType()!=HitResult.Type.BLOCK || !eyeHit.getBlockPos().equals(pos))continue;
            double deviation=to.cross(direction.normalize()).lengthSqr();
            if(deviation<score) {score=deviation;best=cameraHit;}
        }
        return best==null?original:new Result(best,best,false,false);
    }
    public static boolean supports(net.minecraft.world.level.block.state.BlockState state) {
        var block=state.getBlock();
        return block instanceof net.minecraft.world.level.block.ButtonBlock
            || block instanceof net.minecraft.world.level.block.LeverBlock
            || block instanceof net.minecraft.world.level.block.DoorBlock
            || block instanceof net.minecraft.world.level.block.ChestBlock
            || block instanceof net.minecraft.world.level.block.EnderChestBlock;
    }
    public record Ray(Vec3 origin,Vec3 direction) {}
    public static Ray screenRay(org.joml.Matrix4f projection,org.joml.Quaternionf rotation,Vec3 camera) {
        var inverse=new org.joml.Matrix4f(projection).invert();
        var near=inverse.transformProject(new org.joml.Vector3f(0,0,-1));
        var far=inverse.transformProject(new org.joml.Vector3f(0,0,1));
        near.rotate(rotation);far.rotate(rotation);
        return new Ray(camera.add(near.x,near.y,near.z),
            new Vec3(far.x-near.x,far.y-near.y,far.z-near.z).normalize());
    }
    public record Result(HitResult hit, BlockHitResult preview, boolean tooFar, boolean blocked) {}
    public static Result pick(Level level, Entity player, Vec3 eye, Vec3 origin, Vec3 direction,
                              double blockRange, double entityRange) {
        Vec3 end=origin.add(direction.normalize().scale(origin.distanceTo(eye)+Math.max(blockRange,entityRange)+8));
        BlockHitResult block=level.clip(new ClipContext(origin,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        double limit=origin.distanceToSqr(block.getLocation());
        EntityHitResult entity=ProjectileUtil.getEntityHitResult(player,origin,end,
            new AABB(origin,end).inflate(1),e->e!=player && !e.isSpectator() && e.isPickable(),limit);
        if(entity!=null && origin.distanceToSqr(entity.getLocation())<limit) {
            boolean far=eye.distanceToSqr(entity.getLocation())>entityRange*entityRange;
            var obstruction=level.clip(new ClipContext(eye,entity.getLocation(),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
            boolean blocked=obstruction.getType()!=HitResult.Type.MISS;
            return new Result(far||blocked?miss(entity.getLocation(),direction):entity,null,far,blocked);
        }
        if(block.getType()==HitResult.Type.MISS)return new Result(block,null,false,false);
        boolean far=eye.distanceToSqr(block.getLocation())>blockRange*blockRange;
        Vec3 toward=block.getLocation().subtract(eye);
        var visible=level.clip(new ClipContext(eye,block.getLocation().add(toward.normalize().scale(.001)),
            ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        boolean blocked=visible.getType()!=HitResult.Type.MISS && !visible.getBlockPos().equals(block.getBlockPos());
        return new Result(far||blocked?miss(block.getLocation(),direction):block,block,far,blocked);
    }
    private static BlockHitResult miss(Vec3 point,Vec3 direction) {
        return BlockHitResult.miss(point,Direction.getNearest(direction.x,direction.y,direction.z),BlockPos.containing(point));
    }
}

