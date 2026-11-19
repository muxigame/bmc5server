package com.muxigame.sevenfold.mixin;
import com.muxigame.sevenfold.CombatSlots;
import net.minecraft.world.inventory.*;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractContainerMenu.class)
abstract class MenuSwapMixin {
    @Inject(method="clicked",at=@At("HEAD"),cancellable=true)
    private void sevenfold$swap(int index,int button,ClickType type,Player p,CallbackInfo ci){var menu=(AbstractContainerMenu)(Object)this;if(type==ClickType.SWAP&&index>=0&&index<menu.slots.size()&&!CombatSlots.mayPlace(p,button,menu.getSlot(index).getItem()))ci.cancel();}
}
