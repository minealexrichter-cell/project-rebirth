package com.projectrebirth.structures;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A decoration block of the large structures (1.6.0): it only stands there and looks the way it is turned. The shape is given
 * for a block looking north (in pixels) and turned for the other sides. A wall-mounted one (monitor, lamp) takes the side of the
 * wall it is put on; the others turn toward the person who places them.
 */
public class DecorBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<DecorBlock> CODEC = simpleCodec(properties -> new DecorBlock(properties, 0, 0, 0, 16, 16, 16, false));

    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
    private final boolean wallMounted;

    public DecorBlock(Properties properties, double x1, double y1, double z1, double x2, double y2, double z2, boolean wallMounted) {
        super(properties);
        this.wallMounted = wallMounted;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            shapes.put(facing, turned(facing, x1, y1, z1, x2, y2, z2));
        }
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /** The north box turned toward a side, seen from above clockwise: east takes x = 16 - z, z = x. */
    static VoxelShape turned(Direction facing, double x1, double y1, double z1, double x2, double y2, double z2) {
        return switch (facing) {
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            default -> Block.box(x1, y1, z1, x2, y2, z2);
        };
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        Direction facing = wallMounted && clicked.getAxis().isHorizontal() ? clicked : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.getOrDefault(state.getValue(FACING), Shapes.block());
    }
}
