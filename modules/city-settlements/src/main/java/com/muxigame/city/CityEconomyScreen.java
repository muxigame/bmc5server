package com.muxigame.city;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CityEconomyScreen extends Screen {
    private CompoundTag data;private int page;private boolean pending;private int wait;
    public CityEconomyScreen(CompoundTag data){super(Component.literal("城市经济"));this.data=data;}
    public void update(CompoundTag d){data=d;pending=false;rebuildWidgets();}
    private boolean market(){return data.getBoolean("Market");}
    private int rows(){return Math.max(1,(height-120)/(market()?44:16));}
    private void request(String op,String value){if(pending)return;pending=true;wait=100;PacketDistributor.sendToServer(new CityNetwork.Request(op,data.getString("Target"),data.getLong("Revision"),0,0,0,0,0,0,value,false));}
    private void button(String text,int x,int y,int w,Runnable task){addRenderableWidget(Button.builder(Component.literal(text),b->task.run()).bounds(x,y,w,20).build());}
    @Override protected void init(){
        button("操作说明",width-86,7,74,()->minecraft.setScreen(new CityHelpScreen(this)));
        int count=market()?data.getList("Offers",10).size():data.getList("Lines",8).size();page=Math.clamp(page,0,Math.max(0,(count-1)/rows()));
        if(market()){var offers=data.getList("Offers",10);for(int i=0;i<rows()&&page*rows()+i<offers.size();i++){
            var o=offers.getCompound(page*rows()+i);int y=58+i*44;String id=o.getString("Id");
            var buy=Button.builder(Component.literal("买1 / "+o.getLong("Buy")),b->request("econbuy",id)).bounds(width-188,y,84,20).build();buy.active=o.getBoolean("Enabled")&&o.getLong("Buy")>=0;addRenderableWidget(buy);
            var sell=Button.builder(Component.literal("卖1 / "+o.getLong("Sell")),b->request("econsell",id)).bounds(width-98,y,84,20).build();sell.active=o.getBoolean("Enabled")&&o.getLong("Sell")>=0;addRenderableWidget(sell);
        }}
        button("上一页",12,height-36,65,()->{page=Math.max(0,page-1);rebuildWidgets();});button("下一页",84,height-36,65,()->{page=Math.min(Math.max(0,(count-1)/rows()),page+1);rebuildWidgets();});
        button("刷新",156,height-36,65,()->request(market()?"econmarket":"econview",""));button("关闭",width-80,height-36,68,this::onClose);
    }
    @Override public void tick(){if(pending&&--wait<=0){pending=false;data.putString("Message","请求未确认，请刷新；不要重复扣款操作。");}}
    @Override public void render(GuiGraphics g,int x,int y,float p){
        g.fill(0,0,width,height,0xff101923);g.drawString(font,font.plainSubstrByWidth(data.getString("Title"),width-110),12,12,0xffedcf8c,false);
        g.drawString(font,market()?"你的余额："+data.getLong("Balance")+" 整币 | 每次交易1件；买卖立即执行":"真实库存 / 账户 / 生产 / 消费 · 第"+(page+1)+"页",12,34,0xffb4d8c1,false);
        if(market()){var offers=data.getList("Offers",10);for(int i=0;i<rows()&&page*rows()+i<offers.size();i++){var o=offers.getCompound(page*rows()+i);int yy=60+i*44;g.drawString(font,font.plainSubstrByWidth(display(o.getString("Item"))+" 库存："+o.getInt("Stock"),Math.max(40,width-210)),12,yy,0xffdce5e8,false);g.drawString(font,font.plainSubstrByWidth(o.getString("Status"),width-24),12,yy+20,0xffffc985,false);}}
        else{var lines=data.getList("Lines",8);for(int i=0;i<rows()&&page*rows()+i<lines.size();i++)g.drawString(font,font.plainSubstrByWidth(display(lines.getString(page*rows()+i)),width-24),12,58+i*16,0xffdce5e8,false);}
        g.drawString(font,font.plainSubstrByWidth(data.getString("Message"),width-24),12,height-54,0xffffc985,false);super.render(g,x,y,p);
    }
    private String display(String value){var matcher=java.util.regex.Pattern.compile("[a-z0-9_.-]+:[a-z0-9_/.-]+").matcher(value);var out=new StringBuffer();while(matcher.find()){var key=net.minecraft.resources.ResourceLocation.tryParse(matcher.group());String label=key!=null&&net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(key)?net.minecraft.core.registries.BuiltInRegistries.ITEM.get(key).getDescription().getString():matcher.group();matcher.appendReplacement(out,java.util.regex.Matcher.quoteReplacement(label));}matcher.appendTail(out);return out.toString();}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float p){}
    @Override public boolean isPauseScreen(){return false;}
}
