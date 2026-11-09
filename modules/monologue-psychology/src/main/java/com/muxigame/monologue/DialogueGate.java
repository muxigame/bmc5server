package com.muxigame.monologue;

import java.util.*;

/** Event cooldowns and anti-repeat selection; one instance per player, cleared on logout. */
public final class DialogueGate {
    private final EnumMap<Situation,Long> next=new EnumMap<>(Situation.class);
    private final Map<String,String> last=new HashMap<>();
    public boolean allow(Situation s,long tick,int cooldown){
        if(tick<next.getOrDefault(s,Long.MIN_VALUE))return false;
        next.put(s,tick+cooldown);return true;
    }
    public String choose(String key,List<String> lines,int random){
        if(lines.isEmpty())return "";
        List<String> candidates=lines.stream().filter(s->!s.equals(last.get(key))).toList();
        if(candidates.isEmpty())candidates=lines;
        String text=candidates.get(Math.floorMod(random,candidates.size()));last.put(key,text);return text;
    }
}
