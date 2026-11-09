package com.muxigame.city;

import java.util.*;

/** Pure rural settlement rules. Quantities are portions; money is existing silver. */
public final class RuralRules {
    public enum Role { NONE, FARMER, COOK, MERCHANT, HUNTER, ARTISAN, VAGRANT, PROCUREMENT, BAKER, SUPPLIER }
    public enum Age { INFANT, CHILD, ADULT, YOUNG, MIDDLE, ELDER }
    public enum Marriage { NONE, FACTUAL, LEGAL }
    public static final class Person {
        public UUID id, family, lover, spouse, caregiver;
        public String name="居民";
        public Role role=Role.NONE;
        public Age age=Age.ADULT;
        public Marriage marriage=Marriage.NONE;
        public boolean female,pregnant,dead;
        public int infants, trainedHours, wheatPrice=3, breadPrice=5;
        public long stageTicks;
        public double nourishment=30, productionCredit, cookingCredit;
        public long workingTicks, cropTicks, huntingTicks, trainingTicks, totalWorkingTicks, hunts, emptyHunts, meatProduced;
        public FoodAge.State pendingAge=new FoodAge.State();public int huntRadius=128,pendingWheat;public long marketRevision;
        public Map<Integer,RuralMarket.Policy> market=new LinkedHashMap<>();
        public Long huntingSpot;
        public String workStatus="等待工作时段";
        public int meal(){return age==Age.INFANT?0:(age==Age.CHILD?1:2)+(pregnant?1:0)+infants;}
        public double maximum(){return meal()*15.0;}
        public int hunger(){if(dead||nourishment<=0)return 3;return nourishment<=maximum()/3?2:nourishment<=maximum()*2/3?1:0;}
    }
    public static final class Family {
        public UUID id, city;
        public List<UUID> members=new ArrayList<>();
        public int bakedThisHour;public boolean extraBread;
    }
    /** Each stock represents exactly one dedicated physical storage box. */
    public static final class Stock {
        public FoodAge.State[] ages=FoodAge.states(12);
        public UUID family;
        public int wheat,bread,ordinarySlots;
        public int[] raw=new int[5], cooked=new int[5];
        public int free(){int types=(wheat>0?1:0)+(bread>0?1:0);for(int n:raw)if(n>0)types++;for(int n:cooked)if(n>0)types++;return ordinarySlots+types>=27?0:Math.max(0,10-wheat-bread-Arrays.stream(raw).sum()-Arrays.stream(cooked).sum());}
    }
    public static double efficiency(int hours){return hours>=9?1:1-Math.pow(.5,Math.max(0,hours)+1);}
    public static int reserved(Family f,Map<UUID,Person> people){return members(f,people).stream().mapToInt(p->1+(p.pregnant?1:0)).sum();}
    public static void admit(Family f,Person p,Map<UUID,Person> people){
        if(f.members.contains(p.id))return;
        if(reserved(f,people)+(p.dead?0:1+(p.pregnant?1:0))>10)throw new IllegalArgumentException("家庭含孕期预留名额最多10人");
        f.members.add(p.id);p.family=f.id;
    }
    public static List<Person> members(Family f,Map<UUID,Person> people){return f.members.stream().map(people::get).filter(Objects::nonNull).filter(p->!p.dead).toList();}
    public static void refreshCaregivers(Map<UUID,Person> people){
        var attached=new HashMap<UUID,Integer>();
        for(var baby:people.values())if(!baby.dead&&baby.age==Age.INFANT&&baby.caregiver!=null){var guardian=people.get(baby.caregiver);if(guardian!=null&&Objects.equals(guardian.family,baby.family))attached.merge(guardian.id,1,Integer::sum);}
        for(var p:people.values()){double maximum=p.maximum(),fraction=maximum<=0?1:p.nourishment/maximum;p.infants=attached.getOrDefault(p.id,0);p.nourishment=p.maximum()*fraction;}
    }
    public static int demand(Family f,Map<UUID,Person> people){return members(f,people).stream().mapToInt(Person::meal).sum();}
    public static int count(List<Stock> stocks,boolean bread){return stocks.stream().mapToInt(s->bread?s.bread:s.wheat).sum();}
    public static int capacity(List<Stock> stocks){return stocks.stream().mapToInt(Stock::free).sum();}
    public static int put(List<Stock> stocks,boolean bread,int quantity){return FoodMoves.add(stocks,bread?1:0,quantity,0);}
    public static int take(List<Stock> stocks,boolean bread,int quantity){return FoodMoves.remove(stocks,bread?1:0,quantity).count();}
    public static int cook(List<Stock> stocks,int limit,int target,boolean surplus){
        int need=Math.max(0,target-count(stocks,true));
        int n=Math.min(limit,Math.min(count(stocks,false),need+(surplus&&need>0?1:0)));
        take(stocks,false,n);int added=put(stocks,true,n);if(added!=n)throw new IllegalStateException("加工容量不守恒");return n;
    }
    /** Sell newly harvested overflow, never the family's already-full reserve. */
    public static int harvest(List<Stock> home,int produced,Family merchant,UUID producer,List<Stock> market,int price,Map<UUID,Long> money){
        int kept=put(home,false,produced),extra=produced-kept;
        int sold=(int)Math.min(Math.min(extra,capacity(market)),balance(merchant,money)/price);
        if(sold>0&&pay(merchant,producer,(long)sold*price,money))put(market,false,sold);
        return kept+sold;
    }
    /** Meal settlement is simultaneous deficit allocation, so list order cannot starve children. */
    public static void meal(List<Person> people,List<Stock> stocks){
        for(var p:people)if(!p.dead&&p.meal()>0)p.nourishment=Math.max(0,p.nourishment-p.meal());
        int available=count(stocks,true)+stocks.stream().mapToInt(s->Arrays.stream(s.cooked).sum()).sum(),eaten=0;
        while(eaten<available){Person next=people.stream().filter(p->!p.dead&&p.meal()>0&&p.nourishment+1<=p.maximum()+1e-8).min(Comparator.comparingDouble(p->p.nourishment/p.maximum())).orElse(null);if(next==null)break;next.nourishment++;eaten++;}
        int bread=take(stocks,true,eaten),left=eaten-bread;for(int type=0;type<5&&left>0;type++)left-=RuralMeat.take(stocks,true,type,left);
        for(var p:people)if(p.meal()>0&&p.nourishment<=0)p.dead=true;
    }
    public static long balance(Family f,Map<UUID,Long> money){return f.members.stream().mapToLong(id->money.getOrDefault(id,0L)).sum();}
    public static boolean pay(Family from,UUID recipient,long amount,Map<UUID,Long> money){
        if(amount<0||balance(from,money)<amount)return false;
        long left=amount;for(UUID id:from.members){long n=Math.min(left,money.getOrDefault(id,0L));money.put(id,money.getOrDefault(id,0L)-n);left-=n;}
        money.merge(recipient,amount,Long::sum);return true;
    }
    public static int trade(Family buyer,UUID seller,List<Stock> source,List<Stock> target,boolean bread,int wanted,int price,Map<UUID,Long> money){
        if(price<=0||buyer.members.contains(seller))return 0;
        int n=(int)Math.min(Math.min(Math.max(0,wanted),Math.min(count(source,bread),capacity(target))),balance(buyer,money)/price);
        if(n==0||!pay(buyer,seller,(long)n*price,money))return 0;
        FoodMoves.move(source,target,bread?1:0,n);return n;
    }
}
