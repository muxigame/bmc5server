package com.muxigame.sevenfold.ui;

/** One camera path for constellation approach/retreat, with a slow start and light settling. */
public final class TreeZoom {
    public static final long MILLIS=900;
    private long began=-1;private double from,to;
    private static double response(double t){double a=t*t;return 1-Math.exp(-10*a)*(Math.cos(8*a)+1.25*Math.sin(8*a));}
    public double value(long now){if(began<0||now-began>=MILLIS)return to;double t=Math.clamp((now-began)/(double)MILLIS,0,1);return mix(from,to,response(t)/response(1));}
    public boolean moving(long now){return began>=0&&now-began<MILLIS&&from!=to;}
    public void towards(boolean detail,long now){double destination=detail?1:0;if(to==destination)return;from=value(now);to=destination;began=now;}
    public void snap(boolean detail){from=to=detail?1:0;began=-1;}
    public static double mix(double a,double b,double t){return a+(b-a)*t;}
    /** Projects the existing world around the chosen constellation; endpoints are unchanged. */
    public static double project(double world,double origin,double destination,double ratio,double t){return mix(origin,destination,t)+(world-origin)*mix(1,ratio,t);}
}
