package com.projectrebirth.environment;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/** What the environment does at a place. A later threat is one more value here; everything else goes through the same calculation. */
public enum Hazard implements StringRepresentable {
    /** Polluted air: reaches whoever breathes it. */
    POLLUTION("pollution"),
    /** Radiation: passes through roofs. */
    RADIATION("radiation"),
    /** Acid falling from the sky: a roof stops it. */
    ACID("acid");

    public static final Codec<Hazard> CODEC = StringRepresentable.fromEnum(Hazard::values);

    private final String id;

    Hazard(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
