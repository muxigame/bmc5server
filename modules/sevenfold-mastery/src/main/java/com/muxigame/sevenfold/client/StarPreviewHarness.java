package com.muxigame.sevenfold.client;

import com.muxigame.sevenfold.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Development render fixture. Excluded from the installed production JAR. No world is opened. */
@EventBusSubscriber(modid=SevenfoldMastery.ID,value=Dist.CLIENT)
public final class StarPreviewHarness {
    private static boolean opened;
    private static StarTreeState progress=new StarTreeState(0);
    private static final StarHold hold=new StarHold();
    private static int checks,diagnostics;
    private static String lastDiagnostic="";
    private static boolean fading,overlap;private static long fadeBegan;private static int fadePhase=-1;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("sevenfold.starPreview"))return;
        var mc=Minecraft.getInstance();
        if(opened){
            if(fading&&mc.screen instanceof StarTreeScreen){long elapsed=(net.minecraft.Util.getMillis()-fadeBegan)%20000;int phase=overlap?(elapsed<4000?0:elapsed<5200?1:elapsed<9000?2:elapsed<10200?3:elapsed<14000?4:elapsed<15200?5:6):(int)((net.minecraft.Util.getMillis()-fadeBegan)/4000)%4;
                if(phase!=fadePhase){fadePhase=phase;progress=new StarTreeState(3,5,WarProfession.NONE);if(overlap){if(phase==1)progress=progress.toggle(StarNode.WARRIOR,0);if(phase==3)progress=progress.toggle(StarNode.WARRIOR,-1);if(phase==5)progress=progress.toggle(StarNode.CRYING,-1);}
                    else{if(phase>=1)progress=progress.toggle(StarNode.WARRIOR,0);if(phase>=2)progress=progress.toggle(StarNode.WARRIOR,-1);if(phase>=3)progress=progress.toggle(StarNode.CRYING,-1);}MasteryClient.stars=progress;}}
            if(mc.screen instanceof StarTreeScreen s&&++checks%10==0&&diagnostics<80){
                String d=s.diagnostic();if(!d.equals(lastDiagnostic)){com.mojang.logging.LogUtils.getLogger().info("Star preview check: {}",d);lastDiagnostic=d;diagnostics++;}
            }
            return;
        }
        if(mc.screen instanceof TitleScreen){
            String stage=System.getProperty("sevenfold.starPreview.stage");overlap="overlap".equals(stage);fading="fade".equals(stage)||overlap;fadeBegan=net.minecraft.Util.getMillis();
            boolean explore="war".equals(stage)||fading;
            if(explore)progress=new StarTreeState(3,5,WarProfession.NONE);
            opened=true;MasteryClient.stars=progress;
            MasteryClient.offlineStarActions=a->{
                var node=StarNode.find(a.node());long tick=net.minecraft.Util.getMillis()/50;
                if(a.action().equals("cancel"))hold.cancel();
                else if(node.isPresent()&&progress.canToggle(node.get(),a.star())){
                    if(a.action().equals("begin"))hold.begin(node.get(),a.star(),tick);
                    else if(a.action().equals("finish")&&hold.finish(node.get(),a.star(),tick))progress=progress.toggle(node.get(),a.star());
                }
                var snapshot=new net.minecraft.nbt.CompoundTag();snapshot.put("star_tree",progress.save());snapshot.put("pools",CombatPools.full().save());MasteryNetwork.receive.accept(snapshot);
            };
            mc.setScreen(new StarTreeScreen(mc.screen).previewOnly(explore));
        }
    }
}
