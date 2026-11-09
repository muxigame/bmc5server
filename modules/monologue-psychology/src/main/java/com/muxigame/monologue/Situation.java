package com.muxigame.monologue;

public enum Situation {
    HURT("hurt","受到攻击",3,60), KILL("kill","击杀敌人",2,80), EAT("eat","进食",1,100);
    public final String id,label;
    public final int priority,cooldown;
    Situation(String id,String label,int priority,int cooldown){this.id=id;this.label=label;this.priority=priority;this.cooldown=cooldown;}
    public static Situation parse(String id){for(var s:values())if(s.id.equals(id))return s;throw new IllegalArgumentException("情境只能是 hurt、kill、eat");}
}
