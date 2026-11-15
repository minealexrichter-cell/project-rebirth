package com.projectrebirth.structures;

import com.projectrebirth.config.RebirthConfig;

/** The config switches of the structures of 1.6.0: all of them at once, and each kind on its own. */
public final class StructureSwitches {
    public static final String LAB = "abandoned_lab";
    public static final String RUINS = "industrial_ruins";
    public static final String REACTOR = "ruined_reactor";

    private StructureSwitches() {
    }

    /** Whether a structure with this switch may be placed in newly generated chunks; an unknown name follows the main switch. */
    public static boolean enabled(String key) {
        if (!RebirthConfig.SERVER_SPEC.isLoaded()) {
            return true;
        }
        if (!RebirthConfig.STRUCTURES_ENABLED.get()) {
            return false;
        }
        return switch (key) {
            case LAB -> RebirthConfig.STRUCTURES_LAB.get();
            case RUINS -> RebirthConfig.STRUCTURES_RUINS.get();
            case REACTOR -> RebirthConfig.STRUCTURES_REACTOR.get();
            default -> true;
        };
    }
}
