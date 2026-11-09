package com.muxigame.zaluokeforging.forging;

import com.muxigame.zaluokeforging.ModContent;
import com.muxigame.zaluokeforging.ZaluokeForging;
import com.muxigame.zaluokeforging.block.WorkshopEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid=ZaluokeForging.MOD_ID)
public final class InteractionEvents {
    @SubscribeEvent public static void hitMold(PlayerInteractEvent.LeftClickBlock event) {
        if(!event.getLevel().getBlockState(event.getPos()).is(ModContent.PLACED_MOLD.get())) return;
        event.setCanceled(true);
    }
}
