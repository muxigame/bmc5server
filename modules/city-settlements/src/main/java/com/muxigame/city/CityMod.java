package com.muxigame.city;

import java.util.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.*;

@Mod(CityMod.ID)
public final class CityMod {
    public static final String ID="city_settlements";
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(ID);
    public static final DeferredItem<Item> SURVEYOR=ITEMS.register("surveyor",()->new PlanningItem("surveyor"));
    public static final DeferredItem<Item> BUILDER=ITEMS.register("building_wand",()->new PlanningItem("builder"));
    public static final DeferredItem<Item> CHARTER=ITEMS.register("charter",()->new PlanningItem("charter"));
    public static final DeferredItem<Item> CLAIM=ITEMS.register("claim_stamp",()->new PlanningItem("claim"));
    public static final DeferredItem<Item> EXCLUDE=ITEMS.register("exclude_stamp",()->new PlanningItem("exclude"));
    public static final DeferredItem<Item> ATLAS=ITEMS.register("city_atlas",()->new PlanningItem("atlas"));
    public static final DeferredItem<Item> LEDGER=ITEMS.register("city_ledger",CityLedgerItem::new);
    public static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,ID);
    public static final DeferredHolder<CreativeModeTab,CreativeModeTab> TAB=TABS.register("city",()->CreativeModeTab.builder().title(Component.translatable("itemGroup.city_settlements"))
        .icon(()->new ItemStack(SURVEYOR.get())).displayItems((p,o)->{for(var item:List.of(SURVEYOR,BUILDER,ATLAS,LEDGER))o.accept(item.get());for(var item:RuralBlocks.ITEMS)o.accept(item.get());o.accept(RuralBlocks.STORAGE_WAND.get());o.accept(RuralBlocks.MARRIAGE_WAND.get());}).build());
    public static final DeferredItem<Item> RICE_SEEDS=ITEMS.register("rice_seeds",()->new ItemNameBlockItem(CityRice.CROP.get(),new Item.Properties()));
    public static final DeferredItem<Item> RICE=ITEMS.register("rice",()->new Item(new Item.Properties()));
    public static final DeferredItem<Item> COOKED_RICE=ITEMS.register("cooked_rice",()->new Item(new Item.Properties().food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(5).saturationModifier(0.6f).build())));
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<UUID,Session> BUILDING_DRAFTS=new HashMap<>();
    private static Session buildingDraft(ServerPlayer p){Session s=BUILDING_DRAFTS.computeIfAbsent(p.getUUID(),id->new Session());String dim=p.level().dimension().location().toString();if(!dim.equals(s.dimension)){s.dimension=dim;s.a=null;s.b=null;s.bottom=null;s.top=null;s.preview=null;s.selectionRevision++;}return s;}
    private static final class Session {
        boolean visible=true;String dimension="";BlockPos a,b;Integer bottom,top;CityBounds preview;long selectionRevision;UUID city;int x,y,z;boolean surface=true;long lastRequest=-100;CompoundTag lastSnapshot;
    }
    public CityMod(IEventBus bus){
        RuralBlocks.BLOCKS.register(bus);RuralBlocks.TYPES.register(bus);CityRice.BLOCKS.register(bus);ITEMS.register(bus);TABS.register(bus);bus.addListener(CityNetwork::register);
        NeoForge.EVENT_BUS.addListener(CityMod::commands);NeoForge.EVENT_BUS.addListener(RuralCommands::register);NeoForge.EVENT_BUS.addListener(RuralControl::register);NeoForge.EVENT_BUS.addListener(CityHouseCommands::register);NeoForge.EVENT_BUS.addListener(CityWorksControl::register);NeoForge.EVENT_BUS.addListener(CityRiskLab::register);NeoForge.EVENT_BUS.addListener(CityModelLab::register);NeoForge.EVENT_BUS.addListener(CityHousing::broken);NeoForge.EVENT_BUS.addListener(CityHousing::placed);NeoForge.EVENT_BUS.addListener(CityHousing::exploded);NeoForge.EVENT_BUS.addListener(CityHousing::piston);NeoForge.EVENT_BUS.addListener(CityHousing::interacting);
        NeoForge.EVENT_BUS.addListener(CitySimulation::unload);
        NeoForge.EVENT_BUS.addListener(CitySimulation::loadChunk);
        NeoForge.EVENT_BUS.addListener(CitySimulation::leave);
        NeoForge.EVENT_BUS.addListener(CitySimulation::join);
        NeoForge.EVENT_BUS.addListener(CitySimulation::death);
        NeoForge.EVENT_BUS.addListener(CitySimulation::stopping);NeoForge.EVENT_BUS.addListener(CityWheatMigration::start);
        NeoForge.EVENT_BUS.addListener(RuralWorkVisuals::joined);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent e)->RuralWorkVisuals.release(e.getServer()));
        NeoForge.EVENT_BUS.addListener(CityNpcDesigner::entity);NeoForge.EVENT_BUS.addListener(CityNpcDesigner::block);NeoForge.EVENT_BUS.addListener(CityNpcDesigner::air);NeoForge.EVENT_BUS.addListener(CityEconomyAccess::interact);NeoForge.EVENT_BUS.addListener(RuralTools::interact);NeoForge.EVENT_BUS.addListener(RuralWorkVisuals::trample);NeoForge.EVENT_BUS.addListener(RuralProjection::heal);NeoForge.EVENT_BUS.addListener(RuralProjection::death);NeoForge.EVENT_BUS.addListener(HunterForest::broken);NeoForge.EVENT_BUS.addListener(HunterForest::placed);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e)->{CityService.tick(e.getServer());CityEconomy.tick(e.getServer());CityMaintenance.tick(e.getServer());CityHousing.tick(e.getServer());CityWorks.tick(e.getServer());CityWorksLab.tick(e.getServer());NpcStorage.tick(e.getServer());HunterForest.tick(e.getServer());RuralSettlement.workTick(e.getServer());RuralSettlement.tick(e.getServer());RuralMonitor.tick(e.getServer());RuralProjection.tick(e.getServer());CityWheatMigration.tick(e.getServer());CityWork.tick(e.getServer());if(e.getServer().getTickCount()%10==0)for(ServerPlayer p:e.getServer().getPlayerList().getPlayers())preview(p);});
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e)->{CityNpcDesigner.logout(e.getEntity().getUUID());SESSIONS.remove(e.getEntity().getUUID());BUILDING_DRAFTS.remove(e.getEntity().getUUID());CityService.logout(e.getEntity().getUUID());});
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e)->{CityNpcDesigner.clear();SESSIONS.clear();BUILDING_DRAFTS.clear();CityService.clear();CityWork.clear();RuralWorkVisuals.clear();CityHousing.clear();CityWorks.clear();SimulationClock.clear();CitySimulation.clear();CityWheatMigration.clear();});
    }
    private static Session session(ServerPlayer p){
        Session s=SESSIONS.computeIfAbsent(p.getUUID(),id->new Session());String dim=p.level().dimension().location().toString();
        if(!s.dimension.equals(dim)){s.dimension=dim;s.a=null;s.b=null;s.bottom=null;s.top=null;s.preview=null;s.selectionRevision++;s.city=null;String saved=p.getPersistentData().getString("CitySettlementsActive");try{CityData.City c=CityData.get(p.server).cities.get(UUID.fromString(saved));if(c!=null&&c.dimension.equals(dim)){s.city=c.id;s.x=c.bounds.min().getX();s.y=c.bounds.max().getY();s.z=c.bounds.min().getZ();}}catch(IllegalArgumentException ignored){}}return s;
    }
    private static void say(ServerPlayer p,String text){p.sendSystemMessage(Component.literal("[城市] "+text));}
    @FunctionalInterface interface Action {void run(ServerPlayer p) throws Exception;}
    private static int run(CommandSourceStack src,Action action){try{action.run(src.getPlayerOrException());return 1;}catch(Exception ex){src.sendFailure(Component.literal("[城市] "+ex.getMessage()));return 0;}}
    private static void editor(ServerPlayer p){if(!CityService.editor(p))throw new IllegalArgumentException("此操作需要创造模式或管理员权限。");}
    private static void point(ServerPlayer p,BlockPos pos,boolean second){
        point(p,pos,second,session(p));}
    private static void point(ServerPlayer p,BlockPos pos,boolean second,Session s){
        if(second)s.b=pos.immutable();else{s.a=pos.immutable();s.b=null;s.bottom=null;s.top=null;}s.selectionRevision++;s.preview=null;
        String info="";if(s.a!=null&&s.b!=null)try{CityBounds b=selection(p,s);info="；范围Y="+b.min().getY()+"至"+b.max().getY();}catch(IllegalArgumentException ex){info="；"+ex.getMessage();}
        say(p,(second?"第二地面角 ":"第一地面角 ")+pos.toShortString()+info+"；对应魔杖右键空气继续。");
    }
    private static void requireCorners(Session s){if(s.a==null||s.b==null)throw new IllegalArgumentException("先在地面用城市魔杖右键第一角，潜行右键第二角。");}
    private static CityBounds selection(ServerPlayer p){return selection(p,session(p));}
    private static CityBounds selection(ServerPlayer p,Session s){requireCorners(s);s.preview=null;CityBounds b=s.bottom==null?CitySelection.automatic(p.serverLevel(),s.a,s.b):CitySelection.manual(p.serverLevel(),s.a,s.b,s.bottom,s.top);s.preview=b;return b;}
    private static void height(ServerPlayer p,boolean automatic,int bottom,int top){height(p,automatic,bottom,top,session(p));}
    private static void height(ServerPlayer p,boolean automatic,int bottom,int top,Session s){requireCorners(s);CityBounds b=automatic?CitySelection.automatic(p.serverLevel(),s.a,s.b):CitySelection.manual(p.serverLevel(),s.a,s.b,bottom,top);s.bottom=automatic?null:bottom;s.top=automatic?null:top;s.preview=b;s.selectionRevision++;}
    private static void selectionSnapshot(ServerPlayer p,boolean founding,String message){
        selectionSnapshot(p,founding,message,false);}
    private static void selectionSnapshot(ServerPlayer p,boolean founding,String message,boolean building){
        Session s=building?buildingDraft(p):session(p);requireCorners(s);CompoundTag t=new CompoundTag();t.putBoolean("BuildingDraft",building);t.putBoolean("SelectionEditor",true);t.putBoolean("Founding",founding);t.putBoolean("Automatic",s.bottom==null);t.putLong("SelectionRevision",s.selectionRevision);
        t.putString("CornerA",s.a.toShortString());t.putString("CornerB",s.b.toShortString());t.putInt("Bottom",s.bottom==null?Math.min(s.a.getY(),s.b.getY()):s.bottom);t.putInt("Top",s.top==null?Math.max(s.a.getY(),s.b.getY()):s.top);
        try{CityBounds b=selection(p,s);t.putBoolean("Valid",true);t.putInt("Bottom",b.min().getY());t.putInt("Top",b.max().getY());t.putLong("Volume",b.volumeLong());t.putBoolean("LargeAutoHeight",s.bottom==null&&(b.sizeX()>128||b.sizeZ()>128));}catch(IllegalArgumentException ex){message=ex.getMessage();}
        if(session(p).city!=null&&CityData.get(p.server).cities.containsKey(session(p).city)){var c=current(p);t.putString("CityId",c.id.toString());t.putString("CityName",c.name);t.putLong("CityRevision",c.revision);}t.putString("Message",message);PacketDistributor.sendToPlayer(p,new CityNetwork.Snapshot(t));
    }
    private static CityData.City create(ServerPlayer p,String name){editor(p);CityData.City c=CityData.get(p.server).create(p.getUUID(),name,session(p).dimension,selection(p));select(p,c);CityService.scan(c);return c;}
    private static void select(ServerPlayer p,CityData.City c){Session s=session(p);s.city=c.id;p.getPersistentData().putString("CitySettlementsActive",c.id.toString());s.x=c.bounds.min().getX();s.y=c.bounds.max().getY();s.z=c.bounds.min().getZ();s.surface=true;}
    private static CityData.City current(ServerPlayer p){Session s=session(p);if(s.city==null)throw new IllegalArgumentException("请先在沙盘中选择聚落。");return CityData.get(p.server).require(s.city);}
    private static CityData.City find(ServerPlayer p,String value){return CityData.get(p.server).cities().stream().filter(c->c.dimension.equals(session(p).dimension)&&(c.id.toString().equals(value)||c.name.equals(value))).findFirst().orElseThrow(()->new IllegalArgumentException("当前维度中找不到此聚落。"));}
    private static void open(ServerPlayer p){editor(p);Session s=session(p);CityData data=CityData.get(p.server);
        if(s.city==null||!data.cities.containsKey(s.city)){data.cities().stream().filter(c->c.dimension.equals(s.dimension)&&c.bounds.contains(p.blockPosition())).findFirst()
            .or(()->data.cities().stream().filter(c->c.dimension.equals(s.dimension)).findFirst()).ifPresent(c->select(p,c));}
        if(s.city!=null)CityService.scan(current(p));snapshot(p,"",true);
    }
    public static void request(ServerPlayer p,CityNetwork.Request r){
        if(r.op().startsWith("career")){CityNpcDesigner.request(p,r);return;}if(r.op().startsWith("works_")){CityWorksControl.request(p,r);return;}if(r.op().startsWith("house")){CityHousing.request(p,r);return;}if(r.op().startsWith("market")||r.op().equals("simconfig")){RuralControl.request(p,r);return;}if(r.op().startsWith("work")){WorkHours.request(p,r);return;}if(r.op().startsWith("rural")){RuralTools.request(p,r);return;}if(r.op().startsWith("maint")){CityMaintenance.request(p,r);return;}
        if(r.op().startsWith("town")){CityTownSetup.request(p,r);return;}if(r.op().startsWith("trade")){CityTradeLab.request(p,r);return;}if(r.op().startsWith("law")){CityLaw.request(p,r);return;}
        if(r.op().startsWith("npcliving")){CityLivingEditor.request(p,r);return;}
        if(r.op().startsWith("econ")){CityEconomyAccess.request(p,r);return;}
        Session s=session(p);long tick=p.serverLevel().getGameTime();
        if(tick-s.lastRequest<3){if(s.lastSnapshot!=null){CompoundTag cached=s.lastSnapshot.copy();cached.putBoolean("Open",false);cached.putString("Message",r.op().equals("view")?"":"操作稍快，请稍后重试。");PacketDistributor.sendToPlayer(p,new CityNetwork.Snapshot(cached));}return;}s.lastRequest=tick;
        try{
            CityData data=CityData.get(p.server);
            if(r.op().equals("globalcityview")){if(!p.hasPermissions(2)||!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()).toString().equals("customnpcs:npcwand"))throw new IllegalArgumentException("城市全局设置需要管理员手持NPC魔杖。");open(p);return;}
            if(r.op().equals("npccityview")||r.op().equals("npccityset")){CityNpcBridge.request(p,r);return;}
            if(r.op().equals("buildingheight")){Session b=buildingDraft(p);if(p.getMainHandItem().getItem()!=BUILDER.get())throw new IllegalArgumentException("请手持建筑魔杖。");if(r.revision()!=b.selectionRevision)throw new IllegalArgumentException("建筑选区已变化。");if(!r.value().equals("auto")&&!r.value().equals("manual"))throw new IllegalArgumentException("未知高度模式。");height(p,r.value().equals("auto"),r.x(),r.y(),b);selectionSnapshot(p,false,"建筑高度已应用。",true);return;}
            if(r.op().equals("buildingform")){if(session(p).city==null)throw new IllegalArgumentException("先用信息魔杖选择城市。");snapshot(p,"",false,true);return;}
            if(r.op().equals("draft")){selectionSnapshot(p,true,"");return;}
            if(r.op().equals("visibility")){s.visible=!s.visible;snapshot(p,"现场显示已"+(s.visible?"开启":"关闭"),false);return;}
            if(r.op().equals("areaadd")||r.op().equals("areacut")){
                if(!(p.getMainHandItem().getItem() instanceof PlanningItem))throw new IllegalArgumentException("请手持魔杖。");
                if(r.revision()!=s.selectionRevision)throw new IllegalArgumentException("选区已改变，请重试。");
                CityData.City c=current(p);if(!c.id.toString().equals(r.city()))throw new IllegalArgumentException("当前城市已改变。");
                CityService.checkEditor(p,c,Long.parseLong(r.value()));long changed=CityService.area(data,c,p.serverLevel(),p.getUUID(),selection(p),r.op().equals("areaadd"));
                selectionSnapshot(p,true,"已"+(r.op().equals("areaadd")?"纳入":"排除")+changed+"格；信息魔杖可撤销。");return;
            }
            if(r.op().equals("height")){
                if(!(p.getMainHandItem().getItem() instanceof PlanningItem))throw new IllegalArgumentException("请手持规划道具。");
                if(r.revision()!=s.selectionRevision)throw new IllegalArgumentException("选区已变化，请重新打开高度面板。");
                if(!r.value().equals("auto")&&!r.value().equals("manual"))throw new IllegalArgumentException("未知高度模式。");
                height(p,r.value().equals("auto"),r.x(),r.y());selectionSnapshot(p,r.surface(),"高度已应用；可新建、纳入或排除。");return;
            }
            if(r.op().equals("found")){if(!(p.getMainHandItem().getItem() instanceof PlanningItem))throw new IllegalArgumentException("请手持魔杖。");if(r.revision()!=s.selectionRevision)throw new IllegalArgumentException("选区已变化，请检查新的高度。");create(p,r.value());CompoundTag done=new CompoundTag();done.putBoolean("Founded",true);PacketDistributor.sendToPlayer(p,new CityNetwork.Snapshot(done));say(p,"城市已登记；城市魔杖调整范围，信息魔杖查看设置。");return;}
            if(r.op().equals("create")){create(p,r.value());snapshot(p,"聚落已创建。",false);return;}
            if(r.op().equals("list")){snapshot(p,"",false);return;}
            CityData.City c=data.require(UUID.fromString(r.city()));
            if(!c.dimension.equals(s.dimension))throw new IllegalArgumentException("聚落不在当前维度。");
            if(r.op().equals("select")){select(p,c);CityService.scan(c);snapshot(p,"",false);return;}
            if(!c.id.equals(s.city))throw new IllegalArgumentException("当前聚落已切换，请刷新沙盘。");
            if(r.op().equals("view")||r.op().equals("refresh")){
                s.x=Math.clamp(r.x(),c.bounds.min().getX(),Math.max(c.bounds.min().getX(),c.bounds.max().getX()-31));
                s.z=Math.clamp(r.z(),c.bounds.min().getZ(),Math.max(c.bounds.min().getZ(),c.bounds.max().getZ()-31));
                s.y=Math.clamp(r.y(),c.bounds.min().getY(),c.bounds.max().getY());s.surface=r.surface();
                if(r.op().equals("refresh"))CityService.scan(c);
                snapshot(p,"",false);return;
            }
            CityService.checkEditor(p,c,r.revision());String message;
            if(r.op().equals("delete")){
                CityService.delete(data,c,p,r.revision(),r.value());
                for(ServerPlayer viewer:p.server.getPlayerList().getPlayers()){
                    Session view=session(viewer);boolean affected=c.id.equals(view.city);
                    if(affected){view.city=null;view.lastSnapshot=null;viewer.getPersistentData().remove("CitySettlementsActive");}
                    snapshot(viewer,affected?"已删除城市「"+c.name+"」；建筑和NPC保留。":"城市列表已更新。",false);
                    preview(viewer);
                }return;
            }
            switch(r.op()){
                case "buildingadd" -> {String[] values=r.value().split("[|]",2);if(values.length!=2)throw new IllegalArgumentException("填写建筑名称和用途。");if(r.state()!=buildingDraft(p).selectionRevision)throw new IllegalArgumentException("建筑选区已改变，请刷新。");CityRegistry.building(data,c,values[0],values[1],selection(p,buildingDraft(p)));message="建筑已登记。";}
                case "wareadd" -> {BlockPos pos=new BlockPos(r.x(),r.y(),r.z());var hit=p.pick(6,0,false);if(!(hit instanceof net.minecraft.world.phys.BlockHitResult h)||hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK||!h.getBlockPos().equals(pos))throw new IllegalArgumentException("目标已改变，请面对容器重新打开信息魔杖。");CityRegistry.warehouse(data,c,p.serverLevel(),r.value(),pos);message="仓库已绑定；物品未移动。";}
                case "residentadd" -> {CityRegistry.bind(data,c,p,UUID.fromString(r.value()));message="NPC已绑定。";}
                case "buildingremove","wareremove","residentremove" -> {UUID id=UUID.fromString(r.value());boolean removed=switch(r.op()){case "buildingremove"->{c.housing.houses.remove(id);yield c.buildings.remove(id)!=null;}case "wareremove"->c.warehouses.remove(id)!=null;default->c.residents.remove(id);};if(!removed)throw new IllegalArgumentException("登记已不存在。");data.changed(c);message="已解除登记；世界建筑、物品和NPC保留。";}
                case "include","exclude" -> {
                    int minX=Math.min(r.x(),r.x2()),maxX=Math.max(r.x(),r.x2()),minZ=Math.min(r.z(),r.z2()),maxZ=Math.max(r.z(),r.z2());
                    if((long)maxX-minX>=32||(long)maxZ-minZ>=32)throw new IllegalArgumentException("一次框选最多32×32格。");
                    if(!c.bounds.contains(new BlockPos(minX,r.y(),minZ))||!c.bounds.contains(new BlockPos(maxX,r.y(),maxZ)))throw new IllegalArgumentException("框选超出聚落边界。");
                    List<BlockPos> points=new ArrayList<>();for(int z=minZ;z<=maxZ;z++)for(int x=minX;x<=maxX;x++)points.add(new BlockPos(x,r.y(),z));
                    int changed=CityService.membership(data,c,p.getUUID(),points,r.op().equals("include"));message="已调整 "+changed+" 格归属（世界方块未移动）。";
                }
                case "replace" -> {editor(p);CityService.replace(data,c,p.serverLevel(),p.getUUID(),new BlockPos(r.x(),r.y(),r.z()),r.state(),r.value());message="已替换世界方块，可撤销。";}
                case "undo" -> {if(CityService.undoChangesWorld(p.getUUID()))editor(p);CityService.undo(data,c,p.serverLevel(),p.getUUID());message="已撤销上一步。";}
                case "npcbind","npcunbind" -> {resident(p,r.op().equals("npcbind"));message="居民归属已更新。";}
                case "rename" -> {c.name=CityData.validName(r.value());data.changed(c);CityService.invalidate(c);message="名称已保存。";}
                case "resize" -> {CityBounds b=selection(p);data.checkBounds(c.id,c.dimension,b);CityRegistry.validateBounds(c,b);CityService.resizeAreas(c,b);c.bounds=b;c.excluded.removeIf(pos->!b.contains(BlockPos.of(pos)));data.changed(c);CityService.invalidate(c);select(p,c);message="范围已更新；新扩展部分默认纳入聚落。";}
                default -> throw new IllegalArgumentException("不支持的沙盘操作。");
            }
            snapshot(p,message,false);
        }catch(IllegalArgumentException ex){if(r.op().equals("buildingheight")&&buildingDraft(p).a!=null&&buildingDraft(p).b!=null){selectionSnapshot(p,false,ex.getMessage(),true);return;}if((r.op().equals("height")||r.op().equals("found")||r.op().startsWith("area"))&&s.a!=null&&s.b!=null)selectionSnapshot(p,r.op().equals("found")||r.surface(),ex.getMessage());else snapshot(p,ex.getMessage(),false);}
    }
    static void openCareerRegistry(ServerPlayer p,CityData.City c){select(p,c);snapshot(p,"先用建筑魔杖圈定房屋；登记后可直接打开属性和住户设置。",false,true);}
    private static void snapshot(ServerPlayer p,String message,boolean open){snapshot(p,message,open,false);}
    private static void snapshot(ServerPlayer p,String message,boolean open,boolean building){
        Session s=session(p);CityData data=CityData.get(p.server);CompoundTag t=new CompoundTag();t.putBoolean("Open",open);t.putBoolean("BuildingRegister",building);Session bd=buildingDraft(p);t.putBoolean("BuildingSelection",bd.a!=null&&bd.b!=null);t.putLong("BuildingSelectionRevision",bd.selectionRevision);t.putBoolean("Visible",s.visible);t.putString("Message",message);
        t.putBoolean("CanCreate",CityService.editor(p)||p.getMainHandItem().getItem() instanceof PlanningItem);t.putBoolean("Selection",s.a!=null&&s.b!=null);t.putLong("SelectionRevision",s.selectionRevision);
        if(s.a!=null)t.putString("CornerA",s.a.toShortString());if(s.b!=null)t.putString("CornerB",s.b.toShortString());
        ListTag cities=new ListTag();for(CityData.City c:data.cities())if(c.dimension.equals(s.dimension)){CompoundTag v=new CompoundTag();v.putString("Id",c.id.toString());v.putString("Name",c.name);cities.add(v);}t.put("Cities",cities);
        if(s.city!=null&&data.cities.containsKey(s.city)){
            CityData.City c=data.require(s.city);t.putString("Id",c.id.toString());t.putString("Name",c.name);t.putLong("Revision",c.revision);
            t.putBoolean("Editable",p.hasPermissions(2)||p.getUUID().equals(c.owner));t.putBoolean("WorldEditable",CityService.editor(p)&&(p.hasPermissions(2)||p.getUUID().equals(c.owner)));
            t.putInt("X",s.x);t.putInt("Y",s.y);t.putInt("Z",s.z);t.putBoolean("Surface",s.surface);t.putLong("Min",c.bounds.min().asLong());t.putLong("Max",c.bounds.max().asLong());t.putLong("Volume",c.bounds.volumeLong());t.putInt("Residents",c.residents.size());t.putInt("Stability",CityLiving.stability(CityEconomy.get(p.server),c));t.putString("PublicOrder",CitySecurity.summary(CityEconomy.get(p.server),c));t.putString("RiotRisk",CitySociety.risk(CitySecurity.riskIndex(CityEconomy.get(p.server),c)));t.putInt("Security",CityLaw.security(p.server,c.id));
            ListTag cells=new ListTag();for(int z=s.z;z<=Math.min(s.z+31,c.bounds.max().getZ());z++)for(int x=s.x;x<=Math.min(s.x+31,c.bounds.max().getX());x++){
                int y=s.y;BlockPos pos=new BlockPos(x,y,z);CompoundTag cell=new CompoundTag();cell.putInt("X",x);cell.putInt("Z",z);
                boolean loaded=p.serverLevel().hasChunkAt(pos);cell.putBoolean("Loaded",loaded);
                if(loaded){
                    if(s.surface){var chunk=p.serverLevel().getChunkSource().getChunkNow(x>>4,z>>4);if(chunk!=null)y=Math.min(y,Math.max(c.bounds.min().getY(),chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,x,z)));while(y>c.bounds.min().getY()&&p.serverLevel().getBlockState(new BlockPos(x,y,z)).isAir())y--;}
                    pos=new BlockPos(x,y,z);var state=p.serverLevel().getBlockState(pos);cell.putInt("State",Block.getId(state));cell.putInt("Color",state.getMapColor(p.serverLevel(),pos).col);cell.putBoolean("Air",state.isAir());
                }
                cell.putInt("Y",y);cell.putBoolean("Included",c.includes(pos));cells.add(cell);
            }t.put("Cells",cells);
            CityRegistry.snapshot(p,c,t);
            CityService.Statistics stats=CityService.statistics(c.id);t.putBoolean("Scanning",CityService.scanning(c.id));
            if(stats!=null){t.putBoolean("HasStats",true);t.putLong("NonAir",stats.nonAir());t.putLong("Excluded",stats.excluded());t.putLong("Unloaded",stats.unloaded());t.putLong("Age",Math.max(0,p.serverLevel().getGameTime()-stats.tick())/20);
                ListTag counts=new ListTag();stats.blocks().entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())).forEach(e->{CompoundTag v=new CompoundTag();v.putString("Block",e.getKey());v.putLong("Count",e.getValue());counts.add(v);});t.put("Counts",counts);
            }
        }
        s.lastSnapshot=t.copy();PacketDistributor.sendToPlayer(p,new CityNetwork.Snapshot(t));
    }
    private static void resident(ServerPlayer p,boolean bind){
        CityData data=CityData.get(p.server);CityData.City c=current(p);CityService.checkEditor(p,c,c.revision);
        var npcs=p.serverLevel().getEntities(p,p.getBoundingBox().inflate(8),entity->entity instanceof net.minecraft.world.entity.LivingEntity&&BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace().equals("customnpcs")&&entity.distanceToSqr(p)<=64);
        var npc=npcs.stream().min(Comparator.comparingDouble(e->e.distanceToSqr(p))).orElseThrow(()->new IllegalArgumentException("8格内没有CustomNPCs实体。"));
        if(bind){for(CityData.City other:data.cities())if(other.residents.contains(npc.getUUID())&&!other.id.equals(c.id))throw new IllegalArgumentException("此NPC已有聚落，请先解除原绑定。");c.residents.add(npc.getUUID());}
        else if(!c.residents.remove(npc.getUUID()))throw new IllegalArgumentException("最近的NPC不属于此聚落。");
        data.changed(c);CityService.invalidate(c);say(p,(bind?"已绑定居民：":"已解除居民绑定：")+npc.getName().getString()+" → "+c.name);
    }
    private static void commands(RegisterCommandsEvent e){
        var root=Commands.literal("city").executes(c->run(c.getSource(),CityMod::open))
            .then(Commands.literal("give").executes(c->run(c.getSource(),p->{editor(p);for(var item:List.of(SURVEYOR,BUILDER,ATLAS,LEDGER))p.getInventory().placeItemBackInInventory(new ItemStack(item.get()));})))
            .then(Commands.literal("create").then(Commands.argument("name",StringArgumentType.greedyString()).executes(c->run(c.getSource(),p->{create(p,StringArgumentType.getString(c,"name"));open(p);})) ))
            .then(Commands.literal("open").then(Commands.argument("name",StringArgumentType.greedyString()).executes(c->run(c.getSource(),p->{select(p,find(p,StringArgumentType.getString(c,"name")));open(p);}))))
            .then(Commands.literal("npc").then(Commands.literal("bind").executes(c->run(c.getSource(),p->resident(p,true)))).then(Commands.literal("unbind").executes(c->run(c.getSource(),p->resident(p,false)))))
            .then(Commands.literal("undo").executes(c->run(c.getSource(),p->{CityData.City city=current(p);CityService.checkEditor(p,city,city.revision);if(CityService.undoChangesWorld(p.getUUID()))editor(p);CityService.undo(CityData.get(p.server),city,p.serverLevel(),p.getUUID());say(p,"已撤销。");})))
            .then(Commands.literal("help").executes(c->run(c.getSource(),p->{CompoundTag help=new CompoundTag();help.putBoolean("Help",true);PacketDistributor.sendToPlayer(p,new CityNetwork.Snapshot(help));})));
        for(int i=1;i<=2;i++){boolean second=i==2;root.then(Commands.literal("pos"+i).executes(c->run(c.getSource(),p->point(p,p.blockPosition(),second)))
            .then(Commands.argument("pos",BlockPosArgument.blockPos()).executes(c->run(c.getSource(),p->point(p,BlockPosArgument.getBlockPos(c,"pos"),second)))));}
        root.then(Commands.literal("height").executes(c->run(c.getSource(),p->selectionSnapshot(p,false,"")))
            .then(Commands.literal("auto").executes(c->run(c.getSource(),p->{height(p,true,0,0);say(p,"已启用自动建筑高度。");})))
            .then(Commands.literal("set").then(Commands.argument("bottom",IntegerArgumentType.integer()).then(Commands.argument("top",IntegerArgumentType.integer()).executes(c->run(c.getSource(),p->{height(p,false,IntegerArgumentType.getInteger(c,"bottom"),IntegerArgumentType.getInteger(c,"top"));say(p,"手动高度已应用。");})))))
            .then(Commands.literal("corners").executes(c->run(c.getSource(),p->{Session s=session(p);requireCorners(s);height(p,false,Math.min(s.a.getY(),s.b.getY()),Math.max(s.a.getY(),s.b.getY()));say(p,"已使用两角原始高度。");}))));
        e.getDispatcher().register(root);CityTradeLab.register(e.getDispatcher());CityBlueprints.register(e.getDispatcher());CityMaintenance.register(e.getDispatcher());CityEconomyAccess.register(e.getDispatcher());RuralWorkVisuals.register(e.getDispatcher());
    }
    private static void tool(ServerPlayer p,String kind,BlockPos clicked){editor(p);
        Session s=session(p);
        switch(kind){
            case "builder" -> {Session b=buildingDraft(p);if(clicked!=null)point(p,clicked,p.isShiftKeyDown(),b);else if(p.isShiftKeyDown()){b.a=null;b.b=null;b.bottom=null;b.top=null;b.preview=null;b.selectionRevision++;say(p,"建筑选区已清空。");}else selectionSnapshot(p,false,"建筑选区独立于城市选区。",true);}
            case "surveyor" -> {if(clicked!=null)point(p,clicked,p.isShiftKeyDown());else if(p.isShiftKeyDown()){s.a=null;s.b=null;s.bottom=null;s.top=null;s.preview=null;s.selectionRevision++;say(p,"选区已清空。");}else selectionSnapshot(p,true,"");}
            case "charter" -> selectionSnapshot(p,true,"");
            case "atlas" -> {if(p.isShiftKeyDown()&&clicked!=null){CityData.City c=CityData.get(p.server).cities().stream().filter(v->v.dimension.equals(s.dimension)&&v.bounds.contains(clicked)).findFirst().orElseThrow(()->new IllegalArgumentException("此处没有聚落。"));select(p,c);say(p,"当前规划："+c.name);}else open(p);}
            case "claim","exclude" -> {
                if(s.city==null&&clicked!=null)CityData.get(p.server).cities().stream().filter(c->c.dimension.equals(s.dimension)&&c.bounds.contains(clicked)).findFirst().ifPresent(c->select(p,c));
                CityData.City c=current(p);CityService.checkEditor(p,c,c.revision);
                if(clicked==null&&!p.isShiftKeyDown())throw new IllegalArgumentException("右键方块调整一格；潜行右键应用城市魔杖选区。");
                CityBounds b=p.isShiftKeyDown()?selection(p):CityBounds.between(clicked,clicked);
                long changed=CityService.area(CityData.get(p.server),c,p.serverLevel(),p.getUUID(),b,kind.equals("claim"));
                say(p,c.name+"："+(kind.equals("claim")?"纳入 ":"排除 ")+changed+" 格；/city undo 撤销。世界方块不变。");
            }
        }
    }
    private static CompoundTag overlayBox(CityBounds b){CompoundTag t=new CompoundTag();t.putLong("Min",b.min().asLong());t.putLong("Max",b.max().asLong());return t;}
    private static void preview(ServerPlayer p){
        if(!(p.getMainHandItem().getItem() instanceof PlanningItem))return;Session s=session(p);
        if(s.city==null)CityData.get(p.server).cities().stream().filter(c->c.dimension.equals(s.dimension)&&c.bounds.contains(p.blockPosition())).findFirst().ifPresent(c->select(p,c));
        CompoundTag t=new CompoundTag();t.putBoolean("Overlay",true);t.putString("Dimension",s.dimension);
        if(s.visible){
            if(s.city!=null){CityData.City c=CityData.get(p.server).cities.get(s.city);if(c!=null){t.put("City",overlayBox(c.bounds));t.put("Runs",CityOverlay.runs(c,p.blockPosition()));
                var hit=p.pick(6,0,false);BlockPos aimed=hit instanceof net.minecraft.world.phys.BlockHitResult block?block.getBlockPos():p.blockPosition();
                p.displayClientMessage(Component.literal(c.name+" | 脚下:"+(c.includes(p.blockPosition())?"城内":"城外")+" 指向:"+(c.includes(aimed)?"纳入":"未纳入")+" | 绿=归属 红=排除 青=待应用（附近8格）"),true);
            }}
            Session draft=p.getMainHandItem().getItem()==BUILDER.get()?buildingDraft(p):s;if(draft.preview!=null)t.put("Draft",overlayBox(draft.preview));else if(draft.a!=null)t.put("Draft",overlayBox(CityBounds.between(draft.a,draft.a)));
        }
        PacketDistributor.sendToPlayer(p,new CityNetwork.Snapshot(t));
    }
    public static final class PlanningItem extends Item {
        final String kind;PlanningItem(String kind){super(new Properties().stacksTo(1));this.kind=kind;}
        @Override public void inventoryTick(ItemStack stack,Level level,net.minecraft.world.entity.Entity entity,int slot,boolean selected){
            if(!level.isClientSide&&entity instanceof Player p&&(kind.equals("charter")||kind.equals("claim")||kind.equals("exclude"))&&slot>=0&&slot<p.getInventory().getContainerSize()&&p.getInventory().getItem(slot)==stack)
                p.getInventory().setItem(slot,new ItemStack(kind.equals("charter")?ATLAS:SURVEYOR,stack.getCount(),stack.getComponentsPatch()));
        }
        @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext c){return useOn(c);}
        @Override public InteractionResult useOn(UseOnContext c){if(c.getHand()!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;if(c.getPlayer() instanceof ServerPlayer p)run(p.createCommandSourceStack(),q->tool(q,kind,c.getClickedPos()));return InteractionResult.sidedSuccess(c.getLevel().isClientSide);}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){if(hand!=InteractionHand.MAIN_HAND)return InteractionResultHolder.pass(player.getItemInHand(hand));if(player instanceof ServerPlayer p)run(p.createCommandSourceStack(),q->tool(q,kind,null));return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);}
        @Override public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state,Level level,BlockPos pos,Player player){return false;}
        @Override public boolean doesSneakBypassUse(ItemStack stack,net.minecraft.world.level.LevelReader level,BlockPos pos,Player player){return true;}
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){String text=switch(kind){case "builder"->"右键第一角，潜行右键第二角；右键空气调高度并登记建筑";case "surveyor"->"右键地面第一角，潜行右键第二角；右键空气新建/纳入/排除";case "charter"->"框选后右键：命名并登记聚落";case "atlas"->"右键城市设置；潜行右键目标城市切换；手持显示归属";default->"右键方块调整一格；潜行右键应用整个选区";};lines.add(Component.literal(text));lines.add(Component.literal("只调整聚落数据，不破坏世界方块"));}
    }
}










