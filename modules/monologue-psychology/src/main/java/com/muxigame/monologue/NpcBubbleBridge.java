package com.muxigame.monologue;

import java.lang.reflect.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** Optional bridge calls the installed CustomNPCs renderer; no recovered source is bundled. */
public final class NpcBubbleBridge {
    private static Constructor<?> constructor;
    private static Method add,render;
    private static boolean initialized,available;
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(NpcBubbleBridge.class);
    public static Object create(String text){
        if(!initialized){initialized=true;try{
            Class<?> type=Class.forName("noppes.npcs.client.ChatMessages");constructor=type.getConstructor();
            add=type.getMethod("addMessage",String.class,Class.forName("noppes.npcs.entity.EntityNPCInterface"));
            render=type.getMethod("renderMessages",PoseStack.class,MultiBufferSource.class,float.class,boolean.class,int.class);
            available=true;LOG.info("Inner Monologue: using native CustomNPCs speech bubble renderer");
        }catch(ReflectiveOperationException e){LOG.info("Inner Monologue: CustomNPCs bubble API unavailable; using simple fallback");}}
        if(!available)return null;
        try{Object bubble=constructor.newInstance();add.invoke(bubble,wrapped(text),null);return bubble;}
        catch(ReflectiveOperationException e){available=false;LOG.warn("Inner Monologue: bubble bridge disabled after API error",e);return null;}
    }
    private static String wrapped(String text){
        Font font=Minecraft.getInstance().font;StringBuilder all=new StringBuilder(),line=new StringBuilder();
        for(int code:text.codePoints().toArray()){
            String chr=new String(Character.toChars(code));
            if(!line.isEmpty()&&font.width(line+chr)>176){all.append(line).append('\n');line.setLength(0);}line.append(chr);
        }
        return all.append(line).toString();
    }
    public static void draw(Object nativeBubble,String text,PoseStack pose,MultiBufferSource buffers,float scale,boolean near,int light){
        if(nativeBubble!=null&&available){try{render.invoke(nativeBubble,pose,buffers,scale,near,light);return;}catch(ReflectiveOperationException e){available=false;LOG.warn("Inner Monologue: native render failed",e);}}
        var mc=Minecraft.getInstance();pose.pushPose();
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());pose.scale(-.02f*scale,-.02f*scale,.02f*scale);
        var lines=mc.font.split(Component.literal(text),176);int y=-lines.size()*10;
        for(var line:lines){mc.font.drawInBatch(line,-mc.font.width(line)/2f,y,0xff101010,false,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0xffeeeeee,light);y+=10;}
        pose.popPose();
    }
}
