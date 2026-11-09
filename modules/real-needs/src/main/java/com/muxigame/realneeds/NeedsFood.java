package com.muxigame.realneeds;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.entity.player.Player;

public final class NeedsFood {
    public static TagKey<Item> tag(String name){return TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath(RealNeeds.ID,name));}
    public static void consume(NeedsState state,ItemStack stack,Player player,double scale){
        var rules=player instanceof net.minecraft.server.level.ServerPlayer p?NeedsRules.get(p.server):new NeedsRules();
        var food=stack.getFoodProperties(player);
        if(food!=null){
            state.add(Need.ENERGY,food.nutrition()*rules.value(PhysiologyRule.FOOD_ENERGY)*scale);
        }
        if(stack.is(tag("protein")))state.add(Need.PROTEIN,rules.value(PhysiologyRule.FOOD_PROTEIN)*scale);
        if(stack.is(tag("vitamins")))state.add(Need.VITAMINS,rules.value(PhysiologyRule.FOOD_VITAMINS)*scale);
        if(stack.is(tag("hydrating")))state.add(Need.WATER,rules.value(PhysiologyRule.FOOD_WATER)*scale);
        var potion=stack.get(DataComponents.POTION_CONTENTS);
        if(stack.is(Items.POTION)&&potion!=null&&potion.is(Potions.WATER))state.add(Need.WATER,rules.value(PhysiologyRule.BOTTLE_WATER)*scale);
    }
}
