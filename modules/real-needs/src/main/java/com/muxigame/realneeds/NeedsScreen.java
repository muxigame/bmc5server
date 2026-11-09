package com.muxigame.realneeds;

import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

public final class NeedsScreen extends Screen {
    private final Screen parent;
    private CompoundTag snapshot;
    public NeedsScreen(Screen parent){super(Component.literal("真实需求"));this.parent=parent;}
    public void update(CompoundTag t){snapshot=t.copy();}
    @Override protected void init(){
        addRenderableWidget(Button.builder(Component.literal("返回"),b->onClose()).bounds(width/2-132,height-24,80,20).build());
        addRenderableWidget(Button.builder(Component.literal("参数调试"),b->NeedsClient.openSettings(this)).bounds(width/2-40,height-24,80,20).build());
        addRenderableWidget(Button.builder(Component.literal("伤病与护理"),b->NeedsClient.openHealth(this)).bounds(width/2+52,height-24,80,20).build());
    }
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        g.fill(0,0,width,height,0xff121a24);
        int w=Math.min(420,width-24),x=(width-w)/2;
        g.drawString(font,"真实需求  /  玩家营养与休息",x,10,0xfff2e8cd);
        if(snapshot==null){g.drawString(font,"正在等待服务器数据…",x,36,0xffc7d1dd);super.render(g,mx,my,dt);return;}
        var rules=snapshot.getCompound("rules");
        var physiology=rules.getCompound("physiology");double low=physiology.contains("need_low",99)?physiology.getDouble("need_low"):20,medium=physiology.contains("need_medium",99)?physiology.getDouble("need_medium"):50;
        String status=!rules.getBoolean("enabled")?"系统已停用":snapshot.getBoolean("paused")?"创造 / 旁观 / 死亡：暂停消耗":"生存模式 · 在线消耗 · 每秒同步";
        g.drawString(font,status,x,26,0xff9dabbc);
        int row=Math.min(38,Math.max(25,(height-94)/5));
        NeedsState s=NeedsState.load(snapshot);
        Need hovered=null;
        for(Need n:Need.values()){
            int y=44+n.ordinal()*row;double v=s.get(n);
            g.fill(x,y-2,x+w,y+row-3,0xff202d3b);
            g.drawString(font,n.label,x+7,y+2,n.color);
            String value=String.format(Locale.ROOT,"%.1f / 100  %s",v,v<=low?"不足":v<medium?"偏低":"充足");
            g.drawString(font,value,x+w-font.width(value)-7,y+2,0xffeef3f7);
            int barY=y+14;g.fill(x+7,barY,x+w-7,barY+4,0xff0e1620);
            g.fill(x+7,barY,x+7+(int)((w-14)*v/100),barY+4,v<=low?0xffd56e66:n.color);
            if(row>=34)g.drawString(font,n.hint+"  ·  "+rules.getDouble(n.id)+" / 分钟",x+7,y+23,0xffa9b8c8);
            if(mx>=x&&mx<x+w&&my>=y&&my<y+row)hovered=n;
        }
        if(height>=260)g.drawString(font,"储备越高越好；首版不足仅提示，不额外扣血。",x,height-40,0xff9dabbc);
        super.render(g,mx,my,dt);
        if(hovered!=null)g.renderTooltip(font,Component.literal(hovered.hint+"；基础消耗 "+rules.getDouble(hovered.id)+" / 分钟"),mx,my);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float dt){}
    @Override public boolean isPauseScreen(){return false;}
}
