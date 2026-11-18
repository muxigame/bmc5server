package com.muxigame.sevenfold;
public enum WarProfession {
    NONE, BERSERKER, WARDEN, KNIGHT, BRAWLER;
    public static WarProfession find(String id){try{return valueOf(id.toUpperCase(java.util.Locale.ROOT));}catch(IllegalArgumentException e){return NONE;}}
    public String id(){return name().toLowerCase(java.util.Locale.ROOT);}
}
