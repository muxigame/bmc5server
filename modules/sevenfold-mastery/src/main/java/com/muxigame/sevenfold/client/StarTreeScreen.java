package com.muxigame.sevenfold.client;
import com.muxigame.sevenfold.*;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import com.muxigame.sevenfold.ui.TreeCamera;
import com.muxigame.sevenfold.ui.TreeLayout;
import com.muxigame.sevenfold.ui.TreeReturnEdge;
import com.muxigame.sevenfold.ui.TreePassage;
import com.muxigame.sevenfold.ui.TreeZoom;
import com.muxigame.sevenfold.ui.TreeLight;
import com.muxigame.sevenfold.ui.TreeAudioChange;
import net.minecraft.ChatFormatting;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;

/** Click a whole constellation to explore its ten stars. No upgrade tracks. */
public final class StarTreeScreen extends Screen {
    private final Screen parent;
    private StarNode focus,held;private int heldStar=-1;private boolean completed,targetLit,preview,demo,heldOverview;
    private StarTreeState drawn;private long began,lastSend,demoAt,lastFrame;
    private Button demoButton,more;
    private boolean menuOpen;
    private final TreeCamera overviewCamera=new TreeCamera(),detailCamera=new TreeCamera();
    private final TreeReturnEdge returnEdge=new TreeReturnEdge();
    private final TreePassage passage=new TreePassage();
    private final TreeZoom zoom=new TreeZoom();
    private final TreeLight light=new TreeLight();
    private final StarSounds audio=new StarSounds();
    private StarNode zoomNode;private double visualZoom;
    private String status="";private StarGatherSound sound;
    private final double[] exposure=new double[8];
    private final long[][] ignitedAt=new long[8][10];
    private final long[] completedAt=new long[8],revealedAt=new long[8],activatedAt=new long[8];
    private record Point(double x,double y){}
    private record Hit(StarNode node,int star){}
    public StarTreeScreen(Screen parent){super(Component.translatable("ui.sevenfold_mastery.star_tree"));this.parent=parent;}
    StarTreeScreen previewOnly(boolean explore){preview=true;if(explore)focus=StarNode.WARRIOR;return this;}
    private static Component text(String key,Object...args){return Component.translatable("tree.sevenfold_mastery."+key,args);}
    @Override protected void init(){
        cancel();resetAnimations();returnEdge.reset();lastFrame=Util.getMillis();
        passage.open(lastFrame);
        if(focus!=null&&zoomNode==null){zoomNode=focus;zoom.snap(true);visualZoom=1;}
        minecraft.mouseHandler.releaseMouse();GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(),GLFW.GLFW_CURSOR,GLFW.GLFW_CURSOR_NORMAL);minecraft.getMusicManager().stopPlaying();
        audio.enter(this,()->passage.sky(Util.getMillis()));
        double scale=TreeLayout.scale(width,height);overviewCamera.bounds(-2000*scale,2000*scale,-1800*scale,0);detailCamera.bounds(-width*.45,width*.45,-height*.4,0);
        overviewCamera.waitForPointerMotion();detailCamera.waitForPointerMotion();
        more=addRenderableWidget(new QuietButton(width-52,8,18,Component.literal("…"),b->{cancel();menuOpen=!menuOpen;}));
        demoButton=addRenderableWidget(new QuietButton(width-112,32,104,text("demo"),b->{cancel();demo=!demo;demoAt=Util.getMillis();focus=null;zoomNode=null;zoom.snap(false);visualZoom=0;detailCamera.reset();overviewCamera.waitForPointerMotion();menuOpen=false;resetAnimations();b.setMessage(text(demo?"end_demo":"demo"));}));
        MasteryClient.starSend("read",null);
    }
    public void received(){
        var next=MasteryClient.stars;if(next==null)return;
        if(!demo&&focus!=null&&focus.path!=null&&!next.isComplete(focus.path))zoomTo(null);
        if(held!=null&&(held==StarNode.RESET?next.path()==null:heldStar==StarTreeState.CONSTELLATION?next.isActivated(held)==targetLit:next.star(held,heldStar)==targetLit)){completed=true;status=targetLit?"lit":"unlit";stopSound();}
    }
    private StarTreeState shown(){
        if(!demo)return MasteryClient.stars==null?new StarTreeState(0):MasteryClient.stars;
        long t=(Util.getMillis()-demoAt)%44000;var s=new StarTreeState(0);if(t>=1400)s=s.toggle(StarNode.CRYING,0);
        if(t>=31000)s=demoStars(s,StarNode.MAGE,t-31000);
        else if(t>=21000)s=demoStars(s,StarNode.ASSASSIN,t-21000);
        else if(t>=4000){s=demoStars(s,StarNode.WARRIOR,t-4000);if(t>=12000)s=demoStars(s,StarNode.BERSERKER,t-12000);}
        focus=t>=40500?null:t>=32200?StarNode.MAGE:t>=31000?null:t>=22200?StarNode.ASSASSIN:t>=21000?null:t>=13200?StarNode.BERSERKER:t>=12000?null:t>=5200?StarNode.WARRIOR:null;if(BuildVariant.LITE&&focus!=null&&focus.path!=null)focus=null;return s;
    }
    private static StarTreeState demoStars(StarTreeState s,StarNode n,long elapsed){s=s.toggle(n,StarTreeState.CONSTELLATION);if(elapsed>=1200)for(int i=0;i<Math.min(10,(elapsed-1200)/500+1);i++)s=s.toggle(n,i);return s;}
    private void resetAnimations(){drawn=null;light.reset();java.util.Arrays.fill(exposure,0);java.util.Arrays.fill(completedAt,0);java.util.Arrays.fill(revealedAt,0);java.util.Arrays.fill(activatedAt,0);for(var row:ignitedAt)java.util.Arrays.fill(row,0);}
    private static double age(long at,long now){return at==0?1e9:now-at;}
    private static double smooth(double f){f=Math.clamp(f,0,1);return f*f*(3-2*f);}
    private double visibility(StarNode n,long now){return light.visible(n,now)*(revealedAt[n.ordinal()]==0?1:smooth(age(revealedAt[n.ordinal()],now)/1200));}
    private void animateChanges(StarTreeState s,long now){
        light.update(s,now);
        if(drawn==null){drawn=s;for(var n:StarNode.values())if(n!=StarNode.RESET)exposure[n.ordinal()]=s.isActivated(n)?.3+.7*s.count(n)/10.0:0;return;}
        var soundChange=TreeAudioChange.between(drawn,s);
        if(s.active()&&!drawn.active())for(int i=0;i<StarNode.BASES.length;i++)revealedAt[StarNode.BASES[i].ordinal()]=now+i*220;
        for(var n:StarNode.values())if(n!=StarNode.RESET){int slot=n.ordinal();
            if(s.isActivated(n)&&!drawn.isActivated(n))activatedAt[slot]=now;else if(!s.isActivated(n))activatedAt[slot]=0;
            for(int i=0;i<10;i++){if(s.star(n,i)&&!drawn.star(n,i))ignitedAt[slot][i]=now;else if(!s.star(n,i))ignitedAt[slot][i]=0;}
            if(s.isComplete(n)&&!drawn.isComplete(n))completedAt[slot]=now;else if(!s.isComplete(n))completedAt[slot]=0;
        }
        if(soundChange.extinction())audio.extinguish(soundChange.majorExtinction());
        if(soundChange.ignition())audio.ignite(soundChange.majorIgnition());
        drawn=s;
    }
    private Point overviewAnchor(StarNode n){
        var p=TreeLayout.position(n);double scale=TreeLayout.scale(width,height);
        return new Point(width*.5+p.x()*scale-overviewCamera.x(),height*.42-Math.max(0,300-height)*.3+p.y()*scale-overviewCamera.y());
    }
    private StarNode visualSubject(){return demo?focus:zoomNode;}
    private double detailWeight(){return Math.clamp(visualZoom,0,1);}
    private Point anchor(StarNode n){
        var p=overviewAnchor(n);var subject=visualSubject();if(subject==null)return p;
        var origin=overviewAnchor(subject);var end=detailCenter();double ratio=detailScale()/18;
        return new Point(TreeZoom.project(p.x,origin.x,end.x,ratio,visualZoom),TreeZoom.project(p.y,origin.y,end.y,ratio,visualZoom));
    }
    private Point detailCenter(){return new Point(width*.5-detailCamera.x(),height*.55-detailCamera.y());}
    private double detailScale(){return Math.min(width*.34,Math.max(44,(height-80)*.65));}
    private Point star(StarNode n,int index){
        var xy=n.pattern()[index];Point center=anchor(n);
        double scale=visualSubject()==null?18:TreeZoom.mix(18,detailScale(),visualZoom);
        // A receding, tilted plane, retaining the original oblique pixel style.
        double perspective=1/(1+(1-xy[1])*.10);
        return new Point(center.x+(xy[0]+xy[1]*.14)*scale*perspective,center.y+xy[1]*scale*.66*perspective);
    }
    private boolean available(StarNode n,StarTreeState s){return s.visible(n);}
    private Hit at(double x,double y){
        if(!demo&&zoom.moving(Util.getMillis()))return null;
        if(TreeReturnEdge.contains(x,y,width,height))return null;
        if(demo)return null;var s=shown();
        if(menuOpen){if(s.path()!=null&&x>=width-112&&x<width-8&&y>=54&&y<72)return new Hit(StarNode.RESET,0);return null;}
        if(y<32)return null;
        if(focus==null){for(var n:StarNode.values())if(n!=StarNode.RESET&&available(n,s)){var p=anchor(n);if(Math.hypot(x-p.x,(y-p.y)*1.3)<37)return new Hit(n,-1);}return null;}
        if(x>=width*.5-font.width(focus.label())*.5-10&&x<width*.5+font.width(focus.label())*.5+10&&y>=37&&y<57)return new Hit(focus,StarTreeState.CONSTELLATION);
        if(BuildVariant.LITE&&focus.base())return null;
        double best=13;int selected=-1;for(int i=0;i<10;i++){var p=star(focus,i);double d=Math.hypot(x-p.x,y-p.y);if(d<best){best=d;selected=i;}}
        if(selected<0&&focus==StarNode.WARRIOR)for(int i=0;i<10;i++){var p=star(focus,i);if(Math.abs(x-p.x)<12&&y>=p.y+10&&y<p.y+23)return new Hit(focus,i);}
        return selected<0?null:new Hit(focus,selected);
    }
    private void stopSound(){if(sound!=null)sound.fadeOut();sound=null;}
    private void cancel(){stopSound();if(held!=null)MasteryClient.starSend("cancel",null);held=null;heldStar=-1;heldOverview=false;completed=false;}
    private void zoomTo(StarNode node){
        if((node!=null)!=(focus!=null))audio.motion(node!=null,false);
        cancel();returnEdge.reset();menuOpen=false;overviewCamera.stop();detailCamera.stop();
        if(node!=null){zoomNode=node;focus=node;detailCamera.reset();detailCamera.waitForPointerMotion();zoom.towards(true,Util.getMillis());}
        else {focus=null;zoom.towards(false,Util.getMillis());overviewCamera.waitForPointerMotion();}
        status="";
    }
    private boolean returnAvailable(){return TreeReturnEdge.available((focus==null?overviewCamera:detailCamera).y(),menuOpen||demo);}
    private void bottomBack(){
        cancel();returnEdge.reset();overviewCamera.stop();detailCamera.stop();
        if(demo){demo=false;focus=null;zoomNode=null;zoom.snap(false);menuOpen=false;resetAnimations();demoButton.setMessage(text("demo"));overviewCamera.waitForPointerMotion();}
        else if(menuOpen)menuOpen=false;
        else if(focus!=null)zoomTo(null);
        else onClose();
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(!passage.interactive(Util.getMillis()))return true;
        if(zoom.moving(Util.getMillis()))return true;
        if(button==0&&TreeReturnEdge.contains(x,y,width,height)){if(returnAvailable())bottomBack();return true;}
        var hit=at(x,y);if(button==0&&hit!=null){
            cancel();var s=MasteryClient.stars;if(s==null){status="loading";MasteryClient.starSend("read",null);return true;}
            int index=hit.star;
            if(!s.canToggle(hit.node,index)){status=s.visible(hit.node)&&!s.isActivated(hit.node)?"needs_activation":"rank_locked";return true;}
            held=hit.node;heldStar=index;heldOverview=index==StarTreeState.CONSTELLATION;targetLit=held!=StarNode.RESET&&!(heldOverview?s.isActivated(held):s.star(held,heldStar));began=Util.getMillis();lastSend=0;
            sound=new StarGatherSound(()->Math.clamp((Util.getMillis()-began)/1200.0,0,1),targetLit);minecraft.getSoundManager().play(sound);
            MasteryClient.starSend("begin",held,heldStar);return true;
        }boolean result=super.mouseClicked(x,y,button);if(!result&&menuOpen){menuOpen=false;cancel();return true;}return result;
    }
    @Override public boolean mouseReleased(double x,double y,int button){if(button==0&&held!=null){
        var selected=held;boolean explore=heldOverview&&selected!=StarNode.CRYING&&!completed&&Util.getMillis()-began<1200;
        if(!completed)status=heldOverview?(selected==StarNode.CRYING?"hold_departure":"hold_constellation"):"cancelled";
        cancel();if(explore){zoomTo(selected);status=shown().isActivated(selected)?"":"needs_activation";}return true;
    }return super.mouseReleased(x,y,button);}
    @Override public void mouseMoved(double x,double y){if(held!=null){var h=at(x,y);if(h==null||h.node!=held||h.star!=heldStar){if(!completed)status="cancelled";cancel();}}super.mouseMoved(x,y);}
    @Override public void tick(){
        audio.tick();
        if(passage.finished(Util.getMillis())){minecraft.setScreen(parent);return;}
        if(!demo&&focus==null&&zoomNode!=null&&!zoom.moving(Util.getMillis())){zoomNode=null;detailCamera.reset();overviewCamera.waitForPointerMotion();}
        if(held==null)return;if(!minecraft.isWindowActive()||GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(),0)!=GLFW.GLFW_PRESS){cancel();return;}
        long now=Util.getMillis();if(!completed&&now-began>=1200&&now-lastSend>=150){MasteryClient.starSend("finish",held,heldStar);lastSend=now;}if(!completed&&now-began>10000){status="timeout";cancel();}
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        double sky=passage.sky(Util.getMillis());
        g.fill(0,0,width,height,0xFF01030A);
        if(sky<.999&&parent!=null){
            g.pose().pushPose();g.pose().translate(0,height*sky,0);parent.render(g,-10000,-10000,partial);g.pose().popPose();
        }
        boolean moving=sky<.999;if(moving)g.enableScissor(0,0,width,Math.max(1,(int)Math.ceil(height*sky)));
        g.pose().pushPose();g.pose().translate(0,-height*(1-sky),200);
        renderTree(g,mx,my,partial);g.pose().popPose();
        if(moving)g.disableScissor();
        if(sky>0&&sky<1){
            // The inventory sinks as the view lifts; pixel mist softens the moving horizon.
            int seam=(int)(height*sky),band=Math.max(12,height/10);
            for(int y=Math.max(0,seam-band);y<Math.min(height,seam+band);y+=2){double haze=(1-Math.abs(y-seam)/(double)band)*Math.sin(Math.PI*sky);g.fill(0,y,width,Math.min(height,y+2),StarArt.alpha(0x07101C,haze*.8));}
        }
    }
    private void renderTree(GuiGraphics g,int mx,int my,float partial){
        long now=Util.getMillis();var s=shown();double time=now/1000.0,dt=Math.min(.1,(now-lastFrame)/1000.0);lastFrame=now;
        var camera=focus==null?overviewCamera:detailCamera;
        visualZoom=demo?(focus==null?0:1):zoom.value(now);
        boolean ready=passage.interactive(now)&&!zoom.moving(now),returning=TreeReturnEdge.contains(mx,my,width,height)&&returnAvailable();
        returnEdge.update(ready&&returning&&minecraft.isWindowActive(),dt);
        camera.update(mx,my,width,height,dt,ready&&!returning&&!demo&&!menuOpen&&held==null&&at(mx,my)==null&&!over(more,mx,my)&&minecraft.isWindowActive());
        if(demo||MasteryClient.stars!=null)animateChanges(s,now);
        for(var n:StarNode.values())if(n!=StarNode.RESET)exposure[n.ordinal()]+=(light.active(n,now)*(.3+.7*light.average(n,now))-exposure[n.ordinal()])*Math.min(1,dt*3);
        background(g,s,time);
        var hover=ready?at(mx,my):null;
        var subject=visualSubject();
        if(subject==null){for(var n:StarNode.values())if(n!=StarNode.RESET&&light.visible(n,now)>.002)constellation(g,n,s,false,hover!=null&&hover.node==n,time);}
        else {if(detailWeight()<1)for(var n:StarNode.values())if(n!=StarNode.RESET&&n!=subject&&light.visible(n,now)>.002)constellation(g,n,s,false,false,time);constellation(g,subject,s,true,false,time);}
        if(held!=null&&!completed){Point p=held==StarNode.RESET?new Point(width-60,63):heldOverview?(focus==null?anchor(held):detailCenter()):star(held,heldStar);double f=Math.clamp((now-began)/1200.0,0,1);particles(g,p,held.color,f);
        }
        g.fillGradient(0,0,width,32,0xA0000309,0x00000309);
        g.drawCenteredString(font,title,width/2,12,0xFFB1C0CE);
        if(demo||preview)g.drawString(font,text(demo?"demo_tag":"preview_tag"),36,12,0xFF768B9D,false);
        if(menuOpen){g.fill(width-116,30,width-4,76,0xEB080F1B);demoButton.render(g,mx,my,partial);g.fill(width-112,54,width-8,72,s.path()!=null?0xB0121A29:0x8020272D);g.drawCenteredString(font,text("reset"),width-60,59,s.path()!=null?0xFFBACAD8:0xFF49515B);}
        demoButton.visible=menuOpen;
        more.render(g,mx,my,partial);
        renderReturnEdge(g);
        if(ready)hoverDetails(g,hover,s,mx,my);
    }
    private void renderReturnEdge(GuiGraphics g){
        double opacity=returnEdge.opacity();if(opacity<.002)return;
        int band=TreeReturnEdge.height(height),top=height-band;
        // Dithered two-pixel tiles: faint cool mist rising only along the lowest edge.
        for(int y=top;y<height;y+=2)for(int x=0;x<width;x+=2){
            double rise=(y-top+1)/(double)band,center=.55+.45*Math.max(0,1-Math.abs(x-width*.5)/(width*.5));
            double dither=((x/2+y/2)%2==0?.92:1.08);
            int color=StarArt.mix(0x9BB6CD,0xE1EDF3,rise);
            g.fill(x,y,Math.min(x+2,width),Math.min(y+2,height),StarArt.alpha(color,opacity*rise*rise*center*.23*dither));
        }
        int cx=width/2,cy=height-band/2;
        StarArt.glow(g,cx,cy,9,0xB9D3E5,opacity*.18);
        for(int row=0;row<5;row++){
            int dx=4-row,y=cy-3+row;
            g.fill(cx-dx-1,y,cx-dx+1,y+1,StarArt.alpha(0xD2E5F0,opacity*.88));
            g.fill(cx+dx-1,y,cx+dx+1,y+1,StarArt.alpha(0xD2E5F0,opacity*.88));
        }
    }
    private void constellation(GuiGraphics g,StarNode n,StarTreeState s,boolean enlarged,boolean hover,double time){
        long now=Util.getMillis();double dim=(s.path()!=null&&n.base()&&s.path()!=n?.5:1)*visibility(n,now);
        double weight=detailWeight();if(visualSubject()!=null&&!enlarged)dim*=Math.pow(1-weight,2);
        if(dim<.002)return;
        // These are the constellation's own outline segments, not upgrade tracks.
        for(var edge:(BuildVariant.LITE&&enlarged&&n.base()?new int[0][]:n.edges())){var a=star(n,edge[0]);var b=star(n,edge[1]);boolean both=s.star(n,edge[0])&&s.star(n,edge[1]);double lineLight=Math.min(light.star(n,edge[0],now),light.star(n,edge[1],now));StarArt.line(g,a.x,a.y,b.x,b.y,n.color,(.075+.205*lineLight)*dim,1,1);
            double t=age(Math.max(ignitedAt[n.ordinal()][edge[0]],ignitedAt[n.ordinal()][edge[1]]),now)/950;
            if(both&&t>=0&&t<1){if(ignitedAt[n.ordinal()][edge[0]]>ignitedAt[n.ordinal()][edge[1]]){var swap=a;a=b;b=swap;}double f=smooth(t);StarArt.line(g,a.x,a.y,b.x,b.y,StarArt.mix(n.color,0xFFFFFF,.65),dim*.8*(1-t),1,f);StarArt.glow(g,a.x+(b.x-a.x)*f,a.y+(b.y-a.y)*f,enlarged?12:5,n.color,dim*.65*(1-t));}
        }
        for(int i=0;i<(BuildVariant.LITE&&enlarged&&n.base()?0:10);i++){
            var p=star(n,i);boolean lit=s.star(n,i);double brightness=light.star(n,i,now),glow=(Math.sin(time*1.5+i)+1)*.5;
            double detail=enlarged?weight:0;
            if(detail<1){g.fill((int)p.x-1,(int)p.y-1,(int)p.x+2,(int)p.y+2,StarArt.alpha(StarArt.mix(n.color,StarArt.mix(n.color,0xFFFFFF,.65),brightness),dim*(.30+.65*brightness)*(1-detail)));if(brightness>.002)StarArt.glow(g,p.x,p.y,10,n.color,dim*.28*brightness*(1-detail));}
            if(detail>0){
                if(detail>=.9999)StarArt.star(g,p.x,p.y,n.color,brightness,dim,glow,false);
                else {float pixels=(float)(.25+.75*detail);g.pose().pushPose();g.pose().translate(p.x,p.y,0);g.pose().scale(pixels,pixels,1);StarArt.star(g,0,0,n.color,brightness,dim*detail,glow,false);g.pose().popPose();}
                double label=smooth((detail-.68)/.32);if(n==StarNode.WARRIOR&&label>.02)g.drawCenteredString(font,text("vitality"),(int)p.x,(int)p.y+12,StarArt.alpha(StarArt.mix(0x5A7163,0xA9C2AF,brightness),label*dim));
            }
            if(lit)starIgnition(g,n,p,i,enlarged,now,dim);
            starExtinction(g,n,p,i,enlarged,now,dim);
        }
        Point p=anchor(n);
        if(enlarged){
            var start=overviewAnchor(n);double x=TreeZoom.mix(start.x,width*.5,visualZoom),y=TreeZoom.mix(start.y+24,44,visualZoom);
            int color=StarArt.mix(StarArt.mix(n.color,0xDCE4EE,.6),0xD5E0EC,weight);
            g.drawCenteredString(font,n.label(),(int)x,(int)y,StarArt.alpha(color,dim*(1-weight)+visibility(n,now)*weight));
            if(weight<1)StarArt.glow(g,p.x,p.y,24,n.color,.25*(1-weight)*light.active(n,now));
        }else {if(hover)StarArt.ellipse(g,p.x,p.y,30,22,n.color,.5);StarArt.glow(g,p.x,p.y,24,n.color,.25*dim*light.active(n,now));if(dim>.02)g.drawCenteredString(font,n.label(),(int)p.x,(int)p.y+24,StarArt.alpha(StarArt.mix(n.color,0xDCE4EE,.6),dim));}
        completion(g,n,enlarged,now,dim);
    }
    private void starExtinction(GuiGraphics g,StarNode n,Point p,int index,boolean enlarged,long now,double opacity){
        double t=light.extinction(n,index,now);if(t<0||t>=1)return;
        double strength=Math.sin(Math.PI*t)*(1-t)*opacity,spread=(enlarged?23:10)*smooth(t);
        // A faint last halo contracts while pixel embers drift out and sink into the mist.
        StarArt.ellipse(g,p.x,p.y,(enlarged?15:6)*(1-smooth(t)),(enlarged?7:3)*(1-smooth(t)),n.color,strength*.45);
        for(int k=0;k<9;k++){double a=k*2.39996+index*.7;int x=(int)(p.x+Math.cos(a)*spread),y=(int)(p.y+Math.sin(a)*spread*.5+t*t*(enlarged?10:4));g.fill(x,y,x+1,y+1,StarArt.alpha(StarArt.mix(n.color,0xC5CED9,.28),strength*.7));}
    }
    private void starIgnition(GuiGraphics g,StarNode n,Point p,int index,boolean enlarged,long now,double opacity){
        double t=age(ignitedAt[n.ordinal()][index],now)/1050;
        double sweep=(age(completedAt[n.ordinal()],now)-index*65)/800;
        double flash=Math.max(t>=0&&t<1?Math.sin(Math.PI*Math.sqrt(t))*(1-t):0,sweep>=0&&sweep<1?Math.sin(Math.PI*sweep)*.7:0);
        if(flash<=0)return;double radius=(enlarged?34:14)*flash;
        StarArt.glow(g,p.x,p.y,radius+5,StarArt.mix(n.color,0xFFFFFF,.45),opacity*flash*.85);
        int x=(int)p.x,y=(int)p.y;g.fill(x-1,y-2,x+2,y+3,StarArt.alpha(0xFFFFFF,flash*opacity));g.fill(x-2,y-1,x+3,y+2,StarArt.alpha(0xFFFFFF,flash*opacity));
        if(t>=0&&t<1)for(int k=0;k<8;k++){double a=k*Math.PI/4+index*.6,r=(enlarged?22:9)*smooth(t);int sx=(int)(p.x+Math.cos(a)*r),sy=(int)(p.y+Math.sin(a)*r*.65);g.fill(sx,sy,sx+1,sy+1,StarArt.alpha(n.color,(1-t)*opacity*.8));}
    }
    private void completion(GuiGraphics g,StarNode n,boolean enlarged,long now,double opacity){
        Point center=anchor(n);double t=age(completedAt[n.ordinal()],now)/2600;
        double activation=age(activatedAt[n.ordinal()],now)/2200;
        if(activation>=0&&activation<1){double f=smooth(activation),strength=Math.sin(Math.PI*activation)*opacity;StarArt.glow(g,center.x,center.y,(enlarged?75:44)*(1-.4*f),n.color,strength*.7);StarArt.ellipse(g,center.x,center.y,12+(enlarged?84:36)*f,8+(enlarged?55:24)*f,n.color,strength*.8);}
        if(t>=0&&t<1){double f=smooth(t),radius=(enlarged?24:8)+(enlarged?102:36)*f,strength=Math.sin(Math.PI*t)*(1-t)*opacity;
            StarArt.ellipse(g,center.x,center.y,radius,radius*.65,n.color,strength*.65);StarArt.glow(g,center.x,center.y,radius*1.15,n.color,strength*.18);
            for(int i=0;i<26;i++){double a=i*2.39996,r=radius*(.6+.4*(i%4)/3.0);int x=(int)(center.x+Math.cos(a)*r),y=(int)(center.y+Math.sin(a)*r*.65);g.fill(x,y,x+1,y+1,StarArt.alpha(StarArt.mix(n.color,0xFFFFFF,.55),strength*.8));}
        }
        double reveal=age(revealedAt[n.ordinal()],now)/1200;
        if(reveal>=0&&reveal<1){StarArt.glow(g,center.x,center.y,48*(1-reveal)+15,n.color,Math.sin(Math.PI*reveal)*.4);particles(g,center,n.color,reveal);}
    }
    private void background(GuiGraphics g,StarTreeState s,double time){
        g.fill(0,0,width,height,0xFF01030A);
        double weight=detailWeight();var subject=visualSubject();
        double cameraX=TreeZoom.mix(overviewCamera.x(),detailCamera.x(),weight),cameraY=TreeZoom.mix(overviewCamera.y(),detailCamera.y(),weight);
        StarArt.sprite(g,StarArt.SKY,-width*.15-cameraX*.08,-height*.15-cameraY*.08,width*1.3,height*1.3,512,256,0xD6DDEA,.65);
        for(var n:StarNode.BASES)if(exposure[n.ordinal()]>.002){Point a=anchor(n);double opacity=n==subject?1:1-weight;if(opacity<.002)continue;double scale=.5+.5*weight;branch(g,a.x,a.y+53*scale,0,-36*scale,3,n.color,.045*exposure[n.ordinal()]*opacity,n.ordinal());}
        for(int y=0;y<height;y+=8)for(int x=0;x<width;x+=8){
            double reveal=.01;for(var n:StarNode.values())if(n!=StarNode.RESET&&exposure[n.ordinal()]>.01){var a=anchor(n);double opacity=n==subject?1:1-weight;if(opacity<.002)continue;double dx=(x-a.x)/(width*.19),dy=(y-a.y)/(height*.27);reveal=Math.max(reveal,.7*exposure[n.ordinal()]*opacity*Math.exp(-2*(dx*dx+dy*dy)));}
            int shade=3+(int)(2*(1+Math.sin(x*.019+y*.013+time*.06)));g.fill(x,y,Math.min(width,x+8),Math.min(height,y+8),StarArt.alpha(0x010307+shade,1-.92*reveal));
        }
        double trunk=light.active(StarNode.CRYING,Util.getMillis());if(trunk>.002){
            Point a=anchor(StarNode.CRYING);if(weight<1)StarArt.sprite(g,StarArt.TRUNK,a.x-55,a.y-180,110,188,128,256,0x9CACB9,.28*(1-weight)*trunk);
            if(weight>0){a=detailCenter();StarArt.sprite(g,StarArt.TRUNK,a.x-55,a.y-180,110,188,128,256,0x9CACB9,.28*weight*trunk);}
        }
    }
    private void branch(GuiGraphics g,double x,double y,double dx,double dy,int depth,int color,double opacity,int seed){
        double ex=x+dx,ey=y+dy;StarArt.line(g,x,y,ex,ey,color,opacity,1,1);if(depth==0)return;
        double side=(seed%2==0?1:-1);branch(g,ex,ey,dx*.55+side*Math.abs(dy)*.66,dy*.69,depth-1,color,opacity*.9,seed+1);branch(g,ex,ey,dx*.45-side*Math.abs(dy)*.53,dy*.61,depth-1,color,opacity*.8,seed+2);
    }
    private void particles(GuiGraphics g,Point p,int color,double f){for(int i=0;i<14+48*f;i++){double r=(35+i*17%37)*Math.pow(1-f,1.7),a=i*2.39996+(1-f)*1.8;int x=(int)(p.x+Math.cos(a)*r),y=(int)(p.y+Math.sin(a)*r*.65);g.fill(x,y,x+(f>.75?2:1),y+(f>.75?2:1),StarArt.alpha(StarArt.mix(color,0xFFFFFF,.6),.3+.6*f));}}
    private static boolean over(Button b,int x,int y){return b.visible&&x>=b.getX()&&x<b.getX()+b.getWidth()&&y>=b.getY()&&y<b.getY()+b.getHeight();}
    private void hoverDetails(GuiGraphics g,Hit hover,StarTreeState s,int mx,int my){
        var details=new ArrayList<Component>();
        if(hover!=null){var n=hover.node;
            if(n==StarNode.RESET){details.add(text("reset").copy().withStyle(ChatFormatting.BOLD));details.add(text("reset_description"));details.add(text("reset_hint").copy().withStyle(ChatFormatting.GRAY));}
            else if(BuildVariant.LITE&&n.base()){details.add(n.label());details.add(Component.literal("内容暂未开放"));}else if(hover.star==StarTreeState.CONSTELLATION){
                details.add(n.label().copy().withStyle(style->style.withColor(StarArt.mix(n.color,0xFFFFFF,.4)).withBold(true)));
                details.add(Component.translatable("tree.sevenfold_mastery.route."+n.id).withStyle(ChatFormatting.GRAY));
                details.add(n.description());
                details.add(text(s.isComplete(n)?"constellation_complete":s.isActivated(n)?"constellation_active":"constellation_revealed").copy().withStyle(s.isActivated(n)?ChatFormatting.AQUA:ChatFormatting.DARK_GRAY));
                details.add(text("star_count",s.count(n)).copy().withStyle(ChatFormatting.GRAY));
                if(n==StarNode.WARRIOR)details.add(text("vitality_total",s.count(n)*StarTreeState.healthPerStar(n)).copy().withStyle(ChatFormatting.GREEN));
                details.add(text(n==StarNode.CRYING?"hold_departure":"hold_constellation").copy().withStyle(ChatFormatting.DARK_GRAY));
            }else {
                boolean vitality=n==StarNode.WARRIOR;
                details.add(text(vitality?"vitality":"unnamed_star").copy().withStyle(ChatFormatting.BOLD));
                details.add(vitality?text("vitality_effect",StarTreeState.healthPerStar(n)).copy().withStyle(ChatFormatting.GREEN):text("unassigned_effect").copy().withStyle(ChatFormatting.GRAY));
                details.add(text(s.star(n,hover.star)?"star_lit":"star_unlit").copy().withStyle(s.star(n,hover.star)?ChatFormatting.AQUA:ChatFormatting.GRAY));
                details.add(text(s.isActivated(n)?"star_hold_hint":"needs_activation").copy().withStyle(ChatFormatting.DARK_GRAY));
            }
        }else if(over(more,mx,my))details.add(text("more"));
        else if(menuOpen&&over(demoButton,mx,my))details.add(text("demo_description"));
        if(details.isEmpty())return;
        var lines=new ArrayList<FormattedCharSequence>();int wrap=Math.clamp(width/3,150,225);
        for(var part:details)lines.addAll(font.split(part,wrap));
        g.renderTooltip(font,lines,mx,my);
    }
    String diagnostic(){var c=focus==null?overviewCamera:detailCamera;long now=Util.getMillis();return "state="+shown().save()+", focus="+focus+", camera="+(int)c.x()+","+(int)c.y()+", zoom="+Math.round(visualZoom*1000)/1000.0+", zoomNode="+zoomNode+", starLight="+Math.round(light.star(StarNode.WARRIOR,0,now)*1000)/1000.0+", branchVisibility="+Math.round(light.visible(StarNode.KNIGHT,now)*1000)/1000.0+", rootLight="+Math.round(light.active(StarNode.CRYING,now)*1000)/1000.0+", overviewHold="+heldOverview+", cursor="+GLFW.glfwGetInputMode(minecraft.getWindow().getWindow(),GLFW.GLFW_CURSOR)+", grabbed="+minecraft.mouseHandler.isMouseGrabbed()+", menuMusic="+minecraft.getMusicManager().isPlayingMusic(net.minecraft.sounds.Musics.MENU)+", audio="+audio.diagnostic();}
    private static final class QuietButton extends Button {
        QuietButton(int x,int y,int w,Component c,OnPress action){super(x,y,w,18,c,action,DEFAULT_NARRATION);}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float p){g.fill(getX(),getY(),getX()+width,getY()+height,0xA00A1422);g.fill(getX(),getY()+height-1,getX()+width,getY()+height,isHoveredOrFocused()?0xFFB2CBDD:0xFF5C7288);g.drawCenteredString(Minecraft.getInstance().font,getMessage(),getX()+width/2,getY()+5,0xFF9FB5C8);}
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean keyPressed(int key,int scan,int mods){if(!passage.interactive(Util.getMillis())){if(key==GLFW.GLFW_KEY_ESCAPE||minecraft.options.keyInventory.matches(key,scan))onClose();return true;}if(zoom.moving(Util.getMillis())){if(key==GLFW.GLFW_KEY_ESCAPE&&focus!=null)zoomTo(null);else if(minecraft.options.keyInventory.matches(key,scan))onClose();return true;}if(key==GLFW.GLFW_KEY_ESCAPE&&menuOpen){menuOpen=false;cancel();return true;}if(key==GLFW.GLFW_KEY_ESCAPE&&focus!=null){zoomTo(null);return true;}if(minecraft.options.keyInventory.matches(key,scan)){onClose();return true;}return super.keyPressed(key,scan,mods);}
    @Override public void onClose(){if(!passage.closing())audio.motion(false,true);cancel();returnEdge.reset();overviewCamera.stop();detailCamera.stop();passage.close(Util.getMillis());}@Override public void removed(){cancel();audio.removed();super.removed();}
}
