package com.muxigame.cameralock;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("camera_lock")
@PrefixGameTestTemplate(false)
public final class InteractionTests {
    @GameTest(template="empty") public static void leverDoorChestAssistance(GameTestHelper h) {
        var states=java.util.List.of(
            Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE,AttachFace.FLOOR),
            Blocks.OAK_DOOR.defaultBlockState(),
            Blocks.CHEST.defaultBlockState());
        int index=0;
        for(var state:states) {
            BlockPos p=h.absolutePos(new BlockPos(4+index++*5,3,5));
            h.getLevel().setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);
            h.getLevel().setBlock(p,state,2);
            if(state.getBlock() instanceof DoorBlock)
                h.getLevel().setBlock(p.above(),state.setValue(DoorBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),2);
            var box=state.getShape(h.getLevel(),p).bounds().move(p);
            Vec3 aim=new Vec3(box.maxX+.12,box.getCenter().y,box.getCenter().z);
            Vec3 eye=aim.add(0,0,-2),camera=aim.add(0,.1,-4),direction=aim.subtract(camera);
            var player=h.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(eye.add(0,-player.getEyeHeight(),0));
            var exact=InteractionRay.pick(h.getLevel(),player,eye,camera,direction,4.5,3);
            h.assertTrue(exact.hit().getType()==HitResult.Type.MISS,"Outside original shape must miss");
            var assisted=InteractionRay.assistInteraction(h.getLevel(),player,eye,camera,direction,4.5,exact);
            h.assertTrue(assisted.hit() instanceof BlockHitResult hit && hit.getBlockPos().equals(p),
                "Expanded selection must acquire "+state.getBlock());
            var hit=(BlockHitResult)assisted.hit();
            if(state.getBlock() instanceof ChestBlock) {
                h.assertTrue(state.getMenuProvider(h.getLevel(),p)!=null,"Real chest menu remains available");
            } else {
                state.useWithoutItem(h.getLevel(),player,hit);
                h.assertTrue(h.getLevel().getBlockState(p).getValue(state.getBlock() instanceof LeverBlock
                    ?LeverBlock.POWERED:DoorBlock.OPEN),"Lever/door must actually toggle");
            }
            var far=InteractionRay.assistInteraction(h.getLevel(),player,eye.add(0,0,-8),camera,direction,4.5,exact);
            h.assertTrue(far==exact,"Assistance must preserve reach");
            h.getLevel().setBlock(p.offset(0,0,-1),Blocks.STONE.defaultBlockState(),2);
            var blocked=InteractionRay.assistInteraction(h.getLevel(),player,eye,camera,direction,4.5,exact);
            h.assertTrue(blocked==exact,"Assistance must not acquire through a wall");
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void enlargedButtonSelection(GameTestHelper h) {
        BlockPos p=button(h);
        Vec3 center=Vec3.atLowerCornerOf(p).add(.5,.0625,.5);
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        Vec3 eye=center.add(0,1.6,-2),camera=eye.add(0,.65,-4);
        Vec3 direction=center.add(.30,0,0).subtract(camera);
        var exact=InteractionRay.pick(h.getLevel(),player,eye,camera,direction,4.5,3);
        h.assertTrue(exact.preview()!=null && !exact.preview().getBlockPos().equals(p),"Fixture misses original tiny button");
        var assisted=InteractionRay.assistInteraction(h.getLevel(),player,eye,camera,direction,4.5,exact);
        h.assertTrue(assisted.hit() instanceof BlockHitResult hit && hit.getBlockPos().equals(p),
            "Near miss must select button rather than support");
        var hit=(BlockHitResult)assisted.hit();
        h.getLevel().getBlockState(p).useWithoutItem(h.getLevel(),player,hit);
        h.assertTrue(h.getLevel().getBlockState(p).getValue(ButtonBlock.POWERED),"Assisted real button powers on");
        var distant=InteractionRay.assistInteraction(h.getLevel(),player,eye.add(0,0,-8),camera,direction,4.5,exact);
        h.assertTrue(distant==exact,"Assistance must not extend player reach");
        h.succeed();
    }
    private static BlockPos button(GameTestHelper h) {
        BlockPos p=h.absolutePos(new BlockPos(10,2,10));
        h.getLevel().setBlock(p.below(),Blocks.STONE.defaultBlockState(),3);
        h.getLevel().setBlock(p,Blocks.STONE_BUTTON.defaultBlockState()
            .setValue(ButtonBlock.FACE,AttachFace.FLOOR).setValue(ButtonBlock.FACING,Direction.NORTH),3);
        return p;
    }
    @GameTest(template="empty") public static void cameraButtonAndPress(GameTestHelper h) {
        BlockPos p=button(h);
        Vec3 aim=Vec3.atLowerCornerOf(p).add(.5,.0625,.5);
        Vec3 eye=aim.add(0,1.6,-2),camera=eye.add(0,.65,-4);
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(eye.add(0,-player.getEyeHeight(),0));
        player.setYRot(180); // body points AWAY: targeting must still follow the screen
        var picked=InteractionRay.pick(h.getLevel(),player,eye,camera,aim.subtract(camera),4.5,3);
        h.assertTrue(picked.hit().getType()==HitResult.Type.BLOCK,"Camera center must hit tiny button");
        var hit=(BlockHitResult)picked.hit();
        h.assertTrue(hit.getBlockPos().equals(p),"Button, not its supporting block");
        h.getLevel().getBlockState(p).useWithoutItem(h.getLevel(),player,hit);
        h.assertTrue(h.getLevel().getBlockState(p).getValue(ButtonBlock.POWERED),"Real button must power on");
        h.succeed();
    }
    @GameTest(template="empty") public static void distantButtonCannotInteract(GameTestHelper h) {
        BlockPos p=button(h);Vec3 aim=Vec3.atLowerCornerOf(p).add(.5,.0625,.5);
        Vec3 eye=aim.add(0,1,-6),camera=eye.add(0,.65,-4);
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        var picked=InteractionRay.pick(h.getLevel(),player,eye,camera,aim.subtract(camera),4.5,3);
        h.assertTrue(picked.preview()!=null && picked.tooFar(),"Far button retained for distance feedback");
        h.assertTrue(picked.hit().getType()==HitResult.Type.MISS,"Camera must not extend reach");
        h.succeed();
    }
    @GameTest(template="empty") public static void cameraOverWallCannotInteract(GameTestHelper h) {
        BlockPos p=button(h);Vec3 aim=Vec3.atLowerCornerOf(p).add(.5,.0625,.5);
        Vec3 eye=aim.add(0,.2,-2),camera=aim.add(0,8,-4);
        h.getLevel().setBlock(p.offset(0,0,-1),Blocks.STONE.defaultBlockState(),3);
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        var picked=InteractionRay.pick(h.getLevel(),player,eye,camera,aim.subtract(camera),4.5,3);
        h.assertTrue(picked.preview()!=null && picked.preview().getBlockPos().equals(p),"Camera sees over wall");
        h.assertTrue(picked.blocked() && picked.hit().getType()==HitResult.Type.MISS,"Eyes cannot interact through wall");
        h.succeed();
    }
    @GameTest(template="empty") public static void sideOffsetButton(GameTestHelper h) {
        BlockPos p=button(h);Vec3 aim=Vec3.atLowerCornerOf(p).add(.5,.0625,.5);
        Vec3 eye=aim.add(0,1.6,-2);
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        for(double shoulder:new double[]{-.65,0,.65}) {
            Vec3 camera=eye.add(shoulder,.65,-4);
            var picked=InteractionRay.pick(h.getLevel(),player,eye,camera,aim.subtract(camera),4.5,3);
            h.assertTrue(picked.hit().getType()==HitResult.Type.BLOCK
                && ((BlockHitResult)picked.hit()).getBlockPos().equals(p),"Shoulder drift must preserve exact button aim");
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void actualProjectionCenter(GameTestHelper h) {
        for(float fov:new float[]{50,70,110})for(float bob:new float[]{-.1f,0,.1f}) {
            var projection=new org.joml.Matrix4f().perspective((float)Math.toRadians(fov),16f/9,.05f,256);
            projection.translate(bob,-bob*.5f,0).rotateZ(bob).rotateX(bob*.5f);
            var rotation=new org.joml.Quaternionf().rotationYXZ(1.3f,-.25f,0);
            Vec3 camera=new Vec3(10,4,10);
            var ray=InteractionRay.screenRay(projection,rotation,camera);
            Vec3 offset=ray.origin().add(ray.direction().scale(7)).subtract(camera);
            var eye=new org.joml.Vector3f((float)offset.x,(float)offset.y,(float)offset.z)
                .rotate(new org.joml.Quaternionf(rotation).conjugate());
            var ndc=projection.transformProject(eye);
            h.assertTrue(Math.abs(ndc.x)<.0001 && Math.abs(ndc.y)<.0001,
                "Interaction ray must project to the exact center with bob/FOV");
        }
        h.succeed();
    }
}

