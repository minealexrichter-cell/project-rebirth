package com.projectrebirth.garrison;

import net.minecraft.world.entity.Entity;

/**
 * A machine of a player (1.7.0): an android (an artificial body run by an AI core), a turret or the mech. The faction rules, the
 * register, the limits and the rules of targets know machines by this.
 */
public interface GarrisonMachine {
    MachineCore machineCore();

    default Entity asEntity() {
        return (Entity) this;
    }
}
