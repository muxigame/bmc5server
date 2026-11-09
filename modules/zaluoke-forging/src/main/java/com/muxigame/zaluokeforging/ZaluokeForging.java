package com.muxigame.zaluokeforging;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import net.neoforged.bus.api.IEventBus;

/** Common entry point for Zaluoke Forging. */
@Mod(ZaluokeForging.MOD_ID)
public final class ZaluokeForging {
    public static final String MOD_ID = "zaluoke_forging";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ZaluokeForging(IEventBus modBus) {
        ModContent.register(modBus);
        LOGGER.info("{} foundation loaded", MOD_ID);
    }
}
