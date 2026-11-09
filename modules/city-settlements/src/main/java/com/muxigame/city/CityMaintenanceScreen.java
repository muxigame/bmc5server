package com.muxigame.city;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import com.google.gson.*;
/** One city page: completion baseline and communal granary. */
public final class CityMaintenanceScreen extends Screen {
 private CompoundTag data;private final Screen parent;private int selected;private JsonArray warehouses;private String message="";
 public CityMaintenanceScreen(CompoundTag t,Screen parent){super(Component.literal("城市落成与公共资源"));this.parent=parent;load(t);}
 private void load(CompoundTag t){data=t;warehouses=JsonParser.parseString(t.getString("Warehouses")).getAsJsonArray();selected=0;for(int i=0;i<warehouses.size();i++)if(warehouses.get(i).getAsJsonObject().get("id").getAsString().equals(t.getString("Warehouse")))selected=i;}
 public void update(CompoundTag t){load(t);rebuildWidgets();}
 private void send(String op,String value){PacketDistributor.sendToServer(new CityNetwork.Request(op,data.getString("City"),data.getLong("Revision"),0,0,0,0,0,0,value,false));}
 private void button(String s,int y,Runnable r){addRenderableWidget(Button.builder(Component.literal(s),b->r.run()).bounds(width/2-150,y,300,20).build());}
 protected void init(){button("城市落成：记录当前方块…",48,()->minecraft.setScreen(new ConfirmScreen(ok->{minecraft.setScreen(this);if(ok)send("maintcomplete","");},Component.literal("记录现在的城市？"),Component.literal("将替换旧落成基准。以后按此基准修复；不记录箱内物品。改建前请暂停修补。"))));button(data.getBoolean("Enabled")?"自动修补：开（点击暂停）":"自动修补：关（点击恢复）",74,()->send("maintpause",""));
 button(warehouses.isEmpty()?"尚无已登记经营粮仓":"公共粮仓："+warehouses.get(selected).getAsJsonObject().get("name").getAsString(),100,()->{if(!warehouses.isEmpty()){selected=(selected+1)%warehouses.size();rebuildWidgets();}});
 button(data.getBoolean("Collective")?"集体生产已开启 · 更换粮仓…":"启用集体口粮…",126,()->{if(warehouses.isEmpty()){message="请先登记粮仓及其经营账户";return;}minecraft.setScreen(new ConfirmScreen(ok->{minecraft.setScreen(this);if(ok)send("maintcollective",warehouses.get(selected).getAsJsonObject().get("id").getAsString());},Component.literal("使用城市公共粮食？"),Component.literal("本城暂停旧生产与消费订单，所有居民改为从公共粮仓吃面包；农民共同负责全城麦田，不发工资、不派送货员。")));});
 button("刷新状态",152,()->send("maintview",""));button("返回",height-30,this::onClose);}
 public void render(GuiGraphics g,int x,int y,float d){g.fill(0,0,width,height,0xff292b28);g.drawCenteredString(font,"城市落成 · "+data.getString("Name"),width/2,15,0xfff2dfb6);g.drawCenteredString(font,"已记录 "+data.getInt("Blocks")+" 格；"+font.plainSubstrByWidth(data.getString("Status"),width-170),width/2,32,0xffd1d6c7);if(height>240){g.drawCenteredString(font,"每名农民最多负责80格；每格每小时1小麦。",width/2,182,0xffd1d6c7);g.drawCenteredString(font,"3小麦自动制1面包；工匠只维修，不生产粮食。",width/2,198,0xffd1d6c7);}if(!message.isEmpty())g.drawCenteredString(font,message,width/2,height-47,0xffffcc77);super.render(g,x,y,d);}
 public void onClose(){minecraft.setScreen(parent);}public boolean isPauseScreen(){return false;}public void renderBackground(GuiGraphics g,int x,int y,float d){}
}
