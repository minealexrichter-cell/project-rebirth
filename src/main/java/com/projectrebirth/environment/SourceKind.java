package com.projectrebirth.environment;

import com.projectrebirth.config.RebirthConfig;

/**
 * The kinds of block that radiate or pollute around themselves (1.6.0); the numbers are read from the config each time, so a
 * change reaches blocks that are already placed. Uranium ore is not here: it has no block entity and is looked for around the person.
 */
public enum SourceKind {
    /** Radioactive debris: strong radiation. */
    DEBRIS,
    /** Leaking waste barrel: polluted air. */
    WASTE_BARREL,
    /** Radioisotope generator: weaker radiation. */
    GENERATOR,
    /** Irradiated sludge in the contamination zone (large structures of 1.6.0): weaker radiation than debris, shorter reach. */
    SLUDGE;

    public boolean enabled() {
        return this != GENERATOR || RebirthConfig.ISOTOPE_RADIATES.get();
    }

    /** Radiation right next to the block. */
    public float radiation() {
        return switch (this) {
            case DEBRIS -> RebirthConfig.DEBRIS_RADIATION.get().floatValue();
            case GENERATOR -> RebirthConfig.ISOTOPE_RADIATION.get().floatValue();
            case SLUDGE -> RebirthConfig.SLUDGE_RADIATION.get().floatValue();
            case WASTE_BARREL -> 0.0f;
        };
    }

    /** Polluted air right next to the block. */
    public float toxin() {
        return this == WASTE_BARREL ? RebirthConfig.BARREL_TOXIN.get().floatValue() : 0.0f;
    }

    /** How far it reaches, in blocks. */
    public int range() {
        return switch (this) {
            case DEBRIS -> RebirthConfig.DEBRIS_RANGE.get();
            case WASTE_BARREL -> RebirthConfig.BARREL_RANGE.get();
            case GENERATOR -> RebirthConfig.ISOTOPE_RANGE.get();
            case SLUDGE -> RebirthConfig.SLUDGE_RANGE.get();
        };
    }
}
