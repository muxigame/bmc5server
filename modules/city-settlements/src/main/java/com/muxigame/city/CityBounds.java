package com.muxigame.city;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Inclusive, bounded volumes. All arithmetic is widened before subtraction. */
public record CityBounds(BlockPos min, BlockPos max) {
    public static final int MAX_VOLUME = 131072;
    public CityBounds {
        min = min.immutable(); max = max.immutable();
        long x=(long)max.getX()-min.getX()+1, y=(long)max.getY()-min.getY()+1, z=(long)max.getZ()-min.getZ()+1;
        if(x<1||y<1||z<1||Math.abs((long)min.getX())>30_000_000L||Math.abs((long)max.getX())>30_000_000L||Math.abs((long)min.getZ())>30_000_000L||Math.abs((long)max.getZ())>30_000_000L||min.getY() < -2048 || max.getY() > 2047)
            throw new IllegalArgumentException("选区坐标超出世界可表示范围。");
        try { Math.multiplyExact(Math.multiplyExact(x,y),z); } catch(ArithmeticException ex) {throw new IllegalArgumentException("选区体积无法表示。",ex);}
    }
    public static CityBounds between(BlockPos a, BlockPos b) {
        return new CityBounds(new BlockPos(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ())),
            new BlockPos(Math.max(a.getX(),b.getX()),Math.max(a.getY(),b.getY()),Math.max(a.getZ(),b.getZ())));
    }
    public int sizeX(){return max.getX()-min.getX()+1;}
    public int sizeZ(){return max.getZ()-min.getZ()+1;}
    public long volumeLong(){return (long)sizeX()*sizeZ()*((long)max.getY()-min.getY()+1);}
    public boolean smallSelection(){return sizeX()<=128&&sizeZ()<=128&&(long)max.getY()-min.getY()+1<=128&&volumeLong()<=MAX_VOLUME;}
    /** Only bounded, single-building operations may use an int index. */
    public int volume(){if(volumeLong()>Integer.MAX_VALUE)throw new IllegalArgumentException("此操作需要分批处理大型区域。");return (int)volumeLong();}
    public void validateBuilding(){if(!smallSelection())throw new IllegalArgumentException("单栋建筑登记最多131072格；城市领地不受此项限制。");}
    public boolean contains(BlockPos p){return p.getX()>=min.getX()&&p.getX()<=max.getX()&&p.getY()>=min.getY()&&p.getY()<=max.getY()&&p.getZ()>=min.getZ()&&p.getZ()<=max.getZ();}
    public boolean intersects(CityBounds b){return min.getX()<=b.max.getX()&&max.getX()>=b.min.getX()&&min.getY()<=b.max.getY()&&max.getY()>=b.min.getY()&&min.getZ()<=b.max.getZ()&&max.getZ()>=b.min.getZ();}
    public BlockPos at(int index){return at((long)index);}
    public BlockPos at(long index){if(index<0||index>=volumeLong())throw new IllegalArgumentException("区域游标越界。");return min.offset((int)(index%sizeX()),(int)(index/((long)sizeX()*sizeZ())),(int)(index/sizeX()%sizeZ()));}
    public void validateWorld(ServerLevel level){
        if(min.getY()<level.getMinBuildHeight()||max.getY()>=level.getMaxBuildHeight()||!level.getWorldBorder().isWithinBounds(min)||!level.getWorldBorder().isWithinBounds(max))
            throw new IllegalArgumentException("选区超出世界边界或建造高度。");
    }
}
