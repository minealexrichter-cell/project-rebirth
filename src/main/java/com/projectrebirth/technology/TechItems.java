package com.projectrebirth.technology;

import java.util.EnumMap;
import java.util.Map;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TechItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProjectRebirth.MOD_ID);

    /** Basic electronic component used in bodies and machines. */
    public static final DeferredItem<Item> CIRCUIT_BOARD = ITEMS.registerSimpleItem("circuit_board");

    /** Second-tier electronics: needed by synthetic bodies and the power armor. */
    public static final DeferredItem<Item> PROCESSOR = ITEMS.registerSimpleItem("processor");
    /** Lithium energy cell: power armor and high-end bodies. */
    public static final DeferredItem<Item> POWER_CELL = ITEMS.registerSimpleItem("power_cell");
    /** Bronze actuator that moves the limbs of bodies and armor. */
    public static final DeferredItem<Item> ACTUATOR = ITEMS.registerSimpleItem("actuator");
    /** Synthetic tissue covering synthetic bodies. */
    public static final DeferredItem<Item> SYNTHETIC_FIBER = ITEMS.registerSimpleItem("synthetic_fiber");

    public static final Map<Station, DeferredItem<BlockItem>> STATIONS = new EnumMap<>(Station.class);
    public static final DeferredItem<BlockItem> MAINTENANCE_STATION = ITEMS.registerSimpleBlockItem(TechBlocks.MAINTENANCE_STATION);
    public static final DeferredItem<BlockItem> COMBUSTION_GENERATOR = ITEMS.registerSimpleBlockItem(TechBlocks.COMBUSTION_GENERATOR);
    public static final DeferredItem<BlockItem> SOLAR_PANEL = ITEMS.registerSimpleBlockItem(TechBlocks.SOLAR_PANEL);
    public static final DeferredItem<BlockItem> BATTERY = ITEMS.registerSimpleBlockItem(TechBlocks.BATTERY);
    public static final DeferredItem<BlockItem> CABLE = ITEMS.registerSimpleBlockItem(TechBlocks.CABLE);

    public static final Map<Metal, DeferredItem<BlockItem>> ORES = new EnumMap<>(Metal.class);
    public static final Map<Metal, DeferredItem<BlockItem>> DEEPSLATE_ORES = new EnumMap<>(Metal.class);
    public static final Map<Metal, DeferredItem<Item>> RAW = new EnumMap<>(Metal.class);
    public static final Map<Metal, DeferredItem<Item>> INGOTS = new EnumMap<>(Metal.class);

    static {
        for (Station station : Station.values()) {
            STATIONS.put(station, ITEMS.registerSimpleBlockItem(TechBlocks.STATIONS.get(station)));
        }
        for (Metal metal : Metal.values()) {
            ORES.put(metal, ITEMS.registerSimpleBlockItem(TechBlocks.ORES.get(metal)));
            DEEPSLATE_ORES.put(metal, ITEMS.registerSimpleBlockItem(TechBlocks.DEEPSLATE_ORES.get(metal)));
            RAW.put(metal, ITEMS.registerSimpleItem("raw_" + metal.id()));
            INGOTS.put(metal, ITEMS.registerSimpleItem(metal.id() + "_ingot"));
        }
    }

    /** Copper and tin alloy (crafted on a normal crafting table). */
    public static final DeferredItem<Item> BRONZE_INGOT = ITEMS.registerSimpleItem("bronze_ingot");

    private TechItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
