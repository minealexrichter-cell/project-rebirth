package com.projectrebirth.structures;

import com.mojang.serialization.MapCodec;
import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The decoration of the large structures of 1.6.0: monitors, server racks, specimen tanks, emergency lamps, wrecked turrets and
 * ammo crates. They do nothing but look the part; broken, each drops itself, so a player can furnish a base with them.
 * (The irradiated sludge of the contamination zone is a radiation source and lives in environment/SourceRegistry.)
 */
public final class StructureBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ProjectRebirth.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProjectRebirth.MOD_ID);

    private static BlockBehaviour.Properties metal(int light) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f, 6.0f).sound(SoundType.METAL)
                .requiresCorrectToolForDrops().noOcclusion().lightLevel(state -> light);
    }

    public static final DeferredBlock<DecorBlock> WALL_MONITOR = BLOCKS.registerBlock("wall_monitor",
            p -> new DecorBlock(p, 1, 1, 13, 15, 14, 16, true), metal(7));
    public static final DeferredBlock<DecorBlock> SERVER_RACK = BLOCKS.registerBlock("server_rack",
            p -> new DecorBlock(p, 0.5, 0, 0.5, 15.5, 16, 15, false), metal(3));
    public static final DeferredBlock<DecorBlock> EMERGENCY_LAMP = BLOCKS.registerBlock("emergency_lamp",
            p -> new DecorBlock(p, 5, 4, 11, 11, 10, 16, true), metal(11));
    public static final DeferredBlock<DecorBlock> WRECKED_TURRET = BLOCKS.registerBlock("wrecked_turret",
            p -> new DecorBlock(p, 2, 0, 0, 14, 13, 14, false), metal(0));
    public static final DeferredBlock<DecorBlock> AMMO_CRATE = BLOCKS.registerBlock("ammo_crate",
            p -> new DecorBlock(p, 0.5, 0, 2, 15.5, 10, 14, false),
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(2.0f, 3.0f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredBlock<SpecimenTankBlock> SPECIMEN_TANK = BLOCKS.registerBlock("specimen_tank",
            SpecimenTankBlock::new, metal(10).sound(SoundType.GLASS));

    public static final DeferredItem<BlockItem> WALL_MONITOR_ITEM = ITEMS.registerSimpleBlockItem(WALL_MONITOR);
    public static final DeferredItem<BlockItem> SERVER_RACK_ITEM = ITEMS.registerSimpleBlockItem(SERVER_RACK);
    public static final DeferredItem<BlockItem> EMERGENCY_LAMP_ITEM = ITEMS.registerSimpleBlockItem(EMERGENCY_LAMP);
    public static final DeferredItem<BlockItem> WRECKED_TURRET_ITEM = ITEMS.registerSimpleBlockItem(WRECKED_TURRET);
    public static final DeferredItem<BlockItem> AMMO_CRATE_ITEM = ITEMS.registerSimpleBlockItem(AMMO_CRATE);
    public static final DeferredItem<BlockItem> SPECIMEN_TANK_ITEM = ITEMS.registerSimpleBlockItem(SPECIMEN_TANK);

    /** A specimen tank: no facing, a little narrower than a full block. */
    public static class SpecimenTankBlock extends Block {
        public static final MapCodec<SpecimenTankBlock> CODEC = simpleCodec(SpecimenTankBlock::new);
        private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

        public SpecimenTankBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected MapCodec<? extends Block> codec() {
            return CODEC;
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }
    }

    private StructureBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
