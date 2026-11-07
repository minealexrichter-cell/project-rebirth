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

    public static final DeferredBlock<DoorBlock> AIRLOCK_DOOR = BLOCKS.registerBlock("airlock_door",
            properties -> new DoorBlock(AIRLOCK, properties),
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<TrapDoorBlock> SEALED_HATCH = BLOCKS.registerBlock("sealed_hatch",
            properties -> new TrapDoorBlock(AIRLOCK, properties),
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .requiresCorrectToolForDrops());

    public static final DeferredItem<BlockItem> AIR_FILTRATION_UNIT_ITEM = ITEMS.registerSimpleBlockItem(AIR_FILTRATION_UNIT);
    public static final DeferredItem<DoubleHighBlockItem> AIRLOCK_DOOR_ITEM = ITEMS.register("airlock_door",
            () -> new DoubleHighBlockItem(AIRLOCK_DOOR.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> SEALED_HATCH_ITEM = ITEMS.registerSimpleBlockItem(SEALED_HATCH);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AirFiltrationUnitBlockEntity>> AIR_FILTRATION_UNIT_ENTITY =
            BLOCK_ENTITIES.register("air_filtration_unit", () -> BlockEntityType.Builder.of(AirFiltrationUnitBlockEntity::new,
                    AIR_FILTRATION_UNIT.get()).build(null));

    private ShelterRegistry() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
    }
}
