package com.muxigame.sevenfold.mixin;
import com.muxigame.sevenfold.CombatSlots;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Slot.class)
abstract class CombatSlotMixin {
    @Inject(method="mayPlace",at=@At("HEAD"),cancellable=true)
    private void sevenfold$allowed(ItemStack stack,CallbackInfoReturnable<Boolean> cir){var slot=(Slot)(Object)this;if(slot.container instanceof Inventory inv&&!CombatSlots.mayPlace(inv.player,slot.getContainerSlot(),stack))cir.setReturnValue(false);}
}
