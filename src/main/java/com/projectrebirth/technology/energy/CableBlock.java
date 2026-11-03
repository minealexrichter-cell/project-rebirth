package com.projectrebirth.technology.energy;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * Energy cable. It connects to other cables and to any block with an energy side (this mod's machines and other
 * mods' machines and cables). It stores nothing: energy pushed into it goes straight to the machines of its network
 * ({@link CableNetwork}).
 */
public class CableBlock extends PipeBlock {
    public static final MapCodec<CableBlock> CODEC = simpleCodec(CableBlock::new);

    public CableBlock(Properties properties) {
        // 0.1875 = a 6 px thick cable
        super(0.1875f, properties);
        BlockState state = stateDefinition.any();
        for (Direction direction : Direction.values()) {
            state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), false);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends PipeBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction direction : Direction.values()) {
            state = state.setValue(PROPERTY_BY_DIRECTION.get(direction),
                    connects(context.getLevel(), context.getClickedPos().relative(direction), direction));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (level instanceof Level realLevel) {
            CableNetwork.invalidate(realLevel);
        }
        return state.setValue(PROPERTY_BY_DIRECTION.get(direction), connects(level, neighborPos, direction));
    }

    /** A machine placed or removed next to the cable does not always change its shape; the network must notice anyway. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        CableNetwork.invalidate(level);
        for (Direction direction : Direction.values()) {
            if (pos.relative(direction).equals(neighborPos)) {
                boolean connected = connects(level, neighborPos, direction);
                if (state.getValue(PROPERTY_BY_DIRECTION.get(direction)) != connected) {
                    level.setBlock(pos, state.setValue(PROPERTY_BY_DIRECTION.get(direction), connected), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        CableNetwork.invalidate(level);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        CableNetwork.invalidate(level);
    }

    private static boolean connects(LevelAccessor level, BlockPos neighborPos, Direction direction) {
        if (level.getBlockState(neighborPos).getBlock() instanceof CableBlock) {
            return true;
        }
        return level instanceof Level realLevel
                && realLevel.getCapability(Capabilities.EnergyStorage.BLOCK, neighborPos, direction.getOpposite()) != null;
    }
}
