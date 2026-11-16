package com.projectrebirth.environment;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The toxic puddle a glowing ghoul leaves when it dies (1.6.0, part B3c): a thin film of green slime that pollutes the air around
 * it (a source like the waste barrel) and dries up by itself after a while (config). It lies only on solid ground, goes when its
 * ground goes and drops nothing.
 */
public class ToxicPuddleBlock extends SourceBlock {
    public ToxicPuddleBlock(Properties properties) {
        super(properties, SourceKind.TOXIC_PUDDLE);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(this)) {
            int life = RebirthConfig.SERVER_SPEC.isLoaded() ? RebirthConfig.PUDDLE_LIFETIME.get() : 3600;
            level.scheduleTick(pos, this, life + level.random.nextInt(Math.max(1, life / 5)));
        }
    }

    /** Dried up. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.removeBlock(pos, false);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }
}
