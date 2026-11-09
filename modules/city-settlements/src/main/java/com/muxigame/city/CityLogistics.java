package com.muxigame.city;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;

/** Same-owner internal freight. Purchase/sale contracts remain separate from delivery. */
public final class CityLogistics {
    public static final class Policy {
        public boolean enabled,visual=true;
        public long periodTicks=200,nextTick,fee,shipments,moved;
        public int targetStock=128,reserveStock=128,minBatch=8,maxBatch=64,itemCursor,merchantCursor;
        public List<String> items=new ArrayList<>();
        public List<UUID> merchants=new ArrayList<>();
        public String status="尚未配送";
        public CitySupply.Policy supply=new CitySupply.Policy();
    }
    static void validate(CityEconomy.State s){
        CityEconomy.require(s.logistics!=null&&s.logistics.size()<=128,"物流城市数量超限");
        for(var p:s.logistics.values()){
            CityEconomy.require(p!=null,"物流配置缺失");CitySupply.validate(p.supply);
            CityEconomy.require(p!=null&&p.periodTicks>=200&&p.periodTicks<=24000000&&p.nextTick>=0&&p.fee>=0&&p.fee<=CityEconomy.LIMIT&&p.targetStock>=1&&p.targetStock<=4096&&p.reserveStock>=0&&p.reserveStock<=4096&&p.minBatch>=1&&p.minBatch<=p.maxBatch&&p.maxBatch<=256,"物流周期/批量/费用越界");
            CityEconomy.require(p.items!=null&&p.items.size()<=16&&new HashSet<>(p.items).size()==p.items.size()&&p.merchants!=null&&p.merchants.size()<=64&&new HashSet<>(p.merchants).size()==p.merchants.size(),"物流商品/商人列表无效");p.items.forEach(CityEconomy::item);
            CityEconomy.require(!p.enabled||!p.items.isEmpty()&&(!p.merchants.isEmpty()||p.supply.enabled&&!p.supply.shops.isEmpty()),"启用物流必须有商品与配送商人");
        }
    }
    static void references(CityEconomy.State e,CityData data){for(var entry:e.logistics.entrySet()){var c=data.require(entry.getKey());CitySupply.references(entry.getValue().supply,e,c);for(var id:entry.getValue().merchants)CityEconomy.require(c.residents.contains(id)&&e.accounts.containsKey(CityEconomy.person(id)),"配送商人须属于同城且有账户");}}
    record Store(UUID id,String owner,Container box,int count){}
    static void move(CityEconomy e,Container from,Container to,String item,int count,String owner,UUID merchant,long fee,long tick){
        CityEconomy.require(from!=to&&count>0&&count<=256,"配送库存或数量无效");
        var money=e.prepareMoney(owner,CityEconomy.person(merchant),fee);var source=new CityEconomy.Slots(from);var target=new CityEconomy.Slots(to);source.remove(item,count);target.add(item,count);
        source.commit();target.commit();e.state.accounts.putAll(money);if(!owner.equals(CityEconomy.person(merchant)))CityLiving.income(e.state,CityEconomy.person(merchant),fee);e.log(tick,"FREIGHT",owner,CityEconomy.person(merchant),fee,item+" ×"+count);
    }
    static boolean handles(CityEconomy e,CityData.City c,UUID warehouse,String item){return e.state.merchants.stream().anyMatch(o->o.enabled&&o.city.equals(c.id)&&o.warehouse.equals(warehouse)&&o.item.equals(item))||e.state.production.stream().anyMatch(j->j.enabled&&j.city.equals(c.id)&&j.warehouse.equals(warehouse)&&(j.inputs.containsKey(item)||j.outputs.containsKey(item)));}
    static List<Store> stores(CityEconomy e,net.minecraft.server.level.ServerLevel l,CityData.City c,String item){List<Store> result=new ArrayList<>();for(var w:c.warehouses.values())try{var box=CityEconomy.warehouse(l,c,w.id());result.add(new Store(w.id(),e.owner(w.id()),box,CitySimulation.contents(box).getOrDefault(item,0)));}catch(IllegalArgumentException ignored){}result.sort(Comparator.comparingInt(Store::count));return result;}
    static boolean ship(CityEconomy e,net.minecraft.server.level.ServerLevel l,CityData.City c,Policy p,UUID merchant,Store from,Store to,String item,int count,long now){
        if(count<1||from.id.equals(to.id)||!from.owner.equals(to.owner))return false;
        try{move(e,from.box,to.box,item,count,from.owner,merchant,p.fee,now);}catch(IllegalArgumentException ex){p.status="配送暂停："+ex.getMessage();return false;}
        p.shipments++;p.moved+=count;p.status="已配送 "+item+" ×"+count+"；累计"+p.shipments+"单/"+p.moved+"件";if(p.visual)CityWork.delivery(l,c,merchant,c.warehouses.get(from.id).pos(),c.warehouses.get(to.id).pos());return true;
    }
    static boolean route(CityEconomy e,net.minecraft.server.level.ServerLevel l,CityData.City c,Policy p,UUID merchant,String item,boolean emergency,long now){
        var all=stores(e,l,c,item);int outlets=(int)all.stream().filter(s->CitySupply.sells(e,l,c,s.id,item)).count();int target=emergency?Math.max(1,(CitySupply.demand(e,l,c,item,2400)*2+Math.max(1,outlets)-1)/Math.max(1,outlets)):p.targetStock;
        for(var to:all){boolean destination=emergency?CitySupply.sells(e,l,c,to.id,item):handles(e,c,to.id,item);if(!destination||to.count>=target)continue;
            for(int i=all.size()-1;i>=0;i--){var from=all.get(i);if(!p.supply.enabled&&!handles(e,c,from.id,item))continue;boolean served=CitySupply.sells(e,l,c,from.id,item);int reserve=emergency?(served?target:0):(!handles(e,c,from.id,item)&&p.supply.enabled?0:p.reserveStock);int count=Math.min(p.maxBatch,Math.min(target-to.count,from.count-reserve));if(count<(emergency?1:p.minBatch))continue;if(ship(e,l,c,p,merchant,from,to,item,count,now))return true;
        }}return false;
    }
    static boolean unblock(CityEconomy e,net.minecraft.server.level.ServerLevel l,CityData.City c,Policy p,UUID merchant,long now){
        for(var cap:p.supply.stockCaps.entrySet()){var all=stores(e,l,c,cap.getKey());for(int i=all.size()-1;i>=0;i--){var from=all.get(i);if(p.supply.overflowWarehouses.contains(from.id)||from.count<=cap.getValue())continue;
            for(var to:all)if(p.supply.overflowWarehouses.contains(to.id)){int count=Math.min(p.maxBatch,from.count-cap.getValue());if(ship(e,l,c,p,merchant,from,to,cap.getKey(),count,now))return true;}
        }}return false;
    }
    static void cycle(MinecraftServer server,CityEconomy e,CityData.City c,Policy p,long now){
        var l=CityEconomy.level(server,c);CitySupply.maintain(server,e,c,p,now);UUID merchant=null;
        for(int i=0;i<p.merchants.size();i++){UUID id=p.merchants.get(Math.floorMod(p.merchantCursor++,p.merchants.size()));if(CitySupply.present(l,c,id)){merchant=id;break;}}
        CityEconomy.require(merchant!=null,"配送商人缺勤或无离城记录");
        if(p.supply.enabled){for(var food:p.supply.essentials)if(CitySupply.shortFood(e,l,c,food)&&route(e,l,c,p,merchant,food,true,now))return;if(unblock(e,l,c,p,merchant,now))return;}
        String item=p.items.get(Math.floorMod(p.itemCursor++,p.items.size()));if(route(e,l,c,p,merchant,item,false,now))return;p.status="库存均衡或未达到最小配送量："+item;
    }
    public static void tick(MinecraftServer server){
        var e=CityEconomy.get(server);long now=server.overworld().getGameTime();var list=new ArrayList<>(e.state.logistics.entrySet());if(list.isEmpty())return;
        // One city inspected per tick; each shipment bounded to one item and <=256 units.
        var entry=list.get(Math.floorMod(server.getTickCount(),list.size()));var p=entry.getValue();if(RuralSettlement.active(server,entry.getKey())||CityMaintenance.collective(server,entry.getKey())||!p.enabled||now<p.nextTick)return;p.nextTick=now+p.periodTicks;
        try{cycle(server,e,CityData.get(server).require(entry.getKey()),p,now);}catch(IllegalArgumentException ex){p.status="暂停："+ex.getMessage();}e.state.revision++;e.setDirty();
    }
}
