package com.muxigame.zaluokeforging.client;

import com.muxigame.zaluokeforging.menu.WorkshopMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class WorkshopScreen extends AbstractContainerScreen<WorkshopMenu> {
    public WorkshopScreen(WorkshopMenu menu, Inventory inventory, Component title) {
        super(menu,inventory,title); imageHeight=194; inventoryLabelY=100;
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my) {
        int x=leftPos,y=topPos;
        g.fill(x,y,x+imageWidth,y+imageHeight,0xFF292725);
        g.fill(x+2,y+2,x+imageWidth-2,y+imageHeight-2,0xFFC5BDAF);
        for(var slot:menu.slots) {
            if(!slot.isActive()) continue;
            g.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xFF514D47);
            g.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+16,0xFF918B81);
        }
        int value=menu.data.get(0)==0 ? menu.data.get(1) : menu.data.get(2);
        int cap=menu.data.get(0)==0 ? 192 : 20000;
        g.fill(x+112,y+35,x+126,y+89,0xFF302B27);
        g.fill(x+114,y+87-(int)(50.0*value/cap),x+124,y+87,0xFFED6B25);
        // Separate orange freeze-progress gauge on the far right of the refinery.
        if(menu.data.get(0)==0) {
            g.blit(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("zaluoke_forging","textures/gui/cooling_frame.png"),x+150,y+32,0,0,16,64,16,64);
            int fill=50*menu.data.get(4)/Math.max(1,menu.data.get(9));
            g.fill(x+155,y+89-fill,x+161,y+89,0xFFFFAA40);
            for(int line=y+89-fill;line<y+89;line+=5) g.fill(x+155,line,x+161,line+1,0xFFFFDA91);
        }
        int progress=menu.data.get(0)==0 ? menu.data.get(3)*80/200 : menu.data.get(5)*80/600;
        if(menu.data.get(0)==0) g.fill(x+24,y+58,x+104,y+64,0xFF514D47);
        if(menu.data.get(0)==0) g.fill(x+24,y+58,x+24+Math.min(80,progress),y+64,0xFFEAAE41);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my) {
        super.renderLabels(g,mx,my);
        boolean refinery=menu.data.get(0)==0;
        g.drawString(font,Component.translatable(refinery?"gui.zaluoke_forging.input":"gui.zaluoke_forging.fuel"),20,23,0x38332C,false);
        if(refinery) g.drawString(font,Component.translatable("gui.zaluoke_forging.slag"),52,23,0x38332C,false);
        g.drawString(font,Component.translatable(refinery?"gui.zaluoke_forging.iron_label":"gui.zaluoke_forging.fire_label"),108,23,0x38332C,false);
        if(refinery) g.drawString(font,Component.translatable("gui.zaluoke_forging.cool_label"),142,23,0x38332C,false);
        String amount=refinery?menu.data.get(1)+" / 192":String.format(java.util.Locale.ROOT,"%.1f / 200",menu.data.get(2)/100.0);
        g.drawString(font,Component.translatable(refinery?"gui.zaluoke_forging.iron":"gui.zaluoke_forging.charcoal",amount),8,70,0x38332C,false);
        String status=refinery && menu.data.get(6)>=64?"blocked":menu.data.get(7)==0?"cold":menu.data.get(8)==1?"boosted":"working";
        g.drawString(font,Component.translatable("gui.zaluoke_forging."+status),8,84,status.equals("blocked")?0xA02020:0x38332C,false);
    }
    @Override public void render(GuiGraphics graphics,int mx,int my,float partial) {
        super.render(graphics,mx,my,partial); renderTooltip(graphics,mx,my);
        if(isHovering(20,33,85,32,mx,my) && menu.data.get(0)==0)
            graphics.renderTooltip(font,Component.translatable("gui.zaluoke_forging.reclaimed",menu.data.get(10)),mx,my);
        if(isHovering(150,22,17,75,mx,my) && menu.data.get(0)==0)
            graphics.renderTooltip(font,Component.translatable("gui.zaluoke_forging.cooling",menu.data.get(4)/20.0,menu.data.get(9)/20.0),mx,my);
    }
}
