package com.projectrebirth.shelter;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Life support of version 1.4.0 "Shelter": sealed rooms and what makes them. The package {@code shelter} keeps the rooms apart
 * from {@code environment}, which only asks it whether a place is inside a sealed room.
 */
public final class ShelterRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ProjectRebirth.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProjectRebirth.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ProjectRebirth.MOD_ID);
    private static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ProjectRebirth.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ProjectRebirth.MOD_ID);

    /** What is left of a filter cartridge; none on a new one (full). */
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<Float>> CARTRIDGE_LIFE =
            COMPONENTS.registerComponentType("cartridge_life", builder -> builder.persistent(com.mojang.serialization.Codec.FLOAT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.FLOAT));

    /** Heavy doors that open by hand, with iron sounds; they seal by the tag sealing_full, not by this. */
    public static final BlockSetType AIRLOCK = BlockSetType.register(new BlockSetType(ProjectRebirth.MOD_ID + "_airlock",
            true, false, false, BlockSetType.PressurePlateSensitivity.MOBS, SoundType.METAL,
            SoundEvents.IRON_DOOR_CLOSE, SoundEvents.IRON_DOOR_OPEN, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundEvents.IRON_TRAPDOOR_OPEN,
            SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
            SoundEvents.STONE_BUTTON_CLICK_OFF, SoundEvents.STONE_BUTTON_CLICK_ON));

    public static final DeferredBlock<AirFiltrationUnitBlock> AIR_FILTRATION_UNIT = BLOCKS.registerBlock("air_filtration_unit",
            AirFiltrationUnitBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<AirlockDoorBlock> AIRLOCK_DOOR = BLOCKS.registerBlock("airlock_door",
            properties -> new AirlockDoorBlock(AIRLOCK, properties),
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<SealedHatchBlock> SEALED_HATCH = BLOCKS.registerBlock("sealed_hatch",
            properties -> new SealedHatchBlock(AIRLOCK, properties),
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .requiresCorrectToolForDrops());

    /** Feeds only life support, only when the main power does not keep up. */
    public static final DeferredBlock<com.projectrebirth.technology.energy.BackupBatteryBlock> BACKUP_BATTERY = BLOCKS.registerBlock("backup_battery",
            com.projectrebirth.technology.energy.BackupBatteryBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(com.projectrebirth.technology.TechSounds.WORKING) ? 7 : 0)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<DecontaminationChamberBlock> DECONTAMINATION_CHAMBER = BLOCKS.registerBlock("decontamination_chamber",
            DecontaminationChamberBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<RadiationPurgeStationBlock> RADIATION_PURGE_STATION = BLOCKS.registerBlock("radiation_purge_station",
            RadiationPurgeStationBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(com.projectrebirth.technology.TechSounds.WORKING) ? 9 : 2)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<ShelterControlPanelBlock> SHELTER_CONTROL_PANEL = BLOCKS.registerBlock("shelter_control_panel",
            ShelterControlPanelBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 4)
                    .requiresCorrectToolForDrops());

    public static final DeferredItem<BlockItem> AIR_FILTRATION_UNIT_ITEM = ITEMS.registerSimpleBlockItem(AIR_FILTRATION_UNIT);
    public static final DeferredItem<BlockItem> SHELTER_CONTROL_PANEL_ITEM = ITEMS.registerSimpleBlockItem(SHELTER_CONTROL_PANEL);
    public static final DeferredItem<DoubleHighBlockItem> DECONTAMINATION_CHAMBER_ITEM = ITEMS.register("decontamination_chamber",
            () -> new DoubleHighBlockItem(DECONTAMINATION_CHAMBER.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> RADIATION_PURGE_STATION_ITEM = ITEMS.registerSimpleBlockItem(RADIATION_PURGE_STATION);
    public static final DeferredItem<Item> DECONTAMINATION_SOLUTION = ITEMS.registerSimpleItem("decontamination_solution", new Item.Properties().stacksTo(16));
    public static final DeferredItem<BlockItem> BACKUP_BATTERY_ITEM = ITEMS.registerSimpleBlockItem(BACKUP_BATTERY);
    public static final DeferredItem<DoubleHighBlockItem> AIRLOCK_DOOR_ITEM = ITEMS.register("airlock_door",
            () -> new DoubleHighBlockItem(AIRLOCK_DOOR.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> SEALED_HATCH_ITEM = ITEMS.registerSimpleBlockItem(SEALED_HATCH);
    public static final DeferredItem<CartridgeItem> FILTER_CARTRIDGE = ITEMS.register("filter_cartridge",
            () -> new CartridgeItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> SPENT_FILTER_CARTRIDGE = ITEMS.registerSimpleItem("spent_filter_cartridge", new Item.Properties().stacksTo(16));

    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<AirFiltrationMenu>> AIR_FILTRATION_MENU =
            MENUS.register("air_filtration_unit", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(AirFiltrationMenu::new));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AirFiltrationUnitBlockEntity>> AIR_FILTRATION_UNIT_ENTITY =
            BLOCK_ENTITIES.register("air_filtration_unit", () -> BlockEntityType.Builder.of(AirFiltrationUnitBlockEntity::new,
                    AIR_FILTRATION_UNIT.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.projectrebirth.technology.energy.BackupBatteryBlockEntity>> BACKUP_BATTERY_ENTITY =
            BLOCK_ENTITIES.register("backup_battery", () -> BlockEntityType.Builder.of(com.projectrebirth.technology.energy.BackupBatteryBlockEntity::new,
                    BACKUP_BATTERY.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DecontaminationChamberBlockEntity>> DECONTAMINATION_CHAMBER_ENTITY =
            BLOCK_ENTITIES.register("decontamination_chamber", () -> BlockEntityType.Builder.of(DecontaminationChamberBlockEntity::new,
                    DECONTAMINATION_CHAMBER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadiationPurgeStationBlockEntity>> RADIATION_PURGE_STATION_ENTITY =
            BLOCK_ENTITIES.register("radiation_purge_station", () -> BlockEntityType.Builder.of(RadiationPurgeStationBlockEntity::new,
                    RADIATION_PURGE_STATION.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShelterControlPanelBlockEntity>> SHELTER_CONTROL_PANEL_ENTITY =
            BLOCK_ENTITIES.register("shelter_control_panel", () -> BlockEntityType.Builder.of(ShelterControlPanelBlockEntity::new,
                    SHELTER_CONTROL_PANEL.get()).build(null));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<ShelterPanelMenu>> SHELTER_PANEL_MENU =
            MENUS.register("shelter_control_panel", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(ShelterPanelMenu::new));

    private static final DeferredRegister<net.neoforged.neoforge.attachment.AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.ATTACHMENT_TYPES, ProjectRebirth.MOD_ID);
    /** External contamination of a player, a body or a power armor; saved, gone on death. */
    public static final java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<Float>> CONTAMINATION = ATTACHMENTS.register("contamination",
            () -> net.neoforged.neoforge.attachment.AttachmentType.builder(() -> 0.0f).serialize(com.mojang.serialization.Codec.FLOAT, amount -> amount > 0.0f).build());

    static {
        com.projectrebirth.technology.energy.BackupBatteryBlockEntity.setType(BACKUP_BATTERY_ENTITY::get);
    }

    private ShelterRegistry() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
        COMPONENTS.register(modEventBus);
        modEventBus.addListener(ShelterRegistry::registerCapabilities);
    }

    private static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, AIR_FILTRATION_UNIT_ENTITY.get(),
                (unit, side) -> unit.automation());
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, AIR_FILTRATION_UNIT_ENTITY.get(),
                (unit, side) -> unit.energy());
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, BACKUP_BATTERY_ENTITY.get(),
                (battery, side) -> battery.input());
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, DECONTAMINATION_CHAMBER_ENTITY.get(),
                (chamber, side) -> chamber.automation());
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, DECONTAMINATION_CHAMBER_ENTITY.get(),
                (chamber, side) -> chamber.energy());
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, RADIATION_PURGE_STATION_ENTITY.get(),
                (station, side) -> station.energy());
    }
}
