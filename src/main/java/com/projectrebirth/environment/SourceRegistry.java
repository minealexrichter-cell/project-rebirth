package com.projectrebirth.environment;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The source blocks of 1.6.0: radioactive debris and the leaking waste barrel (the radioisotope generator is registered with the other generators). */
public final class SourceRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ProjectRebirth.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProjectRebirth.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ProjectRebirth.MOD_ID);

    public static final DeferredBlock<SourceBlock> RADIOACTIVE_DEBRIS = BLOCKS.registerBlock("radioactive_debris",
            properties -> new SourceBlock(properties, SourceKind.DEBRIS),
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GREEN)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.TUFF)
                    .lightLevel(state -> 4));
    public static final DeferredBlock<SourceBlock> LEAKING_WASTE_BARREL = BLOCKS.registerBlock("leaking_waste_barrel",
            properties -> new SourceBlock(properties, SourceKind.WASTE_BARREL),
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion());

    public static final DeferredBlock<SourceBlock> IRRADIATED_SLUDGE = BLOCKS.registerBlock("irradiated_sludge",
            properties -> new SourceBlock(properties, SourceKind.SLUDGE),
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GREEN)
                    .strength(0.6f, 1.0f)
                    .sound(SoundType.SLIME_BLOCK)
                    .speedFactor(0.5f)
                    .noOcclusion()
                    .lightLevel(state -> 8));

    public static final DeferredBlock<ToxicPuddleBlock> TOXIC_PUDDLE = BLOCKS.registerBlock("toxic_puddle",
            ToxicPuddleBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GREEN)
                    .strength(0.1f)
                    .sound(SoundType.SLIME_BLOCK)
                    .noCollission()
                    .replaceable()
                    .noLootTable()
                    .noOcclusion()
                    .lightLevel(state -> 5));

    public static final DeferredItem<BlockItem> RADIOACTIVE_DEBRIS_ITEM = ITEMS.registerSimpleBlockItem(RADIOACTIVE_DEBRIS);
    public static final DeferredItem<BlockItem> LEAKING_WASTE_BARREL_ITEM = ITEMS.registerSimpleBlockItem(LEAKING_WASTE_BARREL);
    public static final DeferredItem<BlockItem> IRRADIATED_SLUDGE_ITEM = ITEMS.registerSimpleBlockItem(IRRADIATED_SLUDGE);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SourceBlockEntity>> SOURCE_ENTITY =
            BLOCK_ENTITIES.register("radiation_source", () -> BlockEntityType.Builder.of(SourceBlockEntity::new,
                    RADIOACTIVE_DEBRIS.get(), LEAKING_WASTE_BARREL.get(), IRRADIATED_SLUDGE.get(), TOXIC_PUDDLE.get()).build(null));

    private SourceRegistry() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
    }
}
