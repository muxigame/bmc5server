package com.muxigame.city;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Bounded visits to already-loaded chunks; empty sections are skipped as a range. */
final class CityScanCursor {
    final List<CityBounds> boxes;
    int part;
    long index, work;
    boolean lostChunk;
    CityScanCursor(List<CityBounds> boxes){this.boxes=List.copyOf(boxes);}
    boolean done(){return part>=boxes.size();}
    int step(ServerLevel level,int budget,Consumer<BlockPos> block,Consumer<CityBounds> air){
        int used=0;
        while(!done()&&used<budget){
            var box=boxes.get(part);
            if(index>=box.volumeLong()){part++;index=0;continue;}
            var pos=box.at(index);used++;work++;
            boolean oneChunk=(box.min().getX()>>4)==(box.max().getX()>>4)&&(box.min().getZ()>>4)==(box.max().getZ()>>4);
            if(pos.getY()<level.getMinBuildHeight()||pos.getY()>=level.getMaxBuildHeight()){
                lostChunk=true;long layer=(long)box.sizeX()*box.sizeZ();
                if(index%layer==0){int end=pos.getY()<level.getMinBuildHeight()?Math.min(box.max().getY(),level.getMinBuildHeight()-1):box.max().getY();index+=((long)end-pos.getY()+1)*layer;}else index++;
                continue;
            }
            var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
            if(chunk==null){lostChunk=true;if(oneChunk){part++;index=0;}else index++;continue;}
            long layer=(long)box.sizeX()*box.sizeZ();
            if(oneChunk&&index%layer==0){
                var section=chunk.getSection(level.getSectionIndex(pos.getY()));
                if(section.hasOnlyAir()){
                    int endY=Math.min(box.max().getY(),pos.getY()|15);
                    var empty=new CityBounds(new BlockPos(box.min().getX(),pos.getY(),box.min().getZ()),new BlockPos(box.max().getX(),endY,box.max().getZ()));
                    index+=empty.volumeLong();air.accept(empty);continue;
                }
            }
            index++;block.accept(pos);
        }
        while(!done()&&index>=boxes.get(part).volumeLong()){part++;index=0;}
        return used;
    }
}
