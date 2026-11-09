package com.muxigame.buildingarchive;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server snapshots drive every action; the client never supplies world coordinates or structure NBT. */
public final class ArchiveScreen extends Screen {
 private String renaming="",renameDraft="";private EditBox renameBox,foundation;private CompoundTag data;private EditBox name,low,high;private boolean pending,confirmReplace,confirmUndo;private int wait;private String message;
 public ArchiveScreen(CompoundTag data){super(Component.literal("建筑档案"));this.data=data;message=data.getString("Message");}
 boolean foundationSettings;
 public ArchiveScreen(CompoundTag data,boolean foundationSettings){this(data);this.foundationSettings=foundationSettings;}
 private int x(){return (width-370)/2;}private int y(){return Math.max(2,(height-242)/2);}
 private Button button(String label,int x,int y,int w,Runnable action,boolean enabled){var b=addRenderableWidget(Button.builder(Component.literal(label),v->action.run()).bounds(x,y,w,18).build());b.active=enabled&&!pending;return b;}
 @Override protected void init(){String draft=name==null?"":name.getValue();clearWidgets();int x=x(),y=y();
  button("重新选区",x+10,y+24,82,()->send("select",""),true);button(foundationSettings?"返回投影":"返回现场",x+96,y+24,82,this::onClose,true);button("刷新档案",x+182,y+24,82,()->send("refresh",""),true);button("使用帮助",x+268,y+24,92,()->minecraft.setScreen(new ArchiveHelpScreen(this)),true);
  name=addRenderableWidget(new EditBox(font,x+10,y+74,164,18,Component.literal("建筑名称")));name.setMaxLength(64);name.setHint(Component.literal("建筑名称（可中文 / 留空自动命名）"));name.setValue(draft);
  button("保存选区",x+10,y+96,78,()->send("export",name.getValue()),data.getBoolean("Valid")&&!data.getBoolean("Loaded"));
  button(confirmUndo?"确认撤销":"撤销上次操作",x+92,y+96,82,()->{if(confirmUndo)send("undo","");else{confirmUndo=true;confirmReplace=false;message="撤销将恢复原区域，也会覆盖操作后对它的修改；再次点击确认。";init();}},data.getBoolean("Undo"));
  if(data.getBoolean("Loaded")){
   name.visible=false;name.active=false;button("按准心远程定位（64格）",x+10,y+74,164,()->send("aim",""),true);
   boolean enabled=data.getBoolean("FoundationSupported");
   foundation=addRenderableWidget(new EditBox(font,x+10,y+131,78,18,Component.literal("地基层数")));foundation.setMaxLength(3);foundation.setValue(""+data.getInt("FoundationLayers"));foundation.setEditable(enabled&&!pending);
   button("保留："+(data.getBoolean("PreserveFoundation")?"开":"关"),x+96,y+131,78,()->send("foundation_mode",data.getBoolean("PreserveFoundation")?"off":"on"),enabled).setTooltip(Tooltip.create(Component.literal("保留地基周围地形：仅跳过底部指定层的空气，以上空气照常覆盖。原档案不变。")));
   button("应用层数",x+10,y+152,78,()->send("foundation_top",foundation.getValue()),enabled);
   button("自动识别",x+96,y+152,78,()->send("foundation_auto",""),enabled);
  }else{
   low=addRenderableWidget(new EditBox(font,x+10,y+131,78,18,Component.literal("最低高度")));high=addRenderableWidget(new EditBox(font,x+96,y+131,78,18,Component.literal("最高高度")));low.setMaxLength(8);high.setMaxLength(8);low.setValue(""+data.getInt("Low"));high.setValue(""+data.getInt("High"));
   button("应用上下高度",x+10,y+152,164,()->send("height",low.getValue()+","+high.getValue()),data.getBoolean("Selected"));
  }
  var files=data.getList("Files",Tag.TAG_STRING);for(int i=0;i<files.size();i++){String file=files.getString(i);if(file.equals(renaming)){renameBox=addRenderableWidget(new EditBox(font,x+192,y+62+i*20,92,18,Component.literal("新建筑名称")));renameBox.setMaxLength(64);renameBox.setValue(renameDraft);renameBox.setResponder(v->renameDraft=v);renameBox.setEditable(!pending);button("保存",x+286,y+62+i*20,36,()->send("rename_archive",file+"\n"+renameDraft),true);button("取消",x+324,y+62+i*20,36,()->{renaming="";init();},true);continue;}button("改名",x+286,y+62+i*20,36,()->{renaming=file;renameDraft=file;init();setInitialFocus(renameBox);},true);button(font.plainSubstrByWidth(file,82),x+192,y+62+i*20,92,()->send("load",file),true).setTooltip(Tooltip.create(Component.literal("载入："+file+"；之后右键地面预览")));button("删除",x+324,y+62+i*20,36,()->minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes->{minecraft.setScreen(this);if(yes)send("delete_archive",file);},Component.literal("删除建筑记录？"),Component.literal("将从档案列表删除「"+file+"」。世界里的建筑不会消失；记录文件会保留备份。"),Component.literal("确认删除记录"),Component.literal("取消"))),true);}
  button("上一页",x+192,y+165,80,()->page(-1),data.getInt("Page")>0);button("下一页",x+280,y+165,80,()->page(1),data.getInt("Page")+1<data.getInt("Pages"));
  if(data.getBoolean("Preview")){boolean occupied=data.getInt("Occupied")>0;button(occupied?(confirmReplace?"确认覆盖（按地基设置）":"覆盖放置…"):"确认放置",x+10,y+190,164,()->{if(!occupied)send("paste","");else if(confirmReplace)send("replace","");else{confirmReplace=true;confirmUndo=false;message="目标有 "+data.getInt("Occupied")+" 格待覆盖方块；"+(data.getBoolean("PreserveFoundation")?"地基层空气保留原地形，以上空气清空目标。":"保存的空气会清空目标。")+"再次点击确认。";init();}},data.getBoolean("CanPaste"));button("取消目标",x+192,y+190,168,()->send("cancel",""),true);}
  else button(data.getBoolean("Loaded")?"返回现场：右键地面预览":"返回现场：右键选角",x+10,y+190,350,this::onClose,true);
 }
 private void page(int delta){renaming="";data.putInt("Page",data.getInt("Page")+delta);send("refresh","");}
 private void send(String op,String value){if(pending)return;pending=true;wait=100;confirmReplace=false;confirmUndo=false;PacketDistributor.sendToServer(new ArchiveNetwork.Request(op,value,data.getLong("Revision"),data.getInt("Page")));init();}
 public void update(CompoundTag next){data=next;pending=false;wait=0;confirmReplace=false;confirmUndo=false;message=data.getString("Message");if(message.startsWith("建筑记录已命名"))renaming="";init();}
 @Override public void tick(){if(wait>0&&--wait==0){pending=false;message="请求未返回，请点击刷新档案后再操作。";init();}}
 private void text(GuiGraphics g,String value,int x,int y,int width,int color){g.drawString(font,font.plainSubstrByWidth(value,width),x,y,color,false);}
 @Override public void render(GuiGraphics g,int mx,int my,float partial){int x=x(),y=y();g.fill(x,y,x+370,y+242,0xf0182735);g.drawString(font,"建筑档案 · 选区 → 保存 → 预览 → 放置",x+10,y+9,0xffe4dbc0,false);
  String status=data.getBoolean("Loaded")?"已载入 "+data.getString("Name"):"选区 "+data.getString("A")+" → "+data.getString("B");text(g,status,x+10,y+47,350,0xff8bd6cc);
  text(g,data.getBoolean("Loaded")?data.getInt("NonAir")+" 块 / "+data.getInt("Kinds")+" 种 · "+data.getString("LoadedSize"):"选区尺寸："+data.getString("Size"),x+10,y+61,170,0xffd1dadc);
  g.drawString(font,"档案 "+(data.getInt("Page")+1)+" / "+data.getInt("Pages")+" 页 · "+data.getInt("Total")+" 份",x+192,y+49,0xffd1dadc,false);
  boolean loaded=data.getBoolean("Loaded");g.drawString(font,loaded?"地基层数":"最低 Y",x+10,y+119,0xffa8bdc7,false);g.drawString(font,loaded?"地基周围地形":"最高 Y",x+96,y+119,0xffa8bdc7,false);
  int layers=data.getInt("FoundationLayers");String tip=data.getBoolean("Preview")?"原点："+data.getString("Anchor")+(layers>0?" · 地基顶Y="+(data.getInt("AnchorY")+layers-1):" · 无地基"):loaded?"地基为底部 "+layers+" 层；0=不保护，可手动调整":"两个角点需包含建筑屋顶，可在上方调整高度";text(g,tip,x+10,y+177,350,0xffa8bdc7);
  String note=message.isEmpty()?"地基以上空气始终覆盖；开放门洞/柱式地基请核对并调整层数。":message;int yy=y+214;for(var line:font.split(Component.literal(note),350)){if(yy>y+234)break;g.drawString(font,line,x+10,yy,0xffffbc77,false);yy+=10;}
  super.render(g,mx,my,partial);
  if(mx>=x+10&&mx<x+180&&my>=y+47&&my<y+73&&data.getBoolean("Loaded")){var lines=new java.util.ArrayList<Component>();lines.add(Component.literal("主要方块（完整统计见 .analysis.json）"));for(var row:data.getList("Materials",Tag.TAG_COMPOUND)){CompoundTag m=(CompoundTag)row;var id=ResourceLocation.tryParse(m.getString("Id"));lines.add(Component.literal((id==null?m.getString("Id"):BuiltInRegistries.BLOCK.get(id).getName().getString())+" × "+m.getInt("Count")));}g.renderComponentTooltip(font,lines,mx,my);}
 }
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float p){}
 @Override public void onClose(){if(foundationSettings&&data.getBoolean("Preview"))minecraft.setScreen(new ArchivePlacementScreen(data));else super.onClose();}
 @Override public boolean isPauseScreen(){return false;}
}



