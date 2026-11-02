package com.projectrebirth.config;

import java.util.EnumMap;
import java.util.Map;

import com.projectrebirth.bodies.BodyClass;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side balance config (config/projectrebirth-server.toml).
 */
public final class RebirthConfig {
    public static final ModConfigSpec SERVER_SPEC;
    private static final Map<BodyClass, BodyStats> BODY_STATS = new EnumMap<>(BodyClass.class);

    public static final ModConfigSpec.IntValue TRANSFER_RADIUS;
    public static final ModConfigSpec.IntValue TRANSFER_COOLDOWN_SECONDS;
    public static final ModConfigSpec.IntValue EMERGENCY_RETURN_INVULNERABILITY_SECONDS;
    public static final ModConfigSpec.BooleanValue HUMAN_BODY_VULNERABLE;
    public static final ModConfigSpec.BooleanValue BACKUP_BODIES_ENABLED;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Consciousness transfer rules").push("transfer");
        TRANSFER_RADIUS = builder
                .comment("Maximum distance in blocks between the transfer capsule and the target body")
                .defineInRange("radius", 16, 1, 128);
        TRANSFER_COOLDOWN_SECONDS = builder
                .comment("Delay in seconds between two transfers of the same player")
                .defineInRange("cooldownSeconds", 5, 0, 3600);
        EMERGENCY_RETURN_INVULNERABILITY_SECONDS = builder
                .comment("Invulnerability in seconds after consciousness returns because the controlled body was destroyed.",
                        "Prevents dying again instantly from the same danger.")
                .defineInRange("emergencyReturnInvulnerabilitySeconds", 3, 0, 60);
        HUMAN_BODY_VULNERABLE = builder
                .comment("Whether the human body left in the capsule can be damaged and killed")
                .define("humanBodyVulnerable", true);
        BACKUP_BODIES_ENABLED = builder
                .comment("If the human body is dead, consciousness moves to another loaded unoccupied body of the same owner")
                .define("backupBodiesEnabled", true);
        builder.pop();

        builder.comment("Artificial body stats, one section per body class").push("bodies");
        for (BodyClass bodyClass : BodyClass.values()) {
            builder.push(bodyClass.id());
            ModConfigSpec.DoubleValue maxHealth = builder
                    .comment("Maximum integrity (health points) of the body")
                    .defineInRange("maxHealth", bodyClass.defaultMaxHealth(), 1.0, 1024.0);
            ModConfigSpec.DoubleValue armor = builder
                    .comment("Armor points of the body")
                    .defineInRange("armor", bodyClass.defaultArmor(), 0.0, 30.0);
            ModConfigSpec.DoubleValue speed = builder
                    .comment("Movement speed multiplier while controlling the body (1.0 = human speed)")
                    .defineInRange("speedMultiplier", bodyClass.defaultSpeedMultiplier(), 0.1, 3.0);
            ModConfigSpec.DoubleValue attack = builder
                    .comment("Extra melee damage while controlling the body")
                    .defineInRange("attackDamageBonus", bodyClass.defaultAttackDamageBonus(), 0.0, 100.0);
            builder.pop();
            BODY_STATS.put(bodyClass, new BodyStats(maxHealth, armor, speed, attack));
        }
        builder.pop();
        SERVER_SPEC = builder.build();
    }

    private RebirthConfig() {
    }

    public static BodyStats bodyStats(BodyClass bodyClass) {
        return BODY_STATS.get(bodyClass);
    }

    public record BodyStats(ModConfigSpec.DoubleValue maxHealth, ModConfigSpec.DoubleValue armor,
                            ModConfigSpec.DoubleValue speedMultiplier, ModConfigSpec.DoubleValue attackDamageBonus) {
    }
}
