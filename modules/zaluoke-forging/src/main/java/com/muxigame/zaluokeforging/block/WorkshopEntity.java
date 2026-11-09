package com.muxigame.zaluokeforging.block;

import com.muxigame.zaluokeforging.ModContent;
import com.muxigame.zaluokeforging.forging.Bellows;
import com.muxigame.zaluokeforging.forging.Workpiece;
import com.muxigame.zaluokeforging.menu.WorkshopMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class WorkshopEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    private NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
    public int liquid, cooling, pourTicks, overflowTicks, water, cracks;
    public double progress, fire, heating;
    public long boostUntil, lastHammer = -100, bellowsStart = -1000;
    public int reclaimedSlag;
    public final ContainerData data = new ContainerData() {
        public int get(int i) { return switch(i) {
            case 0 -> kind().equals("refining_furnace") ? 0 : 1;
            case 1 -> liquid;
            case 2 -> (int)(fire / 32);
            case 3 -> (int)(items.get(0).is(ModContent.SLAG.get()) ? progress*16 : progress);
            case 4 -> cooling;
            case 5 -> (int)heating;
            case 6 -> slag();
            case 7 -> level != null && (kind().equals("refining_furnace") ? Bellows.heat(level, worldPosition.below()) : fire > 0) ? 1 : 0;
            case 9 -> coolingDuration();
            case 10 -> reclaimedSlag;
            case 8 -> level != null && Bellows.active(level, kind().equals("refining_furnace") ? worldPosition.below() : worldPosition) ? 1 : 0;
            default -> 0;
        }; }
        public void set(int i, int value) { }
        public int getCount() { return 11; }
    };

    public WorkshopEntity(BlockPos pos, BlockState state) { super(ModContent.MACHINE.get(), pos, state); }
    public String kind() { return ((WorkshopBlock)getBlockState().getBlock()).design; }
    public ItemStack work() {
        if(kind().equals("blacksmith_hearth") && level!=null && level.getBlockEntity(worldPosition.above()) instanceof WorkshopEntity top && top.kind().equals("placed_blank")) return top.work();
        return items.get(2);
    }
    public void work(ItemStack stack) {
        if(kind().equals("blacksmith_hearth") && level!=null && level.getBlockEntity(worldPosition.above()) instanceof WorkshopEntity top && top.kind().equals("placed_blank")) { top.work(stack); if(stack.isEmpty()) level.removeBlock(top.getBlockPos(),false); }
        else items.set(2, stack);
        changed();
    }
    public int slag() { return items.get(1).getCount(); }
    public boolean blocked() { return slag() >= 64; }
    public void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    private void particles(net.minecraft.core.particles.SimpleParticleType particle, int count, double spread) {
        if (level instanceof ServerLevel server) server.sendParticles(particle, worldPosition.getX()+.5, worldPosition.getY()+1.05, worldPosition.getZ()+.5, count, spread, .12, spread, .03);
    }
    private void sound(net.minecraft.sounds.SoundEvent sound, float pitch) {
        level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, .6F, pitch);
    }
    public void drop(ItemStack stack) { if (!stack.isEmpty()) Block.popResource(level, worldPosition.above(), stack); }
    private void give(Player player, ItemStack stack) { if (!player.addItem(stack)) player.drop(stack, false); }
    private void addSlag() {
        if (slag() < 64) items.set(1, new ItemStack(ModContent.SLAG.get(), slag()+1));
        else drop(new ItemStack(ModContent.SLAG.get()));
    }

    public static void tick(Level level, BlockPos pos, BlockState state, WorkshopEntity be) {
        if (!level.isClientSide) be.serverTick();
    }
    public void serverTick() {
        switch(kind()) {
            case "refining_furnace" -> refine();
            case "blacksmith_hearth" -> heatBillet();
            case "blacksmith_anvil" -> { if(Workpiece.blank(work())) { work(Workpiece.settle(work(),level.getGameTime())); updateTarget(); } }
            case "placed_blank" -> { if(Workpiece.blank(work()) && !(level.getBlockEntity(worldPosition.below()) instanceof WorkshopEntity hearth && hearth.kind().equals("blacksmith_hearth") && hearth.fire>0)) work(Workpiece.settle(work(),level.getGameTime())); }
            case "mold_stand" -> { if (Workpiece.get(work(), "Iron") >= Workpiece.capacity(work()) && level.getGameTime()%5 == 0) particles(ParticleTypes.LARGE_SMOKE, 3, .12); }
            default -> { }
        }
        if (!kind().equals("refining_furnace") && !kind().equals("blacksmith_hearth")) return;
        // Store exact fractional progress on every tick, but avoid a block update packet each tick.
        setChanged();
        if (level.getGameTime()%10 == 0) changed();
    }
    public int coolingDuration() { return level != null && !level.isEmptyBlock(worldPosition.above()) ? 471 : 400; }
    private void refine() {
        boolean hot = Bellows.heat(level, worldPosition.below());
        if (hot) cooling = 0;
        else if (liquid > 0 && ++cooling >= coolingDuration()) { liquid--; cooling=0; for(int n=0;n<3;n++) addSlag(); sound(com.muxigame.zaluokeforging.forging.WorkshopSounds.COOL.get(),1); }
        else if (liquid == 0) cooling = 0;
        ItemStack input = items.get(0);
        boolean slagInput=input.is(ModContent.SLAG.get());
        boolean validInput=slagInput || input.is(Items.RAW_IRON);
        double required=slagInput ? 12.5 : 200;
        if (hot && !blocked() && liquid < 192 && validInput && !input.isEmpty()) {
            progress = Math.round((progress + 1.5 * (1 - .1 * (slag()/8)) * (Bellows.active(level, worldPosition.below()) ? 1.6 : 1))*100)/100.0;
            if (progress >= required) {
                progress -= required; input.shrink(1);
                if(slagInput) { if(++reclaimedSlag>=16) { reclaimedSlag=0; liquid++; } }
                else liquid++;
            }
        }
        if (!validInput || input.isEmpty()) progress = 0;
        Direction facing = getBlockState().getValue(WorkshopBlock.FACING);
        BlockPos standPos = worldPosition.below().relative(facing);
        WorkshopEntity stand = level.getBlockEntity(standPos) instanceof WorkshopEntity other && other.kind().equals("mold_stand") ? other : null;
        if (stand == null || !Workpiece.mold(stand.work())) { pourTicks=0; overflowTicks=0; return; }
        if (blocked()) { pourTicks=0; overflowTicks=0; return; }
        int amount = Workpiece.get(stand.work(), "Iron");
        if (amount < Workpiece.capacity(stand.work())) {
            overflowTicks=0;
            if (liquid > 0 && ++pourTicks >= 20) {
                pourTicks=0; liquid--;
                stand.work(Workpiece.filled(stand.work(), amount+1));
                sound(com.muxigame.zaluokeforging.forging.WorkshopSounds.POUR.get(), 1);
            } else if (liquid == 0) pourTicks=0;
        } else {
            pourTicks=0;
            // 100 ticks grace, then one liquid unit lost per 20 ticks. Taking the mold cancels this immediately.
            if (++overflowTicks > 100 && (overflowTicks-100)%20 == 0 && liquid > 0) {
                liquid--; drop(new ItemStack(ModContent.SLAG.get()));
            }
        }
    }
    public long targetStart = -1, finishedAt = -1000;
    public boolean targetConsumed;
    public void updateTarget() {
        if (!Workpiece.blank(work()) || Workpiece.get(work(), "Hits") >= Workpiece.requiredHits(work())) return;
        long now = level.getGameTime();
        if (targetStart < 0 || now - targetStart >= 40) {
            targetStart = now;
            targetConsumed = false;
            Workpiece.target(work(), level.random);
            changed();
        }
    }
    public boolean targetActive(long now) {
        long age = now - targetStart;
        return targetStart >= 0 && age >= 1 && age < 20 && !targetConsumed
                && Workpiece.get(work(), "HasTarget") == 1 && Workpiece.get(work(), "Hits") < Workpiece.requiredHits(work());
    }
    private void heatBillet() {
        if(!items.get(2).isEmpty() && level.isEmptyBlock(worldPosition.above())) {
            ItemStack legacy=items.get(2); items.set(2,ItemStack.EMPTY);
            level.setBlock(worldPosition.above(),ModContent.PLACED_BLANK.get().defaultBlockState().setValue(WorkshopBlock.FACING,getBlockState().getValue(WorkshopBlock.FACING)),3);
            ((WorkshopEntity)level.getBlockEntity(worldPosition.above())).work(legacy);
        }
        if(work().isEmpty()) heating=0;
        ItemStack fuel = items.get(0);
        if ((fuel.is(Items.COAL) || fuel.is(Items.CHARCOAL)) && fire <= 199*3200) { fuel.shrink(1); fire += 3200; }
        if (fire <= 0) { updateHearthLight(); return; }
        if(level.getGameTime()%80==0) sound(com.muxigame.zaluokeforging.forging.WorkshopSounds.FIRE.get(),1);
        boolean boosted = boostUntil > level.getGameTime();
        fire = Math.max(0, fire - (boosted ? 1.5 : 1));
        updateHearthLight();
        if (level.getGameTime()%10 == 0) particles(ParticleTypes.FLAME, 2, .2);
        if (!level.getBlockState(worldPosition.above()).is(ModContent.PLACED_BLANK.get()) || !Workpiece.blank(work())) return;
        long now=level.getGameTime();
        double before=Workpiece.thermalAt(work(),now);
        // This tick belongs to heating, not passive cooling. Undo this tick's half-unit when the hearth was already heating it.
        if(Workpiece.get(work(),"HeatingLast")==1) before=Workpiece.thermal(work());
        heating=Math.min(400,before+(boosted?1.6:1));
        ItemStack next=Workpiece.thermal(work(),heating,now);
        int excess=before>=400?Workpiece.get(next,"OverheatTicks")+1:0;
        Workpiece.put(next,"OverheatTicks",excess); Workpiece.put(next,"HeatingLast",1);
        if(excess>60 && (excess-60)%20==0 && Workpiece.damageOnce(next)) {
            scrapWork();heating=0;sound(SoundEvents.FIRE_EXTINGUISH,.7F);return;
        }
        if(excess>=60 && level.getGameTime()%5==0) particles(ParticleTypes.LARGE_SMOKE,3,.15);
        work(next);
    }
    private void updateHearthLight() {
        boolean lit = fire > 0;
        if (getBlockState().getValue(WorkshopBlock.LIT) != lit)
            level.setBlock(worldPosition, getBlockState().setValue(WorkshopBlock.LIT, lit), 3);
    }
    private void scrapWork() {
        boolean tongs=Workpiece.clamped(work());work(ItemStack.EMPTY);
        drop(new ItemStack(ModContent.SLAG.get(),24));if(tongs) drop(new ItemStack(ModContent.TONGS.get()));
    }
    public boolean hammer(Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (!kind().equals("blacksmith_anvil") || !Workpiece.blank(work()) || Workpiece.get(work(),"Hits")>=Workpiece.requiredHits(work())) return false;
        if(level.getGameTime()-lastHammer<5) return true;
        lastHammer=level.getGameTime();work(Workpiece.settle(work(),level.getGameTime()));
        updateTarget();
        int zone=hit==null?-1:com.muxigame.zaluokeforging.forging.ForgeTarget.zone(work(),worldPosition,getBlockState().getValue(WorkshopBlock.FACING),hit.getLocation(),hit.getDirection());
        if(!targetActive(level.getGameTime()) || Workpiece.heat(work())==0 || zone!=Workpiece.get(work(),"Target")) {
            particles(ParticleTypes.SMOKE,8,.16);
            if(Workpiece.damageOnce(work())) { scrapWork(); }
            sound(SoundEvents.ANVIL_LAND,.65F);
        } else {
            Workpiece.put(work(),"Hits",Workpiece.get(work(),"Hits")+1);
            targetConsumed = true;
            work(Workpiece.thermal(work(),Workpiece.thermal(work()),level.getGameTime()));
            if(level instanceof ServerLevel server) server.sendParticles(ModContent.FORGE_SPARK.get(),hit.getLocation().x,hit.getLocation().y+.015,hit.getLocation().z,8,.025,.015,.025,.025);
            sound(SoundEvents.ANVIL_LAND,1.3F);
            if (Workpiece.get(work(), "Hits") >= Workpiece.requiredHits(work())) {
                finishedAt = level.getGameTime();
                level.playSound(null, worldPosition, com.muxigame.zaluokeforging.forging.WorkshopSounds.FORGE_COMPLETE.get(), net.minecraft.sounds.SoundSource.BLOCKS, .3F, 1F);
            }
        }
        changed();return true;
    }
    public void crack(Player player,net.minecraft.world.InteractionHand hand) {
        ItemStack tool=player.getItemInHand(hand);
        if (!kind().equals("placed_mold") || work().isEmpty() || !tool.is(ModContent.HAMMER.get())) return;
        if (level.getGameTime()-lastHammer < 5) return;
        lastHammer=level.getGameTime();
        cracks++;
        sound(SoundEvents.STONE_BREAK, 1.1F);
        if (cracks >= 3) {
            boolean axe=Workpiece.axe(work());
            work(ItemStack.EMPTY);
            drop(new ItemStack(axe?ModContent.AXE_BLANK.get():ModContent.SWORD_BLANK.get()));
            drop(new ItemStack(axe?ModContent.AXE_MOLD.get():ModContent.LONGSWORD_MOLD.get()));
            level.removeBlock(worldPosition,false);
        } else changed();
    }
    /** Returns true when the held item was handled; empty hand opens GUIs or retrieves work. */
    public boolean interact(Player player, net.minecraft.world.InteractionHand hand) { return interact(player,hand,null); }
    public boolean interact(Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        ItemStack held=player.getItemInHand(hand);
        String kind=kind();
        if(Workpiece.blank(held)) { held=Workpiece.settle(held,level.getGameTime());player.setItemInHand(hand,held); }
        if(kind.equals("placed_mold") && held.is(ModContent.HAMMER.get())) { crack(player,hand);return true; }
        if (kind.equals("bellows_support")) {
            if(level.getGameTime()-bellowsStart<60) return true;
            bellowsStart=level.getGameTime(); changed();
            boolean result=Bellows.boost(level,worldPosition.relative(getBlockState().getValue(WorkshopBlock.FACING)));
            sound(com.muxigame.zaluokeforging.forging.WorkshopSounds.BELLOWS.get(),1);
            if (result) particles(ParticleTypes.SMOKE,2,.06);
            return true;
        }
        if (kind.equals("quenching_barrel")) {
            if (held.is(Items.WATER_BUCKET) && water<3) { water=3; exchange(player,hand,new ItemStack(Items.BUCKET)); sound(SoundEvents.BUCKET_EMPTY,1); changed(); return true; }
            boolean bottle=held.is(Items.POTION) && held.getOrDefault(DataComponents.POTION_CONTENTS,PotionContents.EMPTY).is(Potions.WATER);
            if (bottle && water<3) { water++; exchange(player,hand,new ItemStack(Items.GLASS_BOTTLE)); sound(SoundEvents.BOTTLE_EMPTY,1); changed(); return true; }
            if (held.is(Items.GLASS_BOTTLE) && water>0) { water--; exchange(player,hand,PotionContents.createItemStack(Items.POTION,Potions.WATER)); sound(SoundEvents.BOTTLE_FILL,1); changed(); return true; }
            if (held.is(Items.BUCKET) && water==3) { water=0; exchange(player,hand,new ItemStack(Items.WATER_BUCKET)); sound(SoundEvents.BUCKET_FILL,1); changed(); return true; }
            if (Workpiece.blank(held) && Workpiece.get(held,"Hits")>=Workpiece.requiredHits(held) && Workpiece.heat(held)>0 && water>0) {
                water--; boolean axe=Workpiece.axe(held), tongs=Workpiece.clamped(held); held.shrink(1); give(player,new ItemStack(axe?ModContent.FORGED_AXE.get():ModContent.FORGED_SWORD.get())); if(tongs) give(player,new ItemStack(ModContent.TONGS.get()));
                particles(ParticleTypes.CLOUD,80,.45); sound(SoundEvents.FIRE_EXTINGUISH,.8F); changed(); return true;
            }
            player.displayClientMessage(Component.translatable("message.zaluoke_forging.water",water),true);
            return true;
        }
        if (kind.equals("refining_furnace") && held.getItem() instanceof ShovelItem) {
            int amount=Math.min(10,slag());
            if (amount>0) { items.get(1).shrink(amount); give(player,new ItemStack(ModContent.SLAG.get(),amount)); changed(); sound(com.muxigame.zaluokeforging.forging.WorkshopSounds.CLEAR.get(),1); }
            return true;
        }
        if ((kind.equals("blacksmith_hearth") || kind.equals("blacksmith_anvil")) && Workpiece.blank(held)) {
            if (work().isEmpty() && (kind.equals("blacksmith_anvil") || level.isEmptyBlock(worldPosition.above()))) {
                if(kind.equals("blacksmith_hearth")) level.setBlock(worldPosition.above(),ModContent.PLACED_BLANK.get().defaultBlockState().setValue(WorkshopBlock.FACING,getBlockState().getValue(WorkshopBlock.FACING)),3);
                work(held.copyWithCount(1)); held.shrink(1);
                sound(com.muxigame.zaluokeforging.forging.WorkshopSounds.PLACE.get(),1);
                heating=Workpiece.thermal(work());
                if(kind.equals("blacksmith_anvil")) { targetStart=-1; targetConsumed=false; finishedAt=-1000; updateTarget(); }
                changed();
            }
            return true;
        }
        if (kind.equals("blacksmith_anvil") && held.is(ModContent.HAMMER.get())) { hammer(player,hit); return true; }
        if (kind.equals("mold_stand") && !work().isEmpty()
                && (work().is(ModContent.FILLED_MOLD.get()) || Workpiece.get(work(), "Iron") >= Workpiece.capacity(work()))) {
            ItemStack filled = work().copy();
            work(ItemStack.EMPTY);
            give(player, filled);
            sound(com.muxigame.zaluokeforging.forging.WorkshopSounds.TAKE.get(), 1);
            return true;
        }
        if (kind.equals("mold_stand") && Workpiece.mold(held)) {
            if (work().isEmpty()) { work(held.copyWithCount(1)); held.shrink(1); sound(com.muxigame.zaluokeforging.forging.WorkshopSounds.PLACE.get(),1); }
            return true;
        }
        if ((held.isEmpty() || held.is(ModContent.TONGS.get())) && !work().isEmpty() && !kind.equals("blacksmith_hearth")) {
            if(Workpiece.blank(work()) && !Workpiece.clamped(work())) {
                ItemStack tongs=held.is(ModContent.TONGS.get())?held:player.getOffhandItem();
                if(tongs.is(ModContent.TONGS.get())) { tongs.shrink(1);Workpiece.put(work(),"Tongs",1); }
            }
            if(Workpiece.blank(work())) { work(Workpiece.settle(work(),level.getGameTime())); Workpiece.put(work(),"OverheatTicks",0); Workpiece.put(work(),"HeatingLast",0); }
            give(player,work()); work(ItemStack.EMPTY); heating=0; cracks=0; sound(com.muxigame.zaluokeforging.forging.WorkshopSounds.TAKE.get(),1);
            if (kind.equals("placed_blank") || kind.equals("placed_mold")) level.removeBlock(worldPosition,false);
            return true;
        }
        if (kind.equals("refining_furnace") || kind.equals("blacksmith_hearth")) {
            player.openMenu(this); return true;
        }
        return kind.equals("mold_stand") || kind.equals("blacksmith_anvil");
    }
    private void exchange(Player player, net.minecraft.world.InteractionHand hand, ItemStack remainder) {
        if (player.isCreative()) return;
        ItemStack held=player.getItemInHand(hand); held.shrink(1);
        if (held.isEmpty()) player.setItemInHand(hand,remainder); else give(player,remainder);
    }
    public void dropContents() {
        if(reclaimedSlag>0) { drop(new ItemStack(ModContent.SLAG.get(),reclaimedSlag)); reclaimedSlag=0; }
        for(ItemStack stack:items) drop(stack);
        items=NonNullList.withSize(3,ItemStack.EMPTY);
        // Breaking the vessel solidifies its remaining contents, so moving it cannot erase clogging losses.
        while(liquid>0) { int count=Math.min(64,liquid); drop(new ItemStack(ModContent.SLAG.get(),count)); liquid-=count; }
    }
    @Override protected Component getDefaultName() { return getBlockState().getBlock().getName(); }
    @Override public int getContainerSize() { return 3; }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items=items; }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) { return new WorkshopMenu(id,inventory,this,data); }
    @Override public void setItem(int slot, ItemStack stack) {
        if(slot==0 && !ItemStack.isSameItemSameComponents(items.get(0),stack)) progress=0;
        super.setItem(slot,stack);
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot==0 && (kind().equals("refining_furnace") ? stack.is(Items.RAW_IRON)||stack.is(ModContent.SLAG.get())
                : kind().equals("blacksmith_hearth") && (stack.is(Items.COAL)||stack.is(Items.CHARCOAL)));
    }
    @Override public int[] getSlotsForFace(Direction direction) { return new int[]{0}; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction direction) { return canPlaceItem(slot,stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) { return slot==0; }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries); ContainerHelper.saveAllItems(tag,items,registries);
        tag.putLong("TargetStart",targetStart); tag.putBoolean("TargetConsumed",targetConsumed); tag.putLong("FinishedAt",finishedAt);
        tag.putInt("ReclaimedSlag",reclaimedSlag);
        tag.putInt("Liquid",liquid); tag.putInt("Cooling",cooling); tag.putInt("Pour",pourTicks); tag.putInt("Overflow",overflowTicks);
        tag.putInt("Water",water); tag.putInt("Cracks",cracks); tag.putDouble("Progress",progress); tag.putDouble("Fire",fire);
        tag.putDouble("Heating",heating); tag.putLong("BoostUntil",boostUntil); tag.putLong("LastHammer",lastHammer); tag.putLong("BellowsStart",bellowsStart);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries); items=NonNullList.withSize(3,ItemStack.EMPTY); ContainerHelper.loadAllItems(tag,items,registries);
        targetStart=tag.contains("TargetStart")?tag.getLong("TargetStart"):-1; targetConsumed=tag.getBoolean("TargetConsumed"); finishedAt=tag.contains("FinishedAt")?tag.getLong("FinishedAt"):-1000;
        reclaimedSlag=Math.clamp(tag.getInt("ReclaimedSlag"),0,15);
        liquid=Math.clamp(tag.getInt("Liquid"),0,192); cooling=tag.getInt("Cooling"); pourTicks=tag.getInt("Pour"); overflowTicks=tag.getInt("Overflow");
        water=Math.clamp(tag.getInt("Water"),0,3); cracks=tag.getInt("Cracks"); progress=tag.getDouble("Progress"); fire=Math.clamp(tag.getDouble("Fire"),0,640000);
        bellowsStart=tag.contains("BellowsStart")?tag.getLong("BellowsStart"):-1000;
        heating=tag.getDouble("Heating"); boostUntil=tag.getLong("BoostUntil"); lastHammer=tag.contains("LastHammer")?tag.getLong("LastHammer"):-100;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
