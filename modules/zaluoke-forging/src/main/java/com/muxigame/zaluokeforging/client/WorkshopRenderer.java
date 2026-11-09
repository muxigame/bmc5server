package com.muxigame.zaluokeforging.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.muxigame.zaluokeforging.block.WorkshopEntity;
import com.muxigame.zaluokeforging.block.WorkshopBlock;
import com.muxigame.zaluokeforging.forging.Workpiece;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.Blocks;

public final class WorkshopRenderer implements BlockEntityRenderer<WorkshopEntity> {
    public WorkshopRenderer(BlockEntityRendererProvider.Context context) { }
    @Override public boolean shouldRenderOffScreen(WorkshopEntity be) { return be.kind().equals("mold_stand"); }
    @Override public void render(WorkshopEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        var mc=Minecraft.getInstance();
        if(be.kind().equals("bellows_support")) bellows(be,partial,pose,buffers,light,overlay);
        if(be.kind().equals("refining_furnace") && be.slag()>=32) hints(be,partial,pose,buffers,"shovel");
        if(be.kind().equals("placed_mold") && !be.work().isEmpty()) hints(be,partial,pose,buffers,"hammer");
        if(be.kind().equals("quenching_barrel") && be.water==0) hints(be,partial,pose,buffers,"water");
        if(be.kind().equals("quenching_barrel") && be.water>0) plane(pose,buffers,"water_still",.18F+be.water*.19F,0x3F76E4,light,overlay);
        if(be.kind().equals("refining_furnace") && be.liquid>0) plane(pose,buffers,"lava_still",.25F+.4F*be.liquid/192,0xFFFFFF,15728880,overlay);
        if(be.kind().equals("blacksmith_hearth") && be.fire>0) plane(pose,buffers,"magma",.57F,0xFFFFFF,15728880,overlay);
        if(be.work().isEmpty() || be.kind().equals("blacksmith_hearth")) return;
        pose.pushPose();
        if(be.kind().equals("mold_stand")) {
            pose.pushPose(); pose.translate(.35,.2,.35); pose.scale(.3F,1.3F,.3F);
            mc.getBlockRenderer().renderSingleBlock(Blocks.IRON_BLOCK.defaultBlockState(),pose,buffers,light,overlay);
            pose.popPose();
        }
        double y=switch(be.kind()) { case "mold_stand" -> 1.94; case "blacksmith_anvil" -> .94; case "blacksmith_hearth" -> .72; case "placed_mold" -> .425; default -> .08; };
        pose.translate(.5,y,.5);
        pose.mulPose(Axis.YP.rotationDegrees(-be.getBlockState().getValue(WorkshopBlock.FACING).toYRot()));
        pose.scale(.85F,.85F,.85F);
        if(Workpiece.blank(be.work())) pose.mulPose(Axis.XP.rotationDegrees(90));
        mc.getItemRenderer().renderStatic(be.work(),ItemDisplayContext.NONE,Workpiece.heat(be.work())>0?15728880:light,overlay,pose,buffers,be.getLevel(),0);
        pose.popPose();
        if(be.kind().equals("blacksmith_anvil") && Workpiece.blank(be.work()) && Workpiece.get(be.work(),"Hits")<Workpiece.requiredHits(be.work())) target(be,partial,pose,buffers);
        if(be.kind().equals("blacksmith_anvil") && Workpiece.blank(be.work())) completion(be,partial,pose,buffers);
    }
    private static void bellows(WorkshopEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        double age=be.getLevel().getGameTime()+partial-be.bellowsStart;
        float compression=age>=0 && age<60 ? (float)(.5-.5*Math.cos(age/60*Math.PI*2)) : 0;
        float top=11-5*compression;
        pose.pushPose(); pose.translate(.5,0,.5);
        pose.mulPose(Axis.YP.rotationDegrees(180-be.getBlockState().getValue(WorkshopBlock.FACING).toYRot()));
        pose.translate(-.5,0,-.5);
        cube(pose,buffers,Blocks.BROWN_TERRACOTTA,4,4,5,12,top,13,light,overlay);
        cube(pose,buffers,Blocks.SPRUCE_PLANKS,3,top,4,13,top+2,14,light,overlay);
        for(int i=1;i<=3;i++) { float y=4+(top-4)*i/4; cube(pose,buffers,Blocks.DARK_OAK_PLANKS,3.5F,y,4.5F,12.5F,y+.6F,13.5F,light,overlay); }
        cube(pose,buffers,Blocks.SPRUCE_PLANKS,13,top-5,9,15,top+4,10,light,overlay);
        pose.popPose();
    }
    private static void cube(PoseStack p,MultiBufferSource b,net.minecraft.world.level.block.Block block,float x,float y,float z,float X,float Y,float Z,int light,int overlay) {
        p.pushPose();p.translate(x/16,y/16,z/16);p.scale((X-x)/16,(Y-y)/16,(Z-z)/16);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(block.defaultBlockState(),p,b,light,overlay);p.popPose();
    }
    private static void hints(WorkshopEntity be,float partial,PoseStack pose,MultiBufferSource buffers,String icon) {
        int alpha=(int)(55+150*(.5+.5*Math.sin((be.getLevel().getGameTime()+partial)*Math.PI/40)));
        var texture=icon.equals("hammer")?ResourceLocation.fromNamespaceAndPath("zaluoke_forging","textures/hint/hammer.png"):ResourceLocation.withDefaultNamespace("textures/item/"+(icon.equals("shovel")?"iron_shovel":"water_bucket")+".png");
        for(var direction:net.minecraft.core.Direction.values()) {
            if(icon.equals("hammer") && direction!=net.minecraft.core.Direction.UP) continue;
            pose.pushPose();pose.translate(.5,be.kind().equals("placed_mold")?.18:.5,.5);
            switch(direction) {
                case NORTH -> pose.mulPose(Axis.YP.rotationDegrees(180));
                case EAST -> pose.mulPose(Axis.YP.rotationDegrees(90));
                case WEST -> pose.mulPose(Axis.YP.rotationDegrees(-90));
                case UP -> pose.mulPose(Axis.XP.rotationDegrees(-90));
                case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(90));
                default -> { }
            }
            pose.translate(0,0,be.kind().equals("placed_mold") && (direction==net.minecraft.core.Direction.UP || direction==net.minecraft.core.Direction.DOWN)?.20:.503);
            var v=buffers.getBuffer(RenderType.entityTranslucent(texture));
            v.addVertex(pose.last(),-.2F,-.2F,0).setColor(255,255,255,alpha).setUv(0,1).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose.last(),0,0,1);
            v.addVertex(pose.last(),.2F,-.2F,0).setColor(255,255,255,alpha).setUv(1,1).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose.last(),0,0,1);
            v.addVertex(pose.last(),.2F,.2F,0).setColor(255,255,255,alpha).setUv(1,0).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose.last(),0,0,1);
            v.addVertex(pose.last(),-.2F,.2F,0).setColor(255,255,255,alpha).setUv(0,0).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose.last(),0,0,1);
            pose.popPose();
        }
    }
    private static void patch(PoseStack pose,VertexConsumer v,float x,float X,float z,float Z,float y,int rgb,int alpha) {
        for(float[] point:new float[][]{{x,z,0,0},{x,Z,0,1},{X,Z,1,1},{X,z,1,0}})
            v.addVertex(pose.last(),point[0],y,point[1]).setColor((rgb>>16)&255,(rgb>>8)&255,rgb&255,alpha).setUv(point[2],point[3]).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose.last(),0,1,0);
    }
    private static void target(WorkshopEntity be,float partial,PoseStack pose,MultiBufferSource buffers) {
        pose.pushPose();pose.translate(.5,com.muxigame.zaluokeforging.forging.ForgeTarget.TOP+.002,.5);
        pose.mulPose(Axis.YP.rotationDegrees(-be.getBlockState().getValue(WorkshopBlock.FACING).toYRot()));
        var v=buffers.getBuffer(RenderType.entityTranslucent(ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png")));
        double age=be.getLevel().getGameTime()+partial-be.targetStart;
        int pulse=be.targetActive(be.getLevel().getGameTime())?(int)(245*Math.sin(Math.PI*age/20)):0;
        for(int zone=0;zone<Workpiece.regions(be.work());zone++) {
            if(zone!=Workpiece.get(be.work(),"Target") || pulse<=0) continue;
            double[] bounds=com.muxigame.zaluokeforging.forging.ForgeTarget.bounds(be.work(),zone);
            float a=(float)bounds[2],b=(float)bounds[3],left=(float)bounds[0],right=(float)bounds[1];
            float edge=.0025F;
            patch(pose,v,left,right,a,a+edge,0,0x050505,Math.min(240,pulse));
            patch(pose,v,left,right,b-edge,b,0,0x050505,Math.min(240,pulse));
            patch(pose,v,left,left+edge,a,b,0,0x050505,Math.min(240,pulse));
            patch(pose,v,right-edge,right,a,b,0,0x050505,Math.min(240,pulse));
            if(zone==Workpiece.get(be.work(),"Target") && pulse>0)
                patch(pose,v,left+edge,right-edge,a+edge,b-edge,.0005F,0xFFFF80,pulse);
        }
        pose.popPose();
    }
    private static void completion(WorkshopEntity be,float partial,PoseStack pose,MultiBufferSource buffers) {
        double age=be.getLevel().getGameTime()+partial-be.finishedAt;
        if(age<0 || age>=12 || Workpiece.get(be.work(),"Hits")<Workpiece.requiredHits(be.work())) return;
        pose.pushPose();pose.translate(.5,com.muxigame.zaluokeforging.forging.ForgeTarget.TOP+.004,.5);
        pose.mulPose(Axis.YP.rotationDegrees(-be.getBlockState().getValue(WorkshopBlock.FACING).toYRot()));
        var v=buffers.getBuffer(RenderType.entityTranslucent(ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png")));
        double start=com.muxigame.zaluokeforging.forging.ForgeTarget.START;
        double center=start+age/12*4*com.muxigame.zaluokeforging.forging.ForgeTarget.STEP;
        for(int j=0;j<48;j++) {
            double z=start+j*.85/64;
            int alpha=(int)(255*Math.max(0,1-Math.abs(z-center)/.08));
            float width=Workpiece.axe(be.work())?(float)(.85*5/16):(float)com.muxigame.zaluokeforging.forging.ForgeTarget.halfWidth(be.work(),z);
            if(alpha>0) patch(pose,v,-width,width,(float)z,(float)(z+.85/64),0,0xE4F3FF,alpha);
        }
        pose.popPose();
    }
    private static void plane(PoseStack pose,MultiBufferSource buffers,String texture,float y,int color,int light,int overlay) {
        var sprite=Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ResourceLocation.withDefaultNamespace("block/"+texture));
        VertexConsumer v=buffers.getBuffer(RenderType.translucent());
        float a=.125F,b=.875F;
        int r=(color>>16)&255,g=(color>>8)&255,blue=color&255;
        v.addVertex(pose.last(),a,y,a).setColor(r,g,blue,255).setUv(sprite.getU0(),sprite.getV0()).setOverlay(overlay).setLight(light).setNormal(pose.last(),0,1,0);
        v.addVertex(pose.last(),a,y,b).setColor(r,g,blue,255).setUv(sprite.getU0(),sprite.getV1()).setOverlay(overlay).setLight(light).setNormal(pose.last(),0,1,0);
        v.addVertex(pose.last(),b,y,b).setColor(r,g,blue,255).setUv(sprite.getU1(),sprite.getV1()).setOverlay(overlay).setLight(light).setNormal(pose.last(),0,1,0);
        v.addVertex(pose.last(),b,y,a).setColor(r,g,blue,255).setUv(sprite.getU1(),sprite.getV0()).setOverlay(overlay).setLight(light).setNormal(pose.last(),0,1,0);
    }
}
