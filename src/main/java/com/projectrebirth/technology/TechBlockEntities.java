package com.projectrebirth.technology;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.technology.energy.BatteryBlockEntity;
import com.projectrebirth.technology.energy.CableInput;
import com.projectrebirth.technology.energy.CombustionGeneratorBlockEntity;
import com.projectrebirth.technology.energy.SolarPanelBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TechBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ProjectRebirth.MOD_ID);

    /** One type for every production station; the block tells which station it is. */
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StationBlockEntity>> STATION =
            BLOCK_ENTITIES.register("station", () -> BlockEntityType.Builder.of(StationBlockEntity::new,
                    TechBlocks.STATIONS.values().stream().map(block -> block.get()).toArray(StationBlock[]::new)).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MaintenanceBlockEntity>> MAINTENANCE =
            BLOCK_ENTITIES.register("maintenance_station", () -> BlockEntityType.Builder.of(MaintenanceBlockEntity::new,
                    TechBlocks.MAINTENANCE_STATION.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CombustionGeneratorBlockEntity>> COMBUSTION_GENERATOR =
            BLOCK_ENTITIES.register("combustion_generator", () -> BlockEntityType.Builder.of(CombustionGeneratorBlockEntity::new,
                    TechBlocks.COMBUSTION_GENERATOR.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SolarPanelBlockEntity>> SOLAR_PANEL =
            BLOCK_ENTITIES.register("solar_panel", () -> BlockEntityType.Builder.of(SolarPanelBlockEntity::new,
                    TechBlocks.SOLAR_PANEL.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BatteryBlockEntity>> BATTERY =
            BLOCK_ENTITIES.register("battery", () -> BlockEntityType.Builder.of(BatteryBlockEntity::new,
                    TechBlocks.BATTERY.get()).build(null));

    private TechBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
        modEventBus.addListener(TechBlockEntities::registerCapabilities);
    }

    /**
     * Hoppers, pipes and other mods' machines move items through the automation handlers, and energy (FE) through
     * the energy buffers: stations take energy in, generators give it out, the battery takes it in except at its front.
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, STATION.get(), (station, side) -> station.automation());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, MAINTENANCE.get(), (station, side) -> station.automation());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, COMBUSTION_GENERATOR.get(), (generator, side) -> generator.automation());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, STATION.get(), (station, side) -> station.energy());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, MAINTENANCE.get(), (station, side) -> station.energy());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, COMBUSTION_GENERATOR.get(), (generator, side) -> generator.energy());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SOLAR_PANEL.get(), SolarPanelBlockEntity::forSide);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BATTERY.get(), BatteryBlockEntity::forSide);
        event.registerBlock(Capabilities.EnergyStorage.BLOCK, (level, pos, state, blockEntity, side) -> new CableInput(level, pos, side),
                TechBlocks.CABLE.get());
    }
}
