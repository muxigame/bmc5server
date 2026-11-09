package com.muxigame.zaluokeforging.forging;

import com.muxigame.zaluokeforging.ModContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** All process history travels with the item, including placement, reheating and saves. */
public final class Workpiece {
    public static final int REQUIRED_HITS = 12;
    public static boolean axe(ItemStack s) { return s.is(ModContent.AXE_BLANK.get()) || s.is(ModContent.BRIGHT_AXE.get()) || s.is(ModContent.DAZZLING_AXE.get()) || s.is(ModContent.READY_AXE.get()) || s.is(ModContent.AXE_MOLD.get()) || s.is(ModContent.FILLED_AXE_MOLD.get()); }
    public static int requiredHits(ItemStack s) { return axe(s)?10:12; }
    public static int capacity(ItemStack s) { return axe(s)?6:10; }
    public static int regions(ItemStack s) { return axe(s)?2:4; }
    public static int damageLimit(ItemStack s) { return axe(s)?8:10; }
    public static boolean clamped(ItemStack s) { return get(s,"Tongs")==1; }
    public static int burnDamage(ItemStack s,long now) { return clamped(s)?0:thermalAt(s,now)>=400?4:thermalAt(s,now)>=200?2:0; }
    public static int get(ItemStack stack, String key) {
        CompoundTag data=stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!data.contains(key)) {
            if (key.equals("Iron") && (stack.is(ModContent.FILLED_MOLD.get()) || stack.is(ModContent.FILLED_AXE_MOLD.get()))) return capacity(stack);
            if (key.equals("Hits") && (stack.is(ModContent.READY_BLANK.get()) || stack.is(ModContent.READY_AXE.get()))) return requiredHits(stack);
        }
        return data.getInt(key);
    }
    public static ItemStack put(ItemStack stack, String key, int value) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.putInt(key, value);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }
    public static boolean blank(ItemStack stack) {
        return axe(stack) && !mold(stack) || stack.is(ModContent.SWORD_BLANK.get()) || stack.is(ModContent.BRIGHT_BLANK.get())
                || stack.is(ModContent.DAZZLING_BLANK.get()) || stack.is(ModContent.READY_BLANK.get());
    }
    private static CompoundTag tag(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag(); }
    public static double thermal(ItemStack stack) {
        var data=tag(stack);
        if(data.contains("Temperature")) return data.getDouble("Temperature");
        return (stack.is(ModContent.DAZZLING_BLANK.get()) || stack.is(ModContent.DAZZLING_AXE.get()))?400:(stack.is(ModContent.BRIGHT_BLANK.get()) || stack.is(ModContent.BRIGHT_AXE.get()))?200:get(stack,"Heat")*200;
    }
    public static double thermalAt(ItemStack stack,long now) {
        var data=tag(stack);
        return Math.max(0,thermal(stack)-(data.contains("ThermalAt")?Math.max(0,now-data.getLong("ThermalAt"))*.5:0));
    }
    public static int heat(ItemStack stack) { return thermal(stack)>=400?2:thermal(stack)>=200?1:0; }
    public static ItemStack temperature(ItemStack stack,int heat) { return thermal(stack,heat*200,Long.MIN_VALUE); }
    public static ItemStack thermal(ItemStack stack,double value,long now) {
        value=Math.clamp(value,0,400);int stage=value>=400?2:value>=200?1:0;
        ItemStack next=axe(stack)?new ItemStack(get(stack,"Hits")>=requiredHits(stack)?ModContent.READY_AXE.get():stage==2?ModContent.DAZZLING_AXE.get():stage==1?ModContent.BRIGHT_AXE.get():ModContent.AXE_BLANK.get()):new ItemStack(get(stack,"Hits")>=requiredHits(stack)?ModContent.READY_BLANK.get():stage==2?ModContent.DAZZLING_BLANK.get():stage==1?ModContent.BRIGHT_BLANK.get():ModContent.SWORD_BLANK.get());
        next.applyComponents(stack.getComponentsPatch());
        var data=tag(next);data.putDouble("Temperature",value);data.putInt("Heat",stage);
        if(now!=Long.MIN_VALUE) data.putLong("ThermalAt",now); else data.remove("ThermalAt");
        next.set(DataComponents.CUSTOM_DATA,CustomData.of(data));return next;
    }
    public static ItemStack settle(ItemStack stack,long now) { return put(put(thermal(stack,thermalAt(stack,now),now),"HeatingLast",0),"OverheatTicks",0); }
    public static int damage(ItemStack stack) { return tag(stack).contains("Damage")?get(stack,"Damage"):get(stack,"ColdHits"); }
    public static boolean damageOnce(ItemStack stack) { return get(put(stack,"Damage",damage(stack)+1),"Damage")>=damageLimit(stack); }
    public static void target(ItemStack stack,net.minecraft.util.RandomSource random) {
        int old=get(stack,"Target");put(stack,"Target",(old+1+random.nextInt(regions(stack)-1))%regions(stack));put(stack,"HasTarget",1);
    }
    public static int color(ItemStack stack,long now) {
        double t=thermalAt(stack,now);int from=t<200?0x80868B:0xFF8A22,to=t<200?0xFF8A22:0xFFF4CE;
        double f=(t<200?t:t-200)/200;int rgb=0xFF000000;
        for(int shift:new int[]{16,8,0}) rgb|=(int)(((from>>shift)&255)*(1-f)+((to>>shift)&255)*f)<<shift;
        return rgb;
    }
    public static boolean mold(ItemStack stack) {
        return stack.is(ModContent.LONGSWORD_MOLD.get()) || stack.is(ModContent.FILLED_MOLD.get()) || stack.is(ModContent.AXE_MOLD.get()) || stack.is(ModContent.FILLED_AXE_MOLD.get());
    }
    public static ItemStack filled(ItemStack stack, int amount) {
        ItemStack result = new ItemStack(axe(stack)?(amount>=6?ModContent.FILLED_AXE_MOLD.get():ModContent.AXE_MOLD.get()):(amount>=10?ModContent.FILLED_MOLD.get():ModContent.LONGSWORD_MOLD.get()));
        result.applyComponents(stack.getComponentsPatch());
        return put(result, "Iron", Math.clamp(amount, 0, capacity(stack)));
    }
    private Workpiece() { }
}
