package com.muxigame.buildingarchive;

import java.util.ArrayDeque;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;

/** Per-placement filtering; the authoritative archive and all upper air remain intact. */
public final class ArchiveFoundation {
    private ArchiveFoundation() {}

    static boolean air(CompoundTag state) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(state.getString("Name"))).defaultBlockState().isAir();
    }

    /** Consecutive bottom layers: connected full cubes, with no horizontally enclosed air. */
    public static int detectLayers(CompoundTag tag) {
        var size=ArchiveData.validate(tag);
        int w=size.getX(),d=size.getZ(),height=size.getY();
        var palette=tag.getList("palette",Tag.TAG_COMPOUND);
        boolean[] isAir=new boolean[palette.size()],full=new boolean[palette.size()];
        var lookup=BuiltInRegistries.BLOCK.asLookup();
        for(int i=0;i<palette.size();i++){
            var state=NbtUtils.readBlockState(lookup,palette.getCompound(i));
            isAir[i]=state.isAir();
            full[i]=!state.isAir()&&state.getFluidState().isEmpty()&&state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE,BlockPos.ZERO);
        }
        byte[][] layers=new byte[height][w*d];
        for(var value:tag.getList("blocks",Tag.TAG_COMPOUND)){
            var block=(CompoundTag)value;var p=block.getList("pos",Tag.TAG_INT);int state=block.getInt("state");
            layers[p.getInt(1)][p.getInt(2)*w+p.getInt(0)]=(byte)(isAir[state]?0:full[state]?1:2);
        }
        int result=0;boolean started=false;
        for(int y=0;y<height;y++){
            byte[] layer=layers[y];int count=0,first=-1;
            for(int i=0;i<layer.length;i++){if(layer[i]==2)return result;if(layer[i]==1){count++;first=i;}}
            if(count==0){if(started)break;continue;}
            started=true;
            boolean[] connected=flood(layer,w,d,first,false);
            int visited=0;for(boolean b:connected)if(b)visited++;
            if(visited!=count)break;
            boolean[] outside=flood(layer,w,d,-1,true);
            boolean hole=false;for(int i=0;i<layer.length;i++)if(layer[i]==0&&!outside[i]){hole=true;break;}
            if(hole)break;
            result=y+1;
        }
        return result;
    }

    private static boolean[] flood(byte[] layer,int w,int d,int seed,boolean outside){
        boolean[] seen=new boolean[layer.length];var queue=new ArrayDeque<Integer>();
        if(outside){for(int z=0;z<d;z++)for(int x=0;x<w;x++)if((x==0||z==0||x==w-1||z==d-1)&&layer[z*w+x]==0){int i=z*w+x;seen[i]=true;queue.add(i);}}
        else {seen[seed]=true;queue.add(seed);}
        while(!queue.isEmpty()){
            int i=queue.removeFirst(),x=i%w,z=i/w;
            for(int n:new int[]{x>0?i-1:-1,x+1<w?i+1:-1,z>0?i-w:-1,z+1<d?i+w:-1})
                if(n>=0&&!seen[n]&&(outside?layer[n]==0:layer[n]==1)){seen[n]=true;queue.addLast(n);}
        }
        return seen;
    }

    public static CompoundTag prepare(CompoundTag source,int layers){
        var size=ArchiveData.validate(source);
        if(layers<0||layers>size.getY())throw new IllegalArgumentException("地基层数必须在0到建筑高度之间。");
        if(layers==0)return source;
        var palette=source.getList("palette",Tag.TAG_COMPOUND);boolean[] isAir=new boolean[palette.size()];
        for(int i=0;i<palette.size();i++)isAir[i]=air(palette.getCompound(i));
        CompoundTag result=source.copy();ListTag filtered=new ListTag();
        for(var value:result.getList("blocks",Tag.TAG_COMPOUND)){
            var block=(CompoundTag)value;
            if(block.getList("pos",Tag.TAG_INT).getInt(1)<layers&&isAir[block.getInt("state")])continue;
            filtered.add(block);
        }
        // Empty structures are legal archives, but an all-air manual filter must not reach place().
        if(filtered.isEmpty())throw new IllegalArgumentException("地基设置过滤了全部方块，请降低层数或关闭保护。");
        result.put("blocks",filtered);return result;
    }
}
