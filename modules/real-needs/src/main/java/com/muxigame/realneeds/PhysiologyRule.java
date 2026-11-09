package com.muxigame.realneeds;

public enum PhysiologyRule {
    DAY_SECONDS("day_seconds",1200,1,86400), MUSCLE_DAY("muscle_day",1), PHYSIQUE_DAY("physique_day",1),
    BLOCK_MUSCLE("block_muscle",.1), NPC_MUSCLE("npc_muscle",.2), ACTIVITY_PHYSIQUE("activity_physique",.1),
    SANITY_HIT("sanity_hit",1), SANITY_DEATH("sanity_death",10), SANITY_SECONDS("sanity_seconds",60,1,86400),
    POISON_IMMUNITY("poison_immunity",2), IMMUNITY_SECONDS("immunity_seconds",420,1,86400),
    COMBAT_SECONDS("combat_seconds",15,1,600), POLLUTION_SECONDS("pollution_seconds",120,1,86400),
    COLD_SECONDS("cold_seconds",360,1,86400), DAMP_SECONDS("damp_seconds",480,1,86400), NORMAL_SECONDS("normal_seconds",2000,1,86400),
    TEMPERATURE_RATE("temperature_rate",.1,0,10), FIRE_CORRECTION("fire_correction",5), FIRE_WARM("fire_warm",5), FIRE_SECONDS("fire_seconds",3,1,60),
    ENTERTAINMENT_SECONDS("entertainment_seconds",300,1,86400), REST_ENTERTAINMENT_SECONDS("rest_entertainment_seconds",60,1,86400),
    BODY_HEAL_SECONDS("body_heal_seconds",60,1,86400), BODY_DAMAGE("body_damage",5),
    BOREDOM_GAIN("boredom_gain",12), FAT_BOREDOM_GAIN("fat_boredom_gain",30), BOREDOM_LIMIT("boredom_limit",80,1,100),
    BOREDOM_RECOVERY("boredom_recovery",1), VOMIT_FOOD("vomit_food",8,0,20), VOMIT_WATER("vomit_water",30),
    INTIMIDATION_BASE("intimidation_base",20), INTIMIDATION_MUSCLE("intimidation_muscle",.2,0,10),
    INTIMIDATION_PHYSIQUE("intimidation_physique",.1,0,10), INTIMIDATION_ARMOR("intimidation_armor",.8,0,10), INTIMIDATION_WEAPON("intimidation_weapon",1.5,0,10),
    SANITY_GAIN("sanity_gain",1), IMMUNITY_GAIN("immunity_gain",1), BODY_HEAL_GAIN("body_heal_gain",1),
    BED_MULTIPLIER("bed_multiplier",2,0,10), FULL_FOOD_MULTIPLIER("full_food_multiplier",2,0,10), FULL_FOOD_LIMIT("full_food_limit",20,1,20),
    ENTERTAINMENT_LOSS("entertainment_loss",1), REST_ENTERTAINMENT_GAIN("rest_entertainment_gain",1),
    KILL_ENTERTAINMENT("kill_entertainment",3), FOOD_ENTERTAINMENT("food_entertainment",2), DEATH_ENTERTAINMENT("death_entertainment",10),
    ALLY_TEMPERAMENT("ally_temperament",-3,-100,100), HOSTILE_TEMPERAMENT("hostile_temperament",.2,-100,100), NEUTRAL_TEMPERAMENT("neutral_temperament",-1,-100,100),
    SWITCH_BOREDOM_MULTIPLIER("switch_boredom_multiplier",.5,0,10), VOMIT_SECONDS("vomit_seconds",5,1,60),
    FIRE_START("fire_start",50), FIRE_STOP("fire_stop",55), FIRE_RADIUS("fire_radius",2,0,8), POLLUTION_RADIUS("pollution_radius",2,0,8), COMBAT_RADIUS("combat_radius",24,0,64),
    TEMPERATURE_BASE("temperature_base",50), COLD_BIOME_LIMIT("cold_biome_limit",.15,-2,3), HOT_BIOME_LIMIT("hot_biome_limit",1.5,-2,3),
    COLD_CORRECTION("cold_correction",-15,-100,100), HOT_CORRECTION("hot_correction",15,-100,100), WATER_CORRECTION("water_correction",-10,-100,100), DAMP_CORRECTION("damp_correction",-5,-100,100),
    EMPTY_FOOD_CORRECTION("empty_food_correction",-10,-100,100), LOW_FOOD_LIMIT("low_food_limit",6,0,20), LOW_FOOD_CORRECTION("low_food_correction",-5,-100,100),
    LOW_WATER_LIMIT("low_water_limit",20), LOW_WATER_CORRECTION("low_water_correction",5,-100,100),
    INTIMIDATION_MUSCLE_BASE("intimidation_muscle_base",50), INTIMIDATION_PHYSIQUE_BASE("intimidation_physique_base",50), INTIMIDATION_ATTACK_BASE("intimidation_attack_base",1,0,100),
    FOOD_ENERGY("food_energy",3), FOOD_PROTEIN("food_protein",18), FOOD_VITAMINS("food_vitamins",15), FOOD_WATER("food_water",20), BOTTLE_WATER("bottle_water",35),
    NIGHT_SLEEP("night_sleep",100), NEED_LOW("need_low",20), NEED_MEDIUM("need_medium",50), DISEASE_PERCENT("disease_percent",1);
    public final String id;public final double initial,min,max;
    PhysiologyRule(String id,double initial){this(id,initial,0,100);}
    PhysiologyRule(String id,double initial,double min,double max){this.id=id;this.initial=initial;this.min=min;this.max=max;}
    public double validate(double value){if(!Double.isFinite(value)||value<min||value>max)throw new IllegalArgumentException(id+"范围："+min+"–"+max);return value;}
    public static PhysiologyRule find(String id){for(var rule:values())if(rule.id.equals(id))return rule;return null;}
}
