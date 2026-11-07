package com.projectrebirth.shelter;

import java.util.IdentityHashMap;
import java.util.Map;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Which sides of a block keep air in. A side seals when the block's collision shape covers that whole face; doors and
 * hatches seal only by the tags below and only while closed; blocks tagged {@code not_sealing} (leaves) let air through.
 * All lists are block tags, so a data pack can add the blocks of other mods.
 */
public final class Sealing {
    /**
     * Closed: airtight. The airlock door and the sealed hatch of the mod. A block here that is not a door seals on all
     * sides whatever its shape: a sealed pass-through, such as the mod's cable through a wall.
     */
    public static final TagKey<Block> FULL = TagKey.create(Registries.BLOCK, ProjectRebirth.id("sealing_full"));
    /** Closed: the room counts as sealed, but each one on its border lets a little air in. Iron and copper doors. */
    public static final TagKey<Block> PARTIAL = TagKey.create(Registries.BLOCK, ProjectRebirth.id("sealing_partial"));
    /** Air passes whatever the shape: leaves. */
    public static final TagKey<Block> NOT_SEALING = TagKey.create(Registries.BLOCK, ProjectRebirth.id("not_sealing"));

    /** Six bits, one per {@link Direction#get3DDataValue()}: the faces this state covers fully. Shapes do not change. */
    private static final Map<BlockState, Byte> FULL_FACES = new IdentityHashMap<>();

    private Sealing() {
    }

    public static boolean isDoorLike(BlockState state) {
        return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock;
    }

    /** Whether a door or hatch is closed and in one of the sealing tags. */
    public static boolean isClosedSealingDoor(BlockState state) {
        return isDoorLike(state) && state.hasProperty(BlockStateProperties.OPEN) && !state.getValue(BlockStateProperties.OPEN)
                && (state.is(FULL) || state.is(PARTIAL));
    }

    /** A closed partial door or hatch: one leak each (a door counts once, by its lower half). */
    public static boolean isPartialLeak(BlockState state) {
        if (!isClosedSealingDoor(state) || !state.is(PARTIAL) || state.is(FULL)) {
            return false;
        }
        return !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                || state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER;
    }

    /** Which sides keep air in, as six bits: two states with the same signature seal alike (a machine switching on does not count). */
    public static int signature(BlockState state) {
        int bits = 0;
        for (Direction side : Direction.values()) {
            if (seals(state, side)) {
                bits |= 1 << side.get3DDataValue();
            }
        }
        return bits;
    }

    /** Whether this side of the block keeps air from passing. */
    public static boolean seals(BlockState state, Direction side) {
        if (state.isAir() || state.is(NOT_SEALING)) {
            return false;
        }
        if (isDoorLike(state)) {
            return isClosedSealingDoor(state);
        }
        if (state.is(FULL)) {
            return true;
        }
        return (fullFaces(state) & (1 << side.get3DDataValue())) != 0;
    }

    private static byte fullFaces(BlockState state) {
        Byte known = FULL_FACES.get(state);
        if (known != null) {
            return known;
        }
        VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        byte faces = 0;
        for (Direction side : Direction.values()) {
            if (Block.isFaceFull(shape, side)) {
                faces |= (byte) (1 << side.get3DDataValue());
            }
        }
        FULL_FACES.put(state, faces); // the server thread only
        return faces;
    }
}
