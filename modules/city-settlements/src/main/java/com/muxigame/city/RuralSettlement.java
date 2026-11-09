package com.muxigame.city;

import java.util.*;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Persisted opt-in rural simulation; ticks never load a chunk or wait for an actor. */
public final class RuralSettlement extends SavedData {
    public static final class Site {public UUID city,worker;public long pos,vacantSince,blockedSince,progressMark;public String kind="",expectedBlock="";public boolean present=true;}
    public static final class Box {public UUID city;public long pos,site;public boolean linked,present=true;public RuralRules.Stock stock=new RuralRules.Stock();public List<String> slots=new ArrayList<>();}
    public static final class State {
        public int schema=3;public int speed=1,stepCredit;public long simulationTicks,simulationDay;public boolean clockStarted;
        public Set<UUID> externalBuyer=new HashSet<>();public Map<String,Integer> priceRemainders=new LinkedHashMap<>();public Map<UUID,RuralMarket.Audit> audits=new LinkedHashMap<>();public long revision,nextMinute;public int minutes;
        public Map<UUID,WorkHours.Config> hours=new LinkedHashMap<>();
        public Map<String,HunterForest.Cell> forest=new LinkedHashMap<>();
        public Set<UUID> monitored=new HashSet<>();
        public Set<UUID> visualCities=new HashSet<>();
        public long monitorUntil,monitorNext;
        public long wheatProduced,breadProduced,meatProduced,meatCooked,foodEaten,meatSold,meatBought,tradedSilver,failedTrades;
        public Map<UUID,RuralRules.Person> people=new LinkedHashMap<>();
        public Map<UUID,RuralRules.Family> families=new LinkedHashMap<>();
        public Map<String,Site> sites=new LinkedHashMap<>();public Map<String,Box> boxes=new LinkedHashMap<>();
        public Set<UUID> enabled=new HashSet<>();public List<String> ledger=new ArrayList<>();
    }
    State state=new State();transient MinecraftServer serverContext;
    public static RuralSettlement get(MinecraftServer s){var d=s.overworld().getDataStorage().computeIfAbsent(new Factory<>(RuralSettlement::new,RuralSettlement::load),"city_rural");d.serverContext=s;return d;}
    static RuralSettlement load(CompoundTag t,HolderLookup.Provider provider){var d=new RuralSettlement();if(t.contains("Json"))d.state=CityEconomy.JSON.fromJson(new String(t.getByteArray("Json"),StandardCharsets.UTF_8),State.class);d.state.speed=Math.clamp(d.state.speed,1,SimulationClock.MAX_SPEED);d.state.stepCredit=Math.clamp(d.state.stepCredit,0,19);if(d.state.schema<=2){for(var p:d.state.people.values())if(p.role==RuralRules.Role.COOK)p.female=true;d.state.schema=3;}if(d.state.schema!=3||d.state.people.size()>512||d.state.boxes.size()>4096)throw new IllegalArgumentException("农村存档版本或容量错误");for(var f:d.state.families.values())if(RuralRules.reserved(f,d.state.people)>10)throw new IllegalArgumentException("家庭超员");return d;}
    @Override public CompoundTag save(CompoundTag t,HolderLookup.Provider provider){t.putByteArray("Json",CityEconomy.JSON.toJson(state).getBytes(StandardCharsets.UTF_8));return t;}
    RuralMarket.Audit audit(UUID city){return state.audits.computeIfAbsent(city,k->new RuralMarket.Audit());}
    static String key(UUID city,long pos){return city+":"+pos;}
    public static boolean active(MinecraftServer s,UUID city){return get(s).state.enabled.contains(city);}
    void log(String text){state.ledger.add(text);while(state.ledger.size()>80)state.ledger.removeFirst();}
    RuralRules.Person enroll(MinecraftServer server,CityData.City city,UUID id,String name){
        if(state.people.containsKey(id))return state.people.get(id);
        CityEconomy.require(state.people.size()<512&&city.residents.contains(id),"先登记为本城居民，最多512人");
        var p=new RuralRules.Person();p.id=id;p.name=name;var f=new RuralRules.Family();f.id=UUID.randomUUID();f.city=city.id;state.families.put(f.id,f);state.people.put(id,p);RuralRules.admit(f,p,state.people);
        var e=CityEconomy.get(server);String account=CityEconomy.person(id);if(!e.state.accounts.containsKey(account)){e.state.accounts.put(account,50L);e.log(server.overworld().getGameTime(),"RURAL_INITIAL", "",account,50,"首次登记成人初始银币");e.setDirty();}
        state.revision++;setDirty();return p;
    }
    private Map<UUID,List<RuralRules.Stock>> workStocks;
    List<RuralRules.Stock> stocks(UUID family){if(workStocks!=null)return workStocks.getOrDefault(family,List.of());return state.boxes.values().stream().filter(b->b.present&&b.linked&&Objects.equals(b.stock.family,family)).map(b->b.stock).toList();}
    List<RuralRules.Person> workers(RuralRules.Family f,RuralRules.Role role){return RuralRules.members(f,state.people).stream().filter(p->p.age.ordinal()>=2&&p.role==role&&state.sites.values().stream().anyMatch(s->s.present&&Objects.equals(s.worker,p.id))).toList();}
    void enable(MinecraftServer server,CityData.City city){
        state.enabled.add(city.id);CityMaintenance.enableDataCity(server,city);var maintenance=CityMaintenance.get(server);var plan=maintenance.state.cities.get(city.id);if(plan!=null)plan.collective=false;maintenance.setDirty();state.revision++;setDirty();
    }
    static long dayTime(MinecraftServer s,CityData.City city){if(city.works.finalized)return city.works.day;var d=get(s);return d.state.clockStarted&&d.state.enabled.contains(city.id)?d.state.simulationDay:CityEconomy.level(s,city).getDayTime();}
    static void tick(MinecraftServer server){var d=get(server);if(d.state.enabled.isEmpty())return;if(!d.state.clockStarted){d.state.clockStarted=true;d.state.simulationDay=server.overworld().getDayTime();}
        for(int step=0;step<SimulationClock.steps(server);step++){d.state.simulationTicks+=20;d.state.simulationDay+=20;CityRiskLab.beforeStep(server);CityCurrency.advance(server,20);for(UUID id:d.state.enabled){var c=CityData.get(server).cities.get(id);if(c!=null)FamilyLivelihood.daily(server,c);}workStep(server,d);if(d.state.simulationTicks%1200==0){d.state.minutes++;for(UUID id:new ArrayList<>(d.state.enabled)){var c=CityData.get(server).cities.get(id);if(c!=null)d.cycle(server,c,d.state.minutes%30==0,d.state.minutes%60==0);}}CityTrade.step(server);CityCurrency.settle(server);CityRiskLab.afterStep(server);CityModelLab.tick(server);}d.setDirty();}
    String report(MinecraftServer server,CityData.City city){var rows=new ArrayList<String>();for(var f:state.families.values())if(f.city.equals(city.id)){long silver=f.members.stream().mapToLong(id->CityEconomy.get(server).state.accounts.getOrDefault(CityEconomy.person(id),0L)).sum();rows.add("家庭 "+f.members.size()+"人 银币="+silver+" 小麦="+RuralRules.count(stocks(f.id),false)+" 面包="+RuralRules.count(stocks(f.id),true)+" 死亡="+f.members.stream().filter(id->state.people.get(id).dead).count());}return String.join("; ",rows);}
    /** Minute settlement consumes only work ticks earned by the server, including across reloads. */
    void cycle(MinecraftServer server,CityData.City city,boolean meal,boolean hour){
        if(CityTrade.urban(server,city.id)){RuralSuccession.tick(server,city);CityTrade.get(server).urbanCycle(server,city,meal);NpcStorage.refresh(server);setDirty();return;}
        CityTrade.beforeRural(server,city);
        RuralSuccession.tick(server,city);RuralRules.refreshCaregivers(state.people);
        var level=CityEconomy.level(server,city);
        for(var box:new ArrayList<>(state.boxes.values()))if(box.city.equals(city.id)&&level.hasChunkAt(BlockPos.of(box.pos))&&level.getBlockEntity(BlockPos.of(box.pos)) instanceof RuralBlocks.Link link)RuralTools.register(level,BlockPos.of(box.pos),link);
        var economy=CityEconomy.get(server);Map<UUID,Long> money=new HashMap<>();for(var p:state.people.values())money.put(p.id,economy.state.accounts.getOrDefault(CityEconomy.person(p.id),0L));
        if(city.works.finalized)money.put(RuralMarket.cityAccount(city.id),economy.balance("market:"+city.id));
        var families=state.families.values().stream().filter(f->f.city.equals(city.id)).toList();
        var merchants=families.stream().filter(f->workers(f,RuralRules.Role.MERCHANT).stream().anyMatch(p->p.workingTicks>0)).toList();
        var plan=CityMaintenance.get(server).state.cities.get(city.id);int plots=0;
        if(plan!=null)for(var entry:plan.baseline.entrySet()){if(!entry.getValue().contains("minecraft:wheat")||plan.damaged.contains(entry.getKey()))continue;var pos=BlockPos.of(entry.getKey());if(city.works.virtual||!level.hasChunkAt(pos)||level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.WHEAT))plots++;}
        // Allocation includes off-duty farmers, so taking a rest cannot give their land to another worker.
        for(var f:families){var stock=stocks(f.id);var farmers=workers(f,RuralRules.Role.FARMER);var cooks=workers(f,RuralRules.Role.COOK);var people=RuralRules.members(f,state.people);
            var fieldWorkers=farmers.isEmpty()&&people.stream().noneMatch(p->p.role==RuralRules.Role.FARMER||p.role==RuralRules.Role.HUNTER||p.role==RuralRules.Role.MERCHANT||p.role==RuralRules.Role.ARTISAN||p.role==RuralRules.Role.VAGRANT)?cooks:farmers;
            for(var p:fieldWorkers){int assigned=Math.min(20,plots);plots-=assigned;double factor=cooks.isEmpty()&&farmers.size()==1?.5:1;
                if(p.pendingWheat==0){p.productionCredit+=assigned*1.4*CityRiskLab.cropFactor(server,city.id)*RuralRules.efficiency(p.trainedHours)*factor*p.workingTicks/36000.0*CityWorks.workerEfficiency(server,city,p.id)/10000.0;p.cropTicks+=p.workingTicks;
                    if(p.cropTicks>=36000){p.cropTicks%=36000;p.pendingWheat=(int)p.productionCredit;p.productionCredit-=p.pendingWheat;audit(city.id).wheatMatured+=p.pendingWheat;}}
                int kept=Math.min(p.pendingWheat,RuralRules.capacity(stock));var keptPart=RuralStalls.take(p,kept);FoodMoves.add(stock,0,kept,keptPart.age());int accepted=kept;
                if(p.pendingWheat>0&&!merchants.isEmpty()&&!merchants.contains(f)){var buyer=merchants.getFirst();var head=workers(buyer,RuralRules.Role.MERCHANT).getFirst();var field=new RuralRules.Stock();field.wheat=p.pendingWheat;field.ages[0]=p.pendingAge.copy();int sold=RuralMarket.buy(this,head,buyer,p.id,List.of(field),stocks(buyer.id),0,p.pendingWheat,farmers.isEmpty()?1:RuralMarket.baseBuy(0),money);p.pendingWheat-=sold;p.pendingAge=field.ages[0].copy();accepted+=sold;}
                state.wheatProduced+=accepted;audit(city.id).wheatHarvested+=accepted;if(accepted>0)log(p.name+"收小麦"+accepted);if(p.pendingWheat>0){p.workStatus="成熟小麦留田等待仓位/收购："+p.pendingWheat;state.failedTrades++;}

            }
            for(var p:workers(f,RuralRules.Role.HUNTER)){
                p.huntingTicks+=Math.round(p.workingTicks*RuralRules.efficiency(p.trainedHours)*CityWorks.workerEfficiency(server,city,p.id)/10000.0);
                while(p.huntingTicks>=36000){p.huntingTicks-=36000;p.hunts++;if(level.random.nextInt(100)>=80){p.emptyHunts++;log(p.name+"本轮空手而归");continue;}int n=4+2*level.random.nextInt(3),accepted=0;for(int i=0;i<n;i++)accepted+=RuralMeat.put(stock,false,RuralMeat.type(level.random.nextInt(100)),1);state.meatProduced+=accepted;p.meatProduced+=accepted;log(p.name+"猎获入库"+accepted+"份");if(accepted<n){p.workStatus="肉食储藏空间不足";state.failedTrades++;}}
            }
        }
        // Main-food purchases precede cooking; no family buys meat with its next meal's budget.
        for(var f:families){var stock=stocks(f.id);boolean farmers=!workers(f,RuralRules.Role.FARMER).isEmpty(),cooks=!workers(f,RuralRules.Role.COOK).isEmpty(),head=!workers(f,RuralRules.Role.MERCHANT).isEmpty();int demand=RuralRules.demand(f,state.people);boolean grower=RuralStaples.grower(this,f);RuralStaples.collect(this,f,stock);
            for(var market:merchants){if(market==f)continue;var liveHeads=workers(market,RuralRules.Role.MERCHANT);if(liveHeads.isEmpty())continue;var merchant=liveHeads.getFirst();var marketStock=stocks(market.id);UUID seller=RuralRules.members(f,state.people).stream().findFirst().map(p->p.id).orElse(null);if(seller==null)continue;
                int breadSurplus=Math.max(0,RuralRules.count(stock,true)-demand*2);int sellPrice=cooks&&!farmers&&!head&&RuralRules.members(f,state.people).stream().noneMatch(p->p.spouse!=null)?3:4;
                int sold=RuralMarket.buy(this,merchant,market,seller,stock,marketStock,1,breadSurplus,sellPrice,money);
                int penalty=HouseholdCooking.penalty(this,f),wheatPrice=merchant.wheatPrice+penalty,breadPrice=merchant.breadPrice+penalty;CityJointTrade.provision(this,merchant,market,f,stock,marketStock,cooks||farmers,cooks||farmers?wheatPrice:breadPrice,money);
                boolean desperate=CityLiving.stability(economy,city)<30;
                int needBread=RuralCooking.breadWanted(this,city,f,stock);
                if(cooks||farmers){int wanted=grower?RuralStaples.deficit(this,f,stock):desperate||wheatPrice<3?RuralRules.capacity(stock):Math.max(0,demand*2-RuralRules.count(stock,false)-RuralRules.count(stock,true));int n=RuralRules.trade(f,merchant.id,marketStock,stock,false,wanted,wheatPrice,money);state.tradedSilver+=(long)n*wheatPrice;}
                // A wife processes wheat first; ready bread is an emergency/cheap purchase, not a duplicate order.
                if(!cooks&&!farmers||meal||breadPrice<5||desperate){int wanted=grower?needBread:desperate||breadPrice<5?RuralRules.capacity(stock):needBread;int n=RuralRules.trade(f,merchant.id,marketStock,stock,true,Math.min(wanted,RuralCooking.available(this,merchant,1,marketStock)),breadPrice,money);state.tradedSilver+=(long)n*breadPrice;}
                int meatSurplus=Math.max(0,RuralMeat.total(stock)-RuralMeat.reserve(f,state.people));
                for(int type=0;type<5&&meatSurplus>0;type++){int n=RuralMarket.buy(this,merchant,market,seller,stock,marketStock,type+2,meatSurplus,RuralMeat.BUY[type],money);meatSurplus-=n;state.meatSold+=n;}
                for(int type=0;type<5&&meatSurplus>0;type++){int n=RuralMarket.buy(this,merchant,market,seller,stock,marketStock,type+7,meatSurplus,RuralMarket.baseBuy(type+7),money);meatSurplus-=n;state.meatSold+=n;}if(meatSurplus>0)state.failedTrades++;
                if(!CityTrade.managed(server,city.id)&&cooks&&RuralRules.count(stock,false)+RuralRules.count(stock,true)>=demand*2){int wanted=Math.max(0,RuralMeat.reserve(f,state.people)-RuralMeat.total(stock));for(int type=0;type<5&&wanted>0;type++){long discretionary=Math.max(0,RuralRules.balance(f,money)-(long)demand*5);int n=RuralMeat.trade(f,merchant.id,marketStock,stock,type,(int)Math.min(wanted,discretionary/RuralMeat.SELL[type]),RuralMeat.SELL[type],money);wanted-=n;state.meatBought+=n;state.tradedSilver+=(long)n*RuralMeat.SELL[type];}}
                RuralFoodAccess.emergency(this,merchant,f,stock,marketStock,money,cooks||farmers);if(sold>0)log("家庭售面包"+sold);
            }
            if(city.works.finalized){boolean desperate=CityLiving.stability(economy,city)<30;int penalty=HouseholdCooking.penalty(this,f);var grain=city.housing.market.get("minecraft:wheat");if(cooks||farmers){int wanted=grower?RuralStaples.deficit(this,f,stock):desperate||grain!=null&&(grain.priceCents+50)/100+penalty<3?RuralRules.capacity(stock):Math.max(0,demand*2-RuralRules.count(stock,false)-RuralRules.count(stock,true));RuralMarket.buyFromCity(this,city,f,stock,0,wanted,penalty,money);}var breadQuote=city.housing.market.get("minecraft:bread");boolean cheap=breadQuote!=null&&(breadQuote.priceCents+50)/100+penalty<5;if(!cooks&&!farmers||meal||cheap||desperate){int wanted=grower?RuralCooking.breadWanted(this,city,f,stock):cheap||desperate?RuralRules.capacity(stock):Math.max(0,demand-RuralRules.count(stock,true));RuralMarket.buyFromCity(this,city,f,stock,1,wanted,penalty,money);}}
        }
        for(var f:families){var stock=stocks(f.id);var cooks=workers(f,RuralRules.Role.COOK);var farmers=workers(f,RuralRules.Role.FARMER);int demand=RuralRules.demand(f,state.people);RuralStaples.collect(this,f,stock);if(!cooks.isEmpty()||!farmers.isEmpty())RuralStalls.household(this,f,money,Math.max(0,demand*2-RuralRules.count(stock,false)-RuralRules.count(stock,true)));var processors=cooks.isEmpty()?farmers:cooks;
            for(var p:processors){p.cookingCredit=Math.min(30,p.cookingCredit+HouseholdCooking.ruralRate(p)*RuralRules.efficiency(p.trainedHours)*p.workingTicks/36000.0*CityWorks.workerEfficiency(server,city,p.id)/10000.0);int limit=(int)p.cookingCredit;int cooked=RuralRules.cook(stock,limit,demand*2,f.extraBread);p.cookingCredit-=cooked;state.breadProduced+=cooked;f.bakedThisHour+=cooked;if(RuralRules.count(stock,true)>demand*2)f.extraBread=false;
                if(!cooks.isEmpty()){int meat=RuralMeat.cook(stock,(int)(p.cookingCredit*30/HouseholdCooking.ruralRate(p)),Math.max(demand*2,RuralMeat.reserve(f,state.people)));p.cookingCredit-=meat*HouseholdCooking.ruralRate(p)/30;state.meatCooked+=meat;}
            }
            if(hour){f.extraBread=f.bakedThisHour>0&&level.random.nextFloat()<.1f;f.bakedThisHour=0;}
            if(meal){RuralSuccession.aid(server,city,f);int before=RuralRules.count(stock,true),meatBefore=RuralMeat.total(stock);RuralRules.meal(RuralRules.members(f,state.people),stock);int breadEaten=before-RuralRules.count(stock,true);state.foodEaten+=breadEaten;audit(city.id).breadEaten+=breadEaten;audit(city.id).meatEaten+=meatBefore-RuralMeat.total(stock);}
            for(var p:RuralRules.members(f,state.people)){p.stageTicks+=1200;p.workingTicks=0;}
        }
        for(var market:merchants){var liveHeads=workers(market,RuralRules.Role.MERCHANT);if(!liveHeads.isEmpty())RuralMarket.export(this,city.id,families,market,liveHeads.getFirst(),money);}
        for(var entry:money.entrySet())economy.state.accounts.put(city.works.finalized&&entry.getKey().equals(RuralMarket.cityAccount(city.id))?"market:"+city.id:CityEconomy.person(entry.getKey()),entry.getValue());economy.setDirty();NpcStorage.refresh(server);state.revision++;
    }
    static void workTick(MinecraftServer server){RuralWorkVisuals.tick(server);}
    static void workStep(MinecraftServer server,RuralSettlement d){
        for(var site:d.state.sites.values())site.present=RuralTools.available(server,site);
        var employedWorkers=new HashSet<UUID>();for(var site:d.state.sites.values())if(site.present&&site.worker!=null)employedWorkers.add(site.worker);
        d.workStocks=new HashMap<>();for(var box:d.state.boxes.values())if(box.present&&box.linked&&box.stock.family!=null)d.workStocks.computeIfAbsent(box.stock.family,k->new ArrayList<>()).add(box.stock);
        try{
        for(var p:d.state.people.values()){var family=d.state.families.get(p.family);if(p.dead||p.age.ordinal()<2||family==null||!d.state.enabled.contains(family.city))continue;var city=CityData.get(server).cities.get(family.city);if(city==null)continue;
            boolean employed=employedWorkers.contains(p.id);if(!employed&&p.role!=RuralRules.Role.ARTISAN&&FamilyLivelihood.household(server,p)==null){p.workStatus="没有已绑定的工作点";continue;}
            if(!WorkHours.active(server,city,p.id)){p.workStatus="休息时段／高稳定度禁止夜班／上晚已加班";continue;}
            if(p.role==RuralRules.Role.HUNTER&&!HunterForest.allocate(server,d,city,p))continue;
            p.workStatus="工作中";FamilyLivelihood.work(server,p);p.workingTicks+=20;p.totalWorkingTicks+=20;p.trainingTicks+=20;
            if(p.trainingTicks>=72000){p.trainingTicks-=72000;p.trainedHours=Math.min(10,p.trainedHours+1);}
        }
        }finally{d.workStocks=null;}
        d.setDirty();
    }
}





