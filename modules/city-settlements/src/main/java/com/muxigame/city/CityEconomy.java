package com.muxigame.city;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Server-thread economic engine. Money is an integer accounting unit; goods remain
 * in real containers. A transaction prepares all slots and balances before commit.
 */
public final class CityEconomy extends SavedData {
    public static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    public static final long LIMIT=1_000_000_000_000L;
    public static final class State {
        int schema=1;
        public long revision;
        public Map<String,Long> accounts=new TreeMap<>();
        public Map<UUID,String> warehouseOwners=new LinkedHashMap<>();
        transient Set<String> ownerIndex;
        public List<Job> production=new ArrayList<>();
        public List<Offer> merchants=new ArrayList<>();
        public List<Demand> consumption=new ArrayList<>();
        public Map<UUID,CityLiving.Profile> living=new LinkedHashMap<>();
        public Map<UUID,CitySimulation.Policy> simulation=new LinkedHashMap<>();
        public Map<UUID,CityLogistics.Policy> logistics=new LinkedHashMap<>();
        public Map<UUID,CitySecurity.Policy> security=new LinkedHashMap<>();
        public Map<UUID,CitySociety.Policy> society=new LinkedHashMap<>();
        public Map<String,Long> businessNet=new TreeMap<>();
        public Map<String,String> settlements=new TreeMap<>();
        public JsonObject extensions=new JsonObject();
    }
    public static final class Job {
        public UUID id,city,building,warehouse,worker;
        public String employer;
        public Map<String,Integer> inputs=new TreeMap<>(),outputs=new TreeMap<>(),requiredBlocks=new TreeMap<>();
        public int healthCredit;public long completedBatches;
        public long wage,periodTicks=24000,nextTick,workClock;public boolean onShift=true;
        public boolean enabled;
        /** Zero keeps legacy processing. Positive values harvest and replant real mature wheat. */
        public int harvestWheat;
        public boolean requireWorkerAtSite;
        public boolean workVisual;
        public String resourceSource="";
        public String status="尚未运行";
    }
    public static final class Offer {
        public UUID id,city,npc,warehouse;
        public String item;
        /** -1 disables that direction. Buy/sell are from the player's perspective. */
        public long buyPrice=-1,sellPrice=-1;
        public boolean enabled;
    }
    public static final class Demand {
        public UUID id,city,npc,offer;
        public int quantity=1;
        public long periodTicks=24000,nextTick;
        public boolean enabled;
        public String status="尚未运行";
    }
    public record Entry(long sequence,long tick,String kind,String from,String to,long amount,String detail){}
    public enum Purpose { TRANSFER, WAGE, DISTRIBUTION, TRADE, CONSUMPTION, POLICY, DEBT_PRINCIPAL, DEBT_INTEREST }
    public State state=new State();
    transient MinecraftServer serverContext;
    private long sequence;
    final Deque<Entry> journal=new ArrayDeque<>();
    public static CityEconomy get(MinecraftServer s){var e=s.overworld().getDataStorage().computeIfAbsent(new Factory<>(CityEconomy::new,CityEconomy::load),"city_economy");e.serverContext=s;return e;}
    public static String person(UUID id){return "person:"+id;}
    public static String treasury(UUID id){return "city:"+id;}
    public long balance(String account){return state.accounts.getOrDefault(account,0L);}
    static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
    static String account(String id){require(id!=null&&id.matches("[A-Za-z0-9_:-]{1,80}"),"账户ID无效");return id;}
    public static Item item(String id){ResourceLocation key=ResourceLocation.tryParse(id==null?"":id);require(key!=null&&BuiltInRegistries.ITEM.containsKey(key),"商品ID不存在："+id);Item item=BuiltInRegistries.ITEM.get(key);require(item!=Items.AIR,"空气不是商品");return item;}
    private static void amounts(Map<String,Integer> map,boolean blocks){
        require(map!=null&&map.size()<=16,"配方最多16类");
        for(var e:map.entrySet()){require(e.getValue()!=null&&e.getValue()>0&&e.getValue()<=4096,"物品/设施数量应为1–4096");if(blocks){var id=ResourceLocation.tryParse(e.getKey());require(id!=null&&BuiltInRegistries.BLOCK.containsKey(id)&&!e.getKey().equals("minecraft:air"),"设施方块无效");}else item(e.getKey());}
    }
    public static State decode(JsonObject json){
        // Gson numeric adapters otherwise truncate fractional JSON numbers into longs.
        exactNumbers(json);State s=JSON.fromJson(json,State.class);validate(s);return s;
    }
    private static void exactNumbers(JsonElement value){
        if(value.isJsonObject()){for(var entry:value.getAsJsonObject().entrySet())if(!entry.getKey().equals("extensions")&&!entry.getKey().equals("journalReadOnly"))exactNumbers(entry.getValue());}
        else if(value.isJsonArray())for(var element:value.getAsJsonArray())exactNumbers(element);
        else if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isNumber())value.getAsBigDecimal().longValueExact();
    }
    public static void validate(State s){
        require(s!=null&&s.schema==1&&s.revision>=0,"经济数据版本无效");
        require(s.accounts!=null&&s.accounts.size()<=16384&&s.warehouseOwners!=null&&s.warehouseOwners.size()<=16384,"账户或仓库数量超限");
        require(s.production!=null&&s.production.size()<=512&&s.merchants!=null&&s.merchants.size()<=512&&s.consumption!=null&&s.consumption.size()<=512,"经济任务数量超限");
        require(s.extensions!=null&&s.extensions.toString().length()<=65536,"扩展配置过大");
        require(s.settlements!=null&&s.settlements.size()<=4096,"结算回执超过4096条");
        s.settlements.forEach((k,v)->{account(k);require(v!=null&&v.length()<=512,"结算回执无效");});
        s.accounts.forEach((a,v)->{account(a);require(v!=null&&v>=0&&v<=LIMIT,"余额越界");});
        s.warehouseOwners.forEach((id,a)->{require(id!=null,"仓库ID缺失");account(a);require(s.accounts.containsKey(a),"仓库所有者账户不存在");});
        Set<UUID> ids=new HashSet<>(),workers=new HashSet<>(),consumers=new HashSet<>();Set<String> stockOffers=new HashSet<>();
        for(Job j:s.production){
            require(j!=null&&j.id!=null&&ids.add(j.id)&&j.city!=null&&j.building!=null&&j.warehouse!=null&&j.worker!=null,"生产ID/关联缺失或重复");
            account(j.employer);require(s.accounts.containsKey(j.employer)&&s.accounts.containsKey(person(j.worker)),"雇主或工人账户不存在");
            require(Objects.equals(s.warehouseOwners.get(j.warehouse),j.employer),"生产仓库必须归雇主所有");
            require(!j.enabled||workers.add(j.worker),"一个NPC不能同时执行两份生产任务");
            amounts(j.inputs,false);amounts(j.outputs,false);amounts(j.requiredBlocks,true);
            require(j.harvestWheat>=0&&j.harvestWheat<=256,"每期收割数量须0–256");
            require(!j.outputs.isEmpty()&&!j.requiredBlocks.isEmpty(),"生产必须有产物和实际设施要求");
            if(j.harvestWheat>0)require(j.inputs.isEmpty()&&j.outputs.size()==1&&j.outputs.getOrDefault("minecraft:wheat",0)==j.harvestWheat,"收割配方只能按成熟作物数量产出小麦，种子用于原地补种");
            else if(j.resourceSource!=null&&!j.resourceSource.isEmpty())CityWork.validateSource(j);
            else require(!j.inputs.isEmpty(),"加工生产必须有原料");
            require(j.harvestWheat==0||j.resourceSource==null||j.resourceSource.isEmpty(),"旧收割不能混用周期资源源");
            schedule(j.periodTicks,j.nextTick);require(j.wage>=0&&j.wage<=LIMIT,"工资越界");j.status=shortStatus(j.status);
        }
        for(Offer o:s.merchants){
            require(o!=null&&o.id!=null&&ids.add(o.id)&&o.city!=null&&o.npc!=null&&o.warehouse!=null,"商人ID/关联缺失或重复");
            item(o.item);require(s.warehouseOwners.containsKey(o.warehouse),"商人仓库无所有者账户");
            require(o.buyPrice>=-1&&o.buyPrice<=LIMIT&&o.sellPrice>=-1&&o.sellPrice<=LIMIT&&(o.buyPrice>=0||o.sellPrice>=0),"报价越界");
            require(!o.enabled||stockOffers.add(o.npc+"|"+o.item),"同一NPC不能同时启用重复商品报价");
        }
        CityLiving.validate(s);CitySociety.validate(s);CitySecurity.validate(s);CitySimulation.validate(s);CityLogistics.validate(s);
        for(Demand d:s.consumption){
            require(d!=null&&d.id!=null&&ids.add(d.id)&&d.city!=null&&d.npc!=null&&d.offer!=null,"消费ID/关联缺失或重复");
            require(s.accounts.containsKey(person(d.npc)),"居民账户不存在");require(!d.enabled||consumers.add(d.npc),"本版每居民只能启用一份消费计划");
            require(!d.enabled||!CityLiving.active(s,d.npc),"复杂消费已启用，请关闭同一NPC的旧口粮计划");
            require(d.quantity>0&&d.quantity<=64,"每期消费数量1–64");schedule(d.periodTicks,d.nextTick);
            Offer offer=s.merchants.stream().filter(o->o.id.equals(d.offer)).findFirst().orElseThrow(()->new IllegalArgumentException("消费报价不存在"));
            require(offer.city.equals(d.city)&&offer.buyPrice>=0,"消费报价必须属于同城并允许购买");
            require(new ItemStack(item(offer.item)).has(net.minecraft.core.component.DataComponents.FOOD),"居民口粮必须为食物");d.status=shortStatus(d.status);
        }
    }
    private static String shortStatus(String s){return s==null?"尚未运行":s.substring(0,Math.min(160,s.length()));}
    private static void schedule(long period,long next){require(period>=200&&period<=24_000_000&&next>=0,"周期须200–24000000tick，下次结算非负");}
    public static void references(State s,CityData data){
        for(var p:s.living.values()){var c=data.require(p.city);require(c.residents.contains(p.npc),"生活配置居民不属于该城市");if(p.basicMode)require(c.warehouses.containsKey(p.basicWarehouse),"基础生活粮仓未登记在所属聚落");}
        CitySociety.references(s,data);CitySecurity.references(s,data);CitySimulation.references(s,data);CityLogistics.references(s,data);
        for(var id:s.warehouseOwners.keySet())require(data.cities().stream().anyMatch(c->c.warehouses.containsKey(id)),"仓库登记不存在："+id);
        for(Job j:s.production){var c=data.require(j.city);require(c.buildings.containsKey(j.building)&&c.warehouses.containsKey(j.warehouse)&&c.residents.contains(j.worker),"生产关联建筑/仓库/居民不存在");require(c.buildings.get(j.building).bounds().volume()<=4096,"生产建筑最多4096格");}
        for(Offer o:s.merchants){var c=data.require(o.city);require(c.warehouses.containsKey(o.warehouse)&&c.residents.contains(o.npc),"商人必须绑定同城NPC和仓库");}
        for(Demand d:s.consumption)require(data.require(d.city).residents.contains(d.npc),"消费居民不属于城市");
    }
    public JsonObject export(){return JSON.toJsonTree(state).getAsJsonObject();}
    public void apply(JsonObject json,long revision,CityData cities,long tick){
        require(state.revision==revision,"经济版本已变化，请重新导出");
        State next=decode(json);require(next.revision==revision,"文件经济版本不匹配");references(next,cities);
        State before=state;CitySimulation.transition(before,next);state=next;
        log(tick,"ADMIN_IMPORT","","",0,"完整经济配置/状态调整；旧数据已由命令备份，账户差额="+(total(next)-total(before)));
    }
    private static long total(State s){return s.accounts.values().stream().mapToLong(Long::longValue).sum();}
    void log(long tick,String kind,String from,String to,long amount,String detail){
        CitySociety.flow(state,kind,from,to,amount);
        journal.addLast(new Entry(++sequence,tick,kind,from,to,amount,detail));while(journal.size()>256)journal.removeFirst();state.revision++;setDirty();
    }
    public void adjust(String id,long target,long tick,String reason){
        account(id);require(target>=0&&target<=LIMIT,"余额越界");require(state.accounts.containsKey(id)||state.accounts.size()<16384,"账户数量已满");
        long delta=target-balance(id);state.accounts.put(id,target);log(tick,"ADMIN_ADJUST","external",id,delta,reason);
    }
    Map<String,Long> prepareMoney(String from,String to,long amount){
        account(from);account(to);require(amount>=0&&amount<=LIMIT,"金额越界");
        require(state.accounts.containsKey(from)&&state.accounts.containsKey(to),"付款或收款账户不存在");
        require(balance(from)>=amount,"付款账户余额不足");
        Map<String,Long> balances=new HashMap<>(2);
        if(!from.equals(to)){require(balance(to)<=LIMIT-amount,"收款账户余额超限");balances.put(from,balance(from)-amount);balances.put(to,balance(to)+amount);}
        return balances;
    }
    /** Future policy and debt services use this same audited transfer, never direct balance edits. */
    public void transfer(String from,String to,long amount,Purpose purpose,String reference,long tick){
        var balances=prepareMoney(from,to,amount);state.accounts.putAll(balances);log(tick,purpose.name(),from,to,amount,reference);
        if(!from.equals(to)&&(purpose==Purpose.WAGE||purpose==Purpose.DISTRIBUTION))CityLiving.income(state,to,amount);
    }
    /** Idempotent contract payment for future policy/debt schedulers. Never issues new money. */
    public boolean settle(String transaction,String from,String to,long amount,Purpose purpose,long tick){
        account(transaction);String fingerprint=from+"|"+to+"|"+amount+"|"+purpose;
        String old=state.settlements.get(transaction);if(old!=null){require(old.equals(fingerprint),"结算ID被不同付款重复使用");return false;}
        require(state.settlements.size()<4096,"结算回执已满，需归档");transfer(from,to,amount,purpose,transaction,tick);state.settlements.put(transaction,fingerprint);setDirty();return true;
    }
    public static ServerLevel level(MinecraftServer server,CityData.City c){
        var l=server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(c.dimension)));require(l!=null,"城市维度未启用");return l;
    }
    static Container warehouse(ServerLevel l,CityData.City c,UUID id){
        Container simulated=CitySimulation.warehouse(l,c,id);if(simulated!=null)return simulated;
        return physicalWarehouse(l,c,id);
    }
    static Container physicalWarehouse(ServerLevel l,CityData.City c,UUID id){
        var w=c.warehouses.get(id);require(w!=null&&c.includes(w.pos()),"仓库不存在或已排除");require(l.hasChunkAt(w.pos()),"仓库区块未加载，暂停");
        var entity=l.getBlockEntity(w.pos());
        require(entity instanceof net.minecraft.world.level.block.entity.ChestBlockEntity||entity instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity,"经济仓库只支持箱子和木桶；容器可能已拆除");
        Container box=(Container)entity;if(get(l.getServer()).state.extensions.has("wheatMigration0814"))CityWheatMigration.inventory(box);require(box.getContainerSize()<=256,"容器超过256槽");return box;
    }
    static void resident(ServerLevel l,CityData.City c,UUID id){
        require(c.residents.contains(id),"NPC已不属于该城市");var e=l.getEntity(id);
        if(e==null&&CitySimulation.available(l,c,id))return;
        require(e instanceof LivingEntity&&e.isAlive(),"NPC未加载或已死亡，暂停");
        require(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals("customnpcs"),"不是CustomNPCs居民");
        require(c.includes(e.blockPosition()),"NPC不在城内，暂停");
    }
    /** Plain goods only: custom names/components are never silently stripped or sold. */
    static boolean plain(ItemStack stack,Item item){return !stack.isEmpty()&&ItemStack.isSameItemSameComponents(stack,new ItemStack(item));}
    static final class Slots {
        final Container target;final int size;final List<ItemStack> slots=new ArrayList<>();
        Slots(Container target){this(target,target.getContainerSize());}
        Slots(Container target,int size){this.target=target;this.size=size;for(int i=0;i<size;i++)slots.add(target.getItem(i).copy());}
        void remove(String id,int count){Item item=item(id);int remaining=count;for(var stack:slots)if(plain(stack,item)){int n=Math.min(remaining,stack.getCount());stack.shrink(n);remaining-=n;}require(remaining==0,"库存不足："+id);}
        void add(String id,int count){Item item=item(id);ItemStack sample=new ItemStack(item);int remaining=count;
            for(int i=0;i<size&&remaining>0;i++){var stack=slots.get(i);if(!stack.isEmpty()&&!plain(stack,item))continue;if(!target.canPlaceItem(i,sample))continue;
                int cap=Math.min(target.getMaxStackSize(sample),sample.getMaxStackSize());int n=Math.min(remaining,cap-stack.getCount());if(n<=0)continue;
                if(stack.isEmpty())slots.set(i,new ItemStack(item,n));else stack.grow(n);remaining-=n;}
            require(remaining==0,"目标容器没有足够空间："+id);
        }
        ItemStack takeTradeOne(String id){Item item=item(id);for(var stack:slots)if(plain(stack,item)||CityLaw.tradeGood(stack,item)){var out=stack.copyWithCount(1);stack.shrink(1);return out;}throw new IllegalArgumentException("库存不足："+id);}
        void addTrade(ItemStack sample){int remaining=sample.getCount();for(int i=0;i<size&&remaining>0;i++){var stack=slots.get(i);if(!stack.isEmpty()&&!ItemStack.isSameItemSameComponents(stack,sample))continue;if(!target.canPlaceItem(i,sample))continue;int cap=Math.min(target.getMaxStackSize(sample),sample.getMaxStackSize());int count=Math.min(remaining,cap-stack.getCount());if(count<=0)continue;if(stack.isEmpty())slots.set(i,sample.copyWithCount(count));else stack.grow(count);remaining-=count;}require(remaining==0,"目标容器没有足够空间");}
        void commit(){for(int i=0;i<size;i++)target.setItem(i,slots.get(i));target.setChanged();CitySimulation.afterCommit(target);}
    }
    public Map<String,Integer> inventory(MinecraftServer server,UUID city,UUID warehouse){
        Container c=warehouse(level(server,CityData.get(server).require(city)),CityData.get(server).require(city),warehouse);Map<String,Integer> result=new TreeMap<>();
        for(int i=0;i<c.getContainerSize();i++){var stack=c.getItem(i);if(!stack.isEmpty()){String key=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()+(plain(stack,stack.getItem())||CityLaw.tradeGood(stack,stack.getItem())?"":" [带组件，不参与交易]");result.merge(key,stack.getCount(),Integer::sum);}}return result;
    }
    public Offer offer(UUID id){return state.merchants.stream().filter(o->o.id.equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("报价不存在"));}
    String owner(UUID warehouse){String id=state.warehouseOwners.get(warehouse);require(id!=null&&state.accounts.containsKey(id),"仓库缺少经营账户");return id;}
    public void trade(ServerPlayer player,UUID id,boolean buy,long revision){
        require(state.revision==revision,"报价/账户已变化，请刷新");Offer o=offer(id);require(o.enabled,"报价已停用");
        var c=CityData.get(player.server).require(o.city);var l=level(player.server,c);require(l==player.serverLevel(),"商人不在当前维度");
        resident(l,c,o.npc);require(l.getEntity(o.npc).distanceToSqr(player)<=36,"请靠近商人（6格）");
        long price=buy?o.buyPrice:o.sellPrice;require(price>=0,"此交易方向未启用");
        String personal=person(player.getUUID()),business=owner(o.warehouse);
        var transferred=exchangeTrade(warehouse(l,c,o.warehouse),player.getInventory(),36,personal,business,o.item,buy,price,l.getGameTime(),id.toString());if(!buy)CityLaw.penalize(player,CityLaw.stolen(transferred),"sale",null);player.containerMenu.broadcastChanges();
    }
    void exchange(Container stock,Container inventory,int slots,String personal,String business,String item,boolean buy,long price,long tick,String reference){
        exchangeTrade(stock,inventory,slots,personal,business,item,buy,price,tick,reference);
    }
    ItemStack exchangeTrade(Container stock,Container inventory,int slots,String personal,String business,String item,boolean buy,long price,long tick,String reference){
        var money=prepareMoney(buy?personal:business,buy?business:personal,price);
        Slots goods=new Slots(stock),bag=new Slots(inventory,slots);
        ItemStack transferred=buy?goods.takeTradeOne(item):bag.takeTradeOne(item);if(buy)bag.addTrade(transferred);else goods.addTrade(transferred);
        goods.commit();bag.commit();state.accounts.putAll(money);
        log(tick,"TRADE",buy?personal:business,buy?business:personal,price,(buy?"买入 ":"卖出 ")+item+" x1 offer="+reference);return transferred;
    }
    public void produce(MinecraftServer server,Job j){
        var c=CityData.get(server).require(j.city);var l=level(server,c);resident(l,c,j.worker);
        if(j.requireWorkerAtSite){var b=c.buildings.get(j.building);require(b!=null&&b.bounds().contains(CitySimulation.position(l,c,j.worker)),"工人不在登记工作区，暂停");}
        productionAt(l,c,j);
    }
    void productionAt(ServerLevel l,CityData.City c,Job j){
        CityHousing.capacity(c,j.building,j.worker,this);
        j.healthCredit=Math.min(10000,j.healthCredit+CityWorks.workerEfficiency(l.getServer(),c,j.worker));if(j.healthCredit<10000){j.status="建筑完好率降低，累计加工进度";return;}long before=j.completedBatches;productionFull(l,c,j);if(j.completedBatches>before)j.healthCredit-=10000;
    }
    void productionFull(ServerLevel l,CityData.City c,Job j){
        var b=c.buildings.get(j.building);require(b!=null&&b.bounds().volume()<=4096,"生产建筑缺失或超过4096格");
        if(CitySimulation.produceAway(this,l,c,j))return;
        List<BlockPos> crops=new ArrayList<>();
        Map<String,Integer> blocks=new HashMap<>();for(int i=0;i<b.bounds().volume();i++){BlockPos p=b.bounds().at(i);require(c.includes(p)&&l.hasChunkAt(p),"建筑被排除或未加载，暂停");var block=l.getBlockState(p);String id=BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString();blocks.merge(id,1,Integer::sum);
            if(j.harvestWheat>0&&crops.size()<j.harvestWheat&&block.is(net.minecraft.world.level.block.Blocks.WHEAT)&&((net.minecraft.world.level.block.CropBlock)net.minecraft.world.level.block.Blocks.WHEAT).isMaxAge(block))crops.add(p.immutable());}
        require(crops.size()>=j.harvestWheat,"成熟小麦不足，等待自然生长");
        j.requiredBlocks.forEach((id,count)->require(blocks.getOrDefault(id,0)>=count,"设施损坏/不足："+id));
        require(owner(j.warehouse).equals(j.employer),"雇主不拥有该仓库");
        var box=warehouse(l,c,j.warehouse);Slots goods=CitySupply.prepare(this,l,c,j,box);if(goods==null)return;var money=prepareMoney(j.employer,person(j.worker),j.wage);
        // All inventory, wage and crop checks complete before any world mutation.
        for(BlockPos p:crops)l.setBlock(p,((net.minecraft.world.level.block.CropBlock)net.minecraft.world.level.block.Blocks.WHEAT).getStateForAge(0),3);
        goods.commit();state.accounts.putAll(money);j.completedBatches++;
        if(!j.employer.equals(person(j.worker)))CityLiving.income(state,person(j.worker),j.wage);
        log(l.getGameTime(),"PRODUCTION",j.employer,person(j.worker),j.wage,j.id+" 原料="+j.inputs+" 收割="+crops.size()+" 产物="+j.outputs);j.status=j.harvestWheat>0?"已收割补种并发薪":"已生产并发放工资";
    }
    public void consume(MinecraftServer server,Demand d){
        var c=CityData.get(server).require(d.city);var l=level(server,c);resident(l,c,d.npc);Offer o=offer(d.offer);
        require(o.enabled&&o.city.equals(d.city)&&o.buyPrice>=0,"口粮商人报价已停用");resident(l,c,o.npc);
        require(new ItemStack(item(o.item)).has(net.minecraft.core.component.DataComponents.FOOD),"口粮不是食物");
        consumeAt(warehouse(l,c,o.warehouse),person(d.npc),owner(o.warehouse),o.item,d.quantity,o.buyPrice,l.getGameTime());d.status="已购买并消耗口粮";
    }
    void consumeAt(Container stock,String personal,String business,String item,int quantity,long unitPrice,long tick){
        require(new ItemStack(item(item)).has(net.minecraft.core.component.DataComponents.FOOD)&&quantity>0&&quantity<=64,"口粮无效");
        consumeGoods(stock,personal,business,item,quantity,unitPrice,tick);
    }
    void consumeGoods(Container stock,String personal,String business,String item,int quantity,long unitPrice,long tick){
        item(item);require(quantity>0&&quantity<=64&&unitPrice>=0&&unitPrice<=LIMIT/quantity,"消费数量或价格越界");
        long cost=Math.multiplyExact(unitPrice,quantity);var money=prepareMoney(personal,business,cost);Slots goods=new Slots(stock);goods.remove(item,quantity);goods.commit();state.accounts.putAll(money);
        log(tick,"CONSUMPTION",personal,business,cost,personal+" 消耗 "+item+" x"+quantity);
    }
    private int jobCursor,demandCursor,livingCursor,societyCursor,securityCursor;
    public int lastProductionCount,lastConsumptionCount;
    public long lastEconomyNanos,maxEconomyNanos;
    private State indexedState;
    private Map<String,List<Offer>> offersByItem=new HashMap<>();
    private Map<UUID,List<Job>> jobsByCity=new HashMap<>();
    private List<CityLiving.Profile> livingTasks=List.of();
    private List<Map.Entry<UUID,CitySociety.Policy>> societyTasks=List.of();
    private List<Map.Entry<UUID,CitySecurity.Policy>> securityTasks=List.of();
    void invalidateIndexes(){indexedState=null;}
    void indexes(){if(indexedState==state)return;indexedState=state;offersByItem.clear();jobsByCity.clear();
        for(Offer o:state.merchants)if(o.enabled&&o.buyPrice>=0)offersByItem.computeIfAbsent(o.city+"|"+o.item,k->new ArrayList<>()).add(o);
        offersByItem.values().forEach(list->list.sort(Comparator.comparingLong(o->o.buyPrice)));
        for(Job j:state.production)if(j.enabled)jobsByCity.computeIfAbsent(j.city,k->new ArrayList<>()).add(j);
        livingTasks=new ArrayList<>(state.living.values());societyTasks=new ArrayList<>(state.society.entrySet());securityTasks=new ArrayList<>(state.security.entrySet());
    }
    List<Offer> offers(UUID city,String item,int quantity){indexes();return offersByItem.getOrDefault(city+"|"+item,List.of()).stream().filter(o->o.buyPrice<=LIMIT/quantity).toList();}
    static List<Offer> rankOffers(List<Offer> offers,java.util.function.ToDoubleFunction<Offer> distance){
        return offers.stream().sorted(Comparator.comparingLong((Offer o)->o.buyPrice).thenComparingDouble(distance)).toList();
    }
    boolean productionDue(UUID city,long now){indexes();return jobsByCity.getOrDefault(city,List.of()).stream().anyMatch(j->j.enabled&&j.onShift&&now>=j.nextTick);}
    record Batch(int cursor,int count){}
    static <T> Batch batch(List<T> tasks,int cursor,int limit,java.util.function.Predicate<T> due,java.util.function.Consumer<T> run){
        int count=0,n=tasks.size();if(n==0)return new Batch(0,0);cursor=Math.floorMod(cursor,n);
        for(int visited=0;visited<n&&count<limit;visited++){T task=tasks.get(cursor);cursor=(cursor+1)%n;if(due.test(task)){run.accept(task);count++;}}
        return new Batch(cursor,count);
    }
    public static void tick(MinecraftServer server){
        CitySimulation.tick(server);CityLogistics.tick(server);
        var e=get(server);long start=System.nanoTime(),now=server.overworld().getGameTime();e.indexes();
        if(server.getTickCount()%20==0)for(var job:e.state.production){var city=CityData.get(server).cities.get(job.city);if(city==null)continue;long elapsed=job.workClock==0?0:Math.max(0,now-job.workClock);job.workClock=now;job.onShift=WorkHours.active(server,city,job.worker);if(!job.onShift){job.nextTick+=elapsed;job.status="作息休息中";}}
        var jobs=batch(e.state.production,e.jobCursor,2,j->j.enabled&&j.onShift&&!CityMaintenance.collective(server,j.city)&&!RuralSettlement.active(server,j.city)&&now>=j.nextTick,j->{j.nextTick=now+j.periodTicks;try{e.produce(server,j);}catch(IllegalArgumentException ex){j.status=ex.getMessage();}e.state.revision++;e.setDirty();});e.jobCursor=jobs.cursor;e.lastProductionCount=jobs.count;
        // Finish due city production before city purchases, avoiding artificial ordering shortages.
        var demands=batch(e.state.consumption,e.demandCursor,4,d->d.enabled&&!CityMaintenance.collective(server,d.city)&&!RuralSettlement.active(server,d.city)&&now>=d.nextTick&&!e.productionDue(d.city,now),d->{d.nextTick=now+d.periodTicks;try{e.consume(server,d);}catch(IllegalArgumentException ex){d.status=ex.getMessage();}e.state.revision++;e.setDirty();});e.demandCursor=demands.cursor;
        var living=batch(e.livingTasks,e.livingCursor,4,p->p.enabled&&!RuralSettlement.active(server,p.city)&&now>=p.nextTick&&(CityMaintenance.collective(server,p.city)||!e.productionDue(p.city,now)),p->{p.nextTick=now+p.periodTicks;try{CityLiving.cycle(server,e,p,now);}catch(IllegalArgumentException ex){p.status="暂停："+ex.getMessage();p.contribution=0;p.income=0;p.comfortableCycles=0;p.affordableCycles=0;}e.state.revision++;e.setDirty();});e.livingCursor=living.cursor;e.lastConsumptionCount=living.count+demands.count;
        var society=batch(e.societyTasks,e.societyCursor,1,v->v.getValue().enabled&&!CityMaintenance.collective(server,v.getKey())&&!RuralSettlement.active(server,v.getKey())&&now>=v.getValue().nextTick,v->CitySociety.run(server,e,v.getKey(),v.getValue(),now));e.societyCursor=society.cursor;
        var security=batch(e.securityTasks,e.securityCursor,1,v->v.getValue().enabled&&!CityMaintenance.collective(server,v.getKey())&&!RuralSettlement.active(server,v.getKey())&&now>=v.getValue().nextTick,v->CitySecurity.run(server,e,v.getKey(),v.getValue(),now));e.securityCursor=security.cursor;
        e.lastEconomyNanos=System.nanoTime()-start;e.maxEconomyNanos=Math.max(e.maxEconomyNanos,e.lastEconomyNanos);
    }
    public boolean referencesCity(UUID city){return state.simulation.containsKey(city)||state.logistics.containsKey(city)|| state.security.containsKey(city)||state.society.containsKey(city)||state.living.values().stream().anyMatch(p->p.city.equals(city))||state.production.stream().anyMatch(j->j.city.equals(city))||state.merchants.stream().anyMatch(o->o.city.equals(city))||state.consumption.stream().anyMatch(d->d.city.equals(city))||balance(treasury(city))!=0;}
    // NBT strings use a 65535-byte modified-UTF limit. A full Chinese journal can
    // exceed it and vanilla writes an empty string. Byte arrays avoid that loss.
    @Override public CompoundTag save(CompoundTag t,HolderLookup.Provider provider){t.putByteArray("StateUtf8",JSON.toJson(state).getBytes(java.nio.charset.StandardCharsets.UTF_8));t.putLong("Sequence",sequence);t.putByteArray("JournalUtf8",JSON.toJson(journal).getBytes(java.nio.charset.StandardCharsets.UTF_8));return t;}
    public static CityEconomy load(CompoundTag t,HolderLookup.Provider provider){CityEconomy e=new CityEconomy();String state=t.contains("StateUtf8",Tag.TAG_BYTE_ARRAY)?new String(t.getByteArray("StateUtf8"),java.nio.charset.StandardCharsets.UTF_8):t.getString("State");e.state=decode(JsonParser.parseString(state).getAsJsonObject());e.sequence=t.getLong("Sequence");String journal=t.contains("JournalUtf8",Tag.TAG_BYTE_ARRAY)?new String(t.getByteArray("JournalUtf8"),java.nio.charset.StandardCharsets.UTF_8):t.getString("Journal");if(!journal.isBlank()){Entry[] entries=JSON.fromJson(journal,Entry[].class);if(entries!=null)for(Entry entry:entries)if(entry!=null)e.journal.addLast(entry);}while(e.journal.size()>256)e.journal.removeFirst();return e;}
}


