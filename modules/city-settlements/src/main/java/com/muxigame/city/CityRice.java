package com.muxigame.city;

import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.registries.*;

/** Rice uses vanilla crop growth. Worker harvesting never invokes loot tables. */
public final class CityRice extends CropBlock {
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(CityMod.ID);
    public static final DeferredBlock<CityRice> CROP=BLOCKS.register("rice_crop",()->new CityRice());
    private CityRice(){super(BlockBehaviour.Properties.ofFullCopy(Blocks.WHEAT));}
    @Override protected ItemLike getBaseSeedId(){return net.minecraft.world.item.Items.WHEAT_SEEDS;}
    @Override protected void randomTick(net.minecraft.world.level.block.state.BlockState state,net.minecraft.server.level.ServerLevel level,net.minecraft.core.BlockPos pos,net.minecraft.util.RandomSource random){level.setBlock(pos,Blocks.WHEAT.defaultBlockState().setValue(AGE,state.getValue(AGE)),2);}
}
