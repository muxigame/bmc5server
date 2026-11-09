package com.muxigame.cameralock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.ButtonBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

@EventBusSubscriber(modid="camera_lock",value=Dist.CLIENT)
public final class ButtonHighlight {
    @SubscribeEvent public static void highlight(RenderHighlightEvent.Block event) {
        if(!CombatCamera.active() || CombatCamera.locked())return;
        var level=Minecraft.getInstance().level;
        var pos=event.getTarget().getBlockPos();
        var state=level.getBlockState(pos);
        if(!InteractionRay.supports(state))return;
        event.setCanceled(true);
        var camera=event.getCamera().getPosition();
        var buffer=event.getMultiBufferSource().getBuffer(RenderType.lines());
        for(var box:state.getShape(level,pos).toAabbs())
            LevelRenderer.renderLineBox(event.getPoseStack(),buffer,
                box.move(pos).inflate(.003).move(-camera.x,-camera.y,-camera.z),1,1,1,.9f);
    }
}

