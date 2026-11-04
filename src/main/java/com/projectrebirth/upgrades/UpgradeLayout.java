package com.projectrebirth.upgrades;

/** The two kinds of upgrade slots: bodies have a number of them, armors have one named slot per hull part. */
public sealed interface UpgradeLayout {
    record Counted(int slots) implements UpgradeLayout {
    }

    enum ArmorParts implements UpgradeLayout {
        INSTANCE
    }
}
