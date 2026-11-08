package citycore.l2compat.mixin;

import dev.xkmc.l2serial.serialization.custom_handler.CodecHandler;
import dev.xkmc.l2serial.serialization.custom_handler.Handlers;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.RegistryManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * L2Serial3.1.1 caches a pre-bootstrap registry-sync decision in its static
 * initialization. Dedicated and client startup can reach it at different times.
 * Keep the same named/indexed codecs, but choose when a packet actually uses
 * them, after registry setup. No payload fields, registries or content are edited.
 */
@Mixin(value=Handlers.class,remap=false)
public abstract class L2HandlersMixin {
    @Inject(method="enableVanilla",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private static <T> void citycore$deferRegistryStreamChoice(Class<T> type,Registry<T> registry,CallbackInfo ci){
        Handlers.registerReg(type,registry.key());
        StreamCodec<RegistryFriendlyByteBuf,T> named=ByteBufCodecs.fromCodecWithRegistries(registry.byNameCodec());
        StreamCodec<RegistryFriendlyByteBuf,T> indexed=ByteBufCodecs.registry(registry.key());
        StreamCodec<RegistryFriendlyByteBuf,T> stream=new StreamCodec<>(){
            private StreamCodec<RegistryFriendlyByteBuf,T> current(){
                return RegistryManager.isNonSyncedBuiltInRegistry(registry)?named:indexed;
            }
            @Override public T decode(RegistryFriendlyByteBuf buf){return current().decode(buf);}
            @Override public void encode(RegistryFriendlyByteBuf buf,T value){current().encode(buf,value);}
        };
        new CodecHandler<>(type,registry.byNameCodec(),stream);
        ci.cancel();
    }
}
