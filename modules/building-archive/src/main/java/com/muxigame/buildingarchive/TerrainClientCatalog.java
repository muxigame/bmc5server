package com.muxigame.buildingarchive;

import com.google.gson.JsonParser;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/** Client-only cache. Translation IO belongs to resource reload's prepare executor. */
final class TerrainClientCatalog {
    private static Map<String,String> chinese=Map.of();private static TerrainClientCatalog cached;
    static final SimplePreparableReloadListener<Map<String,String>> RELOAD=new SimplePreparableReloadListener<>() {
        protected Map<String,String> prepare(ResourceManager resources,ProfilerFiller profiler){
            var names=new HashMap<String,String>();
            for(String namespace:resources.getNamespaces())for(var resource:resources.getResourceStack(ResourceLocation.fromNamespaceAndPath(namespace,"lang/zh_cn.json")))
                try(var reader=resource.openAsReader()){
                    JsonParser.parseReader(reader).getAsJsonObject().entrySet().forEach(entry->{if(entry.getValue().isJsonPrimitive())names.put(entry.getKey(),entry.getValue().getAsString());});
                }catch(Exception ignored){}
            return names;
        }
        protected void apply(Map<String,String> names,ResourceManager resources,ProfilerFiller profiler){chinese=names;cached=null;}
    };
    static TerrainClientCatalog current(){if(cached==null)cached=new TerrainClientCatalog();return cached;}
    static String label(Block block){
        String translated=chinese.get(block.getDescriptionId());if(translated!=null)return translated;
        String local=block.getName().getString();for(int i=0;i<local.length();i++){char c=local.charAt(i);if(c>='\u3400'&&c<='\u9fff')return local;}
        return local+"（未提供中文）";
    }
    final TerrainSearchIndex<Block> index=new TerrainSearchIndex<>();
    private final Iterator<Block> source=BuiltInRegistries.BLOCK.iterator();private int visited;
    int advance(int maximum,long deadline){
        int steps=0;
        while(!index.ready()&&steps<maximum&&source.hasNext()&&System.nanoTime()<deadline){
            Block block=source.next();visited++;steps++;
            if(block.asItem()!=Items.AIR)index.add(BuiltInRegistries.BLOCK.getKey(block).toString(),label(block),block,TerrainTool.usableTarget(block));
        }
        if(!source.hasNext())index.finish();return steps;
    }
    int visited(){return visited;}
    int total(){return BuiltInRegistries.BLOCK.size();}
}
