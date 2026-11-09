package com.muxigame.city;
import java.util.*;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.registries.*;

public final class RuralBlocks {
 public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(CityMod.ID);
 public static final DeferredBlock<WorkBlock> SIFTER=BLOCKS.register("seed_sifter",()->new WorkBlock("farmer"));
 public static final DeferredBlock<WorkBlock> STOVE=BLOCKS.register("rural_stove",()->new WorkBlock("cook"));
 public static final DeferredBlock<WorkBlock> DESK=BLOCKS.register("accounting_desk",()->new WorkBlock("merchant"));
 public static final DeferredBlock<WorkBlock> HUNTER=BLOCKS.register("hunter_table",()->new WorkBlock("hunter"));
 public static final DeferredBlock<WorkBlock> GUILD=BLOCKS.register("merchant_guild_desk",()->new WorkBlock("procurement"));
 public static final DeferredBlock<WorkBlock> OVEN=BLOCKS.register("baking_oven",()->new WorkBlock("baker"));
 public static final DeferredBlock<WorkBlock> SUPPLY=BLOCKS.register("consumer_workbench",()->new WorkBlock("supplier"));
 public static final DeferredBlock<WorkBlock> CHEST=BLOCKS.register("npc_storage",()->new WorkBlock("storage"));
 public static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,CityMod.ID);
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<Link>> LINK=TYPES.register("rural_link",()->BlockEntityType.Builder.of(Link::new,SIFTER.get(),STOVE.get(),DESK.get(),CHEST.get(),HUNTER.get(),GUILD.get(),OVEN.get(),SUPPLY.get()).build(null));
 public static final List<DeferredItem<BlockItem>> ITEMS=List.of(CityMod.ITEMS.registerSimpleBlockItem(SIFTER),CityMod.ITEMS.registerSimpleBlockItem(STOVE),CityMod.ITEMS.registerSimpleBlockItem(DESK),CityMod.ITEMS.registerSimpleBlockItem(CHEST),CityMod.ITEMS.registerSimpleBlockItem(HUNTER),CityMod.ITEMS.registerSimpleBlockItem(GUILD),CityMod.ITEMS.registerSimpleBlockItem(OVEN),CityMod.ITEMS.registerSimpleBlockItem(SUPPLY));
 public static final DeferredItem<Item> STORAGE_WAND=CityMod.ITEMS.register("npc_storage_wand",()->new Wand(false));
 public static final DeferredItem<Item> MARRIAGE_WAND=CityMod.ITEMS.register("marriage_wand",()->new Wand(true));
 public static final class WorkBlock extends BaseEntityBlock {
  final String kind;WorkBlock(String kind){super(BlockBehaviour.Properties.ofFullCopy(kind.equals("cook")?Blocks.STONE_BRICKS:Blocks.OAK_PLANKS).noOcclusion());this.kind=kind;registerDefaultState(stateDefinition.any().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,Direction.NORTH));}
  protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);}
  @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext c){return defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,c.getHorizontalDirection().getOpposite());}
  @Override protected BlockState rotate(BlockState s,Rotation r){var facing=net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;return s.setValue(facing,r.rotate(s.getValue(facing)));}
  @Override protected BlockState mirror(BlockState s,Mirror m){var facing=net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;return s.rotate(m.getRotation(s.getValue(facing)));}
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){if(!level.isClientSide||!kind.equals("storage"))return null;return createTickerHelper(type,LINK.get(),(l,p,s,b)->{b.previousLid=b.lid;b.lid=Math.clamp(b.lid+(b.viewers>0?.1f:-.1f),0f,1f);});}
  protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(p->new WorkBlock(kind));}
  public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new Link(p,s);}
  protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,net.minecraft.world.phys.shapes.CollisionContext c){return kind.equals("storage")?Block.box(1,0,1,15,14,15):super.getShape(s,l,p,c);}
  protected RenderShape getRenderShape(BlockState s){return kind.equals("storage")?RenderShape.ENTITYBLOCK_ANIMATED:RenderShape.MODEL;}
  protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos pos,Player player,BlockHitResult hit){if(player instanceof ServerPlayer p){if(kind.equals("storage")){try{NpcStorage.open(p,(Link)l.getBlockEntity(pos));}catch(IllegalArgumentException ex){RuralTools.say(p,ex.getMessage());}}else if(!CityTradeLab.block(p,pos,kind))RuralTools.block(p,pos);}return InteractionResult.SUCCESS;}
  protected ItemInteractionResult useItemOn(ItemStack stack,BlockState s,Level l,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){if(stack.getItem() instanceof Wand w){if(player instanceof ServerPlayer p)RuralTools.wandBlock(p,pos,w.marriage);return ItemInteractionResult.SUCCESS;}if(kind.equals("storage")){useWithoutItem(s,l,pos,player,hit);return ItemInteractionResult.SUCCESS;}if(player instanceof ServerPlayer p&&!CityTradeLab.block(p,pos,kind))RuralTools.block(p,pos);return ItemInteractionResult.SUCCESS;}
  protected void onRemove(BlockState old,Level l,BlockPos pos,BlockState next,boolean moving){if(old.getBlock()!=next.getBlock()&&l instanceof ServerLevel level)RuralTools.remove(level,pos);super.onRemove(old,l,pos,next,moving);}
 }
 /** Only relative configuration is serialized: no resident identity or economic assets in blueprints. */
 public static final class Link extends BlockEntity implements net.minecraft.world.Container,net.minecraft.world.MenuProvider {
  net.minecraft.core.NonNullList<ItemStack> inventory;int viewers;float lid,previousLid;long closedAt;
  public int getContainerSize(){return 27;}public boolean isEmpty(){return inventory==null||inventory.stream().allMatch(ItemStack::isEmpty);}public ItemStack getItem(int i){return inventory==null?ItemStack.EMPTY:inventory.get(i);}
  public ItemStack removeItem(int i,int count){if(inventory==null)return ItemStack.EMPTY;var result=net.minecraft.world.ContainerHelper.removeItem(inventory,i,count);setChanged();return result;}
  public ItemStack removeItemNoUpdate(int i){if(inventory==null)return ItemStack.EMPTY;var result=net.minecraft.world.ContainerHelper.takeItem(inventory,i);setChanged();return result;}
  public boolean canPlaceItem(int i,ItemStack stack){return inventory!=null&&stack.getCount()<=NpcStorage.limit(this,i,stack);}
  public void setItem(int i,ItemStack stack){if(inventory==null||stack.getCount()>NpcStorage.limit(this,i,stack))return;inventory.set(i,stack);setChanged();}
  public void setChanged(){super.setChanged();if(inventory!=null)NpcStorage.commit(this);}
  public boolean stillValid(Player p){return level!=null&&p.level()==level&&!isRemoved()&&p.distanceToSqr(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5)<=64;}
  public boolean triggerEvent(int id,int value){if(id==1){viewers=value;return true;}return super.triggerEvent(id,value);}
  public void startOpen(Player p){viewers++;if(level!=null&&!level.isClientSide){level.blockEvent(worldPosition,getBlockState().getBlock(),1,viewers);if(viewers==1)level.playSound(null,worldPosition,net.minecraft.sounds.SoundEvents.CHEST_OPEN,net.minecraft.sounds.SoundSource.BLOCKS,.5f,.95f);}}public void stopOpen(Player p){viewers=Math.max(0,viewers-1);closedAt=level==null?0:level.getGameTime();NpcStorage.commit(this);if(level!=null&&!level.isClientSide){level.blockEvent(worldPosition,getBlockState().getBlock(),1,viewers);if(viewers==0)level.playSound(null,worldPosition,net.minecraft.sounds.SoundEvents.CHEST_CLOSE,net.minecraft.sounds.SoundSource.BLOCKS,.5f,.95f);}}
  public net.minecraft.network.chat.Component getDisplayName(){return net.minecraft.network.chat.Component.literal("NPC储藏箱 · 粮食最多10份");}
  public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id,net.minecraft.world.entity.player.Inventory player,Player p){return NpcStorage.menu(id,player,this);}
  public BlockPos offset;public Link(BlockPos p,BlockState s){super(LINK.get(),p,s);}
  protected void saveAdditional(CompoundTag t,HolderLookup.Provider r){super.saveAdditional(t,r);if(offset!=null)t.putIntArray("RuralWorkOffset",new int[]{offset.getX(),offset.getY(),offset.getZ()});}
  protected void loadAdditional(CompoundTag t,HolderLookup.Provider r){super.loadAdditional(t,r);int[] a=t.getIntArray("RuralWorkOffset");offset=a.length==3&&Math.abs((long)a[0])<=32&&Math.abs((long)a[1])<=32&&Math.abs((long)a[2])<=32?new BlockPos(a[0],a[1],a[2]):null;}
  public void onLoad(){super.onLoad();if(level instanceof ServerLevel l)RuralTools.register(l,worldPosition,this);}
  public void clearContent(){NpcStorage.clear(this);}
 }
 public static final class Wand extends Item {
  final boolean marriage;Wand(boolean m){super(new Item.Properties().stacksTo(1));marriage=m;}
  public InteractionResult useOn(UseOnContext c){if(c.getPlayer() instanceof ServerPlayer p)RuralTools.wandBlock(p,c.getClickedPos(),marriage);return InteractionResult.SUCCESS;}
  public InteractionResultHolder<ItemStack> use(Level l,Player p,InteractionHand h){if(p instanceof ServerPlayer s)RuralTools.openSelected(s,marriage);return InteractionResultHolder.success(p.getItemInHand(h));}
 }
}


