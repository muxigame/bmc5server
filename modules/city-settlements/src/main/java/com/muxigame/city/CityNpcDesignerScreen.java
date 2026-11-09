package com.muxigame.city;

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** One short authoring form; detailed runtime numbers remain in their existing editors. */
public final class CityNpcDesignerScreen extends Screen {
 private CompoundTag data;private CityNpcDesigner.Template template;private EditBox name;private int left,top;private String city;private long cityRevision;private List<CompoundTag> cities;
 public CityNpcDesignerScreen(CompoundTag t){super(Component.literal("职业NPC设计"));load(t);}
 private void load(CompoundTag t){data=t;template=CityEconomy.JSON.fromJson(t.getString("Template"),CityNpcDesigner.Template.class);city=t.getString("City");cityRevision=t.getLong("CityRevision");cities=t.getList("Cities",10).stream().map(v->(CompoundTag)v).toList();}
 public void update(CompoundTag t){load(t);rebuildWidgets();}
 private void remember(){if(name!=null)template.name=name.getValue();}
 private void button(String title,int x,int y,int w,Runnable run){addRenderableWidget(Button.builder(Component.literal(title),b->run.run()).bounds(x,y,w,20).build());}
 private String cityName(){return cities.stream().filter(c->c.getString("Id").equals(city)).map(c->c.getString("Name")).findFirst().orElse("选择聚落");}
 private void send(String op){remember();PacketDistributor.sendToServer(new CityNetwork.Request(op,city,data.getLong("Token"),(int)(cityRevision>>>32),(int)cityRevision,0,0,0,0,CityEconomy.JSON.toJson(template),false));}
 private void setup(){remember();var v=new com.google.gson.JsonObject();v.add("template",CityEconomy.JSON.toJsonTree(template));PacketDistributor.sendToServer(new CityNetwork.Request("careerstatus",city,data.getLong("Token"),0,0,0,0,0,0,v.toString(),false));}
 protected void init(){button("极简帮助",width-86,7,74,()->{remember();minecraft.setScreen(CityHelpScreen.quick(this,"职业魔杖 · 极简"));});left=width/2-170;top=Math.max(28,(height-280)/2);name=addRenderableWidget(new EditBox(font,left+46,top+29,294,18,Component.literal("NPC姓名")));name.setMaxLength(32);name.setValue(template.name);
  button("职业："+CityNpcDesigner.label(template.role),left,top+56,224,()->{remember();int next=(CityNpcDesigner.ROLES.indexOf(template.role)+1)%CityNpcDesigner.ROLES.size();template=CityNpcDesigner.defaults(CityNpcDesigner.ROLES.get(next));send("careerview");});
  button(template.female?"外观：女性":"外观：男性",left+230,top+56,110,()->{remember();template.female=!template.female;template.skin="";send("careerview");});
  button("聚落："+cityName(),left,top+83,340,()->{if(cities.isEmpty())return;int index=-1;for(int n=0;n<cities.size();n++)if(cities.get(n).getString("Id").equals(city))index=n;var c=cities.get((index+1)%cities.size());city=c.getString("Id");cityRevision=c.getLong("Revision");send("careerview");});
  button("检查住房 / 粮箱 / 资金 / 启动",left,top+126,340,this::setup);
  button("读取魔杖模板",left,top+149,108,()->send("careerload"));button("保存到魔杖",left+116,top+149,108,()->send("careersave"));button("职业默认值",left+232,top+149,108,()->send("careerreset"));
  boolean target=!data.getString("Target").isEmpty();button(target?"取样职业、皮肤和作息":"新建请潜行右键地面",left,top+176,340,()->{if(target)send("careercapture");});
  button(target?"应用到选中NPC":"在选定位置创建NPC",left,top+221,224,()->send(target?"careerapply":"careerspawn"));button("关闭",left+230,top+221,110,this::onClose);
 }
 public void render(GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xff242924);g.drawCenteredString(font,title,width/2,top+4,0xfff4dfab);g.drawString(font,"姓名",left,top+34,0xffdddddd);g.drawString(font,font.plainSubstrByWidth(data.getString("Readiness"),340),left,top+112,0xffd5cb91);g.drawWordWrap(font,Component.literal("模板只带设计，不带银币、物品、家庭或工作进度。"),left,top+202,340,0xffb8c6b3);g.drawWordWrap(font,Component.literal(data.getString("Message")),left,top+250,340,0xffffd38b);super.render(g,x,y,partial);}
 public boolean isPauseScreen(){return false;}public void renderBackground(GuiGraphics g,int x,int y,float partial){}
}
