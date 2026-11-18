package com.muxigame.sevenfold.ui;

/** Low bottom return region; a single eased opacity drives both arrow and pixel veil. */
public final class TreeReturnEdge {
    private double progress;
    public static int height(int viewportHeight){return Math.clamp((int)Math.round(viewportHeight*.065),14,24);}
    public static boolean contains(double x,double y,int width,int height){return x>=0&&x<width&&y>=height-height(height)&&y<height;}
    public static boolean available(double cameraY,boolean overlay){return overlay||cameraY>=-.001;}
    public void update(boolean hovered,double seconds){progress=Math.clamp(progress+Math.max(0,seconds)*(hovered?1:-2),0,1);}
    public double opacity(){return progress*progress*(3-2*progress);}
    public void reset(){progress=0;}
}
