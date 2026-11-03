package com.projectrebirth.factions;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FactionBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ProjectRebirth.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProjectRebirth.MOD_ID);

    /** Hard to break by hand, immune to explosions, cannot be pushed by pistons, glows faintly. */
    public static final DeferredBlock<FactionCoreBlock> FACTION_CORE = BLOCKS.registerBlock("faction_core", FactionCoreBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(25.0f, 1200.0f)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .lightLevel(state -> 7)
                    .pushReaction(PushReaction.BLOCK)
                    .requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> FACTION_CORE_ITEM = ITEMS.registerSimpleBlockItem(FACTION_CORE);

    private FactionBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
