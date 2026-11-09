package com.muxigame.monologue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class DialogueNetwork {
    public record Request(String op,String event,String channel,int index,String value,long revision) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(id("request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p)->{b.writeUtf(p.op,20);b.writeUtf(p.event,8);b.writeUtf(p.channel,8);b.writeInt(p.index);b.writeUtf(p.value,128);b.writeLong(p.revision);},b->new Request(b.readUtf(20),b.readUtf(8),b.readUtf(8),b.readInt(),b.readUtf(128),b.readLong()));
        public Type<Request> type(){return TYPE;}
    }
    public record Cue(UUID player,String dimension,String head,String thought,int ticks,int priority,int offset) implements CustomPacketPayload {
        public static final Type<Cue> TYPE=new Type<>(id("cue"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Cue> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.player);b.writeUtf(p.dimension,128);b.writeUtf(p.head,128);b.writeUtf(p.thought,128);b.writeInt(p.ticks);b.writeInt(p.priority);b.writeInt(p.offset);},b->new Cue(b.readUUID(),b.readUtf(128),b.readUtf(128),b.readUtf(128),b.readInt(),b.readInt(),b.readInt()));
        public Type<Cue> type(){return TYPE;}
        public Cue forViewer(UUID viewer){return player.equals(viewer)?this:new Cue(player,dimension,head,"",ticks,priority,offset);}
    }
    public record Snapshot(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(id("snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((b,p)->b.writeNbt(p.data),b->new Snapshot(b.readNbt()));
        public Type<Snapshot> type(){return TYPE;}
    }
    private static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath(MonologueMod.ID,name);}
    public static void register(RegisterPayloadHandlersEvent e){var r=e.registrar("1");
        r.playToServer(Request.TYPE,Request.CODEC,(p,c)->{if(c.player() instanceof ServerPlayer s)MonologueMod.request(s,p);});
        r.playToClient(Cue.TYPE,Cue.CODEC,(p,c)->ClientBridge.cue.accept(p));
        r.playToClient(Snapshot.TYPE,Snapshot.CODEC,(p,c)->ClientBridge.snapshot.accept(p.data));
    }
    public static final class ClientBridge {
        public static java.util.function.Consumer<Cue> cue=t->{};
        public static java.util.function.Consumer<CompoundTag> snapshot=t->{};
    }
}
