package com.muxigame.city;

import java.util.*;
import net.minecraft.server.MinecraftServer;

/** Explicit, finite consumption baskets. No stock, credit or income is invented. */
public final class CityLiving {
    public static final class Need {
        public String item="minecraft:bread",category="口粮",status="尚未结算";
        public int quantity=1,comfort=25,tier;
        public boolean essential=true;
    }
    public static final class Profile {
        public UUID npc,city;
        public boolean enabled,distributor,guard;
        public boolean basicMode; public int foodPerHour=2; public UUID basicWarehouse; public long foodCredit;
        public int guardPower=10;
        public long periodTicks=6000,nextTick,income,lastIncome,lastCost,lastBudget,lastTick;
        public long incomeUnit=3;
        public int baseComfort=10,need=30,minNeed=10,maxNeed=90,level,adaptCycles=120,expandCycles=6,step=1;
        public int comfortableCycles,affordableCycles,actual,contribution;
        public boolean lastCovered,lastEssentials;
        public String status="尚未结算";
        public List<Need> basket=new ArrayList<>();
    }
    public static Profile defaults(UUID city,UUID npc){
        Profile p=new Profile();p.city=city;p.npc=npc;
        String[] items={"minecraft:bread","minecraft:apple","minecraft:leather","minecraft:book"};
        String[] names={"口粮","改善食品","衣物耗材","文化用品"};
        for(int i=0;i<4;i++){Need n=new Need();n.item=items[i];n.category=names[i];n.tier=i;n.essential=i==0;n.comfort=i==0?25:20;p.basket.add(n);}return p;
    }
    static boolean active(CityEconomy.State s,UUID npc){var p=s.living.get(npc);return p!=null&&p.enabled;}
    static void income(CityEconomy.State s,String to,long amount){
        if(!to.startsWith("person:"))return;
        try{var p=s.living.get(UUID.fromString(to.substring(7)));if(p!=null&&p.enabled)p.income=Math.min(CityEconomy.LIMIT,p.income+amount);}catch(IllegalArgumentException ignored){}
    }
    static void validate(CityEconomy.State s){
        check(s.living!=null&&s.living.size()<=512,"生活配置最多512人");
        for(var entry:s.living.entrySet()){
            var p=entry.getValue();check(p!=null&&p.npc!=null&&p.npc.equals(entry.getKey())&&p.city!=null,"生活配置NPC/城市缺失");
            check(s.accounts.containsKey(CityEconomy.person(p.npc)),"生活配置缺少个人账户");
            if(p.basicMode)check(p.basicWarehouse!=null&&p.foodPerHour>=1&&p.foodPerHour<=64&&p.foodCredit>=0&&p.foodCredit<72000&&p.periodTicks==1200&&p.basket!=null&&p.basket.size()==1,"基础生活配置无效：需粮仓、1–64份/小时和一分钟结算周期");
            check(p.periodTicks>=200&&p.periodTicks<=24000000&&p.nextTick>=0&&p.lastTick>=0,"生活周期或时间无效");
            check(p.baseComfort>=0&&p.baseComfort<=100&&p.minNeed>=0&&p.minNeed<=p.need&&p.need<=p.maxNeed&&p.maxNeed<=100,"舒适度/需求范围须0–100且下限≤需求≤上限");
            check(p.level>=0&&p.level<=3&&p.adaptCycles>=1&&p.adaptCycles<=1000&&p.expandCycles>=1&&p.expandCycles<=1000&&p.step>=0&&p.step<=20&&p.incomeUnit>=1&&p.incomeUnit<=CityEconomy.LIMIT&&p.expandCycles<=p.adaptCycles,"适应/扩张参数无效");
            check(p.income>=0&&p.income<=CityEconomy.LIMIT&&p.lastIncome>=0&&p.lastIncome<=CityEconomy.LIMIT&&p.lastCost>=0&&p.lastCost<=CityEconomy.LIMIT&&p.lastBudget>=0&&p.lastBudget<=CityEconomy.LIMIT,"生活金额越界");
            check(p.actual>=0&&p.actual<=100&&p.contribution>=-10&&p.contribution<=10&&p.comfortableCycles>=0&&p.comfortableCycles<=1000&&p.affordableCycles>=0&&p.affordableCycles<=1000,"生活历史数值越界");
            check(p.basket!=null&&!p.basket.isEmpty()&&p.basket.size()<=16,"消费清单须1–16项");
            Set<String> items=new HashSet<>();boolean essential=false;
            for(var n:p.basket){
                check(n!=null&&n.category!=null&&!n.category.isBlank()&&n.category.length()<=20&&items.add(n.item),"消费类别为空/过长或商品重复");CityEconomy.item(n.item);
                check(n.quantity>=1&&n.quantity<=64&&n.comfort>=0&&n.comfort<=100&&n.tier>=0&&n.tier<=3,"消费数量/舒适值/档次无效");
                check(!n.essential||n.tier==0,"必需品必须为基础档");essential|=n.essential;
                n.status=trim(n.status);
            }
            check(essential,"至少配置一项基础必需品");p.status=trim(p.status);
        }
    }
    private static String trim(String s){return s==null?"尚未结算":s.substring(0,Math.min(160,s.length()));}
    private static void check(boolean value,String error){CityEconomy.require(value,error);}
    /** Evaluate one completed period. Unknown prices count as uncovered, never as free. */
    static void finish(Profile p,int actual,boolean essentials,long budget,boolean priced,long spent,long tick){
        p.actual=Math.clamp(actual,0,100);p.lastEssentials=essentials;p.lastIncome=p.income;p.income=0;p.lastCost=spent;p.lastBudget=budget;p.lastTick=tick;
        p.lastCovered=priced&&p.lastIncome>=budget;
        // Low income can stagnate. High actual income shortens the growth interval;
        // owning savings is deliberately not a substitute for income.
        String change="";
        long multiple=p.lastIncome/p.incomeUnit;
        int interval=(int)Math.max(p.expandCycles,p.adaptCycles/Math.max(1,multiple));
        if(multiple>0)p.affordableCycles++;
        if(multiple>0&&p.affordableCycles>=interval){p.affordableCycles=0;p.need=Math.min(p.maxNeed,p.need+p.step);int max=p.basket.stream().mapToInt(n->n.tier).max().orElse(0);p.level=Math.max(p.level,Math.min(max,Math.max(0,(p.need-20)/20)));change="；收入推动需求增长";}
        int gap=p.actual-p.need;p.contribution=gap==0?0:Math.clamp((int)Math.ceil(Math.abs(gap)/10.0)*(gap>0?1:-1),-3,3);
        if(!essentials)p.contribution=-4;
        p.status="舒适"+p.actual+" / 需求"+p.need+"；稳定贡献"+(p.contribution>=0?"+":"")+p.contribution+(p.lastCovered?"；收入覆盖":"；收入未覆盖")+change;
    }
    static void cycle(MinecraftServer server,CityEconomy e,Profile p,long now){
        var c=CityData.get(server).require(p.city);var l=CityEconomy.level(server,c);CityEconomy.resident(l,c,p.npc);
        if(p.basicMode){CityBasics.cycle(e,l,c,p,now);return;}
        int actual=p.baseComfort;boolean essentials=true,priced=true;long budget=0,spent=0;
        // Essentials precede discretionary purchases; ties follow the author's list order.
        var needs=new ArrayList<>(p.basket);needs.sort(Comparator.comparing((Need n)->!n.essential).thenComparingInt(n->n.tier));
        for(var n:needs){
            if(n.tier>p.level){n.status="尚未形成需求";continue;}
            var offers=CityEconomy.rankOffers(e.offers(p.city,n.item,n.quantity),o->CitySimulation.distance(l,c,o.npc,p.npc));
            if(offers.isEmpty()){priced=false;if(n.essential)essentials=false;n.status="未满足：没有有效同城报价";continue;}
            long quoted=offers.getFirst().buyPrice*n.quantity;if(budget>CityEconomy.LIMIT-quoted)priced=false;budget=Math.min(CityEconomy.LIMIT,budget+quoted);
            boolean bought=false;String failure="商品不可用";
            for(var offer:offers)try{
                CityEconomy.resident(l,c,offer.npc);e.consumeGoods(CityEconomy.warehouse(l,c,offer.warehouse),CityEconomy.person(p.npc),e.owner(offer.warehouse),n.item,n.quantity,offer.buyPrice,now);
                long cost=offer.buyPrice*n.quantity;if(budget>CityEconomy.LIMIT-(cost-quoted))priced=false;budget=Math.min(CityEconomy.LIMIT,budget+cost-quoted);spent=Math.min(CityEconomy.LIMIT,spent+cost);actual+=n.comfort;n.status="已消费"+n.quantity+"，支出"+cost;bought=true;break;
            }catch(IllegalArgumentException ex){failure=ex.getMessage();}
            if(!bought){if(n.essential)essentials=false;n.status="未满足："+failure;}
        }
        finish(p,actual,essentials,budget,priced,spent,now);
        CitySimulation.consumed(server,e,p);
        e.log(now,"LIVING",CityEconomy.person(p.npc),"",spent,p.status);
    }
    /** A bounded social index, not a currency and not a sum multiplied by population. */
    public static int stability(CityEconomy e,CityData.City c){
        var profiles=e.state.living.values().stream().filter(p->p.enabled&&p.city.equals(c.id)&&c.residents.contains(p.npc)&&p.lastTick>0).toList();
        int social=CitySociety.pressure(e,c);var policy=e.state.society.get(c.id);int external=policy==null?0:policy.externalPressure;
        return Math.clamp(60+(int)Math.round(profiles.stream().mapToInt(p->p.contribution).average().orElse(0)*5)-social-external,0,100);
    }
}
