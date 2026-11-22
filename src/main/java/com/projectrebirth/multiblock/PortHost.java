package com.projectrebirth.multiblock;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * 1.8.0, block J: what a port of a multiblock serves: the controller of an assembler, or of the storage (logistics). A port gives
 * what its host gives; the tracker tells a host when a block of its construction changes.
 */
public interface PortHost {
    /** Whether this port is one of its construction now. */
    boolean serves(BlockPos port);

    /** The energy an energy port takes in, or null. */
    @Nullable
    IEnergyStorage portEnergy();

    /** What a cargo port puts in and takes out, or null. */
    @Nullable
    IItemHandler portItems();

    /** A block of its construction changed: it looks again on its next tick. */
    void markDirty();
}
