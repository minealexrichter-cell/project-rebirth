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

    public static final ModConfigSpec.DoubleValue ARMOR_MAX_STRUCTURAL;
    public static final ModConfigSpec.DoubleValue ARMOR_MAX_CRITICAL;
    public static final ModConfigSpec.DoubleValue ARMOR_STRUCTURAL_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue ARMOR_CRITICAL_EXPLOSION_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue ARMOR_CRITICAL_FIRE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue ARMOR_CRITICAL_OTHER_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue ARMOR_STRUCTURAL_REGEN_PER_SECOND;
    public static final ModConfigSpec.IntValue ARMOR_STRUCTURAL_REGEN_DELAY_SECONDS;
    public static final ModConfigSpec.DoubleValue ARMOR_RESTART_FRACTION;
    public static final ModConfigSpec.IntValue ARMOR_HATCH_RELEASE_SECONDS;
    public static final ModConfigSpec.DoubleValue ARMOR_HATCH_CRITICAL_COST;
    public static final ModConfigSpec.DoubleValue ARMOR_WRECK_PILOT_DAMAGE;
    public static final ModConfigSpec.DoubleValue ARMOR_REPAIR_STRUCTURAL_PER_INGOT;
    public static final ModConfigSpec.DoubleValue ARMOR_REPAIR_CRITICAL_PER_PLATE;
    public static final ModConfigSpec.DoubleValue ARMOR_MOVEMENT_SPEED;
    public static final ModConfigSpec.DoubleValue ARMOR_PILOT_ATTACK_BONUS;
    public static final ModConfigSpec.DoubleValue ARMOR_PILOT_KNOCKBACK_BONUS;
    public static final ModConfigSpec.DoubleValue ARMOR_PILOT_ENTITY_REACH_BONUS;
    public static final ModConfigSpec.DoubleValue ARMOR_PILOT_BLOCK_REACH_BONUS;
    public static final ModConfigSpec.DoubleValue ARMOR_JUMP_STRENGTH;
    public static final ModConfigSpec.IntValue ARMOR_DISMANTLE_IRON;
    public static final ModConfigSpec.IntValue ARMOR_DISMANTLE_PLATES;

    public static final ModConfigSpec.DoubleValue PRODUCTION_TIME_MULTIPLIER;
    public static final ModConfigSpec.IntValue MAINTENANCE_STEP_TICKS;
    public static final ModConfigSpec.DoubleValue MAINTENANCE_BODY_REPAIR_PER_ITEM;
    public static final ModConfigSpec.BooleanValue FACTION_FRIENDLY_FIRE_DEFAULT;
    public static final ModConfigSpec.BooleanValue FACTION_LEADER_CAN_TOGGLE_FRIENDLY_FIRE;
    public static final ModConfigSpec.IntValue FACTION_MAX_MEMBERS;
    public static final ModConfigSpec.IntValue FACTION_CORE_RADIUS;
    public static final ModConfigSpec.IntValue FACTION_MAX_CORES;
    public static final ModConfigSpec.IntValue FACTION_WAR_PREPARATION_MINUTES;
    public static final ModConfigSpec.IntValue FACTION_WAR_MAX_HOURS;

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

        builder.comment("Power armor").push("power_armor");
        ARMOR_MAX_STRUCTURAL = builder
                .comment("Structural integrity: current working condition. At 0 the armor shuts down and locks the pilot in.")
                .defineInRange("maxStructural", 100.0, 1.0, 10000.0);
        ARMOR_MAX_CRITICAL = builder
                .comment("Critical integrity: irreversible damage to key components. At 0 the armor becomes an unrepairable wreck.")
                .defineInRange("maxCritical", 50.0, 1.0, 10000.0);
        ARMOR_STRUCTURAL_DAMAGE_MULTIPLIER = builder
                .comment("Structural integrity lost per point of incoming damage")
                .defineInRange("structuralDamageMultiplier", 1.0, 0.0, 100.0);
        ARMOR_CRITICAL_EXPLOSION_MULTIPLIER = builder
                .comment("Critical integrity lost per point of explosion damage")
                .defineInRange("criticalExplosionMultiplier", 0.5, 0.0, 100.0);
        ARMOR_CRITICAL_FIRE_MULTIPLIER = builder
                .comment("Critical integrity lost per point of fire and lava damage")
                .defineInRange("criticalFireMultiplier", 0.3, 0.0, 100.0);
        ARMOR_CRITICAL_OTHER_MULTIPLIER = builder
                .comment("Critical integrity lost per point of any other damage")
                .defineInRange("criticalOtherMultiplier", 0.05, 0.0, 100.0);
        ARMOR_STRUCTURAL_REGEN_PER_SECOND = builder
                .comment("Structural integrity restored per second while the armor works and is not being damaged (0 = no regeneration)")
                .defineInRange("structuralRegenPerSecond", 1.0, 0.0, 1000.0);
        ARMOR_STRUCTURAL_REGEN_DELAY_SECONDS = builder
                .comment("Seconds without damage before structural regeneration starts")
                .defineInRange("structuralRegenDelaySeconds", 6, 0, 3600);
        ARMOR_RESTART_FRACTION = builder
                .comment("Share of max structural integrity a shut-down armor needs (through repair) to start again")
                .defineInRange("restartStructuralFraction", 0.25, 0.01, 1.0);
        ARMOR_HATCH_RELEASE_SECONDS = builder
                .comment("How long the pilot must hold the emergency hatch key to get out of a shut-down armor")
                .defineInRange("hatchReleaseSeconds", 5, 0, 600);
        ARMOR_HATCH_CRITICAL_COST = builder
                .comment("Share of max critical integrity lost when the hatch is blown from inside")
                .defineInRange("hatchCriticalCostFraction", 0.15, 0.0, 1.0);
        ARMOR_WRECK_PILOT_DAMAGE = builder
                .comment("Damage the pilot takes when the armor is wrecked with them inside")
                .defineInRange("wreckPilotDamage", 6.0, 0.0, 1000.0);
        ARMOR_REPAIR_STRUCTURAL_PER_INGOT = builder
                .comment("Structural integrity restored by one iron ingot (repair wrench)")
                .defineInRange("repairStructuralPerIngot", 15.0, 0.0, 10000.0);
        ARMOR_REPAIR_CRITICAL_PER_PLATE = builder
                .comment("Critical integrity restored by one armor plate (repair wrench)")
                .defineInRange("repairCriticalPerPlate", 10.0, 0.0, 10000.0);
        ARMOR_MOVEMENT_SPEED = builder
                .comment("Movement speed of the armor (a walking player is 0.1)")
                .defineInRange("movementSpeed", 0.12, 0.01, 2.0);
        ARMOR_PILOT_ATTACK_BONUS = builder
                .comment("Extra melee damage of the pilot while in the armor")
                .defineInRange("pilotAttackDamageBonus", 6.0, 0.0, 1000.0);
        ARMOR_PILOT_KNOCKBACK_BONUS = builder
                .comment("Extra melee knockback of the pilot while in the armor")
                .defineInRange("pilotAttackKnockbackBonus", 1.0, 0.0, 10.0);
        ARMOR_PILOT_ENTITY_REACH_BONUS = builder
                .comment("Extra reach in blocks for hitting and using entities while in the armor (a player reaches 3)")
                .defineInRange("pilotEntityReachBonus", 2.0, 0.0, 16.0);
        ARMOR_PILOT_BLOCK_REACH_BONUS = builder
                .comment("Extra reach in blocks for breaking and using blocks while in the armor (a player reaches 4.5)")
                .defineInRange("pilotBlockReachBonus", 1.5, 0.0, 16.0);
        ARMOR_JUMP_STRENGTH = builder
                .comment("Jump strength of the armor at full charge (0.42 is a player's jump, about 1.25 blocks; 0 disables jumping)")
                .defineInRange("jumpStrength", 0.6, 0.0, 3.0);
        ARMOR_DISMANTLE_IRON = builder
                .comment("Iron ingots recovered when dismantling a wreck")
                .defineInRange("dismantleIronIngots", 6, 0, 64);
        ARMOR_DISMANTLE_PLATES = builder
                .comment("Armor plates recovered when dismantling a wreck")
                .defineInRange("dismantleArmorPlates", 2, 0, 64);
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

        builder.comment("Production stations. Ingredients, results and base times of each recipe are data files",
                "(data/projectrebirth/recipe, type projectrebirth:fabrication) and can be changed with a data pack.").push("production");
        PRODUCTION_TIME_MULTIPLIER = builder
                .comment("Multiplier for the production time of every station recipe (0.5 = twice as fast)")
                .defineInRange("timeMultiplier", 1.0, 0.01, 100.0);
        builder.pop();

        builder.comment("Maintenance station: repairs packed power armor (critical integrity only here) and packed bodies.",
                "Armor repair amounts are power_armor.repairStructuralPerIngot and repairCriticalPerPlate.").push("maintenance");
        MAINTENANCE_STEP_TICKS = builder
                .comment("Ticks per repair step; every step uses one material item (20 ticks = 1 second)")
                .defineInRange("stepTicks", 40, 1, 72000);
        MAINTENANCE_BODY_REPAIR_PER_ITEM = builder
                .comment("Body integrity restored per iron ingot (robot bodies) or synthetic fiber (synthetic bodies)")
                .defineInRange("bodyRepairPerItem", 8.0, 0.1, 1024.0);
        builder.pop();

        builder.comment("Factions created by players").push("factions");
        FACTION_FRIENDLY_FIRE_DEFAULT = builder
                .comment("Whether members of a new faction can hurt each other (players, their bodies and power armor)")
                .define("friendlyFireDefault", false);
        FACTION_LEADER_CAN_TOGGLE_FRIENDLY_FIRE = builder
                .comment("Whether faction leaders may change friendly fire. If false, friendlyFireDefault applies to every faction")
                .define("leaderCanToggleFriendlyFire", true);
        FACTION_MAX_MEMBERS = builder
                .comment("Maximum members per faction, 0 = unlimited")
                .defineInRange("maxMembers", 0, 0, 1000);
        FACTION_CORE_RADIUS = builder
                .comment("Territory of a faction core: a square of this many blocks in every direction from the core, full height")
                .defineInRange("coreRadius", 24, 1, 256);
        FACTION_MAX_CORES = builder
                .comment("How many cores (territories) one faction may have")
                .defineInRange("maxCores", 1, 1, 64);
        FACTION_WAR_PREPARATION_MINUTES = builder
                .comment("Real-time minutes between a war declaration and the start of the raid rules (doors and the core of the enemy)")
                .defineInRange("warPreparationMinutes", 10, 0, 10080);
        FACTION_WAR_MAX_HOURS = builder
                .comment("A war ends by itself after this many real-time hours if no peace is made")
                .defineInRange("warMaxHours", 72, 1, 8760);
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
