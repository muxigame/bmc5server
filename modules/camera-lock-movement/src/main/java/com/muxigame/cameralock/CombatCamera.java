package com.muxigame.cameralock;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.lwjgl.glfw.GLFW;
import java.util.Comparator;
import java.util.List;

@EventBusSubscriber(modid="camera_lock", value=Dist.CLIENT)
public final class CombatCamera {
    public static final KeyMapping LOCK = new KeyMapping("key.camera_lock.lock", InputConstants.Type.MOUSE,2,"key.categories.camera_lock");
    private static final Minecraft MC=Minecraft.getInstance();
    private static LocalPlayer owner;
    private static boolean enabled;
    private static LivingEntity target;
    private static float yaw,pitch,freeYaw,freePitch,startYaw,startPitch;
    private static long transition,frameTime;
    private static boolean switchingTransition;
    private static double lateral;
    private static final MotionMath.Gesture gesture=new MotionMath.Gesture();
    public static boolean active() {
        return MC.player!=null && MC.level!=null && MC.options.getCameraType()!=CameraType.FIRST_PERSON
            && !MC.player.isSpectator() && !MC.player.isSleeping() && !MC.player.isPassenger();
    }
    private static void initialize() {
        if(owner!=MC.player || !enabled) {
            owner=MC.player; enabled=true; target=null; gesture.reset();
            yaw=freeYaw=owner.getYRot(); pitch=freePitch=Mth.clamp(owner.getXRot()+12,-65,75);
            transition=0; lateral=0; frameTime=System.nanoTime();
        }
    }
    public static float yaw(){initialize();return yaw;}
    public static float pitch(){initialize();return pitch;}
    public static double lateral(){return lateral;}
    public static boolean locked(){return target!=null;}
    public static void mouse(double dx,double dy) {
        initialize();
        if(MC.screen!=null || MC.isPaused())return;
        if(target!=null) {
            // dx here is sensitivity-adjusted vanilla rotation input. A 28-degree fling crosses a side zone.
            int side=gesture.move(dx*.15,28,System.currentTimeMillis());
            if(side!=0)switchTarget(side);
        } else {
            freeYaw+=(float)(dx*.15); freePitch=Mth.clamp(freePitch+(float)(dy*.15),-65,75);
        }
    }
    private static void begin(){beginTransition();gesture.reset();}
    private static void beginTransition(){switchingTransition=false;startYaw=yaw;startPitch=pitch;transition=System.nanoTime();}
    private static Vec3 point(LivingEntity e){return e.position().add(0,e.getBbHeight()*.62,0);}
    private static float aimYaw(Vec3 d){return (float)Math.toDegrees(Math.atan2(-d.x,d.z));}
    private static float aimPitch(Vec3 d){return (float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)));}
    private static boolean valid(LivingEntity e) {
        return e!=MC.player && e.isAlive() && !e.isRemoved() && !e.isInvisibleTo(MC.player)
            && e.level()==MC.level && MC.player.distanceToSqr(e)<=400 && MC.player.hasLineOfSight(e)
            && NpcBridge.priority(e,MC.player)>=0;
    }
    private static List<LivingEntity> candidates() {
        return MC.level.getEntitiesOfClass(LivingEntity.class,MC.player.getBoundingBox().inflate(20),CombatCamera::valid);
    }
    private static void toggle() {
        if(target!=null){target=null;begin();return;}
        Vec3 forward=Vec3.directionFromRotation(0,yaw);
        target=candidates().stream().filter(e->e.position().subtract(MC.player.position()).dot(forward)>0)
            .min(Comparator.<LivingEntity>comparingInt(e->NpcBridge.priority(e,MC.player))
            .thenComparingDouble(e->MC.player.distanceToSqr(e))).orElse(null);
        if(target!=null)begin();
        else MC.player.displayClientMessage(Component.translatable("message.camera_lock.none"),true);
    }
    private static void switchTarget(int side) {
        if(target==null)return;
        float current=aimYaw(point(target).subtract(MC.player.getEyePosition()));
        LivingEntity next=candidates().stream().filter(e->e!=target).filter(e->{
            double angle=MotionMath.wrap(aimYaw(point(e).subtract(MC.player.getEyePosition()))-current);
            return Math.signum(angle)==side && Math.abs(angle)<150;
        }).min(Comparator.comparingDouble(e->Math.abs(MotionMath.wrap(aimYaw(point(e).subtract(MC.player.getEyePosition()))-current)))).orElse(null);
        // Keep the consumed-stroke latch: the tail of the second fling must not switch again.
        if(next!=null){target=next;beginTransition();switchingTransition=true;}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Pre e) {
        if(!active()){enabled=false;owner=null;target=null;gesture.reset();while(LOCK.consumeClick()){}return;}
        initialize();
        if(MC.screen!=null || MC.isPaused()) {gesture.reset();while(LOCK.consumeClick()){}return;}
        if(target!=null&&!valid(target)){target=null;freeYaw=yaw;freePitch=pitch;begin();}
        while(LOCK.consumeClick())toggle();
        if(target!=null)aimPlayer();
    }
    @SubscribeEvent public static void button(InputEvent.MouseButton.Pre e) {
        if(active() && MC.screen==null && e.getButton()==2 && LOCK.matchesMouse(2)) {
            e.setCanceled(true); // avoid creative pick-block sharing the default middle button
            if(e.getAction()==GLFW.GLFW_PRESS && LOCK.matchesMouse(2)){initialize();toggle();}
        }
    }
    @SubscribeEvent public static void movement(MovementInputUpdateEvent e) {
        if(!active() || e.getEntity()!=MC.player || MC.screen!=null)return;
        initialize();
        float f=e.getInput().forwardImpulse,l=e.getInput().leftImpulse;
        double length=Math.min(1,Math.hypot(f,l));
        if(target!=null)aimPlayer();
        else if(length>.001){
            MC.player.setYRot((float)MotionMath.facing(yaw,f,l));
            MC.player.setXRot(Mth.clamp(freePitch-12,-90,90));
        }
        if(length>.001) {
            double direction=MotionMath.facing(yaw,f,l);
            double relative=Math.toRadians(direction-MC.player.getYRot());
            e.getInput().forwardImpulse=(float)(length*Math.cos(relative));
            e.getInput().leftImpulse=(float)(-length*Math.sin(relative));
        }
    }
    public static void updateCamera() {
        if(!active())return;
        initialize();
        long now=System.nanoTime(); double dt=Math.min(.1,(now-frameTime)/1e9);frameTime=now;
        float desiredYaw=freeYaw,desiredPitch=freePitch;
        if(target!=null) {
            Vec3 d=point(target).subtract(MC.player.getEyePosition().add(0,.65,0));
            desiredYaw=aimYaw(d); desiredPitch=aimPitch(d);
        }
        if(transition!=0) {
            double t=(now-transition)/(MotionMath.TURN_MS*1_000_000.0);
            double a=MotionMath.ease(t,switchingTransition);
            yaw=startYaw+(float)(MotionMath.wrap(desiredYaw-startYaw)*a);
            pitch=Mth.lerp((float)a,startPitch,desiredPitch);
            if(t>=1)transition=0;
        }else {
            double a=1-Math.exp(-dt*24);
            yaw+=(float)(MotionMath.wrap(desiredYaw-yaw)*a);pitch=Mth.lerp((float)a,pitch,desiredPitch);
        }
        // Follow lateral velocity with a bounded shoulder drift, at most 15% of screen width.
        Vec3 vel=MC.player.getDeltaMovement();double r=Math.toRadians(yaw);
        double side=vel.x*Math.cos(r)+vel.z*Math.sin(r);
        double desired=Math.clamp(side*2,-.65,.65);
        lateral+=(desired-lateral)*(1-Math.exp(-dt*9));
    }
    public static void aimPlayer() {
        if(MC.player==null)return;
        aimPlayer(MC.player.isUsingItem()?MC.player.getUseItem():MC.player.getMainHandItem());
    }
    private static void aimPlayer(ItemStack stack) {
        if(!active() || target==null || !valid(target))return;
        Vec3 delta=point(target).subtract(MC.player.getEyePosition());
        double speed=0,g=.05,drag=.99;
        if(stack.getItem() instanceof BowItem && MC.player.isUsingItem()) speed=3*BowItem.getPowerForTime(MC.player.getTicksUsingItem());
        else if(stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
            var projectiles=stack.get(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES);
            if(projectiles!=null && projectiles.getItems().stream().noneMatch(s->s.is(Items.FIREWORK_ROCKET)))speed=3.15;
        }
        else if(stack.getItem() instanceof TridentItem && MC.player.isUsingItem() && MC.player.getTicksUsingItem()>=10) speed=2.5;
        if(speed>.1 && !MC.player.isUnderWater() && !target.isUnderWater()) {
            Vec3 tv=new Vec3(target.getX()-target.xo,target.getY()-target.yo,target.getZ()-target.zo);
            if(tv.length()>1.5)tv=Vec3.ZERO; // teleports are not velocity
            if(target.onGround())tv=new Vec3(tv.x,0,tv.z);
            Vec3 pv=stack.getItem() instanceof CrossbowItem?Vec3.ZERO:MC.player.getDeltaMovement();
            MotionMath.Shot shot=MotionMath.intercept(delta.x,delta.y+.1,delta.z,tv.x,tv.y,tv.z,
                pv.x,MC.player.onGround()?0:pv.y,pv.z,speed,g,drag);
            if(shot!=null)delta=new Vec3(shot.x(),shot.y(),shot.z());
        }
        MC.player.setYRot(aimYaw(delta));MC.player.setXRot(aimPitch(delta));
        MC.player.setYHeadRot(MC.player.getYRot());
    }
    public static void beforeUse() {
        if(active()&&target!=null){aimPlayer();MC.player.connection.send(new ServerboundMovePlayerPacket.Rot(MC.player.getYRot(),MC.player.getXRot(),MC.player.onGround()));}
    }
    public static void beforeUse(net.minecraft.world.InteractionHand hand) {
        if(active()&&target!=null){aimPlayer(MC.player.getItemInHand(hand));MC.player.connection.send(new ServerboundMovePlayerPacket.Rot(MC.player.getYRot(),MC.player.getXRot(),MC.player.onGround()));}
    }
    @SubscribeEvent public static void hideOriginalCrosshair(RenderGuiLayerEvent.Pre e) {
        if(active() && e.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR))e.setCanceled(true);
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post e) {
        if(!active() || MC.options.hideGui || MC.screen!=null)return;
        int x=MC.getWindow().getGuiScaledWidth()/2,y=MC.getWindow().getGuiScaledHeight()/2;
        if(target==null)return;
        var g=e.getGuiGraphics();
        if(target!=null) {
            var camera=MC.gameRenderer.getMainCamera();
            Vec3 offset=point(target).subtract(camera.getPosition());
            var screen=new org.joml.Vector3f((float)offset.x,(float)offset.y,(float)offset.z)
                .rotate(new org.joml.Quaternionf(camera.rotation()).conjugate());
            if(screen.z<-.05) {
                double focal=MC.getWindow().getGuiScaledHeight()/(2*Math.tan(Math.toRadians(MC.options.fov().get()/2.0)));
                x+=(int)(screen.x*focal/-screen.z);y-=(int)(screen.y*focal/-screen.z);
            }
        }
        g.fill(x-1,y-1,x+2,y+2,0xFFFFFFFF);
    }
    @EventBusSubscriber(modid="camera_lock", value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e){e.register(LOCK);}
    }
}


