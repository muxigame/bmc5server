package com.muxigame.city;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.network.PacketDistributor;

/** Optional NPC integration: the city resident set remains the only persisted ownership record. */
public final class CityNpcBridge {
    static CityData.City home(CityData d,UUID npc){return d.cities().stream().filter(c->c.residents.contains(npc)).findFirst().orElse(null);}
    public static void assign(CityData d,ServerPlayer p,UUID npc,String target,long revision,String previous,long previousRevision){
        CityData.City old=home(d,npc),next=target.isEmpty()?null:d.require(UUID.fromString(target));
        if(!Objects.equals(old==null?"":old.id.toString(),previous))throw new IllegalArgumentException("所属城市已变化，请刷新后重试。");
        if(old!=null)CityService.checkEditor(p,old,previousRevision);
        if(next!=null){CityService.checkEditor(p,next,revision);if(next!=old&&next.residents.size()>=4096)throw new IllegalArgumentException("目标城市居民登记已满。");}
        if(next==old)return;
        if(old!=null){old.residents.remove(npc);d.changed(old);}
        if(next!=null){next.residents.add(npc);d.changed(next);}
    }
    static Entity editing(ServerPlayer p,UUID id){
        if(!p.hasPermissions(2)||!BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()).toString().equals("customnpcs:npcwand"))throw new IllegalArgumentException("所属城市设置需要管理员手持NPC魔杖。");
        try{
            Object value=Class.forName("noppes.npcs.NoppesUtilServer").getMethod("getEditingNpc",net.minecraft.world.entity.player.Player.class).invoke(null,p);
            if(value instanceof Entity e&&e.getUUID().equals(id)&&e.level()==p.level()&&e.isAlive())return e;
        }catch(ReflectiveOperationException e){throw new IllegalArgumentException("当前CustomNPCs版本不支持城市联动。");}
        throw new IllegalArgumentException("NPC编辑目标已变化，请重新用NPC魔杖打开。");
    }
    public static void request(ServerPlayer p,CityNetwork.Request r){
        String[] parts=r.value().split("[|]",-1);UUID id=UUID.fromString(parts[0]);Entity npc=editing(p,id);CityData d=CityData.get(p.server);String message="";
        if(r.op().equals("npccityview")&&parts.length==1&&home(d,id)!=null){CityLivingEditor.request(p,new CityNetwork.Request("npclivingbasicview",id.toString(),0,0,0,0,0,0,0,"",false));return;}
        try{if(r.op().equals("npccityset")){if(parts.length!=3)throw new IllegalArgumentException("城市确认数据不完整。");assign(d,p,id,r.city(),r.revision(),parts[1],Long.parseLong(parts[2]));message="所属城市已保存。";}}
        catch(IllegalArgumentException ex){message=ex.getMessage();}
        CompoundTag t=new CompoundTag();t.putBoolean("NpcCityEditor",true);t.putString("NpcId",id.toString());t.putString("NpcName",npc.getName().getString());t.putString("Message",message);
        CityData.City current=home(d,id);t.putString("HomeId",current==null?"":current.id.toString());t.putString("HomeName",current==null?"无所属城市":current.name);t.putLong("HomeRevision",current==null?0:current.revision);
        ListTag list=new ListTag();for(var c:d.cities())if(c.dimension.equals(p.level().dimension().location().toString())){CompoundTag v=new CompoundTag();v.putString("Id",c.id.toString());v.putString("Name",c.name);v.putLong("Revision",c.revision);list.add(v);}t.put("Cities",list);PacketDistributor.sendToPlayer(p,new CityNetwork.Snapshot(t));
    }
}
