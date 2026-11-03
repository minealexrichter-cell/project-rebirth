package com.projectrebirth.technology;

import java.util.EnumMap;
import java.util.Map;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TechBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ProjectRebirth.MOD_ID);

    /** Production stations, one block per {@link Station}, registered under the station's id. */
    public static final Map<Station, DeferredBlock<StationBlock>> STATIONS = new EnumMap<>(Station.class);

    /** Stone and deepslate ores; same hardness as the vanilla ores. */
    public static final Map<Metal, DeferredBlock<Block>> ORES = new EnumMap<>(Metal.class);
    public static final Map<Metal, DeferredBlock<Block>> DEEPSLATE_ORES = new EnumMap<>(Metal.class);

    static {
        for (Station station : Station.values()) {
            STATIONS.put(station, BLOCKS.register(station.id(), () -> new StationBlock(station, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops())));
        }
        for (Metal metal : Metal.values()) {
            ORES.put(metal, BLOCKS.registerSimpleBlock(metal.id() + "_ore", BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(3.0f, 3.0f)
                    .requiresCorrectToolForDrops()));
            DEEPSLATE_ORES.put(metal, BLOCKS.registerSimpleBlock("deepslate_" + metal.id() + "_ore", BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(4.5f, 3.0f)
                    .sound(SoundType.DEEPSLATE)
                    .requiresCorrectToolForDrops()));
        }
    }

    private TechBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
