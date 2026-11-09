package com.muxigame.realneeds;

/** All values are abstract 0–100 game statistics, not medical measurements. */
public enum BodyStat {
    PHYSIQUE("physique","身材",50),
    MUSCLE("muscle","肌肉量",50),
    FOOD_BOREDOM("food_boredom","食物厌倦度",0),
    SANITY("sanity","理智",100),
    IMMUNITY("immunity","免疫",100),
    ENTERTAINMENT("entertainment","娱乐",100),
    TEMPERATURE("temperature","体温",50),
    INTIMIDATION("intimidation","威慑",20),
    LEFT_ARM("left_arm","左臂健康",100),
    RIGHT_ARM("right_arm","右臂健康",100),
    LEFT_LEG("left_leg","左腿健康",100),
    RIGHT_LEG("right_leg","右腿健康",100),
    HEAD("head","头部健康",100),
    TORSO("torso","躯干健康",100),
    TEMPERAMENT("temperament","气质",50);

    public final String id,label;
    public final double debugMax=100,initial;
    BodyStat(String id,String label,double initial){this.id=id;this.label=label;this.initial=initial;}
    public static BodyStat byId(String id){
        if(id.equals("weight"))return PHYSIQUE;
        for(var s:values())if(s.id.equals(id))return s;
        throw new IllegalArgumentException("未知角色数值："+id);
    }
}
