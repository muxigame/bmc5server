package com.muxigame.realneeds;

import java.util.*;
import static com.muxigame.realneeds.AilmentType.*;
import static com.muxigame.realneeds.AilmentState.Site.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.effect.*;

/** Deterministic progression and a small server effect adapter. Never replaces combat logic. */
public final class NeedsIllness {
    public record Rest(boolean combat,boolean sleeping,int food){}
    public record Penalties(double movement,double attack,double attackSpeed,double mining,double jump){}
    private static final Map<UUID,NeedsState> INTERNAL=new HashMap<>();
    public static final ResourceKey<DamageType> DAMAGE=ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.fromNamespaceAndPath(RealNeeds.ID,"ailment"));
    public static boolean enabled(NeedsRules r){return r.enabled&&r.health("health.enabled")==1;}
    public static NeedsState pending(ServerPlayer p){return INTERNAL.get(p.getUUID());}
    public static void stop(){INTERNAL.clear();}
    public static int grade(double severity,NeedsRules r){return severity<=0?0:severity>=r.health("health.severe")?3:severity>=r.health("health.moderate")?2:1;}
    public static void infect(NeedsState s,NeedsRules r,DiseaseCheckEvent.Exposure exposure,String detail){
        if(!enabled(r))return;
        AilmentType type=switch(exposure){
            case UNSAFE_FOOD->detail.startsWith("parasite:")?PARASITE:BACTERIAL;
            case DAMAGE->s.ailments().openWound()?BACTERIAL:null;
            case POLLUTION->BACTERIAL;
            case COLD,DAMP,NORMAL->VIRAL;
        };
        if(type!=null){double current=s.ailments().severity(type,WHOLE);s.ailments().set(type,WHOLE,Math.min(100,current==0?type.rule(r,"initial"):current+type.rule(r,"gain")));}
    }
    public static void trauma(NeedsState s,NeedsRules r,AilmentType type,AilmentState.Site site,double damage){
        if(!enabled(r)||damage<=0||!type.local||type==DISABILITY)return;
        double current=s.ailments().severity(type,site),delta=damage*type.rule(r,"gain");
        if(delta<=0)return;
        double severity=Math.min(100,current==0?Math.max(type.rule(r,"initial"),delta):current+delta);s.ailments().set(type,site,severity);
        // Old low-health saves do not spontaneously acquire a disability: only new major trauma can cause it.
        if((type==FRACTURE||type==BURN||type==LACERATION)&&severity>=r.health("health.disability_severity")&&s.get(site.stat)<=r.health("health.disability_health"))s.ailments().set(DISABILITY,site,Math.max(s.ailments().severity(DISABILITY,site),DISABILITY.rule(r,"initial")));
    }
    public static boolean canHeal(NeedsState s,NeedsRules r,BodyStat part){
        if(!enabled(r))return true;var site=AilmentState.Site.part(part);
        for(var type:List.of(FRACTURE,DISABILITY)){var c=s.ailments().get(type,site);if(c!=null&&c.care<=0)return false;}return true;
    }
    public static double fever(NeedsState s,NeedsRules r){if(!enabled(r))return 0;return grade(s.ailments().maximum(BACTERIAL),r)*BACTERIAL.rule(r,"fever")+grade(s.ailments().maximum(VIRAL),r)*VIRAL.rule(r,"fever");}
    public static double foodScale(NeedsState s,NeedsRules r){return !enabled(r)?1:Math.max(.1,1-grade(s.ailments().maximum(PARASITE),r)*PARASITE.rule(r,"absorption_loss"));}
    private static void thermal(NeedsState s,NeedsRules r,AilmentType type,boolean exposed,String clock){
        if(!exposed){s.clock(clock,0);return;}
        if(s.advance(clock,1,r.health("health.exposure_seconds"))>0){double v=s.ailments().severity(type,WHOLE);s.ailments().set(type,WHOLE,Math.min(100,v==0?type.rule(r,"initial"):v+type.rule(r,"gain")));}
    }
    /** Returns combined HP damage; resource costs, severity and timers are advanced exactly once. */
    public static double advance(NeedsState s,NeedsRules r,Rest rest){
        if(!enabled(r))return 0;
        double temperature=s.get(BodyStat.TEMPERATURE);
        thermal(s,r,HEATSTROKE,temperature>=r.health("health.heat_limit"),"ill_heat");thermal(s,r,HYPOTHERMIA,temperature<=r.health("health.cold_limit"),"ill_cold");
        double hp=0;
        for(var c:s.ailments().cases()){
            var t=c.type;int g=c.grade(r);c.age=Math.min(1e9,c.age+1);c.cooldown=Math.max(0,c.cooldown-1);boolean cared=c.care>0;c.care=Math.max(0,c.care-1);
            switch(t){
                case BACTERIAL->{drain(s,Need.WATER,t.rule(r,"water"),g);s.add(BodyStat.IMMUNITY,-t.rule(r,"immunity")*g/60);}
                case PARASITE->{drain(s,Need.ENERGY,t.rule(r,"energy"),g);drain(s,Need.PROTEIN,t.rule(r,"protein"),g);}
                case VIRAL->{drain(s,Need.SLEEP,t.rule(r,"sleep"),g);s.add(BodyStat.SANITY,-t.rule(r,"sanity")*g/60);}
                case HEATSTROKE->{drain(s,Need.WATER,t.rule(r,"water"),g);drain(s,Need.SLEEP,t.rule(r,"sleep"),g);}
                case HYPOTHERMIA->drain(s,Need.ENERGY,t.rule(r,"energy"),g);
                case BURN,SCRATCH,LACERATION->drain(s,Need.WATER,t.rule(r,"water"),g);
                case FRACTURE,BRUISE,DISABILITY->{if(c.site==TORSO)drain(s,Need.ENERGY,t.rule(r,"energy"),g);}
            }
            double interval=t.rule(r,"pulse_seconds");c.pulse+=1;int pulses=(int)(c.pulse/interval);c.pulse-=pulses*interval;
            if(pulses>0){double factor=cared?r.health("health.care_bleed_multiplier"):1;
                if(t==LACERATION||g==3)hp+=pulses*t.rule(r,"damage")*g*factor;
                if((t==BURN||t==LACERATION)&&c.site.stat!=null)s.add(c.site.stat,-pulses*t.rule(r,"body_loss")*g*factor);
            }
            boolean infection=t==BACTERIAL||t==PARASITE||t==VIRAL;
            boolean comfortable=t==HEATSTROKE?temperature<=r.health("health.heat_safe"):t==HYPOTHERMIA?temperature>=r.health("health.cold_safe"):true;
            double change=0;
            if(infection&&!cared&&s.get(BodyStat.IMMUNITY)<r.health("health.immune_recover"))change=t.rule(r,"worsening")/60;
            else if(comfortable&&(!rest.combat||t==HEATSTROKE||t==HYPOTHERMIA)&&rest.food>0&&s.get(Need.WATER)>0){
                double immune=infection?Math.max(.1,s.get(BodyStat.IMMUNITY)/100):1;
                change=-t.rule(r,"recovery")/60*immune*(rest.sleeping?r.health("health.bed_multiplier"):1)*(cared?r.health("health.care_multiplier"):1);
            }
            s.ailments().set(t,c.site,Math.clamp(c.severity+change,0,100));
        }
        return Math.min(r.health("health.damage_cap"),hp);
    }
    private static void drain(NeedsState s,Need n,double amount,int grade){s.add(n,-amount*grade/60);}
    public static Penalties penalties(NeedsState s,NeedsRules r){
        if(!enabled(r))return new Penalties(0,0,0,0,0);
        double movement=0,attack=0,speed=0,mining=0,jump=0;
        for(var c:s.ailments().cases()){
            int grade=c.grade(r);var t=c.type;
            switch(t){
                case VIRAL->attack+=t.rule(r,"attack")*grade;
                case HYPOTHERMIA->{movement+=t.rule(r,"movement")*grade;mining+=t.rule(r,"mining")*grade;}
                case FRACTURE,BRUISE,DISABILITY->{
                    if(c.site.leg()){double loss=t.rule(r,"movement")*grade;movement+=loss;jump+=loss;}
                    if(c.site.arm()){speed+=t.rule(r,"attack_speed")*grade;mining+=t.rule(r,"mining")*grade;}
                    if(c.site==HEAD)mining+=t.rule(r,"mining")*grade;
                    if(c.site==TORSO)movement+=t.rule(r,"movement")*grade/2;
                }
                default->{}
            }
        }
        double cap=r.health("health.penalty_cap");return new Penalties(Math.min(cap,movement),Math.min(cap,attack),Math.min(cap,speed),Math.min(cap,mining),Math.min(cap,jump));
    }
    private static ResourceLocation id(String suffix){return ResourceLocation.fromNamespaceAndPath(RealNeeds.ID,"ailment_"+suffix);}
    private static void modifier(ServerPlayer p,net.minecraft.core.Holder<Attribute> attribute,String suffix,double penalty){var a=p.getAttribute(attribute);if(a==null)return;var id=id(suffix);a.removeModifier(id);if(penalty>0)a.addTransientModifier(new AttributeModifier(id,-penalty,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));}
    public static void refresh(ServerPlayer p,NeedsState s,NeedsRules r){
        var n=RealNeeds.active(p)?penalties(s,r):new Penalties(0,0,0,0,0);
        modifier(p,Attributes.MOVEMENT_SPEED,"movement",n.movement);modifier(p,Attributes.ATTACK_DAMAGE,"attack",n.attack);modifier(p,Attributes.ATTACK_SPEED,"attack_speed",n.attackSpeed);modifier(p,Attributes.BLOCK_BREAK_SPEED,"mining",n.mining);modifier(p,Attributes.JUMP_STRENGTH,"jump",n.jump);
        if(n.movement>=.3)p.setSprinting(false);
    }
    public static void second(ServerPlayer p,NeedsState s,NeedsRules r){
        double damage=advance(s,r,new Rest(s.clock("combat")>0,p.isSleeping(),p.getFoodData().getFoodLevel()));
        if(enabled(r)&&grade(s.ailments().maximum(HEATSTROKE),r)>=2)p.addEffect(new MobEffectInstance(MobEffects.CONFUSION,(int)r.health("health.nausea_seconds")*20,0,false,false,true));
        if(damage>0){float before=p.getHealth();INTERNAL.put(p.getUUID(),s);
            try{p.hurt(new net.minecraft.world.damagesource.DamageSource(p.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE)),(float)damage);}
            finally{INTERNAL.remove(p.getUUID());}
            if(p.getHealth()<before)s.add(BodyStat.SANITY,-r.value(PhysiologyRule.SANITY_HIT));
        }
    }
    public static String care(ServerPlayer p,NeedsState s,NeedsRules r,AilmentType type,AilmentState.Site site){
        if(!enabled(r)||!p.isAlive()||p.isSpectator())throw new IllegalArgumentException("当前无法护理");
        var c=s.ailments().get(type,site);if(c==null)throw new IllegalArgumentException("该部位没有这项病情");
        if(c.cooldown>0)throw new IllegalArgumentException("请等待 "+(int)Math.ceil(c.cooldown)+" 秒再护理");
        var item=MedicalSupplies.item(type.careItem);var inventory=p.getInventory();int slot=-1;
        for(int i=0;i<inventory.items.size();i++)if(inventory.items.get(i).is(item)){slot=i;break;}
        if(slot<0&&!p.getAbilities().instabuild)throw new IllegalArgumentException("需要1个"+type.careLabel);
        if(!p.getAbilities().instabuild)inventory.items.get(slot).shrink(1);
        s.ailments().set(type,site,Math.max(0,c.severity-type.rule(r,"care_reduction")));
        var remaining=s.ailments().get(type,site);if(remaining!=null){remaining.care=r.health("health.care_seconds");remaining.cooldown=r.health("health.care_cooldown");}
        if(type==HEATSTROKE)s.add(BodyStat.TEMPERATURE,-r.health("health.cooling_delta"));
        if(type==HYPOTHERMIA)s.add(BodyStat.TEMPERATURE,r.health("health.warming_delta"));
        inventory.setChanged();return "已使用"+type.careLabel+"；"+type.label+"程度降低";
    }
}
