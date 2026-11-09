package com.muxigame.realneeds;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/** Only server handlers mutate these values. Player persistent data is serialized by NeoForge. */
public final class NeedsState {
    public static final String KEY="RealNeeds";
    private final double[] values=new double[Need.values().length];
    private final java.util.EnumMap<BodyStat,Double> body=new java.util.EnumMap<>(BodyStat.class);
    private final java.util.Map<String,Double> clocks=new java.util.HashMap<>();
    private final java.util.LinkedHashMap<String,Double> foodFatigue=new java.util.LinkedHashMap<>();
    private CompoundTag disease=new CompoundTag();
    private AilmentState ailments=new AilmentState();
    public AilmentState ailments(){return ailments;}
    private String lastFood="";
    private int foodStreak;
    private boolean lastFoodHighFat;
    public NeedsState() { java.util.Arrays.fill(values,100);for(var stat:BodyStat.values())body.put(stat,stat.initial); }
    public double get(Need n) { return values[n.ordinal()]; }
    public void set(Need n,double value) {
        if(!Double.isFinite(value)) throw new IllegalArgumentException("数值必须为有限数字");
        values[n.ordinal()]=Math.max(0,Math.min(100,value));
    }
    public void add(Need n,double value) { set(n,get(n)+value); }
    public boolean isSet(BodyStat stat){return body.containsKey(stat);}
    public double get(BodyStat stat){return body.getOrDefault(stat,0.0);}
    public void set(BodyStat stat,double value){
        if(!Double.isFinite(value)||value<0||value>stat.debugMax)throw new IllegalArgumentException(stat.label+"调试范围：0–"+stat.debugMax);
        body.put(stat,value);
        if(stat==BodyStat.FOOD_BOREDOM&&!lastFood.isEmpty())rememberFatigue(lastFood,value);
    }
    public void add(BodyStat stat,double value){set(stat,Math.clamp(get(stat)+value,0,100));}
    public void clear(BodyStat stat){set(stat,stat.initial);}
    public double clock(String name){return clocks.getOrDefault(name,0.0);}
    public void clock(String name,double value){clocks.put(name,Math.clamp(value,0,1_000_000_000));}
    public int advance(String name,double amount,double interval){double total=clock(name)+amount;int n=(int)(total/interval);clock(name,total-n*interval);return n;}
    public double fatigue(String food){return food.equals(lastFood)?get(BodyStat.FOOD_BOREDOM):foodFatigue.getOrDefault(food,0.0);}
    private void rememberFatigue(String food,double value){foodFatigue.remove(food);foodFatigue.put(food,value);while(foodFatigue.size()>64)foodFatigue.remove(foodFatigue.keySet().iterator().next());}
    public void recoverFatigue(double amount){foodFatigue.replaceAll((food,value)->Math.max(0,value-amount));if(!lastFood.isEmpty())body.put(BodyStat.FOOD_BOREDOM,foodFatigue.getOrDefault(lastFood,0.0));else add(BodyStat.FOOD_BOREDOM,-amount);}
    public void diseaseCheck(String reason,String detail,double chance,boolean passed){long count=disease.getLong("count");disease.putLong("count",count==Long.MAX_VALUE?count:count+1);disease.putString("reason",reason);disease.putString("detail",detail);disease.putDouble("chance",chance);disease.putBoolean("passed",passed);}
    public CompoundTag disease(){return disease.copy();}
    public String lastFood(){return lastFood;}
    public int foodStreak(){return foodStreak;}
    public boolean lastFoodHighFat(){return lastFoodHighFat;}
    /** Records completed meals, including nonconsecutive per-item fatigue. */
    public void recordFood(String id,boolean highFat){
        if(id.length()>256||net.minecraft.resources.ResourceLocation.tryParse(id)==null)throw new IllegalArgumentException("无效食物ID");
        foodStreak=id.equals(lastFood)?Math.min(1_000_000,foodStreak+1):1;lastFood=id;lastFoodHighFat=highFat;
    }
    public static NeedsState load(CompoundTag tag) {
        return load(tag,null);
    }
    public static NeedsState load(CompoundTag tag,NeedsRules rules){
        NeedsState s=new NeedsState();
        if(rules!=null){for(var n:Need.values())s.set(n,rules.initial(n));for(var b:BodyStat.values())s.set(b,rules.initial(b));}
        for(Need n:Need.values()) if(tag.contains(n.id,99)&&Double.isFinite(tag.getDouble(n.id))) s.set(n,tag.getDouble(n.id));
        var body=tag.getCompound("body");
        for(var stat:BodyStat.values())if(body.contains(stat.id,99)){
            double value=body.getDouble(stat.id);
            if(Double.isFinite(value))s.set(stat,Math.clamp(value,0,100));
        }
        if(!body.contains("physique",99)&&body.contains("weight",99)&&Double.isFinite(body.getDouble("weight")))s.set(BodyStat.PHYSIQUE,Math.clamp(body.getDouble("weight"),0,100));
        String food=tag.getString("last_food");int streak=tag.getInt("food_streak");
        if(!food.isEmpty()&&food.length()<=256&&net.minecraft.resources.ResourceLocation.tryParse(food)!=null&&streak>0){s.lastFood=food;s.foodStreak=Math.min(1_000_000,streak);s.lastFoodHighFat=tag.getBoolean("last_food_high_fat");}
        var fatigue=tag.getCompound("food_fatigue");for(String id:fatigue.getAllKeys())if(s.foodFatigue.size()<64&&id.length()<=256&&net.minecraft.resources.ResourceLocation.tryParse(id)!=null&&fatigue.contains(id,99)&&Double.isFinite(fatigue.getDouble(id)))s.rememberFatigue(id,Math.clamp(fatigue.getDouble(id),0,100));
        if(!s.lastFood.isEmpty())s.rememberFatigue(s.lastFood,s.get(BodyStat.FOOD_BOREDOM));
        var clocks=tag.getCompound("clocks");for(String name:clocks.getAllKeys())if(name.length()<=64&&s.clocks.size()<64&&clocks.contains(name,99)&&Double.isFinite(clocks.getDouble(name)))s.clock(name,clocks.getDouble(name));
        s.disease=tag.getCompound("disease_check").copy();
        s.ailments=AilmentState.load(tag.getCompound("ailments"));
        return s;
    }
    public CompoundTag save() {
        CompoundTag t=new CompoundTag();t.putInt("schema",4);t.put("ailments",ailments.save());
        for(Need n:Need.values()) t.putDouble(n.id,get(n));
        var b=new CompoundTag();for(var stat:BodyStat.values())if(isSet(stat))b.putDouble(stat.id,get(stat));t.put("body",b);
        if(!lastFood.isEmpty()){t.putString("last_food",lastFood);t.putInt("food_streak",foodStreak);t.putBoolean("last_food_high_fat",lastFoodHighFat);}
        var f=new CompoundTag();foodFatigue.forEach(f::putDouble);t.put("food_fatigue",f);
        var c=new CompoundTag();clocks.forEach(c::putDouble);t.put("clocks",c);t.put("disease_check",disease.copy());
        return t;
    }
    public static NeedsState of(Player p) { return load(p.getPersistentData().getCompound(KEY),p instanceof net.minecraft.server.level.ServerPlayer sp?NeedsRules.get(sp.server):null); }
    public void store(Player p) { p.getPersistentData().put(KEY,save()); }
    public void second(NeedsRules rules, boolean sprinting, boolean sleeping) {
        if(!rules.enabled)return;
        for(Need n:Need.values()) {
            if(n==Need.SLEEP&&sleeping) add(n,rules.restPerSecond);
            else add(n,-rules.rates[n.ordinal()]/60 * ((n==Need.WATER||n==Need.ENERGY)&&sprinting?rules.sprintMultiplier:1));
        }
    }
}
