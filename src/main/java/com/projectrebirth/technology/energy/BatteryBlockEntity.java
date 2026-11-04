package com.projectrebirth.technology.energy;

import javax.annotation.Nullable;

import com.projectrebirth.technology.TechSounds;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.technology.TechBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Stores energy. It takes energy in on every side but its front and gives it out only through the front, so a
 * battery between a generator and the stations never sends energy back the way it came.
 */
public class BatteryBlockEntity extends BlockEntity {
    private final ModEnergyStorage energy = new ModEnergyStorage(() -> RebirthConfig.BATTERY_CAPACITY.get(),
            () -> RebirthConfig.BATTERY_TRANSFER.get(), () -> RebirthConfig.BATTERY_TRANSFER.get(), this::setChanged);
    private final IEnergyStorage input = energy.inputOnly();
    private final IEnergyStorage output = energy.outputOnly();
    private int lastSignal = -1;
    private int lastStored = -1;

    public BatteryBlockEntity(BlockPos pos, BlockState state) {
        super(TechBlockEntities.BATTERY.get(), pos, state);
    }

    public ModEnergyStorage energy() {
        return energy;
    }

    public Direction front() {
        return getBlockState().getValue(BatteryBlock.FACING);
    }

    /** What other blocks see on a side: output at the front, input elsewhere, everything without a side. */
    public IEnergyStorage forSide(@Nullable Direction side) {
        if (side == null) {
            return energy;
        }
        return side == front() ? output : input;
    }

    /** Comparator signal 0..15 by charge. */
    public int signal() {
        int capacity = energy.getMaxEnergyStored();
        return capacity <= 0 || energy.getEnergyStored() == 0 ? 0
                : 1 + Mth.floor(14.0f * energy.getEnergyStored() / capacity);
    }

    void serverTick() {
        EnergyUtil.push(level, worldPosition, energy, RebirthConfig.BATTERY_TRANSFER.get(), front());
        // Works while its charge changes; checked once a second so that the block state does not flicker.
        if (level.getGameTime() % 20 == 0) {
            int stored = energy.getEnergyStored();
            TechSounds.setWorking(this, lastStored >= 0 && stored != lastStored);
            lastStored = stored;
        }
        int signal = signal();
        if (signal != lastSignal) {
            lastSignal = signal;
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
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
