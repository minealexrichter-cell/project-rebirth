package com.projectrebirth.powerarmor;

import javax.annotation.Nullable;

/**
 * Power armor variants. They share all the armor logic; they differ only by id (item, model, animations, textures,
 * translation) and by config-driven stats, plus the worker armor's 3x3 mining. The values here are only defaults for the server config.
 */
public enum ArmorVariant {
    /** The armor of 0.1.0: armors saved before variants existed load as this one. */
    COMBAT("combat", "power_armor", 100.0, 50.0, 0.12, 6.0, 0.6, 3.0),
    MOBILE("mobile", "mobile_power_armor", 70.0, 35.0, 0.18, 4.0, 1.0, 15.0),
    WORKER("worker", "worker_power_armor", 60.0, 30.0, 0.09, 2.0, 0.4, 3.0);

    private final String id;
    private final String itemId;
    private final double defaultMaxStructural;
    private final double defaultMaxCritical;
    private final double defaultMovementSpeed;
    private final double defaultPilotAttackBonus;
    private final double defaultJumpStrength;
    private final double defaultSafeFall;

    ArmorVariant(String id, String itemId, double defaultMaxStructural, double defaultMaxCritical, double defaultMovementSpeed,
                 double defaultPilotAttackBonus, double defaultJumpStrength, double defaultSafeFall) {
        this.id = id;
        this.itemId = itemId;
        this.defaultMaxStructural = defaultMaxStructural;
        this.defaultMaxCritical = defaultMaxCritical;
        this.defaultMovementSpeed = defaultMovementSpeed;
        this.defaultPilotAttackBonus = defaultPilotAttackBonus;
        this.defaultJumpStrength = defaultJumpStrength;
        this.defaultSafeFall = defaultSafeFall;
    }

    /** Used in the config, in saves and in translation keys. */
    /**
     * Whether the armor visibly opens to let its pilot in and out (1.5.2). Only the combat armor has a model with the moving
     * parts yet; the others will get theirs, and then only this needs to change.
     */
    public boolean opensShell() {
        return this == COMBAT;
    }

    public String id() {
        return id;
    }

    /** The item (and recipe) id; the combat armor keeps the 0.1.0 id "power_armor". */
    public String itemId() {
        return itemId;
    }

    public String translationKey() {
        return "armor_variant.projectrebirth." + id;
    }

    public double defaultMaxStructural() {
        return defaultMaxStructural;
    }

    public double defaultMaxCritical() {
        return defaultMaxCritical;
    }

    public double defaultMovementSpeed() {
        return defaultMovementSpeed;
    }

    public double defaultPilotAttackBonus() {
        return defaultPilotAttackBonus;
    }

    public double defaultJumpStrength() {
        return defaultJumpStrength;
    }

    /** Blocks the armor can fall without damage; the mobile armor must survive the landing of its own high jump. */
    public double defaultSafeFall() {
        return defaultSafeFall;
    }

    /** The item of this variant's hotbar ability slot (see {@link ArmorAbility}). */
    public String abilityItemId() {
        return id + "_armor_ability";
    }

    /** The variant saved under this id; unknown or missing ids (0.1.0 saves) are the combat armor. */
    public static ArmorVariant byId(@Nullable String id) {
        for (ArmorVariant variant : values()) {
            if (variant.id.equals(id)) {
                return variant;
            }
        }
        return COMBAT;
    }
}
