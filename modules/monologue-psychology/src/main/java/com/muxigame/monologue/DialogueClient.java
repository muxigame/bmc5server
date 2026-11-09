package com.muxigame.monologue;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid=MonologueMod.ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class DialogueClient {
    private static final class Bubble {
        final ActiveLine line=new ActiveLine();Object nativeBubble;
    }
    private static final net.minecraft.client.renderer.MultiBufferSource.BufferSource bubbleBuffers=net.minecraft.client.renderer.MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.ByteBufferBuilder(65536));
    private static final Map<UUID,Bubble> bubbles=new HashMap<>();
    private static ActiveLine thought=new ActiveLine();
    private static Object world,player;
    private static int offset=24;
    private record Projected(UUID player,float x,float y,float scale){}
    private static final List<Projected> projected=new ArrayList<>();
    private static long now(){return System.nanoTime()/1_000_000;}
    private static void checkWorld(){var mc=Minecraft.getInstance();if(world!=mc.level||player!=mc.player){bubbles.clear();projected.clear();thought=new ActiveLine();world=mc.level;player=mc.player;}}
    @SubscribeEvent public static void setup(FMLClientSetupEvent e){e.enqueueWork(()->{
        DialogueNetwork.ClientBridge.cue=cue->{
            checkWorld();var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||!mc.level.dimension().location().toString().equals(cue.dimension()))return;
            long time=now(),duration=Math.clamp(cue.ticks(),20,200)*50L;
            if(!cue.head().isEmpty()){
                Bubble b=bubbles.computeIfAbsent(cue.player(),id->new Bubble());
                if(b.line.offer(cue.head(),cue.priority(),time,duration))b.nativeBubble=NpcBubbleBridge.create(cue.head());
            }
            if(cue.player().equals(mc.player.getUUID())&&thought.offer(cue.thought(),cue.priority(),time,duration))offset=Math.clamp(cue.offset(),12,120);
        };
        DialogueNetwork.ClientBridge.snapshot=t->{
            var config=t.getCompound("config");
            if(!config.getBoolean("enabled")||!config.getBoolean("headEnabled"))bubbles.clear();
            if(!config.getBoolean("enabled")||!config.getBoolean("thoughtEnabled"))thought=new ActiveLine();
            var mc=Minecraft.getInstance();
            if(mc.screen instanceof DialogueScreen s)s.update(t);
            else if(t.getBoolean("open")){var s=new DialogueScreen(mc.screen);s.update(t);mc.setScreen(s);}
        };
    });}
    @EventBusSubscriber(modid=MonologueMod.ID,value=Dist.CLIENT)
    public static final class Events {
        @SubscribeEvent public static void tick(ClientTickEvent.Post e){checkWorld();long time=now();bubbles.values().removeIf(b->!b.line.active(time));if(Minecraft.getInstance().player!=null&&!Minecraft.getInstance().player.isAlive())thought=new ActiveLine();}
        @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut e){bubbles.clear();projected.clear();thought=new ActiveLine();world=null;player=null;}
        @SubscribeEvent public static void screen(ScreenEvent.Init.Post e){
            String name=e.getScreen().getClass().getName();
            if(!name.equals("noppes.npcs.client.gui.player.GuiQuestLog")&&!name.equals("noppes.npcs.client.gui.player.GuiFaction")&&!name.startsWith("noppes.npcs.client.gui.mainmenu."))return;
            e.addListener(Button.builder(Component.literal("独白与心理"),b->PacketDistributor.sendToServer(new DialogueNetwork.Request("view","hurt","head",0,"",0)))
                .bounds(Math.max(4,e.getScreen().width-196),e.getScreen().height-24,104,20).build());
        }
        @SubscribeEvent public static void hud(RenderGuiEvent.Post e){
            var mc=Minecraft.getInstance();long time=now();
            if(mc.player==null||mc.screen!=null||mc.options.hideGui||mc.player.isSpectator())return;
            var gui=e.getGuiGraphics();gui.flush();
            for(var point:projected){var bubble=bubbles.get(point.player());if(bubble==null||!bubble.line.active(time))continue;
                var pose=gui.pose();pose.pushPose();pose.translate(point.x(),point.y(),100);pose.scale(-point.scale(),-point.scale(),-point.scale());
                pose.mulPose(new org.joml.Quaternionf(mc.getEntityRenderDispatcher().cameraOrientation()).conjugate());
                NpcBubbleBridge.draw(bubble.nativeBubble,bubble.line.text,pose,bubbleBuffers,1.2f,true,net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
                bubbleBuffers.endBatch();pose.popPose();
            }
            if(!thought.active(time))return;
            var g=e.getGuiGraphics();int width=mc.getWindow().getGuiScaledWidth(),height=mc.getWindow().getGuiScaledHeight();
            var lines=mc.font.split(Component.literal(thought.text),Math.min(280,width-24));
            int y=Math.min(height/2+offset,height-20-Math.min(4,lines.size())*11);
            int alpha=(int)(255*Math.min(1,(thought.expires-time)/500.0));if(alpha<8)return;
            int color=alpha<<24|0xf0e8da;
            for(int i=0;i<Math.min(4,lines.size());i++){var line=lines.get(i);g.drawString(mc.font,line,(width-mc.font.width(line))/2,y+i*11,color,true);}
        }
        @SubscribeEvent public static void world(RenderLevelStageEvent e){
            if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
            projected.clear();var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.options.hideGui)return;
            long time=now();var camera=e.getCamera().getPosition();
            for(var entry:bubbles.entrySet()){
                var bubble=entry.getValue();var p=mc.level.getPlayerByUUID(entry.getKey());
                if(p==null||!p.isAlive()||p.isSpectator()||p.isInvisibleTo(mc.player)||!bubble.line.active(time)||p==mc.player&&mc.options.getCameraType().isFirstPerson()||p.distanceToSqr(mc.player)>32*32)continue;
                var pos=p.getPosition(e.getPartialTick().getGameTimeDeltaPartialTick(false)).add(0,p.getBbHeight()*1.35,0);
                if(p.distanceToSqr(mc.player)>=16&&mc.level.clip(new net.minecraft.world.level.ClipContext(camera,pos,net.minecraft.world.level.ClipContext.Block.VISUAL,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player)).getType()!=net.minecraft.world.phys.HitResult.Type.MISS)continue;
                var clip=new org.joml.Vector4f((float)(pos.x-camera.x),(float)(pos.y-camera.y),(float)(pos.z-camera.z),1).mul(e.getModelViewMatrix()).mul(e.getProjectionMatrix());
                if(clip.w<=0||clip.z< -clip.w||clip.z>clip.w||Math.abs(clip.x)>clip.w*1.3||Math.abs(clip.y)>clip.w*1.3)continue;
                float w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
                projected.add(new Projected(entry.getKey(),(clip.x/clip.w+1)*w/2,(1-clip.y/clip.w)*h/2,e.getProjectionMatrix().m11()*h/(2*clip.w)*p.getBbHeight()/1.8f));
            }
        }
    }
}