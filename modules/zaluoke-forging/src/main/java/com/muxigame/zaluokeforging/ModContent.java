package com.muxigame.zaluokeforging;

import com.muxigame.zaluokeforging.block.WorkshopBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.flag.FeatureFlags;
import com.muxigame.zaluokeforging.block.WorkshopEntity;
import com.muxigame.zaluokeforging.menu.WorkshopMenu;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registries for the steel bastard sword and battleaxe production chains. */
public final class ModContent {
    public static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, ZaluokeForging.MOD_ID);
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, net.minecraft.core.particles.SimpleParticleType> FORGE_SPARK = PARTICLES.register("forge_spark", () -> new net.minecraft.core.particles.SimpleParticleType(false));
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ZaluokeForging.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ZaluokeForging.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ZaluokeForging.MOD_ID);

    public static final DeferredBlock<WorkshopBlock> ANVIL = block("blacksmith_anvil", SoundType.ANVIL, MapColor.METAL, 5.0F);
    public static final DeferredBlock<WorkshopBlock> BARREL = block("quenching_barrel", SoundType.WOOD, MapColor.WOOD, 2.5F);
    public static final DeferredBlock<WorkshopBlock> HEARTH = block("blacksmith_hearth", SoundType.STONE, MapColor.STONE, 3.5F);
    public static final DeferredBlock<WorkshopBlock> REFINER = block("refining_furnace", SoundType.STONE, MapColor.STONE, 3.5F);
    public static final DeferredBlock<WorkshopBlock> BELLOWS = block("bellows_support", SoundType.WOOD, MapColor.WOOD, 2.5F);

    public static final DeferredBlock<WorkshopBlock> MOLD_STAND = block("mold_stand", SoundType.METAL, MapColor.METAL, 3.0F);
    public static final DeferredBlock<WorkshopBlock> PLACED_BLANK = displayBlock("placed_blank");
    public static final DeferredBlock<WorkshopBlock> PLACED_MOLD = displayBlock("placed_mold");
    public static final DeferredItem<BlockItem> SWORD_BLANK = blank("sword_blank");
    public static final DeferredItem<BlockItem> BRIGHT_BLANK = blank("bright_sword_blank");
    public static final DeferredItem<BlockItem> DAZZLING_BLANK = blank("dazzling_sword_blank");
    public static final DeferredItem<BlockItem> READY_BLANK = blank("ready_sword_blank");
    public static final DeferredItem<BlockItem> FILLED_MOLD = ITEMS.register("filled_iron_sword_mold", () -> new BlockItem(PLACED_MOLD.get(), new Item.Properties().stacksTo(1)));
    public static final DeferredItem<BlockItem> AXE_BLANK = blank("axe_blank"), BRIGHT_AXE = blank("bright_axe_blank"), DAZZLING_AXE = blank("dazzling_axe_blank"), READY_AXE = blank("ready_axe_blank");
    public static final DeferredItem<BlockItem> FILLED_AXE_MOLD = ITEMS.register("filled_axe_mold", () -> new BlockItem(PLACED_MOLD.get(),new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> TONGS = ITEMS.registerSimpleItem("ember_tongs",new Item.Properties().stacksTo(1));
    public static final DeferredItem<net.minecraft.world.item.AxeItem> FORGED_AXE = ITEMS.register("forged_steel_axe", () -> new net.minecraft.world.item.AxeItem(com.muxigame.zaluokeforging.forging.ForgedAxeTier.INSTANCE,new Item.Properties().attributes(net.minecraft.world.item.AxeItem.createAttributes(Tiers.IRON,9,-3.1F))));
    public static final DeferredItem<Item> SLAG = ITEMS.registerSimpleItem("iron_slag");
    public static final DeferredItem<Item> HAMMER = ITEMS.registerSimpleItem("forging_hammer", new Item.Properties().stacksTo(1));
    public static final DeferredItem<SwordItem> FORGED_SWORD = ITEMS.register("forged_iron_sword", () -> {
        SwordItem sword = new SwordItem(com.muxigame.zaluokeforging.forging.ForgedIronTier.INSTANCE, new Item.Properties().attributes(SwordItem.createAttributes(Tiers.IRON, 5, -2.4F)));
        return sword;
    });
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ZaluokeForging.MOD_ID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WorkshopEntity>> MACHINE = ENTITIES.register("workshop", () -> BlockEntityType.Builder.of(WorkshopEntity::new,
            ANVIL.get(), BARREL.get(), HEARTH.get(), REFINER.get(), BELLOWS.get(), MOLD_STAND.get(), PLACED_BLANK.get(), PLACED_MOLD.get()).build(null));
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ZaluokeForging.MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<WorkshopMenu>> MENU = MENUS.register("workshop", () -> new MenuType<>(WorkshopMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredItem<Item> LONGSWORD_MOLD = item("longsword_mold");
    public static final DeferredItem<Item> AXE_MOLD = item("axe_mold");
    public static final DeferredItem<Item> HOE_MOLD = item("hoe_mold");
    public static final DeferredItem<Item> PICKAXE_MOLD = item("pickaxe_mold");
    public static final DeferredItem<Item> HAMMER_MOLD = item("hammer_mold");

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WORKSHOP = TABS.register("workshop", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.zaluoke_forging"))
            .icon(() -> ANVIL.get().asItem().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ANVIL.get());
                output.accept(BARREL.get());
                output.accept(HEARTH.get());
                output.accept(REFINER.get());
                output.accept(BELLOWS.get());
                output.accept(MOLD_STAND.get());
                output.accept(SWORD_BLANK.get());
                output.accept(BRIGHT_BLANK.get());
                output.accept(DAZZLING_BLANK.get());
                output.accept(READY_BLANK.get());
                output.accept(FILLED_MOLD.get());
                output.accept(SLAG.get());
                output.accept(HAMMER.get());
                output.accept(FORGED_SWORD.get());
                output.accept(LONGSWORD_MOLD.get());
                output.accept(AXE_MOLD.get());
                output.accept(FILLED_AXE_MOLD.get());output.accept(AXE_BLANK.get());output.accept(BRIGHT_AXE.get());output.accept(DAZZLING_AXE.get());output.accept(READY_AXE.get());output.accept(FORGED_AXE.get());output.accept(TONGS.get());
                output.accept(HOE_MOLD.get());
                output.accept(PICKAXE_MOLD.get());
                output.accept(HAMMER_MOLD.get());
            }).build());

    private static DeferredBlock<WorkshopBlock> block(String id, SoundType sound, MapColor color, float strength) {
        DeferredBlock<WorkshopBlock> block = BLOCKS.register(id, () -> new WorkshopBlock(id,
                BlockBehaviour.Properties.of().mapColor(color).strength(strength).sound(sound).lightLevel(state -> id.equals("blacksmith_hearth") && state.getValue(WorkshopBlock.LIT) ? 13 : 0).noOcclusion()));
        ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    private static DeferredItem<Item> item(String id) {
        return ITEMS.registerSimpleItem(id, new Item.Properties().stacksTo(1));
    }

    private static DeferredBlock<WorkshopBlock> displayBlock(String id) {
        return BLOCKS.register(id, () -> new WorkshopBlock(id, BlockBehaviour.Properties.of().strength(2).noOcclusion()));
    }
    private static DeferredItem<BlockItem> blank(String id) {
        return ITEMS.register(id, () -> new com.muxigame.zaluokeforging.forging.WorkpieceItem(id, PLACED_BLANK.get(), new Item.Properties().stacksTo(1)));
    }
    public static void register(IEventBus bus) {
        com.muxigame.zaluokeforging.forging.WorkshopSounds.SOUNDS.register(bus);
        PARTICLES.register(bus);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TABS.register(bus);
        ENTITIES.register(bus);
        MENUS.register(bus);
    }

    private ModContent() { }
}
