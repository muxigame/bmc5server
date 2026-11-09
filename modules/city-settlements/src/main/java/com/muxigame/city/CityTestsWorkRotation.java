package com.muxigame.city;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CityMod.ID) @PrefixGameTestTemplate(false)
public final class CityTestsWorkRotation {
 static List<RuralBlocks.WorkBlock> blocks(){return List.of(RuralBlocks.SIFTER.get(),RuralBlocks.STOVE.get(),RuralBlocks.DESK.get(),RuralBlocks.HUNTER.get(),RuralBlocks.GUILD.get(),RuralBlocks.OVEN.get(),RuralBlocks.SUPPLY.get(),RuralBlocks.CHEST.get());}
 static BlockPos transform(BlockPos p,Rotation r){return switch(r){case NONE->p;case CLOCKWISE_90->new BlockPos(-p.getZ(),p.getY(),p.getX());case CLOCKWISE_180->new BlockPos(-p.getX(),p.getY(),-p.getZ());case COUNTERCLOCKWISE_90->new BlockPos(p.getZ(),p.getY(),-p.getX());};}
 @GameTest(template="empty") public static void everyWorkBlockFacingMatchesCoordinateTransform(GameTestHelper h){
  var property=BlockStateProperties.HORIZONTAL_FACING;
  for(var block:blocks())for(var facing:Direction.Plane.HORIZONTAL){var original=block.defaultBlockState().setValue(property,facing);var vector=BlockPos.ZERO.relative(facing);
   for(var rotation:Rotation.values()){var actual=original.rotate(rotation);h.assertTrue(BlockPos.ZERO.relative(actual.getValue(property)).equals(transform(vector,rotation)),"All eight work/storage blocks must rotate in the same direction as their archive coordinates");h.assertTrue(actual.getBlock()==block,"Rotation preserves block identity");}
   for(var mirror:List.of(Mirror.LEFT_RIGHT,Mirror.FRONT_BACK)){var actual=original.mirror(mirror);var expected=mirror==Mirror.FRONT_BACK?new BlockPos(-vector.getX(),0,vector.getZ()):new BlockPos(vector.getX(),0,-vector.getZ());h.assertTrue(BlockPos.ZERO.relative(actual.getValue(property)).equals(expected),"Each mirror matches the selected horizontal axis");h.assertTrue(actual.mirror(mirror).equals(original),"Two identical mirrors restore the exact state");h.assertTrue(original.mirror(mirror).rotate(Rotation.CLOCKWISE_90).mirror(mirror).equals(original.rotate(Rotation.COUNTERCLOCKWISE_90)),"Mirror/rotation composition uses consistent axes");}
   var roundtrip=original;for(int i=0;i<4;i++)roundtrip=roundtrip.rotate(Rotation.CLOCKWISE_90);h.assertTrue(roundtrip.equals(original)&&original.rotate(Rotation.CLOCKWISE_90).rotate(Rotation.COUNTERCLOCKWISE_90).equals(original),"Quarter-turn cycles and inverse turns restore the exact state");
  }h.succeed();
 }
 @GameTest(template="empty") public static void relativeLinkMetadataNeverExportsIdentityOrAssets(GameTestHelper h){
  var server=h.getLevel().getServer();var rural=RuralSettlement.get(server);var economy=CityEconomy.get(server);var trade=CityTrade.get(server);
  String people=CityEconomy.JSON.toJson(rural.state),money=CityEconomy.JSON.toJson(economy.state),business=CityEconomy.JSON.toJson(trade.state);
  for(var block:blocks())for(var facing:Direction.Plane.HORIZONTAL){var link=new RuralBlocks.Link(new BlockPos(3,4,5),block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,facing));link.offset=new BlockPos(2,1,3);link.inventory=NonNullList.withSize(27,ItemStack.EMPTY);link.inventory.set(0,new ItemStack(Items.WHEAT,7));var tag=link.saveWithFullMetadata(h.getLevel().registryAccess());h.assertTrue(Arrays.equals(tag.getIntArray("RuralWorkOffset"),new int[]{2,1,3}),"Archive keeps only the relative work-link configuration");h.assertTrue(tag.getAllKeys().equals(Set.of("id","x","y","z","RuralWorkOffset")),"Entity identity, family, cash and materialized inventory must not enter building NBT");h.assertTrue(link.inventory.get(0).getCount()==7,"Metadata serialization cannot consume or duplicate live inventory");}
  h.assertTrue(people.equals(CityEconomy.JSON.toJson(rural.state))&&money.equals(CityEconomy.JSON.toJson(economy.state))&&business.equals(CityEconomy.JSON.toJson(trade.state)),"Transform metadata tests cannot change people, assets or economic progress");h.succeed();
 }
}
