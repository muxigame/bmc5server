package audit;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.*;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import noppes.npcs.*;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.event.RoleEvent;
import noppes.npcs.api.wrapper.WrapperNpcAPI;
import noppes.npcs.constants.EnumGuiType;
import noppes.npcs.containers.ContainerNPCTrader;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.*;

/** Local-only integration harness. Never install this mod on a shared server. */
@Mod("trader_stock_audit")
public class TraderStockAudit {
    MinecraftServer server;
    ServerLevel level;
    EntityNPCInterface merchant;
    int ticks;
    String eventMode = "";
    ContainerNPCTrader nested;
    FakePlayer nestedPlayer;
    final List<Map<String,Object>> results = new ArrayList<>();
    public TraderStockAudit() {
        NeoForge.EVENT_BUS.addListener(this::started);
        NeoForge.EVENT_BUS.addListener(this::tick);
        WrapperNpcAPI.EVENT_BUS.addListener(this::trade);
    }
    void started(ServerStartedEvent e) { server=e.getServer(); level=server.overworld(); }
    void check(String name, boolean ok) {
        results.add(Map.of("test",name,"status",ok?"PASS":"FAIL"));
        System.out.println("STOCK_AUDIT "+name+" "+(ok?"PASS":"FAIL"));
        save();
    }
    void save() { try { Files.writeString(Path.of("stock-results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(results)); } catch(Exception e) { throw new RuntimeException(e); } }
    void trade(RoleEvent.TraderEvent e) {
        if (eventMode.equals("cancel")) e.setCanceled(true);
        if (eventMode.equals("larger")) e.sold=NpcAPI.Instance().getIItemStack(new ItemStack(Items.DIAMOND,6));
        if (eventMode.equals("nested")) { eventMode=""; nested.clicked(0,0,ClickType.PICKUP,nestedPlayer); }
    }
    EntityNPCInterface npc() {
        var n=CustomEntities.entityCustomNpc.create(level);
        n.setPos(8,-59,8); n.setNoAi(true); n.setInvulnerable(true); n.setPersistenceRequired();
        n.role=new RoleTrader(n); level.addFreshEntity(n); return n;
    }
    FakePlayer player(String name) {
        var p=FakePlayerFactory.get(level,new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name));
        p.setPos(8,-59,8); p.getInventory().clearContent(); p.getInventory().setItem(0,new ItemStack(Items.EMERALD,64)); return p;
    }
    ContainerNPCTrader menu(FakePlayer p, EntityNPCInterface n) {
        var m=new ContainerNPCTrader(123,p.getInventory(),n.getId()); p.containerMenu=m; return m;
    }
    int stock(RoleTrader r) { return r.stock.remaining(0,System.currentTimeMillis()); }
    void reset(RoleTrader r,int amount,int reward) {
        long now=System.currentTimeMillis(); r.stock.configure(0,false,amount,3600,now); r.stock.configure(0,true,amount,3600,now);
        r.inventorySold.setItem(0,new ItemStack(Items.DIAMOND,reward)); r.inventoryCurrency.setItem(0,new ItemStack(Items.EMERALD));
    }
    void stateTests() throws Exception {
        long now=1_000_000;
        var s=new TraderStock();
        check("legacy_unlimited",s.remaining(0,now)==-1&&s.take(0,999999,now));
        s.configure(0,true,10,60,now); s.configure(1,true,20,90,now);
        check("initial_stock",s.remaining(0,now)==10&&s.secondsUntilRefill(0,now)==60);
        check("item_count_not_click_count",s.take(0,3,now)&&s.remaining(0,now)==7);
        check("reject_overdraw_and_nonpositive",!s.take(0,8,now)&&!s.take(0,0,now)&&!s.take(0,-1,now)&&s.remaining(0,now)==7);
        check("independent_offers",s.remaining(1,now)==20&&s.secondsUntilRefill(1,now)==90);
        check("before_deadline",s.remaining(0,now+59999)==7&&s.secondsUntilRefill(0,now+59999)==1);
        check("deadline_refill_no_accumulation",s.remaining(0,now+60000)==10);
        s.take(0,4,now+60000);
        check("offline_skip_cycles",s.remaining(0,now+600000)==10&&s.secondsUntilRefill(0,now+600000)==60);
        now+=600000; s.take(0,4,now);
        CompoundTag saved=new CompoundTag(); s.writeSettings(saved); s.writeState(saved);
        Path path=Path.of("stock-roundtrip.nbt"); NbtIo.write(saved,path);
        var copy=new TraderStock(); var disk=NbtIo.read(path); copy.readSettings(disk,now+1000); copy.readState(disk,now+1000);
        check("disk_nbt_preserves_stock_and_deadline",copy.remaining(0,now+1000)==6&&copy.secondsUntilRefill(0,now+1000)==59);
        copy.readSettings(disk,now+1000);
        check("reopen_same_settings_no_refill",copy.remaining(0,now+1000)==6);
        copy.configure(0,true,20,60,now+1000);
        check("increase_capacity_preserves_remaining",copy.remaining(0,now+1000)==6);
        copy.configure(0,true,3,60,now+1000);
        check("lower_capacity_clamps",copy.remaining(0,now+1000)==3);
        copy.configure(0,true,3,120,now+1000);
        check("interval_edit_reschedules",copy.secondsUntilRefill(0,now+1000)==120);
        copy.configure(2,true,Integer.MAX_VALUE,Integer.MAX_VALUE,now);
        check("limits_clamped",copy.capacity(2)==TraderStock.MAX_CAPACITY&&copy.interval(2)==TraderStock.MAX_SECONDS);
        copy.configure(3,true,-4,0,now);
        check("negative_inputs_clamped",copy.capacity(3)==1&&copy.interval(3)==1);
        check("clock_rollback_bounded",copy.secondsUntilRefill(2,now-100000)==TraderStock.MAX_SECONDS);
        var legacy=new TraderStock(); legacy.readSettings(new CompoundTag(),now);
        check("legacy_save_compatible",legacy.remaining(17,now)==-1);
    }
    void transactions() throws Exception {
        var n=npc(); var r=(RoleTrader)n.role;
        var a=player("StockAuditA"); var b=player("StockAuditB");
        var ma=menu(a,n); var mb=menu(b,n);
        reset(r,5,2);
        ma.clicked(0,0,ClickType.PICKUP,a); mb.clicked(0,0,ClickType.PICKUP,b); ma.clicked(0,0,ClickType.PICKUP,a);
        check("two_buyers_shared_stock_no_oversell",stock(r)==1&&ma.getCarried().getCount()==2&&mb.getCarried().getCount()==2&&a.getInventory().countItem(Items.EMERALD)==63);
        ma.setCarried(ItemStack.EMPTY); reset(r,10,2);
        ma.clicked(0,0,ClickType.QUICK_MOVE,a);
        check("shift_purchase_stops_at_stock",stock(r)==0&&ma.getCarried().getCount()==10&&a.getInventory().countItem(Items.EMERALD)==58);
        ma.clicked(0,0,ClickType.PICKUP,a);
        check("sold_out_no_charge",a.getInventory().countItem(Items.EMERALD)==58);
        reset(r,5,2); ma.setCarried(new ItemStack(Items.DIAMOND,64)); ma.clicked(0,0,ClickType.PICKUP,a);
        check("full_cursor_no_charge_or_stock_loss",stock(r)==5&&a.getInventory().countItem(Items.EMERALD)==58);
        ma.setCarried(ItemStack.EMPTY); a.getInventory().clearContent(); ma.clicked(0,0,ClickType.PICKUP,a);
        check("insufficient_currency_safe",stock(r)==5&&ma.getCarried().isEmpty());
        a.getInventory().setItem(0,new ItemStack(Items.EMERALD,64)); eventMode="cancel"; ma.clicked(0,0,ClickType.PICKUP,a); eventMode="";
        check("cancelled_script_no_charge",stock(r)==5&&a.getInventory().countItem(Items.EMERALD)==64&&ma.getCarried().isEmpty());
        eventMode="larger"; ma.clicked(0,0,ClickType.PICKUP,a); eventMode="";
        check("script_reward_stock_revalidated",stock(r)==5&&a.getInventory().countItem(Items.EMERALD)==64&&ma.getCarried().isEmpty());
        reset(r,2,2); mb.setCarried(ItemStack.EMPTY); nested=mb; nestedPlayer=b; eventMode="nested"; ma.clicked(0,0,ClickType.PICKUP,a);
        check("nested_purchase_cannot_overdraw",stock(r)==0&&ma.getCarried().isEmpty()&&mb.getCarried().getCount()==2&&a.getInventory().countItem(Items.EMERALD)==64);
        reset(r,10,1); r.stock.take(0,7,System.currentTimeMillis()); CompoundTag oldEditor=r.save(new CompoundTag()); r.stock.take(0,1,System.currentTimeMillis());
        r.loadFromEditor(oldEditor,true);
        check("stale_editor_cannot_restore_stock",stock(r)==2);
        var malicious=oldEditor.copy(); var settings=malicious.getList(TraderStock.SETTINGS,Tag.TAG_COMPOUND); settings.getCompound(0).putBoolean("Enabled",false); malicious.putString("TraderMarket","forged-market");
        r.loadFromEditor(malicious,false);
        check("non_op_cannot_edit_stock_or_market",stock(r)==2&&r.stock.enabled(0)&&r.marketName.isEmpty());
        CompoundTag market=r.writeNBT(new CompoundTag()); r.readNBT(market);
        check("market_template_no_runtime_stock",!market.contains(TraderStock.STATE)&&stock(r)==2);
        var other=new RoleTrader(n); other.load(r.save(new CompoundTag()));
        check("role_save_reload",stock(other)==2);
        CompoundTag entityData=new CompoundTag(); n.save(entityData);
        var restored=CustomEntities.entityCustomNpc.create(level); restored.load(entityData);
        check("full_npc_save_reload",restored.role instanceof RoleTrader restoredTrader&&stock(restoredTrader)==2);
        var blockedPacket=new noppes.npcs.packets.server.SPacketNpcRoleSave(malicious);
        a.getInventory().setItem(0,new ItemStack(CustomItems.wand)); NoppesUtilServer.setEditingNpc(a,n);
        noppes.npcs.packets.PacketServerBasic.handle(blockedPacket,server,a);
        check("non_op_wand_packet_rejected",!a.hasPermissions(2)&&stock(r)==2&&r.stock.enabled(0)&&r.marketName.isEmpty());
        a.getInventory().setItem(0,new ItemStack(Items.EMERALD,64));
        reset(r,1_000_000,1);
        r.stock.configure(17,true,65536,TraderStock.MAX_SECONDS,System.currentTimeMillis());
        r.inventorySold.setItem(17,new ItemStack(Items.DIAMOND)); ma.clicked(17,0,ClickType.PICKUP,a);
        check("last_page_offer_purchasable",r.stock.remaining(17,System.currentTimeMillis())==65535&&ma.getCarried().getCount()==1);
        // DataSlot wire values are signed shorts; force the same truncation as the vanilla packet.
        ma.setSynchronizer(new net.minecraft.world.inventory.ContainerSynchronizer() {
            public void sendInitialData(net.minecraft.world.inventory.AbstractContainerMenu m,net.minecraft.core.NonNullList<ItemStack> items,ItemStack carried,int[] data) {
                for(int id=0;id<data.length;id++)m.setData(id,(short)data[id]);
            }
            public void sendSlotChange(net.minecraft.world.inventory.AbstractContainerMenu m,int slot,ItemStack stack) {}
            public void sendCarriedChange(net.minecraft.world.inventory.AbstractContainerMenu m,ItemStack stack) {}
            public void sendDataChange(net.minecraft.world.inventory.AbstractContainerMenu m,int id,int value) { m.setData(id,(short)value); }
        });
        ma.broadcastChanges(); var f=ContainerNPCTrader.class.getDeclaredField("stockData"); f.setAccessible(true); int[] wire=(int[])f.get(ma);
        check("wire_ints_above_65535",wire[0]==1_000_000&&wire[51]==65535&&wire[53]==TraderStock.MAX_SECONDS);
        check("wire_unlimited_negative_sentinel",wire[3]==-1);
        r.stock.configure(0,false,2,1,System.currentTimeMillis()); r.stock.configure(0,true,2,1,System.currentTimeMillis()); r.stock.take(0,2,System.currentTimeMillis());
        merchant=n;
        a.containerMenu=a.inventoryMenu; b.containerMenu=b.inventoryMenu;
        ma.setCarried(ItemStack.EMPTY); mb.setCarried(ItemStack.EMPTY);
    }
    void prepareVisuals() {
        var r=(RoleTrader)merchant.role; long now=System.currentTimeMillis();
        check("live_tick_refill",stock(r)==2);
        merchant.display.setName("库存验证商人");
        Item[] items={Items.DIAMOND,Items.IRON_INGOT,Items.GOLD_INGOT,Items.BREAD,Items.APPLE,Items.ENDER_PEARL};
        for(int i=0;i<18;i++) {
            r.inventorySold.setItem(i,new ItemStack(items[i%6],i%3+1)); r.inventoryCurrency.setItem(i,new ItemStack(Items.EMERALD,i%4+1));
            r.stock.configure(i,false,64,3600,now); r.stock.configure(i,i%6!=5,i%6==4?1_000_000:64,i%6==4?TraderStock.MAX_SECONDS:3600,now);
        }
        r.stock.take(1,64,now); r.stock.take(2,63,now); r.stock.configure(0,true,64,10,now); r.stock.take(0,4,now);
        merchant.updateClient=true;
        try { Files.writeString(Path.of("stock-ready.txt"),"ready"); } catch(Exception e) { throw new RuntimeException(e); }
    }
    void tick(ServerTickEvent.Post e) {
        if(server==null)return;
        try {
            ticks++;
            if(ticks==20) {
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
                level.setChunkForced(0,0,true); stateTests(); transactions();
            }
            if(ticks==65&&merchant!=null)prepareVisuals();
            Path request=Path.of("stock-ui-request.txt");
            if(Files.exists(request)&&merchant!=null&&!server.getPlayerList().getPlayers().isEmpty()) {
                String mode=Files.readString(request).trim(); Files.delete(request);
                var p=server.getPlayerList().getPlayers().getFirst();
                if(!p.getGameProfile().getName().equals("BMC5LocalTest"))throw new IllegalStateException("Unexpected test player");
                server.getPlayerList().op(p.getGameProfile());
                p.teleportTo(8,-59,10); p.getInventory().setItem(0,new ItemStack(CustomItems.wand)); p.getInventory().setItem(1,new ItemStack(Items.EMERALD,64)); p.getInventory().selected=0;
                NoppesUtilServer.setEditingNpc(p,merchant);
                NoppesUtilServer.sendOpenGui(p,mode.equals("editor")?EnumGuiType.SetupTrader:EnumGuiType.PlayerTrader,merchant);
            }
            if(Files.exists(Path.of("stock-stop.flag")))server.halt(false);
        } catch(Throwable error) {
            error.printStackTrace(); check("HARNESS_ERROR_"+ticks,false);
        }
    }
}
