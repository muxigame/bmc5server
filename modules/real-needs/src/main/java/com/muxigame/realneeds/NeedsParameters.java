package com.muxigame.realneeds;

import java.util.*;
import net.minecraft.nbt.CompoundTag;

/** One allowlist shared by persistence, network validation and the Chinese editor. */
public final class NeedsParameters {
    public record Entry(String key,String label,String group,String unit,double min,double max,double initial){
        public void validate(double v){if(!Double.isFinite(v)||v<min||v>max)throw new IllegalArgumentException(label+"范围："+format(min)+"～"+format(max));if((unit.equals("秒")||unit.equals("在线秒")||key.equals("vomit_food")||key.equals("full_food_limit")||key.equals("low_food_limit"))&&v!=Math.rint(v))throw new IllegalArgumentException(label+"请填写整数");}
    }
    public static String format(double v){return java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();}
    public static final List<Entry> ALL=create();
    public static Entry find(String key){return ALL.stream().filter(e->e.key.equals(key)).findFirst().orElseThrow(()->new IllegalArgumentException("未知参数："+key));}
    private static List<Entry> create(){
        var a=new ArrayList<Entry>();
        for(var n:Need.values())a.add(new Entry("initial."+n.id,n.label+"初始值","初始值","点",0,100,100));
        for(var s:BodyStat.values())a.add(new Entry(s==BodyStat.INTIMIDATION?"intimidation_base":"initial."+s.id,s.label+"初始值","初始值",s==BodyStat.INTIMIDATION?"基础值，装备另加":"点",0,100,s.initial));
        a.add(new Entry("enabled","系统启用","营养与睡眠","1开启 / 0关闭",0,1,1));
        for(var n:Need.values())a.add(new Entry(n.id,n.label+"消耗","营养与睡眠","点/分钟",0,100,n.rate));
        a.add(new Entry("sprint","疾跑消耗倍率","营养与睡眠","能量、水分",1,10,1.5));
        a.add(new Entry("rest","卧床睡眠恢复","营养与睡眠","点/秒",0,100,1));
        a.add(new Entry("food","食物营养总倍率","营养与睡眠","倍",0,100,1));
        String data="""
day_seconds|身材与肌肉|一天时长|在线秒
muscle_day|身材与肌肉|肌肉每日减少|点/天
physique_day|身材与肌肉|身材每日减少|点/天
block_muscle|身材与肌肉|破坏方块增加肌肉|点/次
npc_muscle|身材与肌肉|击杀NPC增加肌肉|点/次
activity_physique|身材与肌肉|挖掘或击杀NPC消耗身材|点/次
sanity_hit|理智与免疫|每次扣血减少理智|点/次
sanity_death|理智与免疫|死亡额外减少理智|点/次
sanity_seconds|理智与免疫|非战斗理智恢复间隔|秒
sanity_gain|理智与免疫|每次理智恢复量|点
poison_immunity|理智与免疫|中毒扣血减少免疫|点/次
immunity_seconds|理智与免疫|免疫基础恢复间隔|秒
immunity_gain|理智与免疫|每次免疫恢复量|点
bed_multiplier|理智与免疫|卧床恢复倍率|免疫、部位健康
full_food_multiplier|理智与免疫|高饱食恢复倍率|可与卧床叠加
full_food_limit|理智与免疫|高饱食恢复触发值|原版饱食度≥
combat_seconds|理智与免疫|受击/攻击后战斗持续|秒
combat_radius|理智与免疫|索敌战斗检测半径|格
pollution_seconds|疾病判定|污染环境判定间隔|秒
cold_seconds|疾病判定|寒冷环境判定间隔|秒
damp_seconds|疾病判定|潮湿环境判定间隔|秒
normal_seconds|疾病判定|正常环境判定间隔|秒
pollution_radius|疾病判定|污染源检测半径|格
disease_percent|疾病判定|每点缺失免疫的生病概率|百分比/点，最终≤100%
temperature_rate|体温|环境温度趋近速度|点/秒
temperature_base|体温|常温环境目标|点
cold_biome_limit|体温|寒冷生物群系温度阈值|群系温度＜
hot_biome_limit|体温|炎热生物群系温度阈值|群系温度≥
cold_correction|体温|寒冷环境修正|点
hot_correction|体温|炎热环境修正|点
water_correction|体温|泡水修正|点
damp_correction|体温|潮湿修正|点
empty_food_correction|体温|空饱食度修正|点
low_food_limit|体温|低饱食度触发值|原版饱食度≤
low_food_correction|体温|低饱食度修正|点
low_water_limit|体温|缺水触发值|水分≤
low_water_correction|体温|缺水体温修正|点
fire_correction|体温|近火临时修正|点
fire_warm|体温|每次烤火升温|点
fire_seconds|体温|烤火升温间隔|秒
fire_start|体温|再次加热触发值|体温＜
fire_stop|体温|烤火停止温度|体温≥
fire_radius|体温|热源检测半径|格
entertainment_seconds|娱乐与气质|娱乐自然下降间隔|秒
entertainment_loss|娱乐与气质|每次娱乐自然下降|点
rest_entertainment_seconds|娱乐与气质|卧床娱乐恢复间隔|秒
rest_entertainment_gain|娱乐与气质|每次卧床娱乐恢复|点
kill_entertainment|娱乐与气质|击杀敌人增加娱乐|点
food_entertainment|娱乐与气质|换食物增加娱乐|点
death_entertainment|娱乐与气质|死亡减少娱乐|点
ally_temperament|娱乐与气质|击杀盟友气质变化|有符号增量
hostile_temperament|娱乐与气质|击杀敌人气质变化|有符号增量
neutral_temperament|娱乐与气质|击杀中立气质变化|有符号增量
body_heal_seconds|部位健康|部位基础恢复间隔|秒
body_heal_gain|部位健康|每次各部位恢复量|点
body_damage|部位健康|每点生命伤害扣除健康|倍率
boredom_gain|食物厌倦与呕吐|同食物厌倦增长|点/次
fat_boredom_gain|食物厌倦与呕吐|高油脂厌倦增长|点/次
switch_boredom_multiplier|食物厌倦与呕吐|换回旧食物增长倍率|首次尝新不增加
boredom_limit|食物厌倦与呕吐|继续进食呕吐阈值|进食前厌倦≥
boredom_recovery|食物厌倦与呕吐|厌倦自然恢复|点/分钟
vomit_food|食物厌倦与呕吐|呕吐扣除饱食度|原版0～20
vomit_water|食物厌倦与呕吐|呕吐扣除水分|点
vomit_seconds|食物厌倦与呕吐|反胃持续时间|秒
intimidation_muscle|威慑计算|肌肉影响权重|每点倍率
intimidation_physique|威慑计算|身材影响权重|每点倍率
intimidation_armor|威慑计算|护甲影响权重|每点倍率
intimidation_weapon|威慑计算|武器影响权重|每点倍率
intimidation_muscle_base|威慑计算|肌肉计算基准|点
intimidation_physique_base|威慑计算|身材计算基准|点
intimidation_attack_base|威慑计算|武器伤害计算基准|攻击伤害
food_energy|营养与睡眠|食物每点饱食补充能量|点
food_protein|营养与睡眠|蛋白食物补充量|点
food_vitamins|营养与睡眠|维生素食物补充量|点
food_water|营养与睡眠|含水食物补水量|点
bottle_water|营养与睡眠|水瓶补水量|点
night_sleep|营养与睡眠|睡过整夜后的睡眠值|目标值
need_low|显示阈值|营养不足提示阈值|数值≤，仅提示
need_medium|显示阈值|营养偏低提示阈值|数值＜，仅提示
""";
        var labels=new HashMap<String,String[]>();for(String line:data.strip().split("\\R")){var parts=line.split("\\|",-1);labels.put(parts[0],parts);}
        for(var r:PhysiologyRule.values())if(r!=PhysiologyRule.INTIMIDATION_BASE){var p=labels.get(r.id);if(p==null)throw new IllegalStateException("缺少参数标签："+r.id);a.add(new Entry(r.id,p[2],p[1],p[3],r.min,r.max,r.initial));}
        a.addAll(HealthParameters.ALL);return List.copyOf(a);
    }
    public static double get(CompoundTag settings,Entry e){
        if(e.key.startsWith("health.")){var t=settings.getCompound("health");return t.contains(e.key,99)?t.getDouble(e.key):e.initial;}
        if(e.key.startsWith("initial.")){var t=settings.getCompound("initial");return t.contains(e.key.substring(8),99)?t.getDouble(e.key.substring(8)):e.initial;}
        if(PhysiologyRule.find(e.key)!=null){var t=settings.getCompound("physiology");return t.contains(e.key,99)?t.getDouble(e.key):e.initial;}
        if(e.key.equals("enabled"))return !settings.contains("enabled")||settings.getBoolean("enabled")?1:0;
        return settings.contains(e.key,99)?settings.getDouble(e.key):e.initial;
    }
}
