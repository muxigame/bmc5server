package com.muxigame.zaluokeforging.client;

import com.muxigame.zaluokeforging.ZaluokeForging;
import com.muxigame.zaluokeforging.forging.Workpiece;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid=ZaluokeForging.MOD_ID,value=Dist.CLIENT)
public final class WorkpieceTooltips {
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event) {
        var stack=event.getItemStack();
        if(Workpiece.blank(stack)) {
            if(Workpiece.clamped(stack)) event.getToolTip().add(Component.translatable("tooltip.zaluoke_forging.clamped"));
            event.getToolTip().add(Component.translatable("tooltip.zaluoke_forging.hits",Workpiece.get(stack,"Hits"),Workpiece.requiredHits(stack)));
            event.getToolTip().add(Component.translatable("tooltip.zaluoke_forging.heat",Workpiece.heat(stack)));
        } else if(Workpiece.mold(stack)) event.getToolTip().add(Component.translatable("tooltip.zaluoke_forging.fill",Workpiece.get(stack,"Iron"),Workpiece.capacity(stack)));
    }
}
