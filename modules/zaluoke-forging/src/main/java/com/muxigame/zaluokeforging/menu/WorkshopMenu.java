package com.muxigame.zaluokeforging.menu;

import com.muxigame.zaluokeforging.ModContent;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class WorkshopMenu extends AbstractContainerMenu {
    public final ContainerData data;
    private final Container container;
    public WorkshopMenu(int id, Inventory inventory) { this(id,inventory,new SimpleContainer(3),new SimpleContainerData(11)); }
    public WorkshopMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(ModContent.MENU.get(),id);
        this.container=container; this.data=data;
        addSlot(new Slot(container,0,24,35) {
            @Override public boolean mayPlace(ItemStack stack) {
                return data.get(0)==0 ? stack.is(Items.RAW_IRON)||stack.is(ModContent.SLAG.get()) : stack.is(Items.COAL)||stack.is(Items.CHARCOAL);
            }
        });
        for(int i=1;i<3;i++) {
            final int machineSlot=i;
            addSlot(new Slot(container,i,24+30*i,35) {
                @Override public boolean isActive() { return data.get(0)==0 && machineSlot==1; }
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player) { return false; }
            });
        }
        for(int row=0;row<3;row++) for(int col=0;col<9;col++) addSlot(new Slot(inventory,col+row*9+9,8+col*18,112+row*18));
        for(int col=0;col<9;col++) addSlot(new Slot(inventory,col,8+col*18,170));
        addDataSlots(data);
    }
    @Override public boolean stillValid(Player player) { return container.stillValid(player); }
    @Override public ItemStack quickMoveStack(Player player,int index) {
        if(index==1||index==2) return ItemStack.EMPTY;
        Slot slot=slots.get(index);
        if(!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(),copy=stack.copy();
        if(index==0) { if(!moveItemStackTo(stack,3,39,true)) return ItemStack.EMPTY; }
        else if(slots.getFirst().mayPlace(stack)) { if(!moveItemStackTo(stack,0,1,false)) return ItemStack.EMPTY; }
        else if(index<30) { if(!moveItemStackTo(stack,30,39,false)) return ItemStack.EMPTY; }
        else if(!moveItemStackTo(stack,3,30,false)) return ItemStack.EMPTY;
        if(stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }
}
