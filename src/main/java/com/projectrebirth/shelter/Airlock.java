package com.projectrebirth.shelter;

import java.util.HashSet;
import java.util.Set;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The airlock interlock. The doors of one airlock are the airlock doors and sealed hatches around one small closed space
 * (at most [environment.shelter] airlockMaxVolume blocks of air): while one of them is open, the others do not open. No
 * linking tool is needed; two airlock doors with a small room between them are an airlock. Checked on the server when a
 * player opens a door (redstone opens them freely).
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class Airlock {
    private Airlock() {
    }

    private static boolean isAirlockDoor(BlockState state) {
        return Sealing.isDoorLike(state) && state.is(Sealing.FULL) && state.hasProperty(BlockStateProperties.OPEN);
    }

    private static boolean isOpen(BlockState state) {
        return state.getValue(BlockStateProperties.OPEN);
    }

    /** The blocks of a door: both halves; a hatch is one block. */
    private static Set<BlockPos> doorBlocks(Level level, BlockPos pos, BlockState state) {
        Set<BlockPos> blocks = new HashSet<>();
        blocks.add(pos.immutable());
        if (state.getBlock() instanceof DoorBlock) {
            blocks.add(state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below());
        }
        return blocks;
    }

    /**
     * Whether this airlock door or hatch may open now: no other one of a small space next to it is open. Public for tests.
     */
    public static boolean mayOpen(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!isAirlockDoor(state) || isOpen(state)) {
            return true;
        }
        Set<BlockPos> own = doorBlocks(level, pos, state);
        LongOpenHashSet tried = new LongOpenHashSet();
        for (BlockPos block : own) {
            for (Direction direction : Direction.values()) {
                BlockPos start = block.relative(direction);
                if (own.contains(start) || tried.contains(start.asLong())) {
                    continue;
                }
                if (otherOpenDoor(level, start, own, tried)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Floods a small space from start; true if it is small and another airlock door of it is open. */
    private static boolean otherOpenDoor(Level level, BlockPos start, Set<BlockPos> own, LongOpenHashSet tried) {
        BlockState startState = level.getBlockState(start);
        if (Sealing.isDoorLike(startState) || !level.isLoaded(start) || fullySealed(startState)) {
            return false;
        }
        int limit = RebirthConfig.AIRLOCK_MAX_VOLUME.get();
        LongOpenHashSet air = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        air.add(start.asLong());
        queue.enqueue(start.asLong());
        boolean found = false;
        BlockPos.MutableBlockPos here = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty()) {
            here.set(queue.dequeueLong());
            BlockState hereState = level.getBlockState(here);
            if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, here.getX(), here.getZ()) <= here.getY()) {
                tried.addAll(air);
                return false; // open sky: not a closed space
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = here.relative(direction);
                if (air.contains(next.asLong()) || own.contains(next)) {
                    continue;
                }
                if (Sealing.seals(hereState, direction)) {
                    continue;
                }
                if (!level.isLoaded(next)) {
                    tried.addAll(air);
                    return false;
                }
                BlockState nextState = level.getBlockState(next);
                if (isAirlockDoor(nextState)) {
                    // another door of this space: it ends the space whether open or not
                    found |= isOpen(nextState);
                    continue;
                }
                if (Sealing.seals(nextState, direction.getOpposite())) {
                    continue;
                }
                air.add(next.asLong());
                if (air.size() > limit) {
                    tried.addAll(air);
                    return false; // a large room, not an airlock
                }
                queue.enqueue(next.asLong());
            }
        }
        tried.addAll(air);
        return found;
    }

    private static boolean fullySealed(BlockState state) {
        for (Direction direction : Direction.values()) {
            if (!Sealing.seals(state, direction)) {
                return false;
            }
        }
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getUseBlock() == TriState.FALSE) {
            return;
        }
        if (!mayOpen(player.level(), event.getPos())) {
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            player.displayClientMessage(Component.translatable("message.projectrebirth.airlock.blocked"), true);
        }
    }
}
