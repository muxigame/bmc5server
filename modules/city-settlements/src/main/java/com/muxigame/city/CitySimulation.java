package com.muxigame.city;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** Shared economic state, with conditional physical projections. Never loads chunks. */
public final class CitySimulation {
    private record Binding(CityEconomy e,UUID city,UUID warehouse){}
    private static final Map<Container,Binding> bindings=new WeakHashMap<>();
    static void afterCommit(Container box){var binding=bindings.get(box);if(binding==null)return;var p=binding.e.state.simulation.get(binding.city);if(p!=null){var stock=p.stocks.get(binding.warehouse);if(stock!=null)capture(binding.e,stock,box);}}
    static void clear(){bindings.clear();}
    public static void stopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event){var server=event.getServer();var e=CityEconomy.get(server);for(var entry:e.state.simulation.entrySet()){var c=CityData.get(server).cities.get(entry.getKey());if(c==null)continue;var l=CityEconomy.level(server,c);for(var w:c.warehouses.values())if(l.hasChunkAt(w.pos()))try{var box=CityEconomy.physicalWarehouse(l,c,w.id());var stock=entry.getValue().stocks.computeIfAbsent(w.id(),id->new Stock());reconcile(e,stock,box);capture(e,stock,box);}catch(IllegalArgumentException ignored){}}}
    public static final class Stock {
        public int slots=27;
        public Map<String,Integer> physical=new TreeMap<>(),goods=new TreeMap<>();
        public boolean pending,blocked;
        public String status="待采样";
    }
    public static final class Person {
        public boolean present,retired;
        public long pos;
        public int armor,hunger;
        public double health=1;
        public String archivedEntity="",archivedEconomy="";
    }
    public static final class Facility {
        public String stamp="";
        public boolean valid;
        public Map<String,Integer> blocks=new TreeMap<>();
    }
    public static final class Policy {
        public boolean enabled;
        /** Zero disables migration; counted in completed consumption cycles, not ticks. */
        public int emigrateAfter=96;
        public long offlineProductions,offlineConsumptions,migrations,migrationPeriodTicks=2400,nextMigrationTick;
        public Map<UUID,Stock> stocks=new LinkedHashMap<>();
        public Map<UUID,Person> people=new LinkedHashMap<>();
        public Map<UUID,Facility> facilities=new LinkedHashMap<>();
        transient int personCursor,stockCursor,facilityCursor;
    }
    static Policy policy(MinecraftServer server,UUID city){return CityEconomy.get(server).state.simulation.get(city);}
    static void counts(Map<String,Integer> map,int slots){
        CityEconomy.require(map!=null&&map.size()<=256,"模拟库存类型过多");long occupied=0;
        for(var v:map.entrySet()){Item item=CityEconomy.item(v.getKey());CityEconomy.require(v.getValue()!=null&&v.getValue()>0&&v.getValue()<=16384,"模拟库存数量越界");int cap=new ItemStack(item).getMaxStackSize();occupied+=(v.getValue()+cap-1)/cap;}
        CityEconomy.require(occupied<=slots,"模拟库存超过真实槽位容量");
    }
    static void validate(CityEconomy.State e){
        CityEconomy.require(e.simulation!=null&&e.simulation.size()<=128,"后台城市最多128个");
        for(var p:e.simulation.values()){
            CityEconomy.require(p!=null&&p.emigrateAfter>=0&&p.emigrateAfter<=100000&&p.migrationPeriodTicks>=200&&p.migrationPeriodTicks<=24000000&&p.nextMigrationTick>=0&&p.stocks!=null&&p.stocks.size()<=128&&p.people!=null&&p.people.size()<=1024&&p.facilities!=null&&p.facilities.size()<=128,"后台配置越界");
            for(var s:p.stocks.values()){CityEconomy.require(s!=null&&s.slots>=1&&s.slots<=256&&s.status!=null&&s.status.length()<=256,"仓库快照无效");counts(s.physical,s.slots);counts(s.goods,s.slots);}
            for(var r:p.people.values())CityEconomy.require(r!=null&&r.health>=0&&r.health<=1&&r.armor>=0&&r.armor<=1000&&r.hunger>=0&&r.hunger<=100000&&r.archivedEntity!=null&&r.archivedEntity.length()<=262144&&r.archivedEconomy!=null&&r.archivedEconomy.length()<=262144,"居民快照无效");
            for(var f:p.facilities.values()){CityEconomy.require(f!=null&&f.stamp!=null&&f.stamp.length()<160&&f.blocks!=null&&f.blocks.size()<=4096,"设施快照无效");for(var n:f.blocks.values())CityEconomy.require(n!=null&&n>=0&&n<=4096,"设施数量越界");}
        }
    }
    static void references(CityEconomy.State e,CityData data){for(var id:e.simulation.keySet())data.require(id);}
    static void transition(CityEconomy.State before,CityEconomy.State next){
        for(var entry:before.simulation.entrySet())for(var stock:entry.getValue().stocks.entrySet())if(stock.getValue().pending){var p=next.simulation.get(entry.getKey());CityEconomy.require(p!=null&&p.stocks.containsKey(stock.getKey())&&p.stocks.get(stock.getKey()).pending,"先同步待落地库存，再删除或清空后台快照");}
    }
    static Map<String,Integer> contents(Container c){
        Map<String,Integer> out=new TreeMap<>();for(int i=0;i<c.getContainerSize();i++){var s=c.getItem(i);if(s.isEmpty())continue;CityEconomy.require(CityEconomy.plain(s,s.getItem()),"特殊组件物品不能进入后台仓库，请移出后重试");out.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(),s.getCount(),Integer::sum);}return out;
    }
    static void capture(CityEconomy e,Stock s,Container box){
        if(s.pending)return;
        try{s.physical=contents(box);s.goods=new TreeMap<>(s.physical);s.slots=box.getContainerSize();s.blocked=false;s.status="实物已采样";}catch(IllegalArgumentException ex){s.blocked=true;s.status=ex.getMessage();}e.setDirty();
    }
    static void reconcile(CityEconomy e,Stock s,Container box){
        if(!s.pending)return;
        var actual=contents(box);
        if(box.getContainerSize()!=s.slots||(!actual.equals(s.physical)&&!actual.equals(s.goods))){s.status="实物与离城快照冲突，暂停同步";s.blocked=true;e.setDirty();throw new IllegalArgumentException(s.status);}
        if(!actual.equals(s.goods)){
            var slots=new CityEconomy.Slots(box);for(int i=0;i<slots.size;i++)slots.slots.set(i,ItemStack.EMPTY);s.goods.forEach(slots::add);slots.commit();
        }
        s.physical=new TreeMap<>(s.goods);s.pending=false;s.blocked=false;s.status="后台库存已同步";e.setDirty();
    }
    static final class Shadow extends SimpleContainer {
        private boolean quiet=true;
        private final Stock stock;private final CityEconomy economy;
        Shadow(Stock s,CityEconomy e){super(s.slots);stock=s;economy=e;var slots=new CityEconomy.Slots(this);s.goods.forEach(slots::add);for(int i=0;i<slots.size;i++)super.setItem(i,slots.slots.get(i));quiet=false;}
        @Override public void setItem(int i,ItemStack stack){quiet=true;super.setItem(i,stack);quiet=false;}
        @Override public void setChanged(){if(!quiet){stock.goods=contents(this);stock.pending=true;stock.status="后台结算，等待实物同步";economy.setDirty();}}
    }
    static Container warehouse(ServerLevel l,CityData.City c,UUID id){
        var e=CityEconomy.get(l.getServer());var p=e.state.simulation.get(c.id);if(p==null)return null;
        var w=c.warehouses.get(id);CityEconomy.require(w!=null&&c.includes(w.pos()),"模拟仓库未登记或被排除");
        var s=p.stocks.get(id);
        if(l.hasChunkAt(w.pos())){var box=CityEconomy.physicalWarehouse(l,c,id);if(s!=null)reconcile(e,s,box);if(p.enabled){if(s==null){s=new Stock();p.stocks.put(id,s);}capture(e,s,box);bindings.put(box,new Binding(e,c.id,id));}return box;}
        CityEconomy.require(p.enabled&&s!=null&&!s.blocked,"仓库无可靠离城快照或已暂停");return new Shadow(s,e);
    }
    static boolean available(ServerLevel l,CityData.City c,UUID id){
        var p=policy(l.getServer(),c.id);if(p==null||!p.enabled||!c.residents.contains(id))return false;var r=p.people.get(id);
        // Missing entity inside an entity-ticking chunk is not assumed to be an offline citizen.
        return r!=null&&r.present&&!r.retired&&l.getEntity(id)==null&&!l.isPositionEntityTicking(BlockPos.of(r.pos));
    }
    static BlockPos position(ServerLevel l,CityData.City c,UUID id){var entity=l.getEntity(id);if(entity!=null)return entity.blockPosition();var p=policy(l.getServer(),c.id);return p!=null&&p.people.containsKey(id)?BlockPos.of(p.people.get(id).pos):BlockPos.ZERO;}
    static double distance(ServerLevel l,CityData.City c,UUID a,UUID b){var p=policy(l.getServer(),c.id);if((l.getEntity(a)==null&&(p==null||!p.people.containsKey(a)))||(l.getEntity(b)==null&&(p==null||!p.people.containsKey(b))))return Double.POSITIVE_INFINITY;return position(l,c,a).distSqr(position(l,c,b));}
    static int guardPower(ServerLevel l,CityData.City c,UUID id,int training){var entity=l.getEntity(id);if(entity instanceof LivingEntity npc)return (int)Math.ceil((training+Math.min(20,npc.getArmorValue()))*Math.clamp(npc.getHealth()/npc.getMaxHealth(),0,1));var r=policy(l.getServer(),c.id).people.get(id);return (int)Math.ceil((training+Math.min(20,r.armor))*r.health);}
    static void remember(CityEconomy e,CityData.City c,Policy p,LivingEntity entity,boolean leaving){
        if(!BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace().equals("customnpcs"))return;
        var r=p.people.computeIfAbsent(entity.getUUID(),id->new Person());if(r.retired)return;
        var reason=entity.getRemovalReason();boolean unload=reason==Entity.RemovalReason.UNLOADED_TO_CHUNK||reason==Entity.RemovalReason.UNLOADED_WITH_PLAYER;
        r.present=(entity.isAlive()||leaving&&unload&&entity.getHealth()>0)&&c.includes(entity.blockPosition());r.pos=entity.blockPosition().asLong();r.health=Math.clamp(entity.getHealth()/entity.getMaxHealth(),0,1);r.armor=entity.getArmorValue();e.setDirty();
    }
    static String stamp(CityData.City c,CityRegistry.Building b){return b.bounds().min().asLong()+":"+b.bounds().max().asLong()+":"+c.excluded.hashCode()+(c.areas==null?"":":"+c.areas.hashCode());}
    static boolean loaded(ServerLevel l,CityBounds b){for(int x=b.min().getX()>>4;x<=b.max().getX()>>4;x++)for(int z=b.min().getZ()>>4;z<=b.max().getZ()>>4;z++)if(!l.hasChunk(x,z))return false;return true;}
    static void facility(CityEconomy e,ServerLevel l,CityData.City c,Policy p,CityRegistry.Building b){
        if(b.bounds().volume()>4096||!loaded(l,b.bounds()))return;var f=new Facility();f.stamp=stamp(c,b);f.valid=true;
        for(int i=0;i<b.bounds().volume();i++){var pos=b.bounds().at(i);if(!c.includes(pos))f.valid=false;f.blocks.merge(BuiltInRegistries.BLOCK.getKey(l.getBlockState(pos).getBlock()).toString(),1,Integer::sum);}p.facilities.put(b.id(),f);e.setDirty();
    }
    static boolean produceAway(CityEconomy e,ServerLevel l,CityData.City c,CityEconomy.Job j){
        var p=e.state.simulation.get(c.id);var b=c.buildings.get(j.building);if(p==null||!p.enabled||b==null||loaded(l,b.bounds()))return false;
        var f=p.facilities.get(j.building);CityEconomy.require(f!=null&&f.valid&&f.stamp.equals(stamp(c,b)),"设施未取得可靠离城快照");CityEconomy.require(j.harvestWheat==0,"旧逐株小麦岗位需改为周期资源岗位才支持后台");
        j.requiredBlocks.forEach((id,n)->CityEconomy.require(f.blocks.getOrDefault(id,0)>=n,"离城设施不足："+id));
        CityEconomy.require(e.owner(j.warehouse).equals(j.employer),"雇主不拥有该仓库");var box=CityEconomy.warehouse(l,c,j.warehouse);var goods=CitySupply.prepare(e,l,c,j,box);if(goods==null)return true;var money=e.prepareMoney(j.employer,CityEconomy.person(j.worker),j.wage);
        goods.commit();e.state.accounts.putAll(money);if(!j.employer.equals(CityEconomy.person(j.worker)))CityLiving.income(e.state,CityEconomy.person(j.worker),j.wage);p.offlineProductions++;j.completedBatches++;e.log(l.getGameTime(),"PRODUCTION",j.employer,CityEconomy.person(j.worker),j.wage,"后台生产 "+j.id);j.status="后台生产并发薪";return true;
    }
    static void consumed(MinecraftServer server,CityEconomy e,CityLiving.Profile life){
        var p=e.state.simulation.get(life.city);if(p==null||!p.enabled)return;var person=p.people.get(life.npc);if(person==null)return;
        if(CityEconomy.level(server,CityData.get(server).require(life.city)).getEntity(life.npc)==null)p.offlineConsumptions++;
        person.hunger=life.lastEssentials?0:Math.min(100000,person.hunger+1);e.setDirty();
    }
    static void retire(CityEconomy e,CityData data,CityData.City c,Policy p,UUID id){
        var r=p.people.get(id);if(r.retired)return;
        var archive=new com.google.gson.JsonObject();archive.add("profile",CityEconomy.JSON.toJsonTree(e.state.living.get(id)));archive.add("jobs",CityEconomy.JSON.toJsonTree(e.state.production.stream().filter(j->j.worker.equals(id)).toList()));archive.add("offers",CityEconomy.JSON.toJsonTree(e.state.merchants.stream().filter(o->o.npc.equals(id)).toList()));r.archivedEconomy=archive.toString();
        r.retired=true;r.present=false;p.migrations++;p.nextMigrationTick=e.serverContext.overworld().getGameTime()+p.migrationPeriodTicks;Set<UUID> offers=new HashSet<>();e.state.merchants.removeIf(o->{if(o.npc.equals(id)){offers.add(o.id);return true;}return false;});e.state.production.removeIf(j->j.worker.equals(id));e.state.consumption.removeIf(d->d.npc.equals(id)||offers.contains(d.offer));e.state.living.remove(id);for(var logistics:e.state.logistics.values()){logistics.merchants.remove(id);if(logistics.merchants.isEmpty()&&!logistics.supply.enabled)logistics.enabled=false;}e.invalidateIndexes();
        c.residents.remove(id);data.changed(c);CityService.invalidate(c);e.log(e.serverContext.overworld().getGameTime(),"MIGRATION",CityEconomy.person(id),"",0,"长期缺粮迁出；账户保留 "+c.id);
        var entity=CityEconomy.level(e.serverContext,c).getEntity(id);if(entity!=null&&archiveRetired(e,entity))entity.discard();
    }
    public static void tick(MinecraftServer server){
        var e=CityEconomy.get(server);var policies=new ArrayList<>(e.state.simulation.entrySet());if(policies.isEmpty())return;var entry=policies.get(Math.floorMod(server.getTickCount(),policies.size()));var p=entry.getValue();if(!p.enabled)return;var data=CityData.get(server);var c=data.cities.get(entry.getKey());if(c==null)return;var l=CityEconomy.level(server,c);
        var residents=new ArrayList<>(c.residents);for(int n=0;n<Math.min(2,residents.size());n++){UUID id=residents.get(Math.floorMod(p.personCursor++,residents.size()));var r=p.people.get(id);if(r!=null&&!r.retired&&!(e.state.living.containsKey(id)&&e.state.living.get(id).basicMode)&&p.emigrateAfter>0&&r.hunger>=p.emigrateAfter&&l.getGameTime()>=p.nextMigrationTick){retire(e,data,c,p,id);break;}var npc=l.getEntity(id);if(npc instanceof LivingEntity living)remember(e,c,p,living,false);}
        var warehouses=new ArrayList<>(c.warehouses.values());if(!warehouses.isEmpty()){var w=warehouses.get(Math.floorMod(p.stockCursor++,warehouses.size()));if(l.hasChunkAt(w.pos()))try{var box=CityEconomy.physicalWarehouse(l,c,w.id());var s=p.stocks.computeIfAbsent(w.id(),id->new Stock());reconcile(e,s,box);capture(e,s,box);}catch(IllegalArgumentException ex){var s=p.stocks.get(w.id());if(s!=null){s.blocked=true;s.status=ex.getMessage();e.setDirty();}}}
        var buildings=new ArrayList<>(c.buildings.values());if(!buildings.isEmpty())facility(e,l,c,p,buildings.get(Math.floorMod(p.facilityCursor++,buildings.size())));
    }
    public static void unload(ChunkEvent.Unload event){chunk(event,false);}
    public static void loadChunk(ChunkEvent.Load event){chunk(event,true);}
    private static void chunk(ChunkEvent event,boolean entering){
        if(!(event.getLevel() instanceof ServerLevel l))return;var e=CityEconomy.get(l.getServer());var data=CityData.get(l.getServer());var chunk=event.getChunk();
        for(var entry:e.state.simulation.entrySet()){var p=entry.getValue();var c=data.cities.get(entry.getKey());if(c==null||!c.dimension.equals(l.dimension().location().toString()))continue;
            for(var w:c.warehouses.values())if((w.pos().getX()>>4)==chunk.getPos().x&&(w.pos().getZ()>>4)==chunk.getPos().z){var s=p.stocks.get(w.id());if(s==null&&!p.enabled)continue;if(s==null){s=new Stock();p.stocks.put(w.id(),s);}if(chunk.getBlockEntity(w.pos()) instanceof Container box)try{if(entering)reconcile(e,s,box);else capture(e,s,box);}catch(IllegalArgumentException ex){s.blocked=true;s.status=ex.getMessage();e.setDirty();}else {s.blocked=true;s.status="实物仓库缺失，暂停后台库存";e.setDirty();}}
        }
    }
    public static void leave(EntityLeaveLevelEvent event){rememberEvent(event.getEntity(),true);}
    public static void death(LivingDeathEvent event){rememberEvent(event.getEntity(),false);}
    private static void rememberEvent(Entity entity,boolean leaving){if(!(entity.level() instanceof ServerLevel l)||!(entity instanceof LivingEntity living))return;var e=CityEconomy.get(l.getServer());for(var entry:e.state.simulation.entrySet()){var c=CityData.get(l.getServer()).cities.get(entry.getKey());if(entry.getValue().enabled&&c!=null&&c.residents.contains(entity.getUUID()))remember(e,c,entry.getValue(),living,leaving);}}
    static boolean archiveRetired(CityEconomy e,Entity entity){for(var p:e.state.simulation.values()){var r=p.people.get(entity.getUUID());if(r!=null&&r.retired){String archive=entity.saveWithoutId(new CompoundTag()).toString();if(archive.length()>262144)return false;r.archivedEntity=archive;e.setDirty();return true;}}return false;}
    public static void join(EntityJoinLevelEvent event){if(!(event.getLevel() instanceof ServerLevel l)||!BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).getNamespace().equals("customnpcs"))return;if(archiveRetired(CityEconomy.get(l.getServer()),event.getEntity()))event.setCanceled(true);}
}
