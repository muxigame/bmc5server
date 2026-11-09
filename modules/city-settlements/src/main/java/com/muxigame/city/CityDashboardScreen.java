package com.muxigame.city;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CityDashboardScreen extends Screen {
    private CompoundTag data;private int age;
    public CityDashboardScreen(CompoundTag data){super(Component.literal("城市信息"));this.data=data;}
    public void update(CompoundTag incoming){data=incoming;rebuildWidgets();}
    @Override public void tick(){if(++age%20==0&&data.getBoolean("Scanning")&&data.contains("Id"))PacketDistributor.sendToServer(new CityNetwork.Request("view",data.getString("Id"),data.getLong("Revision"),data.getInt("X"),data.getInt("Y"),data.getInt("Z"),0,0,0,"",data.getBoolean("Surface")));}
    private void button(String label,int x,int y,int w,Runnable action){addRenderableWidget(Button.builder(Component.literal(label),b->action.run()).bounds(x,y,w,20).build());}
    @Override protected void init(){int x=(width-320)/2,y=(height-230)/2;
        button("建筑登记",x+12,y+98,94,()->minecraft.setScreen(new CityRegisterScreen(data,0)));
        button("NPC绑定",x+113,y+98,94,()->minecraft.setScreen(new CityRegisterScreen(data,1)));
        button("仓库绑定",x+214,y+98,94,()->minecraft.setScreen(new CityRegisterScreen(data,2)));
        button("城市管理",x+12,y+168,94,()->minecraft.setScreen(new CityInfoScreen(data)));
        button("方块统计",x+113,y+168,94,()->minecraft.setScreen(new CityBlocksScreen(data)));
        button("刷新信息",x+214,y+168,94,()->{if(data.contains("Id"))PacketDistributor.sendToServer(new CityNetwork.Request("refresh",data.getString("Id"),data.getLong("Revision"),data.getInt("X"),data.getInt("Y"),data.getInt("Z"),0,0,0,"",data.getBoolean("Surface")));});
        button("经济与库存",x+12,y+199,94,()->{if(data.contains("Id"))PacketDistributor.sendToServer(new CityNetwork.Request("econview",data.getString("Id"),0,0,0,0,0,0,0,"",false));});
        button("治安与赃物",x+113,y+199,94,()->{if(data.contains("Id"))PacketDistributor.sendToServer(new CityNetwork.Request("lawread",data.getString("Id"),0,0,0,0,0,0,0,"",false));});
        button("返回现场",x+214,y+199,94,this::onClose);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){int x=(width-320)/2,y=(height-230)/2;g.fill(x,y,x+320,y+230,0xf0182735);
        g.drawString(font,"城市信息 · "+data.getString("Name"),x+12,y+12,0xffedcf8c,false);
        g.drawString(font,"居民登记："+data.getInt("Residents")+" 人（含未加载）",x+12,y+36,0xffb4d8c1,false);
        g.drawString(font,"建筑："+data.getList("Buildings",Tag.TAG_COMPOUND).size()+" 栋    仓库容器："+data.getList("Warehouses",Tag.TAG_COMPOUND).size(),x+12,y+54,0xffb4d8c1,false);
        g.drawString(font,"方块快照："+(data.getBoolean("HasStats")?data.getLong("NonAir")+" 非空气方块":"待统计"),x+12,y+72,0xffb4d8c1,false);
        g.drawString(font,"稳定度："+data.getInt("Stability")+"  · 暴乱风险："+data.getString("RiotRisk"),x+12,y+132,0xffa8bdc7,false);
        g.drawString(font,"治安："+(data.contains("Security")?data.getInt("Security"):100)+" / 100 · 赃物扣分待盘问接入",x+12,y+148,0xffa8bdc7,false);
        if(!data.getString("Message").isEmpty())g.drawString(font,font.plainSubstrByWidth(data.getString("Message"),296),x+12,y+120,0xffffc985,false);super.render(g,mx,my,partial);
    }
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    @Override public boolean isPauseScreen(){return false;}
}
