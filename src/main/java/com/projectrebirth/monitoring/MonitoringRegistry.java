package com.projectrebirth.monitoring;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The environmental monitoring station and its printed contamination map (1.6.0, block E). */
public final class MonitoringRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ProjectRebirth.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProjectRebirth.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ProjectRebirth.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ProjectRebirth.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ProjectRebirth.MOD_ID);

    /** What a printed map shows. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Snapshot>> SNAPSHOT = COMPONENTS.registerComponentType(
            "contamination_snapshot", builder -> builder.persistent(Snapshot.CODEC).networkSynchronized(Snapshot.STREAM_CODEC));

    public static final DeferredBlock<MonitoringStationBlock> MONITORING_STATION = BLOCKS.registerBlock("monitoring_station", MonitoringStationBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 6.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> MONITORING_STATION_ITEM = ITEMS.registerSimpleBlockItem(MONITORING_STATION);
    public static final DeferredItem<ContaminationMapItem> CONTAMINATION_MAP = ITEMS.register("contamination_map",
            () -> new ContaminationMapItem(new Item.Properties().stacksTo(1)));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MonitoringStationBlockEntity>> MONITORING_STATION_ENTITY =
            BLOCK_ENTITIES.register("monitoring_station", () -> BlockEntityType.Builder.of(MonitoringStationBlockEntity::new, MONITORING_STATION.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<MonitoringStationMenu>> MONITORING_STATION_MENU =
            MENUS.register("monitoring_station", () -> IMenuTypeExtension.create(MonitoringStationMenu::new));

    private MonitoringRegistry() {
    }

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        MonitoringPayload.register(modEventBus);
        modEventBus.addListener(MonitoringRegistry::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, MONITORING_STATION_ENTITY.get(), (station, side) -> station.automation());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, MONITORING_STATION_ENTITY.get(), (station, side) -> station.energy().inputOnly());
    }
}
