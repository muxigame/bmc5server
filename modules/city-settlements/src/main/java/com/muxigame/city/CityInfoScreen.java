package com.muxigame.city;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** The two-wand workflow's small settings page; the detailed map stays one click away. */
public final class CityInfoScreen extends Screen {
    private CompoundTag data;private EditBox name;private String editingCity="";private boolean pending;private int wait,age;private String message="";
    public CityInfoScreen(CompoundTag data){super(Component.literal("信息魔杖"));this.data=data;}
    public void update(CompoundTag tag){String draft=name!=null&&!name.getValue().equals(data.getString("Name"))&&data.getString("Id").equals(tag.getString("Id"))?name.getValue():null;data=tag;pending=false;if(!tag.getString("Message").isEmpty())message=tag.getString("Message");if(draft==null)name=null;rebuildWidgets();if(draft!=null)name.setValue(draft);}
    private void send(String op,String id,String value){if(pending)return;pending=true;wait=100;PacketDistributor.sendToServer(new CityNetwork.Request(op,id,data.getLong("Revision"),data.getInt("X"),data.getInt("Y"),data.getInt("Z"),0,0,0,value,data.getBoolean("Surface")));}
    private void send(String op){send(op,data.getString("Id"),"");}
    private void button(String text,int x,int y,int w,Runnable action){addRenderableWidget(Button.builder(Component.literal(text),b->{if(!pending)action.run();}).bounds(x,y,w,20).build());}
    @Override protected void init(){int x=(width-320)/2,y=(height-258)/2;
        String draft=name!=null&&editingCity.equals(data.getString("Id"))?name.getValue():data.getString("Name");
        name=addRenderableWidget(new EditBox(font,x+12,y+35,216,18,Component.literal("城市名称")));name.setMaxLength(32);name.setValue(draft);editingCity=data.getString("Id");
        button("保存名称",x+234,y+34,74,()->send("rename",data.getString("Id"),name.getValue()));
        button("切换城市",x+12,y+64,142,()->{ListTag cities=data.getList("Cities",Tag.TAG_COMPOUND);if(cities.isEmpty())return;int n=-1;for(int i=0;i<cities.size();i++)if(cities.getCompound(i).getString("Id").equals(data.getString("Id")))n=i;send("select",cities.getCompound((n+1)%cities.size()).getString("Id"),"");});
        button(data.getBoolean("Visible")?"现场显示：开":"现场显示：关",x+166,y+64,142,()->send("visibility"));
        button("调整当前选区",x+12,y+94,142,()->send("draft"));button("撤销上次操作",x+166,y+94,142,()->send("undo"));
        button("城市沙盘 / 详细编辑",x+12,y+124,142,()->minecraft.setScreen(new CityScreen(data)));button("方块种类与数量",x+166,y+124,142,()->minecraft.setScreen(new CityBlocksScreen(data)));
        button("建设值 / 城市维护 / 施工蓝图",x+12,y+151,296,()->send("works_view"));
        button("城村经济登记 / 对接地",x+12,y+177,296,()->send("townview"));
        button("操作帮助",x+12,y+203,90,()->minecraft.setScreen(CityHelpScreen.quick(this,"城市与建筑魔杖 · 极简")));
        addRenderableWidget(Button.builder(Component.literal("§c删除城市"),b->confirmDelete()).bounds(x+113,y+203,94,20).build()).active=data.getBoolean("Editable")&&data.contains("Id")&&!pending;
        button("城市信息",x+218,y+203,90,()->minecraft.setScreen(new CityDashboardScreen(data)));
    }
    private void confirmDelete(){
        if(pending)return;String id=data.getString("Id"),cityName=data.getString("Name");long revision=data.getLong("Revision");
        minecraft.setScreen(new ConfirmScreen(ok->{minecraft.setScreen(this);if(ok){pending=true;wait=100;PacketDistributor.sendToServer(new CityNetwork.Request("delete",id,revision,0,0,0,0,0,0,cityName,false));}},
            Component.literal("删除城市「"+cityName+"」？"),Component.literal("将删除城市登记、区域归属和居民绑定。世界建筑和NPC实体保留。此操作不能撤销；需要恢复时请使用存档备份。"),Component.literal("确认删除城市"),Component.literal("取消")));
    }
    @Override public void tick(){if(pending&&--wait<=0){pending=false;message="请求超时，请重试。";}if(!pending&&++age%20==0&&data.getBoolean("Scanning"))send("view");}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){int x=(width-320)/2,y=(height-258)/2;g.fill(x,y,x+320,y+258,0xf0182735);g.drawString(font,"城市设置",x+12,y+12,0xffedcf8c,false);
        
        g.drawString(font,font.plainSubstrByWidth(message.isEmpty()?"城市魔杖选两角，再右键空气应用。":message,296),x+12,y+240,0xffedcf8c,false);super.render(g,mx,my,partial);
    }
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    @Override public boolean isPauseScreen(){return false;}
}
