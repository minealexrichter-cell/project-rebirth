package com.projectrebirth.structures;

import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A turret of the ruins (1.6.0), three blocks: the base on the ground, the tower over it, and the barrel in front of the tower
 * (on the side the turret looks to). Placed, it puts up all three; broken, any part takes the others with it, and only the base
 * drops the item. Decoration for now: it does not shoot. The whole one and the broken one share this block class.
 */
public class TurretBlock extends HorizontalDirectionalBlock {
    public enum Part implements StringRepresentable {
        BASE, TOWER, BARREL;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final MapCodec<TurretBlock> CODEC = simpleCodec(TurretBlock::new);

    private static final VoxelShape BASE = Block.box(0.5, 0, 0.5, 15.5, 16, 15.5);
    private static final Map<Direction, VoxelShape> TOWER = new EnumMap<>(Direction.class);
    private static final Map<Direction, VoxelShape> BARREL = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            TOWER.put(facing, Shapes.or(Block.box(5, 0, 5, 11, 3, 11), DecorBlock.turned(facing, 1.5, 3, 0, 14.5, 13.5, 15)));
            BARREL.put(facing, DecorBlock.turned(facing, 5, 4, 4, 11, 10, 16));
        }
    }

    public TurretBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.BASE));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    /** Where the other parts stand, from the base and the side it looks to. */
    public static BlockPos towerOf(BlockPos base) {
        return base.above();
    }

    public static BlockPos barrelOf(BlockPos base, Direction facing) {
        return base.above().relative(facing);
    }

    private static BlockPos baseOf(BlockState state, BlockPos pos) {
        return switch (state.getValue(PART)) {
            case BASE -> pos;
            case TOWER -> pos.below();
            case BARREL -> pos.relative(state.getValue(FACING).getOpposite()).below();
        };
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockPos base = context.getClickedPos();
        Level level = context.getLevel();
        if (base.getY() + 1 >= level.getMaxBuildHeight()
                || !level.getBlockState(towerOf(base)).canBeReplaced(context)
                || !level.getBlockState(barrelOf(base, facing)).canBeReplaced(context)) {
            return null; // no room for the tower and the barrel
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PART, Part.BASE);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        Direction facing = state.getValue(FACING);
        level.setBlock(towerOf(pos), state.setValue(PART, Part.TOWER), Block.UPDATE_ALL);
        level.setBlock(barrelOf(pos, facing), state.setValue(PART, Part.BARREL), Block.UPDATE_ALL);
    }

    /** A part whose neighbour part is gone goes too, as a door's half does. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction facing = state.getValue(FACING);
        Direction toPart = switch (state.getValue(PART)) {
            case BASE -> direction == Direction.UP ? Direction.UP : null;
            case TOWER -> direction == Direction.DOWN || direction == facing ? direction : null;
            case BARREL -> direction == facing.getOpposite() ? direction : null;
        };
        if (toPart != null && !(neighbor.is(this) && neighbor.getValue(FACING) == facing)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    /** Broken from the tower or the barrel, the turret drops from its base (once) like a door broken from its top half. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && state.getValue(PART) != Part.BASE) {
            BlockPos base = baseOf(state, pos);
            BlockState baseState = level.getBlockState(base);
            if (baseState.is(this)) {
                if (player.isCreative()) {
                    level.setBlock(base, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                } else {
                    level.destroyBlock(base, true, player);
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(PART)) {
            case BASE -> BASE;
            case TOWER -> TOWER.get(state.getValue(FACING));
            case BARREL -> BARREL.get(state.getValue(FACING));
        };
    }
}
