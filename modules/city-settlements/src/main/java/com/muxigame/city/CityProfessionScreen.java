package com.muxigame.city;
import java.util.*;
import com.google.gson.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.network.PacketDistributor;
/** Chinese names are resolved to stable block IDs only at the network boundary. */
public final class CityProfessionScreen extends Screen {
 private CompoundTag data;private final Screen parent;private CityMaintenance.Worker w;private EditBox rate,priority,blocks;private String message="";private int left;private String[] draft;
 public CityProfessionScreen(CompoundTag t,Screen parent){super(Component.literal("职业与修补"));this.parent=parent;load(t);}
 private void load(CompoundTag t){data=t;w=CityEconomy.JSON.fromJson(t.getString("Worker"),CityMaintenance.Worker.class);draft=null;message=t.getString("Message");}
 public void update(CompoundTag t){load(t);rebuildWidgets();}
 static String name(String id){return BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse(id)).getName().getString();}
 private void remember(){if(rate!=null)draft=new String[]{rate.getValue(),priority.getValue(),blocks.getValue()};}
 public void removed(){remember();super.removed();}public void resize(net.minecraft.client.Minecraft mc,int x,int y){remember();super.resize(mc,x,y);}
 private void button(String label,int x,int y,int width,Runnable r){addRenderableWidget(Button.builder(Component.literal(label),b->r.run()).bounds(x,y,width,20).build());}
 private EditBox field(String value,int x,int y,int width){var b=addRenderableWidget(new EditBox(font,x,y,width,18,Component.literal(value)));b.setMaxLength(2048);b.setValue(value);return b;}
 protected void init(){left=width/2-160;button("职业："+w.role,left,40,150,()->{remember();var roles=List.of("无职业","农民","工匠","自定义");w.role=roles.get((roles.indexOf(w.role)+1)%4);w.enabled=!w.role.equals("无职业");w.artisan=w.role.equals("工匠");w.blocks=new ArrayList<>(w.role.equals("农民")?CityMaintenance.FARM:List.of());draft[2]=String.join("，",w.blocks.stream().map(CityProfessionScreen::name).toList());rebuildWidgets();});button(w.enabled?"职业：启用":"职业：停用",left+170,40,150,()->{remember();w.enabled=!w.enabled;rebuildWidgets();});rate=field(draft==null?""+w.perHour:draft[0],left,83,150);priority=field(draft==null?""+w.priority:draft[1],left+170,83,150);blocks=field(draft==null?String.join("，",w.blocks.stream().map(CityProfessionScreen::name).toList()):draft[2],left,124,320);
 button(w.artisan?"工匠能力：开（负责其余方块）":"工匠能力：关",left,151,320,()->{remember();w.artisan=!w.artisan;rebuildWidgets();});button("工作时间 / 猎户巡逻",left,213,320,()->PacketDistributor.sendToServer(new CityNetwork.Request("workview",data.getString("Npc"),0,0,0,0,0,0,0,"",false)));button("保存",left,height-30,100,this::save);button("重读",left+110,height-30,100,()->send("maintworker",data.getString("Npc")));button("返回",left+220,height-30,100,this::onClose);}
 private void save(){try{w.perHour=Integer.parseInt(rate.getValue());w.priority=Integer.parseInt(priority.getValue());var names=new ArrayList<String>();for(String value:blocks.getValue().split("[,，、]")){value=value.trim();if(value.isEmpty())continue;if(value.equals("流动的水")||value.equals("流动水"))value="水";if(value.equals("小麦种子"))value="小麦";String chosen=null;for(var b:BuiltInRegistries.BLOCK)if(b.getName().getString().equals(value)){chosen=BuiltInRegistries.BLOCK.getKey(b).toString();break;}if(chosen==null)throw new IllegalArgumentException("找不到方块："+value+"；请使用中文方块名称");if(!names.contains(chosen))names.add(chosen);}w.blocks=names;var check=new CityMaintenance.State();check.workers.put(UUID.fromString(data.getString("Npc")),w);CityMaintenance.validate(check);var json=new JsonObject();json.addProperty("npc",data.getString("Npc"));json.add("worker",CityEconomy.JSON.toJsonTree(w));send("maintsave",json.toString());message="正在保存…";}catch(RuntimeException ex){message="未保存："+ex.getMessage();}}
 private void send(String op,String value){PacketDistributor.sendToServer(new CityNetwork.Request(op,data.getString("City"),data.getLong("Revision"),0,0,0,0,0,0,value,false));}
 public void render(GuiGraphics g,int x,int y,float d){g.fill(0,0,width,height,0xff292b28);g.drawCenteredString(font,"NPC职业与修补",width/2,16,0xfff2dfb6);g.drawString(font,"效率（格/运行小时）",left,69,0xffddddcc);g.drawString(font,"优先级（越大越先）",left+170,69,0xffddddcc);g.drawString(font,"负责的方块（中文名称，用逗号分隔）",left,110,0xffddddcc);if(height>235){g.drawString(font,"农民预设含耕地、水、小麦；种子对应补种幼苗。",left,180,0xffbfcbb8);g.drawString(font,"工匠修其余方块；不走路，不恢复箱内物品。",left,194,0xffbfcbb8);}g.drawString(font,font.plainSubstrByWidth(message,320),left,height-46,0xffffcc77);super.render(g,x,y,d);}
 public void onClose(){minecraft.setScreen(parent);}public boolean isPauseScreen(){return false;}public void renderBackground(GuiGraphics g,int x,int y,float d){}
}

