package com.muxigame.sevenfold;

import java.util.Optional;
import net.minecraft.network.chat.Component;

/** The seven attack aspects; physical aspects are not professions. */
public enum Aspect {
    SHARPNESS("sharpness", false, 0xF0F0E8),
    BLUNT("blunt", false, 0xF0F0E8),
    PIERCE("pierce", false, 0xF0F0E8),
    FIRE("fire", true, 0xFF4500),
    EARTH("earth", true, 0xA48354, 0x63A947),
    FROST("frost", true, 0x529DEA, 0xE1F7FF),
    THUNDER("thunder", true, 0xF0CD46, 0x7C2446);

    public final String id;
    public final boolean magic;
    public final int color;
    public final int secondaryColor;
    Aspect(String id, boolean magic, int color) { this(id,magic,color,color); }
    Aspect(String id, boolean magic, int color, int secondaryColor) { this.id=id; this.magic=magic; this.color=color;this.secondaryColor=secondaryColor; }
    public Component label() { return Component.translatable("aspect.sevenfold_mastery."+id); }
    public Component description() { return Component.translatable("aspect.sevenfold_mastery."+id+".description"); }
    public static Optional<Aspect> find(String id) {
        for (var a:values()) if(a.id.equals(id)) return Optional.of(a);
        return Optional.empty();
    }
}
