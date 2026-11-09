package com.muxigame.buildingarchive;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
/** Small side panel leaves the world projection visible. All changes are server validated. */
public final class ArchivePlacementScreen extends Screen {
 private CompoundTag data;private boolean pending,confirm;private int step=1,wait;private String message;
 public ArchivePlacementScreen(CompoundTag t){super(Component.literal("建筑投影 · 调整位置"));data=t;message=t.getString("Message");}
 private Button button(String text,int x,int y,int w,Runnable action){var b=addRenderableWidget(Button.builder(Component.literal(text),v->action.run()).bounds(x,y,w,18).build());b.active=!pending;return b;}
 @Override protected void init(){clearWidgets(); if(data.getBoolean("DeletePreview")){button("返回现场核对红框",18,79,184,this::onClose);button("确认删除范围内所有方块",18,103,184,()->send("delete_confirm",""));button("取消删除",18,125,184,()->send("cancel",""));button("使用帮助",18,147,184,()->minecraft.setScreen(new ArchiveHelpScreen(this)));return;}
  button("步长："+step+"格",18,79,88,()->{step=step==1?5:1;confirm=false;init();});button("返回现场",114,79,88,this::onClose);
  button("西 ←",18,101,88,()->move(-step,0,0));button("→ 东",114,101,88,()->move(step,0,0));
  button("北 −Z",18,121,88,()->move(0,0,-step));button("南 +Z",114,121,88,()->move(0,0,step));
  button("上升 ↑",18,141,88,()->move(0,step,0));button("下降 ↓",114,141,88,()->move(0,-step,0));
  button("左转90°",18,161,88,()->send("rotate_left",""));button("右转90°",114,161,88,()->send("rotate_right",""));button("左右镜像",18,181,58,()->send("mirror",""));
  var protect=button("地基："+(data.getBoolean("PreserveFoundation")?"开":"关"),81,181,58,()->send("foundation_mode",data.getBoolean("PreserveFoundation")?"off":"on"));protect.active=!pending&&data.getBoolean("FoundationSupported");protect.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("保留底部 "+data.getInt("FoundationLayers")+" 层空气位置的原地形；以上空气照常覆盖。")));
  button("地基设置",144,181,58,()->minecraft.setScreen(new ArchiveScreen(data,true)));
  boolean occupied=data.getInt("Occupied")>0;
  var place=button(occupied?(confirm?"再次确认覆盖（按地基设置）":"覆盖放置…"):"确认放置建筑",18,201,184,()->{if(!occupied)send("paste","");else if(confirm)send("replace","");else{confirm=true;message=data.getBoolean("PreserveFoundation")?"地基层空气保留原地形；以上空气及建筑实块会覆盖目标。再次点击确认。":"覆盖也会清空原有方块（含地基层空气）！再次点击确认。";init();}});place.active=!pending&&data.getBoolean("CanPaste");
  place.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(data.getBoolean("PreserveFoundation")?"保留底部 "+data.getInt("FoundationLayers")+" 层空气位置的原地形；地基以上空气及建筑实块仍会覆盖目标。":"地基保护已关闭；包括地基层在内的保存空气都会清空目标。")));
  button("取消投影",18,221,88,()->send("cancel",""));button("切换建筑",114,221,88,()->send("library",""));
 }
 private void move(int x,int y,int z){send("move",x+","+y+","+z);}
 private void send(String op,String value){if(pending)return;pending=true;wait=100;confirm=false;PacketDistributor.sendToServer(new ArchiveNetwork.Request(op,value,data.getLong("Revision"),0));init();}
 public void update(CompoundTag t){data=t;pending=false;wait=0;confirm=false;message=t.getString("Message");init();}
 @Override public void tick(){if(wait>0&&--wait==0){pending=false;message="请求超时，请返回现场再右键打开。";init();}}
 @Override public void render(GuiGraphics g,int mx,int my,float partial){g.fill(8,8,212,Math.min(height-4,314),0xde142532);g.drawString(font,data.getBoolean("DeletePreview")?Component.literal("删除建筑 · 核对红框"):Component.literal("建筑投影 · 旋转 "+data.getInt("Rotation")+"°"),18,18,0xffe4dbc0,false);g.drawString(font,"位置："+data.getString("Anchor"),18,34,0xffe0e8e9,false);g.drawString(font,"尺寸："+data.getString("LoadedSize"),18,47,0xffe0e8e9,false);int[] size=data.getCompound("Projection").getIntArray("Size");g.drawString(font,"底Y "+data.getInt("AnchorY")+" / 顶Y "+(data.getInt("AnchorY")+(size.length==3?size[1]-1:0))+(data.getBoolean("PreserveFoundation")&&data.getInt("FoundationLayers")>0?" / 基顶Y "+(data.getInt("AnchorY")+data.getInt("FoundationLayers")-1):""),18,60,0xff8bd6cc,false);
  String note=message.isEmpty()?"蓝色：简化外形；黄色：整体范围。绿色：底面。":message;if(!data.getBoolean("DeletePreview")&&data.getCompound("Projection").getIntArray("Runs").length==0)note="仅显示整体范围（建筑为空或外形过于复杂）。";if(data.getBoolean("DeletePreview"))note="红框内方块和容器内容都会删除，不掉落物品；不删除NPC和生物。确认有效期30秒，可撤销。";int yy=data.getBoolean("DeletePreview")?175:245;for(var line:font.split(Component.literal(note),184)){if(yy+9>height-4||yy>303)break;g.drawString(font,line,18,yy,0xffffbc77,false);yy+=10;}super.render(g,mx,my,partial);
 }
 @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
 @Override public boolean isPauseScreen(){return false;}
}


