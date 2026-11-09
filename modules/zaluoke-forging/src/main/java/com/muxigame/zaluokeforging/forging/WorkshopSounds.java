package com.muxigame.zaluokeforging.forging;

import com.muxigame.zaluokeforging.ZaluokeForging;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class WorkshopSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(Registries.SOUND_EVENT,ZaluokeForging.MOD_ID);
    public static final DeferredHolder<SoundEvent,SoundEvent> BELLOWS=add("bellows"), PLACE=add("place_work"),
            TAKE=add("take_work"), CLEAR=add("clear_slag"), COOL=add("solidify"), POUR=add("pour"), FIRE=add("embers"), FORGE_COMPLETE=add("forge_complete");
    private static DeferredHolder<SoundEvent,SoundEvent> add(String name) {
        return SOUNDS.register(name,()->SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ZaluokeForging.MOD_ID,name)));
    }
}
