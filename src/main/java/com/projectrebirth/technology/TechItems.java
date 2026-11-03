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

    public static final DeferredItem<BlockItem> ASSEMBLY_STATION = ITEMS.registerSimpleBlockItem(TechBlocks.ASSEMBLY_STATION);

    public static final Map<Metal, DeferredItem<BlockItem>> ORES = new EnumMap<>(Metal.class);
    public static final Map<Metal, DeferredItem<BlockItem>> DEEPSLATE_ORES = new EnumMap<>(Metal.class);
    public static final Map<Metal, DeferredItem<Item>> RAW = new EnumMap<>(Metal.class);
    public static final Map<Metal, DeferredItem<Item>> INGOTS = new EnumMap<>(Metal.class);

    static {
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
