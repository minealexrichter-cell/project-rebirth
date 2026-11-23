package com.projectrebirth.machinemind;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/** An invisible solid block of the box of a module: what hits it hits the module. */
public class MindPartBlock extends Block {
    public MindPartBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    /** The module whose box holds this place (other than the one at {@code except}). */
    @Nullable
    public static MindModuleBlockEntity owner(Level level, BlockPos pos, @Nullable BlockPos except) {
        for (BlockPos at : BlockPos.betweenClosed(pos.offset(-2, -3, -2), pos.offset(2, 0, 2))) {
            if (at.equals(except) || !(level.getBlockState(at).getBlock() instanceof MindModuleBlock)) {
                continue;
            }
            if (level.getBlockEntity(at) instanceof MindModuleBlockEntity entity && entity.covers(pos)) {
                return entity;
            }
        }
        return null;
    }

    /** Before any drops: a living module is hurt and stays. */
    @Override
    protected void onExplosionHit(BlockState state, Level level, BlockPos pos, Explosion explosion, java.util.function.BiConsumer<net.minecraft.world.item.ItemStack, BlockPos> drops) {
        if (level.isClientSide || !MindDamage.explosion((ServerLevel) level, pos)) {
            super.onExplosionHit(state, level, pos, explosion, drops);
        }
    }
}
