package com.muxigame.city;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/** A player's temporary selection. Height maps are only queried after every chunk is verified loaded. */
public final class CitySelection {
    private CitySelection(){}
    public static CityBounds manual(ServerLevel level,BlockPos a,BlockPos b,int bottom,int top){
        if(bottom>top)throw new IllegalArgumentException("最低Y不能高于最高Y。");
        CityBounds result=new CityBounds(new BlockPos(Math.min(a.getX(),b.getX()),bottom,Math.min(a.getZ(),b.getZ())),new BlockPos(Math.max(a.getX(),b.getX()),top,Math.max(a.getZ(),b.getZ())));
        result.validateWorld(level);return result;
    }
    public static CityBounds automatic(ServerLevel level,BlockPos a,BlockPos b){
        int bottom=Math.min(a.getY(),b.getY());
        CityBounds footprint=manual(level,a,b,bottom,bottom);
        // A distant city boundary must not require loading its interior. Same two clicks and buttons.
        if(footprint.sizeX()>128||footprint.sizeZ()>128)
            return manual(level,a,b,bottom,level.getMaxBuildHeight()-1);
        // Check the whole footprint before height-map calls; never load/generate a missing chunk.
        for(int cx=footprint.min().getX()>>4;cx<=footprint.max().getX()>>4;cx++)for(int cz=footprint.min().getZ()>>4;cz<=footprint.max().getZ()>>4;cz++)
            if(!level.hasChunk(cx,cz))throw new IllegalArgumentException("选区有未加载区块；请靠近后重试，或改用手动高度。");
        int top=Math.max(a.getY(),b.getY());
        for(int x=footprint.min().getX();x<=footprint.max().getX();x++)for(int z=footprint.min().getZ();z<=footprint.max().getZ();z++)
            top=Math.max(top,level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)-1);
        try{return manual(level,a,b,bottom,top);}catch(IllegalArgumentException ex){throw new IllegalArgumentException("自动高度Y="+bottom+"至"+top+"超出选区限制；请缩小地面范围或手动调整高度。");}
    }
}
