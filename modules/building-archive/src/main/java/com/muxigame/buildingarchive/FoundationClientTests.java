package com.muxigame.buildingarchive;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

/** Opt-in, title-screen-only visual fixture; excluded from the release JAR. */
@EventBusSubscriber(modid=BuildingArchive.ID,value=Dist.CLIENT)
public final class FoundationClientTests {
    static CompoundTag fixture(){
        var t=new CompoundTag();t.putBoolean("Preview",true);t.putBoolean("Loaded",true);t.putBoolean("FoundationSupported",true);t.putBoolean("PreserveFoundation",true);t.putInt("FoundationLayers",3);
        t.putString("Name","农舍地基保护检查");t.putString("LoadedSize","40, 24, 32");t.putString("Anchor","-460, 65, 438");t.putInt("AnchorY",65);t.putInt("NonAir",9000);t.putInt("Kinds",32);t.putInt("Occupied",12000);t.putBoolean("CanPaste",true);t.putInt("Pages",1);t.putString("Message","底部3层空气保留原地形；地基以上空气照常覆盖。请核对顶层Y=67。");
        var projection=new CompoundTag();projection.putIntArray("Size",new int[]{40,24,32});projection.putIntArray("Runs",new int[]{0,0,0,1,1,1});t.put("Projection",projection);return t;
    }
    @SubscribeEvent public static void title(ScreenEvent.Init.Post e){if(Boolean.getBoolean("building_archive.foundationUiTest")&&e.getScreen() instanceof TitleScreen)Minecraft.getInstance().getWindow().setTitle("Building Archive 0.6.3 - Foundation UI Check");}
    @SubscribeEvent public static void key(ScreenEvent.KeyPressed.Pre e){
        if(!Boolean.getBoolean("building_archive.foundationUiTest")||Minecraft.getInstance().level!=null||!(e.getScreen() instanceof TitleScreen)||e.getKeyCode()!=GLFW.GLFW_KEY_F8)return;
        Minecraft.getInstance().setScreen(new ArchivePlacementScreen(fixture()));e.setCanceled(true);
    }
}
