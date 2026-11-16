package com.projectrebirth.multiblock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A frame of a multiblock hall (1.7.0). While its construction is whole and the plan draws a hall over it
 * ({@link MultiblockSchema.Shell}), the controller marks it FORMED and it is not drawn: the hall's walls stand in its place. It
 * stays a solid block all the time; broken, the construction falls apart and the other frames show again.
 */
public class FrameBlock extends Block {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public FrameBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    protected float getShadeBrightness(BlockState state, net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos) {
        return 1.0f;
    }
}
