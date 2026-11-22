package com.projectrebirth.logistics;

import java.util.EnumMap;
import java.util.Map;

import com.projectrebirth.multiblock.FrameBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 1.8.0: a roof panel of the storage (carbon). Loose, or while the storage is not whole: a full flat block. Over a whole storage
 * the controller ({@link StorageControllerBlockEntity}) sets its part and facing; the panel itself is then not drawn — the
 * storage's renderer draws one smooth hip roof over all of them (StorageRenderer) — and its shape follows the slope in four steps
 * (an edge down toward its facing, a corner down toward its facing and the side left of it), so one walks up the roof as up
 * stairs.
 */
public class StorageRoofBlock extends Block {
    public enum Part implements StringRepresentable {
        TOP, EDGE, CORNER;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final Map<Direction, VoxelShape> EDGES = new EnumMap<>(Direction.class);
    private static final Map<Direction, VoxelShape> CORNERS = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape edge = Shapes.empty(), corner = Shapes.empty();
            for (int i = 0; i < 4; i++) {
                // in the model's frame (low side north, a corner's also west), then turned to the facing
                edge = Shapes.or(edge, turned(facing, 0, i * 4, i * 4, 16, i * 4 + 4, 16));
                corner = Shapes.or(corner, turned(facing, i * 4, i * 4, i * 4, 16, i * 4 + 4, 16));
            }
            EDGES.put(facing, edge);
            CORNERS.put(facing, corner);
        }
    }

    /** A box in pixels of the north model turned round the Y axis as the block states turn the models (clockwise from above). */
    private static VoxelShape turned(Direction facing, double x0, double y0, double z0, double x1, double y1, double z1) {
        double ax = x0, az = z0, bx = x1, bz = z1;
        int turns = switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        for (int i = 0; i < turns; i++) {
            // clockwise seen from above: (x, z) -> (16 - z, x)
            double nax = 16 - bz, nbx = 16 - az, naz = ax, nbz = bx;
            ax = nax;
            bx = nbx;
            az = naz;
            bz = nbz;
        }
        return Block.box(Math.min(ax, bx), y0, Math.min(az, bz), Math.max(ax, bx), y1, Math.max(az, bz));
    }

    public StorageRoofBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FrameBlock.FORMED, false).setValue(PART, Part.TOP).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FrameBlock.FORMED, PART, FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(FrameBlock.FORMED)) {
            return Shapes.block();
        }
        return switch (state.getValue(PART)) {
            case EDGE -> EDGES.get(state.getValue(FACING));
            case CORNER -> CORNERS.get(state.getValue(FACING));
            default -> Shapes.block();
        };
    }

    @Override
    protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return state.getValue(FrameBlock.FORMED) ? net.minecraft.world.level.block.RenderShape.INVISIBLE : net.minecraft.world.level.block.RenderShape.MODEL;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }
}
