package com.muxigame.city;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid=CityMod.ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class CityClient {
    private static CityNpcSetupScreen setupReturn(net.minecraft.client.gui.screens.Screen screen){if(screen instanceof CityNpcSetupScreen s)return s;if(screen instanceof CityHouseScreen s)return s.setupParent();if(screen instanceof CityRegisterScreen s)return s.setupParent();if(screen instanceof CityWorksScreen s)return s.setupParent();if(screen instanceof RuralScreen s)return s.setupParent();return null;}
    @SubscribeEvent public static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e){e.registerBlockEntityRenderer(RuralBlocks.LINK.get(),NpcStorageRenderer::new);}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){
        event.enqueueWork(()->CityNetwork.ClientBridge.receive=data->{
            Minecraft mc=Minecraft.getInstance();if(data.getBoolean("TownSetup")){if(mc.screen instanceof CityTownSetupScreen screen)screen.update(data);else mc.setScreen(new CityTownSetupScreen(data,mc.screen));return;}if(data.getBoolean("NpcSetup")){if(mc.screen instanceof CityNpcSetupScreen screen)screen.update(data);else mc.setScreen(new CityNpcSetupScreen(data));return;}if(data.getBoolean("NpcDesignerDone")){mc.setScreen(null);return;}if(data.getBoolean("NpcDesigner")){if(mc.screen instanceof CityNpcDesignerScreen screen)screen.update(data);else mc.setScreen(new CityNpcDesignerScreen(data));return;}if(data.getBoolean("Trade")){if(mc.screen instanceof CityTradeScreen screen)screen.update(data);else mc.setScreen(new CityTradeScreen(data));return;}if(data.getBoolean("WorksPreview")){CityWorksProjection.update(data);return;}if(data.getBoolean("Works")){if(mc.screen instanceof CityWorksScreen screen)screen.update(data);else mc.setScreen(new CityWorksScreen(data,setupReturn(mc.screen)));return;}if(data.getBoolean("Housing")){if(mc.screen instanceof CityHouseScreen screen)screen.update(data);else mc.setScreen(new CityHouseScreen(data,setupReturn(mc.screen),setupReturn(mc.screen)==null?"":setupReturn(mc.screen).npc()));return;}if(data.getBoolean("RuralSettings")){if(mc.screen instanceof RuralSettingsScreen screen)screen.update(data);else mc.setScreen(new RuralSettingsScreen(data));return;}if(data.getBoolean("WorkHours")){if(mc.screen instanceof WorkHoursScreen screen)screen.update(data);else mc.setScreen(new WorkHoursScreen(data,mc.screen));return;}if(data.getBoolean("Rural")){if(mc.screen instanceof RuralScreen screen)screen.update(data);else mc.setScreen(new RuralScreen(data,setupReturn(mc.screen)));return;}
            if(data.getBoolean("MaintenanceWorker")){if(mc.screen instanceof CityProfessionScreen screen)screen.update(data);else mc.setScreen(new CityProfessionScreen(data,mc.screen));return;}
            if(data.getBoolean("MaintenanceCity")){if(mc.screen instanceof CityMaintenanceScreen screen)screen.update(data);else mc.setScreen(new CityMaintenanceScreen(data,mc.screen));return;}
            if(data.getBoolean("CityLawEditor")){if(mc.screen instanceof CityLawScreen screen)screen.update(data);else mc.setScreen(new CityLawScreen(data,mc.screen));return;}
            if(data.getBoolean("Overlay")){CityWorldOverlay.update(data);return;}
            if(data.getBoolean("BasicLivingEditor")){if(mc.screen instanceof CityBasicLivingScreen screen)screen.update(data);else mc.setScreen(new CityBasicLivingScreen(data,mc.screen));return;}
            if(data.getBoolean("Economy")){if(mc.screen instanceof CityEconomyScreen screen)screen.update(data);else mc.setScreen(new CityEconomyScreen(data));}
            else if(data.getBoolean("NpcCityEditor")){if(mc.screen instanceof CityNpcCityScreen screen)screen.update(data);else mc.setScreen(new CityNpcCityScreen(data,mc.screen));}
            else if(data.getBoolean("BuildingRegister"))mc.setScreen(new CityRegisterScreen(data,0,setupReturn(mc.screen)));
            else if(data.getBoolean("Help"))mc.setScreen(new CityHelpScreen(null));
            else if(data.getBoolean("SelectionEditor")){if(mc.screen instanceof CityFoundingScreen screen)screen.update(data);else mc.setScreen(new CityFoundingScreen(data));}
            else if(mc.screen instanceof CityFoundingScreen founding)founding.update(data);
            else if(mc.screen instanceof CityDashboardScreen screen)screen.update(data);
            else if(mc.screen instanceof CityRegisterScreen screen)screen.update(data);
            else if(mc.screen instanceof CityBlocksScreen screen)screen.update(data);
            else if(mc.screen instanceof CityInfoScreen screen)screen.update(data);
            else if(mc.screen instanceof CityScreen screen)screen.update(data);
            else if(data.getBoolean("Open"))mc.setScreen(new CityDashboardScreen(data));
        });
    }
}



