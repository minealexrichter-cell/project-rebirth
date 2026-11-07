package com.projectrebirth.technology.energy;

import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * A life support block (air filtration, later the airlock, decontamination and the shelter panel). The cable feeds these
 * first: production machines on the same network wait while any of them is short. A backup battery feeds only these.
 */
public interface LifeSupport {
    /** Its energy buffer. */
    IEnergyStorage lifeSupportEnergy();

    /** Whether it is charged enough that other machines may have energy too. */
    static boolean charged(IEnergyStorage energy, double share) {
        return energy.getMaxEnergyStored() <= 0 || energy.getEnergyStored() >= energy.getMaxEnergyStored() * share;
    }
}
