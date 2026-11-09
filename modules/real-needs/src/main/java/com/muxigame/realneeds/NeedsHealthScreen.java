package com.muxigame.realneeds;

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NeedsHealthScreen extends Screen {
    private final Screen parent;
    private CompoundTag snapshot;
    private AilmentState.Site site=AilmentState.Site.WHOLE;
    private int page,rows,rowHeight;
    private NeedsRules viewRules=new NeedsRules();
    private long pending;
    private String pendingKey="",message="水分、饱食和休息有助恢复；护理需消耗对应物品。";
    private boolean confirmClear;
    private final Map<String,String> drafts=new HashMap<>();
    private final List<Button> actions=new ArrayList<>();
    private List<AilmentType> visible=List.of();
    public NeedsHealthScreen(Screen parent){super(Component.literal("真实需求 · 伤病与护理"));this.parent=parent;}
    private boolean admin(){return snapshot!=null&&snapshot.getBoolean("editable");}
    private NeedsRules rules(){return viewRules;}
    private static CompoundTag wrapRules(CompoundTag rules){var t=new CompoundTag();t.put("settings",rules);return t;}
    public void update(CompoundTag t){
        boolean first=snapshot==null,permissions=admin();snapshot=t.copy();viewRules=NeedsRules.load(wrapRules(t.getCompound("rules")),null);
        if(pending!=0&&t.getLong("health_ack")==pending){pending=0;message=t.getString("health_result");if(!message.startsWith("操作失败")){if(pendingKey.isEmpty())drafts.clear();else drafts.remove(pendingKey);if(minecraft!=null)rebuildWidgets();}}
        if((first||permissions!=admin())&&minecraft!=null)rebuildWidgets();
        for(var button:actions)button.active=pending==0;
    }
    private List<AilmentType> types(){return Arrays.stream(AilmentType.values()).filter(t->AilmentState.valid(t,site)).toList();}
    private int pages(){return Math.max(1,(types().size()+rows-1)/rows);}
    @Override protected void init(){
        rowHeight=width<480?60:42;rows=Math.max(1,(height-134)/rowHeight);page=Math.clamp(page,0,pages()-1);actions.clear();int y=43,siteRow=Math.max(15,Math.min(23,(height-105)/7));
        for(var part:AilmentState.Site.values()){addRenderableWidget(Button.builder(Component.literal((site==part?"› ":"")+part.label),b->{site=part;page=0;confirmClear=false;rebuildWidgets();}).bounds(8,y,92,Math.min(20,siteRow-2)).build());y+=siteRow;}
        var state=snapshot==null?new NeedsState():NeedsState.load(snapshot);var all=types();int from=page*rows;visible=all.subList(from,Math.min(from+rows,all.size()));
        int fieldX=width-202;
        for(int i=0;i<visible.size();i++){
            var type=visible.get(i);String key=AilmentState.key(type,site);int ry=51+i*rowHeight+(width<480?24:0);
            if(admin()){
                var field=new EditBox(font,fieldX,ry,54,18,Component.literal(type.label+"程度0～100"));field.setMaxLength(16);field.setValue(drafts.getOrDefault(key,NeedsParameters.format(state.ailments().severity(type,site))));field.setResponder(v->{drafts.put(key,v);confirmClear=false;});addRenderableWidget(field);
                actions.add(addRenderableWidget(Button.builder(Component.literal("设定"),b->{try{send("set",type,Double.parseDouble(field.getValue().strip()));}catch(IllegalArgumentException ex){message="操作失败：程度须为0～100的数字";}}).bounds(width-142,ry,60,18).build()));
            }
            actions.add(addRenderableWidget(Button.builder(Component.literal("护理"),b->send("care",type,0)).bounds(width-76,ry,64,18).build()));
        }
        var prev=addRenderableWidget(Button.builder(Component.literal("上一页"),b->{page--;rebuildWidgets();}).bounds(110,height-72,65,18).build());prev.active=page>0;
        var next=addRenderableWidget(Button.builder(Component.literal("下一页"),b->{page++;rebuildWidgets();}).bounds(width-77,height-72,65,18).build());next.active=page+1<pages();
        int center=(width+104)/2;
        addRenderableWidget(Button.builder(Component.literal("返回"),b->onClose()).bounds(center-40,height-46,80,20).build());
        if(admin())actions.add(addRenderableWidget(Button.builder(Component.literal("清除本人伤病"),b->{if(!confirmClear){confirmClear=true;message="再次点击清除本人伤病，仅用于调试，保留各部位健康值。";}else send("clearall",null,0);}).bounds(8,height-46,112,20).build()));
        for(var b:actions)b.active=pending==0;
    }
    private void send(String action,AilmentType type,double severity){
        if(pending!=0)return;if(!Double.isFinite(severity)||severity<0||severity>100)throw new IllegalArgumentException("程度范围0～100");
        pending=System.nanoTime();if(pending==0)pending=1;pendingKey=type==null?"":AilmentState.key(type,site);message="正在等待服务器…";
        PacketDistributor.sendToServer(new NeedsNetwork.HealthAction(pending,action,type==null?"":type.id,site.id(),severity));for(var b:actions)b.active=false;
    }
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        g.fill(0,0,width,height,0xff121a24);g.fill(4,37,104,height-53,0xff1b2936);g.drawString(font,"真实需求 · 伤病与护理",12,13,0xffefdbad);
        if(snapshot==null){g.drawString(font,"等待服务端数据…",112,40,0xffc2d1db);super.render(g,mx,my,dt);return;}
        var state=NeedsState.load(snapshot);var r=rules();
        String status=!NeedsIllness.enabled(r)?"伤病系统暂停":snapshot.getBoolean("paused")?"当前暂停进展/效果":"轻度 / 中度≥"+NeedsParameters.format(r.health("health.moderate"))+" / 重度≥"+NeedsParameters.format(r.health("health.severe"));
        g.drawString(font,status,112,32,0xff9dabbc);
        for(int i=0;i<visible.size();i++){
            var t=visible.get(i);int y=47+i*rowHeight;var c=state.ailments().get(t,site);int reserve=width<480?0:admin()?210:82;int maxLabel=Math.max(30,width-124-reserve);
            g.fill(110,y,width-9,y+rowHeight-3,i%2==0?0xff202d3b:0xff1b2734);
            String label=t.label+" · "+(c==null?"无":c.gradeLabel(r)+" "+String.format(Locale.ROOT,"%.1f",c.severity())+" /100");g.drawString(font,font.plainSubstrByWidth(label,maxLabel),116,y+3,t.color);
            String care=c==null?"护理用品："+t.careLabel:c.care()>0?"护理中 "+(int)Math.ceil(c.care())+"秒；间隔 "+(int)Math.ceil(c.cooldown())+"秒":"护理需1个"+t.careLabel;
            g.drawString(font,font.plainSubstrByWidth(care,maxLabel),116,y+17,0xffb5c4d2);
            int barY=y+rowHeight-10,barEnd=width<480?width-14:admin()?width-211:width-86;g.fill(116,barY,barEnd,barY+3,0xff0e1620);if(c!=null)g.fill(116,barY,116+(int)((barEnd-116)*c.severity()/100),barY+3,t.color);
        }
        g.drawCenteredString(font,(page+1)+" / "+pages()+" 页 · "+site.label,(110+width)/2,height-67,0xffb5c4d2);
        g.drawString(font,font.plainSubstrByWidth(message,width-20),10,height-17,message.startsWith("操作失败")?0xffff9b85:0xffb9d7bd);super.render(g,mx,my,dt);
        for(int i=0;i<visible.size();i++){int y=47+i*rowHeight;if(mx>=110&&mx<width-(width<480?12:210)&&my>=y&&my<y+(width<480?28:rowHeight-2)){var t=visible.get(i);g.renderTooltip(font,font.split(Component.literal(t.description+"。护理：消耗1个"+t.careLabel+"。管理员设定0清除；设定只改病情程度。"),Math.min(280,width-30)),mx,my);}}
    }
    @Override public void onClose(){if(pending!=0){message="正在等待服务器结果…";return;}minecraft.setScreen(parent);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float dt){}
    @Override public boolean isPauseScreen(){return false;}
}
