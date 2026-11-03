package com.projectrebirth.technology.energy;

import javax.annotation.Nullable;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** What a generator, battery or another mod sees on a side of a cable: it takes energy and passes it on at once. */
public class CableInput implements IEnergyStorage {
    private final Level level;
    private final BlockPos pos;
    @Nullable
    private final Direction side;

    public CableInput(Level level, BlockPos pos, @Nullable Direction side) {
        this.level = level;
        this.pos = pos;
        this.side = side;
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        if (level.isClientSide || toReceive <= 0) {
            return 0;
        }
        BlockPos source = side == null ? null : pos.relative(side);
        return CableNetwork.of(level, pos).deliver(level, source, toReceive, simulate);
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return 0;
    }

    @Override
    public int getMaxEnergyStored() {
        return RebirthConfig.CABLE_TRANSFER.get();
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return true;
    }
}
