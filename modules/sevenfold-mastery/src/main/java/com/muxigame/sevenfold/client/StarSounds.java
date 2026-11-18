package com.muxigame.sevenfold.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.Util;
import java.util.ArrayList;
import java.util.function.DoubleSupplier;
import com.muxigame.sevenfold.SevenfoldMastery;
import com.muxigame.sevenfold.ui.TreeSoundFade;

/** Screen-owned original soundscape. Music and effects respect their normal volume categories. */
final class StarSounds {
    private record Cue(SoundInstance sound,long at){}
    private final ArrayList<Cue> cues=new ArrayList<>();
    private Space space;private Voice travel,glass,extinction;
    private final ArrayList<Voice> confirmations=new ArrayList<>();
    static SoundEvent event(String name){return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SevenfoldMastery.ID,"stars."+name));}
    private void play(SoundInstance sound){Minecraft.getInstance().getSoundManager().play(sound);cues.add(new Cue(sound,Util.getMillis()));}
    void enter(StarTreeScreen owner,DoubleSupplier openness){if(space!=null)return;space=new Space(owner,openness);Minecraft.getInstance().getSoundManager().play(space);motion(true,true);}
    void ignite(boolean complete){
        releaseConfirmations();
        confirmation(new Voice(SoundEvents.EXPERIENCE_ORB_PICKUP,SoundSource.MASTER,.64f,complete?1.4f:1.12f,400,30,250));
        confirmation(new Voice(SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.MASTER,complete?.56f:.40f,1.6f,750,50,450));
        confirmation(new Voice(SoundEvents.GLASS_BREAK,SoundSource.MASTER,complete?.48f:.36f,.675f,1000,60,650));
        // The original 3.8s recording includes the irregular fragments and all decaying reflections.
        glass=new Voice(event("glass"),SoundSource.MASTER,complete?.95f:.82f,complete?.515f:.5f,complete?3689:3800,180,800);confirmation(glass);
    }
    private void releaseConfirmations(){for(var voice:confirmations)voice.fade(1500);}
    private void confirmation(Voice sound){confirmations.add(sound);play(sound);}
    void extinguish(boolean whole){
        releaseConfirmations();
        extinction=new Voice(event("extinction"),SoundSource.PLAYERS,whole?.82f:.64f,whole?.92f:1,3700,220);confirmation(extinction);
    }
    void motion(boolean inward,boolean passage){if(travel!=null)travel.fade(900);travel=new Voice(event(passage?(inward?"rise":"fall"):(inward?"approach":"retreat")),SoundSource.PLAYERS,passage?.68f:.66f,1,passage?2100:3900,160);play(travel);}
    void tick(){long now=Util.getMillis();var manager=Minecraft.getInstance().getSoundManager();cues.removeIf(c->now-c.at>12000&&!manager.isActive(c.sound));confirmations.removeIf(v->v.isStopped()||now-v.began>v.duration+500);}
    void removed(){var manager=Minecraft.getInstance().getSoundManager();if(space!=null)manager.stop(space);for(var cue:cues){if(cue.sound instanceof Voice v)v.fade(900);else manager.stop(cue.sound);}cues.clear();confirmations.clear();space=null;travel=glass=extinction=null;}
    String diagnostic(){var m=Minecraft.getInstance().getSoundManager();return "space="+(space!=null&&m.isActive(space))+", travel="+(travel!=null&&m.isActive(travel))+", extinction="+(extinction!=null&&m.isActive(extinction))+", glass="+(glass!=null&&m.isActive(glass));}
    private static final class Space extends AbstractTickableSoundInstance {
        private final StarTreeScreen owner;private final DoubleSupplier openness;
        Space(StarTreeScreen owner,DoubleSupplier openness){super(event("space"),SoundSource.MUSIC,SoundInstance.createUnseededRandom());this.owner=owner;this.openness=openness;looping=true;relative=true;attenuation=Attenuation.NONE;volume=0;pitch=1;}
        @Override public boolean canStartSilent(){return true;}
        @Override public void tick(){if(Minecraft.getInstance().screen!=owner){stop();return;}volume=(float)(.32*Math.clamp(openness.getAsDouble(),0,1));}
    }
    private static final class Voice extends AbstractTickableSoundInstance {
        private final long began=Util.getMillis();private final int duration,softTail;private final float gain;
        private final TreeSoundFade envelope;
        Voice(SoundEvent event,SoundSource source,float gain,float pitch,int duration,long attack){this(event,source,gain,pitch,duration,attack,0);}
        Voice(SoundEvent event,SoundSource source,float gain,float pitch,int duration,long attack,int softTail){super(event,source,SoundInstance.createUnseededRandom());relative=true;attenuation=Attenuation.NONE;volume=0;this.gain=gain;this.pitch=pitch;this.duration=duration;this.softTail=softTail;envelope=new TreeSoundFade(began,attack);}
        @Override public boolean canStartSilent(){return true;}
        void fade(long millis){envelope.fade(Util.getMillis(),millis);}
        @Override public void tick(){long now=Util.getMillis();double tail=softTail==0?1:Math.clamp((duration-(now-began))/(double)softTail,0,1);tail=tail*tail*(3-2*tail);volume=(float)(gain*envelope.value(now)*tail);if(envelope.finished(now)||now-began>=duration)stop();}
    }
}
