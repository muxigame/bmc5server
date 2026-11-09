package com.muxigame.city;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Explicit list selection, with UUID targets captured before confirmation. */
public final class CityRegisterScreen extends Screen {
    private CompoundTag data;private final int kind;private EditBox name;private int type,page,wait;private boolean nearby,pending;private String selected="",message="";
    private final CityNpcSetupScreen setupReturn; CityNpcSetupScreen setupParent(){return setupReturn;}
    public CityRegisterScreen(CompoundTag data,int kind){this(data,kind,null);} public CityRegisterScreen(CompoundTag data,int kind,CityNpcSetupScreen back){super(Component.literal(new String[]{"建筑登记","NPC绑定","仓库绑定"}[kind]));this.data=data;this.kind=kind;setupReturn=back;}
    public void update(CompoundTag tag){if(!data.getString("Id").equals(tag.getString("Id"))){selected="";page=0;}data=tag;pending=false;message=tag.getString("Message");rebuildWidgets();}
    private String key(){return kind==0?"Buildings":kind==2?"Warehouses":nearby?"NearbyNpcs":"ResidentList";}
    private int rows(){return Math.max(1,(height-154)/32);}
    private void send(String op,String value){if(pending||!data.contains("Id"))return;pending=true;wait=100;BlockPos p=data.contains("TargetContainer")?BlockPos.of(data.getLong("TargetContainer")):BlockPos.ZERO;boolean warehouse=op.equals("wareadd");PacketDistributor.sendToServer(new CityNetwork.Request(op,data.getString("Id"),data.getLong("Revision"),warehouse?p.getX():data.getInt("X"),warehouse?p.getY():data.getInt("Y"),warehouse?p.getZ():data.getInt("Z"),0,0,(int)data.getLong("BuildingSelectionRevision"),value,data.getBoolean("Surface")));}
    private Button button(String label,int x,int y,int w,Runnable action){return addRenderableWidget(Button.builder(Component.literal(label),b->action.run()).bounds(x,y,w,20).build());}
    @Override protected void init(){
        String draft=name==null?"":name.getValue();if(kind!=1){name=addRenderableWidget(new EditBox(font,12,35,128,18,Component.literal("登记名称")));name.setMaxLength(32);name.setValue(draft);name.setHint(Component.literal("填写登记名称"));}
        if(kind==0)button(CityRegistry.TYPES.get(type),146,34,64,()->{type=(type+1)%CityRegistry.TYPES.size();rebuildWidgets();});
        if(kind==1)button(nearby?"查看已绑定":"选择附近NPC",12,34,128,()->{nearby=!nearby;selected="";page=0;rebuildWidgets();send("view","");});
        button(kind==0?"登记当前选区":kind==2?"绑定指向容器":"绑定选中NPC",kind==1?146:216,34,100,()->send(kind==0?"buildingadd":kind==2?"wareadd":"residentadd",kind==1?selected:kind==0?name.getValue()+"|"+CityRegistry.TYPES.get(type):name.getValue())).active=data.getBoolean("Editable")&&!pending&&(kind==0?data.getBoolean("BuildingSelection"):kind==2?data.contains("TargetContainer"):nearby&&!selected.isEmpty());
        ListTag list=data.getList(key(),Tag.TAG_COMPOUND);int pages=Math.max(1,(list.size()+rows()-1)/rows());page=Math.clamp(page,0,pages-1);
        for(int i=0;i<rows()&&page*rows()+i<list.size();i++){CompoundTag entry=list.getCompound(page*rows()+i);String id=entry.getString("Id");button((selected.equals(id)?"✓ ":"")+font.plainSubstrByWidth(entry.getString("Name"),width-52),12,85+i*32,width-24,()->{selected=id;rebuildWidgets();});}
        if(kind==0)button("打开所选房屋属性 / 建材市场",12,60,width-24,()->PacketDistributor.sendToServer(new CityNetwork.Request("houseview",data.getString("Id"),0,0,0,0,0,0,0,selected,false))).active=!selected.isEmpty()&&data.getBoolean("Editable");
        button("上一页",12,height-57,66,()->{page=Math.max(0,page-1);selected="";rebuildWidgets();});button("下一页",84,height-57,66,()->{page=Math.min(pages-1,page+1);selected="";rebuildWidgets();});
        button("解除选中登记",156,height-57,100,this::remove).active=!selected.isEmpty()&&!nearby&&data.getBoolean("Editable")&&!pending;
        button("刷新",width-158,height-29,66,()->send("view",""));button("城市信息",width-86,height-29,74,this::onClose);
    }
    private void remove(){String id=selected,city=data.getString("Id");long revision=data.getLong("Revision");String label="";for(Tag raw:data.getList(key(),Tag.TAG_COMPOUND)){CompoundTag t=(CompoundTag)raw;if(t.getString("Id").equals(id))label=t.getString("Name");}
        minecraft.setScreen(new ConfirmScreen(ok->{minecraft.setScreen(this);if(ok){pending=true;wait=100;PacketDistributor.sendToServer(new CityNetwork.Request(kind==0?"buildingremove":kind==2?"wareremove":"residentremove",city,revision,0,0,0,0,0,0,id,false));selected="";}},Component.literal("解除登记："+label+"？"),Component.literal("只解除城市登记，保留建筑、容器物品与NPC实体。")));
    }
    @Override public void tick(){if(pending&&--wait<=0){pending=false;message="请求超时，请刷新。";rebuildWidgets();}}
    @Override public void onClose(){if(setupReturn!=null){setupReturn.resume();return;}minecraft.setScreen(new CityDashboardScreen(data));}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){g.fill(0,0,width,height,0xff101923);g.drawString(font,getTitle().getString()+" · "+data.getString("Name"),12,12,0xffedcf8c,false);
        String hint=kind==0?"建筑魔杖选两角后登记；用途按钮可切换。":kind==1?"选择明确的NPC；未加载居民保留绑定，可按UUID解绑。":"面对容器打开信息魔杖；目标："+data.getString("TargetLabel");g.drawString(font,font.plainSubstrByWidth(hint,width-24),12,62,0xffa8bdc7,false);
        ListTag list=data.getList(key(),Tag.TAG_COMPOUND);for(int i=0;i<rows()&&page*rows()+i<list.size();i++)g.drawString(font,font.plainSubstrByWidth(list.getCompound(page*rows()+i).getString("Detail"),width-24),12,107+i*32,0xffa8bdc7,false);
        if(list.isEmpty())g.drawString(font,"暂无记录",12,90,0xffa8bdc7,false);g.drawString(font,(page+1)+" / "+Math.max(1,(list.size()+rows()-1)/rows()),264,height-51,0xffa8bdc7,false);
        g.drawString(font,font.plainSubstrByWidth(message.isEmpty()?"数据入口：/citydata export、apply（支持全部登记）":message,width-180),12,height-23,0xffffc985,false);super.render(g,mx,my,partial);
    }
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    @Override public boolean isPauseScreen(){return false;}
}
