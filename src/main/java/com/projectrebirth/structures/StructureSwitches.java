package com.projectrebirth.structures;

import com.projectrebirth.config.RebirthConfig;

/** The config switches of the structures of 1.6.0: all of them at once, and each kind on its own. */
public final class StructureSwitches {
    public static final String LAB = "abandoned_lab";
    public static final String RUINS = "industrial_ruins";
    public static final String REACTOR = "ruined_reactor";
    public static final String MILITARY = "military_base";
    public static final String SCIENCE = "science_complex";
    public static final String FACTORY = "abandoned_factory";
    public static final String COMM = "comm_station";
    public static final String OUTPOST = "defensive_outpost";
    public static final String ZONE = "contamination_zone";

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
            case MILITARY -> RebirthConfig.STRUCTURES_MILITARY.get();
            case SCIENCE -> RebirthConfig.STRUCTURES_SCIENCE.get();
            case FACTORY -> RebirthConfig.STRUCTURES_FACTORY.get();
            case COMM -> RebirthConfig.STRUCTURES_COMM.get();
            case OUTPOST -> RebirthConfig.STRUCTURES_OUTPOST.get();
            case ZONE -> RebirthConfig.STRUCTURES_ZONE.get();
            default -> true;
        };
    }
}
