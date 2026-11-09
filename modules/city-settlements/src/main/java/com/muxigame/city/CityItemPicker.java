package com.muxigame.city;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
public final class CityItemPicker extends Screen {
 final Screen parent;final Consumer<String> picked;String query="";int page;
 public CityItemPicker(Screen parent,Consumer<String> picked){super(Component.literal("选择商品"));this.parent=parent;this.picked=picked;}
 protected void init(){var search=addRenderableWidget(new EditBox(font,16,36,width-32,20,Component.literal("中文名称或物品ID")));search.setValue(query);search.setHint(Component.literal("搜索中文名称或物品ID"));search.setResponder(v->{query=v;page=0;rebuildWidgets();});setInitialFocus(search);var items=BuiltInRegistries.ITEM.stream().filter(i->i!=Items.AIR).filter(i->(new ItemStack(i).getHoverName().getString()+BuiltInRegistries.ITEM.getKey(i)).toLowerCase().contains(query.toLowerCase())).toList();int rows=Math.max(1,(height-110)/26),pages=Math.max(1,(items.size()+rows-1)/rows);page=Math.clamp(page,0,pages-1);for(int n=0;n<rows&&page*rows+n<items.size();n++){var item=items.get(page*rows+n);String id=BuiltInRegistries.ITEM.getKey(item).toString();String name=new ItemStack(item).getHoverName().getString();addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(name+" · "+id,width-48)),b->picked.accept(id)).bounds(16,68+n*26,width-32,22).build());}addRenderableWidget(Button.builder(Component.literal("上一页"),b->{page=Math.max(0,page-1);rebuildWidgets();}).bounds(16,height-30,90,20).build());addRenderableWidget(Button.builder(Component.literal("下一页"),b->{page=Math.min(pages-1,page+1);rebuildWidgets();}).bounds(114,height-30,90,20).build());addRenderableWidget(Button.builder(Component.literal("取消"),b->onClose()).bounds(width-106,height-30,90,20).build());}
 public void render(GuiGraphics g,int x,int y,float f){g.fill(0,0,width,height,0xff14212c);g.drawString(font,"选择商品 · 支持中文搜索",16,14,0xffffdd99);super.render(g,x,y,f);}public void onClose(){minecraft.setScreen(parent);}public boolean isPauseScreen(){return false;}public void renderBackground(GuiGraphics g,int x,int y,float f){}
}
