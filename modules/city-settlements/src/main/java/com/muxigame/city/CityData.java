package com.muxigame.city;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Stored in overworld/data/city_settlements.dat; dimension is part of every city identity. */
public final class CityData extends SavedData {
    public static final int SCHEMA=4;
    final LinkedHashMap<UUID,City> cities=new LinkedHashMap<>();
    public static final class City {
        public final UUID id, owner;
        public String name;
        public final String dimension;
        public CityBounds bounds;
        public long revision;
        List<CityBounds> areas;
        public CityHousing.State housing=new CityHousing.State();
        public CityWorks.Plan works=new CityWorks.Plan();
        final Set<Long> excluded=new HashSet<>();
        final Set<UUID> residents=new HashSet<>();
        final LinkedHashMap<UUID,CityRegistry.Building> buildings=new LinkedHashMap<>();
        final LinkedHashMap<UUID,CityRegistry.Warehouse> warehouses=new LinkedHashMap<>();
        City(UUID id,UUID owner,String name,String dimension,CityBounds bounds){this.id=id;this.owner=owner;this.name=validName(name);this.dimension=dimension;this.bounds=bounds;}
        public boolean includes(BlockPos pos){return CityAreas.contains(this,pos);}
        public Set<UUID> residents(){return Set.copyOf(residents);}
    }
    public static CityData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(()->CitySaveSafety.newData(server,"city_settlements",CityData::new),CityData::load),"city_settlements");}
    public Collection<City> cities(){return Collections.unmodifiableCollection(cities.values());}
    public City require(UUID id){City c=cities.get(id);if(c==null)throw new IllegalArgumentException("聚落不存在，请重新打开沙盘。");return c;}
    public static String validName(String name){name=name.strip();if(name.isEmpty()||name.length()>32||name.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("名称应为1–32个可见字符。");return name;}
    public City create(UUID owner,String name,String dimension,CityBounds bounds){
        if(cities.size()>=128)throw new IllegalArgumentException("当前存档最多128个聚落。");
        checkBounds(null,dimension,bounds);
        City city=new City(UUID.randomUUID(),owner,name,dimension,bounds);cities.put(city.id,city);setDirty();return city;
    }
    public void checkBounds(UUID ignore,String dimension,CityBounds bounds){
        for(City c:cities.values())if(!c.id.equals(ignore)&&c.dimension.equals(dimension)&&c.bounds.intersects(bounds))
            throw new IllegalArgumentException("范围与聚落「"+c.name+"」重叠。");
    }
    public void changed(City c){c.revision++;setDirty();}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider provider){
        tag.putInt("Schema",SCHEMA);ListTag list=new ListTag();
        for(City c:cities.values()){
            CompoundTag t=new CompoundTag();t.putUUID("Id",c.id);t.putUUID("Owner",c.owner);t.putString("Name",c.name);t.putString("Dimension",c.dimension);
            t.putLong("Min",c.bounds.min().asLong());t.putLong("Max",c.bounds.max().asLong());t.putLong("Revision",c.revision);
            if(c.areas!=null){ListTag regions=new ListTag();for(var b:c.areas){CompoundTag region=new CompoundTag();region.putLong("Min",b.min().asLong());region.putLong("Max",b.max().asLong());regions.add(region);}t.put("Areas",regions);}
            t.putLongArray("Excluded",c.excluded.stream().mapToLong(Long::longValue).sorted().toArray());
            ListTag residents=new ListTag();c.residents.stream().sorted().forEach(id->residents.add(StringTag.valueOf(id.toString())));t.put("Residents",residents);t.putByteArray("RegistryUtf8",CityRegistry.export(c).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));list.add(t);
        }tag.put("Cities",list);return tag;
    }
    public static CityData load(CompoundTag tag,HolderLookup.Provider provider){
        if(tag.getInt("Schema")!=1&&tag.getInt("Schema")!=2&&tag.getInt("Schema")!=3&&tag.getInt("Schema")!=SCHEMA)throw new IllegalArgumentException("Unsupported city data schema");
        CityData data=new CityData();ListTag list=tag.getList("Cities",Tag.TAG_COMPOUND);
        if(list.size()>128)throw new IllegalArgumentException("Too many cities");
        Set<UUID> linked=new HashSet<>();
        for(Tag entry:list){CompoundTag t=(CompoundTag)entry;
            City c=new City(t.getUUID("Id"),t.getUUID("Owner"),t.getString("Name"),t.getString("Dimension"),new CityBounds(BlockPos.of(t.getLong("Min")),BlockPos.of(t.getLong("Max"))));
            data.checkBounds(null,c.dimension,c.bounds);
            if(data.cities.containsKey(c.id))throw new IllegalArgumentException("Duplicate city ID");
            if(t.contains("Areas",Tag.TAG_LIST)){var regions=t.getList("Areas",Tag.TAG_COMPOUND);if(regions.size()>CityAreas.MAX_PARTS)throw CityAreas.complex();var boxes=new ArrayList<CityBounds>();for(Tag raw:regions){var row=(CompoundTag)raw;boxes.add(new CityBounds(BlockPos.of(row.getLong("Min")),BlockPos.of(row.getLong("Max"))));}c.areas=CityAreas.validate(c.bounds,boxes);}
            c.revision=t.getLong("Revision");for(long p:t.getLongArray("Excluded")){if(!c.bounds.contains(BlockPos.of(p)))throw new IllegalArgumentException("Excluded position outside bounds");c.excluded.add(p);}
            for(Tag r:t.getList("Residents",Tag.TAG_STRING)){UUID id=UUID.fromString(r.getAsString());if(!linked.add(id))throw new IllegalArgumentException("Duplicate resident");c.residents.add(id);}
            String registry=t.contains("RegistryUtf8",Tag.TAG_BYTE_ARRAY)?new String(t.getByteArray("RegistryUtf8"),java.nio.charset.StandardCharsets.UTF_8):t.getString("Registry");
            if(!registry.isEmpty())CityRegistry.read(c,com.google.gson.JsonParser.parseString(registry).getAsJsonObject());
            data.cities.put(c.id,c);
        }return data;
    }
}
