package com.muxigame.zaluokeforging.forging;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Shared geometry for the visible blade, hit testing and selection shape. */
public final class ForgeTarget {
    public static final double WIDTH=.85*3/16, START=-.85*.25, STEP=.85*3/16, TOP=.94+.85/16;
    public static double halfWidth(net.minecraft.world.item.ItemStack stack,double z) {
        int j=Math.clamp((int)((z-START)/(.85/16)),0,11);
        double f=Math.clamp(Workpiece.get(stack,"Hits")/(double)Workpiece.requiredHits(stack),0,1);
        double old=j<10?3:2, end=j<9?1.8:j==9?1.35:j==10?.9:.45;
        return .85/16*(old+(end-old)*f);
    }
    public static int zone(net.minecraft.world.item.ItemStack stack,BlockPos pos,Direction facing,Vec3 hit,Direction face) {
        int region=zone(pos,facing,hit,face);
        double angle=Math.toRadians(-facing.toYRot()),dx=hit.x-pos.getX()-.5,dz=hit.z-pos.getZ()-.5;
        double x=Math.cos(angle)*dx-Math.sin(angle)*dz,z=Math.sin(angle)*dx+Math.cos(angle)*dz;
        if(Workpiece.axe(stack)) {
            if(face!=Direction.UP || Math.abs(hit.y-pos.getY()-TOP)>.035) return -1;
            for(int i=0;i<2;i++) { double[] r=bounds(stack,i);if(x>=r[0] && x<=r[1] && z>=r[2] && z<=r[3]) return i; }
            return -1;
        }
        return Math.abs(x)>halfWidth(stack,z)+.001?-1:region;
    }
    public static int zone(BlockPos pos,Direction facing,Vec3 hit,Direction face) {
        if(face!=Direction.UP || Math.abs(hit.y-pos.getY()-TOP)>.035) return -1;
        double angle=Math.toRadians(-facing.toYRot()),dx=hit.x-pos.getX()-.5,dz=hit.z-pos.getZ()-.5;
        double x=Math.cos(angle)*dx-Math.sin(angle)*dz,z=Math.sin(angle)*dx+Math.cos(angle)*dz;
        if(Math.abs(x)>WIDTH+.001 || z<START || z>START+4*STEP) return -1;
        return Math.min(3,(int)((z-START)/STEP));
    }
    public static double[] bounds(net.minecraft.world.item.ItemStack stack,int zone) {
        if(Workpiece.axe(stack)) return new double[]{-.85*5/16,-.85*2/16,.85*(-2+zone*5)/16,.85*(3+zone*5)/16};
        double a=START+zone*STEP+.006,b=a+STEP-.012,w=halfWidth(stack,b)-.003;
        return new double[]{-w,w,a,b};
    }
    public static Vec3 center(net.minecraft.world.item.ItemStack stack,BlockPos pos,Direction facing,int zone) {
        double[] r=bounds(stack,zone);double x=(r[0]+r[1])/2,z=(r[2]+r[3])/2,a=Math.toRadians(-facing.toYRot());
        return new Vec3(pos.getX()+.5+Math.cos(a)*x+Math.sin(a)*z,pos.getY()+TOP,pos.getZ()+.5-Math.sin(a)*x+Math.cos(a)*z);
    }
    public static Vec3 center(BlockPos pos,Direction facing,int zone) {
        double a=Math.toRadians(-facing.toYRot()),z=START+(zone+.5)*STEP;
        return new Vec3(pos.getX()+.5+Math.sin(a)*z,pos.getY()+TOP,pos.getZ()+.5+Math.cos(a)*z);
    }
}
