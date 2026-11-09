package com.muxigame.buildingarchive;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
@EventBusSubscriber(modid=BuildingArchive.ID,value=Dist.CLIENT)
public final class ArchiveProjection {
 private static CompoundTag data=new CompoundTag();private static long received;
 public static void update(CompoundTag t){data=t;received=System.nanoTime();}
 @SubscribeEvent public static void render(RenderLevelStageEvent e){
  var mc=Minecraft.getInstance();if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||mc.level==null||mc.player==null||!mc.player.getMainHandItem().is(BuildingArchive.TOOL.get())||System.nanoTime()-received>2_000_000_000L||!data.getBoolean("Visible")||!mc.level.dimension().location().toString().equals(data.getString("Dimension")))return;
  int[] size=data.getIntArray("Size"),runs=data.getIntArray("Runs");if(size.length!=3)return;var origin=BlockPos.of(data.getLong("Origin"));var pose=e.getPoseStack();var camera=e.getCamera().getPosition();pose.pushPose();pose.translate(origin.getX()-camera.x,origin.getY()-camera.y,origin.getZ()-camera.z);
  var buffers=mc.renderBuffers().bufferSource();var lines=buffers.getBuffer(RenderType.lines());
  LevelRenderer.renderLineBox(pose,lines,new AABB(0,0,0,size[0],size[1],size[2]).inflate(.012),1f,data.getBoolean("Delete")?.15f:.78f,.15f,1f);
  LevelRenderer.renderLineBox(pose,lines,new AABB(0,0,0,size[0],.025,size[2]),data.getBoolean("Delete")?1f:.25f,data.getBoolean("Delete")?.15f:1f,.35f,1f);
  LevelRenderer.renderLineBox(pose,lines,new AABB(-.08,-.08,-.08,.15,.15,.15),1f,.3f,.3f,1f);
  for(int i=0;i+5<runs.length&&i<2048*6;i+=6)LevelRenderer.renderLineBox(pose,lines,new AABB(runs[i],runs[i+1],runs[i+2],runs[i+3],runs[i+4],runs[i+5]).inflate(.002),.12f,.85f,1f,.6f);
  buffers.endBatch(RenderType.lines());pose.popPose();
 }
}

