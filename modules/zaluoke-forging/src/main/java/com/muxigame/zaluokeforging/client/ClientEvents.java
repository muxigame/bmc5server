package com.muxigame.zaluokeforging.client;

import com.muxigame.zaluokeforging.ZaluokeForging;
import com.muxigame.zaluokeforging.ModContent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid=ZaluokeForging.MOD_ID, value=Dist.CLIENT)
public final class ClientEvents {
    @SubscribeEvent public static void particles(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModContent.FORGE_SPARK.get(), sprites -> (type,level,x,y,z,dx,dy,dz) -> new ForgeSparkParticle(level,x,y,z,dx,dy,dz,sprites));
    }
    private ClientEvents() { }
    @SubscribeEvent
    public static void screens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(ModContent.MENU.get(), WorkshopScreen::new);
    }
    @SubscribeEvent
    public static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModContent.MACHINE.get(), WorkshopRenderer::new);
    }
    @SubscribeEvent
    public static void blockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> 0xFF3F76E4, ModContent.BARREL.get());
    }

    @SubscribeEvent
    public static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> 0xFF3F76E4, ModContent.BARREL.get().asItem());
        event.register((stack,tint) -> com.muxigame.zaluokeforging.forging.Workpiece.color(stack,
                net.minecraft.client.Minecraft.getInstance().level==null?0:net.minecraft.client.Minecraft.getInstance().level.getGameTime()), ModContent.SWORD_BLANK.get(),ModContent.BRIGHT_BLANK.get(),ModContent.DAZZLING_BLANK.get(),ModContent.READY_BLANK.get(),ModContent.AXE_BLANK.get(),ModContent.BRIGHT_AXE.get(),ModContent.DAZZLING_AXE.get(),ModContent.READY_AXE.get());
    }
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (var item : new net.minecraft.world.item.Item[]{ModContent.SWORD_BLANK.get(),ModContent.BRIGHT_BLANK.get(),ModContent.DAZZLING_BLANK.get(),ModContent.READY_BLANK.get(),ModContent.AXE_BLANK.get(),ModContent.BRIGHT_AXE.get(),ModContent.DAZZLING_AXE.get(),ModContent.READY_AXE.get()}) {
                net.minecraft.client.renderer.item.ItemProperties.register(item,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ZaluokeForging.MOD_ID,"forging"),
                        (stack,level,entity,seed) -> Math.min(1F,com.muxigame.zaluokeforging.forging.Workpiece.get(stack,"Hits")/(float)com.muxigame.zaluokeforging.forging.Workpiece.requiredHits(stack)));
                net.minecraft.client.renderer.item.ItemProperties.register(item,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ZaluokeForging.MOD_ID,"clamped"),
                        (stack,level,entity,seed) -> com.muxigame.zaluokeforging.forging.Workpiece.clamped(stack)?1:0);
            }
        });
        ZaluokeForging.LOGGER.info("{} client foundation ready", ZaluokeForging.MOD_ID);
    }
}
