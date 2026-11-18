package com.muxigame.sevenfold.ui;

import com.muxigame.sevenfold.StarNode;
import com.muxigame.sevenfold.StarTreeState;

/** Aggregate a confirmed snapshot into at most one ignition and one extinction cue. */
public record TreeAudioChange(boolean ignition,boolean extinction,boolean majorIgnition,boolean majorExtinction) {
    public static TreeAudioChange between(StarTreeState before,StarTreeState after){
        if(before==null)return new TreeAudioChange(false,false,false,false);
        boolean gain=false,loss=false,bigGain=false,bigLoss=false;int lostStars=0;
        for(var n:StarNode.values())if(n!=StarNode.RESET){
            if(after.isActivated(n)&&!before.isActivated(n)){gain=true;bigGain=true;}
            if(before.isActivated(n)&&!after.isActivated(n)){loss=true;bigLoss=true;}
            if(after.isComplete(n)&&!before.isComplete(n))bigGain=true;
            for(int i=0;i<10;i++){gain|=after.star(n,i)&&!before.star(n,i);if(before.star(n,i)&&!after.star(n,i)){loss=true;lostStars++;}}
        }
        return new TreeAudioChange(gain,loss,bigGain,bigLoss||lostStars>1);
    }
}
