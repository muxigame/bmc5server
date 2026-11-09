package com.muxigame.realneeds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid=RealNeeds.ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NeedsClient {
    static net.minecraft.nbt.CompoundTag latest;
    static Object level,player;
    static void checkSession(){var mc=Minecraft.getInstance();if(level!=mc.level||player!=mc.player){latest=null;level=mc.level;player=mc.player;}}
    public static void applyQuietHealth(net.minecraft.world.entity.player.Player player,NeedsNetwork.QuietHealth packet){
        if(player!=null&&player.getId()==packet.entityId()&&Float.isFinite(packet.health())&&packet.health()>=0)player.setHealth(packet.health());
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent e){e.enqueueWork(()->{NeedsNetwork.ClientBridge.quietHealth=p->applyQuietHealth(Minecraft.getInstance().player,p);NeedsNetwork.ClientBridge.openHealth=()->openHealth(Minecraft.getInstance().screen);NeedsNetwork.ClientBridge.receive=t->{
        var mc=Minecraft.getInstance();
        checkSession();latest=t.copy();
        if(mc.screen instanceof NeedsScreen s)s.update(t);
        else if(mc.screen instanceof NeedsSettingsScreen s)s.update(t);
        else if(mc.screen instanceof NeedsHealthScreen s)s.update(t);
        else if(t.getBoolean("open")){NeedsScreen s=new NeedsScreen(mc.screen);s.update(t);mc.setScreen(s);}
    };});}
    @EventBusSubscriber(modid=RealNeeds.ID,value=Dist.CLIENT)
    public static final class Screens {
        @SubscribeEvent public static void init(ScreenEvent.Init.Post e){
            var screen=e.getScreen();String name=screen.getClass().getName();
            if(screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen||screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen||screen instanceof net.minecraft.client.gui.screens.PauseScreen){
                checkSession();PacketDistributor.sendToServer(new NeedsNetwork.Request(false));
                e.addListener(Button.builder(Component.literal("真实需求 · 参数调试"),b->openSettings(screen)).bounds(4,4,132,20).build());
                e.addListener(Button.builder(Component.literal("伤病与护理"),b->openHealth(screen)).bounds(140,4,92,20).build());
            }
            if(!name.equals("noppes.npcs.client.gui.player.GuiQuestLog")&&!name.equals("noppes.npcs.client.gui.player.GuiFaction")&&!name.startsWith("noppes.npcs.client.gui.mainmenu."))return;
            // A separate bottom tab avoids replacing CustomNPCs widgets or modifying its jar.
            e.addListener(Button.builder(Component.literal("真实需求"),b->{
                Minecraft.getInstance().setScreen(new NeedsScreen(screen));
                PacketDistributor.sendToServer(new NeedsNetwork.Request(false));
            }).bounds(Math.max(4,screen.width-88),screen.height-24,84,20).build());
        }
        @SubscribeEvent public static void render(ScreenEvent.Render.Post e){
            var screen=e.getScreen();
            if(screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen||screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen){checkSession();NeedsInventoryPanel.render(e,(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)screen,latest);}
        }
        @SubscribeEvent public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e){latest=null;level=null;player=null;}
    }
    public static void openSettings(net.minecraft.client.gui.screens.Screen parent){
        checkSession();var screen=new NeedsSettingsScreen(parent);if(latest!=null)screen.update(latest);
        Minecraft.getInstance().setScreen(screen);PacketDistributor.sendToServer(new NeedsNetwork.Request(false));
    }
    public static void openHealth(net.minecraft.client.gui.screens.Screen parent){
        checkSession();var screen=new NeedsHealthScreen(parent);if(latest!=null)screen.update(latest);
        Minecraft.getInstance().setScreen(screen);PacketDistributor.sendToServer(new NeedsNetwork.Request(false));
    }
}
