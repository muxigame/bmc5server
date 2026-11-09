package com.muxigame.realneeds;

/** Fictional gameplay conditions; severity is 0..100, zero means absent. */
public enum AilmentType {
    BACTERIAL("bacterial","细菌感染",false,1,2,"抗菌药剂","antibacterial_dose","免疫下降、发热、额外失水；重度可持续损伤",0xffd1c563),
    PARASITE("parasite","寄生虫感染",false,.15,1,"驱虫药剂","antiparasitic_dose","窃取能量和蛋白质，降低进食营养收益",0xffa0be67),
    VIRAL("viral","病毒感染",false,.8,1,"抗病毒药剂","antiviral_dose","发热、嗜睡、精神消耗，降低攻击能力",0xffb292cf),
    HEATSTROKE("heatstroke","中暑",false,6,0,"降温包","cooling_pack","大量失水、乏力、眩晕；重度持续损伤",0xffe49a58),
    HYPOTHERMIA("hypothermia","失温症",false,6,0,"保温包","warming_pack","能量消耗、动作迟缓、挖掘变慢；重度持续损伤",0xff85bfdc),
    BURN("burn","烧伤",true,.8,0,"烧伤敷料","burn_dressing","部位持续疼痛、失水；开放创面可能感染",0xffdd785b),
    FRACTURE("fracture","骨折",true,.2,0,"夹板","splint","腿伤影响移动和跳跃，臂伤影响攻击与挖掘",0xffded5bf),
    SCRATCH("scratch","刮伤",true,4,0,"绷带","bandage","轻微渗血失水，形成可感染的开放创面",0xffd7a5a0),
    LACERATION("laceration","裂伤",true,1,0,"绷带","bandage","持续出血扣血、失水，并损伤受伤部位",0xffdd6776),
    BRUISE("bruise","淤伤",true,2,0,"舒缓药膏","soothing_paste","部位疼痛，小幅影响移动或操作效率",0xffb28dbc),
    DISABILITY("disability","残废",true,0,0,"康复护理包","rehabilitation_kit","严重部位功能损失；不会自然消失，需康复护理",0xff9da2b4);
    public final String id,label,careLabel,careItem,description;public final boolean local;public final double recovery,worsening;public final int color;
    AilmentType(String id,String label,boolean local,double recovery,double worsening,String careLabel,String careItem,String description,int color){this.id=id;this.label=label;this.local=local;this.recovery=recovery;this.worsening=worsening;this.careLabel=careLabel;this.careItem=careItem;this.description=description;this.color=color;}
    public String key(String option){return "health."+id+"."+option;}
    public double rule(NeedsRules r,String option){return r.health(key(option));}
    public static AilmentType byId(String id){for(var t:values())if(t.id.equals(id))return t;throw new IllegalArgumentException("未知伤病");}
}
