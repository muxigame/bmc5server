package com.muxigame.city;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Server-thread operations shared by commands, network and integration tests. */
public final class CityService {
    private CityService(){}
    public record Statistics(Map<String,Long> blocks,long nonAir,long excluded,long unloaded,long revision,long tick) {
        public Statistics {blocks=Map.copyOf(blocks);}
        public boolean complete(){return unloaded==0;}
    }
    private static final Map<UUID,Statistics> STATS=new HashMap<>();
    private static final LinkedHashMap<UUID,Scan> SCANS=new LinkedHashMap<>();
    private static final Map<UUID,Undo> UNDO=new HashMap<>();
    private record Undo(UUID city,long revision,Map<Long,Boolean> membership,BlockPos pos,BlockState before,BlockState after,CityBounds bounds,Set<Long> excluded,List<CityBounds> areas,List<long[]> unknown){
        Undo(UUID city,long revision,Map<Long,Boolean> membership,BlockPos pos,BlockState before,BlockState after){this(city,revision,membership,pos,before,after,null,null,null,null);}
    }
    private static final class Scan {
        final CityData.City city;final long revision;final Map<String,Long> blocks=new TreeMap<>();long nonAir,known;CityScanCursor cursor;
        Scan(CityData.City city){this.city=city;revision=city.revision;}
    }
    public static void clear(){STATS.clear();SCANS.clear();UNDO.clear();}
    public static void delete(CityData data,CityData.City city,ServerPlayer player,long revision,String confirmedName){
        checkEditor(player,city,revision);
        if(data.require(city.id)!=city||!city.name.equals(confirmedName))throw new IllegalArgumentException("城市信息已变化，请重新确认删除。");
        CityEconomy.require(!CityTrade.managed(player.server,city.id),"已登记有限经济的聚落不能直接删除；请先由管理员安排存档迁移，避免遗失路线和经营资产。");
        CityEconomy.require(!CityEconomy.get(player.server).referencesCity(city.id)&&city.warehouses.keySet().stream().noneMatch(CityEconomy.get(player.server).state.warehouseOwners::containsKey),"请先清理经济任务、仓库经营绑定与财政余额，再删除城市。");
        data.cities.remove(city.id);data.setDirty();STATS.remove(city.id);SCANS.remove(city.id);
        UNDO.entrySet().removeIf(e->e.getValue().city.equals(city.id));
    }
    public static boolean editor(ServerPlayer p){return p.hasPermissions(2)||p.isCreative();}
    public static void checkEditor(ServerPlayer p,CityData.City c,long revision){
        if(!editor(p))throw new IllegalArgumentException("城市规划工具需要管理员或创造模式。");
        if(!p.hasPermissions(2)&&!p.getUUID().equals(c.owner))throw new IllegalArgumentException("仅聚落创建者或管理员可以编辑。");
        if(!p.level().dimension().location().toString().equals(c.dimension))throw new IllegalArgumentException("请回到聚落所在维度。");
        if(revision!=c.revision)throw new IllegalArgumentException("聚落已被其他操作修改，请刷新后重试。");
    }
    public static void scan(CityData.City c){SCANS.computeIfAbsent(c.id,id->new Scan(c));}
    public static boolean scanning(UUID id){return SCANS.containsKey(id);}
    static void forget(UUID id){STATS.remove(id);SCANS.remove(id);UNDO.entrySet().removeIf(e->e.getValue().city.equals(id));}
    public static Statistics statistics(UUID id){return STATS.get(id);}
    public static void tick(MinecraftServer server){
        // One global budget, irrespective of the number of cities or connected viewers.
        int budget=4096;
        var iterator=SCANS.entrySet().iterator();
        while(iterator.hasNext()&&budget>0){Scan s=iterator.next().getValue();
            if(!CityData.get(server).cities.containsKey(s.city.id)){iterator.remove();continue;}
            if(s.revision!=s.city.revision){iterator.remove();continue;}
            ServerLevel level=server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,ResourceLocation.parse(s.city.dimension)));
            if(level==null){iterator.remove();continue;}
            if(s.cursor==null)s.cursor=new CityScanCursor(CityLoadedChunks.scanBoxes(level,s.city.bounds));
            budget-=s.cursor.step(level,budget,p->{
                if(!s.city.includes(p))return;
                s.known++;
                BlockState state=level.getBlockState(p);if(!state.isAir()){s.nonAir++;s.blocks.merge(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),1L,Long::sum);}
            },air->s.known+=CityAreas.volumeIn(s.city,air));
            if(s.cursor.done()){long included=CityAreas.volume(s.city);STATS.put(s.city.id,new Statistics(s.blocks,s.nonAir,s.city.bounds.volumeLong()-included,Math.max(0,included-s.known),s.revision,level.getGameTime()));iterator.remove();}
        }
    }
    public static void invalidate(CityData.City c){STATS.remove(c.id);SCANS.remove(c.id);scan(c);}
    public static boolean undoChangesWorld(UUID actor){Undo u=UNDO.get(actor);return u!=null&&u.pos!=null;}
    static void resizeAreas(CityData.City c,CityBounds next){
        CityWorks.validateResize(c,next);
        List<CityBounds> out=null;if(c.areas!=null){var clipped=new ArrayList<CityBounds>();for(var old:c.areas){var part=CityAreas.intersection(old,next);if(part!=null)clipped.add(part);}CityAreas.subtract(next,c.bounds,clipped);if(clipped.size()>CityAreas.MAX_PARTS)throw CityAreas.complex();out=List.copyOf(clipped);}
        var added=new ArrayList<CityBounds>();CityAreas.subtract(next,c.bounds,added);CityWorks.trimForResize(c,next);for(var part:added)CityWorks.areaChanged(c,part,true);if(out!=null)c.areas=out;
    }
    public static long area(CityData data,CityData.City c,ServerLevel level,UUID actor,CityBounds area,boolean include){
        area.validateWorld(level);
        CityBounds old=c.bounds;
        CityBounds next=include?CityBounds.between(new BlockPos(Math.min(old.min().getX(),area.min().getX()),Math.min(old.min().getY(),area.min().getY()),Math.min(old.min().getZ(),area.min().getZ())),new BlockPos(Math.max(old.max().getX(),area.max().getX()),Math.max(old.max().getY(),area.max().getY()),Math.max(old.max().getZ(),area.max().getZ()))):old;
        if(!include&&(!old.contains(area.min())||!old.contains(area.max())))throw new IllegalArgumentException("排除选区必须完全位于当前聚落范围内。");
        next.validateWorld(level);data.checkBounds(c.id,c.dimension,next);
        long before=CityAreas.volumeIn(c,area),changed=include?area.volumeLong()-before:before;
        if(changed>0){var regions=CityAreas.edit(CityAreas.regions(c),area,include);Set<Long> previous=new HashSet<>(c.excluded);var previousAreas=c.areas;var previousUnknown=CityWorks.pendingSnapshot(c);CityWorks.areaChanged(c,area,include);c.bounds=next;c.areas=regions;if(include)c.excluded.removeIf(p->area.contains(BlockPos.of(p)));data.changed(c);UNDO.put(actor,new Undo(c.id,c.revision,Map.of(),null,null,null,old,previous,previousAreas,previousUnknown));invalidate(c);}return changed;
    }
    public static int membership(CityData data,CityData.City c,UUID actor,Collection<BlockPos> positions,boolean include){
        if(positions.isEmpty()||positions.size()>1024)throw new IllegalArgumentException("一次可调整1–1024格。");
        for(BlockPos p:positions)if(!c.bounds.contains(p))throw new IllegalArgumentException("方块不在当前聚落范围内。");
        Map<Long,Boolean> before=new HashMap<>();var regions=CityAreas.regions(c);
        for(BlockPos p:positions)if(!before.containsKey(p.asLong())&&c.includes(p)!=include){before.put(p.asLong(),c.includes(p));regions=CityAreas.edit(regions,new CityBounds(p,p),include);}
        if(!before.isEmpty()){var oldAreas=c.areas;var oldExcluded=new HashSet<>(c.excluded);var oldUnknown=CityWorks.pendingSnapshot(c);for(BlockPos p:positions)CityWorks.areaChanged(c,new CityBounds(p,p),include);c.areas=regions;if(include)for(BlockPos p:positions)c.excluded.remove(p.asLong());data.changed(c);UNDO.put(actor,new Undo(c.id,c.revision,Map.of(),null,null,null,c.bounds,oldExcluded,oldAreas,oldUnknown));invalidate(c);}return before.size();
    }
    public static void replace(CityData data,CityData.City c,ServerLevel level,UUID actor,BlockPos pos,int expectedState,String blockId){
        if(!c.includes(pos))throw new IllegalArgumentException("请先将此格纳入聚落。");
        c.bounds.validateWorld(level);
        if(!level.hasChunkAt(pos))throw new IllegalArgumentException("目标区块未加载。");
        ResourceLocation key=ResourceLocation.tryParse(blockId);
        if(key==null||!BuiltInRegistries.BLOCK.containsKey(key))throw new IllegalArgumentException("方块ID不存在。");
        BlockState before=level.getBlockState(pos),after=BuiltInRegistries.BLOCK.get(key).defaultBlockState();
        if(net.minecraft.world.level.block.Block.getId(before)!=expectedState)throw new IllegalArgumentException("世界方块已变化，请刷新后重试。");
        // Containers, machines, doors/beds and ticking/physics blocks require richer transactional editing.
        if(!simple(before)||!simple(after)||level.getBlockEntity(pos)!=null)throw new IllegalArgumentException("首版只替换完整实心方块或空气；容器、机器、流体、多格与特殊方块请在世界中编辑。");
        if(before.equals(after))return;
        if(!level.setBlock(pos,after,3))throw new IllegalArgumentException("世界拒绝了方块编辑。");
        data.changed(c);UNDO.put(actor,new Undo(c.id,c.revision,Map.of(),pos.immutable(),before,after));invalidate(c);
    }
    private static boolean simple(BlockState s){return s.isAir()||(!s.hasBlockEntity()&&s.getProperties().isEmpty()&&s.getFluidState().isEmpty()
        &&!(s.getBlock() instanceof net.minecraft.world.level.block.FallingBlock)&&s.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,BlockPos.ZERO));}
    public static void undo(CityData data,CityData.City c,ServerLevel level,UUID actor){
        Undo undo=UNDO.get(actor);if(undo==null||!undo.city.equals(c.id))throw new IllegalArgumentException("本次登录没有此聚落的可撤销操作。");
        if(undo.revision!=c.revision)throw new IllegalArgumentException("聚落已有后续修改，不能覆盖撤销。");
        if(undo.pos!=null){
            if(!level.hasChunkAt(undo.pos)||!level.getBlockState(undo.pos).equals(undo.after)||level.getBlockEntity(undo.pos)!=null)throw new IllegalArgumentException("世界方块已有变化，撤销已拒绝。");
            if(!level.setBlock(undo.pos,undo.before,3))throw new IllegalArgumentException("世界拒绝撤销。");
        }else if(undo.bounds!=null){data.checkBounds(c.id,c.dimension,undo.bounds);c.bounds=undo.bounds;c.areas=undo.areas;c.works.unknownTerrain=new ArrayList<>(undo.unknown);c.works.deferredTerrain=!c.works.unknownTerrain.isEmpty();c.excluded.clear();c.excluded.addAll(undo.excluded);}
        else for(var e:undo.membership.entrySet()){if(e.getValue())c.excluded.remove(e.getKey());else c.excluded.add(e.getKey());}
        data.changed(c);UNDO.remove(actor);invalidate(c);
    }
    public static void logout(UUID actor){UNDO.remove(actor);}
}
