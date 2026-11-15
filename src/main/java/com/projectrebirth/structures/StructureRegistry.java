package com.projectrebirth.structures;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Structures of 1.6.0 (abandoned laboratory, industrial ruins, ruined reactor). The structures, their pieces, decay, sets and
 * loot are data (`data/projectrebirth/worldgen/...`, `structure/`, `loot_table/chests/`); only the switchable placement and the
 * dry-ground check are code.
 */
public final class StructureRegistry {
    private static final DeferredRegister<StructurePlacementType<?>> PLACEMENTS = DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, ProjectRebirth.MOD_ID);

    public static final DeferredHolder<StructurePlacementType<?>, StructurePlacementType<SwitchableSpreadPlacement>> SWITCHABLE_SPREAD =
            PLACEMENTS.register("switchable_spread", () -> () -> SwitchableSpreadPlacement.CODEC);

    private static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, ProjectRebirth.MOD_ID);

    /** A jigsaw structure that keeps off water and steep slopes. */
    public static final DeferredHolder<StructureType<?>, StructureType<DryJigsawStructure>> DRY_JIGSAW =
            TYPES.register("dry_jigsaw", () -> () -> DryJigsawStructure.CODEC);

    private StructureRegistry() {
    }

    public static void register(IEventBus modEventBus) {
        PLACEMENTS.register(modEventBus);
        TYPES.register(modEventBus);
    }
}
