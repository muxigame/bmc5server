package com.muxigame.city;

import java.util.*;
import java.nio.file.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CityMod.ID) @PrefixGameTestTemplate(false)
public class CityTestsAcceleration {
 @GameTest(template="empty") public static void acceleratedClockRatesAndReloadCarry(GameTestHelper h){
  for(int speed:new int[]{1,7,30,100}){var state=new RuralSettlement.State();state.speed=speed;int total=0;for(int n=0;n<20;n++){int steps=SimulationClock.credit(state);h.assertTrue(steps>=0&&steps<=5,"Bounded five data steps per real tick");total+=steps;}h.assertTrue(total==speed&&state.stepCredit==0,"Exactly selected speed per20 real ticks");}
  var rural=new RuralSettlement();rural.state.speed=100;rural.state.stepCredit=13;
  var restored=RuralSettlement.load(rural.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());h.assertTrue(restored.state.speed==100&&restored.state.stepCredit==13,"100x and fractional carry survive reload");
  restored.state.speed=7;h.assertTrue(SimulationClock.credit(restored.state)==1&&restored.state.stepCredit==0,"Carry is preserved across a speed change");h.succeed();
 }
 @GameTest(template="empty") public static void continuationPreservesAssetsAndExistingDeaths(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){
  CityTestsModel.configure(f);for(var c:f.towns){c.works.enabled=false;f.r.state.enabled.remove(c.id);}var dead=f.r.state.people.values().iterator().next();dead.dead=true;
  var old=f.t.state.modelRun;old.city=f.city.id;old.village=f.village.id;old.people.addAll(f.r.state.people.keySet());old.done=true;long money=f.t.money(f.s),grain=f.t.grain(f.s);int people=f.r.state.people.size();
  f.r.state.simulationTicks=48000;f.r.state.simulationDay=48000;var b=f.t.state.businesses.values().iterator().next();b.lastIncome=1000;b.departedDay=0;var price=f.t.state.towns.get(f.city.id).prices[2];price.emptySince=1000;
  CityModelLab.resume(f.s,120,100);var next=f.t.state.modelRun;
  h.assertTrue(next.continuation&&next.targetDays==120&&next.sampleDays==4&&f.r.state.speed==100,"Requested continuation target/speed; bounded samples");
  h.assertTrue(f.t.money(f.s)==money&&f.t.grain(f.s)==grain&&f.r.state.people.size()==people&&dead.dead&&next.initialDead.contains(dead.id),"No reset, revival, stock top-up or money injection");
  h.assertTrue(b.lastIncome==49000&&b.departedDay==2&&price.emptySince==49000,"Paused time preserves operating timers and the valid empty-consumer-market timer");
  h.assertTrue(f.city.works.clock==48000&&f.city.works.nextSegment==51000,"Relative next maintenance deadline preserved");
 }h.succeed();}
 static void scale(GameTestHelper h,int copies){try(var f=new CityTestsScale.Fixture(h,copies)){
  for(var c:f.towns){CityModel.configure(f.s,c,f.t.state.towns.get(c.id).urban);CityWorks.validate(c.works,c);CityHousing.validate(c,c.housing);}
  h.assertTrue(f.r.state.people.size()==copies*100&&f.initialCash==copies*30000L,"Bounded independently funded test copies, never live-world grants");
  f.r.state.speed=100;f.r.state.clockStarted=true;SimulationClock.clear();int iterations=30*240;long totalNanos=0,maxNanos=0;int queueMax=0;
  for(int n=1;n<=iterations;n++){long started=System.nanoTime();CityHousing.tick(f.s);CityWorks.tick(f.s);RuralSettlement.tick(f.s);long elapsed=System.nanoTime()-started;totalNanos+=elapsed;maxNanos=Math.max(maxNanos,elapsed);for(var c:f.towns)queueMax=Math.max(queueMax,c.works.queue.size());
   if(n%240==0){var row=f.sample(n/240);f.daily.add(row);f.conserved&=((Number)row.get("money")).longValue()==f.initialCash+CityCurrency.issued(f.s)-CityCurrency.retired(f.s)&&((Number)row.get("grainError")).longValue()==0&&((Number)row.get("meatError")).longValue()==0&&((Number)row.get("goodsError")).longValue()==0;}
  }
  var report=new LinkedHashMap<String,Object>();report.put("kind","automated_native_entrypoints_benchmark_not_actual_live_ticks");report.put("people",f.r.state.people.size());report.put("pairs",copies);report.put("speed",100);report.put("days",f.r.state.simulationTicks/24000.0);report.put("allDaysConserved",f.conserved);report.put("meanMsPerEquivalentRealTick",totalNanos/1e6/iterations);report.put("maxMsPerEquivalentRealTick",maxNanos/1e6);report.put("maxMaintenanceQueue",queueMax);report.put("daily",f.daily);report.put("businesses",f.t.state.businesses);report.put("accounts",f.e.state.accounts);
  try{Files.writeString(Path.of("fast100-scale"+(copies*100)+".json"),CityEconomy.JSON.toJson(report));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
  h.assertTrue(f.conserved&&f.r.state.simulationTicks==30*24000L,"Native100x entrypoints conserve all money/food/goods and exact clock");
  h.assertTrue(queueMax<64&&f.towns.stream().allMatch(c->c.works.clock==f.r.state.simulationTicks),"No maintenance queue saturation or diverging city clock");
  for(int n=0;n<copies;n++)h.assertTrue(f.t.state.towns.get(f.towns.get(n*2).id).partner.equals(f.towns.get(n*2+1).id),"Each city trades with its own village");
 }finally{SimulationClock.clear();}h.succeed();}
 @GameTest(template="empty",timeoutTicks=200) public static void twoHundredResidentsHundredSpeedThirtyDays(GameTestHelper h){scale(h,2);}
 @GameTest(template="empty",timeoutTicks=200) public static void fourHundredResidentsHundredSpeedThirtyDays(GameTestHelper h){scale(h,4);}
 @GameTest(batch="native_clock",template="empty",timeoutTicks=100) public static void realServerTicksShareOneHundredSpeedSchedule(GameTestHelper h){
  var f=new CityTestsScale.Fixture(h);f.r.state.speed=100;f.r.state.clockStarted=true;SimulationClock.clear();long game=f.s.overworld().getGameTime(),clock=f.r.state.simulationTicks;
  h.runAfterDelay(40,()->{try{long elapsed=f.s.overworld().getGameTime()-game,advanced=f.r.state.simulationTicks-clock;h.assertTrue(advanced==elapsed*100,"Exactly100 simulation ticks per actual server tick");h.assertTrue(f.towns.stream().allMatch(c->c.works.clock==advanced&&c.housing.clock==advanced),"Housing, maintenance and rural share identical scheduler");}finally{f.close();SimulationClock.clear();}h.succeed();});
 }
}
