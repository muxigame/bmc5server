package com.muxigame.sevenfold.ui;

/** Pure viewport motion: edge acceleration, short inertial release, and explicit bounds. */
public final class TreeCamera {
    private double x,y,vx,vy,minX,maxX,minY,maxY;
    private boolean waitingForMotion,hasOrigin;private double originX,originY;
    public double x(){return x;}public double y(){return y;}
    public double speed(){return Math.hypot(vx,vy);}
    public void bounds(double minX,double maxX,double minY,double maxY){this.minX=minX;this.maxX=maxX;this.minY=minY;this.maxY=maxY;clamp();}
    public void stop(){vx=vy=0;}
    public void reset(){x=y=0;stop();clamp();}
    public void waitForPointerMotion(){waitingForMotion=true;hasOrigin=false;stop();}
    private void clamp(){double nx=Math.clamp(x,minX,maxX),ny=Math.clamp(y,minY,maxY);if(nx!=x)vx=0;if(ny!=y)vy=0;x=nx;y=ny;}
    public void update(double mouseX,double mouseY,double width,double height,double seconds,boolean enabled){
        if(waitingForMotion&&mouseX>=0&&mouseY>=0&&mouseX<width&&mouseY<height){if(!hasOrigin){originX=mouseX;originY=mouseY;hasOrigin=true;}else if(Math.hypot(mouseX-originX,mouseY-originY)>8)waitingForMotion=false;}
        if(waitingForMotion){stop();return;}
        if(!enabled||mouseX<0||mouseY<0||mouseX>=width||mouseY>=height){stop();return;}
        double dt=Math.clamp(seconds,0,.05),band=Math.min(42,Math.min(width,height)*.16);
        double dx=edge(mouseX,width,band),dy=edge(mouseY,height,band),length=Math.hypot(dx,dy);
        if(length>1){dx/=length;dy/=length;}
        if(length>0){double blend=1-Math.exp(-4*dt);vx+=(dx*220-vx)*blend;vy+=(dy*220-vy)*blend;}
        else {double drag=Math.exp(-8*dt);vx*=drag;vy*=drag;if(speed()<.15)stop();}
        x+=vx*dt;y+=vy*dt;clamp();
    }
    private static double edge(double at,double size,double band){if(at<band)return -Math.pow((band-at)/band,2);if(at>size-band)return Math.pow((at-size+band)/band,2);return 0;}
}
