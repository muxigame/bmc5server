package com.muxigame.city;

import net.minecraft.server.MinecraftServer;

/** Shared bounded steps spread accelerated data work across real server ticks. */
public final class SimulationClock {
 public static final int MAX_SPEED=100;
 private static MinecraftServer scheduledServer;private static RuralSettlement.State scheduledState;private static int tick=Integer.MIN_VALUE,steps;
 static int credit(RuralSettlement.State state){state.stepCredit=Math.clamp(state.stepCredit,0,19)+Math.clamp(state.speed,1,MAX_SPEED);int count=state.stepCredit/20;state.stepCredit%=20;return count;}
 static int steps(MinecraftServer server){var state=RuralSettlement.get(server).state;if(server!=scheduledServer||state!=scheduledState||tick!=server.getTickCount()){scheduledServer=server;scheduledState=state;tick=server.getTickCount();steps=credit(state);}return steps;}
 static void clear(){scheduledServer=null;scheduledState=null;tick=Integer.MIN_VALUE;steps=0;}
}
