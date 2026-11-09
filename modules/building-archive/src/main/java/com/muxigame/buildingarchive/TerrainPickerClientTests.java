package com.muxigame.buildingarchive;

import java.nio.file.*;
import java.util.*;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

/** NEVER PUBLISH. Excluded by *Tests*.class; opt-in dev title-screen-only UI observation. */
@EventBusSubscriber(modid=BuildingArchive.ID,value=Dist.CLIENT)
public final class TerrainPickerClientTests {
    static boolean enabled(){return Boolean.getBoolean("building_archive.searchUiTest");}
    static final List<Map<String,Object>> events=new ArrayList<>();static String previous="";static long before,frames,maxFrameNanos;
    @SubscribeEvent public static void title(ScreenEvent.Init.Post e){if(enabled()&&e.getScreen() instanceof TitleScreen)Minecraft.getInstance().getWindow().setTitle("Building Archive 0.6.2 - Search UI Check");}
    @SubscribeEvent public static void key(ScreenEvent.KeyPressed.Pre e){
        if(enabled()&&Minecraft.getInstance().level==null&&e.getScreen() instanceof TerrainScreen.Picker&&e.getKeyCode()==GLFW.GLFW_KEY_F9){Minecraft.getInstance().reloadResourcePacks();e.setCanceled(true);return;}
        if(!enabled()||Minecraft.getInstance().level!=null||!(e.getScreen() instanceof TitleScreen)||e.getKeyCode()!=GLFW.GLFW_KEY_F8)return;
        var data=new CompoundTag();data.putString("Mode","replace");data.putString("Source","minecraft:stone");data.putString("Target","minecraft:dirt");data.putString("A","未选择");data.putString("B","未选择");
        Minecraft.getInstance().setScreen(new TerrainScreen.Picker(new TerrainScreen(data),true));e.setCanceled(true);
    }
    @SubscribeEvent public static void begin(ScreenEvent.Render.Pre e){if(enabled()&&e.getScreen() instanceof TerrainScreen.Picker)before=System.nanoTime();}
    @SubscribeEvent public static void observe(ScreenEvent.Render.Post e){
        if(!enabled()||!(e.getScreen() instanceof TerrainScreen.Picker p))return;
        frames++;maxFrameNanos=Math.max(maxFrameNanos,System.nanoTime()-before);
        String key=p.query+"/"+p.busy()+"/"+System.identityHashCode(p.catalog);
        if(key.equals(previous)||events.size()>=40)return;previous=key;
        var event=new LinkedHashMap<String,Object>();event.put("query",p.query);event.put("busy",p.busy());event.put("matches",p.matches.size());event.put("searchWidgetIdentity",System.identityHashCode(p.search));event.put("catalogueIdentity",System.identityHashCode(p.catalog));event.put("searchFocused",p.getFocused()==p.search);event.put("cursor",p.search.getCursorPosition());event.put("frames",frames);event.put("maxWholeScreenRenderNanos",maxFrameNanos);event.put("visibleRows",p.rows.size());events.add(event);
        try{Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("terrain-search-ui-observations.json"),new GsonBuilder().setPrettyPrinting().create().toJson(events));}catch(Exception ex){throw new IllegalStateException("UI evidence write failed",ex);}
    }
}
