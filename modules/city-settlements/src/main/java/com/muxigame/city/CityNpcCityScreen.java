package com.muxigame.city;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CityNpcCityScreen extends Screen {
    private CompoundTag data;private final Screen parent;private int page,wait;private boolean pending;
    public CityNpcCityScreen(CompoundTag data,Screen parent){super(Component.literal("NPC所属城市"));this.data=data;this.parent=parent;}
    public void update(CompoundTag tag){data=tag;pending=false;rebuildWidgets();}
    private void button(String text,int x,int y,int w,Runnable action){addRenderableWidget(Button.builder(Component.literal(text),b->{if(!pending)action.run();}).bounds(x,y,w,20).build());}
    private int rows(){return Math.max(1,(height-145)/24);}
    @Override protected void init(){ListTag cities=data.getList("Cities",Tag.TAG_COMPOUND);page=Math.clamp(page,0,Math.max(0,(cities.size()-1)/rows()));for(int i=0;i<rows()&&page*rows()+i<cities.size();i++){CompoundTag c=cities.getCompound(page*rows()+i);button((c.getString("Id").equals(data.getString("HomeId"))?"✓ ":"")+c.getString("Name"),12,72+i*24,width-24,()->confirm(c.getString("Id"),c.getString("Name"),c.getLong("Revision")));}
        button("上一页",12,height-65,65,()->{page=Math.max(0,page-1);rebuildWidgets();});button("下一页",84,height-65,65,()->{page=Math.min(Math.max(0,(cities.size()-1)/rows()),page+1);rebuildWidgets();});
        button("解除城市归属",156,height-65,100,()->confirm("","无所属城市",0));button("基础生活",12,height-36,70,()->request("npclivingbasicview",data.getString("NpcId"),0,""));button("返回阵营设置",width-116,height-36,104,this::onClose);
    }
    private void request(String op,String city,long revision,String value){pending=true;wait=100;PacketDistributor.sendToServer(new CityNetwork.Request(op,city,revision,0,0,0,0,0,0,value,false));}
    private void confirm(String id,String label,long revision){String value=data.getString("NpcId")+"|"+data.getString("HomeId")+"|"+data.getLong("HomeRevision");minecraft.setScreen(new ConfirmScreen(ok->{minecraft.setScreen(this);if(ok)request("npccityset",id,revision,value);},Component.literal("将 "+data.getString("NpcName")+" 的所属城市设为「"+label+"」？"),Component.literal("同步更新城市居民登记；阵营关系、AI、装备与位置保持。")));}
    @Override public void tick(){if(pending&&--wait<=0){pending=false;data.putString("Message","请求超时，请刷新或重新用NPC魔杖打开。");}}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void render(GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xff101923);g.drawString(font,"NPC所属城市 · "+data.getString("NpcName"),12,12,0xffedcf8c,false);g.drawString(font,"当前："+data.getString("HomeName"),12,34,0xffb4d8c1,false);g.drawString(font,"选择本维度城市；归属与城市居民列表实时共用。",12,52,0xffa8bdc7,false);g.drawString(font,font.plainSubstrByWidth(data.getString("Message"),width-140),84,height-29,0xffffc985,false);super.render(g,x,y,partial);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    @Override public boolean isPauseScreen(){return false;}
}
