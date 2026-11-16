package com.projectrebirth.water;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Polluted water and its purifier (1.6.0, block C). */
public final class WaterRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ProjectRebirth.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProjectRebirth.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ProjectRebirth.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ProjectRebirth.MOD_ID);

    public static final DeferredItem<PollutedWaterBottleItem> POLLUTED_WATER_BOTTLE = ITEMS.register("polluted_water_bottle",
            () -> new PollutedWaterBottleItem(new Item.Properties().stacksTo(16)));
    /** Poured out, it is water like any other: the pollution is a matter of the place. */
    public static final DeferredItem<BucketItem> POLLUTED_WATER_BUCKET = ITEMS.register("polluted_water_bucket",
            () -> new BucketItem(Fluids.WATER, new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    public static final DeferredBlock<WaterPurifierBlock> WATER_PURIFIER = BLOCKS.registerBlock("water_purifier", WaterPurifierBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 6.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> WATER_PURIFIER_ITEM = ITEMS.registerSimpleBlockItem(WATER_PURIFIER);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WaterPurifierBlockEntity>> WATER_PURIFIER_ENTITY =
            BLOCK_ENTITIES.register("water_purifier", () -> BlockEntityType.Builder.of(WaterPurifierBlockEntity::new, WATER_PURIFIER.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<WaterPurifierMenu>> WATER_PURIFIER_MENU =
            MENUS.register("water_purifier", () -> IMenuTypeExtension.create(WaterPurifierMenu::new));

    private WaterRegistry() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        modEventBus.addListener(WaterRegistry::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, WATER_PURIFIER_ENTITY.get(), (purifier, side) -> purifier.automation());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, WATER_PURIFIER_ENTITY.get(), (purifier, side) -> purifier.energy().inputOnly());
    }
}
