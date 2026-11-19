package com.muxigame.sevenfold;
import com.muxigame.zaluokeforging.api.RpgScaleEvent;
import net.neoforged.neoforge.common.NeoForge;
public final class RpgIntegration {
    public static void register(){NeoForge.EVENT_BUS.addListener((RpgScaleEvent e)->{if(StarTreeState.of(e.player()).active())e.setMultiplier(5);});}
}
