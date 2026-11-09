package com.muxigame.monologue;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DialogueScreen extends Screen {
    private final Screen parent;
    private DialogueRules rules;
    private Situation situation=Situation.HURT;
    private String channel="head",message="正在读取服务器台词库…";
    private int index;
    private boolean admin,settings;
    private EditBox text,duration,cooldown,offset;
    public DialogueScreen(Screen parent){super(Component.literal("独白与心理"));this.parent=parent;}
    public void update(CompoundTag t){rules=DialogueRules.load(t,null);admin=t.getBoolean("admin");message=t.getString("message");if(minecraft!=null)init();}
    private List<String> lines(){return rules.entries.get(situation).lines(channel);}
    private void send(String op,String value){PacketDistributor.sendToServer(new DialogueNetwork.Request(op,situation.id,channel,index,value,rules.revision));}
    private Button button(String label,int x,int y,int w,boolean enabled,Runnable action){var b=Button.builder(Component.literal(label),v->action.run()).bounds(x,y,w,20).build();b.active=enabled;addRenderableWidget(b);return b;}
    private EditBox field(int x,int y,int w,String value,int limit,boolean enabled){var b=new EditBox(font,x,y,w,20,Component.literal("编辑值"));b.setMaxLength(limit);b.setValue(value);b.setEditable(enabled);addRenderableWidget(b);return b;}
    @Override protected void init(){
        clearWidgets();int w=Math.min(390,width-24),x=(width-w)/2;
        button("返回",width/2-40,height-24,80,true,this::onClose);
        if(rules==null)return;
        button(settings?"台词编辑":"显示设置",x+w-90,10,90,true,()->{settings=!settings;init();});
        int eventWidth=(w-8)/3;
        for(var s:Situation.values())button((s==situation?"● ":"")+s.label,x+s.ordinal()*(eventWidth+4),38,eventWidth,true,()->{situation=s;index=0;init();});
        if(settings){
            var entry=rules.entries.get(situation);
            duration=field(x+110,66,w-190,Integer.toString(entry.duration),4,admin);
            button("保存",x+w-70,66,70,admin,()->send("duration",duration.getValue()));
            cooldown=field(x+110,92,w-190,Integer.toString(entry.cooldown),4,admin);
            button("保存",x+w-70,92,70,admin,()->send("cooldown",cooldown.getValue()));
            offset=field(x+110,118,w-190,Integer.toString(rules.thoughtOffset),3,admin);
            button("保存",x+w-70,118,70,admin,()->send("offset",offset.getValue()));
            button("系统："+(rules.enabled?"开":"关"),x,146,eventWidth,admin,()->send("enabled",""));
            button("头顶："+(rules.headEnabled?"开":"关"),x+eventWidth+4,146,eventWidth,admin,()->send("headEnabled",""));
            button("心理："+(rules.thoughtEnabled?"开":"关"),x+2*(eventWidth+4),146,eventWidth,admin,()->send("thoughtEnabled",""));
            button("恢复默认",x,172,(w-4)/2,admin,()->send("reset",""));
            button("撤销修改",x+(w+4)/2,172,(w-4)/2,admin,()->send("undo",""));
        }else{
            button("头顶独白（附近可见）",x,64,(w-4)/2,true,()->{channel="head";index=0;init();});
            button("心理活动（仅本人）",x+(w+4)/2,64,(w-4)/2,true,()->{channel="thought";index=0;init();});
            index=Math.clamp(index,0,Math.max(0,lines().size()-1));
            button("上一条",x,88,70,!lines().isEmpty(),()->{index=Math.floorMod(index-1,lines().size());init();});
            button("下一条",x+w-70,88,70,!lines().isEmpty(),()->{index=(index+1)%lines().size();init();});
            text=field(x,112,w,lines().isEmpty()?"":lines().get(index),DialogueRules.MAX_TEXT,admin);
            button("保存此条",x,138,eventWidth,admin&&!lines().isEmpty(),()->send("replace",text.getValue()));
            button("新增此条",x+eventWidth+4,138,eventWidth,admin,()->{send("add",text.getValue());index=lines().size();});
            button("删除此条",x+2*(eventWidth+4),138,eventWidth,admin&&!lines().isEmpty(),()->send("delete",""));
            button("预览此情境（关闭界面后显示）",x,164,w,rules.enabled,()->{send("preview","");minecraft.setScreen(null);});
        }
    }
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        g.fill(0,0,width,height,0xff17202a);int w=Math.min(390,width-24),x=(width-w)/2;
        g.drawString(font,"独白与心理",x,16,0xffeedbb0);
        if(rules!=null){
            if(settings){
                g.drawString(font,"显示时长 / 刻",x,72,0xffd8e1e9);g.drawString(font,"情境冷却 / 刻",x,98,0xffd8e1e9);g.drawString(font,"心理向下偏移",x,124,0xffd8e1e9);
            }else{
                String position=(channel.equals("head")?"头顶独白":"心理活动")+"  "+(lines().isEmpty()?"空列表":(index+1)+" / "+lines().size());
                g.drawCenteredString(font,position,width/2,94,0xffeedbb0);
            }
            String info=message.isEmpty()?(admin?(settings?"20刻=1秒；显示20–200刻，冷却20–1200刻。":"可自定义服务器台词；每组最多12条，随机轮换。"):"只读：修改台词需要管理员权限等级2。") : message;
            int y=settings?198:190;
            var lines=font.split(Component.literal(info),w);for(int i=0;i<Math.min(2,lines.size());i++)g.drawString(font,lines.get(i),x,y+10*i,0xffadbfce,false);
        }else g.drawString(font,message,x,44,0xffadbfce);
        super.render(g,mx,my,dt);
    }
    @Override public void renderBackground(GuiGraphics g,int x,int y,float dt){}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
