package com.projectrebirth.technology;

import java.util.EnumMap;
import java.util.Map;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.technology.energy.BatteryBlock;
import com.projectrebirth.technology.energy.CableBlock;
import com.projectrebirth.technology.energy.CombustionGeneratorBlock;
import com.projectrebirth.technology.energy.RadioisotopeGeneratorBlock;
import com.projectrebirth.technology.energy.SolarPanelBlock;

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
    /** Light of a station or a battery at work: its screens glow. */
    private static final int ACTIVE_LIGHT = 5;

    /** Repairs packed armor (the only place for critical integrity) and packed bodies. */
    public static final DeferredBlock<MaintenanceStationBlock> MAINTENANCE_STATION = BLOCKS.registerBlock("maintenance_station",
            MaintenanceStationBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(TechSounds.WORKING) ? ACTIVE_LIGHT : 0)
                    .requiresCorrectToolForDrops());

    /** Installs gadgets into packed bodies. */
    public static final DeferredBlock<BodyModificationStationBlock> BODY_MODIFICATION_STATION = BLOCKS.registerBlock("body_modification_station",
            BodyModificationStationBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(TechSounds.WORKING) ? ACTIVE_LIGHT : 0)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<CombustionGeneratorBlock> COMBUSTION_GENERATOR = BLOCKS.registerBlock("combustion_generator",
            CombustionGeneratorBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(CombustionGeneratorBlock.LIT) ? 13 : 0)
                    .noOcclusion()
                    .requiresCorrectToolForDrops());

    /** Late: energy without fuel, and it radiates (1.6.0). */
    public static final DeferredBlock<RadioisotopeGeneratorBlock> RADIOISOTOPE_GENERATOR = BLOCKS.registerBlock("radioisotope_generator",
            RadioisotopeGeneratorBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.0f, 8.0f)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(TechSounds.WORKING) ? 4 : 0)
                    .noOcclusion()
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<SolarPanelBlock> SOLAR_PANEL = BLOCKS.registerBlock("solar_panel",
            SolarPanelBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<BatteryBlock> BATTERY = BLOCKS.registerBlock("battery",
            BatteryBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(TechSounds.WORKING) ? ACTIVE_LIGHT : 0)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<CableBlock> CABLE = BLOCKS.registerBlock("cable",
            CableBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(0.5f)
                    .sound(SoundType.METAL)
                    .noOcclusion());

    /** Stone and deepslate ores; same hardness as the vanilla ores. */
    public static final Map<Metal, DeferredBlock<Block>> ORES = new EnumMap<>(Metal.class);
    public static final Map<Metal, DeferredBlock<Block>> DEEPSLATE_ORES = new EnumMap<>(Metal.class);

    static {
        for (Station station : Station.values()) {
            STATIONS.put(station, BLOCKS.register(station.id(), () -> new StationBlock(station, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(TechSounds.WORKING) ? ACTIVE_LIGHT : 0)
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
