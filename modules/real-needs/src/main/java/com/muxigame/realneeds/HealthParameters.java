package com.muxigame.realneeds;

import java.util.*;

public final class HealthParameters {
    public static final List<NeedsParameters.Entry> ALL=create();
    public static final Map<String,NeedsParameters.Entry> BY_KEY;
    static {var map=new LinkedHashMap<String,NeedsParameters.Entry>();for(var e:ALL)map.put(e.key(),e);BY_KEY=Map.copyOf(map);}
    private static void add(List<NeedsParameters.Entry> a,String key,String label,String group,String unit,double initial,double min,double max){a.add(new NeedsParameters.Entry(key,label,group,unit,min,max,initial));}
    private static List<NeedsParameters.Entry> create(){
        var a=new ArrayList<NeedsParameters.Entry>();String group="伤病通用";
        add(a,"health.enabled","伤病系统启用",group,"1开启 / 0关闭",1,0,1);
        add(a,"health.moderate","中度起始程度",group,"程度≥",30,1,99);add(a,"health.severe","重度起始程度",group,"程度≥",70,2,100);
        add(a,"health.heat_limit","中暑触发体温",group,"体温≥",70,0,100);add(a,"health.cold_limit","失温触发体温",group,"体温≤",30,0,100);
        add(a,"health.heat_safe","中暑恢复体温上限",group,"体温≤",60,0,100);add(a,"health.cold_safe","失温恢复体温下限",group,"体温≥",40,0,100);
        add(a,"health.exposure_seconds","异常体温持续判定间隔",group,"秒",30,1,3600);
        add(a,"health.fracture_hit","骨折触发伤害",group,"实际生命伤害≥",6,0,100);add(a,"health.fall_fracture_hit","摔伤骨折触发伤害",group,"实际生命伤害≥",4,0,100);
        add(a,"health.laceration_hit","刃/弹射物裂伤触发伤害",group,"实际生命伤害≥",2,0,100);
        add(a,"health.disability_health","残废触发部位健康",group,"新重伤后健康≤",10,0,100);
        add(a,"health.disability_severity","残废触发伤势程度",group,"新重伤程度≥",70,1,100);
        add(a,"health.immune_recover","感染自愈所需免疫",group,"免疫≥",40,0,100);
        add(a,"health.bed_multiplier","伤病卧床恢复倍率",group,"倍",2,0,10);add(a,"health.care_multiplier","护理期间恢复倍率",group,"倍",3,0,10);
        add(a,"health.care_seconds","护理持续时间",group,"秒",300,1,86400);add(a,"health.care_cooldown","同一病情护理间隔",group,"秒",30,1,3600);
        add(a,"health.care_bleed_multiplier","护理后出血/创面损伤倍率",group,"倍",.2,0,1);
        add(a,"health.damage_cap","伤病每秒总伤害上限",group,"生命值",2,0,20);add(a,"health.penalty_cap","身体功能减益上限",group,"比例，0.85保留15%",.85,0,.95);
        add(a,"health.cooling_delta","降温包体温降低",group,"点",10,0,100);add(a,"health.warming_delta","保温包体温增加",group,"点",10,0,100);
        add(a,"health.nausea_seconds","中暑眩晕持续时间",group,"秒",5,4,30);
        for(var t:AilmentType.values()){
            group="伤病·"+t.label;
            boolean thermal=t==AilmentType.HEATSTROKE||t==AilmentType.HYPOTHERMIA;
            boolean infection=t==AilmentType.BACTERIAL||t==AilmentType.PARASITE||t==AilmentType.VIRAL;
            add(a,t.key("initial"),t==AilmentType.DISABILITY?"新残废初始程度":"首次发病程度",group,"程度",t==AilmentType.DISABILITY?70:10,1,100);
            if(t!=AilmentType.DISABILITY)add(a,t.key("gain"),thermal?"持续异常体温恶化量":t.local?"每点创伤增加程度":"重复感染增加程度",group,"程度",thermal||t.local?6:10,0,100);
            add(a,t.key("recovery"),"基础自然恢复速度",group,"程度/分钟",t.recovery,0,100);
            if(infection)add(a,t.key("worsening"),"低免疫感染恶化速度",group,"程度/分钟",t.worsening,0,100);
            add(a,t.key("pulse_seconds"),"损伤结算间隔",group,"秒",15,1,3600);
            add(a,t.key("damage"),"每级每次生命损伤",group,t==AilmentType.LACERATION?"所有程度均出血":"仅重度结算",t==AilmentType.LACERATION?.25:t==AilmentType.HEATSTROKE||t==AilmentType.HYPOTHERMIA?.5:t==AilmentType.BACTERIAL?.25:0,0,20);
            add(a,t.key("care_reduction"),"每次护理降低程度",group,"程度",t==AilmentType.DISABILITY?3:t==AilmentType.FRACTURE?5:t==AilmentType.PARASITE?30:20,0,100);
        }
        extra(a,AilmentType.BACTERIAL,"water","每级额外水分消耗",2,100);extra(a,AilmentType.BACTERIAL,"immunity","每级免疫消耗",.5,100);extra(a,AilmentType.BACTERIAL,"fever","每级发热修正",2,20);
        extra(a,AilmentType.PARASITE,"energy","每级能量消耗",3,100);extra(a,AilmentType.PARASITE,"protein","每级蛋白消耗",1.5,100);extra(a,AilmentType.PARASITE,"absorption_loss","每级食物收益损失比例",.15,.3);
        extra(a,AilmentType.VIRAL,"sleep","每级睡眠消耗",2,100);extra(a,AilmentType.VIRAL,"sanity","每级理智消耗",.2,100);extra(a,AilmentType.VIRAL,"fever","每级发热修正",1,20);extra(a,AilmentType.VIRAL,"attack","每级攻击伤害减幅",.05,.5);
        extra(a,AilmentType.HEATSTROKE,"water","每级水分消耗",4,100);extra(a,AilmentType.HEATSTROKE,"sleep","每级睡眠消耗",2,100);
        extra(a,AilmentType.HYPOTHERMIA,"energy","每级能量消耗",4,100);extra(a,AilmentType.HYPOTHERMIA,"movement","每级移动减幅",.06,.5);extra(a,AilmentType.HYPOTHERMIA,"mining","每级挖掘减幅",.1,.5);
        extra(a,AilmentType.BURN,"water","每级水分消耗",1,100);extra(a,AilmentType.BURN,"body_loss","每级每次部位损伤",.25,100);
        extra(a,AilmentType.SCRATCH,"water","每级渗血水分消耗",.2,100);
        extra(a,AilmentType.LACERATION,"water","每级失血水分消耗",1,100);extra(a,AilmentType.LACERATION,"body_loss","每级每次部位损伤",.25,100);
        for(var t:List.of(AilmentType.FRACTURE,AilmentType.BRUISE,AilmentType.DISABILITY)){
            extra(a,t,"movement","腿部每级移动减幅",t==AilmentType.BRUISE?.025:t==AilmentType.FRACTURE?.1:.18,.5);
            extra(a,t,"attack_speed","臂部每级攻击速度减幅",t==AilmentType.BRUISE?.025:t==AilmentType.FRACTURE?.08:.15,.5);
            extra(a,t,"mining","臂/头部每级挖掘减幅",t==AilmentType.BRUISE?.025:t==AilmentType.FRACTURE?.12:.18,.5);
            extra(a,t,"energy","躯干每级能量消耗",t==AilmentType.BRUISE?.3:1,100);
        }
        return List.copyOf(a);
    }
    private static void extra(List<NeedsParameters.Entry> a,AilmentType t,String id,String label,double initial,double max){add(a,t.key(id),label,"伤病·"+t.label,id.equals("fever")?"体温目标增量":max<=.5?"比例":id.equals("body_loss")?"点/次":"点/分钟",initial,0,max);}
}
