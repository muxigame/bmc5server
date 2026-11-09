package com.muxigame.realneeds;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** Server extension point, emitted after the built-in infection roll has been applied. */
public final class DiseaseCheckEvent extends Event {
    public enum Exposure {DAMAGE,UNSAFE_FOOD,POLLUTION,COLD,DAMP,NORMAL}
    private final ServerPlayer player;private final NeedsState state;private final Exposure exposure;private final String detail;
    private final double chance,roll;
    public DiseaseCheckEvent(ServerPlayer player,NeedsState state,Exposure exposure,String detail,double chance,double roll){this.player=player;this.state=state;this.exposure=exposure;this.detail=detail;this.chance=chance;this.roll=roll;}
    public ServerPlayer getPlayer(){return player;}
    /** Shared pending state; edits made here are persisted by the caller after dispatch. */
    public NeedsState getState(){return state;}
    public Exposure getExposure(){return exposure;}
    public String getDetail(){return detail;}
    public double getChancePercent(){return chance;}
    public double getRollPercent(){return roll;}
    public boolean passed(){return roll<chance;}
}
