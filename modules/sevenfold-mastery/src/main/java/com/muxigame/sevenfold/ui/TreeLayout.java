package com.muxigame.sevenfold.ui;
import com.muxigame.sevenfold.StarNode;

/** Separate route sectors leave room for future Cunning and Reason specializations. */
public final class TreeLayout {
    private TreeLayout(){}
    public record Position(double x,double y){}
    public static Position position(StarNode n){return switch(n){
        case CRYING->new Position(0,480);
        case WARRIOR->new Position(-720,0);
        case ASSASSIN->new Position(0,0);
        case MAGE->new Position(720,0);
        case BERSERKER->new Position(-990,-740);
        case WARDEN->new Position(-810,-820);
        case KNIGHT->new Position(-630,-820);
        case BRAWLER->new Position(-450,-740);
        default->new Position(0,480);
    };}
    public static double scale(double width,double height){return Math.max(.23,Math.min(width/2560.0,height/1440.0));}
}
