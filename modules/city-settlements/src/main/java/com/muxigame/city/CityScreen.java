package com.muxigame.city;

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-backed 32x32 viewport. All mutations wait for server acknowledgement. */
public final class CityScreen extends Screen {
    private CompoundTag data;
    private EditBox name,blockId;
    private String notice="",lastId="";
    private int noticeTicks,ageTicks,zoom=16,countScroll,panX,panZ,layer,waitTicks,quietTicks;
    private boolean surface=true,isometric=false,materials=false,pending=false,advanced=false;
    private CompoundTag selected,second;
    private final List<Hit> hits=new ArrayList<>();
    private record Hit(CompoundTag cell,double cx,double cy,double halfX,double halfY,boolean diamond){
        boolean contains(double x,double y){double a=Math.abs(x-cx)/halfX,b=Math.abs(y-cy)/halfY;return diamond?a+b<=1:a<=1&&b<=1;}
    }
    public CityScreen(CompoundTag data){super(Component.literal("城市与聚落 · 城市沙盘"));this.data=data;sync();}
    public void update(CompoundTag incoming){
        String old=data.getString("Id");long revision=data.getLong("Revision");data=incoming;pending=false;waitTicks=0;
        if(!incoming.getString("Message").isEmpty()){notice=incoming.getString("Message");noticeTicks=180;}
        if(!old.equals(data.getString("Id"))){selected=null;second=null;countScroll=0;}
        else if(revision!=data.getLong("Revision")){selected=null;second=null;}
        sync();if(minecraft!=null)rebuildWidgets();
    }
    private void sync(){panX=data.getInt("X");panZ=data.getInt("Z");layer=data.getInt("Y");surface=data.getBoolean("Surface");}
    private Button button(String text,int x,int y,int w,Runnable action){return addRenderableWidget(Button.builder(Component.literal(text),b->action.run()).bounds(x,y,w,18).build());}
    private void action(String op){if(selected==null)return;CompoundTag end=second==null?selected:second;send(op,selected.getInt("X"),selected.getInt("Y"),selected.getInt("Z"),end.getInt("X"),end.getInt("Z"),selected.getInt("State"),"",surface);}
    private void send(String op,int x,int y,int z,int x2,int z2,int state,String value,boolean surface){
        if(pending)return;pending=true;waitTicks=0;quietTicks=0;
        PacketDistributor.sendToServer(new CityNetwork.Request(op,data.getString("Id"),data.getLong("Revision"),x,y,z,x2,z2,state,value,surface));
    }
    private void view(boolean refresh){selected=null;second=null;send(refresh?"refresh":"view",panX,layer,panZ,0,0,0,"",surface);}
    private void simple(String op,String value){send(op,0,0,0,0,0,0,value,surface);}
    @Override protected void init(){
        String draft=name==null?"新聚落":name.getValue(),target=blockId==null?"minecraft:stone":blockId.getValue();
        if(!lastId.equals(data.getString("Id"))){draft=data.contains("Id")?data.getString("Name"):"新聚落";lastId=data.getString("Id");}
        if(advanced){name=addRenderableWidget(new EditBox(font,10,31,120,18,Component.literal("聚落名称")));name.setMaxLength(32);name.setValue(draft);
        button("新建",10,53,58,()->simple("create",name.getValue())).active=data.getBoolean("CanCreate")&&data.getBoolean("Selection");
        button("改名",72,53,58,()->simple("rename",name.getValue())).active=data.getBoolean("Editable");
        }
        button("切换聚落",10,75,120,()->{
            ListTag cities=data.getList("Cities",Tag.TAG_COMPOUND);if(cities.isEmpty()||pending)return;int current=-1;
            for(int i=0;i<cities.size();i++)if(cities.getCompound(i).getString("Id").equals(data.getString("Id")))current=i;
            pending=true;waitTicks=0;PacketDistributor.sendToServer(new CityNetwork.Request("select",cities.getCompound((current+1)%cities.size()).getString("Id"),0,0,0,0,0,0,0,"",true));
        });
        if(advanced){button("用当前选区更新范围",10,97,120,()->minecraft.setScreen(new ConfirmScreen(ok->{minecraft.setScreen(this);if(ok)simple("resize","");},Component.literal("更新聚落范围？"),Component.literal("原范围外的归属设置会移除，新扩展部分默认纳入。世界方块不会改变。此操作不能撤销。"))))
            .active=data.getBoolean("Editable")&&data.getBoolean("Selection");
        button("纳入",10,123,58,()->action("include")).active=data.getBoolean("Editable");
        button("排除",72,123,58,()->action("exclude")).active=data.getBoolean("Editable");
        button("撤销上步",10,145,58,()->simple("undo","" )).active=data.getBoolean("Editable");
        button("重统计",72,145,58,()->view(true));
        blockId=addRenderableWidget(new EditBox(font,10,171,120,18,Component.literal("替换方块ID")));blockId.setMaxLength(128);blockId.setValue(target);
        button("替换选中世界方块…",10,193,120,this::confirmReplace).active=data.getBoolean("WorldEditable");}
        button(advanced?"返回道具玩法":"高级数据编辑",width-154,8,100,()->{advanced=!advanced;rebuildWidgets();});
        if(!advanced){button("玩法帮助",10,97,120,()->minecraft.setScreen(CityHelpScreen.quick(this,"城市与建筑魔杖 · 极简")));button("返回现场规划",10,31,120,this::onClose);button("重统计",10,53,120,()->view(true));button("撤销归属调整",10,123,120,()->simple("undo",""));}
        if(height>=335){button("绑定近旁NPC",10,282,120,()->simple("npcbind","")).active=data.getBoolean("Editable");button("解除近旁NPC绑定",10,304,120,()->simple("npcunbind","")).active=data.getBoolean("Editable");}
        button(materials?"返回沙盘":"材料统计",140,31,68,()->{materials=!materials;rebuildWidgets();});
        button(surface?"地表投影":"逐层切片",212,31,68,()->{if(pending)return;if(surface&&selected!=null)layer=selected.getInt("Y");surface=!surface;view(false);});
        if(width>=380)button(isometric?"俯视":"斜视",284,31,44,()->isometric=!isometric);
        button("关闭",width-48,8,40,this::onClose);
        int navY=height-47;
        button("←",140,navY,22,()->{if(!pending){panX-=zoom/2;view(false);}});
        button("→",164,navY,22,()->{if(!pending){panX+=zoom/2;view(false);}});
        button("↑",188,navY,22,()->{if(!pending){panZ-=zoom/2;view(false);}});
        button("↓",212,navY,22,()->{if(!pending){panZ+=zoom/2;view(false);}});
        button("Y−",240,navY,30,()->{if(!pending){layer--;view(false);}});
        button("Y+",272,navY,30,()->{if(!pending){layer++;view(false);}});
        if(width>=360)button("缩放",306,navY,42,()->{zoom=zoom==32?8:zoom*2;selected=null;second=null;});
    }
    private void confirmReplace(){
        if(selected==null||second!=null){notice="请单选一个已纳入的方块。";noticeTicks=100;return;}
        CompoundTag cell=selected.copy();String replacement=blockId.getValue();long revision=data.getLong("Revision");String city=data.getString("Id");
        minecraft.setScreen(new ConfirmScreen(ok->{minecraft.setScreen(this);if(ok&&!pending){pending=true;waitTicks=0;
            PacketDistributor.sendToServer(new CityNetwork.Request("replace",city,revision,cell.getInt("X"),cell.getInt("Y"),cell.getInt("Z"),0,0,cell.getInt("State"),replacement,false));}},
            Component.literal("确认修改真实世界方块？"),Component.literal("将 "+position(cell)+" 的 "+blockName(cell)+" 替换为 "+replacement+"。只支持普通实心方块或空气；不会掉落物品。可撤销上一步。")));
    }
    @Override public void tick(){
        super.tick();if(noticeTicks>0)noticeTicks--;ageTicks++;quietTicks++;
        if(pending&&++waitTicks>100){pending=false;notice="请求超时，可点击重统计重新同步。";noticeTicks=100;}
        // Poll only while a bounded server scan is running, never trigger scans from polling.
        if(!pending&&quietTicks>=4&&data.getBoolean("Scanning")&&ageTicks%20==0)send("view",panX,layer,panZ,0,0,0,"",surface);
    }
    private static String position(CompoundTag c){return c.getInt("X")+", "+c.getInt("Y")+", "+c.getInt("Z");}
    private static String blockName(CompoundTag c){return Block.stateById(c.getInt("State")).getBlock().getName().getString();}
    private boolean inSelection(CompoundTag c){if(selected==null)return false;CompoundTag end=second==null?selected:second;
        return c.getInt("Y")==selected.getInt("Y")&&c.getInt("X")>=Math.min(selected.getInt("X"),end.getInt("X"))&&c.getInt("X")<=Math.max(selected.getInt("X"),end.getInt("X"))&&c.getInt("Z")>=Math.min(selected.getInt("Z"),end.getInt("Z"))&&c.getInt("Z")<=Math.max(selected.getInt("Z"),end.getInt("Z"));}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,0xff101923);g.fill(0,25,136,height,0xff182735);g.drawString(font,"城市与聚落 / 沙盘",10,11,0xffe4dbc0,false);
        hits.clear();
        if(!data.contains("Id")){
            text(g,"先用区域魔杖框选范围",146,65,0xffe4dbc0);text(g,"右键第一角，潜行右键第二角",146,81,0xffa8bdc7);text(g,"使用右键空气 → 应用选区",146,97,0xffa8bdc7);
            text(g,"或 /city pos1 与 /city pos2",146,121,0xffa8bdc7);
        }else{
            text(g,data.getString("Name")+" · Y="+layer+(pending?" · 同步中":""),142,54,0xffeee2c5);
            if(materials)drawCounts(g);else drawMap(g);
        }
        if(!advanced){text(g,"区域魔杖 → 标记两角",10,151,0xffa8bdc7);text(g,"右键空气 → 应用选区",10,166,0xffa8bdc7);text(g,"绿=归属 红=排除",10,181,0xffa8bdc7);text(g,"信息魔杖 → 城市设置",10,196,0xffa8bdc7);}
        int y=221;
        if(height>285){text(g,"居民绑定："+data.getInt("Residents"),10,y,0xff92cfc5);y+=13;
            text(g,data.getBoolean("HasStats")?"非空气："+data.getLong("NonAir"):"尚无统计",10,y,0xffe4dbc0);y+=13;
            text(g,data.getBoolean("Scanning")?"正在扫描…":"排除格："+data.getLong("Excluded"),10,y,0xffa8bdc7);y+=13;
            if(data.getLong("Unloaded")>0)text(g,"未加载："+data.getLong("Unloaded"),10,y,0xffffbe79);
        }
        String detail=selected==null?"点选方块；右键或Shift+点击第二角框选（切片）":position(selected)+" "+blockName(selected)+(selected.getBoolean("Included")?" [纳入]":" [排除]");
        text(g,font.plainSubstrByWidth(detail,width-151),142,height-62,0xffc9dbdf);
        String footer=noticeTicks>0?notice:(materials?"滚轮翻阅材料。统计不含空气、已排除格和未加载格。":"滚轮切换Y层 · 绿色=纳入 红色=排除 灰色=未加载 · 方向键按钮平移");
        text(g,font.plainSubstrByWidth(footer,width-20),10,height-21,noticeTicks>0?0xffffcf88:0xff93aaba);
        super.render(g,mx,my,partial);
        if(!materials&&mx>=140&&my>65&&my<height-65){Hit hit=hit(mx,my);if(hit!=null){CompoundTag c=hit.cell;g.renderTooltip(font,Component.literal(position(c)+" · "+(c.getBoolean("Loaded")?blockName(c):"区块未加载")+(c.getBoolean("Included")?" · 纳入":" · 排除")),mx,my);}}
    }
    private void text(GuiGraphics g,String text,int x,int y,int color){g.drawString(font,text,x,y,color,false);}
    private void drawCounts(GuiGraphics g){
        text(g,"非空气 "+data.getLong("NonAir")+" / 范围 "+data.getLong("Volume")+" 格",146,71,0xffd9e6df);
        text(g,data.getBoolean("Scanning")?"正在统计；完成后自动显示":"快照 "+data.getLong("Age")+" 秒前 · 未加载 "+data.getLong("Unloaded"),146,85,0xffafc1ca);
        ListTag counts=data.getList("Counts",Tag.TAG_COMPOUND);int rows=Math.max(1,(height-175)/16);countScroll=Math.clamp(countScroll,0,Math.max(0,counts.size()-rows));
        for(int i=0;i<rows&&i+countScroll<counts.size();i++){CompoundTag entry=counts.getCompound(i+countScroll);int y=104+i*16;
            g.fill(142,y-2,width-10,y+12,i%2==0?0xff1c2d3b:0xff15232f);text(g,font.plainSubstrByWidth(entry.getString("Block"),width-215),146,y,0xffc5d9db);text(g,""+entry.getLong("Count"),width-59,y,0xffb1d89d);
        }
    }
    private void drawMap(GuiGraphics g){
        int left=142,top=69,right=width-10,bottom=height-68;
        g.fill(left,top,right,bottom,0xff0b131c);if(bottom<=top||right<=left)return;
        g.enableScissor(left,top,right,bottom);
        List<CompoundTag> cells=new ArrayList<>();for(Tag t:data.getList("Cells",Tag.TAG_COMPOUND)){CompoundTag c=(CompoundTag)t;if(c.getInt("X")-panX<zoom&&c.getInt("Z")-panZ<zoom)cells.add(c);}
        int floor=surface?BlockPos.of(data.getLong("Min")).getY():layer;
        int relief=surface?cells.stream().filter(c->c.getBoolean("Loaded")&&!c.getBoolean("Air")).mapToInt(c->c.getInt("Y")-floor).max().orElse(0):0;
        double unit=isometric?Math.min((right-left-10.0)/(zoom*2),(bottom-top-10.0)/(zoom+relief*.6+2)):Math.min((right-left-8.0)/zoom,(bottom-top-8.0)/zoom);
        if(isometric)cells.sort(Comparator.comparingInt(c->c.getInt("X")+c.getInt("Z")));
        for(CompoundTag c:cells){int x=c.getInt("X")-panX,z=c.getInt("Z")-panZ;
            int rgb=c.getInt("Color");if(rgb==0)rgb=0x71858d;
            int color=c.getBoolean("Loaded")?(c.getBoolean("Air")?0xff172b35:0xff000000|rgb):0xff3f424c;
            if(!c.getBoolean("Included"))color=shade(color,.42)|0x005b1010;
            boolean chosen=inSelection(c);
            if(!isometric){int px=(int)(left+4+x*unit),py=(int)(top+4+z*unit),size=Math.max(1,(int)unit-1);
                g.fill(px,py,px+size,py+size,color);int border=chosen?0xffffde69:c.getBoolean("Included")?0xff6dac83:0xffcb6469;
                if(unit>=5){g.fill(px,py,px+size,py+1,border);if(chosen){g.fill(px,py,px+1,py+size,border);g.fill(px,py+size-1,px+size,py+size,border);g.fill(px+size-1,py,px+size,py+size,border);}}
                hits.add(new Hit(c,px+size/2.0,py+size/2.0,size/2.0,size/2.0,false));
            }else{
                double cx=(left+right)/2.0+(x-z)*unit,base=top+5+relief*unit*.6+(x+z+1)*unit/2;
                double cy=base-(surface&&!c.getBoolean("Air")?(c.getInt("Y")-floor)*unit*.6:0);
                for(int px=(int)(cx-unit);px<=cx+unit;px++){
                    double edge=(1-Math.abs(px-cx)/unit)*unit/2;
                    g.fill(px,(int)(cy+edge),px+1,(int)(base+edge+Math.max(2,unit/3)),shade(color,px<cx?.60:.42));
                }
                diamond(g,cx,cy,unit,unit/2,chosen?0xffffd879:color);
                if(!c.getBoolean("Included"))g.fill((int)cx-1,(int)cy-1,(int)cx+2,(int)cy+2,0xffff6868);
                hits.add(new Hit(c,cx,cy,unit,unit/2,true));
            }
        }
        g.disableScissor();
        int infoX=(int)(left+zoom*unit+18);
        if(!isometric&&right-infoX>=130){
            int infoY=top+12;
            text(g,"聚落概况",infoX,infoY,0xffe4dbc0);infoY+=20;
            text(g,"非空气方块  "+(data.getBoolean("HasStats")?data.getLong("NonAir"):"—"),infoX,infoY,0xffc5d9db);infoY+=16;
            text(g,"已排除格数  "+(data.getBoolean("HasStats")?data.getLong("Excluded"):"—"),infoX,infoY,0xffc5d9db);infoY+=16;
            text(g,"未加载格数  "+(data.getBoolean("HasStats")?data.getLong("Unloaded"):"—"),infoX,infoY,0xffc5d9db);infoY+=16;
            text(g,data.getBoolean("Scanning")?"统计扫描中…":"统计快照 / 点击重统计刷新",infoX,infoY,0xff8eacae);infoY+=28;
            if(selected!=null){
                text(g,"当前选中",infoX,infoY,0xffe4dbc0);infoY+=18;
                text(g,position(selected),infoX,infoY,0xffa9d0c5);infoY+=16;
                text(g,blockName(selected),infoX,infoY,0xffc5d9db);infoY+=16;
                text(g,selected.getBoolean("Included")?"归属：城市":"归属：已排除",infoX,infoY,selected.getBoolean("Included")?0xff8cca9f:0xffdb8f8f);
            }else{text(g,"点选地图中的方块",infoX,infoY,0xff8eacae);infoY+=16;text(g,"地表 / 切片可查看不同高度",infoX,infoY,0xff8eacae);}
        }
    }
    private static int shade(int color,double amount){return 0xff000000|((int)((color>>16&255)*amount)<<16)|((int)((color>>8&255)*amount)<<8)|(int)((color&255)*amount);}
    private static void diamond(GuiGraphics g,double cx,double cy,double hx,double hy,int color){for(int y=(int)Math.floor(cy-hy);y<=Math.ceil(cy+hy);y++){double width=hx*(1-Math.abs(y-cy)/hy);if(width>0)g.fill((int)(cx-width),y,(int)(cx+width)+1,y+1,color);}}
    private Hit hit(double x,double y){for(int i=hits.size()-1;i>=0;i--)if(hits.get(i).contains(x,y))return hits.get(i);return null;}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(super.mouseClicked(x,y,button))return true;
        if((button==0||button==1)&&!materials&&y>65&&y<height-65){Hit hit=hit(x,y);if(hit!=null&&hit.cell.getBoolean("Loaded")){
            if((button==1||hasShiftDown())&&!surface&&selected!=null&&selected.getInt("Y")==hit.cell.getInt("Y"))second=hit.cell;
            else {selected=hit.cell;second=null;}return true;
        }}return false;
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){
        if(x>=140){if(materials){countScroll-=Integer.signum((int)dy)*3;return true;}
            if(!pending){layer+=dy>0?1:-1;view(false);}return true;}
        return super.mouseScrolled(x,y,dx,dy);
    }
    @Override public boolean isPauseScreen(){return false;}
    // Screen.render() draws a blurred background by default; our opaque canvas is already drawn.
    @Override public void renderBackground(GuiGraphics g,int mouseX,int mouseY,float partial){}
}

