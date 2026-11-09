package com.muxigame.zaluokeforging.forging;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

public enum ForgedAxeTier implements Tier {
    INSTANCE;
    public int getUses() { return Tiers.IRON.getUses()+20; }
    public float getSpeed() { return Tiers.IRON.getSpeed(); }
    public float getAttackDamageBonus() { return Tiers.IRON.getAttackDamageBonus(); }
    public TagKey<Block> getIncorrectBlocksForDrops() { return Tiers.IRON.getIncorrectBlocksForDrops(); }
    public int getEnchantmentValue() { return Tiers.IRON.getEnchantmentValue(); }
    public Ingredient getRepairIngredient() { return Tiers.IRON.getRepairIngredient(); }
}
