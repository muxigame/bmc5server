package com.muxigame.city;

import java.util.*;
import java.nio.file.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Semantic defaults plus extensible datapack tags; never guesses modded materials from their names. */
public final class CityBlockRules {
 static TagKey<Block> tag(String name){return TagKey.create(Registries.BLOCK,ResourceLocation.fromNamespaceAndPath(CityMod.ID,name));}
 public static final TagKey<Block> NATURAL=tag("maintenance/natural_materials"),IGNORE=tag("maintenance/ignore_changes"),LAST=tag("maintenance/protected_last"),WORK=tag("maintenance/workstations"),DECAY=tag("maintenance/force_decay"),EXACT=tag("maintenance/exact_state");
 static final Set<String> VOLATILE=Set.of("lit","open","powered","power","age","stage","moisture","waterlogged","snowy","distance","persistent","level","signal_fire","honey_level","berries","bloom","can_summon","shrieking","sculk_sensor_phase","triggered","occupied","has_book","has_record","extended","enabled","unstable");
 static boolean natural(BlockState s){return s.is(NATURAL);}
 static boolean ignored(BlockState s){return s.is(IGNORE)||s.getBlock() instanceof BushBlock||s.getBlock() instanceof GrowingPlantBlock||s.getBlock() instanceof LeavesBlock||s.getBlock() instanceof LiquidBlock||s.getBlock() instanceof FireBlock||s.getBlock() instanceof SnowLayerBlock||s.is(Blocks.PUMPKIN)||s.is(Blocks.MELON);}
 static boolean last(BlockState s){return s.is(LAST)||s.getBlock() instanceof EntityBlock||s.getBlock() instanceof BedBlock||s.getBlock() instanceof CraftingTableBlock;}
 static boolean workstation(BlockState s){return s.is(WORK)||s.getBlock() instanceof AbstractFurnaceBlock||s.getBlock() instanceof CraftingTableBlock||s.getBlock() instanceof RuralBlocks.WorkBlock;}
 static boolean decays(BlockState s){return !s.isAir()&&!ignored(s)&&(!natural(s)||s.is(DECAY));}
 static boolean same(BlockState a,BlockState b){if(a.getBlock()!=b.getBlock())return false;if(a.is(EXACT))return a.equals(b);for(var p:a.getProperties())if(!VOLATILE.contains(p.getName())&&!Objects.equals(a.getValue(p),b.getValue(p)))return false;return true;}
 static void table(Path path)throws java.io.IOException{Files.createDirectories(path.getParent());StringBuilder rows=new StringBuilder("block_id,natural_material,ignore_changes,protected_last,workstation,natural_decay\n");for(var b:BuiltInRegistries.BLOCK){var s=b.defaultBlockState();rows.append(BuiltInRegistries.BLOCK.getKey(b)).append(',').append(natural(s)).append(',').append(ignored(s)).append(',').append(last(s)).append(',').append(workstation(s)).append(',').append(decays(s)).append('\n');}Files.writeString(path,rows);}
}
