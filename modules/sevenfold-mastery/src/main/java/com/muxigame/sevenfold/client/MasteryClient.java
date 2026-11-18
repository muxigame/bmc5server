package com.muxigame.sevenfold.client;

import com.muxigame.sevenfold.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid=SevenfoldMastery.ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class MasteryClient {
    public static StarTreeState stars;
    public static CombatPools pools;
    static java.util.function.Consumer<MasteryNetwork.StarAction> offlineStarActions=a->{};
    @SubscribeEvent public static void setup(FMLClientSetupEvent e){e.enqueueWork(()->MasteryNetwork.receive=t->{
        stars=StarTreeState.load(t.getCompound("star_tree"));
        if(Minecraft.getInstance().player!=null)stars.store(Minecraft.getInstance().player);
        pools=CombatPools.load(t.getCompound("pools"));
        if(Minecraft.getInstance().screen instanceof StarTreeScreen s)s.received();
    });}
    public static void starSend(String action,StarNode node){starSend(action,node,0);}
    public static void starSend(String action,StarNode node,int index){
        var packet=new MasteryNetwork.StarAction(action,node==null?"":node.id,index);
        if(Minecraft.getInstance().getConnection()!=null)PacketDistributor.sendToServer(packet);
        else offlineStarActions.accept(packet);
    }
    public static ResourceLocation texture(Mastery a){return ResourceLocation.fromNamespaceAndPath(SevenfoldMastery.ID,"textures/gui/"+(a==null?"mana":a.id)+".png");}
    public static void icon(GuiGraphics g,Mastery a,int x,int y,int scale,int variant){
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);
        g.blit(texture(a),0,0,variant*9,0,9,9,27,9);g.pose().popPose();
    }
    @EventBusSubscriber(modid=SevenfoldMastery.ID,value=Dist.CLIENT)
    public static final class Events {
        @SubscribeEvent public static void init(ScreenEvent.Init.Post e){
            if(!(e.getScreen() instanceof InventoryScreen)&&!(e.getScreen() instanceof CreativeModeInventoryScreen))return;
            var s=(AbstractContainerScreen<?>)e.getScreen();
            e.addListener(Button.builder(Component.translatable("ui.sevenfold_mastery.star_tree"),b->{starSend("read",null);Minecraft.getInstance().setScreen(new StarTreeScreen(s));}).bounds(236,4,72,20).build());
        }
        private static void combatFrame(GuiGraphics g,int x,int y,int size){
            int color=0xFF8CBACD;g.fill(x-1,y-1,x+size+1,y,0xFF314753);g.fill(x-1,y+size,x+size+1,y+size+1,0xFF314753);
            g.fill(x-1,y,x,y+size,color);g.fill(x+size,y,x+size+1,y+size,color);
            g.fill(x,y,x+3,y+1,color);g.fill(x+size-3,y+size-1,x+size,y+size,color);
        }
        @SubscribeEvent public static void inventoryFrames(ScreenEvent.Render.Post e){
            var mc=Minecraft.getInstance();if(stars==null||!stars.active()||mc.player==null||!(e.getScreen() instanceof AbstractContainerScreen<?> s))return;
            for(var slot:s.getMenu().slots)if(slot.container==mc.player.getInventory()&&(slot.getContainerSlot()==0||slot.getContainerSlot()==40))combatFrame(e.getGuiGraphics(),s.getGuiLeft()+slot.x-1,s.getGuiTop()+slot.y-1,18);
        }
        @SubscribeEvent public static void hotbarFrames(RenderGuiLayerEvent.Post e){
            var mc=Minecraft.getInstance();if(!e.getName().equals(VanillaGuiLayers.HOTBAR)||stars==null||!stars.active()||mc.player==null||mc.options.hideGui)return;
            var g=e.getGuiGraphics();int x=g.guiWidth()/2-90,y=g.guiHeight()-21;combatFrame(g,x,y,19);
            int off=mc.player.getMainArm()==net.minecraft.world.entity.HumanoidArm.RIGHT?x-29:x+189;
            if(mc.player.getOffhandItem().isEmpty()){g.fill(off,y,off+19,y+19,0xA020252B);icon(g,Mastery.RESILIENCE,off+5,y+5,1,1);}
            combatFrame(g,off,y,19);
        }
        @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST,receiveCanceled=true)
        public static void noStarMusic(SelectMusicEvent e){if(Minecraft.getInstance().screen instanceof StarTreeScreen)e.overrideMusic(null);}
        @SubscribeEvent public static void food(RenderGuiLayerEvent.Pre e){
            var mc=Minecraft.getInstance();
            if(!e.getName().equals(VanillaGuiLayers.FOOD_LEVEL)||stars==null||!stars.active()||pools==null||mc.player==null||mc.gameMode==null||!mc.gameMode.canHurtPlayer()||mc.getCameraEntity()!=mc.player)return;
            e.setCanceled(true);
            int right=e.getGuiGraphics().guiWidth()/2+91;
            int y=e.getGuiGraphics().guiHeight()-mc.gui.rightHeight;
            var resource=CombatPools.displayedResource(stars);
            int cap=pools.cap(resource);
            double units=pools.value(resource)*20.0/cap;
            for(int i=0;i<10;i++)icon(e.getGuiGraphics(),resource,right-i*8-9,y,1,units>=i*2+2?0:units>i*2?2:1);
            if(stars!=null&&stars.active()){
                var g=e.getGuiGraphics();int food=Math.clamp(mc.player.getFoodData().getFoodLevel(),0,100);
                g.fill(right-81,y+10,right,y+12,0xFF312519);g.fill(right-81,y+10,right-81+(int)(81*food/100.0),y+12,0xFFC99B49);
            }
            mc.gui.rightHeight+=10; // Keep oxygen and mounted health above this row.
        }
        @SubscribeEvent public static void health(RenderGuiLayerEvent.Pre e){
            var mc=Minecraft.getInstance();if(!e.getName().equals(VanillaGuiLayers.PLAYER_HEALTH)||stars==null||!stars.active()||mc.player==null||mc.gameMode==null||!mc.gameMode.canHurtPlayer()||mc.getCameraEntity()!=mc.player)return;
            e.setCanceled(true);var g=e.getGuiGraphics();int left=g.guiWidth()/2-91,y=g.guiHeight()-mc.gui.leftHeight;
            double units=Math.clamp(mc.player.getHealth()/mc.player.getMaxHealth()*20,0,20);
            boolean hardcore=mc.level.getLevelData().isHardcore();
            String type=mc.player.hasEffect(net.minecraft.world.effect.MobEffects.POISON)?"poisoned_":mc.player.hasEffect(net.minecraft.world.effect.MobEffects.WITHER)?"withered_":mc.player.isFullyFrozen()?"frozen_":"";
            String variant=hardcore?"hardcore_":"",blink=mc.player.hurtTime>0&&mc.player.tickCount%4<2?"_blinking":"";
            for(int i=0;i<10;i++){
                g.blitSprite(ResourceLocation.withDefaultNamespace("hud/heart/"+(hardcore?"container_hardcore":"container")),left+i*8,y,9,9);
                if(units>i*2)g.blitSprite(ResourceLocation.withDefaultNamespace("hud/heart/"+type+variant+(units>=i*2+2?"full":"half")+blink),left+i*8,y,9,9);
            }
            if(mc.player.getAbsorptionAmount()>0){int pixels=(int)Math.ceil(81*Math.min(1,mc.player.getAbsorptionAmount()/mc.player.getMaxHealth()));g.fill(left,y-3,left+pixels,y-1,0xFFEAC64D);}
            mc.gui.leftHeight+=10;
        }
        @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){stars=null;pools=null;}
    }
}
