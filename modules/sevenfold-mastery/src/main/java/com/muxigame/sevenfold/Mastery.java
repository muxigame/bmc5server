package com.muxigame.sevenfold;

import java.util.Optional;
import net.minecraft.network.chat.Component;

/** Legacy resource keys retained for combat API/save compatibility; no selectable masteries. */
public enum Mastery {
    RESILIENCE("resilience", null, 0xF0F0E8),
    STAMINA("stamina", null, 0xF0F0E8),
    MIGHT("might", null, 0xF0F0E8),
    FOCUS("focus", null, 0xF0F0E8),
    FIRE("fire", Aspect.FIRE, 0xFF4500),
    EARTH("earth", Aspect.EARTH, 0xA48354),
    FROST("frost", Aspect.FROST, 0x529DEA),
    THUNDER("thunder", Aspect.THUNDER, 0xF0CD46);

    public final String id;
    public final Aspect magicAspect;
    public final boolean magic;
    public final int color;
    public final int secondaryColor;
    Mastery(String id, Aspect magicAspect, int color) {
        this.id=id;this.magicAspect=magicAspect;this.magic=magicAspect!=null;this.color=color;
        this.secondaryColor=magicAspect==null?color:magicAspect.secondaryColor;
    }
    public Component label(){return Component.translatable((magic?"aspect":"resource")+".sevenfold_mastery."+id);}
    public Component description(){return Component.translatable((magic?"aspect":"resource")+".sevenfold_mastery."+id+".description");}
    public static Optional<Mastery> find(String id){
        if(id.equals("insight"))return Optional.of(FOCUS);
        for(var mastery:values())if(mastery.id.equals(id))return Optional.of(mastery);
        return Optional.empty();
    }
}
