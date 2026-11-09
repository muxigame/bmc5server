package com.muxigame.city;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;

/** Exact membership in a bounded local cube, compressed into vertical runs. No world reads. */
public final class CityOverlay {
    public static ListTag runs(CityData.City city,BlockPos center){
        ListTag out=new ListTag();CityBounds b=city.bounds;
        int low=Math.max(b.min().getY(),center.getY()-8),high=Math.min(b.max().getY(),center.getY()+8);
        for(int x=Math.max(b.min().getX(),center.getX()-8);x<=Math.min(b.max().getX(),center.getX()+8);x++)
        for(int z=Math.max(b.min().getZ(),center.getZ()-8);z<=Math.min(b.max().getZ(),center.getZ()+8);z++){
            int y=low;while(y<=high){int start=y;boolean included=city.includes(new BlockPos(x,y,z));
                while(y<high&&city.includes(new BlockPos(x,y+1,z))==included)y++;
                CompoundTag t=new CompoundTag();t.putLong("Min",new BlockPos(x,start,z).asLong());t.putLong("Max",new BlockPos(x,y,z).asLong());t.putBoolean("Included",included);out.add(t);y++;
            }
        }return merge(merge(out,true),false);
    }
    private static ListTag merge(ListTag input,boolean xAxis){
        java.util.Map<String,CompoundTag> last=new java.util.HashMap<>();ListTag out=new ListTag();
        // Input is ordered X then Z. Sorting makes both merge passes deterministic.
        java.util.List<CompoundTag> sorted=new java.util.ArrayList<>();for(Tag t:input)sorted.add((CompoundTag)t);
        sorted.sort(java.util.Comparator.comparingInt(t->{BlockPos p=BlockPos.of(t.getLong("Min"));return xAxis?p.getX():p.getZ();}));
        for(CompoundTag t:sorted){BlockPos a=BlockPos.of(t.getLong("Min")),b=BlockPos.of(t.getLong("Max"));String key=a.getY()+":"+b.getY()+":"+t.getBoolean("Included")+":"+(xAxis?a.getZ()+":"+b.getZ():a.getX()+":"+b.getX());
            CompoundTag prior=last.get(key);BlockPos end=prior==null?null:BlockPos.of(prior.getLong("Max"));
            if(end!=null&&(xAxis?end.getX()+1==a.getX():end.getZ()+1==a.getZ()))prior.putLong("Max",b.asLong());
            else{CompoundTag copy=t.copy();out.add(copy);last.put(key,copy);}
        }return out;
    }
}
