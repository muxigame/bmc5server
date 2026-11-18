package com.muxigame.sevenfold.mixin;

import com.muxigame.sevenfold.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Transient RPG capacities must exist before the saved health is finally restored. */
@Mixin(Player.class)
abstract class PlayerRpgLoadMixin {
    @Inject(method="readAdditionalSaveData",at=@At("RETURN"))
    private void sevenfold$restoreSavedVitals(CompoundTag tag,CallbackInfo ci){
        var player=(Player)(Object)this;
        if(player.level().isClientSide)return;
        var state=StarTreeState.load(tag.getCompound("NeoForgeData").getCompound(StarTreeState.KEY));
        if(!state.active())return;
        // Entity's persistent-data load order can differ from Player's vanilla health load.
        // Populate only our key; the enclosing entity load retains every other module's data.
        state.store(player);
        StarAttributes.apply(player);
        if(tag.contains("Health",99))player.setHealth(Math.clamp(tag.getFloat("Health"),0,player.getMaxHealth()));
        if(tag.contains("AbsorptionAmount",99))player.setAbsorptionAmount(Math.clamp(tag.getFloat("AbsorptionAmount"),0,player.getMaxAbsorption()));
    }
}
