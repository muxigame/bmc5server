package com.muxigame.city;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

/** A real 27-slot menu with a single economic stock and disposable live projection. */
public final class NpcStorage {
 static final Set<RuralBlocks.Link> live=new LinkedHashSet<>();
 static int food(ItemStack s){if(s.is(Items.WHEAT))return 0;if(s.is(Items.BREAD))return 1;for(int i=0;i<5;i++){if(s.is(RuralMeat.RAW[i]))return i+2;if(s.is(RuralMeat.COOKED[i]))return i+7;}return -1;}
 static Item item(int i){return i==0?Items.WHEAT:i==1?Items.BREAD:i<7?RuralMeat.RAW[i-2]:RuralMeat.COOKED[i-7];}
 static int[] counts(RuralRules.Stock s){int[] n=new int[12];n[0]=s.wheat;n[1]=s.bread;System.arraycopy(s.raw,0,n,2,5);System.arraycopy(s.cooked,0,n,7,5);return n;}
 static void counts(RuralRules.Stock s,int[] n){s.wheat=n[0];s.bread=n[1];System.arraycopy(n,2,s.raw,0,5);System.arraycopy(n,7,s.cooked,0,5);}
 static RuralSettlement.Box box(RuralBlocks.Link link){if(!(link.getLevel() instanceof ServerLevel l))return null;var c=RuralTools.city(l,link.getBlockPos());if(c==null)return null;return RuralSettlement.get(l.getServer()).state.boxes.get(RuralSettlement.key(c.id,link.getBlockPos().asLong()));}
 static int total(RuralBlocks.Link l){if(l.inventory==null)return 0;return l.inventory.stream().filter(s->food(s)>=0).mapToInt(ItemStack::getCount).sum();}
 static NonNullList<ItemStack> projected(RuralBlocks.Link link,RuralSettlement.Box b){return projected((ServerLevel)link.getLevel(),b);}
 static NonNullList<ItemStack> projected(ServerLevel level,RuralSettlement.Box b){var provider=level.registryAccess();var list=NonNullList.withSize(27,ItemStack.EMPTY);for(int i=0;i<Math.min(27,b.slots.size());i++)if(!b.slots.get(i).isEmpty())try{var parsed=ItemStack.parseOptional(provider,TagParser.parseTag(b.slots.get(i)));CityEconomy.require(!parsed.isEmpty(),"缺少物品或组件对应模组，原记录保留");list.set(i,parsed);}catch(Exception ex){throw new IllegalArgumentException("储藏箱物品记录损坏，保留数据等待处理",ex);}int[] want=counts(b.stock);for(int type=0;type<12;type++){int actual=0;for(var stack:list)if(food(stack)==type)actual+=stack.getCount();int remove=Math.max(0,actual-want[type]);for(var stack:list)if(food(stack)==type&&remove>0){int n=Math.min(remove,stack.getCount());stack.shrink(n);remove-=n;}int add=Math.max(0,want[type]-actual);for(int i=0;i<27&&add>0;i++){var s=list.get(i);if(s.isEmpty()){list.set(i,new ItemStack(item(type),add));add=0;}else if(s.is(item(type))&&CityEconomy.plain(s,s.getItem())){int n=Math.min(add,s.getMaxStackSize()-s.getCount());s.grow(n);add-=n;}}CityEconomy.require(add==0,"储藏箱实体槽位不足，库存保留");}return list;}
 static boolean begin(RuralBlocks.Link link){var b=box(link);if(b==null)return false;if(link.inventory==null){link.inventory=projected(link,b);live.add(link);}return true;}
 static boolean takeData(ServerLevel level,RuralSettlement.Box box,String item){var list=projected(level,box);var target=CityEconomy.item(item);for(var stack:list)if(CityEconomy.plain(stack,target)){stack.shrink(1);var totals=new int[12];var slots=new ArrayList<String>();int ordinary=0;for(var s:list){int f=food(s);if(f>=0)totals[f]+=s.getCount();else if(!s.isEmpty())ordinary++;slots.add(s.isEmpty()?"":s.save(level.registryAccess()).toString());}counts(box.stock,totals);box.stock.ordinarySlots=ordinary;box.slots=slots;RuralSettlement.get(level.getServer()).setDirty();var pos=BlockPos.of(box.pos);if(level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof RuralBlocks.Link link&&link.inventory!=null)link.inventory=projected(level,box);return true;}return false;}
 static void open(ServerPlayer p,RuralBlocks.Link link){var level=p.serverLevel();CityEconomy.require(!level.getBlockState(link.getBlockPos().above()).isRedstoneConductor(level,link.getBlockPos().above()),"箱子上方被遮挡");RuralTools.register(level,link.getBlockPos(),link);CityEconomy.require(begin(link),"请先将NPC储藏箱放入已登记聚落");p.openMenu(link);}
 static void commit(RuralBlocks.Link link){if(link.inventory==null||!(link.getLevel() instanceof ServerLevel l))return;var b=box(link);if(b==null)return;var totals=new int[12];var slots=new ArrayList<String>();int ordinary=0;for(var s:link.inventory){int f=food(s);if(f>=0)totals[f]+=s.getCount();else if(!s.isEmpty())ordinary++;slots.add(s.isEmpty()?"":s.save(l.registryAccess()).toString());}CityEconomy.require(Arrays.stream(totals).sum()<=10,"NPC箱子最多10份粮食");counts(b.stock,totals);b.stock.ordinarySlots=ordinary;b.slots=slots;RuralSettlement.get(l.getServer()).setDirty();}
 static void refresh(MinecraftServer s){for(var link:new ArrayList<>(live)){if(link.isRemoved()||link.getLevel()==null){live.remove(link);continue;}var b=box(link);if(b==null)continue;link.inventory=projected(link,b);}}
 static void virtualize(RuralBlocks.Link link){if(link.inventory==null)return;commit(link);link.inventory=null;live.remove(link);}
 static void tick(MinecraftServer s){if(s.getTickCount()%20!=0)return;for(var l:new ArrayList<>(live)){if(l.getLevel() instanceof ServerLevel level&&l.viewers==0){long now=level.getGameTime();boolean nearby=level.players().stream().anyMatch(p->p.chunkPosition().equals(new net.minecraft.world.level.ChunkPos(l.getBlockPos())));if(!nearby||now-l.closedAt>=120000)virtualize(l);}}}
 static int limit(RuralBlocks.Link l,int slot,ItemStack incoming){return food(incoming)<0?incoming.getMaxStackSize():Math.max(0,Math.min(incoming.getMaxStackSize(),10-total(l)+(l.inventory!=null&&food(l.inventory.get(slot))>=0?l.inventory.get(slot).getCount():0)));}
 static void clear(RuralBlocks.Link link){var b=box(link);if(b!=null){counts(b.stock,new int[12]);b.stock.ordinarySlots=0;b.slots.clear();if(link.getLevel() instanceof ServerLevel l)RuralSettlement.get(l.getServer()).setDirty();}if(link.inventory!=null)link.inventory.clear();link.inventory=null;live.remove(link);}
 static void drop(ServerLevel level,BlockPos pos,RuralBlocks.Link link,RuralSettlement.Box b){var inventory=link==null?null:projected(link,b);if(inventory==null){for(int i=0;i<12;i++)if(counts(b.stock)[i]>0)Block.popResource(level,pos,new ItemStack(item(i),counts(b.stock)[i]));}else{counts(b.stock,new int[12]);b.slots.clear();link.inventory=null;live.remove(link);for(var stack:inventory)if(!stack.isEmpty())Block.popResource(level,pos,stack);}counts(b.stock,new int[12]);b.slots.clear();}
 static ChestMenu menu(int id,Inventory player,RuralBlocks.Link link){if(!begin(link))return null;return new ChestMenu(MenuType.GENERIC_9x3,id,player,link,3){
  {for(int i=0;i<27;i++){var old=slots.get(i);final int slotNumber=i;var replacement=new Slot(link,i,old.x,old.y){public boolean mayPlace(ItemStack s){return limit(link,slotNumber,s)>0;}public int getMaxStackSize(ItemStack s){return limit(link,slotNumber,s);}};replacement.index=i;slots.set(i,replacement);}}
 };}
}
