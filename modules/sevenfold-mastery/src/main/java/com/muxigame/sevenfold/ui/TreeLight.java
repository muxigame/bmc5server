package com.muxigame.sevenfold.ui;

import com.muxigame.sevenfold.StarNode;
import com.muxigame.sevenfold.StarTreeState;

/** Presentation only: confirmed state changes fade without delaying gameplay or hit testing. */
public final class TreeLight {
    public static final long STAR_MILLIS=1100, HIDDEN_MILLIS=1500;
    private final Fade[][] stars=new Fade[8][10];
    private final Fade[] active=new Fade[8],visible=new Fade[8];
    private boolean initialized;
    public TreeLight(){for(int n=0;n<8;n++){active[n]=new Fade();visible[n]=new Fade();for(int i=0;i<10;i++)stars[n][i]=new Fade();}}
    public void reset(){initialized=false;}
    public void update(StarTreeState state,long now){
        for(var n:StarNode.values())if(n!=StarNode.RESET){int k=n.ordinal();
            active[k].update(state.isActivated(n),now,950,initialized);
            visible[k].update(state.visible(n),now,HIDDEN_MILLIS,initialized);
            for(int i=0;i<10;i++)stars[k][i].update(state.star(n,i),now,STAR_MILLIS,initialized);
        }initialized=true;
    }
    public double star(StarNode n,int index,long now){return stars[n.ordinal()][index].value(now);}
    public double active(StarNode n,long now){return active[n.ordinal()].value(now);}
    public double visible(StarNode n,long now){return visible[n.ordinal()].value(now);}
    public double average(StarNode n,long now){double total=0;for(int i=0;i<10;i++)total+=star(n,i,now);return total/10;}
    /** Negative outside a confirmed fade-out; no particles on loading already dark stars. */
    public double extinction(StarNode n,int index,long now){var f=stars[n.ordinal()][index];return f.target||f.from<=0||now>=f.began+f.duration?-1:(now-f.began)/(double)f.duration;}
    private static final class Fade {
        boolean target;double from;long began,duration;
        double value(long now){if(duration==0||now>=began+duration)return target?1:0;double t=Math.clamp((now-began)/(double)duration,0,1),f=t*t*(3-2*t);return from+((target?1:0)-from)*f;}
        void update(boolean next,long now,long fadeOut,boolean initialized){
            if(!initialized){target=next;from=next?1:0;duration=0;return;}
            if(next==target)return;
            from=value(now);target=next;began=now;
            // Keep established ignition timing; a reversal of an unfinished fade remains continuous.
            duration=next?(from>0&&from<1?320:0):fadeOut;
        }
    }
}
