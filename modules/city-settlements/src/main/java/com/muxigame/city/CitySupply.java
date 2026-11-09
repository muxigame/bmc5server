package com.muxigame.city;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
/** Explicit shop and workplace mandates survive their previous employees. No grants or chunk tickets. */
public final class CitySupply {
    public static final class Shop {
        public UUID id,warehouse; public String item; public long buyPrice=-1,sellPrice=-1;
    }
    public static final class Kitchen {
        public UUID building,warehouse; public String employer;
        public Map<String,Integer> inputs=new TreeMap<>(),outputs=new TreeMap<>(),requiredBlocks=new TreeMap<>();
        public int maxWorkers=8; public long wage=3,periodTicks=2400;
    }
    public static final class Policy {
        public boolean enabled; public List<Shop> shops=new ArrayList<>(); public List<Kitchen> kitchens=new ArrayList<>();
        public Map<String,Integer> stockCaps=new TreeMap<>(); public List<UUID> overflowWarehouses=new ArrayList<>();
        public List<String> essentials=new ArrayList<>(); public int capacityPercent=110,donorReserveCycles=12,standbyWagePercent;
        public long staffPeriodTicks=2400,nextStaffTick,successions,reassignments;
        public String status="尚未调度";
    }
    static Policy policy(CityEconomy e,UUID city){var p=e.state.logistics.get(city);return p!=null&&p.enabled&&p.supply.enabled?p.supply:null;}
    static void validate(Policy p){
        CityEconomy.require(p!=null&&p.shops!=null&&p.shops.size()<=128&&p.kitchens!=null&&p.kitchens.size()<=64&&p.stockCaps!=null&&p.stockCaps.size()<=16&&p.overflowWarehouses!=null&&p.overflowWarehouses.size()<=128&&p.essentials!=null&&p.essentials.size()<=8,"供应配置数量越界");
        CityEconomy.require(p.standbyWagePercent>=0&&p.standbyWagePercent<=100&&p.capacityPercent>=100&&p.capacityPercent<=200&&p.donorReserveCycles>=1&&p.donorReserveCycles<=100&&p.staffPeriodTicks>=1200&&p.staffPeriodTicks<=24000000&&p.nextStaffTick>=0,"供应调度参数越界");
        Set<UUID> ids=new HashSet<>();Set<String> products=new HashSet<>();
        for(var s:p.shops){CityEconomy.require(s!=null&&s.id!=null&&ids.add(s.id)&&s.warehouse!=null&&products.add(s.warehouse+"|"+s.item)&&s.buyPrice>=-1&&s.buyPrice<=CityEconomy.LIMIT&&s.sellPrice>=-1&&s.sellPrice<=CityEconomy.LIMIT&&(s.buyPrice>=0||s.sellPrice>=0),"商店委托无效/重复");CityEconomy.item(s.item);}
        p.essentials.forEach(CityEconomy::item);p.stockCaps.forEach((i,n)->{CityEconomy.item(i);CityEconomy.require(n!=null&&n>=1&&n<=4096,"原料库存上限越界");});
        for(var k:p.kitchens){CityEconomy.require(k!=null&&k.building!=null&&k.warehouse!=null&&k.employer!=null&&k.maxWorkers>=1&&k.maxWorkers<=32&&k.wage>=0&&k.wage<=CityEconomy.LIMIT&&k.periodTicks>=1200&&k.periodTicks<=24000000,"厨房配置无效");var state=new CityEconomy.State();var j=job(k,UUID.randomUUID(),UUID.randomUUID(),0);state.accounts.put(k.employer,0L);state.accounts.put(CityEconomy.person(j.worker),0L);state.warehouseOwners.put(k.warehouse,k.employer);state.production.add(j);CityEconomy.validate(state);}
    }
    static void references(Policy p,CityEconomy.State e,CityData.City c){
        for(var s:p.shops){CityEconomy.require(c.warehouses.containsKey(s.warehouse)&&e.warehouseOwners.containsKey(s.warehouse),"委托商店仓库未登记");var o=e.merchants.stream().filter(v->v.id.equals(s.id)).findFirst().orElse(null);CityEconomy.require(o==null||o.city.equals(c.id)&&o.warehouse.equals(s.warehouse)&&o.item.equals(s.item),"商店委托ID与其他报价冲突");CityEconomy.require(e.production.stream().noneMatch(j->j.id.equals(s.id))&&e.consumption.stream().noneMatch(d->d.id.equals(s.id)),"商店委托ID与任务冲突");}
        for(var k:p.kitchens)CityEconomy.require(c.buildings.containsKey(k.building)&&c.buildings.get(k.building).bounds().volume()<=4096&&c.warehouses.containsKey(k.warehouse)&&Objects.equals(e.warehouseOwners.get(k.warehouse),k.employer),"厨房设施/仓库归属无效");
        for(var id:p.overflowWarehouses)CityEconomy.require(c.warehouses.containsKey(id)&&e.warehouseOwners.containsKey(id),"周转仓未登记");
    }
    static boolean present(ServerLevel l,CityData.City c,UUID id){try{CityEconomy.resident(l,c,id);return true;}catch(IllegalArgumentException ex){return false;}}
    static boolean sells(CityEconomy e,ServerLevel l,CityData.City c,UUID warehouse,String item){return e.state.merchants.stream().anyMatch(o->o.enabled&&o.city.equals(c.id)&&o.warehouse.equals(warehouse)&&o.item.equals(item)&&o.buyPrice>=0&&present(l,c,o.npc));}
    static int demand(CityEconomy e,ServerLevel l,CityData.City c,String item,long period){double n=0;for(var p:e.state.living.values())if(p.enabled&&p.city.equals(c.id)&&present(l,c,p.npc))for(var need:p.basket)if(need.item.equals(item)&&need.tier<=p.level)n+=p.basicMode?(double)p.foodPerHour*period/72000:(double)need.quantity*period/p.periodTicks;return (int)Math.ceil(n);}
    static int stock(CityEconomy e,ServerLevel l,CityData.City c,String item){int total=0;for(var w:c.warehouses.values())try{total+=CitySimulation.contents(CityEconomy.warehouse(l,c,w.id())).getOrDefault(item,0);}catch(IllegalArgumentException ignored){}return total;}
    static boolean shortFood(CityEconomy e,ServerLevel l,CityData.City c,String item){int need=demand(e,l,c,item,2400);if(need==0)return false;int sale=0;for(var w:c.warehouses.values())if(sells(e,l,c,w.id(),item))try{sale+=CitySimulation.contents(CityEconomy.warehouse(l,c,w.id())).getOrDefault(item,0);}catch(IllegalArgumentException ignored){}return sale<need*2;}
    static void limit(CityEconomy e,CityData.City c,CityEconomy.Job j,net.minecraft.world.Container box){var p=policy(e,c.id);if(p==null)return;var counts=CitySimulation.contents(box);for(var v:j.outputs.entrySet()){var cap=p.stockCaps.get(v.getKey());if(cap!=null)CityEconomy.require(counts.getOrDefault(v.getKey(),0)-j.inputs.getOrDefault(v.getKey(),0)+v.getValue()<=cap,"原料达到库存上限，保留成品空间");}}
    static CityEconomy.Slots prepare(CityEconomy e,ServerLevel l,CityData.City c,CityEconomy.Job j,net.minecraft.world.Container box){
        try{limit(e,c,j,box);var slots=new CityEconomy.Slots(box);j.inputs.forEach(slots::remove);j.outputs.forEach(slots::add);return slots;}
        catch(IllegalArgumentException ex){var p=policy(e,c.id);if(p==null||p.standbyWagePercent==0)throw ex;
            long wage=j.wage*p.standbyWagePercent/100;var payment=e.prepareMoney(j.employer,CityEconomy.person(j.worker),wage);e.state.accounts.putAll(payment);if(!j.employer.equals(CityEconomy.person(j.worker)))CityLiving.income(e.state,CityEconomy.person(j.worker),wage);
            e.log(l.getGameTime(),"STANDBY_WAGE",j.employer,CityEconomy.person(j.worker),wage,j.id+" 维护待工："+ex.getMessage());j.status="维护待工，实付"+wage+"："+ex.getMessage();return null;
        }
    }
    static void shops(CityEconomy e,ServerLevel l,CityData.City c,CityLogistics.Policy logistics,long now){var p=logistics.supply;int repaired=0;
        for(var s:p.shops){var old=e.state.merchants.stream().filter(o->o.id.equals(s.id)).findFirst().orElse(null);if(old!=null&&present(l,c,old.npc))continue;
            UUID replacement=c.residents.stream().filter(id->present(l,c,id)&&e.state.accounts.containsKey(CityEconomy.person(id)))
                .filter(id->e.state.merchants.stream().noneMatch(o->o.enabled&&o.npc.equals(id)&&o.item.equals(s.item)))
                .min(Comparator.<UUID>comparingInt(id->e.state.merchants.stream().anyMatch(o->o.npc.equals(id)&&o.warehouse.equals(s.warehouse))?0:e.state.production.stream().anyMatch(j->j.enabled&&j.worker.equals(id)&&j.warehouse.equals(s.warehouse))?1:2).thenComparing(UUID::toString)).orElse(null);
            if(replacement==null){p.status="缺少可接任商人的在城居民";continue;}if(old==null){if(e.state.merchants.size()>=512)break;old=new CityEconomy.Offer();old.id=s.id;old.city=c.id;old.warehouse=s.warehouse;old.item=s.item;old.buyPrice=s.buyPrice;old.sellPrice=s.sellPrice;old.enabled=true;e.state.merchants.add(old);}old.npc=replacement;
            if(logistics.merchants.size()<64&&!logistics.merchants.contains(replacement))logistics.merchants.add(replacement);p.successions++;p.status="商店已接任："+s.item;e.invalidateIndexes();e.log(now,"SUCCESSION","",CityEconomy.person(replacement),0,s.id.toString());if(++repaired>=3)break;
        }
    }
    static CityEconomy.Job job(Kitchen k,UUID city,UUID worker,long next){var j=new CityEconomy.Job();j.id=UUID.randomUUID();j.city=city;j.worker=worker;j.building=k.building;j.warehouse=k.warehouse;j.employer=k.employer;j.inputs=new TreeMap<>(k.inputs);j.outputs=new TreeMap<>(k.outputs);j.requiredBlocks=new TreeMap<>(k.requiredBlocks);j.wage=k.wage;j.periodTicks=k.periodTicks;j.nextTick=next;j.enabled=true;j.workVisual=true;return j;}
    static boolean facility(ServerLevel l,CityData.City c,Kitchen k){var b=c.buildings.get(k.building);if(b==null||b.bounds().volume()>4096)return false;Map<String,Integer> blocks=new HashMap<>();if(CitySimulation.loaded(l,b.bounds())){for(int i=0;i<b.bounds().volume();i++){var pos=b.bounds().at(i);if(!c.includes(pos))return false;blocks.merge(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(l.getBlockState(pos).getBlock()).toString(),1,Integer::sum);}}else{var p=CitySimulation.policy(l.getServer(),c.id);var f=p==null?null:p.facilities.get(k.building);if(p==null||!p.enabled||f==null||!f.valid||!f.stamp.equals(CitySimulation.stamp(c,b)))return false;blocks=f.blocks;}for(var n:k.requiredBlocks.entrySet())if(blocks.getOrDefault(n.getKey(),0)<n.getValue())return false;return true;}
    static void staff(CityEconomy e,ServerLevel l,CityData.City c,Policy p,long now){if(now<p.nextStaffTick)return;p.nextStaffTick=now+p.staffPeriodTicks;
        Map<String,Integer> stockCache=new HashMap<>();
        for(var k:p.kitchens){if(!facility(l,c,k))continue;String food=k.outputs.keySet().stream().filter(p.essentials::contains).findFirst().orElse(null);if(food==null)continue;int need=(int)Math.ceil(demand(e,l,c,food,k.periodTicks)*p.capacityPercent/100.0);double capacity=0;int workers=0;
            for(var j:e.state.production)if(j.enabled&&j.city.equals(c.id)){if(j.building.equals(k.building))workers++;if(present(l,c,j.worker))capacity+=(double)j.outputs.getOrDefault(food,0)*k.periodTicks/j.periodTicks;}
            if(capacity>=need||workers>=k.maxWorkers)continue;
            var candidates=new ArrayList<>(c.residents);candidates.sort(Comparator.comparingInt(id->e.state.production.stream().anyMatch(j->j.enabled&&j.worker.equals(id))?1:0));
            for(var id:candidates){var life=e.state.living.get(id);if(life==null||life.guard||life.distributor||!present(l,c,id))continue;var old=e.state.production.stream().filter(j->j.enabled&&j.worker.equals(id)).findFirst().orElse(null);
                if(old!=null){if(!old.employer.equals(k.employer)||old.outputs.keySet().stream().anyMatch(p.essentials::contains)||old.outputs.keySet().stream().anyMatch(item->p.stockCaps.containsKey(item)||p.kitchens.stream().anyMatch(kit->kit.inputs.containsKey(item))))continue;boolean spare=true;for(var item:old.outputs.keySet())if(stockCache.computeIfAbsent(item,key->stock(e,l,c,key))<Math.max(1,demand(e,l,c,item,old.periodTicks))*p.donorReserveCycles)spare=false;if(!spare)continue;}
                if(e.state.production.size()>=512){p.status="岗位数量已达上限";return;}var j=job(k,c.id,id,Math.max(now+k.periodTicks,old==null?0:old.nextTick));if(old!=null){old.enabled=false;old.status="暂存原岗位：转任厨房 "+j.id;}e.state.production.add(j);e.invalidateIndexes();p.reassignments++;p.status="一名居民转任厨房，下周期按实际原料和工资结算";e.log(now,"REASSIGN",old==null?"":old.id.toString(),j.id.toString(),0,id.toString());return;
            }p.status="厨房缺工，但没有可调配的居民或富余行业";
        }
    }
    static void maintain(MinecraftServer server,CityEconomy e,CityData.City c,CityLogistics.Policy p,long now){if(!p.supply.enabled)return;var l=CityEconomy.level(server,c);shops(e,l,c,p,now);staff(e,l,c,p.supply,now);}
}
