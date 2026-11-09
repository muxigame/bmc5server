package com.muxigame.realneeds;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Read-only debug sidebar. Does not replace the inventory screen or any slots. */
public final class NeedsInventoryPanel {
    private static final int WIDTH=116,ROW=11,HEIGHT=264;
    public static void render(ScreenEvent.Render.Post event,AbstractContainerScreen<?> screen,CompoundTag snapshot){
        var mc=Minecraft.getInstance();var g=event.getGuiGraphics();var font=mc.font;
        int boundary=screen.getGuiLeft();
        if(screen instanceof InventoryScreen inventory&&inventory.getRecipeBookComponent().isVisible()){
            int free=screen.width<379?(screen.width-147)/2-30:boundary-177-30;
            // At narrow widths the recipe book already covers the inventory; keep the
            // debug labels readable at the inventory's left edge instead of scaling to zero.
            if(free>=64)boundary=free;
        }
        float scale=Math.min(1f,Math.min(Math.max(1,boundary-8)/(float)WIDTH,(screen.height-8)/(float)HEIGHT));
        int x=Math.max(2,boundary-4-(int)(WIDTH*scale));
        int y=Math.max(4,Math.min(screen.getGuiTop(),screen.height-4-(int)(HEIGHT*scale)));
        double mx=(event.getMouseX()-x)/scale,my=(event.getMouseY()-y)/scale;
        g.pose().pushPose();g.pose().translate(x,y,400);g.pose().scale(scale,scale,1);
        g.fill(0,0,WIDTH,HEIGHT,0xe817202c);g.fill(0,0,WIDTH,18,0xff2a394b);
        g.drawString(font,"真实需求 · 调试",6,5,0xffefdbad);
        if(snapshot==null)g.drawString(font,"等待服务端数据…",6,25,0xffb7c5d6);
        else{
            var state=NeedsState.load(snapshot);int row=0;
            for(var need:Need.values())draw(g,need.label,String.format(Locale.ROOT,"%.1f",state.get(need)),row++,need.color);
            for(var stat:BodyStat.values())draw(g,stat.label,state.isSet(stat)?String.format(Locale.ROOT,"%.1f",state.get(stat)):"未设",row++,state.isSet(stat)?0xffd2dce7:0xff8798ac);
        }
        int count=snapshot==null?0:NeedsState.load(snapshot).ailments().cases().size();
        g.drawString(font,"伤病 "+count+" 项 · 上方按钮",6,HEIGHT-16,count>0?0xffe4aa87:0xff91a5bc);
        g.pose().popPose();
        int hover=(int)((my-24)/ROW)-Need.values().length;
        if(snapshot!=null&&mx>=0&&mx<WIDTH&&my>=24&&hover>=0&&hover<BodyStat.values().length){
            var stat=BodyStat.values()[hover];var state=NeedsState.load(snapshot);
            var entry=NeedsParameters.find(stat==BodyStat.INTIMIDATION?"intimidation_base":"initial."+stat.id);
            String detail=stat.label+"：0–100，初始 "+NeedsParameters.format(NeedsParameters.get(snapshot.getCompound("rules"),entry))+"；点击左上角参数调试可修改规则。";
            if(stat==BodyStat.FOOD_BOREDOM)detail+=" 最近食物："+(state.lastFood().isEmpty()?"无":state.lastFood())+"；连续 "+state.foodStreak()+" 次；高油脂标签："+(state.lastFoodHighFat()?"是":"否/未分类")+"。";
            if(stat==BodyStat.IMMUNITY)detail+=" 最近感染判定："+state.disease()+"；上方伤病与护理查看实际病情。";
            if(stat==BodyStat.INTIMIDATION)detail+=" 根据身材、肌肉、护甲和当前武器自动更新。";
            if(stat==BodyStat.TEMPERAMENT)detail+=" 低值偏流氓，高值偏正气；对话联动接口预留。";
            g.renderTooltip(font,Component.literal(detail),event.getMouseX(),event.getMouseY());
        }
    }
    private static void draw(net.minecraft.client.gui.GuiGraphics g,String label,String value,int row,int color){
        var font=Minecraft.getInstance().font;int y=24+row*ROW;
        if(row%2==0)g.fill(3,y-1,WIDTH-3,y+10,0x242e4761);
        g.drawString(font,label,6,y,color);g.drawString(font,value,WIDTH-font.width(value)-6,y,0xffe8eef5);
    }
}
