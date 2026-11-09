package com.muxigame.city;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class WorkHoursScreen extends Screen {
    CompoundTag data;WorkHours.Config config;final Screen parent;EditBox start,end,nightStart,nightEnd,chance,radius;int left;String message="";
    public WorkHoursScreen(CompoundTag t,Screen parent){super(Component.literal("个人工作时间"));this.parent=parent;load(t);}
    void load(CompoundTag t){data=t;config=CityEconomy.JSON.fromJson(t.getString("Config"),WorkHours.Config.class);message=t.getString("Message");}
    void update(CompoundTag t){load(t);rebuildWidgets();}
    EditBox field(String name,int value,int x,int y){var b=addRenderableWidget(new EditBox(font,x,y,150,20,Component.literal(name)));b.setMaxLength(3);b.setValue(""+value);return b;}
    void button(String s,int x,int y,int w,Runnable r){addRenderableWidget(Button.builder(Component.literal(s),b->r.run()).bounds(x,y,w,20).build());}
    protected void init(){left=width/2-160;start=field("白班开始",config.start,left,55);end=field("白班结束",config.end,left+170,55);nightStart=field("夜班开始",config.nightStart,left,95);nightEnd=field("夜班结束",config.nightEnd,left+170,95);chance=field("加班意愿",config.willingness,left,135);radius=field("猎户巡逻半径",data.getInt("Radius"),left+170,135);radius.active=data.getBoolean("Hunter");button("保存时间",left,height-30,100,this::save);button("职业默认值",left+110,height-30,100,()->send("workreset","",0));button("返回",left+220,height-30,100,this::onClose);if(data.getBoolean("Hunter"))button("保存巡逻半径",left+170,160,150,()->{try{send("workradius","",Integer.parseInt(radius.getValue()));}catch(Exception e){message="半径须整数64—256";}});}
    void save(){try{config.start=Integer.parseInt(start.getValue());config.end=Integer.parseInt(end.getValue());config.nightStart=Integer.parseInt(nightStart.getValue());config.nightEnd=Integer.parseInt(nightEnd.getValue());config.willingness=Integer.parseInt(chance.getValue());WorkHours.validate(config);send("worksave",CityEconomy.JSON.toJson(config),0);}catch(Exception e){message=e.getMessage();}}
    void send(String op,String value,int x){PacketDistributor.sendToServer(new CityNetwork.Request(op,data.getString("Npc"),data.getLong("Revision"),x,0,0,0,0,0,value,false));}
    public void render(GuiGraphics g,int x,int y,float tick){g.fill(0,0,width,height,0xff242b2d);g.drawCenteredString(font,"个人工作时间 · "+config.profession,width/2,14,0xffefdaad);String[] labels={"白班开始（0—23时）","白班结束（0—23时）","夜班开始（0—23时）","夜班结束（0—23时）","加班意愿（0—100%）","猎户半径（64—256格）"};for(int i=0;i<6;i++)g.drawString(font,labels[i],left+i%2*170,42+i/2*40,0xffeeeecc);if(height>270){g.drawString(font,"稳定度>50夜间停工；通宵后下一晚必须休息。",left,190,0xffbbbbbb);g.drawString(font,"转职会恢复默认时间与意愿；不重置夜班记录。",left,204,0xffbbbbbb);g.drawString(font,"猎户工作间距64格；回家不受限。",left,218,0xffbbbbbb);g.drawString(font,font.plainSubstrByWidth(data.getString("Status"),320),left,232,0xffaaddaa);}g.drawString(font,font.plainSubstrByWidth(message,320),left,height-45,0xffffcc88);super.render(g,x,y,tick);}
    public void onClose(){minecraft.setScreen(parent);}public boolean isPauseScreen(){return false;}public void renderBackground(GuiGraphics g,int x,int y,float d){}
}
