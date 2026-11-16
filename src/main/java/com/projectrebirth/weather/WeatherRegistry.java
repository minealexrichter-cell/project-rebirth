package com.projectrebirth.weather;

import com.projectrebirth.ProjectRebirth;

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

/** The weather station and its long range forecast module (1.6.0, block D). */
public final class WeatherRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ProjectRebirth.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProjectRebirth.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ProjectRebirth.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ProjectRebirth.MOD_ID);

    public static final DeferredBlock<WeatherStationBlock> WEATHER_STATION = BLOCKS.registerBlock("weather_station", WeatherStationBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 6.0f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion());
    public static final DeferredItem<BlockItem> WEATHER_STATION_ITEM = ITEMS.registerSimpleBlockItem(WEATHER_STATION);
    public static final DeferredItem<Item> LONG_RANGE_MODULE = ITEMS.registerSimpleItem("long_range_forecast_module", new Item.Properties().stacksTo(16));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WeatherStationBlockEntity>> WEATHER_STATION_ENTITY =
            BLOCK_ENTITIES.register("weather_station", () -> BlockEntityType.Builder.of(WeatherStationBlockEntity::new, WEATHER_STATION.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<WeatherStationMenu>> WEATHER_STATION_MENU =
            MENUS.register("weather_station", () -> IMenuTypeExtension.create(WeatherStationMenu::new));

    private WeatherRegistry() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        ForecastPayload.register(modEventBus);
        modEventBus.addListener(WeatherRegistry::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, WEATHER_STATION_ENTITY.get(), (station, side) -> station.items());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, WEATHER_STATION_ENTITY.get(), (station, side) -> station.energy().inputOnly());
    }
}
