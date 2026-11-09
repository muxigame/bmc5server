package com.muxigame.realneeds;

import java.util.*;
import static com.muxigame.realneeds.BodyStat.*;
import static com.muxigame.realneeds.PhysiologyRule.*;
import com.muxigame.realneeds.DiseaseCheckEvent.Exposure;

/** Deterministic server mechanics. Time is active online game seconds, never wall-clock time. */
public final class NeedsPhysiology {
    public static final List<BodyStat> PARTS=List.of(LEFT_ARM,RIGHT_ARM,LEFT_LEG,RIGHT_LEG,HEAD,TORSO);
    public record Conditions(boolean combat,boolean sleeping,int food,boolean sick,boolean polluted,boolean cold,boolean damp,boolean fire,double temperatureTarget){}
    public enum Injury {TORSO,HEAD,LEFT_ARM,RIGHT_ARM,LEFT_LEG,RIGHT_LEG,FALL,AREA}
    public static double diseaseChance(NeedsState s){return 100-s.get(IMMUNITY);}
    public static double diseaseChance(NeedsState s,NeedsRules r){return Math.clamp((100-s.get(IMMUNITY))*r.value(DISEASE_PERCENT),0,100);}
    public static void combat(NeedsState s,NeedsRules r){s.clock("combat",r.seconds(COMBAT_SECONDS));}
    public static void block(NeedsState s,NeedsRules r){s.add(MUSCLE,r.value(BLOCK_MUSCLE));s.add(PHYSIQUE,-r.value(ACTIVITY_PHYSIQUE));}
    public static void kill(NeedsState s,NeedsRules r,boolean npc,boolean hostile,boolean allied){
        combat(s,r);if(npc){s.add(MUSCLE,r.value(NPC_MUSCLE));s.add(PHYSIQUE,-r.value(ACTIVITY_PHYSIQUE));}
        s.add(TEMPERAMENT,allied?r.value(ALLY_TEMPERAMENT):hostile?r.value(HOSTILE_TEMPERAMENT):r.value(NEUTRAL_TEMPERAMENT));s.add(ENTERTAINMENT,hostile?r.value(KILL_ENTERTAINMENT):0);
    }
    public static void damage(NeedsState s,NeedsRules r,float amount,Injury injury,boolean poison,boolean inCombat){
        if(amount<=0)return;s.add(SANITY,-r.value(SANITY_HIT));if(poison)s.add(IMMUNITY,-r.value(POISON_IMMUNITY));if(inCombat)combat(s,r);
        double loss=amount*r.value(BODY_DAMAGE);
        switch(injury){
            case FALL->{s.add(LEFT_LEG,-loss/2);s.add(RIGHT_LEG,-loss/2);}
            case AREA->{for(var part:PARTS)s.add(part,-loss/PARTS.size());}
            default->s.add(BodyStat.valueOf(injury.name()),-loss);
        }
    }
    public static void death(NeedsState s,NeedsRules r){s.add(SANITY,-r.value(SANITY_DEATH));s.add(ENTERTAINMENT,-r.value(DEATH_ENTERTAINMENT));s.clock("combat",0);s.clock("sanity_rest",0);}
    public static double intimidation(NeedsState s,NeedsRules r,double armor,double attack){
        return Math.clamp(r.value(INTIMIDATION_BASE)+(s.get(MUSCLE)-r.value(INTIMIDATION_MUSCLE_BASE))*r.value(INTIMIDATION_MUSCLE)+(s.get(PHYSIQUE)-r.value(INTIMIDATION_PHYSIQUE_BASE))*r.value(INTIMIDATION_PHYSIQUE)+Math.max(0,armor)*r.value(INTIMIDATION_ARMOR)+Math.max(0,attack-r.value(INTIMIDATION_ATTACK_BASE))*r.value(INTIMIDATION_WEAPON),0,100);
    }
    /** Caller applies audiovisual vomiting and vanilla hunger loss after food completion. */
    public static boolean food(NeedsState s,NeedsRules r,String food,boolean highFat){
        double before=s.lastFood().isEmpty()?s.get(FOOD_BOREDOM):s.fatigue(food);boolean same=food.equals(s.lastFood()),known=s.fatigue(food)>0;
        boolean vomit=before>=r.value(BOREDOM_LIMIT);
        double gain=same?(highFat?r.value(FAT_BOREDOM_GAIN):r.value(BOREDOM_GAIN)):known?(highFat?r.value(FAT_BOREDOM_GAIN):r.value(BOREDOM_GAIN))*r.value(SWITCH_BOREDOM_MULTIPLIER):0;
        s.recordFood(food,highFat);s.set(FOOD_BOREDOM,Math.min(100,before+gain));if(!same)s.add(ENTERTAINMENT,r.value(FOOD_ENTERTAINMENT));
        if(vomit){s.add(Need.WATER,-r.value(VOMIT_WATER));s.clock("vomit",r.seconds(VOMIT_SECONDS));}
        return vomit;
    }
    public static List<Exposure> second(NeedsState s,NeedsRules r,Conditions c){
        List<Exposure> checks=new ArrayList<>();if(!r.enabled)return checks;
        boolean fighting=c.combat||s.clock("combat")>0;s.clock("combat",Math.max(0,s.clock("combat")-1));s.clock("vomit",Math.max(0,s.clock("vomit")-1));
        int days=s.advance("day",1,r.seconds(DAY_SECONDS));if(days>0){s.add(MUSCLE,-days*r.value(MUSCLE_DAY));s.add(PHYSIQUE,-days*r.value(PHYSIQUE_DAY));}
        if(fighting){s.clock("sanity_rest",0);s.clock("immune_rest",0);s.clock("body_rest",0);}
        else{
            s.add(SANITY,s.advance("sanity_rest",1,r.seconds(SANITY_SECONDS))*r.value(SANITY_GAIN));
            if(c.food>0&&!c.sick){
                double multiplier=(c.sleeping?r.value(BED_MULTIPLIER):1)*(c.food>=r.value(FULL_FOOD_LIMIT)?r.value(FULL_FOOD_MULTIPLIER):1);
                s.add(IMMUNITY,s.advance("immune_rest",multiplier,r.seconds(IMMUNITY_SECONDS))*r.value(IMMUNITY_GAIN));
                int healed=s.advance("body_rest",multiplier,r.seconds(BODY_HEAL_SECONDS));if(healed>0)for(var part:PARTS)if(NeedsIllness.canHeal(s,r,part))s.add(part,healed*r.value(BODY_HEAL_GAIN));
            }
        }
        s.add(ENTERTAINMENT,-s.advance("entertainment_loss",1,r.seconds(ENTERTAINMENT_SECONDS))*r.value(ENTERTAINMENT_LOSS));
        if(c.sleeping&&!fighting)s.add(ENTERTAINMENT,s.advance("rest_entertainment",1,r.seconds(REST_ENTERTAINMENT_SECONDS))*r.value(REST_ENTERTAINMENT_GAIN));else s.clock("rest_entertainment",0);
        s.recoverFatigue(r.value(BOREDOM_RECOVERY)/60);
        temperature(s,r,c.fire,c.temperatureTarget);
        exposure(s,checks,"pollution",c.polluted,r.seconds(POLLUTION_SECONDS),Exposure.POLLUTION);
        exposure(s,checks,"cold",c.cold,r.seconds(COLD_SECONDS),Exposure.COLD);
        exposure(s,checks,"damp",c.damp,r.seconds(DAMP_SECONDS),Exposure.DAMP);
        exposure(s,checks,"normal",!c.polluted&&!c.cold&&!c.damp,r.seconds(NORMAL_SECONDS),Exposure.NORMAL);
        return checks;
    }
    private static void exposure(NeedsState s,List<Exposure> checks,String name,boolean present,int interval,Exposure reason){if(present){if(s.advance("exposure_"+name,1,interval)>0)checks.add(reason);}else s.clock("exposure_"+name,0);}
    static void temperature(NeedsState s,NeedsRules r,boolean fire,double target){
        double previous=s.clock("fire_bonus"),bonus=fire?r.value(FIRE_CORRECTION):0;
        boolean entered=fire&&s.clock("near_fire")==0;
        double core=Math.clamp(s.get(TEMPERATURE)-previous,0,100);
        if(!entered){double step=r.value(TEMPERATURE_RATE);core+=Math.clamp(target-core,-step,step);}
        if(fire){
            if(entered||core+bonus<r.value(FIRE_START))s.clock("warming",1);
            if(core+bonus>=r.value(FIRE_STOP))s.clock("warming",0);
            if(s.clock("warming")>0&&s.advance("fire_time",1,r.seconds(FIRE_SECONDS))>0){core=Math.min(r.value(FIRE_STOP)-bonus,core+r.value(FIRE_WARM));if(core+bonus>=r.value(FIRE_STOP))s.clock("warming",0);}
        }else{s.clock("fire_time",0);s.clock("warming",0);}
        s.clock("near_fire",fire?1:0);s.clock("fire_bonus",bonus);s.set(TEMPERATURE,Math.clamp(core+bonus,0,100));
    }
}
