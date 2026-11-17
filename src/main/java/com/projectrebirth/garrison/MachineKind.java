package com.projectrebirth.garrison;

import java.util.Locale;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.util.StringRepresentable;

/**
 * The kinds of the player's machines (1.7.0): limits, integrity and upgrade slots come from the config by kind
 * ([garrison.machines.&lt;kind&gt;]). TEST is the machine of the automated tests only (its entity is never in a played world).
 */
public enum MachineKind implements StringRepresentable {
    ANDROID("an artificial body run by an AI core"),
    TURRET("a defence turret"),
    MECH("the guard mech"),
    ROBOT("the small robot (1.7.1)"),
    TEST("the stand-in machine of the automated tests");

    private final String description;

    MachineKind(String description) {
        this.description = description;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String description() {
        return description;
    }

    public float maxStructural() {
        return RebirthConfig.MACHINE_MAX_STRUCTURAL.get(this).get().floatValue();
    }

    public float maxCritical() {
        return RebirthConfig.MACHINE_MAX_CRITICAL.get(this).get().floatValue();
    }

    public int upgradeSlots() {
        return RebirthConfig.MACHINE_UPGRADE_SLOTS.get(this).get();
    }

    public int playerLimit() {
        return RebirthConfig.MACHINE_PLAYER_LIMIT.get(this).get();
    }

    public int factionLimit() {
        return RebirthConfig.MACHINE_FACTION_LIMIT.get(this).get();
    }

    public String translationKey() {
        return "machine_kind.projectrebirth." + id();
    }

    @Override
    public String getSerializedName() {
        return id();
    }

    public static MachineKind byId(String id) {
        for (MachineKind kind : values()) {
            if (kind.id().equals(id)) {
                return kind;
            }
        }
        return TEST;
    }
}
