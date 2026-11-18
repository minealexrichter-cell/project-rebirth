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
 * The alarm lamp (1.6.0): a red glass column over a bulb in a cage on a dark housing. It stands on a block, a fence or a stone
 * wall, hangs under one, or is fixed to the side of one; on the side of a post (a fence or any stone wall) it gets a bracket
 * reaching to the post ({@link #POST}). It holds on to anything solid, a fence or a wall; with nothing to hold on to it drops.
 */
public class EmergencyLampBlock extends FaceAttachedHorizontalDirectionalBlock {
    public static final MapCodec<EmergencyLampBlock> CODEC = simpleCodec(EmergencyLampBlock::new);
    /** On the side of a thin post (a fence or a stone wall): the model has a bracket reaching across to it. */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty POST =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("post");
    // 1.7.3, block G: a small lamp, 6 by 6 and half a block high (it was nearly a whole block)
    private static final VoxelShape FLOOR = Block.box(5, 0, 5, 11, 8.25, 11);
    private static final VoxelShape CEILING = Block.box(5, 7.75, 5, 11, 16, 11);
    private static final Map<Direction, VoxelShape> WALL = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            WALL.put(facing, DecorBlock.turned(facing, 5, 5, 7.75, 11, 11, 16));
        }
    }

    public EmergencyLampBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FACE, AttachFace.WALL).setValue(POST, false));
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, POST);
    }

    /** Whether the block holding the lamp is a thin post: a fence or a stone wall of any kind. */
    public static boolean isPost(BlockState support) {
        return support.is(BlockTags.FENCES) || support.is(BlockTags.WALLS);
    }

    private BlockState withPost(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
        boolean post = state.getValue(FACE) == AttachFace.WALL && isPost(level.getBlockState(pos.relative(getConnectedDirection(state).getOpposite())));
        return state.setValue(POST, post);
    }

    @javax.annotation.Nullable
    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : withPost(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, net.minecraft.world.level.LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        BlockState updated = super.updateShape(state, direction, neighbor, level, pos, neighborPos);
        return updated.is(this) ? withPost(updated, level, pos) : updated;
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
