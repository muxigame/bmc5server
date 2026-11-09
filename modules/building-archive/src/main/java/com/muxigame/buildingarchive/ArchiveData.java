package com.muxigame.buildingarchive;

import com.google.gson.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Vanilla structure NBT is the authoritative archive; JSON is a derived analysis. */
public final class ArchiveData {
    public static final int MAX_EDGE=128, MAX_VOLUME=32768;
    public static final long MAX_BYTES=32L*1024*1024;
    public record Bounds(BlockPos origin, Vec3i size) {}
    public static Bounds bounds(BlockPos a, BlockPos b) {
        long x=Math.abs((long)a.getX()-b.getX())+1, y=Math.abs((long)a.getY()-b.getY())+1, z=Math.abs((long)a.getZ()-b.getZ())+1;
        checkSize(x,y,z);
        return new Bounds(new BlockPos(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ())),new Vec3i((int)x,(int)y,(int)z));
    }
    private static void checkSize(long x,long y,long z) {
        if(x<1||y<1||z<1||x>MAX_EDGE||y>MAX_EDGE||z>MAX_EDGE||x*y*z>MAX_VOLUME)
            throw new IllegalArgumentException("选区每边最多128格，总体积最多32768格（含空气）。");
    }
    public static Path file(Path root,String name) throws IOException {
        if(!name.matches("[\\p{L}\\p{N}_-]{1,64}")) throw new IllegalArgumentException("名称支持1–64个中文、字母、数字、下划线或短横线。");
        Files.createDirectories(root);
        Path p=root.resolve(name+".nbt");
        if(Files.isSymbolicLink(p)) throw new IllegalArgumentException("不读取符号链接档案。");
        return p;
    }
    public static void checkArea(ServerLevel level,BlockPos origin,Vec3i size) {
        checkSize(size.getX(),size.getY(),size.getZ());
        BlockPos end=origin.offset(size.getX()-1,size.getY()-1,size.getZ()-1);
        if(level.isOutsideBuildHeight(origin)||level.isOutsideBuildHeight(end)||!level.getWorldBorder().isWithinBounds(origin)||!level.getWorldBorder().isWithinBounds(end))
            throw new IllegalArgumentException("区域超出世界边界或建筑高度。");
        for(int x=origin.getX()>>4;x<=end.getX()>>4;x++) for(int z=origin.getZ()>>4;z<=end.getZ()>>4;z++)
            if(!level.hasChunk(x,z)) throw new IllegalArgumentException("区域包含未加载区块，请先靠近该区域。");
    }
    public static CompoundTag capture(ServerLevel level,BlockPos origin,Vec3i size) {
        checkArea(level,origin,size);
        StructureTemplate template=new StructureTemplate();
        template.fillFromWorld(level,origin,size,false,null);
        CompoundTag tag=template.save(new CompoundTag());
        tag.putInt("DataVersion",SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        tag.putString("ArchiveCreated",Instant.now().toString());
        tag.putString("ArchiveDimension",level.dimension().location().toString());
        tag.putIntArray("ArchiveOrigin",new int[]{origin.getX(),origin.getY(),origin.getZ()});
        checkRuralLinks(tag,size);
        return tag;
    }
    public static void writeNew(Path path,CompoundTag data) throws IOException {
        if(Files.exists(path)) throw new IllegalArgumentException("同名档案已存在，请使用新名称。");
        Path tmp=Files.createTempFile(path.getParent(),"archive-",".tmp");
        try { NbtIo.writeCompressed(data,tmp); Files.move(tmp,path); }
        finally { Files.deleteIfExists(tmp); }
    }
    static void checkRuralLinks(CompoundTag tag,Vec3i size){
        for(var entry:tag.getList("blocks",Tag.TAG_COMPOUND)){
            CompoundTag block=(CompoundTag)entry;var nbt=block.getCompound("nbt");
            if(!nbt.getString("id").equals("city_settlements:rural_link")||!nbt.contains("RuralWorkOffset"))continue;
            int[] offset=nbt.getIntArray("RuralWorkOffset");var pos=block.getList("pos",Tag.TAG_INT);
            if(offset.length!=3)throw new IllegalArgumentException("NPC储藏箱关联格式无效");
            int[] limit={size.getX(),size.getY(),size.getZ()};
            for(int i=0;i<3;i++)if(Math.abs((long)offset[i])>32||(long)pos.getInt(i)+offset[i]<0||(long)pos.getInt(i)+offset[i]>=limit[i])throw new IllegalArgumentException("NPC储藏箱关联的工作点在选区外，请将工作点一起选入建筑。");
        }
    }
    static void transformRuralLink(CompoundTag block,int mode){var nbt=block.getCompound("nbt");if(!nbt.getString("id").equals("city_settlements:rural_link"))return;int[] a=nbt.getIntArray("RuralWorkOffset");if(a.length!=3)return;nbt.putIntArray("RuralWorkOffset",mode==0?new int[]{-a[2],a[1],a[0]}:mode==1?new int[]{a[2],a[1],-a[0]}:new int[]{-a[0],a[1],a[2]});}
    public static CompoundTag read(Path path) throws IOException {
        if(Files.size(path)>MAX_BYTES) throw new IllegalArgumentException("档案文件过大。");
        return NbtIo.readCompressed(path,NbtAccounter.create(MAX_BYTES));
    }
    /** Reject missing mods/properties rather than silently replacing unknown blocks with air. */
    public static Vec3i validate(CompoundTag tag) {
        var size=tag.getList("size",Tag.TAG_INT);
        if(size.size()!=3) throw new IllegalArgumentException("不是有效的原版结构NBT：缺少size。");
        checkSize(size.getInt(0),size.getInt(1),size.getInt(2));
        if(tag.contains("palettes")) throw new IllegalArgumentException("暂不支持多随机调色板结构，请导出单一建筑。");
        if(tag.getList("entities",Tag.TAG_COMPOUND).size()>0) throw new IllegalArgumentException("此版本仅导入方块；请使用不含实体的建筑档案。");
        int version=tag.getInt("DataVersion");
        if(version!=SharedConstants.getCurrentVersion().getDataVersion().getVersion())
            throw new IllegalArgumentException("建筑数据版本不同，请先在相同的Minecraft版本导出。");
        var palette=tag.getList("palette",Tag.TAG_COMPOUND);
        if(palette.isEmpty()||palette.size()>MAX_VOLUME) throw new IllegalArgumentException("方块调色板无效。");
        for(int i=0;i<palette.size();i++) {
            var state=palette.getCompound(i);
            var id=ResourceLocation.tryParse(state.getString("Name"));
            if(id==null||!BuiltInRegistries.BLOCK.containsKey(id)) throw new IllegalArgumentException("缺少方块/模组："+state.getString("Name"));
            var block=BuiltInRegistries.BLOCK.get(id);
            var props=state.getCompound("Properties");
            for(String key:props.getAllKeys()) {
                var property=block.getStateDefinition().getProperty(key);
                if(property==null||property.getValue(props.getString(key)).isEmpty()) throw new IllegalArgumentException("方块状态不兼容："+id+" / "+key);
            }
        }
        var blocks=tag.getList("blocks",Tag.TAG_COMPOUND);
        if(blocks.isEmpty()||blocks.size()>MAX_VOLUME) throw new IllegalArgumentException("方块列表无效。");
        Set<BlockPos> seen=new HashSet<>();
        for(int i=0;i<blocks.size();i++) {
            var entry=blocks.getCompound(i); var p=entry.getList("pos",Tag.TAG_INT);
            if(p.size()!=3||p.getInt(0)<0||p.getInt(1)<0||p.getInt(2)<0||p.getInt(0)>=size.getInt(0)||p.getInt(1)>=size.getInt(1)||p.getInt(2)>=size.getInt(2)) throw new IllegalArgumentException("建筑含越界坐标。");
            if(!seen.add(new BlockPos(p.getInt(0),p.getInt(1),p.getInt(2)))) throw new IllegalArgumentException("建筑含重复坐标。");
            if(!entry.contains("state",Tag.TAG_INT)||entry.getInt("state")<0||entry.getInt("state")>=palette.size()) throw new IllegalArgumentException("建筑含无效调色板索引。");
        }
        return new Vec3i(size.getInt(0),size.getInt(1),size.getInt(2));
    }
    public static void place(ServerLevel level,BlockPos origin,CompoundTag tag) {
        Vec3i size=validate(tag); checkArea(level,origin,size);
        StructureTemplate template=new StructureTemplate();
        template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK),tag);
        var blocks=tag.getList("blocks",Tag.TAG_COMPOUND);
        for(int i=0;i<blocks.size();i++) {
            var pos=blocks.getCompound(i).getList("pos",Tag.TAG_INT);
            net.minecraft.world.Clearable.tryClear(level.getBlockEntity(origin.offset(pos.getInt(0),pos.getInt(1),pos.getInt(2))));
        }
        var settings=new StructurePlaceSettings().setIgnoreEntities(true).setKnownShape(true).setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
        if(!template.placeInWorld(level,origin,origin,settings,level.random,2|16)) throw new IllegalStateException("建筑放置失败。");
    }
    /** Rotate a copy clockwise around Y, normalizing the minimum corner to zero. */
    public static CompoundTag rotate(CompoundTag source, net.minecraft.core.HolderLookup<net.minecraft.world.level.block.Block> lookup, boolean clockwise) {
        Vec3i size=validate(source);CompoundTag result=source.copy();
        var rotation=clockwise?net.minecraft.world.level.block.Rotation.CLOCKWISE_90:net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90;
        ListTag palette=new ListTag();for(var entry:source.getList("palette",Tag.TAG_COMPOUND))palette.add(NbtUtils.writeBlockState(NbtUtils.readBlockState(lookup,(CompoundTag)entry).rotate(rotation)));result.put("palette",palette);
        ListTag dimensions=new ListTag();for(int n:new int[]{size.getZ(),size.getY(),size.getX()})dimensions.add(IntTag.valueOf(n));result.put("size",dimensions);
        for(var entry:result.getList("blocks",Tag.TAG_COMPOUND)){CompoundTag block=(CompoundTag)entry;var old=block.getList("pos",Tag.TAG_INT);int x=old.getInt(0),y=old.getInt(1),z=old.getInt(2);ListTag pos=new ListTag();for(int n:new int[]{clockwise?size.getZ()-1-z:z,y,clockwise?x:size.getX()-1-x})pos.add(IntTag.valueOf(n));block.put("pos",pos);transformRuralLink(block,clockwise?0:1);}
        validate(result);return result;
    }
    /** Reflect across the current east-west axis; height and north-south positions stay unchanged. */
    public static CompoundTag mirror(CompoundTag source, net.minecraft.core.HolderLookup<net.minecraft.world.level.block.Block> lookup) {
        Vec3i size=validate(source);CompoundTag result=source.copy();
        ListTag palette=new ListTag();
        for(var entry:source.getList("palette",Tag.TAG_COMPOUND))palette.add(NbtUtils.writeBlockState(NbtUtils.readBlockState(lookup,(CompoundTag)entry).mirror(net.minecraft.world.level.block.Mirror.FRONT_BACK)));
        result.put("palette",palette);
        for(var entry:result.getList("blocks",Tag.TAG_COMPOUND)){
            CompoundTag block=(CompoundTag)entry;var old=block.getList("pos",Tag.TAG_INT);ListTag pos=new ListTag();
            for(int n:new int[]{size.getX()-1-old.getInt(0),old.getInt(1),old.getInt(2)})pos.add(IntTag.valueOf(n));block.put("pos",pos);transformRuralLink(block,2);
        }
        validate(result);return result;
    }
    public static CompoundTag emptyRegion(CompoundTag source){
        validate(source);CompoundTag result=source.copy();ListTag palette=new ListTag();CompoundTag air=new CompoundTag();air.putString("Name","minecraft:air");palette.add(air);result.put("palette",palette);
        for(var entry:result.getList("blocks",Tag.TAG_COMPOUND)){CompoundTag b=(CompoundTag)entry;b.putInt("state",0);b.remove("nbt");}return result;
    }
    /** Exact full-block occupancy runs, capped to bound rendering and packet size. */
    public static int[] projectionRuns(CompoundTag tag){
        Vec3i size=validate(tag);boolean[][][] solid=new boolean[size.getY()][size.getZ()][size.getX()];var palette=tag.getList("palette",Tag.TAG_COMPOUND);
        for(var value:tag.getList("blocks",Tag.TAG_COMPOUND)){CompoundTag b=(CompoundTag)value;String id=palette.getCompound(b.getInt("state")).getString("Name");if(id.equals("minecraft:air")||id.equals("minecraft:cave_air")||id.equals("minecraft:void_air"))continue;var pos=b.getList("pos",Tag.TAG_INT);solid[pos.getInt(1)][pos.getInt(2)][pos.getInt(0)]=true;}
        java.util.ArrayList<Integer> runs=new java.util.ArrayList<>();
        for(int y=0;y<size.getY();y++)for(int z=0;z<size.getZ();z++)for(int x=0;x<size.getX();x++){if(!solid[y][z][x])continue;int start=x;while(x+1<size.getX()&&solid[y][z][x+1])x++;if(runs.size()/6>=2048)return new int[0];for(int v:new int[]{start,y,z,x+1,y+1,z+1})runs.add(v);}
        return runs.stream().mapToInt(Integer::intValue).toArray();
    }
    public static int occupied(ServerLevel level,BlockPos origin,CompoundTag tag) {
        Vec3i size=validate(tag);checkArea(level,origin,size);int count=0;
        var blocks=tag.getList("blocks",Tag.TAG_COMPOUND);
        for(int i=0;i<blocks.size();i++) {
            var p=blocks.getCompound(i).getList("pos",Tag.TAG_INT);
            if(!level.getBlockState(origin.offset(p.getInt(0),p.getInt(1),p.getInt(2))).isAir()) count++;
        }
        return count;
    }
    public static JsonObject analysis(CompoundTag tag) {
        Vec3i size=validate(tag);var palette=tag.getList("palette",Tag.TAG_COMPOUND);var blocks=tag.getList("blocks",Tag.TAG_COMPOUND);
        Map<String,Integer> counts=new TreeMap<>(), mods=new TreeMap<>();
        Map<Integer,Map<String,Integer>> layers=new TreeMap<>();int[] states=new int[palette.size()];int air=0,be=0;
        for(int i=0;i<blocks.size();i++) {
            var e=blocks.getCompound(i); int idx=e.getInt("state"); states[idx]++;
            String id=palette.getCompound(idx).getString("Name");
            boolean isAir=BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState().isAir();
            if(isAir) {air++;continue;}
            counts.merge(id,1,Integer::sum);mods.merge(id.split(":")[0],1,Integer::sum);
            layers.computeIfAbsent(e.getList("pos",Tag.TAG_INT).getInt(1),k->new TreeMap<>()).merge(id,1,Integer::sum);
            if(e.contains("nbt")) be++;
        }
        Gson gson=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();JsonObject out=new JsonObject();
        out.addProperty("schema",1);out.addProperty("minecraft","1.21.1");out.addProperty("created",tag.getString("ArchiveCreated"));
        out.addProperty("dimension",tag.getString("ArchiveDimension"));out.add("origin",gson.toJsonTree(tag.getIntArray("ArchiveOrigin")));
        out.add("size",gson.toJsonTree(new int[]{size.getX(),size.getY(),size.getZ()}));
        out.addProperty("volume",size.getX()*size.getY()*size.getZ());out.addProperty("recordedBlocks",blocks.size());out.addProperty("air",air);out.addProperty("nonAir",blocks.size()-air);out.addProperty("blockEntities",be);
        out.addProperty("scope","Block counts, not crafting/material costs; entities and scheduled ticks excluded. Style labels are user-authored.");
        out.add("blocks",gson.toJsonTree(counts));out.add("mods",gson.toJsonTree(mods));out.add("layers",gson.toJsonTree(layers));
        JsonArray variants=new JsonArray();
        for(int i=0;i<palette.size();i++) {
            JsonObject state=new JsonObject();var p=palette.getCompound(i);state.addProperty("id",p.getString("Name"));state.addProperty("count",states[i]);
            JsonObject props=new JsonObject();for(String key:new TreeSet<>(p.getCompound("Properties").getAllKeys()))props.addProperty(key,p.getCompound("Properties").getString(key));
            state.add("properties",props);variants.add(state);
        }
        out.add("states",variants);out.add("styleTags",new JsonArray());out.addProperty("styleNotes","");return out;
    }
}


