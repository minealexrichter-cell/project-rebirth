package com.projectrebirth.environment;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The entity of the radioactive debris and the leaking waste barrel (1.6.0). It does nothing by itself: it puts its block into the
 * index of {@link RadiationSources} when it loads and takes it out when it is removed or its chunk unloads, so a player's second
 * only looks into the index and never scans the world.
 */
public class SourceBlockEntity extends BlockEntity {
    public SourceBlockEntity(BlockPos pos, BlockState state) {
        super(SourceRegistry.SOURCE_ENTITY.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server && getBlockState().getBlock() instanceof SourceBlock block) {
            RadiationSources.register(server, worldPosition, block.kind());
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel server) {
            RadiationSources.unregister(server, worldPosition);
        }
    }
}
