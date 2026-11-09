package com.muxigame.city;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;

/** Registrations are data only. No inventory transfers, NPC AI changes or world edits. */
public final class CityRegistry {
    public static final List<String> TYPES=List.of("住宅","农田","粮仓","工坊","商铺","兵营","城墙","其他");
    public record Building(UUID id,String name,String type,CityBounds bounds){}
    public record Warehouse(UUID id,String name,BlockPos pos){}
    static void inside(CityData.City c,CityBounds b){if(!c.bounds.contains(b.min())||!c.bounds.contains(b.max()))throw new IllegalArgumentException("登记范围必须在城市边界内。");}
    public static void building(CityData d,CityData.City c,String name,String type,CityBounds b){
        inside(c,b);if(!TYPES.contains(type))throw new IllegalArgumentException("未知建筑用途。");
        if(c.buildings.size()>=128)throw new IllegalArgumentException("每城最多128栋建筑。");
        if(c.buildings.values().stream().anyMatch(v->v.bounds.intersects(b)))throw new IllegalArgumentException("建筑登记范围不能重叠。");
        b.validateBuilding();if(!CityAreas.covers(c,b))throw new IllegalArgumentException("建筑选区含未纳入城市的空间，请先纳入。");
        UUID id=UUID.randomUUID();c.buildings.put(id,new Building(id,CityData.validName(name),type,b));d.changed(c);
    }
    public static void warehouse(CityData d,CityData.City c,ServerLevel level,String name,BlockPos pos){
        if(!c.includes(pos)||!level.hasChunkAt(pos)||!(level.getBlockEntity(pos) instanceof Container))throw new IllegalArgumentException("请指向城内已加载的箱子、木桶等容器。");
        if(c.warehouses.size()>=128||c.warehouses.values().stream().anyMatch(v->v.pos.equals(pos)))throw new IllegalArgumentException("容器已登记或已达128个上限。");
        UUID id=UUID.randomUUID();c.warehouses.put(id,new Warehouse(id,CityData.validName(name),pos.immutable()));d.changed(c);
    }
    public static void bind(CityData d,CityData.City c,ServerPlayer p,UUID id){
        var e=p.serverLevel().getEntity(id);
        if(!(e instanceof LivingEntity)||!e.isAlive()||!BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals("customnpcs")||e.distanceToSqr(p)>64)throw new IllegalArgumentException("请选择8格内存活的CustomNPCs角色。");
        if(c.residents.size()>=4096)throw new IllegalArgumentException("居民已达上限。");
        if(d.cities().stream().anyMatch(v->v!=c&&v.residents.contains(id)))throw new IllegalArgumentException("NPC已属于其他城市，请先解绑。");
        if(c.residents.add(id))d.changed(c);
    }
    public static JsonObject export(CityData.City c){JsonObject j=new JsonObject();JsonArray bs=new JsonArray(),ws=new JsonArray();
        for(var b:c.buildings.values()){JsonObject v=new JsonObject();v.addProperty("id",b.id.toString());v.addProperty("name",b.name);v.addProperty("type",b.type);v.add("min",CityBlueprints.pos(b.bounds.min()));v.add("max",CityBlueprints.pos(b.bounds.max()));bs.add(v);}
        for(var w:c.warehouses.values()){JsonObject v=new JsonObject();v.addProperty("id",w.id.toString());v.addProperty("name",w.name);v.add("pos",CityBlueprints.pos(w.pos));ws.add(v);}j.add("works",CityEconomy.JSON.toJsonTree(c.works));j.add("housing",CityEconomy.JSON.toJsonTree(c.housing));j.add("buildings",bs);j.add("warehouses",ws);return j;
    }
    /** Parse into a detached city; callers commit only after all checks pass. World absence is a status, not data loss. */
    public static void read(CityData.City c,JsonObject j){
        JsonArray bs=j.getAsJsonArray("buildings"),ws=j.getAsJsonArray("warehouses");if(bs.size()>128||ws.size()>128)throw new IllegalArgumentException("建筑或仓库超过128个。");
        for(JsonElement raw:bs){JsonObject v=raw.getAsJsonObject();UUID id=UUID.fromString(v.get("id").getAsString());String name=CityData.validName(v.get("name").getAsString()),type=v.get("type").getAsString();CityBounds b=new CityBounds(CityBlueprints.pos(v.getAsJsonArray("min")),CityBlueprints.pos(v.getAsJsonArray("max")));b.validateBuilding();inside(c,b);
            if(!TYPES.contains(type)||c.buildings.containsKey(id)||c.buildings.values().stream().anyMatch(old->old.bounds.intersects(b)))throw new IllegalArgumentException("建筑用途、ID或范围无效。");c.buildings.put(id,new Building(id,name,type,b));}
        Set<BlockPos> positions=new HashSet<>();for(JsonElement raw:ws){JsonObject v=raw.getAsJsonObject();UUID id=UUID.fromString(v.get("id").getAsString());BlockPos p=CityBlueprints.pos(v.getAsJsonArray("pos"));if(!c.bounds.contains(p)||!positions.add(p)||c.warehouses.containsKey(id))throw new IllegalArgumentException("仓库坐标越界或重复。");c.warehouses.put(id,new Warehouse(id,CityData.validName(v.get("name").getAsString()),p));}
        if(j.has("works")){CityMaintenance.exact(j.get("works"));c.works=CityEconomy.JSON.fromJson(j.get("works"),CityWorks.Plan.class);CityWorks.validate(c.works,c);}
        if(j.has("housing")){CityMaintenance.exact(j.get("housing"));c.housing=CityEconomy.JSON.fromJson(j.get("housing"),CityHousing.State.class);CityHousing.validate(c,c.housing);}
    }
    public static void validateBounds(CityData.City c,CityBounds bounds){for(var b:c.buildings.values())if(!bounds.contains(b.bounds.min())||!bounds.contains(b.bounds.max()))throw new IllegalArgumentException("新范围会移除已登记建筑，请先解除登记。");for(var w:c.warehouses.values())if(!bounds.contains(w.pos))throw new IllegalArgumentException("新范围会移除仓库，请先解绑。");}
    private static CompoundTag row(String id,String name,String detail){CompoundTag t=new CompoundTag();t.putString("Id",id);t.putString("Name",name);t.putString("Detail",detail);return t;}
    public static void snapshot(ServerPlayer p,CityData.City c,CompoundTag t){
        ListTag bs=new ListTag(),ws=new ListTag(),ns=new ListTag(),near=new ListTag();
        for(var b:c.buildings.values()){var h=c.housing.houses.get(b.id());String info=h==null?"未配置房屋属性":(h.personal?"私人":"公共")+" · 血量"+(h.baseline.size()-h.damaged.size())+"/"+h.baseline.size()+" · 住户"+h.residents.size()+"/"+h.beds+" · 工人"+h.workers.size()+"/"+h.workerLimit;bs.add(row(b.id.toString(),b.name+" · "+b.type,info+" · "+b.bounds.min().toShortString()+" → "+b.bounds.max().toShortString()));}
        for(var w:c.warehouses.values()){String status;if(!c.includes(w.pos))status="已排除，停用";else if(!p.serverLevel().hasChunkAt(w.pos))status="未加载，待核验";else if(p.serverLevel().getBlockEntity(w.pos) instanceof Container container){int occupied=0;long count=0;for(int i=0;i<container.getContainerSize();i++){var item=container.getItem(i);if(!item.isEmpty()){occupied++;count+=item.getCount();}}status=occupied+"槽 / "+count+"件";}else status="容器已移除";ws.add(row(w.id.toString(),w.name,w.pos.toShortString()+" · "+status));}
        for(UUID id:c.residents.stream().sorted().toList()){var entity=p.serverLevel().getEntity(id);ns.add(row(id.toString(),entity==null?"未加载的居民":entity.getName().getString(),id+" · "+(entity==null?"待核验":entity.isAlive()?"存活":"死亡")));}
        for(var e:p.serverLevel().getEntities(p,p.getBoundingBox().inflate(8),e->e instanceof LivingEntity&&e.isAlive()&&e.distanceToSqr(p)<=64&&BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals("customnpcs")).stream().sorted(Comparator.comparingDouble(e->e.distanceToSqr(p))).limit(32).toList())near.add(row(e.getUUID().toString(),e.getName().getString(),e.blockPosition().toShortString()+(c.residents.contains(e.getUUID())?" · 已绑定":"")));
        t.put("Buildings",bs);t.put("Warehouses",ws);t.put("ResidentList",ns);t.put("NearbyNpcs",near);
        var hit=p.pick(6,0,false);if(hit instanceof BlockHitResult h&&hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK){BlockPos pos=h.getBlockPos();if(p.serverLevel().hasChunkAt(pos)&&p.serverLevel().getBlockEntity(pos) instanceof Container){t.putLong("TargetContainer",pos.asLong());t.putString("TargetLabel",pos.toShortString());}}
    }
}
