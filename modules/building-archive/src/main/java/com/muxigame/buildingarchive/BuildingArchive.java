package com.muxigame.buildingarchive;

import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.*;
import java.nio.file.*;
import java.util.*;

@Mod(BuildingArchive.ID)
public final class BuildingArchive {
    public static final String ID="building_archive";
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(ID);
    public static final DeferredItem<Item> TOOL=ITEMS.register("archive_wand",()->new Wand(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> TERRAIN=ITEMS.register("terrain_tool",TerrainTool::new);
    public static final DeferredItem<Item> TRANSFER=ITEMS.register("archive_transfer",()->new TransferTool(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> GUIDE=ITEMS.register("archive_guide",()->new Guide(new Item.Properties().stacksTo(1)));
    public static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,ID);
    public static final DeferredHolder<CreativeModeTab,CreativeModeTab> TAB=TABS.register("archive",()->CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.building_archive")).icon(()->new ItemStack(TOOL.get()))
        .displayItems((params,out)->{out.accept(TOOL.get());out.accept(GUIDE.get());out.accept(TERRAIN.get());out.accept(TRANSFER.get());}).build());
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final class Session {
        int rotation; CompoundTag deletion; BlockPos deletionOrigin; long deletionExpires;
        int[] projection=new int[0];long revision; String loadedName=""; String dimension; BlockPos a,b,anchor; CompoundTag loaded,undo; BlockPos undoOrigin;String undoDimension;
    }
    public BuildingArchive(IEventBus bus) {
        ITEMS.register(bus);TABS.register(bus);bus.addListener(ArchiveNetwork::register);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e)->{if(e.getServer().getTickCount()%20==0)for(ServerPlayer p:e.getServer().getPlayerList().getPlayers()){guide(p);TerrainTool.guide(p);ArchiveTransfer.expire();}});
        NeoForge.EVENT_BUS.addListener(BuildingArchive::commands);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e)->{ArchiveTransfer.clear(e.getEntity().getUUID());SESSIONS.remove(e.getEntity().getUUID());TerrainTool.STATES.remove(e.getEntity().getUUID());});
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e)->{SESSIONS.clear();TerrainTool.STATES.clear();ArchiveTransfer.clearAll();});
    }
    private static Session session(ServerPlayer p) {
        Session s=SESSIONS.computeIfAbsent(p.getUUID(),id->new Session());String dim=p.level().dimension().location().toString();
        if(!dim.equals(s.dimension)) {s.a=null;s.b=null;s.anchor=null;s.deletion=null;s.dimension=dim;s.revision++;}
        return s;
    }
    private static boolean allowed(ServerPlayer p) {return p.isCreative()||p.hasPermissions(2);}
    private static Path root(ServerPlayer p) {return p.server.getWorldPath(LevelResource.ROOT).resolve("building_archive");}
    private static void say(ServerPlayer p,String text) {p.sendSystemMessage(Component.literal("[建筑档案] "+text));}
    @FunctionalInterface private interface Action {void run(ServerPlayer p) throws Exception;}
    private static int run(CommandSourceStack source,Action action) {
        try {ServerPlayer p=source.getPlayerOrException();if(!allowed(p))throw new IllegalArgumentException("仅创造模式玩家或管理员可以使用建筑档案工具。");action.run(p);return 1;}
        catch(Exception e){source.sendFailure(Component.literal("[建筑档案] "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage())));return 0;}
    }
    private static void point(ServerPlayer p,BlockPos pos,boolean second) {
        Session s=session(p);s.revision++;s.deletion=null;s.rotation=0;s.loaded=null;s.loadedName="";s.anchor=null;if(second)s.b=pos.immutable();else s.a=pos.immutable();
        say(p,(second?"第二角点：":"第一角点：")+pos.toShortString());
        if(s.a!=null&&s.b!=null) {var b=ArchiveData.bounds(s.a,s.b);outline(p,b.origin(),b.size());say(p,"选区 "+b.size().toShortString()+"；右键空气打开面板保存。");}
    }
    static void outline(ServerPlayer p,BlockPos start,Vec3i size) {
        // All twelve box edges, bounded particle count and visible only to this player.
        double[] length={size.getX(),size.getY(),size.getZ()};
        for(int axis=0;axis<3;axis++) for(int a=0;a<2;a++) for(int b=0;b<2;b++) {
            int steps=Math.min(64,(int)length[axis]*2);
            for(int i=0;i<=steps;i++) {
                double[] v={start.getX(),start.getY(),start.getZ()};v[axis]+=length[axis]*i/steps;
                v[(axis+1)%3]+=a*length[(axis+1)%3];v[(axis+2)%3]+=b*length[(axis+2)%3];
                p.serverLevel().sendParticles(p,ParticleTypes.END_ROD,true,v[0],v[1],v[2],1,0,0,0,0);
            }
        }
    }
    private static void export(ServerPlayer p,String name) throws Exception {
        Session s=session(p);if(s.a==null||s.b==null)throw new IllegalArgumentException("先手持道具右键选择第一角，潜行右键选择第二角。");
        var bounds=ArchiveData.bounds(s.a,s.b);Path file=ArchiveData.file(root(p),name);
        var data=ArchiveData.capture(p.serverLevel(),bounds.origin(),bounds.size());
        ArchiveData.checkRuralLinks(data,bounds.size());
        var report=ArchiveData.analysis(data);
        ArchiveData.writeNew(file,data);
        try {Files.writeString(root(p).resolve(name+".analysis.json"),new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report),StandardOpenOption.CREATE_NEW);}
        catch(Exception e){say(p,"NBT已保存，但统计报告写入失败："+e.getMessage());}
        s.rotation=0;s.deletion=null;s.loaded=data;s.projection=ArchiveData.projectionRuns(data);s.loadedName=name;s.anchor=null;s.revision++;
        say(p,"已导出 "+name+"："+report.get("nonAir")+"个非空气方块，"+report.getAsJsonObject("blocks").size()+"种方块。右键地面可预览导入位置。");
        say(p,"服务端文件："+file.toAbsolutePath());
    }
    private static void load(ServerPlayer p,String name) throws Exception {
        CompoundTag data=ArchiveData.read(ArchiveData.file(root(p),name));var size=ArchiveData.validate(data);
        Session s=session(p);s.rotation=0;s.deletion=null;s.loaded=data;s.projection=ArchiveData.projectionRuns(data);s.loadedName=name;s.anchor=null;s.revision++;say(p,"已载入 "+name+"（"+size.toShortString()+"）。右键方块表面选择放置原点；不会立即放置。");
    }
    private static void preview(ServerPlayer p,BlockPos pos) {
        Session s=session(p);if(s.loaded==null)throw new IllegalArgumentException("请先 /ba load 名称。");
        int occupied=ArchiveData.occupied(p.serverLevel(),pos,s.loaded);s.anchor=pos.immutable();s.revision++;outline(p,pos,ArchiveData.validate(s.loaded));
        say(p,"原点 "+pos.toShortString()+"；目标已有 "+occupied+" 格方块。建筑向 +X、+Y、+Z 延伸，包含保存的空气。");
        String command=occupied==0?"/ba paste":"/ba paste replace";
        p.sendSystemMessage(Component.literal(occupied==0?"[点击填入放置命令]":"[点击填入覆盖命令，覆盖含空气]")
            .withStyle(style->style.withColor(net.minecraft.ChatFormatting.AQUA).withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,command))));
    }
    private static void paste(ServerPlayer p,boolean replace) throws Exception {
        Session s=session(p);if(s.loaded==null||s.anchor==null)throw new IllegalArgumentException("请先载入档案并右键预览目标位置。");
        int occupied=ArchiveData.occupied(p.serverLevel(),s.anchor,s.loaded);
        if(occupied>0&&!replace)throw new IllegalArgumentException("目标存在方块；确定覆盖时使用 /ba paste replace。");
        Vec3i size=ArchiveData.validate(s.loaded);var before=ArchiveData.capture(p.serverLevel(),s.anchor,size);
        Path undoRoot=root(p).resolve("backups");String name="undo_"+p.getUUID()+"_"+System.currentTimeMillis();
        ArchiveData.writeNew(ArchiveData.file(undoRoot,name),before);
        // Keep a usable undo even if a third-party block throws during placement.
        s.undo=before;s.undoOrigin=s.anchor;s.undoDimension=s.dimension;
        ArchiveData.place(p.serverLevel(),s.anchor,s.loaded);say(p,"放置完成。/ba undo 撤销本次放置；原区域备份：backups/"+name+".nbt");clearLoaded(s);
    }
    private static void clearLoaded(Session s){s.loaded=null;s.loadedName="";s.anchor=null;s.projection=new int[0];s.rotation=0;s.deletion=null;s.revision++;}
    private static void undo(ServerPlayer p) {
        Session s=session(p);if(s.undo==null)throw new IllegalArgumentException("本次登录没有可撤销的操作。");
        if(!s.dimension.equals(s.undoDimension))throw new IllegalArgumentException("请回到操作所在维度再撤销。");
        ArchiveData.place(p.serverLevel(),s.undoOrigin,s.undo);s.undo=null;s.revision++;say(p,"已恢复操作前的区域（会覆盖该区域此后进行的编辑）。");
    }
    private static void rotate(ServerPlayer p,boolean clockwise){
        Session s=session(p);if(s.loaded==null||s.deletion!=null)throw new IllegalArgumentException("请先载入要旋转的建筑。");
        var rotated=ArchiveData.rotate(s.loaded,p.serverLevel().registryAccess().lookupOrThrow(Registries.BLOCK),clockwise);
        if(s.anchor!=null)ArchiveData.checkArea(p.serverLevel(),s.anchor,ArchiveData.validate(rotated));
        s.loaded=rotated;s.rotation=Math.floorMod(s.rotation+(clockwise?90:-90),360);s.projection=ArchiveData.projectionRuns(rotated);s.revision++;
    }
    private static void mirror(ServerPlayer p){
        Session s=session(p);if(s.loaded==null||s.deletion!=null)throw new IllegalArgumentException("请先载入要镜像的建筑。");
        var mirrored=ArchiveData.mirror(s.loaded,p.serverLevel().registryAccess().lookupOrThrow(Registries.BLOCK));
        if(s.anchor!=null)ArchiveData.checkArea(p.serverLevel(),s.anchor,ArchiveData.validate(mirrored));
        s.loaded=mirrored;s.projection=ArchiveData.projectionRuns(mirrored);s.revision++;
    }
    static void renameArchive(ServerPlayer p,String oldName,String newName) throws Exception {
        Path folder=root(p),oldFile=ArchiveData.file(folder,oldName),newFile=ArchiveData.file(folder,newName);
        if(!Files.isRegularFile(oldFile,LinkOption.NOFOLLOW_LINKS))throw new IllegalArgumentException("原建筑记录已不存在，请刷新列表。");
        if(oldName.equals(newName))return;
        Path oldReport=folder.resolve(oldName+".analysis.json"),newReport=folder.resolve(newName+".analysis.json");
        if(Files.exists(newFile,LinkOption.NOFOLLOW_LINKS)||Files.exists(newReport,LinkOption.NOFOLLOW_LINKS))throw new IllegalArgumentException("该名称已存在，请换一个名字。");
        if(Files.exists(oldReport,LinkOption.NOFOLLOW_LINKS)&&!Files.isRegularFile(oldReport,LinkOption.NOFOLLOW_LINKS))throw new IllegalArgumentException("统计文件类型异常，未改名。");
        boolean movedReport=false;
        try{if(Files.exists(oldReport)){Files.move(oldReport,newReport);movedReport=true;}Files.move(oldFile,newFile);}
        catch(Exception e){if(movedReport)Files.move(newReport,oldReport);throw e;}
        for(Session other:SESSIONS.values())if(oldName.equals(other.loadedName)){other.loadedName=newName;other.revision++;}
        session(p).revision++;
    }
    static void deleteArchive(ServerPlayer p,String name) throws Exception {
        Path folder=root(p),file=ArchiveData.file(folder,name),report=folder.resolve(name+".analysis.json");
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IllegalArgumentException("该建筑档案已不存在，请刷新列表。");
        if(Files.exists(report,LinkOption.NOFOLLOW_LINKS)&&!Files.isRegularFile(report,LinkOption.NOFOLLOW_LINKS))throw new IllegalArgumentException("统计文件类型异常，未删除档案。");
        Path trash=folder.resolve("deleted");Files.createDirectories(trash);if(Files.isSymbolicLink(trash))throw new IllegalArgumentException("备份目录不能是符号链接。");
        Path backup=Files.createDirectory(trash.resolve(UUID.randomUUID().toString()));
        // Move the report first; restore it if moving the authoritative NBT fails.
        boolean movedReport=false;
        try{if(Files.exists(report)){Files.move(report,backup.resolve(report.getFileName()));movedReport=true;}Files.move(file,backup.resolve(file.getFileName()));}
        catch(Exception e){if(movedReport)Files.move(backup.resolve(report.getFileName()),report);throw e;}
        for(Session other:SESSIONS.values()){if(name.equals(other.loadedName)){other.loaded=null;other.loadedName="";other.anchor=null;other.projection=new int[0];other.rotation=0;other.revision++;}}
        session(p).revision++;say(p,"已删除建筑记录「"+name+"」，世界里的建筑保留。备份：deleted/"+backup.getFileName());
    }
    private static void prepareDelete(ServerPlayer p){
        Session s=session(p);if(s.a==null||s.b==null||s.loaded!=null)throw new IllegalArgumentException("先点击重新选区，再框住要删除的建筑。");
        var bounds=ArchiveData.bounds(s.a,s.b);var captured=ArchiveData.capture(p.serverLevel(),bounds.origin(),bounds.size());
        s.deletion=captured;s.deletionOrigin=bounds.origin();s.deletionExpires=p.level().getGameTime()+600;s.revision++;
    }
    private static void deleteConfirmed(ServerPlayer p) throws Exception {
        Session s=session(p);if(s.deletion==null)throw new IllegalArgumentException("请先预览删除范围。");
        if(p.level().getGameTime()>s.deletionExpires){s.deletion=null;s.revision++;throw new IllegalArgumentException("删除确认已超过30秒，请重新预览范围。");}
        var current=ArchiveData.capture(p.serverLevel(),s.deletionOrigin,ArchiveData.validate(s.deletion));
        if(!current.get("blocks").equals(s.deletion.get("blocks"))||!current.get("palette").equals(s.deletion.get("palette"))){s.deletion=null;s.revision++;throw new IllegalArgumentException("范围里的方块或容器已变化，请重新预览删除。");}
        String name="delete_"+p.getUUID()+"_"+System.currentTimeMillis();ArchiveData.writeNew(ArchiveData.file(root(p).resolve("backups"),name),current);
        s.undo=current;s.undoOrigin=s.deletionOrigin;s.undoDimension=s.dimension;
        ArchiveData.place(p.serverLevel(),s.deletionOrigin,ArchiveData.emptyRegion(current));s.deletion=null;s.revision++;
        say(p,"已删除选区内的方块（含容器内容），不产生掉落物。可撤销上次操作；磁盘备份：backups/"+name+".nbt");
    }
    private static void guide(ServerPlayer p){
        if(!allowed(p)||!p.getMainHandItem().is(TOOL.get()))return;
        Session s=session(p);String hint=s.deletion!=null?"红框为删除范围 · 右键空气确认或取消 · 将清除方块和容器内容":s.loaded!=null?(s.anchor==null?"已载入：瞄准64格内的地面右键远程预览 · 潜行右键空气打开面板":"投影已固定 · 右键空气调整 / 确认 · 右键方块重新定位"):(s.a==null?"第1步：右键建筑底部一角":s.b==null?"第2步：右键建筑对角（包括屋顶高度）":"选区完成 · 右键空气命名保存");
        p.displayClientMessage(Component.literal(hint),true);
        try{if(s.deletion!=null||(s.loaded!=null&&s.anchor!=null))sendProjection(p);else if(s.a!=null&&s.b!=null){var b=ArchiveData.bounds(s.a,s.b);outline(p,b.origin(),b.size());}}catch(IllegalArgumentException ignored){}
    }
    static CompoundTag snapshot(ServerPlayer p,String message,int page) throws Exception{
        Session s=session(p);CompoundTag t=new CompoundTag();t.putLong("Revision",s.revision);t.putString("Message",message);t.putString("A",s.a==null?"尚未选择":s.a.toShortString());t.putString("B",s.b==null?"尚未选择":s.b.toShortString());
        t.putBoolean("Selected",s.a!=null&&s.b!=null);t.putBoolean("Loaded",s.loaded!=null);t.putString("Name",s.loadedName);t.putBoolean("Undo",s.undo!=null&&s.dimension.equals(s.undoDimension));t.putBoolean("Preview",s.anchor!=null&&s.loaded!=null);
        if(s.a!=null&&s.b!=null){try{var b=ArchiveData.bounds(s.a,s.b);t.putString("Size",b.size().toShortString());t.putInt("Low",b.origin().getY());t.putInt("High",b.origin().getY()+b.size().getY()-1);t.putBoolean("Valid",true);}catch(IllegalArgumentException e){t.putString("Message",e.getMessage());}}
        if(s.loaded!=null){t.putString("LoadedSize",ArchiveData.validate(s.loaded).toShortString());var stats=ArchiveData.analysis(s.loaded);t.putInt("NonAir",stats.get("nonAir").getAsInt());var counts=stats.getAsJsonObject("blocks");t.putInt("Kinds",counts.size());var materials=new net.minecraft.nbt.ListTag();counts.entrySet().stream().sorted((a,b)->Integer.compare(b.getValue().getAsInt(),a.getValue().getAsInt())).limit(5).forEach(e->{CompoundTag row=new CompoundTag();row.putString("Id",e.getKey());row.putInt("Count",e.getValue().getAsInt());materials.add(row);});t.put("Materials",materials);}
        if(t.getBoolean("Preview")){t.putString("Anchor",s.anchor.toShortString());t.putInt("AnchorX",s.anchor.getX());t.putInt("AnchorY",s.anchor.getY());t.putInt("AnchorZ",s.anchor.getZ());try{t.putInt("Occupied",ArchiveData.occupied(p.serverLevel(),s.anchor,s.loaded));t.putBoolean("CanPaste",true);}catch(IllegalArgumentException e){t.putString("Message",e.getMessage());}}
        Files.createDirectories(root(p));try(var files=Files.list(root(p))){var names=files.filter(f->Files.isRegularFile(f,LinkOption.NOFOLLOW_LINKS)&&f.getFileName().toString().endsWith(".nbt")).map(f->f.getFileName().toString().replaceFirst("\\.nbt$","")).sorted().toList();int pages=Math.max(1,(names.size()+4)/5),current=Math.max(0,Math.min(page,pages-1));t.putInt("Page",current);t.putInt("Pages",pages);t.putInt("Total",names.size());var list=new net.minecraft.nbt.ListTag();names.stream().skip(current*5L).limit(5).forEach(n->list.add(net.minecraft.nbt.StringTag.valueOf(n)));t.put("Files",list);}
        t.putInt("Rotation",s.rotation);t.putBoolean("DeletePreview",s.deletion!=null);
        if(s.deletion!=null){var size=ArchiveData.validate(s.deletion);t.putBoolean("Preview",true);t.putString("Anchor",s.deletionOrigin.toShortString());t.putInt("AnchorY",s.deletionOrigin.getY());t.putString("LoadedSize",size.toShortString());t.putBoolean("CanPaste",true);}
        t.put("Projection",projection(p));return t;
    }
    private static void panel(ServerPlayer p,String message,int page) throws Exception{net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,new ArchiveNetwork.Snapshot(snapshot(p,message,page)));}
    private static CompoundTag projection(ServerPlayer p){Session s=session(p);CompoundTag t=new CompoundTag();t.putString("Dimension",s.dimension);if(s.deletion!=null){var size=ArchiveData.validate(s.deletion);t.putBoolean("Visible",true);t.putBoolean("Delete",true);t.putLong("Origin",s.deletionOrigin.asLong());t.putIntArray("Size",new int[]{size.getX(),size.getY(),size.getZ()});t.putIntArray("Runs",new int[0]);}else if(s.loaded!=null&&s.anchor!=null){Vec3i size=ArchiveData.validate(s.loaded);t.putBoolean("Visible",true);t.putLong("Origin",s.anchor.asLong());t.putIntArray("Size",new int[]{size.getX(),size.getY(),size.getZ()});t.putIntArray("Runs",s.projection);}return t;}
    private static void sendProjection(ServerPlayer p){CompoundTag t=new CompoundTag();t.putBoolean("ProjectionOnly",true);t.put("Projection",projection(p));net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,new ArchiveNetwork.Snapshot(t));}
    static BlockPos remoteTarget(ServerPlayer p){
        var start=p.getEyePosition();var direction=p.getLookAngle();double distance=64;
        // Never force-load chunks along the sight line.
        for(double d=0;d<=64;d+=.5){var pos=BlockPos.containing(start.add(direction.scale(d)));if(!p.serverLevel().hasChunkAt(pos)){distance=Math.max(0,d-.5);break;}}
        var hit=p.serverLevel().clip(new net.minecraft.world.level.ClipContext(start,start.add(direction.scale(distance)),net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.ANY,p));
        if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK)throw new IllegalArgumentException("没有找到64格内的地面。请对准地面或方块，再试一次。");
        return hit.getBlockPos().relative(hit.getDirection());
    }
    static BlockPos shiftedAnchor(ServerPlayer p,BlockPos current,String value){
        if(current==null)throw new IllegalArgumentException("先选择投影位置。");String[] v=value.split(",");if(v.length!=3)throw new IllegalArgumentException("移动参数无效。");int x=Integer.parseInt(v[0]),y=Integer.parseInt(v[1]),z=Integer.parseInt(v[2]);if(Math.abs((long)x)+Math.abs((long)y)+Math.abs((long)z)!=1&&Math.abs((long)x)+Math.abs((long)y)+Math.abs((long)z)!=5)throw new IllegalArgumentException("每次只能移动1格或5格。");if((x!=0?1:0)+(y!=0?1:0)+(z!=0?1:0)!=1)throw new IllegalArgumentException("一次只能调整一个方向。");BlockPos next=current.offset(x,y,z);if(p.getEyePosition().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(next))>96)throw new IllegalArgumentException("投影离你超过96格，请靠近后调整。");return next;
    }
    static void action(ServerPlayer p,ArchiveNetwork.Request r){
        if(r.op().startsWith("terrain_")){TerrainTool.action(p,r);return;}
        if(!allowed(p))return;String message="";
        try{Session s=session(p);if(!r.op().equals("refresh")&&r.revision()!=s.revision)throw new IllegalArgumentException("选区或预览已变化，请按最新面板重新操作。");
            switch(r.op()){
                case "rename_archive" -> {String[] names=r.value().split("\\n",-1);if(names.length!=2)throw new IllegalArgumentException("改名参数无效。");renameArchive(p,names[0],names[1].trim());message="建筑记录已命名为「"+names[1].trim()+"」。";}
                case "delete_archive" -> {deleteArchive(p,r.value());message="已从档案列表移除「"+r.value()+"」。世界里的建筑保留，文件已备份到deleted目录。";}
                case "mirror" -> {mirror(p);message="已左右镜像（东西方向），高度不变。再点一次即可还原。";}
                case "rotate_left" -> {rotate(p,false);message="已向左旋转90°，请核对投影后放置。";}
                case "rotate_right" -> {rotate(p,true);message="已向右旋转90°，请核对投影后放置。";}
                case "delete_preview" -> {prepareDelete(p);message="红框内所有方块及容器内容都会删除，不掉落物品；NPC和生物不删除。30秒内确认。";}
                case "delete_confirm" -> {deleteConfirmed(p);message="删除完成，可用撤销上次操作恢复。";}
                case "refresh" -> {}
                case "library" -> {clearLoaded(s);message="请选择下一份建筑；已放置的建筑和撤销记录不受影响。";}
                case "aim" -> {preview(p,remoteTarget(p));message="远程投影已固定。返回现场绕着看，或在面板调整位置。";}
                case "move" -> {preview(p,shiftedAnchor(p,s.anchor,r.value()));message="投影已移动，世界方块尚未改变。";}
                case "select" -> {s.deletion=null;s.a=null;s.b=null;s.loaded=null;s.loadedName="";s.anchor=null;s.revision++;message="回到现场，连续右键建筑的两个角点。";}
                case "export" -> {String name=r.value().trim();if(name.isEmpty())name="建筑_"+System.currentTimeMillis();export(p,name);message="保存成功。可去现场选择导入位置，也可开始新的选区。";}
                case "load" -> {load(p,r.value());message="载入成功。返回现场，右键目标地面预览。";}
                case "height" -> {if(s.a==null||s.b==null)throw new IllegalArgumentException("请先选好两个角点。");String[] parts=r.value().split(",");if(parts.length!=2)throw new IllegalArgumentException("请填写最低和最高高度。");int low=Integer.parseInt(parts[0]),high=Integer.parseInt(parts[1]);if(low>high)throw new IllegalArgumentException("最低高度不能大于最高高度。");BlockPos a=new BlockPos(s.a.getX(),low,s.a.getZ()),b=new BlockPos(s.b.getX(),high,s.b.getZ());var bounds=ArchiveData.bounds(a,b);ArchiveData.checkArea(p.serverLevel(),bounds.origin(),bounds.size());s.a=a;s.b=b;s.revision++;message="高度已调整，请核对现场边框。";}
                case "paste" -> {paste(p,false);message="建筑已放置，请从右侧列表选择下一份建筑；仍可撤销。";}
                case "replace" -> {paste(p,true);message="覆盖放置完成；已保留原区域备份。";}
                case "cancel" -> {s.deletion=null;s.anchor=null;s.revision++;message="已取消目标位置；可以重新选择。";}
                case "undo" -> {undo(p);message="已恢复操作前的区域。";}
                default -> throw new IllegalArgumentException("未知操作。");
            }
        }catch(Exception e){message=e instanceof NumberFormatException?"高度必须是整数。":String.valueOf(e.getMessage());}
        try{panel(p,message,r.page());}catch(Exception e){say(p,"面板读取失败："+e.getMessage());}
    }
    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ba").requires(src->src.getEntity() instanceof ServerPlayer p&&allowed(p))
            .executes(c->run(c.getSource(),p->panel(p,"",0)))
            .then(Commands.literal("give").executes(c->run(c.getSource(),p->{p.getInventory().placeItemBackInInventory(new ItemStack(TOOL.get()));p.getInventory().placeItemBackInInventory(new ItemStack(TRANSFER.get()));})))
            .then(Commands.literal("files").executes(c->run(c.getSource(),ArchiveTransfer::panel)))
            .then(Commands.literal("select").executes(c->run(c.getSource(),p->{Session s=session(p);s.deletion=null;s.loaded=null;s.anchor=null;s.a=null;s.b=null;s.revision++;say(p,"重新选区：连续右键两个角点，右键空气打开面板。");})))
            .then(Commands.literal("pos1").executes(c->run(c.getSource(),p->point(p,p.blockPosition(),false))))
            .then(Commands.literal("pos2").executes(c->run(c.getSource(),p->point(p,p.blockPosition(),true))))
            .then(Commands.literal("export").then(Commands.argument("name",StringArgumentType.word()).executes(c->run(c.getSource(),p->export(p,StringArgumentType.getString(c,"name"))))))
            .then(Commands.literal("load").then(Commands.argument("name",StringArgumentType.word()).executes(c->run(c.getSource(),p->load(p,StringArgumentType.getString(c,"name"))))))
            .then(Commands.literal("list").executes(c->run(c.getSource(),p->{Files.createDirectories(root(p));try(var files=Files.list(root(p))){say(p,"档案："+String.join(", ",files.filter(f->f.toString().endsWith(".nbt")).map(f->f.getFileName().toString().replaceFirst("\\.nbt$","")).sorted().limit(100).toList()));}})))
            .then(Commands.literal("preview").executes(c->run(c.getSource(),p->preview(p,p.blockPosition()))))
            .then(Commands.literal("paste").executes(c->run(c.getSource(),p->paste(p,false)))
                .then(Commands.literal("replace").executes(c->run(c.getSource(),p->paste(p,true)))))
            .then(Commands.literal("undo").executes(c->run(c.getSource(),BuildingArchive::undo))));
    }
    public static final class TransferTool extends Item {
        TransferTool(Properties p){super(p);}
        @Override public InteractionResultHolder<ItemStack> use(Level level,net.minecraft.world.entity.player.Player player,InteractionHand hand){if(player instanceof ServerPlayer p)ArchiveTransfer.panel(p);return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);}
        @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext c){return useOn(c);}
        @Override public InteractionResult useOn(UseOnContext c){if(c.getPlayer() instanceof ServerPlayer p)ArchiveTransfer.panel(p);return InteractionResult.sidedSuccess(c.getLevel().isClientSide);}
        @Override public void appendHoverText(ItemStack stack,TooltipContext c,List<Component> tips,TooltipFlag flag){tips.add(Component.literal("右键：上传本地建筑 / 下载服务器建筑"));tips.add(Component.literal("联机需管理员权限；单人创造可用。"));}
    }
    public static final class Guide extends Item {
        Guide(Properties properties){super(properties);}
        @Override public InteractionResultHolder<ItemStack> use(Level level,net.minecraft.world.entity.player.Player player,InteractionHand hand){
            if(level.isClientSide)ArchiveNetwork.ClientBridge.help.run();
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
        }
        @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){return useOn(context);}
        @Override public InteractionResult useOn(UseOnContext context){if(context.getLevel().isClientSide)ArchiveNetwork.ClientBridge.help.run();return InteractionResult.sidedSuccess(context.getLevel().isClientSide);}
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag){
            tooltip.add(Component.literal("右键打开。跟着三步，把建筑保存下来再放回去。"));
            if(ArchiveNetwork.ClientBridge.shift.getAsBoolean()){
                tooltip.add(Component.literal("入门：怎么框选、保存、放置，遇到问题怎么办。"));
                tooltip.add(Component.literal("进阶：范围限制、覆盖与撤销、文件与方块统计。"));
                tooltip.add(Component.literal("看指南不需要管理员权限，也不会修改建筑。"));
            }else tooltip.add(Component.literal("按住 Shift 查看指南内容").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }
    public static final class Wand extends Item {
        Wand(Properties props){super(props);}
        @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){return useOn(context);}
        @Override public InteractionResult useOn(UseOnContext context) {
            if(context.getHand()!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
            if(context.getPlayer() instanceof ServerPlayer p) run(p.createCommandSourceStack(),player->{
                Session s=session(player);if(s.loaded!=null&&player.isShiftKeyDown()){clearLoaded(s);panel(player,"请选择要切换的建筑。",0);}else if(s.loaded!=null){preview(player,context.getClickedPos().relative(context.getClickedFace()));panel(player,"已预览；确认范围后再放置。",0);}
                else {point(player,context.getClickedPos(),player.isShiftKeyDown()||s.a!=null);if(s.a!=null&&s.b!=null)panel(player,"选区完成。取一个名字，保存建筑。",0);}
            });
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        @Override public InteractionResultHolder<ItemStack> use(Level level,net.minecraft.world.entity.player.Player player,InteractionHand hand) {
            if(hand!=InteractionHand.MAIN_HAND)return InteractionResultHolder.pass(player.getItemInHand(hand));
            if(player instanceof ServerPlayer p) run(p.createCommandSourceStack(),q->{
                if(session(q).loaded!=null&&q.isShiftKeyDown()){clearLoaded(session(q));panel(q,"请选择要切换的建筑。",0);}else if(session(q).loaded!=null&&session(q).anchor==null){try{preview(q,remoteTarget(q));panel(q,"远程投影已固定；还没有真正放置。",0);}catch(IllegalArgumentException ex){panel(q,ex.getMessage(),0);}}else panel(q,"",0);
            });
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
        }
        @Override public boolean doesSneakBypassUse(ItemStack stack,net.minecraft.world.level.LevelReader level,BlockPos pos,net.minecraft.world.entity.player.Player player){return true;}
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.building_archive.wand"));
            tooltip.add(Component.literal("不会用了？右键《建筑档案使用指南》。"));
            if(ArchiveNetwork.ClientBridge.shift.getAsBoolean()){
                tooltip.add(Component.literal("两角要包住整栋建筑；屋顶高度可在面板补填。"));
                tooltip.add(Component.literal("保存后从列表载入，右键地面预览，再确认放置。"));
                tooltip.add(Component.literal("更详细的限制与文件说明，见指南的「进阶说明」。"));
            }else tooltip.add(Component.literal("按住 Shift 查看更多操作提示").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }
}



