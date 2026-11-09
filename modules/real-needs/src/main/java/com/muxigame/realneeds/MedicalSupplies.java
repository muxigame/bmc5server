package com.muxigame.realneeds;

import java.util.*;
import net.minecraft.world.item.*;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;

public final class MedicalSupplies {
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(RealNeeds.ID);
    private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,RealNeeds.ID);
    private static final Map<String,net.neoforged.neoforge.registries.DeferredItem<Item>> SUPPLIES=new LinkedHashMap<>();
    static {
        for(var type:AilmentType.values())SUPPLIES.computeIfAbsent(type.careItem,id->ITEMS.registerItem(id,Supply::new,new Item.Properties().stacksTo(16)));
        TABS.register("medical_supplies",()->CreativeModeTab.builder().title(Component.literal("真实需求 · 护理用品")).icon(()->new ItemStack(item("bandage"))).displayItems((p,out)->SUPPLIES.values().forEach(out::accept)).build());
    }
    public static Item item(String id){var item=SUPPLIES.get(id);if(item==null)throw new IllegalArgumentException("未知护理用品");return item.get();}
    public static void register(IEventBus bus){ITEMS.register(bus);TABS.register(bus);}
    public static final class Supply extends Item {
        public Supply(Properties properties){super(properties);}
        @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand){
            if(level.isClientSide)NeedsNetwork.ClientBridge.openHealth.run();
            return net.minecraft.world.InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
        }
        @Override public void appendHoverText(ItemStack stack,Item.TooltipContext context,List<Component> tooltip,TooltipFlag flag){tooltip.add(Component.literal("右键打开伤病与护理；选择部位和病情后消耗1份。"));}
    }
}
