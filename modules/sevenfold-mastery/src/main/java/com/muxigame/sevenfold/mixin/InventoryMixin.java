package com.muxigame.sevenfold.mixin;
import com.muxigame.sevenfold.StarTreeState;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Inventory.class)
abstract class InventoryMixin {
    @Inject(method="getFreeSlot",at=@At("HEAD"),cancellable=true)
    private void sevenfold$free(CallbackInfoReturnable<Integer> cir){var inv=(Inventory)(Object)this;if(StarTreeState.of(inv.player).active()){for(int i=1;i<36;i++)if(inv.items.get(i).isEmpty()){cir.setReturnValue(i);return;}cir.setReturnValue(-1);}}
}
