package com.muxigame.buildingarchive;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
@EventBusSubscriber(modid=BuildingArchive.ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ArchiveClient {
 @SubscribeEvent public static void reload(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent e){e.registerReloadListener(TerrainClientCatalog.RELOAD);}
 @SubscribeEvent public static void setup(FMLClientSetupEvent e){e.enqueueWork(()->{ArchiveNetwork.ClientBridge.help=()->Minecraft.getInstance().setScreen(new ArchiveHelpScreen(null));ArchiveNetwork.ClientBridge.transfer=p->{var mc=Minecraft.getInstance();if(mc.screen instanceof ArchiveTransferScreen screen)screen.receive(p);};ArchiveNetwork.ClientBridge.shift=net.minecraft.client.gui.screens.Screen::hasShiftDown;ArchiveNetwork.ClientBridge.receive=data->{var mc=Minecraft.getInstance();if(data.getBoolean("Transfer")){if(mc.screen instanceof ArchiveTransferScreen screen)screen.update(data);else mc.setScreen(new ArchiveTransferScreen(data));return;}if(data.getBoolean("Terrain")){if(mc.screen instanceof TerrainScreen terrain)terrain.update(data);else mc.setScreen(new TerrainScreen(data));return;}ArchiveProjection.update(data.getCompound("Projection"));if(data.getBoolean("ProjectionOnly"))return;if(data.getBoolean("Preview")){if(mc.screen instanceof ArchivePlacementScreen ps)ps.update(data);else mc.setScreen(new ArchivePlacementScreen(data));return;}if(mc.screen instanceof ArchiveScreen s)s.update(data);else mc.setScreen(new ArchiveScreen(data));};});}
}

