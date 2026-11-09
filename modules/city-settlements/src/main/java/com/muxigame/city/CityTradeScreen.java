package com.muxigame.city;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.network.PacketDistributor;
public final class CityTradeScreen extends Screen {
 CompoundTag data;boolean settings;EditBox[] fields=new EditBox[6];CityTradeScreen(CompoundTag n){super(Component.literal("一城一村 · 有限经济"));data=n;}
 void update(CompoundTag n){data=n;}
 protected void init(){addRenderableWidget(Button.builder(Component.literal(settings?"保存参数":"刷新账目"),b->{if(settings)save();else send("tradeview");}).bounds(width/2-160,height-35,100,20).build());addRenderableWidget(Button.builder(Component.literal(settings?"返回账目":"经济参数"),b->{settings=!settings;rebuildWidgets();}).bounds(width/2-50,height-35,100,20).build());addRenderableWidget(Button.builder(Component.literal("返回游戏"),b->onClose()).bounds(width/2+60,height-35,100,20).build());if(settings){var c=CityEconomy.JSON.fromJson(data.getString("Config"),CityTrade.Config.class);for(int i=0;i<6;i++){fields[i]=new EditBox(font,width/2+25,42+i*25,95,20,Component.literal("参数"));fields[i].setValue(Double.toString(i==0?c.cityMultiplier:i==1?c.perceptionError:c.targets[i-2]));addRenderableWidget(fields[i]);}}}
 void save(){try{var c=CityEconomy.JSON.fromJson(data.getString("Config"),CityTrade.Config.class);c.cityMultiplier=Double.parseDouble(fields[0].getValue());c.perceptionError=Double.parseDouble(fields[1].getValue());for(int i=0;i<4;i++)c.targets[i]=Double.parseDouble(fields[i+2].getValue());CityTrade.validate(c);PacketDistributor.sendToServer(new CityNetwork.Request("tradeconfig","",data.getLong("ConfigRevision"),0,0,0,0,0,0,CityEconomy.JSON.toJson(c),false));}catch(IllegalArgumentException ex){data.putString("Message","请检查参数范围和数字");}}
 void send(String op){PacketDistributor.sendToServer(new CityNetwork.Request(op,"",0,0,0,0,0,0,0,"",false));}
 public boolean isPauseScreen(){return false;}
 public void render(GuiGraphics g,int x,int y,float partial){renderBackground(g,x,y,partial);g.drawCenteredString(font,title,width/2,15,0xffffff);if(settings){String[] labels={"城市标准价倍率","商人估价误差（0.1=10%）","每人口正常小麦量","每人口正常面包量","每人口正常画数量","每人口正常书数量"};for(int i=0;i<6;i++)g.drawString(font,labels[i],width/2-170,48+i*25,0xdddddd);}else{int py=40;for(String line:("状态："+data.getString("Status")+"  已运行 "+String.format(java.util.Locale.ROOT,"%.2f",data.getDouble("Days"))+" 日\n"+data.getString("Rows")+"\n详细记录保存在服务器 finite-economy 文件夹").split("\n")){g.drawString(font,line,Math.max(12,width/2-210),py,0xdddddd);py+=14;if(py>height-65)break;}}g.drawCenteredString(font,data.getString("Message"),width/2,height-55,0xffbb55);super.render(g,x,y,partial);}
}
