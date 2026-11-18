package com.muxigame.sevenfold.ui;

/** Wall-clock audio envelope: interrupted voices keep a continuous, unhurried release. */
public final class TreeSoundFade {
    private final long began,attack;
    private long released=-1,release;
    private double releasedFrom;
    public TreeSoundFade(long now,long attack){began=now;this.attack=attack;}
    private static double smooth(double t){t=Math.clamp(t,0,1);return t*t*(3-2*t);}
    public double value(long now){return released<0?smooth((now-began)/(double)Math.max(1,attack)):releasedFrom*(1-smooth((now-released)/(double)release));}
    public void fade(long now,long millis){if(released>=0)return;releasedFrom=value(now);released=now;release=Math.max(1,millis);}
    public boolean finished(long now){return released>=0&&now-released>=release;}
}
