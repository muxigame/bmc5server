package com.muxigame.sevenfold.mixin;
import com.muxigame.sevenfold.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerGamePacketListenerImpl.class)
abstract class SwapHandsMixin {
    @Shadow public ServerPlayer player;
    @Inject(method="handlePlayerAction",at=@At("HEAD"),cancellable=true)
    private void sevenfold$swap(ServerboundPlayerActionPacket packet,CallbackInfo ci){if(packet.getAction()==ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND&&StarTreeState.of(player).active()&&(!CombatSlots.allowed(player.getMainHandItem())||!CombatSlots.allowed(player.getOffhandItem()))){player.server.execute(()->player.inventoryMenu.broadcastFullState());ci.cancel();}}
}
