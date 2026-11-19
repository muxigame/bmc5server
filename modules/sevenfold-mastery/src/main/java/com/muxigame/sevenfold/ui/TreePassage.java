package com.muxigame.sevenfold.ui;

/** Reversible one-second camera lift. It controls presentation only, never player pitch. */
public final class TreePassage {
    public static final long MILLIS=1000;
    private long began=-1,closedAt=-1;private double closedFrom;
    public void open(long now){if(began<0)began=now;}
    private static double ease(double f){f=Math.clamp(f,0,1);return f*f*f*(f*(6*f-15)+10);}
    public double sky(long now){if(closedAt>=0)return closedFrom*(1-ease((now-closedAt)/(double)MILLIS));return began<0?0:ease((now-began)/(double)MILLIS);}
    public void close(long now){if(closedAt<0){closedFrom=sky(now);closedAt=now;}}
    public boolean interactive(long now){return began>=0&&closedAt<0&&now-began>=MILLIS;}
    public boolean finished(long now){return closedAt>=0&&now-closedAt>=MILLIS;}
    public boolean closing(){return closedAt>=0;}
}
