package audit;

import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import noppes.npcs.client.gui.player.GuiNPCTrader;
import noppes.npcs.client.gui.roles.GuiNpcTraderSetup;
import noppes.npcs.client.gui.roles.SubGuiTraderStock;

/** Controlled screenshots of the real game renderer, restricted to this local test instance. */
@Mod(value="trader_stock_visual",dist=Dist.CLIENT)
public class TraderStockVisual {
    public TraderStockVisual() { NeoForge.EVENT_BUS.addListener(this::tick); }
    void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        mc.getWindow().setTitle("BMC5 NPC Stock - isolated local test");
        Path request=mc.gameDirectory.toPath().resolve("stock-visual-request.txt");
        if(!Files.exists(request))return;
        try {
            String action=Files.readString(request).trim(); Files.delete(request);
            if(action.startsWith("shot:")) Screenshot.grab(mc.gameDirectory,action.substring(5)+".png",mc.getMainRenderTarget(),text->System.out.println("STOCK_SCREENSHOT "+text.getString()));
            if(action.equals("next")&&mc.screen instanceof GuiNPCTrader gui) gui.getButton(1).onPress();
            if(action.equals("stock")&&mc.screen instanceof GuiNpcTraderSetup gui) gui.getButton(100).onPress();
            if(action.equals("configure")&&mc.screen instanceof GuiNpcTraderSetup gui&&gui.getSubGui() instanceof SubGuiTraderStock sub) {
                sub.getTextField(1).setValue("12"); sub.getTextField(2).setValue("30"); sub.getButton(66).onPress(); gui.onClose();
            }
            if(action.equals("buy")&&mc.screen instanceof GuiNPCTrader gui) mc.gameMode.handleInventoryMouseClick(gui.getMenu().containerId,gui.getMenu().page*6,0,net.minecraft.world.inventory.ClickType.PICKUP,mc.player);
            if(action.equals("close"))mc.player.closeContainer();
            if(action.equals("quit"))mc.stop();
            String state=mc.screen==null?"none":mc.screen.getClass().getSimpleName();
            if(mc.level!=null)for(var entity:mc.level.entitiesForRendering())if(entity instanceof noppes.npcs.entity.EntityNPCInterface)state+=" npc="+entity.getId()+"@"+entity.position();
            if(mc.screen instanceof GuiNPCTrader gui)state+=" page="+gui.getMenu().page+" stock="+gui.getMenu().stockRemaining(gui.getMenu().page*6)+" cursor="+gui.getMenu().getCarried();
            Files.writeString(mc.gameDirectory.toPath().resolve("stock-visual-state.txt"),state);
        }catch(Throwable error){error.printStackTrace();}
    }
}
