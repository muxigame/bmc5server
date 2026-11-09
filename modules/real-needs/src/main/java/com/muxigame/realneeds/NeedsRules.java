package com.muxigame.realneeds;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World-specific settings, with a persisted previous version for in-game undo. */
public final class NeedsRules extends SavedData {
    public boolean enabled=true;
    public final double[] rates=new double[5];
    public double sprintMultiplier=1.5, restPerSecond=1, foodMultiplier=1;
    private CompoundTag previous;
    private long revision;
    private CompoundTag initialValues=new CompoundTag();
    private CompoundTag healthValues=new CompoundTag();
    public double health(String key){var e=HealthParameters.BY_KEY.get(key);if(e==null)throw new IllegalArgumentException("未知伤病参数："+key);return healthValues.contains(key,99)?healthValues.getDouble(key):e.initial();}
    public long revision(){return revision;}
    public boolean canUndo(){return previous!=null;}
    public double initial(Need n){return initialValues.contains(n.id,99)?initialValues.getDouble(n.id):100;}
    public double initial(BodyStat s){return s==BodyStat.INTIMIDATION?value(PhysiologyRule.INTIMIDATION_BASE):initialValues.contains(s.id,99)?initialValues.getDouble(s.id):s.initial;}
    private final java.util.EnumMap<PhysiologyRule,Double> physiology=new java.util.EnumMap<>(PhysiologyRule.class);
    public NeedsRules(){for(Need n:Need.values())rates[n.ordinal()]=n.rate;for(var rule:PhysiologyRule.values())physiology.put(rule,rule.initial);}
    public double value(PhysiologyRule rule){return physiology.get(rule);}
    public int seconds(PhysiologyRule rule){return Math.max(1,(int)Math.round(value(rule)));}
    public static NeedsRules get(MinecraftServer s){return s.overworld().getDataStorage().computeIfAbsent(new Factory<>(NeedsRules::new,NeedsRules::load),"real_needs_rules");}
    public CompoundTag settings(){CompoundTag t=new CompoundTag();t.putBoolean("enabled",enabled);for(Need n:Need.values())t.putDouble(n.id,rates[n.ordinal()]);t.putDouble("sprint",sprintMultiplier);t.putDouble("rest",restPerSecond);t.putDouble("food",foodMultiplier);var p=new CompoundTag();for(var rule:PhysiologyRule.values())p.putDouble(rule.id,value(rule));t.put("physiology",p);t.put("initial",initialValues.copy());var health=new CompoundTag();for(var e:HealthParameters.ALL)health.putDouble(e.key(),health(e.key()));t.put("health",health);return t;}
    private void read(CompoundTag t){healthValues=new CompoundTag();for(var e:HealthParameters.ALL)healthValues.putDouble(e.key(),valid(t.getCompound("health"),e.key(),e.initial(),e.min(),e.max()));initialValues=new CompoundTag();for(var e:NeedsParameters.ALL)if(e.key().startsWith("initial."))initialValues.putDouble(e.key().substring(8),valid(t.getCompound("initial"),e.key().substring(8),e.initial(),e.min(),e.max()));enabled=!t.contains("enabled")||t.getBoolean("enabled");for(Need n:Need.values())rates[n.ordinal()]=valid(t,n.id,n.rate,0,100);sprintMultiplier=valid(t,"sprint",1.5,1,10);restPerSecond=valid(t,"rest",1,0,100);foodMultiplier=valid(t,"food",1,0,100);var p=t.getCompound("physiology");for(var rule:PhysiologyRule.values())physiology.put(rule,valid(p,rule.id,rule.initial,rule.min,rule.max));}
    private static double valid(CompoundTag t,String k,double fallback,double min,double max){double v=t.getDouble(k);return t.contains(k,99)&&Double.isFinite(v)&&v>=min&&v<=max?v:fallback;}
    private static void put(CompoundTag t,String key,double value){
        if(key.startsWith("health."))t.getCompound("health").putDouble(key,value);
        else if(key.startsWith("initial."))t.getCompound("initial").putDouble(key.substring(8),value);
        else if(PhysiologyRule.find(key)!=null)t.getCompound("physiology").putDouble(key,value);
        else if(key.equals("enabled"))t.putBoolean(key,value==1);
        else t.putDouble(key,value);
    }
    /** Validate all edits before mutating anything; reject stale editors. */
    public void edit(long expected,CompoundTag changes){
        if(expected!=revision)throw new IllegalArgumentException("设置已被其他操作修改，请先重读服务器设置");
        if(changes==null||changes.isEmpty()||changes.size()>NeedsParameters.ALL.size())throw new IllegalArgumentException("没有修改或参数过多");
        CompoundTag next=settings();
        for(String key:changes.getAllKeys()){
            var entry=NeedsParameters.find(key);
            if(!changes.contains(key,99))throw new IllegalArgumentException(entry.label()+"必须是数字");
            double value=changes.getDouble(key);entry.validate(value);
            if((key.equals("enabled")||key.equals("health.enabled"))&&value!=0&&value!=1)throw new IllegalArgumentException("系统启用只能填0或1");
            put(next,key,value);
        }
        var p=next.getCompound("physiology");
        if(p.getDouble("fire_start")>p.getDouble("fire_stop"))throw new IllegalArgumentException("烤火停止温度不能低于再次加热触发值");
        if(p.getDouble("cold_biome_limit")>p.getDouble("hot_biome_limit"))throw new IllegalArgumentException("炎热群系阈值不能低于寒冷群系阈值");
        if(p.getDouble("need_low")>p.getDouble("need_medium"))throw new IllegalArgumentException("偏低提示阈值不能低于不足提示阈值");
        var health=next.getCompound("health");
        if(health.getDouble("health.moderate")>=health.getDouble("health.severe"))throw new IllegalArgumentException("伤病重度阈值必须高于中度阈值");
        if(!(health.getDouble("health.cold_limit")<health.getDouble("health.cold_safe")&&health.getDouble("health.cold_safe")<health.getDouble("health.heat_safe")&&health.getDouble("health.heat_safe")<health.getDouble("health.heat_limit")))throw new IllegalArgumentException("体温阈值须满足：失温触发＜失温恢复＜中暑恢复＜中暑触发");
        previous=settings();read(next);revision++;setDirty();
    }
    public void change(String key,double value){var t=new CompoundTag();t.putDouble(key,value);edit(revision,t);}
    public void reset(){previous=settings();read(new CompoundTag());revision++;setDirty();}
    public void undo(){if(previous==null)throw new IllegalArgumentException("没有可恢复的设置");CompoundTag now=settings();read(previous);previous=now;revision++;setDirty();}
    public static NeedsRules load(CompoundTag t,HolderLookup.Provider p){NeedsRules r=new NeedsRules();r.read(t.getCompound("settings"));r.revision=Math.max(0,t.getLong("revision"));if(t.contains("previous",10))r.previous=t.getCompound("previous").copy();return r;}
    @Override public CompoundTag save(CompoundTag t,HolderLookup.Provider p){t.putLong("revision",revision);t.put("settings",settings());if(previous!=null)t.put("previous",previous);return t;}
}
