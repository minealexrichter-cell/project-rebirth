package com.projectrebirth.shelter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An air filtration unit in the world. The room itself lives in {@link Shelters}; the unit only tells it that it is here and
 * keeps the pollution of the room's air over a restart.
 */
public class AirFiltrationUnitBlockEntity extends BlockEntity {
    private static final String POLLUTION = "room_pollution";
    /** NaN: never in a room yet. */
    private float pollution = Float.NaN;

    public AirFiltrationUnitBlockEntity(BlockPos pos, BlockState state) {
        super(ShelterRegistry.AIR_FILTRATION_UNIT_ENTITY.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            Shelters.of(server).addUnit(worldPosition, pollution);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel server) {
            Shelters found = Shelters.find(server);
            if (found != null) {
                found.removeUnit(worldPosition);
            }
        }
    }

    void remember(float roomPollution) {
        if (Float.compare(pollution, roomPollution) != 0) {
            pollution = roomPollution;
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!Float.isNaN(pollution)) {
            tag.putFloat(POLLUTION, pollution);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        pollution = tag.contains(POLLUTION) ? tag.getFloat(POLLUTION) : Float.NaN;
    }
}
