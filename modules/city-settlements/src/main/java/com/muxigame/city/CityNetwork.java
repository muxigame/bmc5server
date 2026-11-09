package com.muxigame.city;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class CityNetwork {
    // Fixed-size primitive coordinates and bounded strings keep client requests small.
    public record Request(String op,String city,long revision,int x,int y,int z,int x2,int z2,int state,String value,boolean surface) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(CityMod.ID,"request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p)->{
            b.writeUtf(p.op,16);b.writeUtf(p.city,36);b.writeLong(p.revision);b.writeInt(p.x);b.writeInt(p.y);b.writeInt(p.z);b.writeInt(p.x2);b.writeInt(p.z2);b.writeInt(p.state);b.writeUtf(p.value,16384);b.writeBoolean(p.surface);
        },b->new Request(b.readUtf(16),b.readUtf(36),b.readLong(),b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readUtf(16384),b.readBoolean()));
        @Override public Type<Request> type(){return TYPE;}
    }
    public record Snapshot(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(CityMod.ID,"snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((b,p)->b.writeNbt(p.data),b->new Snapshot(b.readNbt()));
        @Override public Type<Snapshot> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event){
        var r=event.registrar("3");
        r.playToServer(Request.TYPE,Request.CODEC,(p,ctx)->{if(ctx.player() instanceof ServerPlayer player)CityMod.request(player,p);});
        r.playToClient(Snapshot.TYPE,Snapshot.CODEC,(p,ctx)->ClientBridge.receive.accept(p.data));
    }
    // The dedicated server never resolves a net.minecraft.client class.
    public static final class ClientBridge {public static java.util.function.Consumer<CompoundTag> receive=t->{};}
}
