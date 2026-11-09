package com.muxigame.monologue;

import java.util.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.*;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@Mod(MonologueMod.ID)
@EventBusSubscriber(modid=MonologueMod.ID)
public final class MonologueMod {
    public static final String ID="inner_monologue";
    static final class Session {
        final DialogueGate gate=new DialogueGate();
        long lastRequest=Long.MIN_VALUE,lastPreview=Long.MIN_VALUE;
        int emitted;Situation lastSituation;
    }
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    static Session session(Player p){return SESSIONS.computeIfAbsent(p.getUUID(),id->new Session());}
    public MonologueMod(IEventBus bus){bus.addListener(DialogueNetwork::register);}
    public static boolean enemy(LivingEntity target,ServerPlayer killer){
        if(target==killer||target.isAlliedTo(killer)||target instanceof TamableAnimal t&&t.isOwnedBy(killer))return false;
        return target instanceof Enemy||target instanceof Player||target instanceof Mob mob&&mob.getTarget()==killer;
    }
    @SubscribeEvent public static void hurt(LivingDamageEvent.Post e){
        if(e.getEntity() instanceof ServerPlayer p&&e.getNewDamage()>0&&p.isAlive()&&e.getSource().getEntity() instanceof LivingEntity source&&source!=p)trigger(p,Situation.HURT,false);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void killed(LivingDeathEvent e){
        LivingEntity target=e.getEntity();
        var killer=e.getSource().getEntity() instanceof ServerPlayer p?p:target.getKillCredit();
        if(killer instanceof ServerPlayer p&&enemy(target,p))trigger(p,Situation.KILL,false);
    }
    @SubscribeEvent public static void ate(LivingEntityUseItemEvent.Finish e){
        if(e.getEntity() instanceof ServerPlayer p&&e.getItem().getFoodProperties(p)!=null)trigger(p,Situation.EAT,false);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)snapshot(p,false,"");}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){SESSIONS.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){SESSIONS.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e){SESSIONS.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){SESSIONS.clear();}
    static boolean trigger(ServerPlayer p,Situation situation,boolean preview){
        DialogueRules rules=DialogueRules.get(p.server);
        if(!rules.enabled||!p.isAlive()||p.isSpectator())return false;
        Session s=session(p);long tick=p.server.getTickCount();var entry=rules.entries.get(situation);
        if(preview){if(s.lastPreview!=Long.MIN_VALUE&&tick-s.lastPreview<20)return false;s.lastPreview=tick;}
        else if(!s.gate.allow(situation,tick,entry.cooldown))return false;
        String head=rules.headEnabled?s.gate.choose(situation.id+"/head",entry.head,p.getRandom().nextInt()):"";
        String thought=rules.thoughtEnabled?s.gate.choose(situation.id+"/thought",entry.thought,p.getRandom().nextInt()):"";
        if(head.isEmpty()&&thought.isEmpty())return false;
        s.emitted++;s.lastSituation=situation;
        var cue=new DialogueNetwork.Cue(p.getUUID(),p.level().dimension().location().toString(),head,thought,entry.duration,situation.priority,rules.thoughtOffset);
        for(ServerPlayer viewer:p.serverLevel().players())if((viewer==p||!preview&&viewer.distanceToSqr(p)<=32*32)&&viewer.connection!=null&&viewer.connection.hasChannel(DialogueNetwork.Cue.TYPE)){
            var visible=cue.forViewer(viewer.getUUID());
            if(!visible.head().isEmpty()||!visible.thought().isEmpty())PacketDistributor.sendToPlayer(viewer,visible);
        }
        return true;
    }
    static boolean canEdit(ServerPlayer p){return p.hasPermissions(2);}
    static void snapshot(ServerPlayer p,boolean open,String message){
        if(p.connection==null||!p.connection.hasChannel(DialogueNetwork.Snapshot.TYPE))return;
        var r=DialogueRules.get(p.server);var t=new net.minecraft.nbt.CompoundTag();t.put("config",r.config());t.putLong("revision",r.revision);t.putBoolean("admin",canEdit(p));t.putBoolean("open",open);t.putString("message",message);
        PacketDistributor.sendToPlayer(p,new DialogueNetwork.Snapshot(t));
    }
    public static void request(ServerPlayer p,DialogueNetwork.Request r){
        Session s=session(p);long tick=p.server.getTickCount();
        if(s.lastRequest!=Long.MIN_VALUE&&tick-s.lastRequest<4){snapshot(p,false,"操作稍快，请稍后重试");return;}s.lastRequest=tick;
        try{
            if(r.op().equals("view")){snapshot(p,true,"");return;}
            if(r.op().equals("preview")){trigger(p,Situation.parse(r.event()),true);snapshot(p,false,"预览仅发送给本人；关闭菜单可看心理文字，F5第三人称可看头顶气泡");return;}
            if(!canEdit(p))throw new IllegalArgumentException("修改服务器台词库需要管理员权限等级2");
            var rules=DialogueRules.get(p.server);rules.edit(r.revision(),r.op(),r.event(),r.channel(),r.index(),r.value());
            p.server.overworld().getDataStorage().save();
            for(ServerPlayer viewer:p.server.getPlayerList().getPlayers())snapshot(viewer,false,viewer==p?"已保存；可用「撤销修改」恢复上一版":"服务器台词库已更新");
        }catch(IllegalArgumentException ex){snapshot(p,false,ex instanceof NumberFormatException?"请输入整数刻数（20刻=1秒）":ex.getMessage());}
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent e){
        var root=Commands.literal("monologue").executes(c->{snapshot(c.getSource().getPlayerOrException(),true,"");return 1;});
        root.then(Commands.literal("preview").then(Commands.argument("event",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(List.of("hurt","kill","eat"),b)).executes(c->{
            try{var p=c.getSource().getPlayerOrException();return trigger(p,Situation.parse(StringArgumentType.getString(c,"event")),true)?1:0;}
            catch(IllegalArgumentException ex){c.getSource().sendFailure(Component.literal(ex.getMessage()));return 0;}
        })));
        root.then(Commands.literal("help").executes(c->{c.getSource().sendSuccess(()->Component.literal("/monologue 打开台词库；管理员可编辑。/monologue preview hurt|kill|eat 仅本人预览。头顶独白附近玩家可见，心理文字只在本人准星下方显示；正常事件自动触发，不发送聊天消息。"),false);return 1;}));
        e.getDispatcher().register(root);
    }
}
