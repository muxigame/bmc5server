package com.muxigame.realneeds;

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Drafts survive periodic snapshots, category changes, pagination and resize. */
public final class NeedsSettingsScreen extends Screen {
    private final Screen parent;
    private final Map<String,String> draft=new LinkedHashMap<>();
    private CompoundTag base;
    private long revision,pending;
    private boolean editable,canUndo,rereadPending;
    private String category="初始值",query="",message="正在读取服务器设置…",armed="";
    private int page,rows,categoryPage;
    private EditBox search;
    private final List<EditBox> fields=new ArrayList<>();
    private List<NeedsParameters.Entry> visible=List.of();
    private Button save,reload,defaults,undo,prev,next;
    public NeedsSettingsScreen(Screen parent){super(Component.literal("真实需求 · 参数调试"));this.parent=parent;}
    public void update(CompoundTag snapshot){
        editable=snapshot.getBoolean("editable");canUndo=snapshot.getBoolean("can_undo");
        if(base==null){read(snapshot);if(minecraft!=null)rebuildWidgets();}
        else if(rereadPending){rereadPending=false;read(snapshot);message="已读取服务器设置，未保存的修改已放弃。";rebuildWidgets();}
        else if(pending!=0&&snapshot.getLong("ack")==pending){
            pending=0;message=snapshot.getString("result");
            if(!message.startsWith("未保存")){read(snapshot);rebuildWidgets();}
        }
        // Unsolicited snapshots never replace the user's draft or its revision.
        updateButtons();
    }
    private void read(CompoundTag t){
        base=t.getCompound("rules").copy();revision=t.getLong("revision");draft.clear();
        for(var e:NeedsParameters.ALL)draft.put(e.key(),NeedsParameters.format(NeedsParameters.get(base,e)));
        if(message.startsWith("正在"))message=editable?"修改后点击保存；初始值不覆盖已有角色进度。":"只读：需要管理员权限才能保存。";
    }
    private List<NeedsParameters.Entry> filtered(){return NeedsParameters.ALL.stream().filter(e->query.isBlank()?e.group().equals(category):(e.label()+e.group()+e.key()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();}
    private int pageCount(){return Math.max(1,(filtered().size()+rows-1)/rows);}
    private boolean dirty(){if(base==null)return false;return NeedsParameters.ALL.stream().anyMatch(e->{try{return Double.compare(Double.parseDouble(draft.get(e.key())),NeedsParameters.get(base,e))!=0;}catch(Exception ex){return true;}});}
    private void updateButtons(){if(save==null)return;boolean ready=base!=null&&editable&&pending==0&&!rereadPending;save.active=ready&&dirty();defaults.active=ready;undo.active=ready&&canUndo;reload.active=pending==0&&!rereadPending;prev.active=page>0;next.active=page+1<pageCount();for(var f:fields)f.setEditable(ready);}
    @Override protected void init(){
        boolean searching=search!=null&&search.isFocused();
        rows=Math.max(1,(height-149)/34);page=Math.clamp(page,0,pageCount()-1);fields.clear();
        int right=140,editX=width-102;
        search=new EditBox(font,right,34,Math.max(90,width-right-90),20,Component.literal("搜索参数"));search.setMaxLength(64);search.setValue(query);
        search.setHint(Component.literal("搜索中文名称或参数ID"));search.setResponder(v->{query=v;page=0;armed="";rebuildWidgets();});addRenderableWidget(search);if(searching){setFocused(search);search.setCursorPosition(search.getValue().length());}
        addRenderableWidget(Button.builder(Component.literal("清空"),b->{query="";page=0;rebuildWidgets();}).bounds(width-78,34,66,20).build());
        var groups=NeedsParameters.ALL.stream().map(NeedsParameters.Entry::group).distinct().toList();int groupRows=Math.max(1,(height-134)/20),groupPages=(groups.size()+groupRows-1)/groupRows;categoryPage=Math.clamp(categoryPage,0,groupPages-1);
        int cy=62;for(String group:groups.subList(categoryPage*groupRows,Math.min((categoryPage+1)*groupRows,groups.size()))){
            String name=group;addRenderableWidget(Button.builder(Component.literal((query.isBlank()&&category.equals(group)?"› ":"")+group),b->{category=name;query="";page=0;armed="";rebuildWidgets();}).bounds(8,cy,122,18).build());cy+=20;
        }
        if(groupPages>1){addRenderableWidget(Button.builder(Component.literal("‹ 分类"),b->{categoryPage=Math.floorMod(categoryPage-1,groupPages);rebuildWidgets();}).bounds(8,height-78,59,18).build());addRenderableWidget(Button.builder(Component.literal("分类 ›"),b->{categoryPage=(categoryPage+1)%groupPages;rebuildWidgets();}).bounds(71,height-78,59,18).build());}
        var all=filtered();int from=page*rows;visible=all.subList(Math.min(from,all.size()),Math.min(from+rows,all.size()));
        for(int i=0;i<visible.size();i++){
            var e=visible.get(i);EditBox box=new EditBox(font,editX,68+i*34,88,20,Component.literal(e.label()+"（"+e.unit()+"）"));
            box.setMaxLength(24);box.setValue(draft.getOrDefault(e.key(),""));box.setResponder(v->{draft.put(e.key(),v);armed="";updateButtons();});addRenderableWidget(box);fields.add(box);
        }
        prev=addRenderableWidget(Button.builder(Component.literal("上一页"),b->{page--;rebuildWidgets();}).bounds(right,height-76,64,18).build());
        next=addRenderableWidget(Button.builder(Component.literal("下一页"),b->{page++;rebuildWidgets();}).bounds(width-78,height-76,66,18).build());
        int bw=Math.min(86,(width-40)/5),gap=bw+6,x=Math.max(8,(width-(bw*5+24))/2),y=height-50;
        save=addRenderableWidget(Button.builder(Component.literal("保存修改"),b->submit("save")).bounds(x,y,bw,20).build());
        reload=addRenderableWidget(Button.builder(Component.literal("重读设置"),b->{if(dirty()&&!arm("reload"))return;rereadPending=true;message="正在重读服务器设置…";updateButtons();PacketDistributor.sendToServer(new NeedsNetwork.Request(false));}).bounds(x+gap,y,bw,20).build());
        defaults=addRenderableWidget(Button.builder(Component.literal("恢复默认"),b->{if(arm("reset"))submit("reset");}).bounds(x+gap*2,y,bw,20).build());
        undo=addRenderableWidget(Button.builder(Component.literal("撤销上次"),b->{if(!dirty()||arm("undo"))submit("undo");}).bounds(x+gap*3,y,bw,20).build());
        addRenderableWidget(Button.builder(Component.literal("返回"),b->onClose()).bounds(x+gap*4,y,bw,20).build());updateButtons();
    }
    private boolean arm(String action){if(armed.equals(action)){armed="";return true;}armed=action;message=switch(action){case "reset"->"再次点击恢复默认，将保存默认参数；可撤销。";case "close"->"有未保存修改，再次点击返回将放弃修改。";default->"有未保存修改，再次点击该按钮将放弃修改。";};return false;}
    private void submit(String action){
        if(base==null||!editable||pending!=0)return;
        var patch=new CompoundTag();
        if(action.equals("save"))try{
            for(var e:NeedsParameters.ALL){double value=Double.parseDouble(draft.get(e.key()).strip());e.validate(value);if(e.key().equals("enabled")&&value!=0&&value!=1)throw new IllegalArgumentException("系统启用只能填0或1");if(Double.compare(value,NeedsParameters.get(base,e))!=0)patch.putDouble(e.key(),value);}
        }catch(IllegalArgumentException ex){message="未保存："+(ex instanceof NumberFormatException?"请填写有效数字（使用英文小数点）":ex.getMessage());return;}
        if(action.equals("save")&&patch.isEmpty()){message="没有修改，无需保存。";return;}
        pending=System.nanoTime();if(pending==0)pending=1;message="正在保存…";armed="";
        PacketDistributor.sendToServer(new NeedsNetwork.Edit(pending,revision,action,patch));updateButtons();
    }
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        g.fill(0,0,width,height,0xff121a24);g.fill(4,58,134,height-58,0xff1b2936);
        g.drawString(font,"真实需求 · 参数调试",12,12,0xfff2e8cd);
        String head="存档共用参数 · 保存后立即生效";g.drawString(font,head,width-font.width(head)-12,14,0xffa9b8c8);
        for(int i=0;i<visible.size();i++){
            var e=visible.get(i);int y=64+i*34;g.fill(140,y,width-10,y+32,i%2==0?0xff202d3b:0xff1b2734);
            g.drawString(font,font.plainSubstrByWidth(e.label(),Math.max(20,width-252)),147,y+4,0xffedf3f7);
            g.drawString(font,font.plainSubstrByWidth(e.unit()+" · "+NeedsParameters.format(e.min())+"～"+NeedsParameters.format(e.max()),Math.max(20,width-252)),147,y+18,0xff9dabbc);
        }
        String pages=(page+1)+" / "+pageCount()+" 页 · "+filtered().size()+" 项";g.drawCenteredString(font,pages,(140+width)/2,height-71,0xffa9b8c8);
        g.drawString(font,font.plainSubstrByWidth(message,width-24),12,height-22,message.startsWith("未保存")?0xffff9b85:0xffb9d7bd);
        super.render(g,mx,my,dt);
        for(int i=0;i<visible.size();i++){var e=visible.get(i);int y=64+i*34;if(mx>=140&&mx<width-106&&my>=y&&my<y+32)g.renderTooltip(font,Component.literal(e.label()+"；"+e.unit()+"；默认 "+NeedsParameters.format(e.initial())),mx,my);}
    }
    @Override public void onClose(){if(pending!=0){message="正在等待服务器保存结果…";return;}if(dirty()&&!arm("close"))return;minecraft.setScreen(parent);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float dt){}
    @Override public boolean isPauseScreen(){return false;}
}
