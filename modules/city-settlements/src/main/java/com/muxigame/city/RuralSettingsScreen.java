package com.muxigame.city;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.network.PacketDistributor;
public final class RuralSettingsScreen extends Screen {
 CompoundTag data;EditBox target,low,high,up,down,speed;boolean buyer,enabled;int left;
 public RuralSettingsScreen(CompoundTag t){super(Component.literal("头人库存与村庄模拟"));update(t);}
 void update(CompoundTag t){data=t;if(minecraft!=null)rebuildWidgets();}
 EditBox field(String text,int x,int y){var box=new EditBox(font,x,y,85,18,Component.literal(text));box.setValue(text);addRenderableWidget(box);return box;}
 void button(String text,int x,int y,int w,Runnable r){addRenderableWidget(Button.builder(Component.literal(text),b->{r.run();if(text.startsWith("价格浮动"))b.setMessage(Component.literal(enabled?"价格浮动：开启":"价格浮动：关闭"));if(text.startsWith("无限城商"))b.setMessage(Component.literal(buyer?"无限城商：开启":"无限城商：关闭"));}).bounds(x,y,w,20).build());}
 protected void init(){left=width/2-170;var p=CityEconomy.JSON.fromJson(data.getString("Policy"),RuralMarket.Policy.class);buyer=data.getBoolean("Buyer");enabled=p.enabled;
  target=field(""+p.target,left+240,65);low=field(""+p.low,left+240,90);high=field(""+p.high,left+240,115);up=field(""+p.markup,left+240,140);down=field(""+p.discount,left+240,165);
  button("上一货品",left,35,95,()->send("marketview","",(data.getInt("Good")+11)%12,0));button("下一货品",left+235,35,95,()->send("marketview","",(data.getInt("Good")+1)%12,0));
  button(enabled?"价格浮动：开启":"价格浮动：关闭",left,190,160,()->{enabled=!enabled;});button("保存货品设置",left+170,190,160,()->{try{p.target=Integer.parseInt(target.getValue());p.low=Integer.parseInt(low.getValue());p.high=Integer.parseInt(high.getValue());p.markup=Integer.parseInt(up.getValue());p.discount=Integer.parseInt(down.getValue());p.enabled=enabled;send("marketsave",CityEconomy.JSON.toJson(p),data.getInt("Good"),0);}catch(Exception ignored){}});
  speed=field(""+data.getInt("Speed"),left+240,250);button("100倍",left+175,250,55,()->{speed.setValue("100");send("simconfig","",data.getInt("Good"),100);});button(buyer?"无限城商：开启":"无限城商：关闭",left,275,160,()->{buyer=!buyer;});button("保存模拟设置",left+170,275,160,()->{try{send("simconfig","",data.getInt("Good"),Integer.parseInt(speed.getValue()));}catch(Exception ignored){}});
  button("恢复货品默认",left,height-30,160,()->send("marketreset","",data.getInt("Good"),0));button("关闭",left+170,height-30,160,this::onClose);
 }
 void send(String op,String value,int x,int y){PacketDistributor.sendToServer(new CityNetwork.Request(op,data.getString("Npc"),data.getLong("Revision"),x,y,0,0,0,0,value,buyer));}
 public void render(GuiGraphics g,int x,int y,float f){g.fill(0,0,width,height,0xff242921);g.drawCenteredString(font,"头人职业 · 库存预期与模拟",width/2,10,0xfff4d99c);g.drawCenteredString(font,RuralMarket.NAMES[data.getInt("Good")],width/2,40,0xffffffff);String[] labels={"预期库存（份）","缺货加价线（目标%）","过量压价线（目标%）","最高加价（%）","最大压价（%）"};for(int i=0;i<labels.length;i++)g.drawString(font,labels[i],left,70+i*25,0xffffffff);g.drawString(font,"当前库存 "+data.getInt("Stock")+"；收购报价 "+data.getInt("Quote")/10.0+" 银币",left,220,0xffffdd88);g.drawString(font,"模拟速度（1—100倍）",left,255,0xffffffff);g.drawString(font,"城商九折采购；收购价保本；保留本地两餐缺口。",left,305,0xffcccccc);g.drawString(font,font.plainSubstrByWidth(data.getString("Message"),340),left,320,0xffffcc77);super.render(g,x,y,f);}
 public boolean isPauseScreen(){return false;}public void renderBackground(GuiGraphics g,int x,int y,float f){}
}
