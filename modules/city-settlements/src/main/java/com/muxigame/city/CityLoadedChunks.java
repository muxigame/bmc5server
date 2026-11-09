package com.muxigame.city;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** A server-thread index of chunks already loaded by the game. Never obtains a chunk ticket. */
@EventBusSubscriber(modid=CityMod.ID)
public final class CityLoadedChunks {
    private static final Map<ServerLevel,Set<Long>> LOADED=new WeakHashMap<>();
    @SubscribeEvent public static void load(ChunkEvent.Load event){if(event.getLevel() instanceof ServerLevel l)LOADED.computeIfAbsent(l,k->new TreeSet<>()).add(event.getChunk().getPos().toLong());}
    @SubscribeEvent public static void unload(ChunkEvent.Unload event){if(event.getLevel() instanceof ServerLevel l){var set=LOADED.get(l);if(set!=null)set.remove(event.getChunk().getPos().toLong());}}
    static List<CityBounds> boxes(ServerLevel l,CityBounds bounds){
        var out=new ArrayList<CityBounds>();
        for(long key:LOADED.getOrDefault(l,Set.of())){
            var p=new ChunkPos(key);
            if(p.x<(bounds.min().getX()>>4)||p.x>(bounds.max().getX()>>4)||p.z<(bounds.min().getZ()>>4)||p.z>(bounds.max().getZ()>>4)||l.getChunkSource().getChunkNow(p.x,p.z)==null)continue;
            var box=new CityBounds(new BlockPos(Math.max(bounds.min().getX(),p.getMinBlockX()),bounds.min().getY(),Math.max(bounds.min().getZ(),p.getMinBlockZ())),new BlockPos(Math.min(bounds.max().getX(),p.getMaxBlockX()),bounds.max().getY(),Math.min(bounds.max().getZ(),p.getMaxBlockZ())));
            out.add(box);
        }
        return out;
    }
    /** Small selections retain their exact old traversal, including explicit unloaded status. */
    static List<CityBounds> scanBoxes(ServerLevel l,CityBounds bounds){return bounds.smallSelection()?List.of(bounds):boxes(l,bounds);}
}
