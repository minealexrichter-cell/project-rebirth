package com.projectrebirth.structures;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The emergency lamp (1.6.0): a red glass dome over a bulb, fixed to a wall, standing on a block or on a fence post (its sleeve
 * fits round the post), or hanging from a ceiling. It holds on to anything solid, a fence or a wall block; with nothing to
 * hold on to it drops.
 */
public class EmergencyLampBlock extends FaceAttachedHorizontalDirectionalBlock {
    public static final MapCodec<EmergencyLampBlock> CODEC = simpleCodec(EmergencyLampBlock::new);
    private static final VoxelShape FLOOR = Block.box(5, 0, 5, 11, 7.5, 11);
    private static final VoxelShape CEILING = Block.box(5, 8.5, 5, 11, 16, 11);
    private static final Map<Direction, VoxelShape> WALL = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            WALL.put(facing, DecorBlock.turned(facing, 4.5, 4, 8.5, 11.5, 12, 16));
        }
    }

    public EmergencyLampBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FACE, AttachFace.WALL));
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> FLOOR;
            case CEILING -> CEILING;
            default -> WALL.get(state.getValue(FACING));
        };
    }

    /** A fence post, a wall block or anything solid holds the lamp, not only a full face. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction toSupport = getConnectedDirection(state).getOpposite();
        BlockPos supportPos = pos.relative(toSupport);
        BlockState support = level.getBlockState(supportPos);
        return support.is(BlockTags.FENCES) || support.is(BlockTags.WALLS)
                || Block.canSupportCenter(level, supportPos, toSupport.getOpposite())
                || support.isFaceSturdy(level, supportPos, toSupport.getOpposite())
                || support.isSolid();
    }
}
