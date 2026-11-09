package com.muxigame.city;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
public final class RuralScreen extends Screen {
 CompoundTag data;RuralRules.Person person;int left;
 private final CityNpcSetupScreen setupReturn; CityNpcSetupScreen setupParent(){return setupReturn;}
 public RuralScreen(CompoundTag t){this(t,null);}public RuralScreen(CompoundTag t,CityNpcSetupScreen back){super(Component.literal(back==null?"农村家庭与生活":"居民循环设置"));setupReturn=back;update(t);}
 public void onClose(){if(setupReturn!=null)setupReturn.resume();else super.onClose();}
 void update(CompoundTag t){data=t;person=CityEconomy.JSON.fromJson(t.getString("Person"),RuralRules.Person.class);if(minecraft!=null)rebuildWidgets();}
 protected void init(){button("极简帮助",width-86,7,74,()->minecraft.setScreen(CityHelpScreen.quick(this,"储存与家庭魔杖 · 极简")));left=width/2-165;int y=Math.max(232,height-105);button(person.role==RuralRules.Role.MERCHANT?"头人库存/村庄模拟":"村庄模拟设置",left,205,330,()->send("marketview",""));button("指定婴儿照护",left,155,105,()->send("ruralcare",""));button("加入所选家庭",left,178,160,()->send("ruraljoin",""));button("工作时间 / 猎户巡逻",left+170,178,160,()->send("workview",""));if(person.role==RuralRules.Role.MERCHANT){button("小麦价 "+person.wheatPrice,left+112,155,105,()->send("ruralwheatprice",""));button("面包价 "+person.breadPrice,left+224,155,106,()->send("ruralbreadprice",""));}button(setupReturn==null?"启用本村主粮循环":"启用本聚落居民循环",left,y,160,()->send("ruralenable",""));button("年龄阶段",left+170,y,160,()->send("ruralage",""));button("怀孕标记",left,y+24,105,()->send("ruralpregnant",""));button("恋爱",left+112,y+24,105,()->send("ruralrelation","love"));button("单身/解除关系",left+224,y+24,106,()->send("ruralrelation","single"));button("事实婚姻",left,y+48,105,()->send("ruralrelation","factual"));button("法定婚姻",left+112,y+48,105,()->send("ruralrelation","legal"));button("关闭",left+224,y+48,106,this::onClose);}
 void button(String text,int x,int y,int w,Runnable action){addRenderableWidget(Button.builder(Component.literal(text),b->action.run()).bounds(x,y,w,20).build());}
 void send(String op,String value){PacketDistributor.sendToServer(new CityNetwork.Request(op,data.getString("Npc"),data.getLong("Revision"),0,0,0,0,0,0,value,false));}
 public void render(GuiGraphics g,int x,int y,float tick){g.fill(0,0,width,height,0xff242921);g.drawCenteredString(font,(setupReturn==null?"农村家庭 · ":"居民循环 · ")+data.getString("Title"),width/2,12,0xfff4d99c);String[] ages={"幼年","童年","成年","青年","中年","老年"};String[] roles={"无职业","农夫","农妇","头人","猎户"};String[] marriages={"无婚姻","事实婚姻","法定婚姻"};g.drawString(font,(data.contains("Occupation")?data.getString("Occupation"):person.role.ordinal()<roles.length?roles[person.role.ordinal()]:person.role.name())+" | "+ages[person.age.ordinal()]+" | 银币 "+data.getLong("Silver"),left,34,0xffffffff);g.drawString(font,marriages[person.marriage.ordinal()]+" | "+(person.lover==null?"无恋爱关系":"恋爱中"),left,48,0xffeeeecc);g.drawString(font,data.getString("Family"),left,62,0xffeeeecc);g.drawString(font,"饱食储备 "+(int)person.nourishment+" / "+(int)person.maximum()+"；职业训练 "+person.trainedHours+"小时",left,76,0xffeeeecc);g.fill(left,94,left+330,102,0xff444444);g.fill(left,94,left+(int)(330*Math.min(1,person.stageTicks/1440000.0)),102,0xff77aa66);g.drawString(font,"阶段成长进度（暂不自动成长生育）",left,106,0xffaabb99);g.drawString(font,data.getBoolean("Enabled")?(setupReturn==null?"数据农村运行中":"本聚落居民循环运行中"):"未启用；先检查工作点、住房和储藏箱",left,122,0xffffdd77);g.drawString(font,font.plainSubstrByWidth(data.getString("Message"),330),left,136,0xffffaa66);super.render(g,x,y,tick);}
 public boolean isPauseScreen(){return false;}public void renderBackground(GuiGraphics g,int x,int y,float t){}
}



