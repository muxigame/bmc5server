package com.muxigame.buildingarchive;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

public final class TerrainScreen extends Screen {
    private CompoundTag data;private String message;private boolean pending;private int wait;private EditBox low,high;
    public TerrainScreen(CompoundTag data){super(Component.literal("地形改造"));this.data=data;message=data.getString("Message");}
    String label(Block b){return TerrainClientCatalog.label(b);}
    private int x(){return (width-390)/2;}private int y(){return Math.max(2,(height-268)/2);}
    private Button button(String label,int x,int y,int width,Runnable action,boolean active){var b=addRenderableWidget(Button.builder(Component.literal(label),v->action.run()).bounds(x,y,width,18).build());b.active=active&&!pending;return b;}
    private String blockLabel(String key){return label(TerrainTool.block(data.getString(key)));}
    @Override protected void init(){clearWidgets();int x=x(),y=y();String mode=data.getString("Mode");
        button("重新选区",x+10,y+27,110,()->send("terrain_select",""),true);button("返回现场看范围",x+126,y+27,126,this::onClose,true);button("操作说明",x+258,y+27,122,()->minecraft.setScreen(new Guide(this)),true);
        String[] ids={"replace","clean","fill"},names={"替换指定方块","清理植物/薄雪","填补空气"};
        for(int i=0;i<3;i++){String id=ids[i];button((mode.equals(id)?"✓ ":"")+names[i],x+10+i*125,y+78,120,()->send("terrain_mode",id),true);}
        button(font.plainSubstrByWidth("原方块："+blockLabel("Source"),171),x+10,y+103,181,()->minecraft.setScreen(new Picker(this,false)),mode.equals("replace"));
        button(font.plainSubstrByWidth("目标："+blockLabel("Target"),171),x+199,y+103,181,()->minecraft.setScreen(new Picker(this,true)),!mode.equals("clean"));
        low=addRenderableWidget(new EditBox(font,x+54,y+132,65,18,Component.literal("最低Y")));high=addRenderableWidget(new EditBox(font,x+173,y+132,65,18,Component.literal("最高Y")));low.setMaxLength(8);high.setMaxLength(8);low.setValue(""+data.getInt("Low"));high.setValue(""+data.getInt("High"));
        button("应用高度",x+246,y+132,134,()->send("terrain_height",low.getValue()+","+high.getValue()),data.getBoolean("Selected"));
        button("① 预览修改数量",x+10,y+169,181,()->send("terrain_preview",""),data.getBoolean("Selected"));
        button("② 确认修改 "+data.getInt("Count")+" 格",x+199,y+169,181,()->send("terrain_apply",""),data.getBoolean("Ready")&&data.getInt("Count")>0);
        button("撤销上次地形操作",x+10,y+194,370,()->minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes->{minecraft.setScreen(this);if(yes)send("terrain_undo","");},Component.literal("撤销上次地形操作？"),Component.literal("恢复上次修改的位置；后续已被改成其他方块的位置会跳过。"))),data.getBoolean("Undo"));
    }
    void send(String op,String value){if(pending)return;pending=true;wait=100;PacketDistributor.sendToServer(new ArchiveNetwork.Request(op,value,data.getLong("Revision"),0));init();}
    public void update(CompoundTag data){this.data=data;message=data.getString("Message");pending=false;wait=0;init();}
    @Override public void tick(){if(wait>0&&--wait==0){pending=false;message="请求超时，请重新打开工具。";init();}}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){int x=x(),y=y();g.fill(x,y,x+390,y+268,0xf0182735);g.drawString(font,title,x+10,y+10,0xffe4dbc0,false);g.drawString(font,"两角："+data.getString("A")+" → "+data.getString("B"),x+10,y+51,0xff8bd6cc,false);g.drawString(font,"范围："+data.getString("Size")+" · 上限32768格，每边128格",x+10,y+64,0xffd1dadc,false);g.drawString(font,"最低Y",x+10,y+137,0xffd1dadc,false);g.drawString(font,"最高Y",x+129,y+137,0xffd1dadc,false);g.drawString(font,"容器自动跳过；替换使用默认朝向；不产生掉落物。",x+10,y+156,0xffffbc77,false);
        String note=message.isEmpty()?"选择操作和方块 → 预览数量 → 核对范围 → 确认。":message;int yy=y+220;for(var line:font.split(Component.literal(note),370)){if(yy>y+254)break;g.drawString(font,line,x+10,yy,0xffffbc77,false);yy+=10;}super.render(g,mx,my,partial);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float p){}
    @Override public boolean isPauseScreen(){return false;}

    static final class Picker extends Screen {
        static final int PAGE_SIZE=12,INDEX_BUDGET=512,SEARCH_BUDGET=4096;
        static final long FRAME_BUDGET=2_000_000L,DEBOUNCE=120_000_000L;
        final TerrainScreen parent;final boolean target;EditBox search;String query="";int page;
        TerrainClientCatalog catalog;TerrainSearchIndex.Query<Block> job;
        List<TerrainSearchIndex.Entry<Block>> matches=List.of();
        final List<Button> rows=new ArrayList<>();final List<ItemStack> icons=new ArrayList<>();Button prev,next;
        boolean requested=true;long due;
        Picker(TerrainScreen parent,boolean target){super(Component.literal(target?"选择目标方块":"选择要替换的原方块"));this.parent=parent;this.target=target;}
        int panelHeight(){return Math.min(268,height-4);}
        int x(){return (width-390)/2;}int y(){return Math.max(2,(height-panelHeight())/2);}
        int rowPitch(){return Math.min(28,Math.max(18,(panelHeight()-100)/6));}
        boolean busy(){return catalog==null||!catalog.index.ready()||requested||job!=null&&!job.done();}
        void request(String value){
            if(query.equals(value))return;
            query=value;page=0;requested=true;due=System.nanoTime()+DEBOUNCE;
            if(job!=null){job.cancel();job=null;}for(var button:rows)button.active=false;
            if(prev!=null){prev.active=false;next.active=false;}
        }
        @Override protected void init(){
            int cursor=search==null?query.length():search.getCursorPosition();clearWidgets();rows.clear();icons.clear();int x=x(),y=y();
            search=addRenderableWidget(new EditBox(font,x+10,y+30,370,18,Component.literal("输入中文名称或方块ID")));search.setMaxLength(256);search.setHint(Component.literal("搜索中文，如：石头、泥土、橡木"));search.setValue(query);search.moveCursorTo(Math.min(cursor,query.length()),false);search.setResponder(this::request);
            prev=addRenderableWidget(Button.builder(Component.literal("上一页"),b->{if(!busy()){page--;resultRows();}}).bounds(x+10,y+panelHeight()-38,100,18).build());
            next=addRenderableWidget(Button.builder(Component.literal("下一页"),b->{if(!busy()){page++;resultRows();}}).bounds(x+116,y+panelHeight()-38,100,18).build());
            addRenderableWidget(Button.builder(Component.literal("返回"),b->onClose()).bounds(x+280,y+panelHeight()-38,100,18).build());resultRows();setInitialFocus(search);
        }
        void resultRows(){
            for(var row:rows)removeWidget(row);rows.clear();icons.clear();page=Math.clamp(page,0,Math.max(0,(matches.size()-1)/PAGE_SIZE));int x=x(),y=y();
            for(int i=0;i<PAGE_SIZE&&page*PAGE_SIZE+i<matches.size();i++){
                var entry=matches.get(page*PAGE_SIZE+i);int xx=x+10+(i%2)*190,yy=y+58+(i/2)*rowPitch();
                var button=addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(entry.label(),145)),b->{if(!busy()){minecraft.setScreen(parent);parent.send(target?"terrain_target":"terrain_source",entry.id());}}).bounds(xx+22,yy,158,Math.min(24,rowPitch()-2)).build());
                button.setTooltip(Tooltip.create(Component.literal(entry.label()+"\n"+entry.id())));button.active=!busy();rows.add(button);icons.add(new ItemStack(entry.value()));
            }
            prev.active=!busy()&&page>0;next.active=!busy()&&(page+1)*PAGE_SIZE<matches.size();
        }
        void advance(){
            var current=TerrainClientCatalog.current();
            if(catalog!=current){if(job!=null)job.cancel();job=null;catalog=current;matches=List.of();page=0;requested=true;due=0;resultRows();}
            long deadline=System.nanoTime()+FRAME_BUDGET;
            if(!catalog.index.ready())catalog.advance(INDEX_BUDGET,deadline);
            if(!catalog.index.ready())return;
            if(requested&&System.nanoTime()>=due){job=catalog.index.query(query,target);requested=false;}
            if(job!=null&&!job.done()){
                job.step(SEARCH_BUDGET,deadline);
                if(job.done()){matches=job.results();page=0;resultRows();}
            }
        }
        @Override public void onClose(){if(job!=null)job.cancel();minecraft.setScreen(parent);}
        @Override public void removed(){if(job!=null)job.cancel();}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            advance();int x=x(),y=y();g.fill(x,y,x+390,y+panelHeight(),0xf0182735);g.drawString(font,title,x+10,y+10,0xffe4dbc0,false);
            if(!busy())for(int i=0;i<icons.size();i++)g.renderItem(icons.get(i),x+12+(i%2)*190,y+58+(Math.min(24,rowPitch()-2)-16)/2+(i/2)*rowPitch());
            String status=catalog==null||!catalog.index.ready()?"正在建立方块索引 "+(catalog==null?0:catalog.visited())+" / "+(catalog==null?0:catalog.total())
                :requested?"等待输入完成…":job!=null&&!job.done()?"搜索中 "+job.scanned()+" / "+job.total():"共 "+matches.size()+" 种 · 第 "+(page+1)+"/"+Math.max(1,(matches.size()+PAGE_SIZE-1)/PAGE_SIZE)+" 页";
            g.drawString(font,status,x+10,y+panelHeight()-15,0xff8bd6cc,false);super.render(g,mx,my,partial);
        }
        @Override public void renderBackground(GuiGraphics g,int x,int y,float p){}
        @Override public boolean isPauseScreen(){return false;}
    }
    static final class Guide extends Screen {
        final Screen parent;Guide(Screen parent){super(Component.literal("地形改造怎么用"));this.parent=parent;}
        @Override protected void init(){addRenderableWidget(Button.builder(Component.literal("返回"),b->onClose()).bounds(width/2-70,height-35,140,20).build());}
        @Override public void onClose(){minecraft.setScreen(parent);}
        @Override public void render(GuiGraphics g,int mx,int my,float p){g.fill(10,10,width-10,height-10,0xf0182735);int yy=22;String text="地形改造工具\n1. 手持工具右键两个对角，框住长方体。面板可补填最低/最高Y。\n2. 替换：只改选中的原方块；填补：只填空气，不填水和岩浆。\n3. 清理：移除草、花、作物、藤蔓、发光地衣、悬根和薄雪；不会砍树，也不移除水、实体或掉落物。\n4. 点击原方块/目标，用中文名称搜索并查看图标；没有中文翻译的模组方块会明确标注。\n5. 先预览数量，返回现场核对边框，再确认。30秒后需重新预览。\n每边最多128格，总体积32768格；所有区块需已加载。仅创造或管理员可用。\n容器和机器方块自动跳过；新方块使用默认状态，遵循后续游戏物理。不可填入容器、流体或需要两格的门/高植物。\n操作不掉落物品。修改前备份至世界存档/building_archive/backups/terrain_开头的NBT；失败时仍保留撤销。撤销仅本次登录当前维度最近一次，跳过后续被改动的位置。";
            for(var line:font.split(Component.literal(text),Math.min(width-40,500))){g.drawString(font,line,20,yy,0xffe0e8e9,false);yy+=10;}super.render(g,mx,my,p);}
        @Override public void renderBackground(GuiGraphics g,int x,int y,float p){}
        @Override public boolean isPauseScreen(){return false;}
    }
}

