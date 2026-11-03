package com.projectrebirth.technology;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Working sounds of machines. The server marks a machine as working in its block state; every client then plays one
 * looping sound per working machine and fades it out when the machine stops (see client/TechSoundsClient).
 */
public final class TechSounds {
    /** True while a station is producing or repairing. Old blocks without it load as not working. */
    public static final BooleanProperty WORKING = BooleanProperty.create("working");

    /** Plays the loops on a client; set by the client at start-up, never on a dedicated server. */
    @Nullable
    public static volatile LoopPlayer loops;

    public interface LoopPlayer {
        void keepPlaying(Level level, BlockPos pos, SoundEvent sound);
    }

    private TechSounds() {
    }

    /** Changes the block state only when the working state really changes (so not every tick). */
    static void setWorking(BlockEntity blockEntity, boolean working) {
        Level level = blockEntity.getLevel();
        BlockState state = blockEntity.getBlockState();
        if (level != null && state.hasProperty(WORKING) && state.getValue(WORKING) != working) {
            level.setBlock(blockEntity.getBlockPos(), state.setValue(WORKING, working), Block.UPDATE_CLIENTS);
        }
    }

    /** Client ticker of a machine block: keeps its loop going while the given property is true. */
    public static <T extends BlockEntity> BlockEntityTicker<T> clientLoop(BooleanProperty property, SoundEvent sound) {
        return (level, pos, state, blockEntity) -> {
            LoopPlayer player = loops;
            if (player != null && state.hasProperty(property) && state.getValue(property)) {
                player.keepPlaying(level, pos, sound);
            }
        };
    }
}
