package com.muxigame.monologue;

/** Priority belongs to the displayed text, not the underlying event cooldown. */
public final class ActiveLine {
    public String text="";
    public long expires;
    public int priority;
    public boolean offer(String value,int rank,long now,long lifetime){
        if(value.isEmpty()||now<expires&&rank<priority)return false;
        text=value;priority=rank;expires=now+lifetime;return true;
    }
    public boolean active(long now){return !text.isEmpty()&&now<expires;}
}
