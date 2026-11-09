package com.muxigame.city;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
/** Deliberately one small page: no policy, security, freight or production forms. */
public final class CityBasicLivingScreen extends Screen {
    private CompoundTag data;private final Screen parent;private CityLiving.Profile profile;private JsonArray warehouses;private int warehouseIndex,left,w,wait;private boolean enabled,dirty,pending;private String message="";private EditBox food,min,max,treatment,item;private String[] draft;
    public CityBasicLivingScreen(CompoundTag data,Screen parent){super(Component.literal("NPC基础生活"));this.parent=parent;load(data);}
    private void load(CompoundTag t){data=t;profile=CityEconomy.JSON.fromJson(JsonParser.parseString(t.getString("Draft")).getAsJsonObject().get("profile"),CityLiving.Profile.class);warehouses=JsonParser.parseString(t.getString("Choices")).getAsJsonObject().getAsJsonArray("warehouses");warehouseIndex=0;for(int i=0;i<warehouses.size();i++)if(warehouses.get(i).getAsJsonObject().get("id").getAsString().equals(String.valueOf(profile.basicWarehouse)))warehouseIndex=i;enabled=profile.enabled;dirty=false;draft=null;}
    public void update(CompoundTag t){pending=false;message=t.getString("Message");if(!t.getBoolean("Failed")){load(t);rebuildWidgets();}}
    private Button button(String label,int x,int y,int width,Runnable run){return addRenderableWidget(Button.builder(Component.literal(label),b->{if(!pending)run.run();}).bounds(x,y,width,20).build());}
    private EditBox field(String label,String value,int x,int y,int width){var box=addRenderableWidget(new EditBox(font,x,y,width,18,Component.literal(label)));box.setMaxLength(128);box.setValue(value);box.setResponder(v->dirty=true);return box;}
    private void remember(){if(food!=null)draft=new String[]{food.getValue(),item.getValue(),min.getValue(),max.getValue(),treatment.getValue()};}
    @Override public void removed(){remember();super.removed();}
    @Override public void resize(net.minecraft.client.Minecraft mc,int width,int height){remember();super.resize(mc,width,height);}
    @Override protected void init(){w=Math.min(410,width-24);left=(width-w)/2;int half=w/2;
        button("所属聚落："+data.getString("CityName"),left,34,w-200,()->send("npccityview","",data.getString("NpcId")+"|choose"));
        button("工作时间",left+w-194,34,94,()->send("workview",data.getString("NpcId"),""));button("职业与修补",left+w-96,34,96,()->send("maintworker",profile.city.toString(),data.getString("NpcId")));
        food=field("每实际小时食量",draft==null?Integer.toString(profile.basicMode?profile.foodPerHour:2):draft[0],left,76,half-8);item=field("口粮物品",draft==null?net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(profile.basket.getFirst().item)).getDescription().getString():draft[1],left+half,76,half-8);
        min=field("待遇需求下限",draft==null?Integer.toString(profile.basicMode?profile.minNeed:20):draft[2],left,115,half-8);max=field("待遇需求上限",draft==null?Integer.toString(profile.basicMode?profile.maxNeed:40):draft[3],left+half,115,half-8);
        treatment=field("实际待遇",draft==null?Integer.toString(profile.basicMode?profile.baseComfort:30):draft[4],left,154,half-8);
        addRenderableWidget(Button.builder(Component.literal(warehouseLabel()),b->{if(!pending&&!warehouses.isEmpty()){warehouseIndex=(warehouseIndex+1)%warehouses.size();b.setMessage(Component.literal(warehouseLabel()));dirty=true;}}).bounds(left+half,154,half-8,20).build());
        addRenderableWidget(Button.builder(Component.literal(enabled?"生活结算：开":"生活结算：关"),b->{if(!pending){enabled=!enabled;dirty=true;b.setMessage(Component.literal(enabled?"生活结算：开":"生活结算：关"));}}).bounds(left,height-50,w/4-4,20).build());
        button("保存",left+w/4,height-50,w/4-4,this::save);button("默认值",left+w/2,height-50,w/4-4,()->{food.setValue("2");min.setValue("20");max.setValue("40");treatment.setValue("30");});button("返回",left+3*w/4,height-50,w/4,this::onClose);
    }
    private String warehouseLabel(){return warehouses.isEmpty()?"没有已登记经营粮仓":"粮仓："+warehouses.get(warehouseIndex).getAsJsonObject().get("name").getAsString();}
    private void save(){try{if(warehouses.isEmpty())throw new IllegalArgumentException("先登记一个粮仓和经营账户");var form=new JsonObject();form.addProperty("enabled",enabled);form.addProperty("foodPerHour",Integer.parseInt(food.getValue()));form.addProperty("min",Integer.parseInt(min.getValue()));form.addProperty("max",Integer.parseInt(max.getValue()));form.addProperty("treatment",Integer.parseInt(treatment.getValue()));String foodName=item.getValue().trim();String foodId=null;for(var entry:net.minecraft.core.registries.BuiltInRegistries.ITEM)if(entry.getDescription().getString().equals(foodName)){foodId=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(entry).toString();break;}if(foodId==null)throw new IllegalArgumentException("找不到口粮，请填写中文名称，例如面包");form.addProperty("item",foodId);form.addProperty("warehouse",warehouses.get(warehouseIndex).getAsJsonObject().get("id").getAsString());CityBasics.edit(JsonParser.parseString(data.getString("Draft")).getAsJsonObject(),form);
        minecraft.setScreen(new ConfirmScreen(ok->{minecraft.setScreen(this);if(ok)send("npclivingbasicsave",data.getString("NpcId"),form.toString());},Component.literal("保存这个NPC的基础生活？"),Component.literal("只从指定公粮仓领饭，不扣钱；暂停此NPC的复杂消费、需求增长和自动迁出。原设置备份。")));
    }catch(RuntimeException ex){message="未保存："+(ex.getMessage()==null?"请填写有效整数":ex.getMessage());}}
    private void send(String op,String city,String value){pending=true;wait=100;PacketDistributor.sendToServer(new CityNetwork.Request(op,city,data.getLong("Revision"),0,0,0,0,0,0,value,false));}
    @Override public void tick(){if(pending&&--wait<=0){pending=false;message="请求超时，请返回后重新打开；草稿保留。";}}
    @Override public void onClose(){if(dirty)minecraft.setScreen(new ConfirmScreen(ok->{minecraft.setScreen(ok?parent:this);},Component.literal("放弃尚未保存的修改？"),Component.literal("只有点击保存后才会修改NPC。")));else minecraft.setScreen(parent);}
    @Override public void render(GuiGraphics g,int x,int y,float delta){g.fill(0,0,width,height,0xff2e2a24);g.drawString(font,"基础生活 · "+data.getString("Name"),left,13,0xffead6a6,false);String[] labels={"每实际运行小时食物（份）","口粮名称（例如：面包）","待遇需求下限（0–100）","待遇需求上限（0–100）","实际待遇（暂由你设定）","指定公共粮仓"};for(int i=0;i<labels.length;i++)g.drawString(font,labels[i],left+(i%2)*(w/2),64+(i/2)*39,0xffded7ca,false);
        if(height>=280){g.drawString(font,"1小时=正常速度60分钟；2份/小时即每30分钟1份。",left,183,0xffc6bfaf,false);g.drawString(font,"低于下限：不足；达到上限：满意；中间：可接受。",left,198,0xffc6bfaf,false);g.drawString(font,"这一版不自动增加欲望、不派快递员、不自动迁出。",left,213,0xffc6bfaf,false);}
        super.render(g,x,y,delta);g.drawString(font,font.plainSubstrByWidth((pending?"正在提交…":"")+message,w),left,height-24,0xffffce7d,false);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float delta){} @Override public boolean isPauseScreen(){return false;}
}

