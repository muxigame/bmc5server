package com.muxigame.realneeds;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(RealNeeds.ID)
@PrefixGameTestTemplate(false)
public final class NeedsTests {
    @GameTest(template="empty") public static void editableInitialsPreserveExistingProgress(GameTestHelper h){
        var r=new NeedsRules();var edit=new CompoundTag();edit.putDouble("initial.water",32.5);edit.putDouble("initial.muscle",67);edit.putDouble("initial.head",73);edit.putDouble("intimidation_base",25);r.edit(0,edit);
        var s=NeedsState.load(new CompoundTag(),r);near(h,s.get(Need.WATER),32.5,"Configured new player water");near(h,s.get(BodyStat.MUSCLE),67,"Configured muscle");near(h,s.get(BodyStat.HEAD),73,"Configured head");near(h,s.get(BodyStat.INTIMIDATION),25,"Configured intimidation");
        s.set(Need.WATER,11);s.set(BodyStat.MUSCLE,40);var existing=NeedsState.load(s.save(),r);near(h,existing.get(Need.WATER),11,"Existing water is not reset");near(h,existing.get(BodyStat.MUSCLE),40,"Existing muscle is not reset");
        var saved=NeedsRules.load(r.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());near(h,saved.initial(BodyStat.HEAD),73,"Initials survive restart");h.assertTrue(saved.revision()==1,"Revision persists");saved.undo();near(h,saved.initial(Need.WATER),100,"Undo restores all initial values");h.succeed();
    }
    @GameTest(template="empty") public static void settingsAtomicityAndStaleEditor(GameTestHelper h){
        var r=new NeedsRules();var bad=new CompoundTag();bad.putDouble("block_muscle",2);bad.putDouble("sanity_seconds",0);
        try{r.edit(0,bad);h.fail("Invalid batch accepted");}catch(IllegalArgumentException expected){}
        near(h,r.value(PhysiologyRule.BLOCK_MUSCLE),.1,"No partially applied batch");h.assertTrue(r.revision()==0&&!r.canUndo(),"Invalid edit leaves revision and undo untouched");
        r.change("block_muscle",.7);var next=new CompoundTag();next.putDouble("block_muscle",.9);try{r.edit(0,next);h.fail("Stale editor accepted");}catch(IllegalArgumentException expected){}near(h,r.value(PhysiologyRule.BLOCK_MUSCLE),.7,"Stale save cannot overwrite");
        for(String key:new String[]{"unknown","initial.courage","initial.weight"}){bad=new CompoundTag();bad.putDouble(key,20);try{r.edit(r.revision(),bad);h.fail("Unknown key accepted");}catch(IllegalArgumentException expected){}}
        bad=new CompoundTag();bad.putString("water","5");try{r.edit(r.revision(),bad);h.fail("Wrong NBT type accepted");}catch(IllegalArgumentException expected){}
        bad=new CompoundTag();bad.putDouble("fire_start",70);bad.putDouble("fire_stop",40);try{r.edit(r.revision(),bad);h.fail("Inverted thresholds accepted");}catch(IllegalArgumentException expected){}h.succeed();
    }
    @GameTest(template="empty") public static void customRecoveryAmountsAndMultipliers(GameTestHelper h){
        var r=new NeedsRules();r.change("sanity_seconds",2);r.change("sanity_gain",4);r.change("immunity_seconds",8);r.change("immunity_gain",3);r.change("body_heal_seconds",8);r.change("body_heal_gain",5);r.change("bed_multiplier",2);r.change("full_food_multiplier",2);
        var s=new NeedsState();s.set(BodyStat.SANITY,50);s.set(BodyStat.IMMUNITY,50);s.set(BodyStat.HEAD,50);seconds(s,r,calm(20,true),2);near(h,s.get(BodyStat.SANITY),54,"Custom sanity interval and gain");near(h,s.get(BodyStat.IMMUNITY),53,"Custom immunity gain and multiplier");near(h,s.get(BodyStat.HEAD),55,"Custom body healing gain");h.succeed();
    }
    @GameTest(template="empty") public static void customBoredomTemperatureAndTemperament(GameTestHelper h){
        var r=new NeedsRules();r.change("boredom_limit",10);r.change("fat_boredom_gain",15);r.change("vomit_water",7);r.change("vomit_seconds",2);r.change("hostile_temperament",2);r.change("temperature_rate",0);r.change("fire_start",40);r.change("fire_stop",45);r.change("fire_seconds",1);
        var s=new NeedsState();NeedsPhysiology.food(s,r,"minecraft:cooked_porkchop",true);NeedsPhysiology.food(s,r,"minecraft:cooked_porkchop",true);h.assertTrue(NeedsPhysiology.food(s,r,"minecraft:cooked_porkchop",true),"Configured threshold triggers vomit");near(h,s.get(Need.WATER),93,"Configured vomit loss");near(h,s.clock("vomit"),2,"Configured vomit duration");NeedsPhysiology.kill(s,r,false,true,false);near(h,s.get(BodyStat.TEMPERAMENT),52,"Configured temperament delta");
        s.set(BodyStat.TEMPERATURE,20);for(int i=0;i<10;i++)NeedsPhysiology.temperature(s,r,true,0);near(h,s.get(BodyStat.TEMPERATURE),45,"Configured fire stop threshold");s.set(BodyStat.IMMUNITY,60);r.change("disease_percent",2);near(h,NeedsPhysiology.diseaseChance(s,r),80,"Configured disease probability");h.succeed();
    }
    @GameTest(template="empty") public static void parameterCatalogRoundTripAndPermission(GameTestHelper h){
        var r=new NeedsRules();var keys=new java.util.HashSet<String>();var patch=new CompoundTag();
        for(var e:NeedsParameters.ALL){h.assertTrue(keys.add(e.key()),"Unique GUI entry "+e.key());e.validate(e.initial());patch.putDouble(e.key(),e.initial());}
        r.edit(0,patch);for(var e:NeedsParameters.ALL)near(h,NeedsParameters.get(r.settings(),e),e.initial(),"GUI parameter round trip "+e.key());
        var p=survival(h);var worldRules=NeedsRules.get(p.server);long before=worldRules.revision();var edit=new CompoundTag();edit.putDouble("water",77);RealNeeds.edit(p,new NeedsNetwork.Edit(1,before,"save",edit));h.assertTrue(worldRules.revision()==before,"Unauthorized packet rejected");h.succeed();
    }
    private static NeedsPhysiology.Conditions calm(int food,boolean bed){return new NeedsPhysiology.Conditions(false,bed,food,false,false,false,false,false,50);}
    private static void seconds(NeedsState s,NeedsRules r,NeedsPhysiology.Conditions c,int count){for(int i=0;i<count;i++)NeedsPhysiology.second(s,r,c);}
    private static net.minecraft.server.level.ServerPlayer survival(GameTestHelper h){var p=h.makeMockServerPlayerInLevel();p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setInvulnerable(false);return p;}
    @GameTest(template="empty") public static void dailyDecayAndTraining(GameTestHelper h){
        var s=new NeedsState();var r=new NeedsRules();seconds(s,r,calm(10,false),1199);near(h,s.get(BodyStat.MUSCLE),50,"Day not elapsed");
        var copy=NeedsState.load(s.save());seconds(copy,r,calm(10,false),1);near(h,copy.get(BodyStat.MUSCLE),49,"Timer persists across reload");near(h,copy.get(BodyStat.PHYSIQUE),49,"Physique daily loss");
        NeedsPhysiology.block(copy,r);near(h,copy.get(BodyStat.MUSCLE),49.1,"Mining gain");near(h,copy.get(BodyStat.PHYSIQUE),48.9,"Mining cost");
        NeedsPhysiology.kill(copy,r,true,true,false);near(h,copy.get(BodyStat.MUSCLE),49.3,"NPC gain");near(h,copy.get(BodyStat.PHYSIQUE),48.8,"NPC cost");h.succeed();
    }
    @GameTest(template="empty") public static void damageSanityPoisonAndRecovery(GameTestHelper h){
        var s=new NeedsState();var r=new NeedsRules();NeedsPhysiology.damage(s,r,2,NeedsPhysiology.Injury.FALL,true,false);
        near(h,s.get(BodyStat.SANITY),99,"Each independent health damage loses one sanity");near(h,s.get(BodyStat.IMMUNITY),98,"Poison loses two immunity");near(h,s.get(BodyStat.LEFT_LEG),95,"Fall divided between legs");near(h,s.get(BodyStat.RIGHT_LEG),95,"Fall other leg");
        NeedsPhysiology.damage(s,r,0,NeedsPhysiology.Injury.HEAD,true,true);near(h,s.get(BodyStat.SANITY),99,"Zero damage ignored");
        seconds(s,r,calm(10,false),59);near(h,s.get(BodyStat.SANITY),99,"No early sanity recovery");seconds(s,r,calm(10,false),1);near(h,s.get(BodyStat.SANITY),100,"60 seconds recovery");
        NeedsPhysiology.death(s,r);near(h,s.get(BodyStat.SANITY),90,"Death loses ten");h.succeed();
    }
    @GameTest(template="empty") public static void immunityMultipliersAndCombat(GameTestHelper h){
        var r=new NeedsRules();for(int mode=0;mode<4;mode++){
            var s=new NeedsState();s.set(BodyStat.IMMUNITY,80);boolean bed=(mode&1)!=0;int food=(mode&2)!=0?20:10;int interval=420/((bed?2:1)*(food==20?2:1));
            seconds(s,r,calm(food,bed),interval-1);near(h,s.get(BodyStat.IMMUNITY),80,"No early multiplier recovery");seconds(s,r,calm(food,bed),1);near(h,s.get(BodyStat.IMMUNITY),81,"Bed/full hunger multiply independently");
        }
        var s=new NeedsState();s.set(BodyStat.IMMUNITY,80);s.set(BodyStat.SANITY,80);seconds(s,r,calm(0,true),500);near(h,s.get(BodyStat.IMMUNITY),80,"No food no immune recovery");
        var combat=new NeedsPhysiology.Conditions(true,true,20,false,false,false,false,false,50);seconds(s,r,combat,500);near(h,s.get(BodyStat.IMMUNITY),80,"Combat prevents recovery");
        s.set(BodyStat.SANITY,80);seconds(s,r,combat,60);near(h,s.get(BodyStat.SANITY),80,"Combat prevents sanity recovery");h.succeed();
    }
    @GameTest(template="empty") public static void exposureIntervalsAndDiseaseChance(GameTestHelper h){
        var r=new NeedsRules();int[] intervals={120,360,480,2000};var types=new DiseaseCheckEvent.Exposure[]{DiseaseCheckEvent.Exposure.POLLUTION,DiseaseCheckEvent.Exposure.COLD,DiseaseCheckEvent.Exposure.DAMP,DiseaseCheckEvent.Exposure.NORMAL};
        for(int i=0;i<4;i++){var s=new NeedsState();var c=new NeedsPhysiology.Conditions(false,false,0,false,i==0,i==1,i==2,false,50);for(int j=1;j<intervals[i];j++)h.assertTrue(!NeedsPhysiology.second(s,r,c).contains(types[i]),"No early exposure roll");h.assertTrue(NeedsPhysiology.second(s,r,c).contains(types[i]),"Exact exposure boundary");}
        var s=new NeedsState();near(h,NeedsPhysiology.diseaseChance(s),0,"100 immunity no risk");s.set(BodyStat.IMMUNITY,63);near(h,NeedsPhysiology.diseaseChance(s),37,"One percent per missing point");s.set(BodyStat.IMMUNITY,0);near(h,NeedsPhysiology.diseaseChance(s),100,"Zero immunity certain positive roll");h.succeed();
    }
    @GameTest(template="empty") public static void fireCorrectionAndEnvironmentTemperature(GameTestHelper h){
        var r=new NeedsRules();r.change("temperature_rate",0);var s=new NeedsState();s.set(BodyStat.TEMPERATURE,20);
        NeedsPhysiology.temperature(s,r,true,0);near(h,s.get(BodyStat.TEMPERATURE),25,"Immediate additive fire correction");
        NeedsPhysiology.temperature(s,r,true,0);near(h,s.get(BodyStat.TEMPERATURE),25,"Wait three active seconds");NeedsPhysiology.temperature(s,r,true,0);near(h,s.get(BodyStat.TEMPERATURE),30,"Five point warming step");
        for(int i=0;i<30;i++)NeedsPhysiology.temperature(s,r,true,0);near(h,s.get(BodyStat.TEMPERATURE),55,"Warming reaches 55 without repeated runaway correction");
        NeedsPhysiology.temperature(s,r,false,50);near(h,s.get(BodyStat.TEMPERATURE),50,"Leaving fire removes temporary correction");r.change("temperature_rate",.1);NeedsPhysiology.temperature(s,r,false,35);near(h,s.get(BodyStat.TEMPERATURE),49.9,"Environment gradual correction");h.succeed();
    }
    @GameTest(template="empty") public static void boredomHighFatMemoryAndVomit(GameTestHelper h){
        var r=new NeedsRules();var lean=new NeedsState();var fatty=new NeedsState();
        for(int i=0;i<4;i++){NeedsPhysiology.food(lean,r,"minecraft:apple",false);NeedsPhysiology.food(fatty,r,"minecraft:cooked_porkchop",true);}
        near(h,lean.get(BodyStat.FOOD_BOREDOM),36,"Ordinary repeated food");near(h,fatty.get(BodyStat.FOOD_BOREDOM),90,"High fat much faster");
        h.assertTrue(NeedsPhysiology.food(fatty,r,"minecraft:cooked_porkchop",true),"Continued high boredom causes vomiting");near(h,fatty.get(Need.WATER),70,"Vomit water loss");
        NeedsPhysiology.food(fatty,r,"minecraft:carrot",false);near(h,fatty.get(BodyStat.FOOD_BOREDOM),0,"Different food has own fatigue");
        var copy=NeedsState.load(fatty.save());h.assertTrue(NeedsPhysiology.food(copy,r,"minecraft:cooked_porkchop",true),"Changing food does not erase former fatigue");
        copy.recoverFatigue(10);near(h,copy.get(BodyStat.FOOD_BOREDOM),90,"Fatigue recovery");h.succeed();
    }
    @GameTest(template="empty") public static void physiqueMigrationIntimidationAndRules(GameTestHelper h){
        var b=new CompoundTag();b.putDouble("weight",123);b.putDouble("courage",90);var t=new CompoundTag();t.put("body",b);var s=NeedsState.load(t);
        near(h,s.get(BodyStat.PHYSIQUE),100,"Old weight maps into 0-100 physique");h.assertTrue(!s.save().getCompound("body").contains("courage"),"Courage removed");
        s=new NeedsState();var r=new NeedsRules();near(h,NeedsPhysiology.intimidation(s,r,0,1),20,"Default intimidation");h.assertTrue(NeedsPhysiology.intimidation(s,r,20,8)>20,"Equipment changes intimidation");s.set(BodyStat.MUSCLE,80);h.assertTrue(NeedsPhysiology.intimidation(s,r,0,1)>20,"Muscle affects intimidation");
        r.change("immunity_seconds",777);var copy=NeedsRules.load(r.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());near(h,copy.value(PhysiologyRule.IMMUNITY_SECONDS),777,"New rules saved");copy.undo();near(h,copy.value(PhysiologyRule.IMMUNITY_SECONDS),420,"New rules undo");h.succeed();
    }
    @GameTest(template="empty") public static void actualHealthDamageHook(GameTestHelper h){
        var p=survival(h);p.setHealth(20);new NeedsState().store(p);var zombie=h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE,new net.minecraft.core.BlockPos(1,2,1));zombie.setNoAi(true);
        var source=new net.minecraft.world.damagesource.DamageSource(p.damageSources().genericKill().typeHolder(),zombie);p.hurt(source,1);
        var s=NeedsState.of(p);near(h,s.get(BodyStat.SANITY),99,"Real Post damage hook");h.assertTrue(s.disease().getLong("count")==1&&s.clock("combat")>0,"Damage disease check and combat gate");h.succeed();
    }
    @GameTest(template="empty") public static void actualNpcKillHook(GameTestHelper h){
        var p=survival(h);new NeedsState().store(p);var npc=h.spawn(net.minecraft.world.entity.EntityType.VILLAGER,new net.minecraft.core.BlockPos(1,2,1));npc.setNoAi(true);h.assertTrue(NeedsWorld.npc(npc),"NPC entity tag consumed");npc.hurt(p.damageSources().playerAttack(p),1000);
        near(h,NeedsState.of(p).get(BodyStat.MUSCLE),50.2,"Real NPC death training");near(h,NeedsState.of(p).get(BodyStat.PHYSIQUE),49.9,"NPC activity physique loss");h.succeed();
    }
    @GameTest(template="empty") public static void actualBlockBreakAndCancelledGate(GameTestHelper h){
        var p=survival(h);new NeedsState().store(p);var level=h.getLevel();var pos=h.absolutePos(new net.minecraft.core.BlockPos(1,2,1));level.setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());var before=level.getBlockState(pos);
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(level,pos,before,p));NeedsWorld.finishBreaks();near(h,NeedsState.of(p).get(BodyStat.MUSCLE),50,"No credit before actual removal");
        var cancel=new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(level,pos,before,p);cancel.setCanceled(true);NeoForge.EVENT_BUS.post(cancel);level.removeBlock(pos,false);NeedsWorld.finishBreaks();near(h,NeedsState.of(p).get(BodyStat.MUSCLE),50,"Cancelled break no credit");
        level.setBlockAndUpdate(pos,before);NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(level,pos,before,p));level.removeBlock(pos,false);NeedsWorld.finishBreaks();near(h,NeedsState.of(p).get(BodyStat.MUSCLE),50.1,"Successful removal gets one training gain");h.succeed();
    }
    @GameTest(template="empty") public static void actualVomitFinishHook(GameTestHelper h){
        var p=survival(h);p.getFoodData().setFoodLevel(20);var s=new NeedsState();s.recordFood("minecraft:apple",false);s.set(BodyStat.FOOD_BOREDOM,90);s.store(p);
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish(p,new ItemStack(Items.APPLE),32,ItemStack.EMPTY));
        near(h,p.getFoodData().getFoodLevel(),12,"Real hunger loss");near(h,NeedsState.of(p).get(Need.WATER),70,"Real water loss");h.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.CONFUSION),"Short nausea applied");h.succeed();
    }
    @GameTest(template="empty") public static void actualHeatSourceRadiusAndLitState(GameTestHelper h){
        var p=survival(h);var base=h.absolutePos(new net.minecraft.core.BlockPos(1,2,1));p.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);
        var r=new NeedsRules();r.change("temperature_rate",0);var s=new NeedsState();s.set(BodyStat.TEMPERATURE,20);
        var pos=base.below();var block=net.minecraft.world.level.block.Blocks.CAMPFIRE.defaultBlockState();
        h.getLevel().setBlockAndUpdate(pos,block.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT,false));NeedsWorld.second(p,s,r);near(h,s.get(BodyStat.TEMPERATURE),20,"Unlit campfire not a heat source");
        h.getLevel().setBlockAndUpdate(pos,block.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT,true));NeedsWorld.second(p,s,r);near(h,s.get(BodyStat.TEMPERATURE),25,"Real lit source within two blocks");
        p.setPos(base.getX()+4.5,base.getY(),base.getZ()+.5);NeedsWorld.second(p,s,r);near(h,s.get(BodyStat.TEMPERATURE),20,"Outside radius removes only temporary correction");h.succeed();
    }
    @GameTest(template="empty") public static void diseaseExtensionDispatchAndTags(GameTestHelper h){
        var p=survival(h);var state=new NeedsState();state.set(BodyStat.IMMUNITY,0);var observed=new java.util.concurrent.atomic.AtomicReference<DiseaseCheckEvent>();
        java.util.function.Consumer<DiseaseCheckEvent> listener=e->{if(e.getPlayer()==p)observed.set(e);};NeoForge.EVENT_BUS.addListener(listener);
        try{NeedsWorld.check(p,state,DiseaseCheckEvent.Exposure.UNSAFE_FOOD,"minecraft:rotten_flesh");h.assertTrue(observed.get()!=null&&observed.get().passed()&&observed.get().getChancePercent()==100,"Public extension event with exact roll");}
        finally{NeoForge.EVENT_BUS.unregister(listener);}
        h.assertTrue(new ItemStack(Items.COOKED_PORKCHOP).is(NeedsFood.tag("high_fat"))&&new ItemStack(Items.ROTTEN_FLESH).is(NeedsFood.tag("unsafe_food")),"Default classifications loaded");h.succeed();
    }
    @GameTest(template="empty") public static void bodyFieldsMigrationAndIsolation(GameTestHelper h){
        var legacy=new CompoundTag();legacy.putDouble("water",37);
        var state=NeedsState.load(legacy);near(h,state.get(Need.WATER),37,"Old needs retained");
        h.assertTrue(BodyStat.values().length==15,"Courage replaced; temperature and intimidation added");
        for(var stat:BodyStat.values())near(h,state.get(stat),stat.initial,"Approved initial value "+stat);
        state.set(BodyStat.LEFT_ARM,0);state.set(BodyStat.PHYSIQUE,93.25);
        var restored=NeedsState.load(state.save());
        h.assertTrue(restored.isSet(BodyStat.LEFT_ARM),"Zero is a real configured value");near(h,restored.get(BodyStat.PHYSIQUE),93.25,"Body value persisted");
        restored.clear(BodyStat.PHYSIQUE);near(h,restored.get(BodyStat.PHYSIQUE),50,"Clear restores initial value");near(h,state.get(BodyStat.PHYSIQUE),93.25,"Clear is isolated");h.succeed();
    }
    @GameTest(template="empty") public static void bodyCorruptionAndValidation(GameTestHelper h){
        var body=new CompoundTag();body.putDouble("sanity",Double.NaN);body.putDouble("head",101);body.putDouble("muscle",-1);body.putDouble("entertainment",36);
        var tag=new CompoundTag();tag.put("body",body);var state=NeedsState.load(tag);
        near(h,state.get(BodyStat.SANITY),100,"Nonfinite repaired");near(h,state.get(BodyStat.HEAD),100,"Old values clamped");near(h,state.get(BodyStat.MUSCLE),0,"Floor");near(h,state.get(BodyStat.ENTERTAINMENT),36,"Valid field retained");
        for(double bad:new double[]{Double.POSITIVE_INFINITY,Double.NaN,-1,101}){
            boolean rejected=false;try{state.set(BodyStat.SANITY,bad);}catch(IllegalArgumentException ex){rejected=true;}h.assertTrue(rejected,"Bad debug value rejected");
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void mealHistoryPersistsWithoutInventedBoredom(GameTestHelper h){
        var state=new NeedsState();state.recordFood("minecraft:apple",false);state.recordFood("minecraft:apple",false);
        var restored=NeedsState.load(state.save());h.assertTrue(restored.foodStreak()==2&&restored.lastFood().equals("minecraft:apple"),"Repeated food survives reload");
        restored.recordFood("minecraft:cooked_porkchop",true);h.assertTrue(restored.foodStreak()==1&&restored.lastFoodHighFat(),"New type resets streak; classification recorded");
        near(h,restored.get(BodyStat.FOOD_BOREDOM),0,"History alone does not double-apply consumption mechanics");
        var player=h.makeMockServerPlayerInLevel();NeedsFood.consume(restored,new ItemStack(Items.MILK_BUCKET),player,1);h.assertTrue(restored.foodStreak()==1&&restored.lastFoodHighFat(),"Non-food drinks do not reset food history");h.succeed();
    }
    @GameTest(template="empty") public static void bodyCloneAndPassiveTicks(GameTestHelper h){
        var old=h.makeMockServerPlayerInLevel();var next=h.makeMockServerPlayerInLevel();var state=new NeedsState();
        for(var stat:BodyStat.values())state.set(stat,41.25);state.recordFood("minecraft:carrot",false);state.store(old);
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(next,old,true));var copy=NeedsState.of(next);copy.second(new NeedsRules(),true,false);
        for(var stat:BodyStat.values())near(h,copy.get(stat),41.25,"No unapproved passive mechanic "+stat);
        h.assertTrue(copy.foodStreak()==1&&copy.lastFood().equals("minecraft:carrot"),"Food history cloned");h.succeed();
    }
    private static void near(GameTestHelper h,double a,double b,String what){h.assertTrue(Math.abs(a-b)<0.00001,what+": "+a+" != "+b);}
    private static NeedsState empty(){NeedsState s=new NeedsState();for(Need n:Need.values())s.set(n,0);return s;}
    @GameTest(template="empty") public static void newPlayerAndLegacyData(GameTestHelper h){
        NeedsState s=NeedsState.load(new CompoundTag());for(Need n:Need.values())near(h,s.get(n),100,"Initial reserve");
        CompoundTag t=new CompoundTag();t.putDouble("water",24);t.putDouble("protein",Double.NaN);s=NeedsState.load(t);
        near(h,s.get(Need.WATER),24,"Existing value");near(h,s.get(Need.ENERGY),100,"New field default");near(h,s.get(Need.PROTEIN),100,"Corrupt value repaired");h.succeed();
    }
    @GameTest(template="empty") public static void boundsAndPersistence(GameTestHelper h){
        NeedsState s=new NeedsState();s.set(Need.WATER,-300);s.set(Need.ENERGY,800);s.set(Need.PROTEIN,42.125);
        NeedsState copy=NeedsState.load(s.save());near(h,copy.get(Need.WATER),0,"Floor");near(h,copy.get(Need.ENERGY),100,"Ceiling");near(h,copy.get(Need.PROTEIN),42.125,"Fraction survives save");
        boolean rejected=false;try{s.set(Need.SLEEP,Double.POSITIVE_INFINITY);}catch(IllegalArgumentException e){rejected=true;}h.assertTrue(rejected,"Non-finite mutation rejected");h.succeed();
    }
    @GameTest(template="empty") public static void minuteOfNormalActivity(GameTestHelper h){
        NeedsState s=new NeedsState();NeedsRules r=new NeedsRules();for(int i=0;i<60;i++)s.second(r,false,false);
        for(Need n:Need.values())near(h,s.get(n),100-n.rate,"Minute consumption "+n);h.succeed();
    }
    @GameTest(template="empty") public static void sprintAndSleep(GameTestHelper h){
        NeedsState s=new NeedsState();NeedsRules r=new NeedsRules();for(int i=0;i<60;i++)s.second(r,true,false);
        near(h,s.get(Need.WATER),97,"Sprint hydration");near(h,s.get(Need.ENERGY),97.75,"Sprint energy");near(h,s.get(Need.PROTEIN),99.6,"Protein unchanged multiplier");
        s.set(Need.SLEEP,30);s.second(r,false,true);near(h,s.get(Need.SLEEP),31,"Sleep recovery");h.succeed();
    }
    @GameTest(template="empty") public static void disableAndRulesUndo(GameTestHelper h){
        NeedsRules r=new NeedsRules();r.change("enabled",0);NeedsState s=new NeedsState();s.second(r,true,true);for(Need n:Need.values())near(h,s.get(n),100,"Disabled");
        r.undo();h.assertTrue(r.enabled,"Undo enabled");r.change("water",7);r.reset();near(h,r.rates[3],2,"Reset");r.undo();near(h,r.rates[3],7,"Restore pre-reset");h.succeed();
    }
    @GameTest(template="empty") public static void rulesSaveAndValidation(GameTestHelper h){
        NeedsRules r=new NeedsRules();r.change("water",8);r.change("food",2);
        NeedsRules copy=NeedsRules.load(r.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());near(h,copy.rates[3],8,"World rules saved");near(h,copy.foodMultiplier,2,"Food scale saved");copy.undo();near(h,copy.foodMultiplier,1,"Undo saved across restart");
        for(String key:new String[]{"unknown","enabled","sprint"}){boolean bad=false;try{r.change(key,50);}catch(IllegalArgumentException e){bad=true;}h.assertTrue(bad,"Invalid rule rejected "+key);}h.succeed();
    }
    @GameTest(template="empty") public static void meatAndVegetables(GameTestHelper h){
        var p=h.makeMockServerPlayerInLevel();NeedsState s=empty();NeedsFood.consume(s,new ItemStack(Items.COOKED_BEEF),p,1);
        near(h,s.get(Need.PROTEIN),18,"Steak protein");near(h,s.get(Need.ENERGY),24,"Steak energy");near(h,s.get(Need.VITAMINS),0,"No arbitrary vitamin credit");
        NeedsFood.consume(s,new ItemStack(Items.CARROT),p,1);near(h,s.get(Need.VITAMINS),15,"Carrot vitamins");near(h,s.get(Need.ENERGY),33,"Carrot energy");h.succeed();
    }
    @GameTest(template="empty") public static void waterOnlyAndMilk(GameTestHelper h){
        var p=h.makeMockServerPlayerInLevel();NeedsState s=empty();ItemStack water=new ItemStack(Items.POTION);water.set(DataComponents.POTION_CONTENTS,new PotionContents(Potions.WATER));NeedsFood.consume(s,water,p,1);near(h,s.get(Need.WATER),35,"Water bottle");
        ItemStack poison=new ItemStack(Items.POTION);poison.set(DataComponents.POTION_CONTENTS,new PotionContents(Potions.POISON));NeedsFood.consume(s,poison,p,1);near(h,s.get(Need.WATER),35,"Non-water potion ignored");
        NeedsFood.consume(s,new ItemStack(Items.MILK_BUCKET),p,1);near(h,s.get(Need.WATER),55,"Milk hydration");h.succeed();
    }
    @GameTest(template="empty") public static void foodScalingAndCaps(GameTestHelper h){
        var p=h.makeMockServerPlayerInLevel();NeedsState s=empty();NeedsFood.consume(s,new ItemStack(Items.COOKED_BEEF),p,2);near(h,s.get(Need.PROTEIN),36,"Runtime food multiplier");
        for(int i=0;i<10;i++)NeedsFood.consume(s,new ItemStack(Items.COOKED_BEEF),p,2);near(h,s.get(Need.PROTEIN),100,"Food cap");h.succeed();
    }
    @GameTest(template="empty") public static void cloneAndPlayerIsolation(GameTestHelper h){
        var old=h.makeMockServerPlayerInLevel();var next=h.makeMockServerPlayerInLevel();var other=h.makeMockServerPlayerInLevel();NeedsState s=empty();s.set(Need.WATER,27);s.store(old);
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(next,old,true));near(h,NeedsState.of(next).get(Need.WATER),27,"Death clone preserves reserves");near(h,NeedsState.of(other).get(Need.WATER),100,"Other player isolated");
        s.set(Need.WATER,11);s.store(old);near(h,NeedsState.of(next).get(Need.WATER),27,"No mutable data alias");h.succeed();
    }
}
