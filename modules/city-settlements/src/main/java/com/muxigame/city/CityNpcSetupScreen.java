package com.muxigame.city;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Four plain-language checks with links, not another collection of economic parameters. */
public final class CityNpcSetupScreen extends Screen {
 private CompoundTag data;private int page,scroll;private boolean pending;private int wait;private String local="";
 public CityNpcSetupScreen(CompoundTag t){super(Component.literal("职业NPC · 运行条件"));data=t;page=t.getInt("Page");}
 public void update(CompoundTag t){data=t;if(page!=t.getInt("Page"))scroll=0;page=t.getInt("Page");pending=false;local="";rebuildWidgets();}
 public String npc(){return data.getString("Npc");}
 void send(String op,String target){if(pending)return;pending=true;wait=100;var value=new JsonObject();value.add("template",com.google.gson.JsonParser.parseString(data.getString("Template")));value.addProperty("target",target);PacketDistributor.sendToServer(new CityNetwork.Request(op,data.getString("City"),data.getLong("Token"),0,0,0,0,0,page,value.toString(),false));}
 public void resume(){pending=false;minecraft.setScreen(this);send("careerstatus","");}
 private Button button(String text,int x,int y,int width,Runnable run){return addRenderableWidget(Button.builder(Component.literal(text),b->{if(!pending)run.run();}).bounds(x,y,width,20).build());}
 private int left(){return Math.max(12,(width-500)/2);}private int usable(){return Math.min(500,width-24);}private int rows(){return Math.max(1,(height-170)/48);}
 private String key(){return page==1?"Houses":page==2?"Boxes":"Accounts";}
 protected void init(){button("极简帮助",width-86,7,74,()->minecraft.setScreen(CityHelpScreen.quick(this,"职业魔杖 · 极简")));int x=left(),w=usable();
  if(page==0){var checks=data.getList("Checks",10);int gap=Math.min(59,Math.max(48,(height-112)/4));for(int i=0;i<checks.size();i++){var row=checks.getCompound(i);String op=switch(i){case 0->"careerhomes";case 1->"careerstores";case 2->"careerfunds";default->"careerrun";};button(row.getString("Action"),x+w-104,60+i*gap,104,()->send(op,"")).active=!data.getString("City").isEmpty();}}
  else if(page==4){button("打开居民 / 主粮循环设置",x,103,w,()->send("careercycle","")).active=!npc().isEmpty();button("打开城市维护设置",x,136,w,()->send("careermaint","")).active=!data.getString("City").isEmpty();}
  else{var list=data.getList(key(),10);scroll=Math.clamp(scroll,0,Math.max(0,(list.size()-1)/rows()));for(int i=0;i<rows()&&scroll*rows()+i<list.size();i++){var row=list.getCompound(scroll*rows()+i);int y=85+i*48;if(page==1)button("住户设置",x+w-104,y,104,()->send("careerhouse",row.getString("Id")));else if(page==2)button("关联此粮箱",x+w-104,y,104,()->send("careerboxbind",row.getString("Pos"))).active=row.getBoolean("CanBind");else button("账户命令…",x+w-104,y,104,()->account(row)).active=data.getBoolean("CanEditMoney");}
   button("上一页",x,height-69,75,()->{scroll=Math.max(0,scroll-1);rebuildWidgets();});button("下一页",x+82,height-69,75,()->{scroll=Math.min(Math.max(0,(list.size()-1)/rows()),scroll+1);rebuildWidgets();});if(page==1)button("打开房屋登记",x+165,height-69,w-165,()->send("careerreg",""));}
  button(page==0?"返回职业设计":"返回条件总览",x,height-33,180,()->{if(page==0)send("careerback","");else{page=0;send("careerstatus","");}});button("刷新检查",x+188,height-33,110,()->send("careerstatus",""));button("关闭",x+w-94,height-33,94,()->minecraft.setScreen(null));
 }
 private void account(CompoundTag row){String command=CityEconomyAccess.accountCommand(row.getString("Id"),row.getLong("Balance"));var parent=this;pending=false;minecraft.setScreen(new ChatScreen(command){public void onClose(){parent.resume();}public boolean keyPressed(int key,int scan,int modifiers){boolean used=super.keyPressed(key,scan,modifiers);if((key==257||key==335||key==256)&&minecraft.screen==null)parent.resume();return used;}});}
 public void tick(){if(pending&&--wait<=0){pending=false;local="请求未确认，请刷新检查；不要重复执行关联操作。";}}
 public void onClose(){if(page==0)send("careerback","");else{page=0;send("careerstatus","");}}
 public void render(GuiGraphics g,int mx,int my,float partial){int x=left(),w=usable();g.fill(0,0,width,height,0xff242924);g.drawCenteredString(font,title,width/2,12,0xfff4dfab);g.drawString(font,font.plainSubstrByWidth(data.getString("Name")+" · "+data.getString("CityName"),w),x,33,0xffc9d7c3);
  if(page==0){var checks=data.getList("Checks",10);int gap=Math.min(59,Math.max(48,(height-112)/4));for(int i=0;i<checks.size();i++){var r=checks.getCompound(i);int y=57+i*gap,level=r.getInt("Level");int color=level==3?0xff98d88c:level==1?0xffffb98c:0xffded08c;String state=level==3?"已具备":level==1?"缺少":level==2?"需留意":"待配置";g.drawString(font,r.getString("Title")+" · "+state,x,y,color);g.drawWordWrap(font,Component.literal(r.getString("Detail")),x,y+14,w-115,0xffddddcc);}}
  else if(page==4){g.drawWordWrap(font,Component.literal(data.getList("Checks",10).getCompound(3).getString("Detail")),x,57,w,0xffded08c);g.drawWordWrap(font,Component.literal("两个开关互不代替。打开设置不会启动；请在原设置中确认后操作。"),x,174,w,0xffc3d0bd);if(npc().isEmpty())g.drawWordWrap(font,Component.literal("尚未选中NPC：先返回职业设计创建或选择NPC，再打开居民循环设置。"),x,211,w,0xffffc68b);}
  else{String hint=page==1?"打开房屋后已定位该NPC；点击住户条目登记入住。":page==2?"只关联专用NPC储藏箱；缺箱先摆放，再刷新。关联后需实际放入食物。":"这里不发钱。账户命令需OP手动修改余额并发送，会沿用原审计记录。";g.drawWordWrap(font,Component.literal(hint),x,53,w,0xffded08c);var list=data.getList(key(),10);for(int i=0;i<rows()&&scroll*rows()+i<list.size();i++){var r=list.getCompound(scroll*rows()+i);int y=85+i*48;g.drawString(font,font.plainSubstrByWidth(r.getString("Name"),w-115),x,y,0xffeeeecc);g.drawWordWrap(font,Component.literal(page==3?"当前余额："+r.getLong("Balance")+"银币":r.getString("Detail")),x,y+15,w-115,0xffc3d0bd);}if(list.isEmpty())g.drawWordWrap(font,Component.literal(page==1?"还没有登记房屋；请用建筑魔杖圈定后进入房屋登记。":page==2?"尚无专用NPC储藏箱记录，请在本聚落摆放NPC储藏箱。":"尚无可查看账户，请先创建或选中已登记NPC。"),x,96,w,0xffffc68b);}
  String message=local.isEmpty()?data.getString("Message"):local;g.drawString(font,font.plainSubstrByWidth(message,w),x,height-48,0xffffc68b);super.render(g,mx,my,partial);
 }
 public boolean isPauseScreen(){return false;}public void renderBackground(GuiGraphics g,int x,int y,float partial){}
}
