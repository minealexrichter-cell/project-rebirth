package com.projectrebirth.bodies;

import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Two blocks tall consciousness transfer capsule. The front is open so the human body standing inside
 * stays visible and can be hit. Using the capsule moves the player's consciousness into their nearest body.
 */
public class TransferCapsuleBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<TransferCapsuleBlock> CODEC = simpleCodec(TransferCapsuleBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    // Boxes for a north-facing capsule (open side at z = 0): floor, back wall, two front pillars, roof.
    private static final double[][] LOWER_BOXES = {{0, 0, 0, 16, 2, 16}, {0, 2, 14, 16, 16, 16}, {0, 2, 0, 2, 16, 2}, {14, 2, 0, 16, 16, 2}};
    private static final double[][] UPPER_BOXES = {{0, 0, 14, 16, 14, 16}, {0, 14, 0, 16, 16, 16}, {0, 0, 0, 2, 14, 2}, {14, 0, 0, 16, 14, 2}};
    private static final Map<Direction, VoxelShape> LOWER_SHAPES = rotatedShapes(LOWER_BOXES);
    private static final Map<Direction, VoxelShape> UPPER_SHAPES = rotatedShapes(UPPER_BOXES);

    public TransferCapsuleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Map<Direction, VoxelShape> shapes = state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPES : UPPER_SHAPES;
        return shapes.get(state.getValue(FACING));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(pos.above()).canBeReplaced(context)) {
            return defaultBlockState()
                    .setValue(FACING, context.getHorizontalDirection().getOpposite())
                    .setValue(HALF, DoubleBlockHalf.LOWER);
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP)) {
            return neighborState.is(this) && neighborState.getValue(HALF) != half
                    ? state.setValue(FACING, neighborState.getValue(FACING))
                    : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
        return true;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Same as doors: in creative, breaking the top half must not drop the item from the bottom half.
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);
            if (belowState.is(this) && belowState.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, below, Block.getId(belowState));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer) {
            BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
            TransferService.TransferResult result = TransferService.transferFromCapsule(serverPlayer, lower, state.getValue(FACING));
            serverPlayer.displayClientMessage(result.message(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static Map<Direction, VoxelShape> rotatedShapes(double[][] northBoxes) {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : northBoxes) {
                double[] r = rotate(b, facing);
                shape = Shapes.or(shape, Block.box(r[0], r[1], r[2], r[3], r[4], r[5]));
            }
            shapes.put(facing, shape);
        }
        return shapes;
    }

    /** Rotates a north-facing box clockwise (seen from above) the same way blockstate "y" rotation does. */
    private static double[] rotate(double[] b, Direction facing) {
        double x1 = b[0], y1 = b[1], z1 = b[2], x2 = b[3], y2 = b[4], z2 = b[5];
        return switch (facing) {
            case EAST -> new double[]{16 - z2, y1, x1, 16 - z1, y2, x2};
            case SOUTH -> new double[]{16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1};
            case WEST -> new double[]{z1, y1, 16 - x2, z2, y2, 16 - x1};
            default -> b;
        };
    }
}
