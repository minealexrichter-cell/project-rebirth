package com.projectrebirth.technology.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Shared energy helpers for the mod's blocks. */
public final class EnergyUtil {
    private EnergyUtil() {
    }

    /**
     * Gives energy from the buffer to the blocks on the given sides (this mod's cable, stations, other mods' machines
     * and cables), at most {@code limit} in total; returns what was given.
     */
    public static int push(Level level, BlockPos pos, ModEnergyStorage from, int limit, Direction... sides) {
        int left = Math.min(limit, from.getEnergyStored());
        int given = 0;
        for (Direction side : sides) {
            if (left <= 0) {
                break;
            }
            IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(side), side.getOpposite());
            if (target == null || !target.canReceive()) {
                continue;
            }
            int accepted = target.receiveEnergy(left, false);
            if (accepted > 0) {
                from.take(accepted, false);
                left -= accepted;
                given += accepted;
            }
        }
        return given;
    }

    /** A number that does not fit in a menu data slot (16 bits), split into two. */
    public static int low(int value) {
        return value & 0xFFFF;
    }

    public static int high(int value) {
        return value >>> 16;
    }

    public static int join(int low, int high) {
        return (high & 0xFFFF) << 16 | low & 0xFFFF;
    }

    /** "12 345 FE"-style readable number. */
    public static String format(int energy) {
        return String.format(java.util.Locale.ROOT, "%,d", energy).replace(',', ' ');
    }
}
