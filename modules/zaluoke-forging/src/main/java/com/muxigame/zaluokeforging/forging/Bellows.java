package com.muxigame.zaluokeforging.forging;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import com.muxigame.zaluokeforging.ZaluokeForging;
import com.muxigame.zaluokeforging.block.WorkshopEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = ZaluokeForging.MOD_ID)
public final class Bellows {
    private static final Map<Level, Map<BlockPos, Long>> ACTIVE = new WeakHashMap<>();
    public static boolean boost(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof WorkshopEntity hearth && hearth.kind().equals("blacksmith_hearth")) {
            hearth.boostUntil = level.getGameTime() + 120;
            hearth.changed();
            return true;
        }
        if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity) {
            ACTIVE.computeIfAbsent(level, key -> new HashMap<>()).put(pos.immutable(), level.getGameTime() + 120);
            return true;
        }
        return false;
    }
    public static boolean active(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof WorkshopEntity machine) return machine.boostUntil > level.getGameTime();
        return ACTIVE.getOrDefault(level, Map.of()).getOrDefault(pos, 0L) > level.getGameTime();
    }
    public static boolean heat(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof WorkshopEntity machine) return machine.kind().equals("blacksmith_hearth") && machine.fire > 0;
        return level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace && furnace.litTime > 0;
    }
    @SubscribeEvent
    public static void tick(LevelTickEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Map<BlockPos, Long> positions = ACTIVE.get(level);
        if (positions == null) return;
        positions.entrySet().removeIf(entry -> entry.getValue() <= level.getGameTime());
        for (BlockPos pos : positions.keySet()) {
            if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace)) continue;
            // Vanilla spends one tick afterwards; one extra every second tick makes 1.5x fuel use.
            if (furnace.litTime > 0 && (level.getGameTime() & 1) == 0) furnace.litTime--;
            if (furnace.litTime <= 0) {
                ItemStack fuel = furnace.getItem(1);
                int duration = fuel.getBurnTime(furnace instanceof BlastFurnaceBlockEntity ? RecipeType.BLASTING : RecipeType.SMELTING);
                if (furnace instanceof BlastFurnaceBlockEntity) duration /= 2;
                if (duration > 0) {
                    ItemStack remainder = fuel.getCraftingRemainingItem();
                    fuel.shrink(1);
                    if (fuel.isEmpty()) furnace.setItem(1, remainder);
                    furnace.litTime = duration;
                    furnace.litDuration = duration;
                    level.setBlock(pos, furnace.getBlockState().setValue(AbstractFurnaceBlock.LIT, true), 3);
                } else if (furnace.getBlockState().getValue(AbstractFurnaceBlock.LIT)) {
                    level.setBlock(pos, furnace.getBlockState().setValue(AbstractFurnaceBlock.LIT, false), 3);
                }
            }
            furnace.setChanged();
        }
    }
    private Bellows() { }
}
