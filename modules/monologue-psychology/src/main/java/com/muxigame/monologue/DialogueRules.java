package com.muxigame.monologue;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Canonical per-world dialogue library. Empty lists intentionally silence that channel. */
public final class DialogueRules extends SavedData {
    public static final int MAX_LINES=12, MAX_TEXT=96;
    public static final class Entry {
        public final List<String> head=new ArrayList<>(),thought=new ArrayList<>();
        public int duration=80,cooldown;
        Entry(int cooldown){this.cooldown=cooldown;}
        public List<String> lines(String channel){return switch(channel){case "head"->head;case "thought"->thought;default->throw new IllegalArgumentException("频道只能是 head 或 thought");};}
    }
    public final EnumMap<Situation,Entry> entries=new EnumMap<>(Situation.class);
    public boolean enabled=false,headEnabled=true,thoughtEnabled=true;
    public int thoughtOffset=24;
    public long revision;
    private CompoundTag previous;

    public DialogueRules(){
        for(var s:Situation.values())entries.put(s,new Entry(s.cooldown));
        defaults(Situation.HURT,List.of("嘶……！","别想轻易放倒我。","还撑得住。"),List.of("稳住，别再露出破绽。","这一击不轻，得拉开一点距离。","注意对方的动作，别急着反击。"));
        defaults(Situation.KILL,List.of("结束了。","暂时安全了。","呼……解决了。"),List.of("先确认四周，还有没有别的敌人。","缓口气，接下来得更小心。","手还在发紧……先把呼吸稳下来。"));
        defaults(Situation.EAT,List.of("先吃点东西。","嗯，补充点体力。","慢慢吃。"),List.of("肚子里有了东西，心里也踏实些。","趁现在补充体力，等会儿再赶路。","这一口下去，总算舒服了些。"));
    }
    private void defaults(Situation s,List<String> head,List<String> thought){entries.get(s).head.addAll(head);entries.get(s).thought.addAll(thought);}
    public static DialogueRules get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(DialogueRules::new,DialogueRules::load),"inner_monologue");}
    public static String validateText(String text){
        String value=text.strip();
        if(value.isEmpty()||value.length()>MAX_TEXT||value.chars().anyMatch(c->Character.isISOControl(c)||c=='§'))throw new IllegalArgumentException("台词须为1–96个字符，不含换行或格式控制符");
        return value;
    }
    private static int range(int value,int min,int max){if(value<min||value>max)throw new IllegalArgumentException("数值范围："+min+"–"+max);return value;}
    private static ListTag list(List<String> lines){ListTag t=new ListTag();for(String s:lines)t.add(StringTag.valueOf(s));return t;}
    public CompoundTag config(){
        CompoundTag t=new CompoundTag();t.putBoolean("enabled",enabled);t.putBoolean("headEnabled",headEnabled);t.putBoolean("thoughtEnabled",thoughtEnabled);t.putInt("thoughtOffset",thoughtOffset);
        for(var s:Situation.values()){Entry e=entries.get(s);CompoundTag v=new CompoundTag();v.put("head",list(e.head));v.put("thought",list(e.thought));v.putInt("duration",e.duration);v.putInt("cooldown",e.cooldown);t.put(s.id,v);}return t;
    }
    private void readConfig(CompoundTag t){
        enabled=t.getBoolean("enabled");headEnabled=!t.contains("headEnabled")||t.getBoolean("headEnabled");thoughtEnabled=!t.contains("thoughtEnabled")||t.getBoolean("thoughtEnabled");thoughtOffset=t.contains("thoughtOffset",99)?range(t.getInt("thoughtOffset"),12,120):24;
        for(var s:Situation.values())if(t.contains(s.id,10)){
            CompoundTag v=t.getCompound(s.id);Entry e=entries.get(s);
            e.duration=v.contains("duration",99)?range(v.getInt("duration"),20,200):80;
            e.cooldown=v.contains("cooldown",99)?range(v.getInt("cooldown"),20,1200):s.cooldown;
            for(String channel:List.of("head","thought"))if(v.contains(channel,9)){
                ListTag values=v.getList(channel,Tag.TAG_STRING);if(values.size()>MAX_LINES)throw new IllegalArgumentException("每组最多12条台词");
                var lines=e.lines(channel);lines.clear();for(Tag value:values)lines.add(validateText(value.getAsString()));
            }
        }
    }
    public void edit(long expected,String op,String event,String channel,int index,String value){
        if(expected!=revision)throw new IllegalArgumentException("配置已被更新，已刷新页面，请重新编辑");
        DialogueRules draft=new DialogueRules();draft.readConfig(config());
        switch(op){
            case "reset"->draft=new DialogueRules();
            case "undo"->{if(previous==null)throw new IllegalArgumentException("没有上一版配置");draft.readConfig(previous);}
            case "enabled"->draft.enabled=!enabled;
            case "headEnabled"->draft.headEnabled=!headEnabled;
            case "thoughtEnabled"->draft.thoughtEnabled=!thoughtEnabled;
            case "offset"->draft.thoughtOffset=range(Integer.parseInt(value),12,120);
            case "duration","cooldown"->{Entry e=draft.entries.get(Situation.parse(event));int number=Integer.parseInt(value);if(op.equals("duration"))e.duration=range(number,20,200);else e.cooldown=range(number,20,1200);}
            case "add","replace","delete"->{
                var lines=draft.entries.get(Situation.parse(event)).lines(channel);
                if(op.equals("add")){if(lines.size()>=MAX_LINES)throw new IllegalArgumentException("每组最多12条台词");lines.add(validateText(value));}
                else {if(index<0||index>=lines.size())throw new IllegalArgumentException("台词不存在，请重新选择");if(op.equals("delete"))lines.remove(index);else lines.set(index,validateText(value));}
            }
            default->throw new IllegalArgumentException("未知操作");
        }
        previous=config();readConfig(draft.config());revision++;setDirty();
    }
    public static DialogueRules load(CompoundTag t,HolderLookup.Provider provider){DialogueRules r=new DialogueRules();r.readConfig(t.getCompound("config"));r.revision=t.getLong("revision");if(t.contains("previous",10))r.previous=t.getCompound("previous").copy();if(t.contains("schema",99)&&t.getInt("schema")<2){r.enabled=false;r.revision++;r.setDirty();}return r;}
    @Override public CompoundTag save(CompoundTag t,HolderLookup.Provider provider){t.putInt("schema",2);t.putLong("revision",revision);t.put("config",config());if(previous!=null)t.put("previous",previous);return t;}
}
