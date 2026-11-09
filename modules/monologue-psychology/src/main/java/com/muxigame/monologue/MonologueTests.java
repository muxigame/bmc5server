package com.muxigame.monologue;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(MonologueMod.ID)
@PrefixGameTestTemplate(false)
public final class MonologueTests {
    static void reject(GameTestHelper h,Runnable action){boolean bad=false;try{action.run();}catch(IllegalArgumentException e){bad=true;}h.assertTrue(bad,"Invalid change must be rejected");}
    @GameTest(template="empty") public static void defaultLibrary(GameTestHelper h){
        DialogueRules r=new DialogueRules();for(var s:Situation.values()){var e=r.entries.get(s);h.assertTrue(e.head.size()==3&&e.thought.size()==3,"Three lines per channel and situation");for(var l:e.head)DialogueRules.validateText(l);for(var l:e.thought)DialogueRules.validateText(l);}
        h.assertTrue(!r.enabled,"Disabled by default");h.assertTrue(r.thoughtOffset>0,"Thoughts below crosshair");h.succeed();
    }
    @GameTest(template="empty") public static void persistenceAndUndo(GameTestHelper h){
        DialogueRules r=new DialogueRules();String old=r.entries.get(Situation.HURT).thought.get(0);r.edit(0,"replace","hurt","thought",0,"先站稳脚步。");
        var t=r.save(new CompoundTag(),h.getLevel().registryAccess());var copy=DialogueRules.load(t,h.getLevel().registryAccess());
        h.assertTrue(copy.revision==1&&copy.entries.get(Situation.HURT).thought.get(0).equals("先站稳脚步。"),"Edited text saved");copy.edit(1,"undo","","",0,"");h.assertTrue(copy.entries.get(Situation.HURT).thought.get(0).equals(old),"Previous library saved and restored");h.succeed();
    }
    @GameTest(template="empty") public static void staleAndInvalidEditsAtomic(GameTestHelper h){
        DialogueRules r=new DialogueRules();r.edit(0,"duration","eat","",0,"120");CompoundTag before=r.config();
        reject(h,()->r.edit(0,"delete","eat","head",0,""));reject(h,()->r.edit(1,"duration","eat","",0,"999"));reject(h,()->r.edit(1,"replace","eat","head",0,"\n\t"));reject(h,()->r.edit(1,"replace","eat","head",0,"长".repeat(97)));reject(h,()->r.edit(1,"delete","eat","head",999,""));
        h.assertTrue(r.config().equals(before)&&r.revision==1,"Failure preserves config and revision");h.succeed();
    }
    @GameTest(template="empty") public static void emptyLibraryAndReset(GameTestHelper h){
        DialogueRules r=new DialogueRules();for(int i=0;i<3;i++)r.edit(r.revision,"delete","eat","thought",0,"");
        h.assertTrue(r.entries.get(Situation.EAT).thought.isEmpty(),"Empty list intentionally silences");var copy=DialogueRules.load(r.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());h.assertTrue(copy.entries.get(Situation.EAT).thought.isEmpty(),"Empty list stays empty after reload");
        copy.edit(copy.revision,"reset","","",0,"");h.assertTrue(copy.entries.get(Situation.EAT).thought.size()==3,"Defaults recoverable");h.succeed();
    }
    @GameTest(template="empty") public static void cooldownIsolationAndBoundaries(GameTestHelper h){
        DialogueGate g=new DialogueGate();h.assertTrue(g.allow(Situation.HURT,0,60),"First event");h.assertTrue(!g.allow(Situation.HURT,59,60),"Repeated damage suppressed");h.assertTrue(g.allow(Situation.EAT,1,100),"Different event independent");h.assertTrue(g.allow(Situation.HURT,60,60),"Cooldown boundary accepted");h.assertTrue(new DialogueGate().allow(Situation.HURT,1,60),"Other player independent");h.succeed();
    }
    @GameTest(template="empty") public static void randomNoConsecutiveRepeat(GameTestHelper h){
        DialogueGate g=new DialogueGate();String last="";for(int i=0;i<20;i++){String next=g.choose("hurt",List.of("甲","乙","丙"),0);h.assertTrue(!next.equals(last),"No consecutive repeat");last=next;}
        h.assertTrue(g.choose("empty",List.of(),0).isEmpty(),"Empty safe");h.assertTrue(g.choose("one",List.of("仅一句"),-1).equals("仅一句"),"Single line safe");h.succeed();
    }
    @GameTest(template="empty") public static void priorityAndExpiry(GameTestHelper h){
        ActiveLine line=new ActiveLine();h.assertTrue(line.offer("进食",1,0,4000),"Initial");h.assertTrue(line.offer("受击",3,20,4000),"Damage interrupts eating");h.assertTrue(!line.offer("进食",1,30,4000),"Eating cannot overwrite hurt");h.assertTrue(line.active(4019)&&!line.active(4020),"Exact expiry");h.assertTrue(line.offer("击杀",2,4020,4000),"Next after expiry");h.succeed();
    }
    @GameTest(template="empty") public static void thoughtPrivacy(GameTestHelper h){
        UUID owner=UUID.randomUUID();var cue=new DialogueNetwork.Cue(owner,"minecraft:overworld","说出口","只在心里",80,3,24);
        h.assertTrue(cue.forViewer(owner).thought().equals("只在心里"),"Owner sees thoughts");var other=cue.forViewer(UUID.randomUUID());h.assertTrue(other.thought().isEmpty()&&other.head().equals("说出口"),"Other players only receive speech");h.succeed();
    }
    @GameTest(template="empty") public static void classifyEnemies(GameTestHelper h){
        DialogueRules.get(h.getLevel().getServer()).enabled=true;var p=h.makeMockServerPlayerInLevel();var zombie=h.spawn(EntityType.ZOMBIE,new BlockPos(1,2,1));var cow=h.spawn(EntityType.COW,new BlockPos(2,2,1));var wolf=h.spawn(EntityType.WOLF,new BlockPos(3,2,1));
        h.assertTrue(MonologueMod.enemy(zombie,p),"Hostile mobs count");h.assertTrue(!MonologueMod.enemy(cow,p),"Livestock not enemies");wolf.tame(p);wolf.setTarget(p);h.assertTrue(!MonologueMod.enemy(wolf,p),"Own pet never an enemy");cow.setTarget(p);h.assertTrue(MonologueMod.enemy(cow,p),"Aggressive neutral / NPC target counts");h.succeed();
    }
    @GameTest(template="empty") public static void actualDamageHook(GameTestHelper h){
        DialogueRules.get(h.getLevel().getServer()).enabled=true;var p=h.makeMockServerPlayerInLevel();p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(false);p.setHealth(20);var zombie=h.spawn(EntityType.ZOMBIE,new BlockPos(1,2,1));zombie.setNoAi(true);
        // GameTest's mock is hard-coded creative and starts with spawn protection.
        // A bypass-invulnerability damage type still executes the real health/Post path.
        var source=new net.minecraft.world.damagesource.DamageSource(p.damageSources().genericKill().typeHolder(),zombie);
        int before=MonologueMod.session(p).emitted;p.hurt(source,1);
        h.assertTrue(MonologueMod.session(p).emitted==before+1&&MonologueMod.session(p).lastSituation==Situation.HURT,"Actual health damage emits hurt");
        p.invulnerableTime=0;p.hurt(p.damageSources().fall(),1);h.assertTrue(MonologueMod.session(p).emitted==before+1,"Environmental fall is not attack dialogue");h.succeed();
    }
    @GameTest(template="empty") public static void actualKillHook(GameTestHelper h){
        DialogueRules.get(h.getLevel().getServer()).enabled=true;var p=h.makeMockServerPlayerInLevel();var zombie=h.spawn(EntityType.ZOMBIE,new BlockPos(1,2,1));zombie.setNoAi(true);
        int before=MonologueMod.session(p).emitted;zombie.hurt(p.damageSources().playerAttack(p),1000);
        h.assertTrue(MonologueMod.session(p).emitted==before+1&&MonologueMod.session(p).lastSituation==Situation.KILL,"Actual hostile death emits kill");h.succeed();
    }
    @GameTest(template="empty") public static void foodFinishAndDrinkIgnored(GameTestHelper h){
        DialogueRules.get(h.getLevel().getServer()).enabled=true;var p=h.makeMockServerPlayerInLevel();int before=MonologueMod.session(p).emitted;
        NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(p,new ItemStack(Items.MILK_BUCKET),32,new ItemStack(Items.BUCKET)));
        h.assertTrue(MonologueMod.session(p).emitted==before,"Milk without FoodProperties ignored");
        NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(p,new ItemStack(Items.APPLE),32,ItemStack.EMPTY));
        h.assertTrue(MonologueMod.session(p).emitted==before+1&&MonologueMod.session(p).lastSituation==Situation.EAT,"Food finish event emits eating");h.succeed();
    }
    @GameTest(template="empty") public static void nonAdminCannotWrite(GameTestHelper h){
        var p=h.makeMockServerPlayerInLevel();var r=DialogueRules.get(p.server);CompoundTag before=r.config();long rev=r.revision;
        h.assertTrue(!MonologueMod.canEdit(p),"Mock player has no admin permission");MonologueMod.request(p,new DialogueNetwork.Request("reset","hurt","head",0,"",rev));
        h.assertTrue(r.revision==rev&&r.config().equals(before),"Unauthorized packet does not mutate library");h.succeed();
    }
    @GameTest(template="empty") public static void disabledMigrationAndManualToggle(GameTestHelper h){
        var r=new DialogueRules();r.enabled=true;r.entries.get(Situation.HURT).head.set(0,"保留自定义台词");
        var old=r.save(new CompoundTag(),h.getLevel().registryAccess());old.putInt("schema",1);
        var migrated=DialogueRules.load(old,h.getLevel().registryAccess());
        h.assertTrue(!migrated.enabled&&migrated.isDirty(),"Old worlds disabled once and migration saved");
        h.assertTrue(migrated.entries.get(Situation.HURT).head.get(0).equals("保留自定义台词"),"Custom lines retained");
        migrated.edit(migrated.revision,"enabled","","",0,"");
        var restored=DialogueRules.load(migrated.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(restored.enabled,"Manual enable survives restart");
        restored.edit(restored.revision,"enabled","","",0,"");
        h.assertTrue(!restored.enabled,"Manual disable works");
        restored.edit(restored.revision,"reset","","",0,"");h.assertTrue(!restored.enabled,"Reset stays disabled");h.succeed();
    }
}