package com.muxigame.buildingarchive;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Separate terrain selection and undo; never touches the archive selection or saved records. */
public final class TerrainTool extends Item {
    static final Map<UUID,State> STATES=new HashMap<>();
    static final class State {
        String dimension="",mode="replace",source="minecraft:stone",target="minecraft:dirt";
        BlockPos a,b;long revision;List<Change> pending,undo;long expires;
    }
    record Change(BlockPos pos,BlockState before,BlockState after) {}
    public TerrainTool(){super(new Properties().stacksTo(1));}
    static boolean allowed(ServerPlayer p){return p.isCreative()||p.hasPermissions(2);}
    static State state(ServerPlayer p){
        State s=STATES.computeIfAbsent(p.getUUID(),id->new State());String dim=p.level().dimension().location().toString();
        if(!dim.equals(s.dimension)){s.dimension=dim;s.a=null;s.b=null;s.pending=null;s.undo=null;s.revision++;}return s;
    }
    static Block block(String id){
        var key=ResourceLocation.tryParse(id);if(key==null||!BuiltInRegistries.BLOCK.containsKey(key))throw new IllegalArgumentException("没有找到这个方块。");
        return BuiltInRegistries.BLOCK.get(key);
    }
    static boolean usableTarget(Block b){return !(b instanceof DoorBlock)&&!(b instanceof DoublePlantBlock)&&b.asItem()!=Items.AIR&&!b.defaultBlockState().hasBlockEntity()&&b.defaultBlockState().getFluidState().isEmpty();}
    static boolean clutter(BlockState s){return s.getFluidState().isEmpty()&&(s.getBlock() instanceof BushBlock||s.getBlock() instanceof VineBlock||s.getBlock() instanceof SnowLayerBlock||s.is(Blocks.GLOW_LICHEN)||s.is(Blocks.HANGING_ROOTS)||s.is(Blocks.CAVE_VINES)||s.is(Blocks.CAVE_VINES_PLANT));}
    static void select(ServerPlayer p,BlockPos pos){
        State s=state(p);s.pending=null;s.revision++;
        if(s.a==null||s.b!=null){s.a=pos.immutable();s.b=null;}else s.b=pos.immutable();
    }
    static List<Change> plan(ServerPlayer p){
        State s=state(p);if(s.a==null||s.b==null)throw new IllegalArgumentException("请先右键选择两个对角，或补填上下高度。");
        var bounds=ArchiveData.bounds(s.a,s.b);ArchiveData.checkArea(p.serverLevel(),bounds.origin(),bounds.size());
        Block from=block(s.source),to=block(s.target);
        if(!s.mode.equals("clean")&&!usableTarget(to))throw new IllegalArgumentException("目标需为非容器、非流体的实体方块物品。");
        List<Change> changes=new ArrayList<>();
        for(BlockPos pos:BlockPos.betweenClosed(bounds.origin(),bounds.origin().offset(bounds.size().getX()-1,bounds.size().getY()-1,bounds.size().getZ()-1))){
            BlockState old=p.serverLevel().getBlockState(pos);if(old.hasBlockEntity()||p.serverLevel().getBlockEntity(pos)!=null)continue;
            boolean match=switch(s.mode){case "replace"->old.is(from);case "fill"->old.isAir();case "clean"->clutter(old);default->throw new IllegalArgumentException("未知地形操作。");};
            BlockState next=s.mode.equals("clean")?Blocks.AIR.defaultBlockState():to.defaultBlockState();
            if(match&&!old.equals(next))changes.add(new Change(pos.immutable(),old,next));
        }return changes;
    }
    static void apply(ServerPlayer p) throws Exception {
        State s=state(p);if(s.pending==null||p.level().getGameTime()>s.expires)throw new IllegalArgumentException("请重新点击预览，确认有效期为30秒。");
        var bounds=ArchiveData.bounds(s.a,s.b);ArchiveData.checkArea(p.serverLevel(),bounds.origin(),bounds.size());
        for(Change c:s.pending)if(!p.serverLevel().getBlockState(c.pos).equals(c.before)||p.serverLevel().getBlockEntity(c.pos)!=null){s.pending=null;s.revision++;throw new IllegalArgumentException("待修改方块已变化，请重新预览。");}
        if(s.pending.isEmpty())throw new IllegalArgumentException("范围内没有需要修改的方块。");
        var backup=ArchiveData.capture(p.serverLevel(),bounds.origin(),bounds.size());
        var folder=p.server.getWorldPath(LevelResource.ROOT).resolve("building_archive/backups");
        ArchiveData.writeNew(ArchiveData.file(folder,"terrain_"+p.getUUID()+"_"+System.currentTimeMillis()),backup);
        s.undo=new ArrayList<>();var changes=s.pending;s.pending=null;s.revision++;
        for(Change c:changes){s.undo.add(c);p.serverLevel().setBlock(c.pos,c.after,2|16);}
    }
    static int undo(ServerPlayer p){
        State s=state(p);if(s.undo==null)throw new IllegalArgumentException("没有可撤销的地形操作。");
        // Validate every chunk first. Restore only positions that still match this tool's output.
        for(Change c:s.undo)ArchiveData.checkArea(p.serverLevel(),c.pos,new Vec3i(1,1,1));
        int skipped=0;
        for(Change c:s.undo){if(p.serverLevel().getBlockState(c.pos).equals(c.after)&&p.serverLevel().getBlockEntity(c.pos)==null)p.serverLevel().setBlock(c.pos,c.before,2|16);else skipped++;}
        s.undo=null;s.pending=null;s.revision++;return skipped;
    }
    static CompoundTag snapshot(ServerPlayer p,String message){
        State s=state(p);CompoundTag t=new CompoundTag();t.putBoolean("Terrain",true);t.putLong("Revision",s.revision);t.putString("Mode",s.mode);t.putString("Source",s.source);t.putString("Target",s.target);t.putString("Message",message);
        t.putString("A",s.a==null?"未选择":s.a.toShortString());t.putString("B",s.b==null?"未选择":s.b.toShortString());t.putBoolean("Selected",s.a!=null&&s.b!=null);t.putBoolean("Undo",s.undo!=null);t.putBoolean("Ready",s.pending!=null);t.putInt("Count",s.pending==null?0:s.pending.size());
        if(s.a!=null&&s.b!=null){try{var b=ArchiveData.bounds(s.a,s.b);t.putString("Size",b.size().toShortString());t.putInt("Low",b.origin().getY());t.putInt("High",b.origin().getY()+b.size().getY()-1);}catch(IllegalArgumentException e){t.putString("Message",e.getMessage());}}
        return t;
    }
    static void panel(ServerPlayer p,String message){PacketDistributor.sendToPlayer(p,new ArchiveNetwork.Snapshot(snapshot(p,message)));}
    static void action(ServerPlayer p,ArchiveNetwork.Request r){var result=handle(p,r);if(result!=null)PacketDistributor.sendToPlayer(p,new ArchiveNetwork.Snapshot(result));}
    static CompoundTag handle(ServerPlayer p,ArchiveNetwork.Request r){
        if(!allowed(p))return null;String message="";
        try{State s=state(p);if(r.revision()!=s.revision)throw new IllegalArgumentException("面板已过期，请重新打开工具。");
            switch(r.op()){
                case "terrain_select"->{s.a=null;s.b=null;s.pending=null;s.revision++;message="返回现场，右键两次选择长方体的两个对角。";}
                case "terrain_mode"->{if(!List.of("replace","fill","clean").contains(r.value()))throw new IllegalArgumentException("未知模式。");s.mode=r.value();s.pending=null;s.revision++;}
                case "terrain_source"->{block(r.value());s.source=r.value();s.pending=null;s.revision++;}
                case "terrain_target"->{if(!usableTarget(block(r.value())))throw new IllegalArgumentException("目标不能是容器或流体。");s.target=r.value();s.pending=null;s.revision++;}
                case "terrain_height"->{if(s.a==null||s.b==null)throw new IllegalArgumentException("先选两个角。");var v=r.value().split(",");if(v.length!=2)throw new IllegalArgumentException("高度格式错误。");int low=Integer.parseInt(v[0]),high=Integer.parseInt(v[1]);if(low>high)throw new IllegalArgumentException("最低高度不能大于最高高度。");var a=new BlockPos(s.a.getX(),low,s.a.getZ());var b=new BlockPos(s.b.getX(),high,s.b.getZ());var bounds=ArchiveData.bounds(a,b);ArchiveData.checkArea(p.serverLevel(),bounds.origin(),bounds.size());s.a=a;s.b=b;s.pending=null;s.revision++;}
                case "terrain_preview"->{s.pending=plan(p);s.expires=p.level().getGameTime()+600;s.revision++;outline(p);message="预计修改 "+s.pending.size()+" 格。核对边框后，在30秒内确认执行。";}
                case "terrain_apply"->{int count=s.pending==null?0:s.pending.size();apply(p);message="已修改 "+count+" 格，已保存备份，可以撤销。";}
                case "terrain_undo"->{message="已撤销，跳过 "+undo(p)+" 格后续被修改的位置。";}
                default->throw new IllegalArgumentException("未知地形操作。");
            }
        }catch(Exception e){message=e instanceof NumberFormatException?"高度必须为整数。":String.valueOf(e.getMessage());}
        return snapshot(p,message);
    }
    static void outline(ServerPlayer p){State s=state(p);if(s.a!=null&&s.b!=null){var b=ArchiveData.bounds(s.a,s.b);BuildingArchive.outline(p,b.origin(),b.size());}}
    static void guide(ServerPlayer p){if(!allowed(p)||!p.getMainHandItem().is(BuildingArchive.TERRAIN.get()))return;var s=state(p);p.displayClientMessage(Component.literal(s.b==null?"地形改造：右键选两角 · 潜行右键空气打开面板":"地形范围已选 · 右键空气打开面板 · 再点方块重新选区"),true);try{outline(p);}catch(IllegalArgumentException ignored){}}
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){return useOn(context);}
    @Override public InteractionResult useOn(UseOnContext c){if(c.getHand()!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;if(c.getPlayer() instanceof ServerPlayer p&&allowed(p)){select(p,c.getClickedPos());if(state(p).b!=null)panel(p,"选区完成，请选择操作。");}return InteractionResult.sidedSuccess(c.getLevel().isClientSide);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,net.minecraft.world.entity.player.Player player,InteractionHand hand){if(hand!=InteractionHand.MAIN_HAND)return InteractionResultHolder.pass(player.getItemInHand(hand));if(player instanceof ServerPlayer p&&allowed(p))panel(p,"");return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);}
    @Override public boolean doesSneakBypassUse(ItemStack stack,net.minecraft.world.level.LevelReader level,BlockPos pos,net.minecraft.world.entity.player.Player player){return true;}
    @Override public void appendHoverText(ItemStack stack,TooltipContext c,List<Component> text,TooltipFlag flag){text.add(Component.literal("右键选两角 · 右键空气打开面板"));text.add(Component.literal("替换方块 / 清理植物和薄雪 / 填补空气"));text.add(Component.literal("仅创造或管理员使用；容器自动跳过。"));}
}



