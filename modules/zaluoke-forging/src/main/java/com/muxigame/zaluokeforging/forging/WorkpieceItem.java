package com.muxigame.zaluokeforging.forging;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

/** Different heat-stage items share one placed block, but keep their own translated names. */
public final class WorkpieceItem extends BlockItem {
    private final String id;
    public WorkpieceItem(String id, Block block, Properties properties) { super(block,properties); this.id=id; }
    @Override public void inventoryTick(net.minecraft.world.item.ItemStack stack,net.minecraft.world.level.Level level,net.minecraft.world.entity.Entity entity,int slot,boolean selected) {
        super.inventoryTick(stack,level,entity,slot,selected);
        if(!level.isClientSide && level.getGameTime()%10==0 && entity instanceof net.minecraft.world.entity.player.Player player) {
            int damage=Workpiece.burnDamage(stack,level.getGameTime());
            if(damage>0 && !player.isCreative()) player.hurt(level.damageSources().hotFloor(),damage);
            for(int i=0;i<player.getInventory().getContainerSize();i++) if(player.getInventory().getItem(i)==stack) { player.getInventory().setItem(i,Workpiece.settle(stack,level.getGameTime()));break; }
        }
    }
    @Override public boolean onEntityItemUpdate(net.minecraft.world.item.ItemStack stack,net.minecraft.world.entity.item.ItemEntity entity) {
        if(!entity.level().isClientSide && entity.level().getGameTime()%10==0) entity.setItem(Workpiece.settle(stack,entity.level().getGameTime()));
        return false;
    }
    @Override public String getDescriptionId() { return "item.zaluoke_forging."+id; }
}
