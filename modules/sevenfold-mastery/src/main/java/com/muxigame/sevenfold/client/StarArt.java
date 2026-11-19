package com.muxigame.sevenfold.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.muxigame.sevenfold.SevenfoldMastery;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

final class StarArt {
    static final ResourceLocation SKY=texture("sky"),MIST=texture("mist"),GLOW=texture("glow"),TRUNK=texture("trunk");
    static ResourceLocation texture(String id){return ResourceLocation.fromNamespaceAndPath(SevenfoldMastery.ID,"textures/gui/stars/"+id+".png");}
    static int alpha(int rgb,double opacity){return ((int)(Math.clamp(opacity,0,1)*255)<<24)|(rgb&0xFFFFFF);}
    static int mix(int a,int b,double f){int r=(int)(((a>>16)&255)*(1-f)+((b>>16)&255)*f),g=(int)(((a>>8)&255)*(1-f)+((b>>8)&255)*f),bl=(int)((a&255)*(1-f)+(b&255)*f);return (r<<16)|(g<<8)|bl;}
    static void sprite(GuiGraphics g,ResourceLocation texture,double x,double y,double width,double height,int sw,int sh,int tint,double opacity){
        if(opacity<=.002)return;
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        g.setColor(((tint>>16)&255)/255f,((tint>>8)&255)/255f,(tint&255)/255f,(float)Math.clamp(opacity,0,1));
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale((float)(width/sw),(float)(height/sh),1);
        g.blit(texture,0,0,0,0,sw,sh,sw,sh);g.pose().popPose();g.setColor(1,1,1,1);
    }
    static void glow(GuiGraphics g,double x,double y,double radius,int color,double strength){sprite(g,GLOW,x-radius,y-radius,radius*2,radius*2,64,64,color,strength);}
    static void line(GuiGraphics g,double x1,double y1,double x2,double y2,int color,double opacity,int thickness,double fraction){
        double dx=x2-x1,dy=y2-y1;int steps=Math.max(1,(int)Math.ceil(Math.max(Math.abs(dx),Math.abs(dy))));
        int end=(int)(steps*Math.clamp(fraction,0,1)),argb=alpha(color,opacity);
        for(int i=0;i<=end;i++){int x=(int)Math.round(x1+dx*i/steps),y=(int)Math.round(y1+dy*i/steps);g.fill(x-thickness/2,y-thickness/2,x+(thickness+1)/2,y+(thickness+1)/2,argb);}
    }
    static void ellipse(GuiGraphics g,double x,double y,double rx,double ry,int color,double opacity){
        for(int i=0;i<48;i++){double a=i*Math.PI/24,b=(i+1)*Math.PI/24;line(g,x+Math.cos(a)*rx,y+Math.sin(a)*ry,x+Math.cos(b)*rx,y+Math.sin(b)*ry,color,opacity,1,1);}
    }
    static void star(GuiGraphics g,double x,double y,int color,boolean lit,double opacity,double pulse,boolean crying){
        star(g,x,y,color,lit?1:0,opacity,pulse,crying);
    }
    static void star(GuiGraphics g,double x,double y,int color,double light,double opacity,double pulse,boolean crying){
        glow(g,x,y,crying?56:41,color,opacity*(.28+.32*light));
        glow(g,x,y,crying?24:18,mix(color,0xFFFFFF,.35),opacity*(.38+.42*light));
        int cx=(int)x,cy=(int)y,r=crying?6:4;
        int bright=mix(mix(color,0x152035,.56),mix(color,0xFFFFFF,.78),light);
        g.fill(cx-1,cy-r-3,cx+1,cy+r+4,alpha(color,opacity*.65));
        g.fill(cx-r-3,cy-1,cx+r+4,cy+1,alpha(color,opacity*.65));
        for(int yy=-r;yy<=r;yy++){int half=Math.max(0,r-Math.abs(yy));g.fill(cx-half,cy+yy,cx+half+1,cy+yy+1,alpha(bright,opacity));}
        g.fill(cx-1,cy-1,cx+2,cy+2,alpha(mix(mix(color,0x152035,.3),0xFFFFFF,light),opacity));
        if(light>.002){ellipse(g,x,y,crying?16:12,crying?7:5,color,opacity*(.25+pulse*.15)*light);}
        if(crying){
            for(int i=0;i<5;i++){int ty=cy+10+i*3;g.fill(cx,ty,cx+1,ty+2,alpha(0xA9D9F7,opacity*(.7-i*.11)));}
        }
    }
}
