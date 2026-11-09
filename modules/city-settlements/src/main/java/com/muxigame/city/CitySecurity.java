package com.muxigame.city;

import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;

/** Paid, present guards provide order. Order does not erase social grievances. */
public final class CitySecurity {
    public static final class Policy {
        public boolean enabled;
        public String payer="",status="未启用治安结算";
        public long wage=3,periodTicks=6000,nextTick,lastTick,lastCost;
        public int baseOrder=40,servicePerResident=5,externalCrime,repression;
        public int recoveryPerCycle=2,decayPerCycle=2;
        public int order=40,crime,activeGuards,absentGuards,unpaidGuards,coverage;
    }
    static void validate(CityEconomy.State s){
        CityEconomy.require(s.security!=null&&s.security.size()<=512,"治安配置最多512城");
        for(var entry:s.security.entrySet()){var p=entry.getValue();CityEconomy.require(entry.getKey()!=null&&p!=null,"治安配置缺失");
            CityEconomy.require(p.payer!=null&&(!p.enabled||s.accounts.containsKey(p.payer)),"启用治安前须指定已有付款账户");
            if(!p.payer.isEmpty())CityEconomy.account(p.payer);
            CityEconomy.require(p.wage>=0&&p.wage<=CityEconomy.LIMIT&&p.periodTicks>=200&&p.periodTicks<=24000000&&p.nextTick>=0&&p.lastTick>=0&&p.lastCost>=0&&p.lastCost<=CityEconomy.LIMIT,"治安工资或周期无效");
            CityEconomy.require(p.baseOrder>=0&&p.baseOrder<=100&&p.servicePerResident>=1&&p.servicePerResident<=100&&p.externalCrime>=0&&p.externalCrime<=100&&p.repression>=0&&p.repression<=100,"治安参数越界");
            CityEconomy.require(p.recoveryPerCycle>=0&&p.recoveryPerCycle<=20&&p.decayPerCycle>=0&&p.decayPerCycle<=20,"每期治安恢复/衰减须0–20");
            CityEconomy.require(p.order>=0&&p.order<=100&&p.crime>=0&&p.crime<=100&&p.coverage>=0&&p.coverage<=100&&p.activeGuards>=0&&p.activeGuards<=128&&p.absentGuards>=0&&p.absentGuards<=128&&p.unpaidGuards>=0&&p.unpaidGuards<=128,"治安快照越界");
            p.status=p.status==null?"尚未运行":p.status.substring(0,Math.min(160,p.status.length()));
        }
        Map<UUID,Integer> counts=new HashMap<>();
        for(var v:s.living.values())if(v.guard){CityEconomy.require(v.guardPower>=1&&v.guardPower<=100,"守卫训练值须1–100");CityEconomy.require(counts.merge(v.city,1,Integer::sum)<=128,"每城最多128名登记守卫");CityEconomy.require(s.production.stream().noneMatch(j->j.enabled&&j.worker.equals(v.npc)),"守卫不能同时执行启用的生产岗位，请先停用生产");}
    }
    static void references(CityEconomy.State s,CityData data){for(var id:s.security.keySet())data.require(id);}
    static int coverage(int power,int population,int demand){return Math.clamp((int)((long)power*100/Math.max(1,(long)population*demand)),0,100);}
    static int order(int base,int coverage,int crime,int repression){return Math.clamp(base+coverage/2-crime/3+repression/5,0,100);}
    static void pay(CityEconomy e,Policy p,UUID npc,UUID city,long now){
        CityEconomy.require(!p.payer.equals(CityEconomy.person(npc)),"守卫不能向自己支付工资");
        e.transfer(p.payer,CityEconomy.person(npc),p.wage,CityEconomy.Purpose.WAGE,"城市守卫工资 "+city,now);
    }
    static void cycle(MinecraftServer server,CityEconomy e,CityData.City c,Policy p,long now){
        var l=CityEconomy.level(server,c);
        CityEconomy.require(c.residents.stream().anyMatch(id->{var v=l.getEntity(id);return v!=null&&v.isAlive()&&c.includes(v.blockPosition())||CitySimulation.available(l,c,id);}),"本城居民未加载，治安暂停");
        p.activeGuards=0;p.absentGuards=0;p.unpaidGuards=0;p.lastCost=0;int power=0;
        // Rotating order avoids permanently favouring the first UUID during wage shortages.
        var guards=e.state.living.values().stream().filter(v->v.guard&&v.city.equals(c.id)&&c.residents.contains(v.npc)).sorted(Comparator.comparing(v->v.npc)).toList();
        int shift=guards.isEmpty()?0:(int)((now/p.periodTicks)%guards.size());
        for(int i=0;i<guards.size();i++){var v=guards.get((i+shift)%guards.size());
            if(!WorkHours.active(server,c,v.npc)){p.absentGuards++;continue;}
            try{CityEconomy.resident(l,c,v.npc);}catch(IllegalArgumentException ex){p.absentGuards++;continue;}
            if(p.payer.equals(CityEconomy.person(v.npc))){p.unpaidGuards++;continue;}
            try{pay(e,p,v.npc,c.id,now);}catch(IllegalArgumentException ex){p.unpaidGuards++;continue;}
            power+=CitySimulation.guardPower(l,c,v.npc,v.guardPower);p.activeGuards++;p.lastCost=Math.min(CityEconomy.LIMIT,p.lastCost+p.wage);
        }
        int shortages=0,known=0;for(var v:e.state.living.values())if(v.enabled&&v.city.equals(c.id)&&c.residents.contains(v.npc)&&v.lastTick>0){known++;if(!v.lastEssentials)shortages++;}
        p.crime=Math.clamp(15+CitySociety.pressure(e,c)+(known==0?0:shortages*30/known)+p.externalCrime,0,100);
        p.coverage=coverage(power,c.residents.size(),p.servicePerResident);p.order=order(p.baseOrder,p.coverage,p.crime,p.repression);p.lastTick=now;
        int actual=CityLaw.patrol(server,c.id,p.order,p.activeGuards>0,p.recoveryPerCycle,p.decayPerCycle,now);
        p.status="治安"+actual+" / 值勤目标"+p.order+"；在岗"+p.activeGuards+" / 缺勤"+p.absentGuards+" / 欠薪"+p.unpaidGuards+"；实付"+p.lastCost;
        e.log(now,"SECURITY",p.payer,"",p.lastCost,p.status);
    }
    static void run(MinecraftServer server,CityEconomy e,UUID city,Policy p,long now){
        p.nextTick=now+p.periodTicks;
        try{cycle(server,e,CityData.get(server).require(city),p,now);}catch(IllegalArgumentException ex){p.status="暂停："+ex.getMessage();p.activeGuards=0;p.coverage=0;p.order=p.baseOrder;p.lastTick=0;}
        e.state.revision++;e.setDirty();
    }
    public static int riskIndex(CityEconomy e,CityData.City c){
        int stability=CityLiving.stability(e,c);var p=e.state.security.get(c.id);if(p==null||!p.enabled||p.lastTick==0)return stability;
        var society=e.state.society.get(c.id);int external=society==null?0:society.externalPressure;
        // Keep natural dynamics above the high-riot threshold, but retain external shocks.
        int actual=e.serverContext==null?p.order:CityLaw.security(e.serverContext,c.id);
        int natural=Math.max(20,stability+external+(actual-50)/5-p.repression/10);
        return Math.clamp(natural-external-p.externalCrime/5,0,100);
    }
    public static String summary(CityEconomy e,CityData.City c){var p=e.state.security.get(c.id);return p==null||!p.enabled?"治安未启用":p.status;}
}

