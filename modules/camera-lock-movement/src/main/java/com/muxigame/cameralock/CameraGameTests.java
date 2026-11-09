package com.muxigame.cameralock;

import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

/** Runs only under NeoForge GameTest; no CustomNPCs present in this test runtime. */
@GameTestHolder("camera_lock")
@PrefixGameTestTemplate(false)
public final class CameraGameTests {
    @GameTest(template="empty",timeoutTicks=50) public static void stationaryArrow(GameTestHelper h){shot(h,0,0,3);}
    @GameTest(template="empty",timeoutTicks=50) public static void movingTargetArrow(GameTestHelper h){shot(h,.18,0,3);}
    @GameTest(template="empty",timeoutTicks=50) public static void bothMovingArrow(GameTestHelper h){shot(h,-.18,.2,3);}
    @GameTest(template="empty",timeoutTicks=50) public static void crossbowArrow(GameTestHelper h){shot(h,.18,0,3.15);}
    @GameTest(template="empty") public static void vanillaClassification(GameTestHelper h){
        var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        h.assertTrue(NpcBridge.priority(EntityType.HUSK.create(h.getLevel()),player)==1,"Vanilla hostile priority");
        h.assertTrue(NpcBridge.priority(EntityType.COW.create(h.getLevel()),player)==-1,"Do not lock ordinary animals");
        h.succeed();
    }
    private static void shot(GameTestHelper h,double targetV,double shooterV,double speed){
        Vec3 origin=h.absoluteVec(new Vec3(8,12,2));
        Husk target=EntityType.HUSK.create(h.getLevel());
        target.setPos(origin.add(0,-1,18));target.setNoAi(true);target.setNoGravity(true);
        target.setSilent(true);h.getLevel().addFreshEntity(target);
        Vec3 offset=target.position().add(0,target.getBbHeight()*.62,0).subtract(origin);
        var aim=MotionMath.intercept(offset.x,offset.y,offset.z,targetV,0,0,shooterV,0,0,speed,.05,.99);
        h.assertTrue(aim!=null,"18-block intercept exists");
        Arrow arrow=EntityType.ARROW.create(h.getLevel());arrow.setPos(origin);
        arrow.shoot(aim.x(),aim.y(),aim.z(),(float)speed,1); // real vanilla inaccuracy retained
        arrow.setDeltaMovement(arrow.getDeltaMovement().add(shooterV,0,0));
        h.getLevel().addFreshEntity(arrow);
        h.onEachTick(()->{target.setPos(target.position().add(targetV,0,0));target.setDeltaMovement(Vec3.ZERO);});
        h.runAtTickTime(25,()->{
            boolean hit=target.getHealth()<target.getMaxHealth();
            target.discard();arrow.discard();
            h.assertTrue(hit,"Real server arrow must damage predicted target");h.succeed();
        });
    }
}

