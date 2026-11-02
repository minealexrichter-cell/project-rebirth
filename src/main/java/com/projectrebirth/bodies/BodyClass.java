package com.projectrebirth.bodies;

/**
 * Classes of artificial bodies. All classes share the same entity and item logic;
 * they differ only by id (models, textures, translations) and by config-driven stats.
 * The values here are only defaults for the server config.
 */
public enum BodyClass {
    BASIC_ROBOT("basic_robot_body", 20.0, 2.0);

    private final String id;
    private final double defaultMaxHealth;
    private final double defaultArmor;

    BodyClass(String id, double defaultMaxHealth, double defaultArmor) {
        this.id = id;
        this.defaultMaxHealth = defaultMaxHealth;
        this.defaultArmor = defaultArmor;
    }

    public String id() {
        return id;
    }

    public double defaultMaxHealth() {
        return defaultMaxHealth;
    }

    public double defaultArmor() {
        return defaultArmor;
    }
}
