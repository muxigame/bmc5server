package com.muxigame.sevenfold;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/** Read compatibility for pre-retirement saves. Not used to choose a profession or HUD. */
public record MasteryState(Mastery mastery, int resource) {
    public static final String KEY="SevenfoldMastery";
    public static final int MAX_RESOURCE=100; // Wisdom may increase the base twenty-point mana reserve.
    public MasteryState { resource=Math.clamp(resource,0,MAX_RESOURCE); }
    public static MasteryState of(Player player) { return load(player.getPersistentData().getCompound(KEY)); }
    public static void copyLegacy(Player from,Player to){
        if(from.getPersistentData().contains(KEY,10))to.getPersistentData().put(KEY,from.getPersistentData().getCompound(KEY).copy());
    }
    public static MasteryState load(CompoundTag tag) {
        // v1 saved damage aspects as professions. Preserve magic selections,
        // and require an explicit choice for former physical-aspect selections.
        String id=tag.contains("mastery",8)?tag.getString("mastery"):tag.getString("aspect");
        int reserve=tag.contains("resource",99)?tag.getInt("resource"):20;
        if(tag.getInt("schema")<3&&tag.contains("resource",99))reserve=(int)Math.ceil(Math.clamp(reserve,0,100)/5.0);
        return new MasteryState(Mastery.find(id).orElse(null),reserve);
    }
    public CompoundTag save() {
        var tag=new CompoundTag();
        tag.putInt("schema",3);
        if(mastery!=null)tag.putString("mastery",mastery.id);
        tag.putInt("resource",resource);
        return tag;
    }
    public void store(Player player) { player.getPersistentData().put(KEY,save()); }
}
