package com.muxigame.realneeds;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class NeedsNetwork {
    public record Request(boolean open) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(RealNeeds.ID,"request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p)->b.writeBoolean(p.open),b->new Request(b.readBoolean()));
        public Type<Request> type(){return TYPE;}
    }
    public record Snapshot(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(RealNeeds.ID,"snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((b,p)->b.writeNbt(p.data),b->new Snapshot(b.readNbt()));
        public Type<Snapshot> type(){return TYPE;}
    }
    public record QuietHealth(int entityId,float health) implements CustomPacketPayload {
        public static final Type<QuietHealth> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(RealNeeds.ID,"quiet_health"));
        public static final StreamCodec<RegistryFriendlyByteBuf,QuietHealth> CODEC=StreamCodec.of((b,p)->{b.writeVarInt(p.entityId);b.writeFloat(p.health);},b->new QuietHealth(b.readVarInt(),b.readFloat()));
        public Type<QuietHealth> type(){return TYPE;}
    }
    public record Edit(long request,long revision,String action,CompoundTag changes) implements CustomPacketPayload {
        public static final Type<Edit> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(RealNeeds.ID,"edit"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Edit> CODEC=StreamCodec.of((b,p)->{b.writeLong(p.request);b.writeLong(p.revision);b.writeUtf(p.action,8);b.writeNbt(p.changes);},b->new Edit(b.readLong(),b.readLong(),b.readUtf(8),b.readNbt()));
        public Type<Edit> type(){return TYPE;}
    }
    public record HealthAction(long request,String action,String disease,String site,double severity) implements CustomPacketPayload {
        public static final Type<HealthAction> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(RealNeeds.ID,"health_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,HealthAction> CODEC=StreamCodec.of((b,p)->{b.writeLong(p.request);b.writeUtf(p.action,8);b.writeUtf(p.disease,24);b.writeUtf(p.site,16);b.writeDouble(p.severity);},b->new HealthAction(b.readLong(),b.readUtf(8),b.readUtf(24),b.readUtf(16),b.readDouble()));
        public Type<HealthAction> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent e){
        var r=e.registrar("4");
        r.playToServer(Request.TYPE,Request.CODEC,(p,c)->{if(c.player() instanceof ServerPlayer s)RealNeeds.request(s,p.open);});
        r.playToServer(Edit.TYPE,Edit.CODEC,(p,c)->{if(c.player() instanceof ServerPlayer s)RealNeeds.edit(s,p);});
        r.playToServer(HealthAction.TYPE,HealthAction.CODEC,(p,c)->{if(c.player() instanceof ServerPlayer s)RealNeeds.healthAction(s,p);});
        r.playToClient(Snapshot.TYPE,Snapshot.CODEC,(p,c)->ClientBridge.receive.accept(p.data));
        r.playToClient(QuietHealth.TYPE,QuietHealth.CODEC,(p,c)->ClientBridge.quietHealth.accept(p));
    }
    public static final class ClientBridge {public static java.util.function.Consumer<CompoundTag> receive=t->{};public static java.util.function.Consumer<QuietHealth> quietHealth=p->{};public static Runnable openHealth=()->{};}
}
