package com.projectrebirth.technology.energy;

import java.util.function.IntSupplier;

import net.minecraft.util.Mth;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * An energy buffer (NeoForge Energy, FE) whose size and transfer limits come from the config, so config changes
 * reach existing blocks. {@link #receiveEnergy}/{@link #extractEnergy} are what other blocks and mods see;
 * {@link #generate}/{@link #consume} are the owner's own production and use.
 */
public class ModEnergyStorage implements IEnergyStorage {
    private final IntSupplier capacity;
    private final IntSupplier maxReceive;
    private final IntSupplier maxExtract;
    private final Runnable onChanged;
    private int energy;

    /** A limit of 0 means that side of the buffer is closed (for example a generator takes nothing in). */
    public ModEnergyStorage(IntSupplier capacity, IntSupplier maxReceive, IntSupplier maxExtract, Runnable onChanged) {
        this.capacity = capacity;
        this.maxReceive = maxReceive;
        this.maxExtract = maxExtract;
        this.onChanged = onChanged;
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        return canReceive() ? add(Math.min(toReceive, maxReceive.getAsInt()), simulate) : 0;
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        return canExtract() ? take(Math.min(toExtract, maxExtract.getAsInt()), simulate) : 0;
    }

    /** The owner's own production; returns what fitted. */
    public int generate(int amount) {
        return add(amount, false);
    }

    /** Takes exactly this much if it is there; returns whether it was. */
    public boolean consume(int amount) {
        if (energy < amount) {
            return false;
        }
        take(amount, false);
        return true;
    }

    /** The owner hands energy on (for example a generator to its neighbours); returns what was taken. */
    public int take(int amount, boolean simulate) {
        int taken = Mth.clamp(amount, 0, energy);
        if (!simulate && taken > 0) {
            energy -= taken;
            onChanged.run();
        }
        return taken;
    }

    private int add(int amount, boolean simulate) {
        int added = Mth.clamp(amount, 0, Math.max(0, getMaxEnergyStored() - energy));
        if (!simulate && added > 0) {
            energy += added;
            onChanged.run();
        }
        return added;
    }

    @Override
    public int getEnergyStored() {
        return Math.min(energy, getMaxEnergyStored());
    }

    @Override
    public int getMaxEnergyStored() {
        return capacity.getAsInt();
    }

    public boolean isFull() {
        return getEnergyStored() >= getMaxEnergyStored();
    }

    @Override
    public boolean canExtract() {
        return maxExtract.getAsInt() > 0;
    }

    @Override
    public boolean canReceive() {
        return maxReceive.getAsInt() > 0;
    }

    /** Saved as a plain number; a smaller capacity in the config cuts it down when the block loads. */
    public int saved() {
        return energy;
    }

    public void load(int saved) {
        energy = Math.max(0, saved);
    }

    /** One side of this buffer for other blocks: only in or only out. */
    public IEnergyStorage inputOnly() {
        return new View(true, false);
    }

    public IEnergyStorage outputOnly() {
        return new View(false, true);
    }

    private final class View implements IEnergyStorage {
        private final boolean in;
        private final boolean out;

        View(boolean in, boolean out) {
            this.in = in;
            this.out = out;
        }

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            return in ? ModEnergyStorage.this.receiveEnergy(toReceive, simulate) : 0;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return out ? ModEnergyStorage.this.extractEnergy(toExtract, simulate) : 0;
        }

        @Override
        public int getEnergyStored() {
            return ModEnergyStorage.this.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return ModEnergyStorage.this.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return out && ModEnergyStorage.this.canExtract();
        }

        @Override
        public boolean canReceive() {
            return in && ModEnergyStorage.this.canReceive();
        }
    }
}
