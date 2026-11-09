package com.muxigame.city;

import java.util.*;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CityMod.ID) @PrefixGameTestTemplate(false)
public class CityTestsWorkActors {
 @GameTest(template="empty") public static void playerPlacementUsesAllFourHorizontalFacings(GameTestHelper h){var p=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());var at=h.absolutePos(BlockPos.ZERO).above();float originalYaw=p.getYRot();try{for(float yaw:new float[]{0,90,180,270}){p.setYRot(yaw);var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at),net.minecraft.core.Direction.UP,at,false);var context=new net.minecraft.world.item.context.BlockPlaceContext(p,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(RuralBlocks.SIFTER.get().asItem()),hit);net.minecraft.world.level.block.Block block=RuralBlocks.SIFTER.get();var state=block.getStateForPlacement(context);h.assertTrue(state!=null&&state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)==p.getDirection().getOpposite(),"BlockItem dispatch must call the placement override, not a UseOnContext overload that leaves every block north");}}finally{p.setYRot(originalYaw);}h.succeed();}
 @GameTest(template="empty") public static void farmlandSupportsVisibleWorkWithoutBeingAFullCube(GameTestHelper h){try(var f=new CityTestsCityWorks.Fixture(h)){var at=f.bench.above(3);for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){var p=at.offset(x,0,z);h.getLevel().setBlock(p.below(),Blocks.FARMLAND.defaultBlockState(),2);h.getLevel().setBlock(p,Blocks.WHEAT.defaultBlockState(),2);h.getLevel().setBlock(p.above(),Blocks.AIR.defaultBlockState(),2);}var target=RuralWorkVisuals.walkable(h.getLevel(),at);h.assertTrue(target!=null&&h.getLevel().getBlockState(target.below()).is(Blocks.FARMLAND),"A crop plot is a valid work destination despite farmland's15/16height; no forced chunk or economy change needed");}h.succeed();}
 @GameTest(template="empty") public static void currentSchemaPreservesCookSexAndLegacyMigrationStaysScoped(GameTestHelper h){try(var f=new CityTestsCityWorks.Fixture(h)){var d=RuralSettlement.get(f.server);f.person.role=RuralRules.Role.COOK;f.person.female=false;var saved=d.save(new CompoundTag(),h.getLevel().registryAccess());h.assertTrue(!RuralSettlement.load(saved,h.getLevel().registryAccess()).state.people.get(f.worker).female,"Current schema read preserves individual sex; profession does not change it on restart");var json=com.google.gson.JsonParser.parseString(new String(saved.getByteArray("Json"),StandardCharsets.UTF_8)).getAsJsonObject();json.addProperty("schema",2);saved.putByteArray("Json",json.toString().getBytes(StandardCharsets.UTF_8));h.assertTrue(RuralSettlement.load(saved,h.getLevel().registryAccess()).state.people.get(f.worker).female,"Old schema2 still receives its one-time historical migration");}h.succeed();}
 @GameTest(template="empty") public static void cropVisualNeverCreatesASecondHarvest(GameTestHelper h){try(var f=new CityTestsCityWorks.Fixture(h)){var pos=f.bench.above(2);h.getLevel().setBlock(pos,Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE,7),2);long money=f.money();int people=RuralSettlement.get(f.server).state.people.size();int drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(3)).size();RuralWorkVisuals.cropVisual(h.getLevel(),pos);h.assertTrue(h.getLevel().getBlockState(pos).getValue(net.minecraft.world.level.block.CropBlock.AGE)==0&&f.money()==money&&RuralSettlement.get(f.server).state.people.size()==people&&h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(3)).size()==drops,"Crop animation resets appearance without drops, new resources, currency or population");}h.succeed();}
 @GameTest(template="empty") public static void visualsAreOptInAndPersistWithoutEconomicEdits(GameTestHelper h){try(var f=new CityTestsCityWorks.Fixture(h)){
  var d=RuralSettlement.get(f.server);h.assertTrue(!d.state.visualCities.contains(f.city.id),"Existing cities do not silently start actor navigation");long money=f.money();
  RuralWorkVisuals.enable(f.server,f.city.id,true);var saved=d.save(new CompoundTag(),h.getLevel().registryAccess());var loaded=RuralSettlement.load(saved,h.getLevel().registryAccess());h.assertTrue(loaded.state.visualCities.contains(f.city.id)&&f.money()==money,"Opt-in persists without creating people, goods or funding");
  var legacy=com.google.gson.JsonParser.parseString(new String(saved.getByteArray("Json"),StandardCharsets.UTF_8)).getAsJsonObject();legacy.remove("visualCities");saved.putByteArray("Json",legacy.toString().getBytes(StandardCharsets.UTF_8));h.assertTrue(RuralSettlement.load(saved,h.getLevel().registryAccess()).state.visualCities.isEmpty(),"Legacy state defaults to no visual cities");RuralWorkVisuals.enable(f.server,f.city.id,false);
 }finally{RuralWorkVisuals.clear();}h.succeed();}
 @GameTest(template="empty") public static void workSwingDoesNotAwardOrMutateEconomy(GameTestHelper h){Villager actor=null;try(var f=new CityTestsCityWorks.Fixture(h)){
  var d=RuralSettlement.get(f.server);d.state.enabled.add(f.city.id);RuralWorkVisuals.enable(f.server,f.city.id,true);f.person.workStatus="工作中";
  for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)h.getLevel().setBlock(f.bench.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
  actor=EntityType.VILLAGER.create(h.getLevel());actor.setUUID(f.worker);actor.setPersistenceRequired();actor.setPos(f.bench.getX()+.5,f.bench.getY(),f.bench.getZ()-.5);h.getLevel().addFreshEntity(actor);
  String before=CityEconomy.JSON.toJson(d.state),cash=CityEconomy.JSON.toJson(CityEconomy.get(f.server).state);RuralWorkVisuals.tick(f.server);
  h.assertTrue(RuralWorkVisuals.swings==1&&before.equals(CityEconomy.JSON.toJson(d.state))&&cash.equals(CityEconomy.JSON.toJson(CityEconomy.get(f.server).state)),"Visible work swing changes actor only, never production/night decisions/cash or stock");RuralWorkVisuals.enable(f.server,f.city.id,false);
 }finally{if(actor!=null)actor.discard();RuralWorkVisuals.clear();}h.succeed();}
 @GameTest(template="empty") public static void missingActorNeverStopsDataWorkOrSpawnsDuplicates(GameTestHelper h){try(var f=new CityTestsCityWorks.Fixture(h)){
  var d=RuralSettlement.get(f.server);d.state.enabled.add(f.city.id);f.city.works.day=0;RuralWorkVisuals.enable(f.server,f.city.id,true);long before=f.person.totalWorkingTicks,money=f.money();int people=d.state.people.size();
  RuralWorkVisuals.tick(f.server);RuralSettlement.workStep(f.server,d);
  h.assertTrue(f.person.totalWorkingTicks==before+20&&d.state.people.size()==people&&h.getLevel().getEntity(f.worker)==null&&f.money()==money,"No loaded actor: simulation still earns work time, no new actor or coin is manufactured");RuralWorkVisuals.enable(f.server,f.city.id,false);
 }finally{RuralWorkVisuals.clear();}h.succeed();}
 @GameTest(template="empty") public static void hundredActorRecordsHaveBoundedTickInspection(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h,1,3)){
  for(var c:f.towns)RuralWorkVisuals.enable(f.s,c.id,true);long money=f.t.money(f.s),grain=f.t.grain(f.s);RuralWorkVisuals.tick(f.s);
  h.assertTrue(RuralWorkVisuals.inspections<=2&&RuralWorkVisuals.paths<=1&&f.t.money(f.s)==money&&f.t.grain(f.s)==grain,"Hundred records: at most two actor inspections/one path request, no hidden economic work");for(var c:f.towns)RuralWorkVisuals.enable(f.s,c.id,false);
 }finally{RuralWorkVisuals.clear();}h.succeed();}
}
