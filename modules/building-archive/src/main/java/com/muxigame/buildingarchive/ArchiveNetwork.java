package com.muxigame.buildingarchive;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
public final class ArchiveNetwork {
 public record Request(String op,String value,long revision,int page) implements CustomPacketPayload {
  public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(BuildingArchive.ID,"request"));
  public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p)->{b.writeUtf(p.op,16);b.writeUtf(p.value,129);b.writeLong(p.revision);b.writeInt(p.page);},b->new Request(b.readUtf(16),b.readUtf(129),b.readLong(),b.readInt()));
  public Type<Request> type(){return TYPE;}
 }
 public record Snapshot(CompoundTag data) implements CustomPacketPayload {
  public static final Type<Snapshot> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(BuildingArchive.ID,"snapshot"));
  public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((b,p)->b.writeNbt(p.data),b->new Snapshot(b.readNbt()));
  public Type<Snapshot> type(){return TYPE;}
 }
 public record Transfer(String op,String name,java.util.UUID id,int offset,int total,byte[] bytes) implements CustomPacketPayload {
  public static final Type<Transfer> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(BuildingArchive.ID,"transfer"));
  public static final StreamCodec<RegistryFriendlyByteBuf,Transfer> CODEC=StreamCodec.of((b,p)->{b.writeUtf(p.op,16);b.writeUtf(p.name,128);b.writeUUID(p.id);b.writeInt(p.offset);b.writeInt(p.total);b.writeByteArray(p.bytes);},b->new Transfer(b.readUtf(16),b.readUtf(128),b.readUUID(),b.readInt(),b.readInt(),b.readByteArray(ArchiveTransfer.CHUNK)));
  public Type<Transfer> type(){return TYPE;}
 }
 public static void register(RegisterPayloadHandlersEvent e){var r=e.registrar("4");r.playBidirectional(Transfer.TYPE,Transfer.CODEC,(p,c)->{if(c.player() instanceof ServerPlayer s)ArchiveTransfer.handle(s,p);else ClientBridge.transfer.accept(p);});r.playToServer(Request.TYPE,Request.CODEC,(p,c)->{if(c.player() instanceof ServerPlayer s)BuildingArchive.action(s,p);});r.playToClient(Snapshot.TYPE,Snapshot.CODEC,(p,c)->ClientBridge.receive.accept(p.data));}
 public static final class ClientBridge {public static Runnable help=()->{};public static Runnable files=()->{};public static java.util.function.Consumer<Transfer> transfer=p->{};public static java.util.function.BooleanSupplier shift=()->false;public static java.util.function.Consumer<CompoundTag> receive=t->{};}
}


