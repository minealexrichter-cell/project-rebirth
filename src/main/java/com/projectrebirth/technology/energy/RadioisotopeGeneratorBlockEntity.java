package com.projectrebirth.technology.energy;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.RadiationSources;
import com.projectrebirth.environment.SourceKind;
import com.projectrebirth.technology.TechBlockEntities;
import com.projectrebirth.technology.TechSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The radioisotope generator (1.6.0): makes energy all the time without fuel, gives it to every neighbour and cable, and radiates
 * around itself (it is in the index of {@link RadiationSources} while it is loaded).
 */
public class RadioisotopeGeneratorBlockEntity extends BlockEntity {
    private final ModEnergyStorage energy = new ModEnergyStorage(() -> RebirthConfig.ISOTOPE_CAPACITY.get(),
            () -> 0, () -> RebirthConfig.GENERATOR_OUTPUT.get(), this::setChanged);
    private int lastSignal = -1;

    public RadioisotopeGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(TechBlockEntities.RADIOISOTOPE_GENERATOR.get(), pos, state);
    }

    public ModEnergyStorage energy() {
        return energy;
    }

    /** Comparator signal 0..15 by charge. */
    public int signal() {
        int capacity = energy.getMaxEnergyStored();
        return capacity <= 0 || energy.getEnergyStored() == 0 ? 0 : 1 + Mth.floor(14.0f * energy.getEnergyStored() / capacity);
    }

    void serverTick() {
        energy.generate(RebirthConfig.ISOTOPE_ENERGY_PER_TICK.get());
        EnergyUtil.push(level, worldPosition, energy, RebirthConfig.GENERATOR_OUTPUT.get(), Direction.values());
        // lit while it still has room for what it makes; checked once a second so that the block state does not flicker
        if (level.getGameTime() % 20 == 0) {
            TechSounds.setWorking(this, !energy.isFull());
        }
        int signal = signal();
        if (signal != lastSignal) {
            lastSignal = signal;
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            RadiationSources.register(server, worldPosition, SourceKind.GENERATOR);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel server) {
            RadiationSources.unregister(server, worldPosition);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("energy", energy.saved());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.load(tag.getInt("energy"));
    }
}
