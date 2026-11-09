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
public final class CityWorksProjection {
 static CompoundTag data=new CompoundTag();static long received;
 static void update(CompoundTag t){data=t;received=System.nanoTime();}
 @SubscribeEvent public static void render(RenderLevelStageEvent event){var mc=Minecraft.getInstance();if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||mc.level==null||mc.player==null||System.nanoTime()-received>60_000_000_000L||!data.getString("Dimension").equals(mc.level.dimension().location().toString()))return;var min=BlockPos.of(data.getLong("Min"));var max=BlockPos.of(data.getLong("Max"));var pose=event.getPoseStack();var camera=event.getCamera().getPosition();pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);var buffers=mc.renderBuffers().bufferSource();var lines=buffers.getBuffer(RenderType.lines());LevelRenderer.renderLineBox(pose,lines,new AABB(min.getX(),min.getY(),min.getZ(),max.getX()+1,max.getY()+1,max.getZ()+1),.2f,1f,.8f,1f);for(Tag raw:data.getList("Samples",Tag.TAG_LONG)){var pos=BlockPos.of(((LongTag)raw).getAsLong());LevelRenderer.renderLineBox(pose,lines,new AABB(pos).inflate(.003),.2f,.7f,1f,.5f);}buffers.endBatch(RenderType.lines());pose.popPose();}
}
