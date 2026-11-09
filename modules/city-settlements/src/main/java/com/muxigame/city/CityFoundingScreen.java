package com.muxigame.city;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** The same temporary-height editor is reachable from the surveying rod and founding charter. */
public final class CityFoundingScreen extends Screen {
    private CompoundTag selection;private final boolean founding;private final boolean building;private EditBox name,bottom,top;private String message="";private Button register,apply,automatic,include,exclude;private int wait;private boolean pending;
    public CityFoundingScreen(CompoundTag data){super(Component.literal("选区高度"));selection=data;founding=data.getBoolean("Founding");building=data.getBoolean("BuildingDraft");message=data.getString("Message");}
    private int left(){return (width-320)/2;}private int upper(){return (height-240)/2;}
    @Override protected void init(){int x=left(),y=upper();String draft=name==null?"新聚落":name.getValue();
        bottom=addRenderableWidget(new EditBox(font,x+12,y+78,140,18,Component.literal("最低Y")));top=addRenderableWidget(new EditBox(font,x+168,y+78,140,18,Component.literal("最高Y")));bottom.setMaxLength(12);top.setMaxLength(12);bottom.setValue(""+selection.getInt("Bottom"));top.setValue(""+selection.getInt("Top"));
        apply=addRenderableWidget(Button.builder(Component.literal("应用手动高度"),b->apply(false)).bounds(x+12,y+102,140,20).build());automatic=addRenderableWidget(Button.builder(Component.literal("自动识别高度"),b->apply(true)).bounds(x+168,y+102,140,20).build());
        if(founding){name=addRenderableWidget(new EditBox(font,x+12,y+143,296,18,Component.literal("聚落名称")));name.setMaxLength(32);name.setValue(draft);register=addRenderableWidget(Button.builder(Component.literal("新建城市"),b->send("found",0,0,name.getValue())).bounds(x+12,y+169,94,20).build());include=addRenderableWidget(Button.builder(Component.literal("纳入城市"),b->area(true)).bounds(x+113,y+169,94,20).build());exclude=addRenderableWidget(Button.builder(Component.literal("排除城市"),b->area(false)).bounds(x+214,y+169,94,20).build());}
        if(building)register=addRenderableWidget(Button.builder(Component.literal("进入建筑登记"),b->send("buildingform",0,0,"")).bounds(x+12,y+163,296,20).build());
        addRenderableWidget(Button.builder(Component.literal("返回现场"),b->onClose()).bounds(x+90,y+193,140,20).build());if(founding)setInitialFocus(name);buttons();
    }
    private boolean dirty(){return !bottom.getValue().equals(""+selection.getInt("Bottom"))||!top.getValue().equals(""+selection.getInt("Top"));}
    private void buttons(){if(apply==null)return;apply.active=!pending;automatic.active=!pending;if(register!=null)register.active=!pending&&selection.getBoolean("Valid")&&!dirty();if(include!=null){include.active=exclude.active=!pending&&selection.getBoolean("Valid")&&!dirty()&&selection.contains("CityId");}}
    private void area(boolean add){if(pending)return;pending=true;wait=100;buttons();PacketDistributor.sendToServer(new CityNetwork.Request(add?"areaadd":"areacut",selection.getString("CityId"),selection.getLong("SelectionRevision"),0,0,0,0,0,0,""+selection.getLong("CityRevision"),true));}
    private void send(String op,int low,int high,String value){if(pending)return;pending=true;wait=100;buttons();PacketDistributor.sendToServer(new CityNetwork.Request(op,"",selection.getLong("SelectionRevision"),low,high,0,0,0,0,value,founding));}
    private void apply(boolean auto){try{send(building?"buildingheight":"height",auto?0:Integer.parseInt(bottom.getValue().trim()),auto?0:Integer.parseInt(top.getValue().trim()),auto?"auto":"manual");}catch(NumberFormatException e){message="最低Y和最高Y必须是整数。";}}
    public void update(CompoundTag data){if(data.getBoolean("Founded")){onClose();return;}pending=false;wait=0;message=data.getString("Message");if(data.getBoolean("SelectionEditor")){selection=data;if(bottom!=null){bottom.setValue(""+data.getInt("Bottom"));top.setValue(""+data.getInt("Top"));}}buttons();}
    @Override public void tick(){if(wait>0&&--wait==0){pending=false;message="请求超时，请重新应用高度。";}buttons();}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){int x=left(),y=upper();g.fill(x,y,x+320,y+240,0xf0182735);
        g.drawString(font,(building?"建筑魔杖 · ":"城市魔杖 · ")+(selection.contains("CityName")?selection.getString("CityName"):"尚未选择城市"),x+12,y+10,0xffe4dbc0,false);
        g.drawString(font,selection.getString("CornerA")+" → "+selection.getString("CornerB"),x+12,y+29,0xffa8bdc7,false);
        g.drawString(font,selection.getBoolean("LargeAutoHeight")?"大型区域：从所选地面包含至世界最高处":selection.getBoolean("Automatic")?"自动：从地面包含至最高方块（含树木）":"手动：按指定上下高度选择",x+12,y+47,0xff8bd6cc,false);
        g.drawString(font,"最低 Y（可包含地下）",x+12,y+65,0xffa8bdc7,false);g.drawString(font,"最高 Y（屋顶 / 楼层）",x+168,y+65,0xffa8bdc7,false);
        g.drawString(font,selection.getBoolean("Valid")?"当前生效：Y "+selection.getInt("Bottom")+" 至 "+selection.getInt("Top")+" · "+selection.getLong("Volume")+" 格":"自动范围不可用，请手动调整或缩小选区",x+12,y+127,0xffe4dbc0,false);
        if(!founding)g.drawString(font,building?"只登记建筑，不改变城市范围。":"填写城市名称，或应用到当前城市。",x+12,y+149,0xffa8bdc7,false);
        String note=dirty()?"已修改输入：请先点击应用手动高度。":message;int ty=y+218;for(var line:font.split(Component.literal(note),296)){if(ty>y+229)break;g.drawString(font,line,x+12,ty,0xffffbc77,false);ty+=11;}super.render(g,mx,my,partial);
    }
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    @Override public boolean isPauseScreen(){return false;}
}

