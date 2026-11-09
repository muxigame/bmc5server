package com.muxigame.city;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
/** Global menu owns city configuration; faction menu owns only the selected citizen. */
@EventBusSubscriber(modid=CityMod.ID,value=Dist.CLIENT)
public final class CityNpcFactionHook {
    private static boolean global(Screen s){return s.getClass().getName().equals("noppes.npcs.client.gui.mainmenu.GuiNPCGlobalMainMenu");}
    private static boolean supported(Screen s){return global(s)||s.getClass().getName().equals("noppes.npcs.client.gui.advanced.GuiNPCFactionSetup");}
    private static String label(Screen s){return global(s)?"城市设置":"NPC个人 · 基础生活";}
    private static Button create(Screen s)throws ReflectiveOperationException{
        boolean global=global(s);int left=coordinate(s,"guiLeft"),top=coordinate(s,"guiTop");
        String npc=global?"":((Entity)s.getClass().getField("npc").get(s)).getUUID().toString();
        return Button.builder(Component.literal(label(s)),b->PacketDistributor.sendToServer(new CityNetwork.Request(global?"globalcityview":"npccityview","",0,0,0,0,0,0,0,npc,false))).bounds(left+(global?85:4),top+(global?208:100),global?200:180,20).build();
    }
    @SubscribeEvent public static void init(ScreenEvent.Init.Post event){if(!supported(event.getScreen()))return;try{event.addListener(create(event.getScreen()));}catch(ReflectiveOperationException ex){org.slf4j.LoggerFactory.getLogger(CityMod.ID).warn("Cannot attach city menu",ex);}}
    // CustomNPCs re-initializes its menu when asynchronous data arrives.
    @SubscribeEvent public static void render(ScreenEvent.Render.Pre event){var s=event.getScreen();if(!supported(s)||s.children().stream().anyMatch(v->v instanceof Button b&&b.getMessage().getString().equals(label(s))))return;
        try{var m=Screen.class.getDeclaredMethod("addRenderableWidget",net.minecraft.client.gui.components.events.GuiEventListener.class);m.setAccessible(true);m.invoke(s,create(s));}catch(ReflectiveOperationException ex){org.slf4j.LoggerFactory.getLogger(CityMod.ID).debug("Cannot restore city menu",ex);}}
    private static int coordinate(Object screen,String name)throws ReflectiveOperationException{for(Class<?> c=screen.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);return f.getInt(screen);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
}
