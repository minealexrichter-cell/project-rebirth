package com.projectrebirth.technology;

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

    private TechItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
