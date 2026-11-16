package com.projectrebirth.upgrades;

/**
 * The kinds of upgrade slots: bodies have a number of them, armors have one named slot per hull part, machines (1.7.0: turrets,
 * the mech) a number of slots that take armor upgrades.
 */
public sealed interface UpgradeLayout {
    record Counted(int slots) implements UpgradeLayout {
    }

    /** A machine: so many slots, each for one armor upgrade (any hull part), one of each kind. */
    record MachineSlots(int slots) implements UpgradeLayout {
    }

    enum ArmorParts implements UpgradeLayout {
        INSTANCE
    }
}
