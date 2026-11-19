package com.muxigame.sevenfold;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@Mod(SevenfoldMastery.ID)
public final class SevenfoldMastery {
    public static final String ID="sevenfold_mastery";
    private static final Map<UUID,StarHold> STAR_HOLDS=new HashMap<>();
    public SevenfoldMastery(IEventBus bus){
        bus.addListener(MasteryNetwork::register);
        CombatResources.register();RpgNumbers.register();RpgIntegration.register();
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e)->{if(e.getEntity() instanceof ServerPlayer p){var prior=p.getPersistentData().getCompound(StarTreeState.KEY);if(prior.contains("lit")&&prior.getInt("schema")<3&&!p.getPersistentData().contains("SevenfoldStarTreeBeforeTenStars"))p.getPersistentData().put("SevenfoldStarTreeBeforeTenStars",prior.copy());CombatPools.of(p).store(p);StarTreeState.of(p).store(p);StarAttributes.apply(p);sync(p);}});
        NeoForge.EVENT_BUS.addListener((PlayerEvent.Clone e)->{if(e.getOriginal() instanceof ServerPlayer p)CombatResources.checkpoint(p);MasteryState.copyLegacy(e.getOriginal(),e.getEntity());StarTreeState.of(e.getOriginal()).store(e.getEntity());CombatPools.of(e.getOriginal()).store(e.getEntity());e.getEntity().getPersistentData().put("SevenfoldHandRecovery",e.getOriginal().getPersistentData().getList("SevenfoldHandRecovery",10).copy());e.getEntity().getPersistentData().put(CombatResources.CLOCK_KEY,e.getOriginal().getPersistentData().getCompound(CombatResources.CLOCK_KEY).copy());StarAttributes.apply(e.getEntity());STAR_HOLDS.remove(e.getEntity().getUUID());});
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e)->{if(e.getEntity() instanceof ServerPlayer p){StarAttributes.apply(p);sync(p);}});
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e)->{STAR_HOLDS.remove(e.getEntity().getUUID());if(e.getEntity() instanceof ServerPlayer p)sync(p);});
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e)->{STAR_HOLDS.remove(e.getEntity().getUUID());if(e.getEntity() instanceof ServerPlayer p)CombatResources.forget(p);});
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e)->{STAR_HOLDS.clear();CombatResources.clear();});
    }
    public static void sync(ServerPlayer p){var t=new net.minecraft.nbt.CompoundTag();t.put("star_tree",StarTreeState.of(p).save());t.put("pools",CombatPools.of(p).save());t.putInt("food_cap",StarTreeState.of(p).active()?100:20);if(p.connection!=null)PacketDistributor.sendToPlayer(p,new MasteryNetwork.Snapshot(t));}
    public static void starAction(ServerPlayer p,MasteryNetwork.StarAction a){
        if(a.action().equals("read")){sync(p);return;}
        var hold=STAR_HOLDS.computeIfAbsent(p.getUUID(),u->new StarHold());
        if(a.action().equals("cancel")){hold.cancel();return;}
        if(!p.isAlive()||p.isSpectator()||p.containerMenu!=p.inventoryMenu){hold.cancel();return;}
        var node=StarNode.find(a.node());if(node.isEmpty())return;
        var state=StarTreeState.of(p);
        if(!state.canToggle(node.get(),a.star())){hold.cancel();sync(p);return;}
        long tick=p.server.getTickCount();
        if(a.action().equals("begin"))hold.begin(node.get(),a.star(),tick);
        else if(a.action().equals("finish")){
            if(hold.finish(node.get(),a.star(),tick)){
                applyStars(p,state.toggle(node.get(),a.star()));
            }
            sync(p);
        }
    }
    public static boolean applyStars(ServerPlayer p,StarTreeState next){
        var previous=StarTreeState.of(p);
        if(!previous.active()&&next.active()&&!CombatSlots.prepareActivation(p)){p.displayClientMessage(net.minecraft.network.chat.Component.translatable("tree.sevenfold_mastery.bag_full"),true);sync(p);return false;}
        next.store(p);
        if(next.profession()!=previous.profession())com.muxigame.zaluokeforging.combat.CombatOutcomes.invalidateProfessionBenefits(p);
        StarAttributes.apply(p);return true;
    }
    /** Retired protocol8 endpoint: old selection packets cannot change any player state. */
    public static void action(ServerPlayer p,MasteryNetwork.Action a){
        if(a.action().equals("read"))sync(p);
    }
}