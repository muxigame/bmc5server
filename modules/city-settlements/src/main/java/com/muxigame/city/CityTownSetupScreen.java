package com.muxigame.city;

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** One type choice and one optional opposite-town choice; no economic reset or sample button. */
public final class CityTownSetupScreen extends Screen {
 final Screen parent;CompoundTag data;boolean urban,pending;String peer="",message="";int wait;
 CityTownSetupScreen(CompoundTag data,Screen parent){super(Component.literal("城村经济登记"));this.parent=parent;update(data);}
 void update(CompoundTag tag){pending=false;if(tag.getBoolean("Failed")){message=tag.getString("Message");return;}data=tag;urban=tag.getBoolean("Urban");peer=tag.getString("Partner");message=tag.getString("Message");if(minecraft!=null)rebuildWidgets();}
 List<CompoundTag> peers(){var rows=new ArrayList<CompoundTag>();for(var v:data.getList("Peers",10)){var row=(CompoundTag)v;if(row.getBoolean("Urban")!=urban&&row.getBoolean("Available"))rows.add(row);}return rows;}
 void send(String op){if(pending||data==null)return;var form=new CityTownSetup.Form();form.revision=data.getLong("RegistrationRevision");form.urban=urban;form.partner=peer;for(var row:peers())if(row.getString("Id").equals(peer))form.partnerRevision=row.getLong("Revision");pending=true;wait=100;PacketDistributor.sendToServer(new CityNetwork.Request(op,data.getString("City"),data.getLong("CityRevision"),0,0,0,0,0,0,CityEconomy.JSON.toJson(form),false));}
 void button(String label,int x,int y,int w,Runnable action,boolean active){addRenderableWidget(Button.builder(Component.literal(label),b->{if(!pending)action.run();}).bounds(x,y,w,20).build()).active=active;}
 protected void init(){if(data==null){button("返回",width/2-50,height/2+24,100,this::onClose,true);return;}int x=(width-340)/2,y=Math.max(8,(height-258)/2);boolean registered=data.getBoolean("Registered");
  button(urban?"类型：城市":"类型：村落",x+12,y+44,152,()->{urban=!urban;peer="";rebuildWidgets();},!registered);
  button(registered?"已登记（类型锁定）":"登记本聚落",x+176,y+44,152,()->send("townregister"),!registered);
  String peerName=peer.isEmpty()?"暂不对接":peers().stream().filter(row->row.getString("Id").equals(peer)).map(row->row.getString("Name")).findFirst().orElse("当前对接不可编辑");
  button("对接："+font.plainSubstrByWidth(peerName,118),x+12,y+88,210,()->{var rows=peers();int at=-1;for(int i=0;i<rows.size();i++)if(rows.get(i).getString("Id").equals(peer))at=i;peer=at+1<rows.size()?rows.get(at+1).getString("Id"):"";rebuildWidgets();},!registered||data.getString("Partner").isEmpty());
  button("确认对接",x+234,y+88,94,()->send("townlink"),registered&&!peer.isEmpty()&&data.getString("Partner").isEmpty());
  button("刷新",x+12,y+216,100,()->send("townview"),true);button("返回",x+228,y+216,100,this::onClose,true);
 }
 public void tick(){if(pending&&--wait<=0){pending=false;message="请求超时，请先刷新核对，勿重复初始化。";}}
 public void render(GuiGraphics g,int mx,int my,float pt){int x=(width-340)/2,y=Math.max(8,(height-258)/2);g.fill(x,y,x+340,y+258,0xf0182735);if(data==null){g.drawWordWrap(font,Component.literal(message),x+12,y+14,316,0xffffc68b);super.render(g,mx,my,pt);return;}g.drawString(font,"城村经济登记 · "+font.plainSubstrByWidth(data.getString("Name"),204),x+12,y+14,0xffedcf8c,false);g.drawWordWrap(font,Component.literal("先登记两处已有聚落的类型，再在任一处选择另一处对接。只支持一城一村，不覆盖已有路线。"),x+12,y+120,316,0xffccddce);g.drawWordWrap(font,Component.literal("登记不送钱粮、不生成NPC、不自动开城。已有NPC须重新保存职业以登记经营岗位。"),x+12,y+155,316,0xffffd19b);g.drawWordWrap(font,Component.literal(message),x+12,y+185,316,0xffffc68b);super.render(g,mx,my,pt);}
 public boolean isPauseScreen(){return false;}public void renderBackground(GuiGraphics g,int x,int y,float pt){}public void onClose(){minecraft.setScreen(parent);}
}
