package com.projectrebirth.technology;

/**
 * Metals mined from new ores. Each one has a stone ore, a deepslate ore, a raw material and an ingot; their
 * models, recipes, tags and world generation live in data files named after {@link #id()}.
 */
public enum Metal {
    /** Alloyed with copper into bronze for actuators. */
    TIN("tin"),
    /** Light frames of the robot bodies. */
    ALUMINUM("aluminum"),
    /** Conductor for electronics. */
    SILVER("silver"),
    /** Energy cells. */
    LITHIUM("lithium"),
    /** Armor plates and high-end body frames. */
    TITANIUM("titanium"),
    /** Radioactive (1.6.0): radiates in the ground and in an inventory; the radioisotope generator. */
    URANIUM("uranium");

    private final String id;

    Metal(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
