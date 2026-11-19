package com.muxigame.sevenfold;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class MasteryNetwork {
    public record Action(String action,String mastery) implements CustomPacketPayload {
        public static final Type<Action> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SevenfoldMastery.ID,"action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=StreamCodec.of((b,p)->{b.writeUtf(p.action,8);b.writeUtf(p.mastery,16);},b->new Action(b.readUtf(8),b.readUtf(16)));
        public Type<Action> type(){return TYPE;}
    }
    public record Snapshot(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SevenfoldMastery.ID,"state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((b,p)->b.writeNbt(p.data),b->new Snapshot(b.readNbt()));
        public Type<Snapshot> type(){return TYPE;}
    }
    public record StarAction(String action,String node,int star) implements CustomPacketPayload {
        public static final Type<StarAction> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SevenfoldMastery.ID,"star_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,StarAction> CODEC=StreamCodec.of((b,p)->{b.writeUtf(p.action,8);b.writeUtf(p.node,16);b.writeByte(p.star);},b->new StarAction(b.readUtf(8),b.readUtf(16),b.readByte()));
        public Type<StarAction> type(){return TYPE;}
    }
    public static java.util.function.Consumer<CompoundTag> receive=t->{};
    public static void register(RegisterPayloadHandlersEvent e){
        var r=e.registrar("8");
        r.playToServer(Action.TYPE,Action.CODEC,(p,c)->{if(c.player() instanceof ServerPlayer s)SevenfoldMastery.action(s,p);});
        r.playToServer(StarAction.TYPE,StarAction.CODEC,(p,c)->{if(c.player() instanceof ServerPlayer s)SevenfoldMastery.starAction(s,p);});
        r.playToClient(Snapshot.TYPE,Snapshot.CODEC,(p,c)->{if(p.data!=null)receive.accept(p.data);});
    }
}
