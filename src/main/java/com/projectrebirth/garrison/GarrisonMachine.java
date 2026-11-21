package com.projectrebirth.garrison;

import java.util.List;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * A machine of a player (1.7.0): an android (an artificial body run by an AI core), a turret or the mech. The faction rules, the
 * register, the limits and the rules of targets know machines by this.
 */
public interface GarrisonMachine {
    MachineCore machineCore();

    default Entity asEntity() {
        return (Entity) this;
    }

    /** 1.8.0: its charge or energy as a share (0..1) for the console's cards, or -1 when it has none of its own (a turret). */
    default float consoleEnergy() {
        return -1.0f;
    }

    /** 1.8.0: the rounds it can fire now (its magazine, or its mount's), or -1 when it uses none. */
    default int consoleAmmo() {
        return -1;
    }

    /** 1.8.0: how far it shoots (a turret's circle on the console's map), or 0. */
    default int consoleRange() {
        return 0;
    }

    /** 1.8.0: whether it does work orders (a work android). */
    default boolean consoleWorker() {
        return false;
    }

    /** 1.8.0: the upgrades installed in it, for the machine's window of the console. */
    default List<ItemStack> consoleUpgrades() {
        return List.of();
    }
}
