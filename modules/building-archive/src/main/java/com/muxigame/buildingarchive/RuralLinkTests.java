package com.muxigame.buildingarchive;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.neoforged.neoforge.gametest.*;
@GameTestHolder("building_archive") @PrefixGameTestTemplate(false)
public final class RuralLinkTests {
 @GameTest(template="empty") public static void relativeLinkTransforms(GameTestHelper h){var block=new CompoundTag();var nbt=new CompoundTag();nbt.putString("id","city_settlements:rural_link");nbt.putIntArray("RuralWorkOffset",new int[]{2,1,3});block.put("nbt",nbt);ArchiveData.transformRuralLink(block,0);h.assertTrue(java.util.Arrays.equals(nbt.getIntArray("RuralWorkOffset"),new int[]{-3,1,2}),"Clockwise link transform");ArchiveData.transformRuralLink(block,1);h.assertTrue(java.util.Arrays.equals(nbt.getIntArray("RuralWorkOffset"),new int[]{2,1,3}),"Inverse rotation restores link");ArchiveData.transformRuralLink(block,2);h.assertTrue(java.util.Arrays.equals(nbt.getIntArray("RuralWorkOffset"),new int[]{-2,1,3}),"Horizontal mirror preserves height");h.succeed();}
}
