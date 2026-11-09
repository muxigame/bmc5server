package com.muxigame.city;

import java.util.*;
import net.minecraft.server.MinecraftServer;

/** Cash-surplus distribution, explicitly not a full accrual-profit calculation. */
public final class CitySociety {
    private static final Set<String> CASH_KINDS=Set.of("PRODUCTION","WAGE","TRADE","CONSUMPTION");
    public static final class Policy {
        public boolean enabled,autoRecruit;
        public int sharePercent=25,externalPressure;
        public long reserve=500,recruitWealth=1000,periodTicks=24000,nextTick,lastDistributed;
        public String status="尚未结算";
    }
    static void validate(CityEconomy.State s){
        CityEconomy.require(s.society!=null&&s.society.size()<=512&&s.businessNet!=null&&s.businessNet.size()<=16384,"社会配置过大");
        s.businessNet.forEach((a,v)->{CityEconomy.account(a);CityEconomy.require(v!=null&&v>=-CityEconomy.LIMIT&&v<=CityEconomy.LIMIT,"经营净现金流越界");});
        for(var entry:s.society.entrySet()){var p=entry.getValue();CityEconomy.require(entry.getKey()!=null&&p!=null,"社会配置缺失");
            CityEconomy.require(p.sharePercent>=0&&p.sharePercent<=90&&p.externalPressure>=0&&p.externalPressure<=100,"分配比例0–90，外部压力0–100");
            CityEconomy.require(p.reserve>=0&&p.reserve<=CityEconomy.LIMIT&&p.recruitWealth>=1&&p.recruitWealth<=CityEconomy.LIMIT&&p.periodTicks>=200&&p.periodTicks<=24000000&&p.nextTick>=0&&p.lastDistributed>=0&&p.lastDistributed<=CityEconomy.LIMIT,"社会金额/周期越界");
            p.status=p.status==null?"尚未结算":p.status.substring(0,Math.min(160,p.status.length()));
        }
    }
    static Set<String> businesses(CityEconomy.State s,CityData.City c){Set<String> out=new TreeSet<>();for(UUID w:c.warehouses.keySet())if(s.warehouseOwners.containsKey(w))out.add(s.warehouseOwners.get(w));return out;}
    static void references(CityEconomy.State s,CityData data){for(var entry:s.society.entrySet()){var c=data.require(entry.getKey());if(!entry.getValue().enabled)continue;var owned=businesses(s,c);for(var other:data.cities())if(other!=c){var shared=businesses(s,other);shared.retainAll(owned);CityEconomy.require(shared.isEmpty(),"启用剩余分配前须拆分跨城共用经营账户");}}}
    static void flow(CityEconomy.State s,String kind,String from,String to,long amount){
        if(amount<=0||from.equals(to)||!CASH_KINDS.contains(kind))return;
        if(s.ownerIndex==null)s.ownerIndex=new HashSet<>(s.warehouseOwners.values());
        if(s.ownerIndex.contains(from))s.businessNet.compute(from,(k,v)->Math.max(-CityEconomy.LIMIT,(v==null?0:v)-amount));
        if(s.ownerIndex.contains(to))s.businessNet.compute(to,(k,v)->Math.min(CityEconomy.LIMIT,(v==null?0:v)+amount));
    }
    static long pool(long balance,long net,Policy p){return Math.min(Math.max(0,balance-p.reserve),Math.max(0,net)*p.sharePercent/100);}
    static void distribute(CityEconomy e,CityData.City c,Policy p,long tick){
        var residents=e.state.living.values().stream().filter(v->v.enabled&&v.city.equals(c.id)&&c.residents.contains(v.npc)).toList();
        if(p.autoRecruit)for(var v:residents)if(e.balance(CityEconomy.person(v.npc))>=p.recruitWealth)v.distributor=true;
        var recipients=residents.stream().filter(v->v.distributor).sorted(Comparator.comparing(v->v.npc)).toList();p.lastDistributed=0;
        if(recipients.isEmpty()){p.status="无已启用的分配者，保留经营现金流";return;}
        for(String account:businesses(e.state,c)){
            long net=e.state.businessNet.getOrDefault(account,0L),pool=pool(e.balance(account),net,p);long each=pool/recipients.size();
            // Preflight all recipients before changing this business account.
            if(recipients.stream().anyMatch(v->CityEconomy.person(v.npc).equals(account)||e.balance(CityEconomy.person(v.npc))>CityEconomy.LIMIT-each))continue;
            if(each>0)for(var v:recipients){e.transfer(account,CityEconomy.person(v.npc),each,CityEconomy.Purpose.DISTRIBUTION,"城市经营剩余分配 "+c.id,tick);p.lastDistributed=Math.min(CityEconomy.LIMIT,p.lastDistributed+each);}
            if(net>0)e.state.businessNet.put(account,0L);
        }
        p.status="本期真实分配"+p.lastDistributed+"整币；分配者"+recipients.size()+"人；保留周转金"+p.reserve;
    }
    static void run(MinecraftServer server,CityEconomy e,UUID city,Policy p,long now){
        p.nextTick=now+p.periodTicks;
        try{var c=CityData.get(server).require(city);var l=CityEconomy.level(server,c);boolean loaded=c.residents.stream().anyMatch(id->{var entity=l.getEntity(id);return entity!=null&&entity.isAlive()&&c.includes(entity.blockPosition())||CitySimulation.available(l,c,id);});CityEconomy.require(loaded,"居民未加载，分配暂停");distribute(e,c,p,now);}catch(IllegalArgumentException ex){p.status=ex.getMessage();}
        e.state.revision++;e.setDirty();
    }
    public static int pressure(CityEconomy e,CityData.City c){
        if(e.state.living.values().stream().noneMatch(p->p.enabled&&p.distributor&&p.city.equals(c.id)&&c.residents.contains(p.npc)))return 0;
        if(c.residents.isEmpty())return 0;int elite=0;long total=0,wealth=0;
        for(UUID id:c.residents){long v=e.balance(CityEconomy.person(id));total+=v;var profile=e.state.living.get(id);if(profile!=null&&profile.enabled&&profile.city.equals(c.id)&&profile.distributor){elite++;wealth+=v;}}
        if(elite==0||total==0)return 0;double populationShare=(double)elite/c.residents.size(),wealthShare=(double)wealth/total;
        return Math.clamp((int)Math.round(Math.max(0,wealthShare-populationShare)*20+populationShare*5),0,20);
    }
    public static String risk(int stability){return stability<20?"高（外部冲击区）":stability<40?"中等":stability<55?"不满 / 低风险":"低";}
}
