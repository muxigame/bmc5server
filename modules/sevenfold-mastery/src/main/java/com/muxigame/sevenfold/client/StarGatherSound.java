package com.muxigame.sevenfold.client;

import java.util.function.DoubleSupplier;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.Util;

/** Airy vanilla beacon tone with a rising envelope; an effect, never a music track. */
final class StarGatherSound extends AbstractTickableSoundInstance {
    private final DoubleSupplier progress;
    private final boolean kindling;
    private boolean fading;
    private long fadeAt;private float fadeFrom;
    StarGatherSound(DoubleSupplier progress,boolean kindling){
        super(SoundEvents.BEACON_AMBIENT,SoundSource.PLAYERS,SoundInstance.createUnseededRandom());
        this.progress=progress;this.kindling=kindling;looping=true;relative=true;attenuation=Attenuation.NONE;volume=.025f;pitch=kindling?.62f:.92f;
    }
    void fadeOut(){if(!fading){fading=true;fadeAt=Util.getMillis();fadeFrom=volume;}}
    @Override public boolean canStartSilent(){return true;}
    @Override public void tick(){
        if(fading){double t=Math.clamp((Util.getMillis()-fadeAt)/600.0,0,1);volume=(float)(fadeFrom*(1-t*t*(3-2*t)));if(t>=1)stop();return;}
        double f=Math.clamp(progress.getAsDouble(),0,1);
        // Still a gentle start, now audible over the space bed; clearly builds toward confirmation.
        double ease=f*f*(3-2*f);volume=(float)(.06+(kindling?.58:.42)*Math.pow(f,1.7));pitch=(float)(kindling?.62+.82*ease:.92-.44*ease);
    }
}
