package com.muxigame.realneeds;

import java.util.*;
import com.mojang.brigadier.arguments.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@Mod(RealNeeds.ID)
public final class RealNeeds {
    public static final String ID="real_needs";
    private static final Map<UUID,Integer> REQUESTS=new HashMap<>();
    private static final Map<UUID,Integer> EDITS=new HashMap<>();
    private static final Map<UUID,Integer> HEALTH_REQUESTS=new HashMap<>();
    public RealNeeds(IEventBus bus){
        bus.addListener(NeedsNetwork::register);MedicalSupplies.register(bus);
        NeedsWorld.register();
        NeoForge.EVENT_BUS.addListener(RealNeeds::tick);
        NeoForge.EVENT_BUS.addListener(RealNeeds::consume);
        NeoForge.EVENT_BUS.addListener(RealNeeds::sleep);
        NeoForge.EVENT_BUS.addListener(RealNeeds::commands);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.Clone e)->NeedsState.of(e.getOriginal()).store(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e)->{if(e.getEntity() instanceof ServerPlayer p){NeedsState.of(p).store(p);sync(p,false);}});
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e)->{if(e.getEntity() instanceof ServerPlayer p)sync(p,false);});
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e)->{if(e.getEntity() instanceof ServerPlayer p)sync(p,false);});
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e)->{REQUESTS.remove(e.getEntity().getUUID());EDITS.remove(e.getEntity().getUUID());HEALTH_REQUESTS.remove(e.getEntity().getUUID());});
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e)->{REQUESTS.clear();EDITS.clear();HEALTH_REQUESTS.clear();NeedsWorld.stop();NeedsIllness.stop();});
    }
    static boolean participates(ServerPlayer p){return p.gameMode.getGameModeForPlayer()!=net.minecraft.world.level.GameType.CREATIVE&&!p.isSpectator();}
    static boolean active(ServerPlayer p){return p.isAlive()&&participates(p);}
    static void tick(ServerTickEvent.Post e){
        NeedsWorld.finishBreaks();
        if(e.getServer().getTickCount()%20!=0)return;
        NeedsRules rules=NeedsRules.get(e.getServer());
        for(ServerPlayer p:e.getServer().getPlayerList().getPlayers()){
            if(p.isAlive()&&rules.enabled){
                NeedsState s=NeedsState.of(p);
                if(active(p)){s.second(rules,p.isSprinting(),p.isSleeping());NeedsWorld.second(p,s,rules);NeedsIllness.second(p,s,rules);}
                s.set(BodyStat.INTIMIDATION,NeedsPhysiology.intimidation(s,rules,p.getArmorValue(),p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)));s.store(p);
            }
            NeedsIllness.refresh(p,NeedsState.of(p),rules);sync(p,false);
        }
    }
    static void consume(LivingEntityUseItemEvent.Finish e){
        if(e.getEntity() instanceof ServerPlayer p&&active(p)){
            NeedsRules r=NeedsRules.get(p.server);if(!r.enabled)return;
            NeedsState s=NeedsState.of(p);NeedsFood.consume(s,e.getItem(),p,r.foodMultiplier*NeedsIllness.foodScale(s,r));NeedsWorld.consumed(p,s,e.getItem(),r);s.store(p);sync(p,false);
        }
    }
    static void sleep(SleepFinishedTimeEvent e){
        if(e.getLevel() instanceof ServerLevel level&&NeedsRules.get(level.getServer()).enabled)
            for(ServerPlayer p:level.players())if(active(p)&&p.isSleepingLongEnough()){
                NeedsState s=NeedsState.of(p);s.set(Need.SLEEP,NeedsRules.get(level.getServer()).value(PhysiologyRule.NIGHT_SLEEP));s.store(p);sync(p,false);
            }
    }
    public static void request(ServerPlayer p,boolean open){
        int now=p.server.getTickCount();Integer last=REQUESTS.get(p.getUUID());
        if(last!=null&&now-last<10)return;
        REQUESTS.put(p.getUUID(),now);sync(p,open);
    }
    public static void sync(ServerPlayer p,boolean open){
        sync(p,open,0,"");
    }
    private static void sync(ServerPlayer p,boolean open,long ack,String result){
        // Fake players and unnegotiated connections have no client payload channel.
        if(p.connection==null||!p.connection.hasChannel(NeedsNetwork.Snapshot.TYPE))return;
        var t=snapshot(p,open);
        if(ack!=0){t.putLong("ack",ack);t.putString("result",result);}
        PacketDistributor.sendToPlayer(p,new NeedsNetwork.Snapshot(t));
    }
    private static net.minecraft.nbt.CompoundTag snapshot(ServerPlayer p,boolean open){
        var t=NeedsState.of(p).save();t.put("rules",NeedsRules.get(p.server).settings());t.putBoolean("open",open);
        t.putLong("revision",NeedsRules.get(p.server).revision());t.putBoolean("editable",p.hasPermissions(2));t.putBoolean("can_undo",NeedsRules.get(p.server).canUndo());
        t.putBoolean("paused",!active(p));return t;
    }
    public static String applyHealthAction(ServerPlayer p,NeedsNetwork.HealthAction packet){
        var rules=NeedsRules.get(p.server);var s=NeedsState.of(p);String result;
        switch(packet.action()){
            case "set"->{if(!p.hasPermissions(2))throw new IllegalArgumentException("管理员才能设定伤病程度");var type=AilmentType.byId(packet.disease());var site=AilmentState.Site.byId(packet.site());s.ailments().set(type,site,packet.severity());result="已设定本人"+site.label+"的"+type.label;}
            case "clearall"->{if(!p.hasPermissions(2))throw new IllegalArgumentException("管理员才能清除伤病");s.ailments().clear();s.clock("ill_heat",0);s.clock("ill_cold",0);result="已清除本人全部伤病；部位健康值保留";}
            case "care"->result=NeedsIllness.care(p,s,rules,AilmentType.byId(packet.disease()),AilmentState.Site.byId(packet.site()));
            default->throw new IllegalArgumentException("未知伤病操作");
        }
        s.store(p);NeedsIllness.refresh(p,s,rules);return result;
    }
    public static void healthAction(ServerPlayer p,NeedsNetwork.HealthAction packet){
        String message;
        try{int now=p.server.getTickCount();var last=HEALTH_REQUESTS.get(p.getUUID());if(last!=null&&now-last<10)throw new IllegalArgumentException("操作过快，请稍后再试");HEALTH_REQUESTS.put(p.getUUID(),now);message=applyHealthAction(p,packet);}
        catch(IllegalArgumentException ex){message="操作失败："+ex.getMessage();}
        if(p.connection==null||!p.connection.hasChannel(NeedsNetwork.Snapshot.TYPE))return;
        var data=snapshot(p,false);data.putLong("health_ack",packet.request());data.putString("health_result",message);PacketDistributor.sendToPlayer(p,new NeedsNetwork.Snapshot(data));
    }
    public static void edit(ServerPlayer p,NeedsNetwork.Edit packet){
        String result;
        try{
            if(!p.hasPermissions(2))throw new IllegalArgumentException("需要管理员权限才能修改存档参数");
            int now=p.server.getTickCount();var last=EDITS.get(p.getUUID());
            if(last!=null&&now-last<10)throw new IllegalArgumentException("操作过快，请稍后再试");
            EDITS.put(p.getUUID(),now);
            var rules=NeedsRules.get(p.server);
            if(packet.revision()!=rules.revision())throw new IllegalArgumentException("设置已经改变，请先重读服务器设置");
            switch(packet.action()){
                case "save"->rules.edit(packet.revision(),packet.changes());
                case "reset"->rules.reset();
                case "undo"->rules.undo();
                default->throw new IllegalArgumentException("未知设置操作");
            }
            p.server.overworld().getDataStorage().save();
            result="已保存到当前存档，立即生效";
            for(var other:p.server.getPlayerList().getPlayers())if(other!=p)sync(other,false);
        }catch(IllegalArgumentException ex){result="未保存："+ex.getMessage();}
        sync(p,false,packet.request(),result);
    }
    @FunctionalInterface interface Action {void run(CommandSourceStack s) throws Exception;}
    static int run(CommandSourceStack s,Action a){try{a.run(s);return 1;}catch(Exception e){s.sendFailure(Component.literal(e.getMessage()==null?"操作失败":e.getMessage()));return 0;}}
    static void saved(CommandSourceStack s){NeedsRules.get(s.getServer()).setDirty();s.getServer().overworld().getDataStorage().save();s.sendSuccess(()->Component.literal("真实需求：设置已保存；/needs rules undo 可恢复上次设置。"),true);for(var p:s.getServer().getPlayerList().getPlayers())sync(p,false);}
    static void commands(RegisterCommandsEvent e){
        var root=Commands.literal("needs").executes(c->run(c.getSource(),s->sync(s.getPlayerOrException(),true)));
        root.then(Commands.literal("help").executes(c->{c.getSource().sendSuccess(()->Component.literal("背包左侧显示20项数值（全部0–100）。管理员：/needs stat set <玩家> <字段> <值>；clear 恢复该项初始值。身材physique（兼容旧weight），肌肉muscle，体温temperature，威慑intimidation由身材/肌肉/护甲/武器自动计算。/needs checks 查看本人最近疾病判定。原营养 /needs set；规则 /needs rules set <参数> <值>（Tab补全）；reset/undo 支持全部新旧规则。"),false);return 1;}));
        root.then(Commands.literal("checks").executes(c->run(c.getSource(),s->{var data=NeedsState.of(s.getPlayerOrException()).disease();s.sendSuccess(()->Component.literal(data.toString()),false);})));
        root.then(Commands.literal("set").requires(s->s.hasPermission(2)).then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("need",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(Arrays.stream(Need.values()).map(n->n.id),b)).then(Commands.argument("value",DoubleArgumentType.doubleArg(0,100)).executes(c->run(c.getSource(),s->{ServerPlayer p=EntityArgument.getPlayer(c,"player");NeedsState state=NeedsState.of(p);state.set(Need.byId(StringArgumentType.getString(c,"need")),DoubleArgumentType.getDouble(c,"value"));state.store(p);sync(p,false);s.sendSuccess(()->Component.literal("真实需求：玩家数值已更新。"),true);}))))));
        var stats=Commands.literal("stat").requires(s->s.hasPermission(2));
        for(String operation:List.of("set","clear")){
            var argument=Commands.argument("stat",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(Arrays.stream(BodyStat.values()).map(n->n.id),b));
            if(operation.equals("set"))argument.then(Commands.argument("value",DoubleArgumentType.doubleArg(0,100)).executes(c->run(c.getSource(),s->{
                var p=EntityArgument.getPlayer(c,"player");var state=NeedsState.of(p);var stat=BodyStat.byId(StringArgumentType.getString(c,"stat"));state.set(stat,DoubleArgumentType.getDouble(c,"value"));state.store(p);sync(p,false);s.sendSuccess(()->Component.literal(stat.label+"已设为 "+state.get(stat)),true);
            })));
            else argument.executes(c->run(c.getSource(),s->{var p=EntityArgument.getPlayer(c,"player");var state=NeedsState.of(p);var stat=BodyStat.byId(StringArgumentType.getString(c,"stat"));state.set(stat,NeedsRules.get(p.server).initial(stat));state.store(p);sync(p,false);s.sendSuccess(()->Component.literal(stat.label+"已恢复初始值"),true);}));
            stats.then(Commands.literal(operation).then(Commands.argument("player",EntityArgument.player()).then(argument)));
        }
        root.then(stats);
        var rules=Commands.literal("rules").requires(s->s.hasPermission(2)).executes(c->{c.getSource().sendSuccess(()->Component.literal(NeedsRules.get(c.getSource().getServer()).settings().toString()),false);return 1;});
        rules.then(Commands.literal("set").then(Commands.argument("key",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(List.of("protein","energy","vitamins","water","sleep","enabled","sprint","rest","food").stream(),Arrays.stream(PhysiologyRule.values()).map(v->v.id)),b)).then(Commands.argument("value",DoubleArgumentType.doubleArg(0,86400)).executes(c->run(c.getSource(),s->{NeedsRules.get(s.getServer()).change(StringArgumentType.getString(c,"key"),DoubleArgumentType.getDouble(c,"value"));saved(s);})))));
        rules.then(Commands.literal("reset").executes(c->run(c.getSource(),s->{NeedsRules.get(s.getServer()).reset();saved(s);})));
        rules.then(Commands.literal("undo").executes(c->run(c.getSource(),s->{NeedsRules.get(s.getServer()).undo();saved(s);})));
        root.then(rules);e.getDispatcher().register(root);
    }
}
