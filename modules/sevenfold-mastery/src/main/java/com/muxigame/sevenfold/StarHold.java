package com.muxigame.sevenfold;
public final class StarHold {
    private StarNode pending;private int index;private long began;
    public void begin(StarNode node,int star,long tick){if(pending==null||tick-began>200){pending=node;index=star;began=tick;}}
    public boolean finish(StarNode node,int star,long tick){if(pending==null||pending!=node||index!=star)return false;if(tick-began>200){cancel();return false;}if(tick-began<24)return false;cancel();return true;}
    public void cancel(){pending=null;}
}
