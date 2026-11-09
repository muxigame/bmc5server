package com.muxigame.realneeds;

import static com.muxigame.realneeds.AilmentType.*;
import static com.muxigame.realneeds.AilmentState.Site.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(RealNeeds.ID)
@PrefixGameTestTemplate(false)
public final class NeedsTestsIllness {
    private static void near(GameTestHelper h,double a,double b,String msg){h.assertTrue(Math.abs(a-b)<.00001,msg+": "+a+" != "+b);}
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h){var p=h.makeMockServerPlayerInLevel();p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setInvulnerable(false);p.setHealth(20);new NeedsState().store(p);return p;}
    private static NeedsIllness.Rest rest(){return new NeedsIllness.Rest(false,false,20);}
    private static void step(NeedsState s,NeedsRules r,int seconds){for(int i=0;i<seconds;i++)NeedsIllness.advance(s,r,rest());}
    @GameTest(template="empty") public static void individualThermalGrowthAndDisabilityInitial(GameTestHelper h){
        var r=new NeedsRules();r.change("health.exposure_seconds",1);r.change(HEATSTROKE.key("initial"),3);r.change(HEATSTROKE.key("gain"),9);r.change(HYPOTHERMIA.key("initial"),7);r.change(HYPOTHERMIA.key("gain"),4);
        var hot=new NeedsState();hot.set(BodyStat.TEMPERATURE,75);step(hot,r,2);near(h,hot.ailments().severity(HEATSTROKE,WHOLE),12,"Heat growth has independent consumer");
        var cold=new NeedsState();cold.set(BodyStat.TEMPERATURE,20);step(cold,r,2);near(h,cold.ailments().severity(HYPOTHERMIA,WHOLE),11,"Cold growth independent");
        r.change(DISABILITY.key("initial"),55);var injury=new NeedsState();injury.set(BodyStat.LEFT_LEG,0);NeedsIllness.trauma(injury,r,FRACTURE,LEFT_LEG,15);near(h,injury.ailments().severity(DISABILITY,LEFT_LEG),55,"Configured initial disability used");
        h.assertTrue(!HealthParameters.BY_KEY.containsKey(FRACTURE.key("worsening"))&&!HealthParameters.BY_KEY.containsKey(DISABILITY.key("gain")),"Inapplicable settings not offered");h.succeed();
    }
    @GameTest(template="empty") public static void allConditionsPersistenceAndGrades(GameTestHelper h){
        var s=new NeedsState();int count=0;for(var t:AilmentType.values())for(var site:AilmentState.Site.values())if(AilmentState.valid(t,site)){s.ailments().set(t,site,75);count++;}
        var copy=NeedsState.load(s.save());h.assertTrue(count==41&&copy.ailments().cases().size()==41,"All eleven types and six physical sites persist");var r=new NeedsRules();for(var c:copy.ailments().cases())h.assertTrue(c.grade(r)==3,"Severe grade persists");
        for(double v:new double[]{1,29.9,30,69.9,70,100}){copy.ailments().set(BACTERIAL,WHOLE,v);h.assertTrue(copy.ailments().get(BACTERIAL,WHOLE).grade(r)==(v<30?1:v<70?2:3),"Exact severity boundaries");}
        h.assertTrue(NeedsState.load(new CompoundTag()).ailments().cases().isEmpty(),"Old saves healthy, no invented injuries");copy.ailments().set(BACTERIAL,WHOLE,0);h.assertTrue(copy.ailments().get(BACTERIAL,WHOLE)==null,"Zero clears condition");h.succeed();
    }
    @GameTest(template="empty") public static void badConditionDataAndWrongSites(GameTestHelper h){
        var cases=new CompoundTag();var bad=new CompoundTag();bad.putDouble("severity",Double.NaN);cases.put("bacterial__whole",bad);var wrong=new CompoundTag();wrong.putDouble("severity",90);cases.put("viral__left_arm",wrong);h.assertTrue(AilmentState.load(cases).cases().isEmpty(),"NaN and invalid sites ignored");
        var s=new AilmentState();for(double v:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1,101}){boolean rejected=false;try{s.set(BURN,LEFT_ARM,v);}catch(IllegalArgumentException ex){rejected=true;}h.assertTrue(rejected,"Invalid severity rejected");}
        boolean rejected=false;try{s.set(FRACTURE,WHOLE,20);}catch(IllegalArgumentException ex){rejected=true;}h.assertTrue(rejected,"Fracture must have body site");h.succeed();
    }
    @GameTest(template="empty") public static void infectionRoutesAndIndependentSymptoms(GameTestHelper h){
        var r=new NeedsRules();var a=new NeedsState();var b=new NeedsState();var c=new NeedsState();
        NeedsIllness.infect(a,r,DiseaseCheckEvent.Exposure.POLLUTION,"test");NeedsIllness.infect(b,r,DiseaseCheckEvent.Exposure.UNSAFE_FOOD,"parasite:minecraft:chicken");NeedsIllness.infect(c,r,DiseaseCheckEvent.Exposure.COLD,"test");
        step(a,r,1);step(b,r,1);step(c,r,1);
        h.assertTrue(a.get(BodyStat.IMMUNITY)<100&&a.get(Need.WATER)<100&&NeedsIllness.fever(a,r)>0,"Bacteria immunity/water/fever");
        h.assertTrue(b.get(Need.PROTEIN)<100&&b.get(Need.ENERGY)<100&&NeedsIllness.foodScale(b,r)==.85,"Parasite nutrition absorption and reserves");
        h.assertTrue(c.get(Need.SLEEP)<100&&c.get(BodyStat.SANITY)<100&&NeedsIllness.penalties(c,r).attack()>0,"Virus fatigue, sanity and damage weakness");
        var intact=new NeedsState();NeedsIllness.infect(intact,r,DiseaseCheckEvent.Exposure.DAMAGE,"blunt");h.assertTrue(intact.ailments().cases().isEmpty(),"Closed blunt trauma has no infection entry");h.succeed();
    }
    @GameTest(template="empty") public static void thermalExposureHysteresisAndRecovery(GameTestHelper h){
        var r=new NeedsRules();var hot=new NeedsState();hot.set(BodyStat.TEMPERATURE,75);step(hot,r,29);h.assertTrue(hot.ailments().cases().isEmpty(),"Must sustain heat exposure");step(hot,r,1);near(h,hot.ailments().severity(HEATSTROKE,WHOLE),10,"Thirty seconds triggers mild heatstroke");
        hot.set(BodyStat.TEMPERATURE,65);step(hot,r,60);near(h,hot.ailments().severity(HEATSTROKE,WHOLE),10,"Hysteresis band holds severity");hot.set(BodyStat.TEMPERATURE,50);step(hot,r,60);near(h,hot.ailments().severity(HEATSTROKE,WHOLE),4,"Safe temperature recovers");
        var cold=new NeedsState();cold.set(BodyStat.TEMPERATURE,20);step(cold,r,30);h.assertTrue(cold.ailments().severity(HYPOTHERMIA,WHOLE)>0&&cold.get(Need.ENERGY)<100&&NeedsIllness.penalties(cold,r).movement()>0,"Cold exposure and distinct slowing/energy");h.succeed();
    }
    @GameTest(template="empty") public static void bodySitesAndDisabilityRequireNewTrauma(GameTestHelper h){
        var s=new NeedsState();var r=new NeedsRules();s.set(BodyStat.LEFT_LEG,0);step(s,r,60);h.assertTrue(s.ailments().cases().isEmpty(),"No retroactive disability from old values");
        NeedsIllness.trauma(s,r,FRACTURE,LEFT_LEG,15);h.assertTrue(s.ailments().severity(DISABILITY,LEFT_LEG)>=70,"New severe injury on destroyed limb disables it");var leg=NeedsIllness.penalties(s,r);h.assertTrue(leg.movement()>0&&leg.jump()>0&&leg.attackSpeed()==0,"Leg affects walking/jumping, not attack speed");
        s=new NeedsState();s.ailments().set(FRACTURE,LEFT_ARM,50);var arm=NeedsIllness.penalties(s,r);h.assertTrue(arm.mining()>0&&arm.attackSpeed()>0&&arm.movement()==0,"Arm affects manipulation");h.assertTrue(!NeedsIllness.canHeal(s,r,BodyStat.LEFT_ARM)&&NeedsIllness.canHeal(s,r,BodyStat.RIGHT_ARM),"Untreated fracture blocks only own part healing");h.succeed();
    }
    @GameTest(template="empty") public static void bleedingPulseAndNaturalRecovery(GameTestHelper h){
        var r=new NeedsRules();r.change(LACERATION.key("recovery"),0);var s=new NeedsState();s.ailments().set(LACERATION,LEFT_ARM,40);
        for(int i=0;i<14;i++)near(h,NeedsIllness.advance(s,r,rest()),0,"No premature bleed pulse");near(h,NeedsIllness.advance(s,r,rest()),.5,"Moderate laceration periodic HP loss");near(h,s.get(BodyStat.LEFT_ARM),99.5,"Laceration also injures site");
        var bruise=new NeedsState();bruise.ailments().set(BRUISE,RIGHT_LEG,20);step(bruise,r,60);near(h,bruise.ailments().severity(BRUISE,RIGHT_LEG),18,"Bruises recover, distinct from permanent disability");
        var disability=new NeedsState();disability.ailments().set(DISABILITY,RIGHT_ARM,40);step(disability,r,600);near(h,disability.ailments().severity(DISABILITY,RIGHT_ARM),40,"Disability cannot naturally vanish");h.succeed();
    }
    @GameTest(template="empty") public static void lowImmunityWorseningAndBedCare(GameTestHelper h){
        var r=new NeedsRules();var s=new NeedsState();s.set(BodyStat.IMMUNITY,20);s.ailments().set(VIRAL,WHOLE,20);step(s,r,60);near(h,s.ailments().severity(VIRAL,WHOLE),21,"Low immunity infection worsens");
        s=new NeedsState();s.ailments().set(SCRATCH,LEFT_ARM,40);var c=s.ailments().get(SCRATCH,LEFT_ARM);c.care=300;
        for(int i=0;i<60;i++)NeedsIllness.advance(s,r,new NeedsIllness.Rest(false,true,20));near(h,s.ailments().severity(SCRATCH,LEFT_ARM),16,"Bed and care multiply recovery");var copy=NeedsState.load(s.save());near(h,copy.ailments().get(SCRATCH,LEFT_ARM).care(),240,"Care timer persists and no offline simulation");h.succeed();
    }
    @GameTest(template="empty") public static void actualSharpBluntFireAndPoisonHooks(GameTestHelper h){
        var p=player(h);p.setNoGravity(true);p.setPos(h.absolutePos(new net.minecraft.core.BlockPos(1,2,1)).getCenter());
        h.runAfterDelay(65,()->{p.setHealth(20);new NeedsState().store(p);
        var mob=h.spawn(EntityType.ZOMBIE,new net.minecraft.core.BlockPos(1,2,1));mob.setNoAi(true);mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));p.hurt(p.damageSources().mobAttack(mob),3);
        h.assertTrue(NeedsState.of(p).ailments().maximum(LACERATION)>0,"Actual sharp attack laceration");p.invulnerableTime=0;p.setHealth(20);mob.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);p.hurt(p.damageSources().mobAttack(mob),7);
        h.assertTrue(NeedsState.of(p).ailments().maximum(BRUISE)>0&&NeedsState.of(p).ailments().maximum(FRACTURE)>0,"Actual blunt fracture");
        p.invulnerableTime=0;p.setHealth(20);p.hurt(p.damageSources().inFire(),1);h.assertTrue(NeedsState.of(p).ailments().maximum(BURN)>0,"Actual burn hook");
        new NeedsState().store(p);p.invulnerableTime=0;p.hurt(p.damageSources().magic(),1);h.assertTrue(NeedsState.of(p).ailments().cases().isEmpty(),"Magic cannot cause a sword cut");h.succeed();
        });
    }
    @GameTest(template="empty") public static void realInfectionRollAndUnsafeFood(GameTestHelper h){
        var p=player(h);var s=new NeedsState();s.set(BodyStat.IMMUNITY,0);NeedsWorld.consumed(p,s,new ItemStack(Items.CHICKEN),NeedsRules.get(p.server));h.assertTrue(s.ailments().maximum(PARASITE)>0,"Raw food parasite at guaranteed immunity roll");
        s=new NeedsState();s.set(BodyStat.IMMUNITY,0);NeedsWorld.check(p,s,DiseaseCheckEvent.Exposure.POLLUTION,"source");h.assertTrue(NeedsState.of(p).ailments().maximum(BACTERIAL)>0,"Actual saved infection after public check");
        s=new NeedsState();NeedsWorld.check(p,s,DiseaseCheckEvent.Exposure.COLD,"source");h.assertTrue(s.ailments().cases().isEmpty(),"Full immunity prevents environmental infection");h.succeed();
    }
    @GameTest(template="empty") public static void actualInternalBleedingNoRecursionAndDeathClone(GameTestHelper h){
        var p=player(h);p.setNoGravity(true);p.setPos(h.absolutePos(new net.minecraft.core.BlockPos(1,2,1)).getCenter());
        h.runAfterDelay(65,()->{p.setHealth(20);new NeedsState().store(p);
        var r=new NeedsRules();r.change(LACERATION.key("pulse_seconds"),1);r.change(LACERATION.key("recovery"),0);var s=new NeedsState();s.ailments().set(LACERATION,LEFT_ARM,70);p.setHealth(1);NeedsIllness.second(p,s,r);s.store(p);
        near(h,p.getHealth(),.25,"Actual custom damage applies");near(h,s.get(BodyStat.SANITY),99,"One actual bleed event sanity loss");h.assertTrue(s.disease().getLong("count")==0&&s.ailments().cases().size()==1,"Internal damage cannot recursively create injuries/infections");
        p.invulnerableTime=0;NeedsIllness.second(p,s,r);s.store(p);h.assertTrue(!p.isAlive(),"Severe untreated bleeding can be fatal");near(h,s.get(BodyStat.SANITY),88,"Death extra sanity loss preserved in shared in-flight state");
        var next=player(h);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone(next,p,true));h.assertTrue(NeedsState.of(next).ailments().maximum(LACERATION)>0,"Respawning does not exploit-clear conditions");h.succeed();
        });
    }
    @GameTest(template="empty") public static void illnessDamagePreservesMotionAndOrdinaryCombatKnockback(GameTestHelper h){
        var p=player(h);p.setNoGravity(true);p.setPos(h.absolutePos(new net.minecraft.core.BlockPos(1,2,1)).getCenter());
        h.runAfterDelay(65,()->{
            var r=new NeedsRules();r.change(LACERATION.key("pulse_seconds"),1);r.change(LACERATION.key("recovery"),0);
            var s=new NeedsState();s.ailments().set(LACERATION,LEFT_ARM,70);
            var motions=java.util.List.of(net.minecraft.world.phys.Vec3.ZERO,new net.minecraft.world.phys.Vec3(.12,0,-.04),new net.minecraft.world.phys.Vec3(.12,.42,-.07));
            for(int i=0;i<motions.size();i++){
                var velocity=motions.get(i);p.setHealth(20);p.invulnerableTime=0;p.setOnGround(i<2);p.setDeltaMovement(velocity);p.hurtMarked=false;
                var position=p.position();float fall=p.fallDistance;float yaw=p.getYRot(),pitch=p.getXRot();
                p.hurtTime=i;p.hurtDuration=i+3;p.walkAnimation.setSpeed(.23F);
                for(int pulse=0;pulse<3;pulse++){
                    p.invulnerableTime=0;float health=p.getHealth();NeedsIllness.second(p,s,r);
                    near(h,p.getHealth(),health-.75,"Illness still deals real HP damage");
                    h.assertTrue(p.getDeltaMovement().distanceToSqr(velocity)<1e-12,"Repeated illness must preserve standing, walking and jumping velocity, case "+i);
                    h.assertTrue(p.position().distanceToSqr(position)<1e-12,"Illness never teleports or displaces player");
                    h.assertTrue(p.onGround()==(i<2)&&!p.hurtMarked,"Illness does not request impact velocity sync or change ground state");
                    near(h,p.fallDistance,fall,"Fall distance preserved");near(h,p.getYRot(),yaw,"Yaw preserved");near(h,p.getXRot(),pitch,"Pitch preserved");
                    h.assertTrue(p.hurtTime==i&&p.hurtDuration==i+3,"Illness must not create or erase a real-hit animation");
                    near(h,p.walkAnimation.speed(),.23F,"Illness must not kick walk animation");
                }
            }
            near(h,s.get(BodyStat.SANITY),91,"All nine real damage pulses still cost sanity");
            h.assertTrue(s.ailments().cases().size()==1&&s.disease().getLong("count")==0,"No recursive illness or trauma");
            var source=new net.minecraft.world.damagesource.DamageSource(p.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(NeedsIllness.DAMAGE));
            h.assertTrue(source.is(net.minecraft.tags.DamageTypeTags.NO_KNOCKBACK)&&source.is(net.minecraft.tags.DamageTypeTags.NO_IMPACT),"Loaded illness damage tags exclude knockback and impact");
            p.setHealth(20);p.invulnerableTime=0;p.setOnGround(true);p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            var mob=h.spawn(EntityType.ZOMBIE,new net.minecraft.core.BlockPos(2,2,1));mob.setNoAi(true);
            p.hurt(p.damageSources().mobAttack(mob),2);
            h.assertTrue(p.getDeltaMovement().lengthSqr()>1e-6,"Ordinary melee still knocks player back");
            h.assertTrue(p.hurtTime==10&&p.hurtDuration==10,"Ordinary melee retains hit animation");
            h.succeed();
        });
    }
    @GameTest(template="empty") public static void modifiersComposeAndDisabledCleanUp(GameTestHelper h){
        var p=player(h);var r=new NeedsRules();var other=ResourceLocation.fromNamespaceAndPath("test","other_speed");var attribute=p.getAttribute(Attributes.MOVEMENT_SPEED);attribute.addTransientModifier(new AttributeModifier(other,.1,AttributeModifier.Operation.ADD_VALUE));
        var s=new NeedsState();s.ailments().set(FRACTURE,LEFT_LEG,80);NeedsIllness.refresh(p,s,r);double reduced=attribute.getValue();h.assertTrue(reduced<.2&&attribute.getModifier(other)!=null,"Ailment composes with other modifiers");
        r.change("health.enabled",0);NeedsIllness.refresh(p,s,r);near(h,attribute.getValue(),.2,"Disable removes own movement penalty");h.assertTrue(attribute.getModifier(other)!=null,"Other mod modifier retained");double severity=s.ailments().severity(FRACTURE,LEFT_LEG);step(s,r,100);near(h,s.ailments().severity(FRACTURE,LEFT_LEG),severity,"Disabled freezes progression");h.succeed();
    }
    @GameTest(template="empty") public static void careConsumesSupplyAndPreventsReplay(GameTestHelper h){
        var p=player(h);var r=new NeedsRules();var s=new NeedsState();s.ailments().set(FRACTURE,RIGHT_ARM,70);p.getInventory().setItem(0,new ItemStack(MedicalSupplies.item("splint"),2));NeedsIllness.care(p,s,r,FRACTURE,RIGHT_ARM);
        near(h,s.ailments().severity(FRACTURE,RIGHT_ARM),65,"Splint reduces fracture");h.assertTrue(p.getInventory().getItem(0).getCount()==1&&NeedsIllness.canHeal(s,r,BodyStat.RIGHT_ARM),"One supply consumed, own part can heal during care");
        boolean denied=false;try{NeedsIllness.care(p,s,r,FRACTURE,RIGHT_ARM);}catch(IllegalArgumentException ex){denied=true;}h.assertTrue(denied&&p.getInventory().getItem(0).getCount()==1,"Duplicate care blocked without item loss");
        s.ailments().set(BURN,LEFT_ARM,40);denied=false;try{NeedsIllness.care(p,s,r,BURN,LEFT_ARM);}catch(IllegalArgumentException ex){denied=true;}h.assertTrue(denied,"Cannot care without matching supply");h.succeed();
    }
    @GameTest(template="empty") public static void healthActionPermissionAndConfigPersistence(GameTestHelper h){
        var p=player(h);boolean denied=false;try{RealNeeds.applyHealthAction(p,new NeedsNetwork.HealthAction(1,"set","bacterial","whole",80));}catch(IllegalArgumentException ex){denied=true;}h.assertTrue(denied&&NeedsState.of(p).ailments().cases().isEmpty(),"Non-admin cannot set disease");
        var r=new NeedsRules();r.change("health.severe",80);r.change(LACERATION.key("damage"),1);var copy=NeedsRules.load(r.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());near(h,copy.health("health.severe"),80,"Health threshold persists");near(h,copy.health(LACERATION.key("damage")),1,"Unique damage parameter persists");copy.undo();near(h,copy.health(LACERATION.key("damage")),.25,"Undo restores disease setting");
        denied=false;try{r.change("health.moderate",90);}catch(IllegalArgumentException ex){denied=true;}h.assertTrue(denied,"Invalid severity threshold ordering rejected");h.succeed();
    }
}
