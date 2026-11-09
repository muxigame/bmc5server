package com.muxigame.realneeds;

import java.util.*;
import net.minecraft.nbt.CompoundTag;

/** Bounded per-condition/per-site storage. Timers pause offline and survive death/reload. */
public final class AilmentState {
    public enum Site {
        WHOLE("全身",null),LEFT_ARM("左臂",BodyStat.LEFT_ARM),RIGHT_ARM("右臂",BodyStat.RIGHT_ARM),LEFT_LEG("左腿",BodyStat.LEFT_LEG),RIGHT_LEG("右腿",BodyStat.RIGHT_LEG),HEAD("头部",BodyStat.HEAD),TORSO("躯干",BodyStat.TORSO);
        public final String label;public final BodyStat stat;
        Site(String label,BodyStat stat){this.label=label;this.stat=stat;}
        public String id(){return name().toLowerCase(Locale.ROOT);}
        public boolean leg(){return this==LEFT_LEG||this==RIGHT_LEG;}
        public boolean arm(){return this==LEFT_ARM||this==RIGHT_ARM;}
        public static Site part(BodyStat part){for(var s:values())if(s.stat==part)return s;throw new IllegalArgumentException("不是健康部位");}
        public static Site byId(String id){for(var s:values())if(s.id().equals(id))return s;throw new IllegalArgumentException("未知部位");}
    }
    public static final class Case {
        public final AilmentType type;public final Site site;
        double severity,age,care,cooldown,pulse,progress;
        Case(AilmentType type,Site site,double severity){this.type=type;this.site=site;this.severity=severity;}
        public double severity(){return severity;}
        public double care(){return care;}
        public double cooldown(){return cooldown;}
        public double age(){return age;}
        public String key(){return AilmentState.key(type,site);}
        public int grade(NeedsRules r){return severity>=r.health("health.severe")?3:severity>=r.health("health.moderate")?2:1;}
        public String gradeLabel(NeedsRules r){return switch(grade(r)){case 3->"重度";case 2->"中度";default->"轻度";};}
    }
    private final Map<String,Case> cases=new LinkedHashMap<>();
    public static String key(AilmentType type,Site site){return type.id+"__"+site.id();}
    public static boolean valid(AilmentType type,Site site){return type.local?site!=Site.WHOLE:site==Site.WHOLE;}
    public List<Case> cases(){return List.copyOf(cases.values());}
    public Case get(AilmentType type,Site site){return cases.get(key(type,site));}
    public double severity(AilmentType type,Site site){var c=get(type,site);return c==null?0:c.severity;}
    public double maximum(AilmentType type){return cases.values().stream().filter(c->c.type==type).mapToDouble(c->c.severity).max().orElse(0);}
    public void set(AilmentType type,Site site,double value){
        if(!valid(type,site)||!Double.isFinite(value)||value<0||value>100)throw new IllegalArgumentException("伤病程度须为0～100，且部位匹配");
        String key=key(type,site);if(value==0){cases.remove(key);return;}var c=cases.get(key);if(c==null)cases.put(key,new Case(type,site,value));else c.severity=value;
    }
    public void add(AilmentType type,Site site,double amount){set(type,site,Math.clamp(severity(type,site)+amount,0,100));}
    public void clear(){cases.clear();}
    public boolean openWound(){return cases.values().stream().anyMatch(c->c.type==AilmentType.BURN||c.type==AilmentType.SCRATCH||c.type==AilmentType.LACERATION);}
    public CompoundTag save(){var tag=new CompoundTag();for(var c:cases.values()){var t=new CompoundTag();t.putDouble("severity",c.severity);t.putDouble("age",c.age);t.putDouble("care",c.care);t.putDouble("cooldown",c.cooldown);t.putDouble("pulse",c.pulse);t.putDouble("progress",c.progress);tag.put(c.key(),t);}return tag;}
    private static double number(CompoundTag t,String key,double max){double n=t.getDouble(key);return t.contains(key,99)&&Double.isFinite(n)?Math.clamp(n,0,max):0;}
    public static AilmentState load(CompoundTag t){var state=new AilmentState();for(var type:AilmentType.values())for(var site:Site.values())if(valid(type,site)){
        var n=t.getCompound(key(type,site));double severity=number(n,"severity",100);if(severity==0)continue;
        state.set(type,site,severity);var c=state.get(type,site);c.age=number(n,"age",1e9);c.care=number(n,"care",86400);c.cooldown=number(n,"cooldown",86400);c.pulse=number(n,"pulse",86400);c.progress=number(n,"progress",86400);
    }return state;}
}
