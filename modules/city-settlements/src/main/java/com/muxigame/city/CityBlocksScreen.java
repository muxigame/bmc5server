package com.muxigame.city;

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/** Dedicated, paginated material inventory, grouped by block registry ID. */
public final class CityBlocksScreen extends Screen {
    private CompoundTag data;private EditBox search;private int page,age,wait;private boolean pending;private String message="";
    public CityBlocksScreen(CompoundTag data){super(Component.literal("城市方块统计"));this.data=data;}
    public void update(CompoundTag incoming){if(!data.getString("Id").equals(incoming.getString("Id")))page=0;data=incoming;pending=false;if(!incoming.getString("Message").isEmpty())message=incoming.getString("Message");}
    private int rows(){return Math.max(1,(height-160)/25);}
    private String name(CompoundTag t){ResourceLocation id=ResourceLocation.tryParse(t.getString("Block"));return id!=null&&BuiltInRegistries.BLOCK.containsKey(id)?BuiltInRegistries.BLOCK.get(id).getName().getString():t.getString("Block");}
    private List<CompoundTag> entries(){String query=search==null?"":search.getValue().strip().toLowerCase(Locale.ROOT);List<CompoundTag> result=new ArrayList<>();for(Tag raw:data.getList("Counts",Tag.TAG_COMPOUND)){CompoundTag t=(CompoundTag)raw;if(query.isEmpty()||name(t).toLowerCase(Locale.ROOT).contains(query)||t.getString("Block").toLowerCase(Locale.ROOT).contains(query))result.add(t);}return result;}
    private void send(boolean refresh){if(pending||!data.contains("Id"))return;pending=true;wait=100;PacketDistributor.sendToServer(new CityNetwork.Request(refresh?"refresh":"view",data.getString("Id"),data.getLong("Revision"),data.getInt("X"),data.getInt("Y"),data.getInt("Z"),0,0,0,"",data.getBoolean("Surface")));}
    @Override protected void init(){String query=search==null?"":search.getValue();search=addRenderableWidget(new EditBox(font,12,39,Math.max(100,width-214),18,Component.literal("搜索方块名称或ID")));search.setMaxLength(128);search.setValue(query);search.setHint(Component.literal("搜索名称或ID"));search.setResponder(value->page=0);
        addRenderableWidget(Button.builder(Component.literal("重新统计"),b->send(true)).bounds(width-194,38,86,20).build());
        addRenderableWidget(Button.builder(Component.literal("返回设置"),b->onClose()).bounds(width-102,38,90,20).build());
        addRenderableWidget(Button.builder(Component.literal("上一页"),b->page=Math.max(0,page-1)).bounds(12,height-55,80,20).build());
        addRenderableWidget(Button.builder(Component.literal("下一页"),b->page=Math.min(Math.max(0,(entries().size()-1)/rows()),page+1)).bounds(width-92,height-55,80,20).build());
    }
    @Override public void added(){super.added();send(true);}
    @Override public void tick(){if(pending&&--wait<=0){pending=false;message="请求超时，请重新统计。";}if(!pending&&++age%20==0&&data.getBoolean("Scanning"))send(false);}
    @Override public void onClose(){minecraft.setScreen(new CityInfoScreen(data));}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){g.fill(0,0,width,height,0xff101923);g.drawString(font,font.plainSubstrByWidth("方块种类与数量 · "+data.getString("Name"),width-24),12,12,0xffedcf8c,false);
        String summary=data.contains("Id")?(data.getBoolean("HasStats")?data.getList("Counts",Tag.TAG_COMPOUND).size()+" 种 / "+data.getLong("NonAir")+" 块":"尚无统计结果")+(data.getBoolean("Scanning")?" · 正在扫描…":" · 快照 "+data.getLong("Age")+" 秒前"):"当前城市已删除或尚未选择";
        g.drawString(font,summary,12,67,0xffb4d8c1,false);g.drawString(font,"方块名称 / ID",12,84,0xffa8bdc7,false);g.drawString(font,"数量",width-58,84,0xffa8bdc7,false);
        List<CompoundTag> all=entries();int pages=Math.max(1,(all.size()+rows()-1)/rows());page=Math.clamp(page,0,pages-1);
        for(int i=0;i<rows()&&page*rows()+i<all.size();i++){CompoundTag t=all.get(page*rows()+i);int y=99+i*25;g.fill(10,y-2,width-10,y+22,i%2==0?0xff1b2b38:0xff14232e);g.drawString(font,font.plainSubstrByWidth(name(t),width-100),14,y,0xffe1e7df,false);g.drawString(font,font.plainSubstrByWidth(t.getString("Block"),width-100),14,y+11,0xff91abb9,false);g.drawString(font,""+t.getLong("Count"),width-58,y+5,0xffb4e19e,false);if(my>=y-2&&my<y+22&&mx>=10&&mx<width-10)g.renderTooltip(font,Component.literal(name(t)+" · "+t.getString("Block")),mx,my);}
        if(all.isEmpty())g.drawString(font,data.getBoolean("Scanning")?"正在统计，请稍候…":"没有符合条件的方块",12,103,0xffa8bdc7,false);
        g.drawCenteredString(font,(page+1)+" / "+pages,width/2,height-49,0xffc7d8df);
        String note=!message.isEmpty()?message:data.getLong("Unloaded")>0?"未加载 "+data.getLong("Unloaded")+" 格，统计不完整；靠近后重新统计。":"仅统计已纳入且已加载的非空气方块；相同方块ID合并。";
        g.drawString(font,font.plainSubstrByWidth(note,width-24),12,height-24,0xffffc985,false);super.render(g,mx,my,partial);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){page=Math.clamp(page+(dy<0?1:-1),0,Math.max(0,(entries().size()-1)/rows()));return true;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    @Override public boolean isPauseScreen(){return false;}
}
