package com.projectrebirth.bodies;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

/**
 * Classes of artificial bodies, from cheapest to best. All classes share the same entity, item and
 * transfer logic; they differ only by id (models, textures, translations) and by config-driven stats.
 * The values here are only defaults for the server config.
 */
public enum BodyClass implements StringRepresentable {
    BASIC_ROBOT(0, "basic_robot_body", false, 20.0, 2.0, 0.85, 0.0),
    IMPROVED_ROBOT(1, "improved_robot_body", false, 26.0, 4.0, 0.95, 0.5),
    ADVANCED_SYNTHETIC(2, "advanced_synthetic_body", true, 30.0, 6.0, 1.0, 1.0),
    HIGHTECH_SYNTHETIC(3, "hightech_synthetic_body", true, 36.0, 8.0, 1.1, 2.0),
    PREMIUM_SYNTHETIC(4, "premium_synthetic_body", true, 40.0, 10.0, 1.2, 3.0);

    public static final Codec<BodyClass> CODEC = StringRepresentable.fromEnum(BodyClass::values);
    public static final StreamCodec<ByteBuf, BodyClass> STREAM_CODEC = ByteBufCodecs.idMapper(
            ByIdMap.continuous(BodyClass::networkId, values(), ByIdMap.OutOfBoundsStrategy.ZERO), BodyClass::networkId);

    private final int networkId;
    private final String id;
    private final boolean slimArms;
    private final double defaultMaxHealth;
    private final double defaultArmor;
    private final double defaultSpeedMultiplier;
    private final double defaultAttackDamageBonus;

    BodyClass(int networkId, String id, boolean slimArms, double defaultMaxHealth, double defaultArmor,
              double defaultSpeedMultiplier, double defaultAttackDamageBonus) {
        this.networkId = networkId;
        this.id = id;
        this.slimArms = slimArms;
        this.defaultMaxHealth = defaultMaxHealth;
        this.defaultArmor = defaultArmor;
        this.defaultSpeedMultiplier = defaultSpeedMultiplier;
        this.defaultAttackDamageBonus = defaultAttackDamageBonus;
    }

    private int networkId() {
        return networkId;
    }

    public String id() {
        return id;
    }

    /** 3-pixel arms like the slim player skin; the model texture uses the player skin layout for arms. */
    public boolean slimArms() {
        return slimArms;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public String translationKey() {
        return "entity.projectrebirth." + id;
    }

    public double defaultMaxHealth() {
        return defaultMaxHealth;
    }

    public double defaultArmor() {
        return defaultArmor;
    }

    public double defaultSpeedMultiplier() {
        return defaultSpeedMultiplier;
    }

    public double defaultAttackDamageBonus() {
        return defaultAttackDamageBonus;
    }
}
