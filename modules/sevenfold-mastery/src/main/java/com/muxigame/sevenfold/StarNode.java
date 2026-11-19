package com.muxigame.sevenfold;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/** Stable IDs preserve old saves; crying is displayed as the Departure constellation. */
public enum StarNode {
    CRYING("crying",0xDDEBFF,null,WarProfession.NONE),
    WARRIOR("warrior",0xC62B40,null,WarProfession.NONE),
    ASSASSIN("assassin",0x17603D,null,WarProfession.NONE),
    MAGE("mage",0x2145A6,null,WarProfession.NONE),
    BERSERKER("berserker",0xC62B40,WARRIOR,WarProfession.BERSERKER),
    WARDEN("warden",0xC62B40,WARRIOR,WarProfession.WARDEN),
    KNIGHT("knight",0xC62B40,WARRIOR,WarProfession.KNIGHT),
    BRAWLER("brawler",0xC62B40,WARRIOR,WarProfession.BRAWLER),
    RESET("reset",0xA7B8CF,null,WarProfession.NONE);
    public final String id;public final int color;public final StarNode path;public final WarProfession profession;
    StarNode(String id,int color,StarNode path,WarProfession profession){this.id=id;this.color=color;this.path=path;this.profession=profession;}
    public boolean base(){return this==WARRIOR||this==ASSASSIN||this==MAGE;}
    public Component label(){return Component.translatable("star.sevenfold_mastery."+id);}
    public Component description(){return Component.translatable("star.sevenfold_mastery."+id+".description");}
    public static Optional<StarNode> find(String id){if(id.equals("departure"))return Optional.of(CRYING);for(var n:values())if(n.id.equals(id))return Optional.of(n);return Optional.empty();}
    public static final StarNode[] BASES={WARRIOR,ASSASSIN,MAGE};
    public int[][] edges(){return switch(this){
        case WARRIOR->new int[][]{{0,1},{1,3},{3,5},{5,6},{6,4},{4,2},{2,0},{7,8},{8,9}};
        case CRYING->new int[][]{{0,1},{0,2},{1,3},{2,4},{3,5},{4,6},{5,8},{6,8},{7,8},{8,9}};
        case MAGE->new int[][]{{0,1},{0,2},{1,3},{2,4},{3,5},{4,6},{5,7},{6,7},{8,9}};
        case BERSERKER->new int[][]{{0,1},{1,2},{2,3},{3,4},{5,6},{6,7},{7,8},{8,9}};
        case WARDEN->new int[][]{{0,1},{1,2},{1,3},{1,4},{5,6},{6,7},{6,8},{6,9}};
        case KNIGHT->new int[][]{{0,1},{1,2},{2,3},{3,4},{4,5},{5,6},{6,7},{7,0},{8,9}};
        default->new int[][]{{0,1},{1,2},{2,3},{3,4},{4,5},{5,6},{6,7},{7,0},{8,9}};
    };}
    public double[][] pattern(){return switch(this){
        case CRYING->new double[][]{{0,-.9},{-.48,-.35},{.48,-.35},{-.75,.1},{.75,.1},{-.48,.45},{.48,.45},{0,.15},{0,.72},{0,1}};
        case WARRIOR->new double[][]{{-.7,-.75},{.7,-.75},{-.7,-.1},{.7,-.1},{-.48,.5},{.48,.5},{0,.9},{0,-.65},{0,-.05},{0,.45}};
        case ASSASSIN->new double[][]{{-.8,-.2},{-.4,-.65},{.1,-.85},{.65,-.5},{.8,0},{.48,.5},{0,.8},{-.48,.5},{-.3,.05},{.25,.1}};
        case MAGE->new double[][]{{0,-.9},{-.6,-.5},{.6,-.5},{-.8,0},{.8,0},{-.5,.5},{.5,.5},{0,.85},{0,-.25},{0,.3}};
        case BERSERKER->new double[][]{{-.85,-.65},{-.35,-.8},{-.55,-.2},{-.15,.2},{.5,.8},{.85,-.65},{.35,-.8},{.55,-.2},{.15,.2},{-.5,.8}};
        case WARDEN->new double[][]{{-.55,-.9},{-.55,-.35},{-.85,.05},{-.25,.05},{-.55,.85},{.55,-.9},{.55,-.35},{.25,.05},{.85,.05},{.55,.85}};
        case KNIGHT->new double[][]{{-.75,-.65},{0,-.85},{.75,-.65},{.65,.1},{.4,.55},{0,.9},{-.4,.55},{-.65,.1},{0,-.3},{0,.25}};
        default->new double[][]{{0,-.9},{-.5,-.5},{.5,-.5},{-.9,0},{.9,0},{-.5,.5},{.5,.5},{0,.9},{-.2,0},{.2,0}};
    };}
}
