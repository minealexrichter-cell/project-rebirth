package com.projectrebirth.bodies;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BodyBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ProjectRebirth.MOD_ID);

    public static final DeferredBlock<TransferCapsuleBlock> TRANSFER_CAPSULE = BLOCKS.registerBlock("transfer_capsule",
            TransferCapsuleBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK));

    private BodyBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
