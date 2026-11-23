package com.projectrebirth.machinemind;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 1.9.0, block F: the machine mind: the modules of a node, the blocks of its base, its drone, the mind chip. */
public final class MindRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ProjectRebirth.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProjectRebirth.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ProjectRebirth.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, ProjectRebirth.MOD_ID);

    private static BlockBehaviour.Properties metal(float strength, int light) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(strength, 8.0f).sound(SoundType.NETHERITE_BLOCK)
                .requiresCorrectToolForDrops().lightLevel(state -> light);
    }

    private static DeferredBlock<MindModuleBlock> module(MindModule module) {
        return BLOCKS.registerBlock(module.getSerializedName(), p -> new MindModuleBlock(module, p),
                metal(6.0f, 7).noOcclusion().pushReaction(PushReaction.BLOCK));
    }

    public static final DeferredBlock<MindModuleBlock> COMPUTER = module(MindModule.COMPUTER);
    public static final DeferredBlock<MindModuleBlock> LINE = module(MindModule.LINE);
    public static final DeferredBlock<MindModuleBlock> FOUNDRY = module(MindModule.FOUNDRY);
    public static final DeferredBlock<MindModuleBlock> HANGAR = module(MindModule.HANGAR);
    public static final DeferredBlock<MindPartBlock> PART = BLOCKS.registerBlock("mind_part", MindPartBlock::new,
            metal(6.0f, 0).noOcclusion().noLootTable().pushReaction(PushReaction.BLOCK).isValidSpawn((s, l, p, t) -> false)
                    .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false));
    public static final DeferredBlock<MindWreckBlock> WRECK = BLOCKS.registerBlock("mind_computer_wreck", MindWreckBlock::new, metal(5.0f, 0).noOcclusion());

    public static final DeferredBlock<Block> PLATING = BLOCKS.registerSimpleBlock("mind_plating", metal(5.0f, 0));
    public static final DeferredBlock<Block> CABLE = BLOCKS.registerSimpleBlock("mind_cable", metal(2.5f, 3));
    public static final DeferredBlock<Block> FLOODLIGHT = BLOCKS.registerSimpleBlock("mind_floodlight", metal(2.5f, 15));
    public static final DeferredBlock<GeneratorBlock> GENERATOR = BLOCKS.registerBlock("mind_generator", GeneratorBlock::new, metal(4.0f, 9));
    public static final DeferredBlock<IronBarsBlock> FENCE = BLOCKS.registerBlock("mind_fence", IronBarsBlock::new, metal(4.0f, 0).noOcclusion());
    public static final DeferredBlock<FenceGateBlock> GATE = BLOCKS.registerBlock("mind_gate",
            p -> new FenceGateBlock(java.util.Optional.empty(), p, java.util.Optional.of(SoundEvents.IRON_DOOR_OPEN), java.util.Optional.of(SoundEvents.IRON_DOOR_CLOSE)),
            metal(4.0f, 0).noOcclusion().forceSolidOn());

    public static final DeferredItem<BlockItem> COMPUTER_ITEM = ITEMS.registerSimpleBlockItem(COMPUTER);
    public static final DeferredItem<BlockItem> LINE_ITEM = ITEMS.registerSimpleBlockItem(LINE);
    public static final DeferredItem<BlockItem> FOUNDRY_ITEM = ITEMS.registerSimpleBlockItem(FOUNDRY);
    public static final DeferredItem<BlockItem> HANGAR_ITEM = ITEMS.registerSimpleBlockItem(HANGAR);
    public static final DeferredItem<BlockItem> WRECK_ITEM = ITEMS.registerSimpleBlockItem(WRECK);
    public static final DeferredItem<BlockItem> PLATING_ITEM = ITEMS.registerSimpleBlockItem(PLATING);
    public static final DeferredItem<BlockItem> CABLE_ITEM = ITEMS.registerSimpleBlockItem(CABLE);
    public static final DeferredItem<BlockItem> FLOODLIGHT_ITEM = ITEMS.registerSimpleBlockItem(FLOODLIGHT);
    public static final DeferredItem<BlockItem> GENERATOR_ITEM = ITEMS.registerSimpleBlockItem(GENERATOR);
    public static final DeferredItem<BlockItem> FENCE_ITEM = ITEMS.registerSimpleBlockItem(FENCE);
    public static final DeferredItem<BlockItem> GATE_ITEM = ITEMS.registerSimpleBlockItem(GATE);
    /** What the computer of a node leaves: the material of the upgrades of a later version. */
    public static final DeferredItem<Item> CHIP = ITEMS.registerSimpleItem("mind_chip");

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MindModuleBlockEntity>> MODULE_ENTITY = BLOCK_ENTITIES.register("mind_module",
            () -> BlockEntityType.Builder.of(MindModuleBlockEntity::new, LINE.get(), FOUNDRY.get(), HANGAR.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MindComputerBlockEntity>> COMPUTER_ENTITY = BLOCK_ENTITIES.register("mind_computer",
            () -> BlockEntityType.Builder.of(MindComputerBlockEntity::new, COMPUTER.get()).build(null));

    public static final DeferredHolder<EntityType<?>, EntityType<MindDroneEntity>> DRONE = ENTITY_TYPES.register("mind_drone",
            () -> EntityType.Builder.<MindDroneEntity>of(MindDroneEntity::new, MobCategory.MONSTER).sized(0.7f, 0.6f).eyeHeight(0.35f)
                    .clientTrackingRange(10).build("mind_drone"));
    public static final DeferredItem<Item> DRONE_SPAWN_EGG = ITEMS.register("mind_drone_spawn_egg",
            () -> new DeferredSpawnEggItem(DRONE, 0x23262B, 0xFF2A1F, new Item.Properties()));

    private MindRegistry() {
    }

    /** A generator of the base: broken, it sets the node back. */
    public static class GeneratorBlock extends Block {
        public GeneratorBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
            if (!state.is(next.getBlock()) && level instanceof ServerLevel server) {
                for (MindNodes.Node node : MindNodes.get(server.getServer()).near(server, pos.getX(), pos.getZ(), 24)) {
                    if (server.isLoaded(node.pos()) && server.getBlockEntity(node.pos()) instanceof MindComputerBlockEntity computer
                            && computer.active() && computer.plan(server).stream().anyMatch(cell -> cell.pos().equals(pos))) {
                        computer.delay(com.projectrebirth.config.WastelandConfig.MIND_GENERATOR_DELAY.get() * 1200L);
                        computer.noteDamage(server);
                    }
                }
            }
            super.onRemove(state, level, pos, next, moved);
        }
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(MindRegistry::registerAttributes);
        modEventBus.addListener(MindRegistry::registerData);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(DRONE.get(), MindDroneEntity.createAttributes().build());
    }

    private static void registerData(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(MindStage.REGISTRY, MindStage.CODEC);
    }
}
