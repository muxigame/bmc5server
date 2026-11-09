package com.muxigame.city;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid=CityMod.ID,value=Dist.CLIENT)
public final class CityWorldOverlay {
    private static CompoundTag data=new CompoundTag();private static long received;
    public static void update(CompoundTag tag){data=tag;received=System.nanoTime();}
    private static AABB box(CompoundTag t){BlockPos a=BlockPos.of(t.getLong("Min")),b=BlockPos.of(t.getLong("Max"));return new AABB(a.getX(),a.getY(),a.getZ(),b.getX()+1,b.getY()+1,b.getZ()+1);}
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        var mc=Minecraft.getInstance();if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||mc.player==null||mc.level==null||!(mc.player.getMainHandItem().getItem() instanceof CityMod.PlanningItem)||System.nanoTime()-received>2_000_000_000L||!data.getString("Dimension").equals(mc.level.dimension().location().toString()))return;
        var pose=e.getPoseStack();var camera=e.getCamera().getPosition();pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        var buffers=mc.renderBuffers().bufferSource();var lines=buffers.getBuffer(RenderType.lines());
        if(data.contains("City"))LevelRenderer.renderLineBox(pose,lines,box(data.getCompound("City")).inflate(.006),1f,.72f,.16f,1f);
        for(Tag value:data.getList("Runs",Tag.TAG_COMPOUND)){CompoundTag run=(CompoundTag)value;boolean yes=run.getBoolean("Included");LevelRenderer.renderLineBox(pose,lines,box(run).inflate(.004),yes?.15f:1f,yes?1f:.15f,yes?.4f:.15f,yes?.5f:.95f);}
        if(data.contains("Draft"))LevelRenderer.renderLineBox(pose,lines,box(data.getCompound("Draft")).inflate(.025),.12f,.85f,1f,1f);
        buffers.endBatch(RenderType.lines());pose.popPose();
    }
}
