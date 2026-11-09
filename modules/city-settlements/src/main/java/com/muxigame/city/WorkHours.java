package com.muxigame.city;

import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.network.PacketDistributor;

/** Runtime tick time earns output; changing the sun or sleeping never earns skipped work. */
public final class WorkHours {
    public static final class Config {
        public String profession="无职业";
        public int start=6,end=18,nightStart=18,nightEnd=6,willingness=30;
        public long revision,night=Long.MIN_VALUE,lastWorkedNight=Long.MIN_VALUE;
        public boolean accepted;
    }
    static int willingness(String role){return switch(role){case "农夫","农民","养殖户"->25;case "工匠"->40;case "守卫"->45;default->30;};}
    static String role(MinecraftServer s,UUID id){
        var rural=RuralSettlement.get(s).state.people.get(id);
        if(rural!=null&&rural.role!=RuralRules.Role.NONE)return switch(rural.role){case FARMER->"农夫";case COOK->"农妇";case MERCHANT->"头人";case HUNTER->"猎户";case ARTISAN->"工匠";case VAGRANT->"游民";case PROCUREMENT->"采购商";case BAKER->"烘焙师";case SUPPLIER->"工坊工人";default->"无职业";};
        if(CityEconomy.get(s).state.living.values().stream().anyMatch(p->p.npc.equals(id)&&p.guard))return "守卫";
        var worker=CityMaintenance.get(s).state.workers.get(id);return worker==null?"无职业":worker.role;
    }
    static Config config(MinecraftServer s,UUID id){
        var d=RuralSettlement.get(s);var c=d.state.hours.computeIfAbsent(id,k->new Config());String role=role(s,id);
        if(!role.equals(c.profession)){defaults(c,role);d.setDirty();}return c;
    }
    static void defaults(Config c,String role){c.profession=role;c.start=6;c.end=18;c.nightStart=18;c.nightEnd=6;c.willingness=willingness(role);c.revision++;}
    static boolean inWindow(double hour,int start,int end){return start<end?hour>=start&&hour<end:hour>=start||hour<end;}
    static boolean allowed(Config c,long dayTime,double stability,int roll){
        long time=Math.floorMod(dayTime,24000);double hour=(time/1000.0+6)%24;
        if(time<12000)return inWindow(hour,c.start,c.end);
        if(stability>50||!inWindow(hour,c.nightStart,c.nightEnd))return false;
        long night=Math.floorDiv(dayTime-12000,24000);
        if(c.night!=night){c.night=night;c.accepted=c.lastWorkedNight!=night-1&&roll<c.willingness;}
        if(c.accepted){c.lastWorkedNight=night;return true;}return false;
    }
    static boolean active(MinecraftServer s,CityData.City city,UUID npc){
        var c=config(s,npc);var level=CityEconomy.level(s,city);
        // Stable per-NPC/night roll: reopening settings or reloading cannot reroll overtime.
        long day=RuralSettlement.dayTime(s,city);long night=Math.floorDiv(day-12000,24000);int roll=Math.floorMod(Objects.hash(npc,night),100);
        boolean yes=allowed(c,day,CityLiving.stability(CityEconomy.get(s),city),roll);RuralSettlement.get(s).setDirty();return yes;
    }
    static void validate(Config c){CityEconomy.require(c!=null&&c.start>=0&&c.start<24&&c.end>=0&&c.end<24&&c.start!=c.end&&c.nightStart>=0&&c.nightStart<24&&c.nightEnd>=0&&c.nightEnd<24&&c.nightStart!=c.nightEnd&&c.willingness>=0&&c.willingness<=100,"时间须0—23且起止不同；加班意愿0—100");}
    static void access(ServerPlayer p,UUID npc){
        RuralTools.editor(p);
        if(npc.equals(RuralTools.selected.get(p.getUUID())))return;
        CityNpcBridge.editing(p,npc);
    }
    static void request(ServerPlayer p,CityNetwork.Request r){try{
        UUID id=UUID.fromString(r.city());access(p,id);var home=CityNpcBridge.home(CityData.get(p.server),id);CityEconomy.require(home!=null,"先设置NPC所属村落/城市");
        var c=config(p.server,id);String message="";
        if(r.op().equals("worksave")){CityEconomy.require(r.revision()==c.revision,"设置已变化，请重新打开");var input=CityEconomy.JSON.fromJson(r.value(),Config.class);validate(input);c.start=input.start;c.end=input.end;c.nightStart=input.nightStart;c.nightEnd=input.nightEnd;c.willingness=input.willingness;c.revision++;message="工作时间已保存";}
        if(r.op().equals("workreset")){defaults(c,role(p.server,id));message="已恢复职业默认值；夜班休息记录保留";}
        if(r.op().equals("workradius")){var person=RuralSettlement.get(p.server).state.people.get(id);CityEconomy.require(person!=null&&person.role==RuralRules.Role.HUNTER&&r.x()>=64&&r.x()<=256,"猎户半径须64—256格");person.huntRadius=r.x();person.huntingSpot=null;message="巡逻范围已保存，重新分配林地";}
        var t=new CompoundTag();t.putBoolean("WorkHours",true);t.putString("Npc",id.toString());t.putString("Config",CityEconomy.JSON.toJson(c));t.putString("Message",message);t.putLong("Revision",c.revision);
        var person=RuralSettlement.get(p.server).state.people.get(id);t.putInt("Radius",person==null?128:person.huntRadius);t.putBoolean("Hunter",person!=null&&person.role==RuralRules.Role.HUNTER);t.putString("Status",person==null?"按职业作息执行；本页预览不消耗夜班资格":person.workStatus);
        PacketDistributor.sendToPlayer(p,new CityNetwork.Snapshot(t));RuralSettlement.get(p.server).setDirty();
    }catch(Exception e){p.sendSystemMessage(net.minecraft.network.chat.Component.literal("[作息] "+e.getMessage()));}}
}

