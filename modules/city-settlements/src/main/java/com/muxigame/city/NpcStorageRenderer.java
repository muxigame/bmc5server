package com.muxigame.city;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
/** Vanilla chest geometry, facing and lid animation with our separate original texture. */
public final class NpcStorageRenderer implements BlockEntityRenderer<RuralBlocks.Link> {
 final ModelPart lid,lock,bottom;
 static final net.minecraft.resources.ResourceLocation TEXTURE=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(CityMod.ID,"textures/entity/chest/npc_storage.png");
 public NpcStorageRenderer(BlockEntityRendererProvider.Context context){var model=context.bakeLayer(ModelLayers.CHEST);lid=model.getChild("lid");lock=model.getChild("lock");bottom=model.getChild("bottom");}
 public void render(RuralBlocks.Link link,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){if(!(link.getBlockState().getBlock() instanceof RuralBlocks.WorkBlock block)||!block.kind.equals("storage"))return;pose.pushPose();pose.translate(.5,.5,.5);pose.mulPose(Axis.YP.rotationDegrees(-link.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot()));pose.translate(-.5,-.5,-.5);float open=Mth.lerp(partial,link.previousLid,link.lid);open=1-(1-open)*(1-open)*(1-open);lid.xRot=-open*(float)Math.PI/2;lock.xRot=lid.xRot;var vertices=buffers.getBuffer(RenderType.entityCutout(TEXTURE));lid.render(pose,vertices,light,overlay);lock.render(pose,vertices,light,overlay);bottom.render(pose,vertices,light,overlay);pose.popPose();}
}
