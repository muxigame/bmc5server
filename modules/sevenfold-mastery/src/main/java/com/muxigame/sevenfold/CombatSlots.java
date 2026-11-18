package com.muxigame.sevenfold;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ItemAbilities;

/** Server policy shared by menu checks, pickup and activation. Items are never discarded. */
public final class CombatSlots {
    public static final TagKey<Item> WEAPONS=tag("combat_weapons"),OCCUPANTS=tag("hand_occupants"),OFFHAND_OCCUPANTS=tag("offhand_occupants");
    private static TagKey<Item> tag(String id){return TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath(SevenfoldMastery.ID,id));}
    public static boolean allowed(ItemStack s){return s.isEmpty()||s.is(WEAPONS)||s.is(OCCUPANTS)||s.canPerformAction(ItemAbilities.SHIELD_BLOCK);}
    public static boolean accepted(int slot,ItemStack s){return allowed(s)||slot==40&&s.is(OFFHAND_OCCUPANTS);}
    public static boolean restricted(Player p,int slot){return (slot==0||slot==40)&&StarTreeState.of(p).active();}
    public static boolean mayPlace(Player p,int slot,ItemStack s){return !restricted(p,slot)||accepted(slot,s);}
    private static boolean insert(java.util.List<ItemStack> items,ItemStack stack){
        for(int i=1;i<36&&!stack.isEmpty();i++){var target=items.get(i);if(!target.isEmpty()&&ItemStack.isSameItemSameComponents(target,stack)){int move=Math.min(stack.getCount(),Math.max(0,target.getMaxStackSize()-target.getCount()));target.grow(move);stack.shrink(move);}}
        for(int i=1;i<36&&!stack.isEmpty();i++)if(items.get(i).isEmpty()){int move=Math.min(stack.getCount(),stack.getMaxStackSize());items.set(i,stack.copyWithCount(move));stack.shrink(move);}
        return stack.isEmpty();
    }
    public static boolean prepareActivation(Player p){
        var inv=p.getInventory();var trial=new java.util.ArrayList<ItemStack>();for(var item:inv.items)trial.add(item.copy());
        var first=inv.getItem(0);var off=inv.getItem(40);if(!allowed(first)){trial.set(0,ItemStack.EMPTY);if(!insert(trial,first.copy()))return false;}
        if(!accepted(40,off)&&!insert(trial,off.copy()))return false;
        for(int i=0;i<36;i++)inv.setItem(i,trial.get(i));if(!accepted(40,off))inv.setItem(40,ItemStack.EMPTY);inv.setChanged();return true;
    }
    /** Handles out-of-band command/mod writes as a recoverable escrow, including a full bag. */
    public static void reconcile(Player p){
        var inv=p.getInventory();var saved=p.getPersistentData().getList("SevenfoldHandRecovery",10);var remaining=new ListTag();
        for(int i=0;i<saved.size();i++){var stack=ItemStack.parseOptional(p.registryAccess(),saved.getCompound(i));if(!insert(inv.items,stack))remaining.add(stack.save(p.registryAccess()));}
        if(StarTreeState.of(p).active())for(int slot:new int[]{0,40}){var stack=inv.getItem(slot);if(!mayPlace(p,slot,stack)){inv.setItem(slot,ItemStack.EMPTY);if(!insert(inv.items,stack))remaining.add(stack.save(p.registryAccess()));}}
        p.getPersistentData().put("SevenfoldHandRecovery",remaining);if(!remaining.isEmpty()&&p.tickCount%100==0)p.displayClientMessage(Component.translatable("tree.sevenfold_mastery.recovery",remaining.size()),true);
    }
    public static boolean reserveOffhand(Player p,ItemStack occupant){
        if(!StarTreeState.of(p).active()||!(occupant.is(OCCUPANTS)||occupant.is(OFFHAND_OCCUPANTS)))return false;
        var inv=p.getInventory();var trial=new java.util.ArrayList<ItemStack>();for(var stack:inv.items)trial.add(stack.copy());
        if(ItemStack.isSameItemSameComponents(inv.getItem(40),occupant)&&inv.getItem(40).getCount()==occupant.getCount())return true;
        if(!insert(trial,inv.getItem(40).copy()))return false;
        for(int i=1;i<36;i++)inv.setItem(i,trial.get(i));inv.setItem(40,occupant.copy());inv.setChanged();return true;
    }
}
