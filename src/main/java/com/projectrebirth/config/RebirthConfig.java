package com.projectrebirth.config;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.environment.Hazard;
import com.projectrebirth.powerarmor.ArmorVariant;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side balance config (config/projectrebirth-server.toml).
 */
public final class RebirthConfig {
    public static final ModConfigSpec SERVER_SPEC;
    private static final Map<BodyClass, BodyStats> BODY_STATS = new EnumMap<>(BodyClass.class);
    /** From the worst body to the best. */
    // 1.5.0 block B2: acid wears the new wear scale (percent), no longer the integrity
    private static final double[] DEFAULT_ACID_WEAR = {0.05, 0.04, 0.03, 0.02, 0.01};
    // basic body about 10% wear per game day of work, premium about 3%
    private static final double[] DEFAULT_WEAR = {1.0, 0.8, 0.6, 0.45, 0.3};
    // 1.5.0: the basic body lasts about one game day of ordinary work, the premium one about four
    private static final int[] DEFAULT_ENERGY_CAPACITY = {60_000, 80_000, 100_000, 130_000, 160_000};
    private static final double[] DEFAULT_ENERGY_USE = {1.0, 0.9, 0.8, 0.7, 0.65};
    // 1.5.0 block B: better bodies cool faster
    private static final double[] DEFAULT_COOLING = {1.0, 1.15, 1.3, 1.5, 1.7};
    private static final Map<ArmorVariant, ArmorStats> ARMOR_STATS = new EnumMap<>(ArmorVariant.class);

    public static final ModConfigSpec.IntValue TRANSFER_RADIUS;
    public static final ModConfigSpec.IntValue TRANSFER_COOLDOWN_SECONDS;
    public static final ModConfigSpec.IntValue EMERGENCY_RETURN_INVULNERABILITY_SECONDS;
    public static final ModConfigSpec.BooleanValue HUMAN_BODY_VULNERABLE;
    public static final ModConfigSpec.BooleanValue BACKUP_BODIES_ENABLED;

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
    public static final ModConfigSpec.DoubleValue ARMOR_PILOT_KNOCKBACK_BONUS;
    public static final ModConfigSpec.DoubleValue ARMOR_PILOT_ENTITY_REACH_BONUS;
    public static final ModConfigSpec.DoubleValue ARMOR_PILOT_BLOCK_REACH_BONUS;
    public static final ModConfigSpec.IntValue ARMOR_DISMANTLE_IRON;
    public static final ModConfigSpec.IntValue ARMOR_DISMANTLE_PLATES;

    public static final ModConfigSpec.DoubleValue REPAIR_KIT_USE_SECONDS;
    public static final ModConfigSpec.DoubleValue REPAIR_KIT_FRACTION;
    public static ModConfigSpec.DoubleValue ARMOR_ABILITY_ATTACK_BONUS;
    public static ModConfigSpec.DoubleValue ARMOR_ABILITY_SPEED_BONUS;
    public static final ModConfigSpec.DoubleValue PRODUCTION_TIME_MULTIPLIER;
    public static final ModConfigSpec.IntValue MAINTENANCE_STEP_TICKS;
    private static final Map<String, PlatingStats> PLATING = new HashMap<>();
    private static final Map<String, ArmorPlatingStats> ARMOR_PLATING = new HashMap<>();
    private static final Map<String, WeaponStats> WEAPONS = new HashMap<>();
    public static final ModConfigSpec.IntValue WEAPON_HEAVY_HINT_INTERVAL;
    private static final Map<String, EnvironmentEventStats> ENVIRONMENT_EVENTS = new HashMap<>();
    public static final ModConfigSpec.BooleanValue ENVIRONMENT_ENABLED;
    public static final ModConfigSpec.DoubleValue ENVIRONMENT_ROOF_POLLUTION;
    public static final ModConfigSpec.DoubleValue ENVIRONMENT_ROOF_RADIATION;
    public static final ModConfigSpec.DoubleValue ENVIRONMENT_ROOF_ACID;
    public static final ModConfigSpec.IntValue ENVIRONMENT_UNDERGROUND_DEPTH;
    public static final ModConfigSpec.DoubleValue ENVIRONMENT_UNDERGROUND_POLLUTION;
    public static final ModConfigSpec.IntValue SHELTER_MAX_VOLUME_PER_UNIT;
    public static final ModConfigSpec.IntValue SHELTER_MAX_UNITS_PER_ROOM;
    public static final ModConfigSpec.IntValue SHELTER_SCAN_BLOCKS_PER_TICK;
    public static final ModConfigSpec.IntValue SHELTER_RECHECK_SECONDS;
    public static final ModConfigSpec.DoubleValue SHELTER_SEALED_RADIATION;
    public static final ModConfigSpec.DoubleValue SHELTER_SEALED_DRIFT;
    public static final ModConfigSpec.DoubleValue SHELTER_PARTIAL_DOOR_LEAK;
    public static final ModConfigSpec.DoubleValue SHELTER_LEAK_DRIFT;
    public static final ModConfigSpec.DoubleValue SHELTER_CLEAN_AIR_PER_SECOND;
    public static final ModConfigSpec.DoubleValue SHELTER_CARTRIDGE_LIFE;
    public static final ModConfigSpec.DoubleValue CONTAMINATION_MAX;
    public static final ModConfigSpec.DoubleValue CONTAMINATION_PER_POLLUTION;
    public static final ModConfigSpec.DoubleValue CONTAMINATION_PER_RADIATION;
    public static final ModConfigSpec.DoubleValue CONTAMINATION_PER_ACID;
    public static final ModConfigSpec.DoubleValue CONTAMINATION_SHED_SHARE;
    public static final ModConfigSpec.DoubleValue CONTAMINATION_AIR_PER_UNIT;
    public static final ModConfigSpec.DoubleValue CONTAMINATION_DECAY;
    public static final ModConfigSpec.DoubleValue DECON_PER_SECOND;
    public static final ModConfigSpec.DoubleValue DECON_PER_SOLUTION;
    public static final ModConfigSpec.IntValue DECON_ENERGY;
    public static final ModConfigSpec.IntValue DECON_CAPACITY;
    public static final ModConfigSpec.DoubleValue PURGE_DOSE_PER_SECOND;
    public static final ModConfigSpec.DoubleValue PURGE_MAX_POLLUTION;
    public static final ModConfigSpec.IntValue PURGE_ENERGY;
    public static final ModConfigSpec.IntValue PURGE_CAPACITY;
    public static final ModConfigSpec.IntValue PURGE_RECEIVE;
    public static final ModConfigSpec.IntValue AIRLOCK_MAX_VOLUME;
    public static final ModConfigSpec.IntValue SHELTER_ALERT_DELAY;
    public static final ModConfigSpec.DoubleValue SHELTER_CARTRIDGE_BASE_USE;
    public static final ModConfigSpec.DoubleValue SHELTER_CARTRIDGE_WORK_USE;
    public static final ModConfigSpec.IntValue SHELTER_ENERGY_BASE;
    public static final ModConfigSpec.IntValue SHELTER_ENERGY_PER_THOUSAND;
    public static final ModConfigSpec.IntValue SHELTER_ENERGY_CAPACITY;
    public static final ModConfigSpec.IntValue SHELTER_ENERGY_RECEIVE;
    public static final ModConfigSpec.IntValue ENVIRONMENT_AFTERMATH_SECONDS;
    public static final ModConfigSpec.IntValue ENVIRONMENT_MIN_CALM_SECONDS;
    public static final ModConfigSpec.IntValue ENVIRONMENT_MAX_CALM_SECONDS;
    public static final ModConfigSpec.IntValue ENVIRONMENT_WARNING_SECONDS;
    public static final ModConfigSpec.BooleanValue ENVIRONMENT_ACID_RAIN_WEATHER;
    private static final Map<String, ProtectionStats> PROTECTION_KITS = new HashMap<>();
    public static final ModConfigSpec.DoubleValue PROTECTION_HEAD_SHARE;
    public static final ModConfigSpec.DoubleValue PROTECTION_CHEST_SHARE;
    public static final ModConfigSpec.DoubleValue PROTECTION_LEGS_SHARE;
    public static final ModConfigSpec.DoubleValue PROTECTION_FEET_SHARE;
    public static final ModConfigSpec.DoubleValue PROTECTION_FILTER_LIFE;
    public static final ModConfigSpec.DoubleValue PROTECTION_FILTER_USE;
    public static final ModConfigSpec.DoubleValue PROTECTION_FILTER_SAFE_POLLUTION;
    public static final ModConfigSpec.IntValue PROTECTION_ACID_WEAR;
    public static final ModConfigSpec.IntValue PROTECTION_ACID_WEAR_INTERVAL;
    public static final ModConfigSpec.IntValue MACHINE_ACID_WEAR_INTERVAL;
    public static final ModConfigSpec.DoubleValue HUD_DANGER_POLLUTION;
    public static final ModConfigSpec.DoubleValue HUD_DANGER_RADIATION;
    public static final ModConfigSpec.DoubleValue HUD_DANGER_ACID;
    public static final ModConfigSpec.DoubleValue MACHINE_PILOT_RADIATION_PER_PLATE;
    public static final ModConfigSpec.BooleanValue MACHINE_ARMOR_ACID_WEAR;
    public static final ModConfigSpec.DoubleValue MACHINE_ARMOR_ACID_WEAR_PER_SECOND;
    public static final ModConfigSpec.DoubleValue HUMAN_DOSE_PER_RADIATION;
    public static final ModConfigSpec.DoubleValue HUMAN_DOSE_CLEARANCE;
    public static final ModConfigSpec.DoubleValue HUMAN_TOXIN_PER_POLLUTION;
    public static final ModConfigSpec.DoubleValue HUMAN_TOXIN_PER_ACID;
    public static final ModConfigSpec.DoubleValue HUMAN_TOXIN_CLEARANCE;
    public static final ModConfigSpec.DoubleValue HUMAN_MAX_DOSE;
    public static final ModConfigSpec.DoubleValue HUMAN_MAX_TOXIN;
    public static final ModConfigSpec.DoubleValue HUMAN_DOSE_WEAKNESS;
    public static final ModConfigSpec.DoubleValue HUMAN_DOSE_SLOW_HEALING;
    public static final ModConfigSpec.DoubleValue HUMAN_DOSE_MAX_HEALTH;
    public static final ModConfigSpec.DoubleValue HUMAN_DOSE_DAMAGE;
    public static final ModConfigSpec.DoubleValue HUMAN_TOXIN_NAUSEA;
    public static final ModConfigSpec.DoubleValue HUMAN_TOXIN_WEAKNESS;
    public static final ModConfigSpec.DoubleValue HUMAN_TOXIN_DAMAGE;
    public static final ModConfigSpec.DoubleValue HUMAN_WEAKNESS_SPEED_PENALTY;
    public static final ModConfigSpec.DoubleValue HUMAN_WEAKNESS_ATTACK_PENALTY;
    public static final ModConfigSpec.DoubleValue HUMAN_DOSE_HEALING_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue HUMAN_LAST_STAGE_HEALING_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue HUMAN_DOSE_HEALTH_PENALTY;
    public static final ModConfigSpec.DoubleValue HUMAN_SICKNESS_DAMAGE;
    public static final ModConfigSpec.DoubleValue HUMAN_NAUSEA_CHANCE;
    public static final ModConfigSpec.DoubleValue HUMAN_ACID_DAMAGE;
    public static final ModConfigSpec.DoubleValue HUMAN_KEPT_ON_DEATH;
    public static final ModConfigSpec.DoubleValue HUMAN_ANTIRADIN_DOSE;
    public static final ModConfigSpec.DoubleValue HUMAN_SORBENT_TOXIN;
    public static final ModConfigSpec.DoubleValue HUMAN_PILOT_RADIATION_PROTECTION;
    public static final ModConfigSpec.IntValue HUMAN_SICKNESS_DAMAGE_INTERVAL;
    public static final ModConfigSpec.IntValue HUMAN_ACID_DAMAGE_INTERVAL;
    public static final ModConfigSpec.DoubleValue WEAPON_HEAVY_WALK_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue ARMOR_JUMP_PER_AIR_MODULE;
    public static final ModConfigSpec.DoubleValue ARMOR_MAX_JUMP_STRENGTH;
    public static final ModConfigSpec.DoubleValue ARMOR_SAFE_FALL_PER_AIR_MODULE;
    public static final ModConfigSpec.BooleanValue FLOODLIGHT_ENABLED;
    public static final ModConfigSpec.IntValue FLOODLIGHT_RANGE;
    public static final ModConfigSpec.DoubleValue JETPACK_THRUST_SECONDS;
    public static final ModConfigSpec.IntValue EXTRA_BATTERY_CAPACITY;
    public static final ModConfigSpec.DoubleValue ENVIRONMENT_ROOF_DUST;
    public static final ModConfigSpec.DoubleValue HUMAN_DUST_SLOWDOWN;
    public static final ModConfigSpec.DoubleValue HUMAN_DUST_COUGH_CHANCE;
    public static final ModConfigSpec.DoubleValue HUMAN_TOXIN_PER_DUST;
    public static final ModConfigSpec.DoubleValue PROTECTION_FILTER_DUST_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue HUD_DANGER_DUST;
    public static final ModConfigSpec.DoubleValue MACHINE_BODY_DUST_WEAR;
    public static final ModConfigSpec.DoubleValue MACHINE_BODY_DUST_HEAT;
    public static final ModConfigSpec.DoubleValue MACHINE_ARMOR_DUST_WEAR_PER_SECOND;
    public static final ModConfigSpec.DoubleValue CONTAMINATION_PER_DUST;
    public static final ModConfigSpec.DoubleValue SOLAR_DUST_FACTOR;
    public static final ModConfigSpec.BooleanValue TEMP_ENABLED;
    public static final ModConfigSpec.DoubleValue TEMP_BIOME_SCALE;
    public static final ModConfigSpec.DoubleValue TEMP_BIOME_OFFSET;
    public static final ModConfigSpec.DoubleValue TEMP_NIGHT;
    public static final ModConfigSpec.IntValue TEMP_ALTITUDE_FROM;
    public static final ModConfigSpec.DoubleValue TEMP_ALTITUDE_PER_DEGREE;
    public static final ModConfigSpec.DoubleValue TEMP_RAIN;
    public static final ModConfigSpec.DoubleValue TEMP_INDOOR;
    public static final ModConfigSpec.DoubleValue TEMP_INDOOR_BLEND;
    public static final ModConfigSpec.DoubleValue TEMP_UNDERGROUND;
    public static final ModConfigSpec.DoubleValue TEMP_UNDERGROUND_BLEND;
    public static final ModConfigSpec.DoubleValue TEMP_HOT_DIMENSION;
    public static final ModConfigSpec.DoubleValue TEMP_SHELTER;
    public static final ModConfigSpec.DoubleValue TEMP_FIRE;
    public static final ModConfigSpec.DoubleValue TEMP_WARM_BLOCK;
    public static final ModConfigSpec.DoubleValue TEMP_SOURCES_MAX;
    public static final ModConfigSpec.IntValue TEMP_SOURCE_RADIUS;
    public static final ModConfigSpec.DoubleValue TEMP_COMFORT_MIN;
    public static final ModConfigSpec.DoubleValue TEMP_COMFORT_MAX;
    public static final ModConfigSpec.DoubleValue TEMP_HEAT_RATE;
    public static final ModConfigSpec.DoubleValue TEMP_COLD_RATE;
    public static final ModConfigSpec.DoubleValue TEMP_RECOVERY;
    public static final ModConfigSpec.DoubleValue TEMP_WEAKNESS;
    public static final ModConfigSpec.DoubleValue TEMP_SLOW_HEALING;
    public static final ModConfigSpec.DoubleValue TEMP_DAMAGE;
    public static final ModConfigSpec.DoubleValue TEMP_HEALING_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue TEMP_HEAT_EXHAUSTION;
    public static final ModConfigSpec.DoubleValue TEMP_COLD_EXHAUSTION_RELIEF;
    public static final ModConfigSpec.DoubleValue THERMAL_LINER_PROTECTION;
    public static final ModConfigSpec.DoubleValue HEATER_WARMTH;
    public static final ModConfigSpec.IntValue HEATER_RADIUS;
    public static final ModConfigSpec.DoubleValue HEATER_THERMOSTAT;
    public static final ModConfigSpec.IntValue HEATER_ENERGY;
    public static final ModConfigSpec.IntValue HEATER_CAPACITY;
    public static final ModConfigSpec.BooleanValue BODY_WEAR_ENABLED;
    public static final ModConfigSpec.DoubleValue WEAR_WORK;
    public static final ModConfigSpec.DoubleValue WEAR_OVERHEAT;
    public static final ModConfigSpec.DoubleValue WEAR_ENERGY_AT_25;
    public static final ModConfigSpec.DoubleValue WEAR_SLOWDOWN_AT_50;
    public static final ModConfigSpec.DoubleValue WEAR_SELF_REPAIR_AT_50;
    public static final ModConfigSpec.DoubleValue WEAR_HEAT_AT_50;
    public static final ModConfigSpec.DoubleValue WEAR_ATTACK_AT_75;
    public static final ModConfigSpec.DoubleValue WEAR_FAULTS_AT_75;
    public static final ModConfigSpec.DoubleValue WEAR_MAX_HEALTH_AT_100;
    public static final ModConfigSpec.DoubleValue WEAR_SLOWDOWN_AT_100;
    public static final ModConfigSpec.DoubleValue MAINTENANCE_BODY_WEAR_PER_ITEM;
    public static final ModConfigSpec.DoubleValue COOLING_MODULE_COOLING;
    public static final ModConfigSpec.BooleanValue BODY_HEAT_ENABLED;
    public static final ModConfigSpec.DoubleValue HEAT_SPRINT;
    public static final ModConfigSpec.DoubleValue HEAT_ATTACK;
    public static final ModConfigSpec.DoubleValue HEAT_BLOCK;
    public static final ModConfigSpec.DoubleValue HEAT_JETPACK;
    public static final ModConfigSpec.DoubleValue HEAT_HOT_BIOME;
    public static final ModConfigSpec.DoubleValue HEAT_HOT_BIOME_FROM;
    public static final ModConfigSpec.DoubleValue HEAT_NETHER;
    public static final ModConfigSpec.DoubleValue HEAT_NEAR_FIRE;
    public static final ModConfigSpec.DoubleValue HEAT_NEAR_FIRE_MAX;
    public static final ModConfigSpec.IntValue HEAT_FIRE_RADIUS;
    public static final ModConfigSpec.DoubleValue HEAT_BURNING;
    public static final ModConfigSpec.DoubleValue COOL_BASE;
    public static final ModConfigSpec.DoubleValue COOL_REST;
    public static final ModConfigSpec.DoubleValue COOL_WATER;
    public static final ModConfigSpec.DoubleValue COOL_RAIN;
    public static final ModConfigSpec.DoubleValue COOL_COLD_BIOME;
    public static final ModConfigSpec.DoubleValue COOL_COLD_BIOME_BELOW;
    public static final ModConfigSpec.DoubleValue COOL_SHADE;
    public static final ModConfigSpec.DoubleValue HEAT_WARNING;
    public static final ModConfigSpec.DoubleValue HEAT_THROTTLE;
    public static final ModConfigSpec.DoubleValue HEAT_THROTTLE_SLOWDOWN;
    public static final ModConfigSpec.DoubleValue HEAT_THROTTLE_ATTACK;
    public static final ModConfigSpec.BooleanValue BODY_ENERGY_ENABLED;
    public static final ModConfigSpec.DoubleValue BODY_ENERGY_IDLE;
    public static final ModConfigSpec.DoubleValue BODY_ENERGY_WALK;
    public static final ModConfigSpec.DoubleValue BODY_ENERGY_SPRINT;
    public static final ModConfigSpec.DoubleValue BODY_ENERGY_ATTACK;
    public static final ModConfigSpec.DoubleValue BODY_ENERGY_BLOCK;
    public static final ModConfigSpec.DoubleValue BODY_ENERGY_SELF_REPAIR;
    public static final ModConfigSpec.DoubleValue BODY_ENERGY_JETPACK;
    public static final ModConfigSpec.DoubleValue BODY_ENERGY_STANDING;
    public static final ModConfigSpec.DoubleValue BODY_ENERGY_LOW_SHARE;
    public static final ModConfigSpec.DoubleValue BODY_ENERGY_LOW_SLOWDOWN;
    public static final ModConfigSpec.DoubleValue BODY_ENERGY_EMPTY_SLOWDOWN;
    public static final ModConfigSpec.DoubleValue BODY_ENERGY_EMPTY_BREAK_SPEED;
    public static final ModConfigSpec.IntValue CHARGING_PAD_CAPACITY;
    public static final ModConfigSpec.IntValue CHARGING_PAD_RECEIVE;
    public static final ModConfigSpec.IntValue CHARGING_PAD_RATE;
    public static final ModConfigSpec.BooleanValue CAPSULE_CHARGES;
    public static final ModConfigSpec.IntValue BODY_BATTERY_CAPACITY;
    public static final ModConfigSpec.IntValue BODY_BATTERY_RECEIVE;
    public static final ModConfigSpec.DoubleValue BODY_BATTERY_USE_SECONDS;
    public static final ModConfigSpec.DoubleValue JETPACK_RECHARGE_SECONDS;
    public static final ModConfigSpec.DoubleValue JETPACK_RISE_SPEED;
    public static final ModConfigSpec.IntValue JETPACK_MAX_HEIGHT;
    public static final ModConfigSpec.IntValue BODY_MODIFICATION_INSTALL_TICKS;
    public static final ModConfigSpec.IntValue BODY_MODIFICATION_ENERGY_PER_TICK;
    public static final ModConfigSpec.DoubleValue MAINTENANCE_BODY_REPAIR_PER_ITEM;
    public static final ModConfigSpec.BooleanValue STATIONS_NEED_ENERGY;
    public static final ModConfigSpec.DoubleValue LIFE_SUPPORT_CHARGED_SHARE;
    public static final ModConfigSpec.DoubleValue BACKUP_START_SHARE;
    public static final ModConfigSpec.IntValue BACKUP_CAPACITY;
    public static final ModConfigSpec.IntValue BACKUP_TRANSFER;
    public static final ModConfigSpec.IntValue STATION_ENERGY_CAPACITY;
    public static final ModConfigSpec.IntValue STATION_ENERGY_PER_TICK;
    public static final ModConfigSpec.IntValue MAINTENANCE_ENERGY_PER_TICK;
    public static final ModConfigSpec.IntValue COMBUSTION_ENERGY_PER_TICK;
    public static final ModConfigSpec.IntValue COMBUSTION_CAPACITY;
    public static final ModConfigSpec.IntValue SOLAR_ENERGY_PER_TICK;
    public static final ModConfigSpec.IntValue SOLAR_CAPACITY;
    public static final ModConfigSpec.IntValue BATTERY_CAPACITY;
    public static final ModConfigSpec.IntValue BATTERY_TRANSFER;
    public static final ModConfigSpec.IntValue GENERATOR_OUTPUT;
    public static final ModConfigSpec.IntValue CABLE_TRANSFER;
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
        ARMOR_PILOT_KNOCKBACK_BONUS = builder
                .comment("Extra melee knockback of the pilot while in the armor")
                .defineInRange("pilotAttackKnockbackBonus", 1.0, 0.0, 10.0);
        ARMOR_PILOT_ENTITY_REACH_BONUS = builder
                .comment("Extra reach in blocks for hitting and using entities while in the armor (a player reaches 3)")
                .defineInRange("pilotEntityReachBonus", 2.0, 0.0, 16.0);
        ARMOR_PILOT_BLOCK_REACH_BONUS = builder
                .comment("Extra reach in blocks for breaking and using blocks while in the armor (a player reaches 4.5)")
                .defineInRange("pilotBlockReachBonus", 1.5, 0.0, 16.0);
        ARMOR_DISMANTLE_IRON = builder
                .comment("Iron ingots recovered when dismantling a wreck")
                .defineInRange("dismantleIronIngots", 6, 0, 64);
        ARMOR_DISMANTLE_PLATES = builder
                .comment("Armor plates recovered when dismantling a wreck")
                .defineInRange("dismantleArmorPlates", 2, 0, 64);
        for (ArmorVariant variant : ArmorVariant.values()) {
            builder.comment("Stats of the " + variant.id() + " armor").push(variant.id());
            ModConfigSpec.DoubleValue maxStructural = builder
                    .comment("Structural integrity: current working condition. At 0 the armor shuts down and locks the pilot in.")
                    .defineInRange("maxStructural", variant.defaultMaxStructural(), 1.0, 10000.0);
            ModConfigSpec.DoubleValue maxCritical = builder
                    .comment("Critical integrity: irreversible damage to key components. At 0 the armor becomes an unrepairable wreck.")
                    .defineInRange("maxCritical", variant.defaultMaxCritical(), 1.0, 10000.0);
            ModConfigSpec.DoubleValue speed = builder
                    .comment("Movement speed of the armor (a walking player is 0.1)")
                    .defineInRange("movementSpeed", variant.defaultMovementSpeed(), 0.01, 2.0);
            ModConfigSpec.DoubleValue attack = builder
                    .comment("Extra melee damage of the pilot while in the armor")
                    .defineInRange("pilotAttackDamageBonus", variant.defaultPilotAttackBonus(), 0.0, 1000.0);
            ModConfigSpec.DoubleValue jump = builder
                    .comment("Jump strength at full charge (0.42 is a player's jump, about 1.25 blocks; 0 disables jumping)")
                    .defineInRange("jumpStrength", variant.defaultJumpStrength(), 0.0, 3.0);
            ModConfigSpec.DoubleValue safeFall = builder
                    .comment("Blocks the armor can fall without damage (fall damage goes to structural integrity)")
                    .defineInRange("safeFallDistance", variant.defaultSafeFall(), 0.0, 256.0);
            if (variant == ArmorVariant.COMBAT) {
                ARMOR_ABILITY_ATTACK_BONUS = builder
                        .comment("Extra damage of a punch while the armor's ability slot (hotbar slot 9) is selected, on top of pilotAttackDamageBonus")
                        .defineInRange("abilityAttackBonus", 6.0, 0.0, 1000.0);
            } else if (variant == ArmorVariant.MOBILE) {
                ARMOR_ABILITY_SPEED_BONUS = builder
                        .comment("Extra speed while the armor's ability slot (hotbar slot 9) is selected: 0.4 = 40% faster than movementSpeed")
                        .defineInRange("abilitySpeedBonus", 0.4, 0.0, 10.0);
            }
            builder.pop();
            ARMOR_STATS.put(variant, new ArmorStats(maxStructural, maxCritical, speed, attack, jump, safeFall));
        }
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
            ModConfigSpec.DoubleValue selfRepair = builder
                    .comment("Integrity restored per second once the body has not been damaged for selfRepairDelaySeconds; 0 = no self-repair")
                    .defineInRange("selfRepairPerSecond", bodyClass.defaultSelfRepairPerSecond(), 0.0, 100.0);
            ModConfigSpec.IntValue selfRepairDelay = builder
                    .comment("Seconds without damage before self-repair starts")
                    .defineInRange("selfRepairDelaySeconds", 8, 0, 3600);
            ModConfigSpec.BooleanValue canConsume = builder
                    .comment("Whether the body may eat and drink the items of the tag projectrebirth:body_consumables",
                            "(golden apples and potions by default). Other food and drinks are never allowed")
                    .define("canConsume", bodyClass.defaultLivingBody());
            ModConfigSpec.BooleanValue immuneToEffects = builder
                    .comment("Whether no status effects at all apply to the body (splash and lingering potions, beacons, mobs)")
                    .define("immuneToEffects", !bodyClass.defaultLivingBody());
            ModConfigSpec.DoubleValue acidWear = builder
                    .comment("Wear (percent) acid rain adds per second per unit of acid to a body without a sealing module (1.5.0: acid no",
                            "longer takes integrity); better bodies wear more slowly")
                    .defineInRange("acidWearPercentPerSecond", DEFAULT_ACID_WEAR[bodyClass.ordinal()], 0.0, 100.0);
            ModConfigSpec.DoubleValue wear = builder
                    .comment("Wear from work and overheating (see [body_wear]) is multiplied by this: better bodies wear more slowly")
                    .defineInRange("wearMultiplier", DEFAULT_WEAR[bodyClass.ordinal()], 0.0, 100.0);
            ModConfigSpec.IntValue upgradeSlots = builder
                    .comment("Upgrade slots of the body. If lowered, what is already installed stays, but nothing more fits")
                    .defineInRange("upgradeSlots", bodyClass.defaultUpgradeSlots(), 0, 16);
            ModConfigSpec.IntValue energyCapacity = builder
                    .comment("Charge of a full body, in FE (since 1.5.0, see [body_energy])")
                    .defineInRange("energyCapacity", DEFAULT_ENERGY_CAPACITY[bodyClass.ordinal()], 1, 100_000_000);
            ModConfigSpec.DoubleValue energyUse = builder
                    .comment("Everything the body spends of its charge is multiplied by this: better bodies are more economical")
                    .defineInRange("energyUseMultiplier", DEFAULT_ENERGY_USE[bodyClass.ordinal()], 0.0, 100.0);
            ModConfigSpec.DoubleValue cooling = builder
                    .comment("All cooling of the body (see [body_heat]) is multiplied by this: better bodies cool faster")
                    .defineInRange("coolingMultiplier", DEFAULT_COOLING[bodyClass.ordinal()], 0.0, 100.0);
            builder.pop();
            BODY_STATS.put(bodyClass, new BodyStats(maxHealth, armor, speed, attack, selfRepair, selfRepairDelay, canConsume, immuneToEffects,
                    upgradeSlots, acidWear, energyCapacity, energyUse, cooling, wear));
        }
        builder.pop();

        builder.comment("Repair kit: used by a player in any artificial body, interrupted by damage").push("repair_kit");
        REPAIR_KIT_USE_SECONDS = builder
                .comment("How long the kit takes to use")
                .defineInRange("useSeconds", 4.0, 0.25, 60.0);
        REPAIR_KIT_FRACTION = builder
                .comment("Part of the body's maximum integrity restored by one kit (0.35 = 35%)")
                .defineInRange("repairFraction", 0.35, 0.01, 1.0);
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
        MAINTENANCE_BODY_WEAR_PER_ITEM = builder
                .comment("Wear (percent) taken off a body per actuator (robot bodies) or circuit board (synthetic bodies), 1.5.0")
                .defineInRange("bodyWearPerItem", 10.0, 0.1, 100.0);
        MAINTENANCE_BODY_REPAIR_PER_ITEM = builder
                .comment("Body integrity restored per iron ingot (robot bodies) or synthetic fiber (synthetic bodies)")
                .defineInRange("bodyRepairPerItem", 8.0, 0.1, 1024.0);
        builder.pop();

        builder.comment("Body modification station: installs gadgets into packed bodies. Taking one out is instant and free.").push("body_modification");
        BODY_MODIFICATION_INSTALL_TICKS = builder
                .comment("Ticks to install one gadget (20 ticks = 1 second)")
                .defineInRange("installTicks", 100, 1, 72000);
        BODY_MODIFICATION_ENERGY_PER_TICK = builder
                .comment("Energy used per tick while installing; none if energy.stationsNeedEnergy is false")
                .defineInRange("energyPerTick", 20, 0, 1_000_000);
        builder.pop();

        builder.comment("The hostile environment. Off: the mod works as if it were not there").push("environment");
        ENVIRONMENT_ENABLED = builder.comment("The whole system").define("enabled", true);
        // 1.4.0: a new name, so that worlds from 1.3.0 get the weaker roof too; only a sealed room protects fully
        ENVIRONMENT_ROOF_POLLUTION = builder.comment("Pollution is multiplied by this under a roof that is not a sealed room")
                .defineInRange("unsealedRoofPollutionMultiplier", 0.75, 0.0, 1.0);
        ENVIRONMENT_ROOF_RADIATION = builder.comment("Radiation is multiplied by this under a roof")
                .defineInRange("roofRadiationMultiplier", 1.0, 0.0, 1.0);
        ENVIRONMENT_ROOF_ACID = builder.comment("Acid is multiplied by this under a roof")
                .defineInRange("roofAcidMultiplier", 0.0, 0.0, 1.0);
        ENVIRONMENT_UNDERGROUND_DEPTH = builder.comment("Blocks below the surface where a place counts as underground")
                .defineInRange("undergroundDepth", 16, 1, 512);
        ENVIRONMENT_ROOF_DUST = builder.comment("Dust is multiplied by this under a roof (1.5.0); deep underground and in a sealed room there is none")
                .defineInRange("roofDust", 0.3, 0.0, 1.0);
        ENVIRONMENT_UNDERGROUND_POLLUTION = builder.comment("Pollution is also multiplied by this underground")
                .defineInRange("undergroundPollutionMultiplier", 0.5, 0.0, 1.0);
        ENVIRONMENT_AFTERMATH_SECONDS = builder.comment("Seconds a catastrophe takes to fade out after its active phase")
                .defineInRange("aftermathSeconds", 60, 1, 86400);
        ENVIRONMENT_MIN_CALM_SECONDS = builder.comment("Shortest calm between two catastrophes, in seconds. A new world also waits this long at least")
                .defineInRange("minCalmSeconds", 2400, 1, 10_000_000);
        ENVIRONMENT_MAX_CALM_SECONDS = builder.comment("Longest calm between two catastrophes, in seconds")
                .defineInRange("maxCalmSeconds", 4800, 1, 10_000_000);
        ENVIRONMENT_WARNING_SECONDS = builder.comment("Seconds between the warning and the start of a catastrophe")
                .defineInRange("warningSeconds", 120, 0, 86400);
        ENVIRONMENT_ACID_RAIN_WEATHER = builder.comment("Whether acid rain makes it rain in the dimension (where the biome has rain)")
                .define("acidRainWeather", true);
        builder.comment("What the environment does to a human body. Amounts are per second, for hazard strength 1").push("human");
        HUMAN_DOSE_PER_RADIATION = builder.comment("Dose gained from radiation").defineInRange("dosePerRadiation", 0.004, 0.0, 100.0);
        HUMAN_DOSE_CLEARANCE = builder.comment("Dose the body clears by itself").defineInRange("doseClearance", 0.002, 0.0, 100.0);
        HUMAN_TOXIN_PER_POLLUTION = builder.comment("Toxic load gained from polluted air").defineInRange("toxinPerPollution", 0.02, 0.0, 100.0);
        HUMAN_TOXIN_PER_ACID = builder.comment("Toxic load gained from acid").defineInRange("toxinPerAcid", 0.2, 0.0, 100.0);
        HUMAN_TOXIN_CLEARANCE = builder.comment("Toxic load the body clears by itself").defineInRange("toxinClearance", 0.015, 0.0, 100.0);
        HUMAN_MAX_DOSE = builder.comment("The highest dose").defineInRange("maxDose", 500.0, 1.0, 100000.0);
        HUMAN_MAX_TOXIN = builder.comment("The highest toxic load").defineInRange("maxToxin", 100.0, 1.0, 100000.0);
        HUMAN_DOSE_WEAKNESS = builder.comment("Dose from which the person is weak").defineInRange("doseWeakness", 50.0, 0.0, 100000.0);
        HUMAN_DOSE_SLOW_HEALING = builder.comment("Dose from which healing is slower").defineInRange("doseSlowHealing", 100.0, 0.0, 100000.0);
        HUMAN_DOSE_MAX_HEALTH = builder.comment("Dose from which maximum health is lower").defineInRange("doseMaxHealth", 200.0, 0.0, 100000.0);
        HUMAN_DOSE_DAMAGE = builder.comment("Dose from which it hurts").defineInRange("doseDamage", 300.0, 0.0, 100000.0);
        HUMAN_TOXIN_NAUSEA = builder.comment("Toxic load from which the person feels sick").defineInRange("toxinNausea", 30.0, 0.0, 100000.0);
        HUMAN_TOXIN_WEAKNESS = builder.comment("Toxic load from which the person is weak").defineInRange("toxinWeakness", 60.0, 0.0, 100000.0);
        HUMAN_TOXIN_DAMAGE = builder.comment("Toxic load from which it hurts").defineInRange("toxinDamage", 90.0, 0.0, 100000.0);
        HUMAN_WEAKNESS_SPEED_PENALTY = builder.comment("Share of walking speed lost to weakness; radiation and toxins each take it").defineInRange("weaknessSpeedPenalty", 0.1, 0.0, 1.0);
        HUMAN_WEAKNESS_ATTACK_PENALTY = builder.comment("Share of attack damage lost to weakness; radiation and toxins each take it").defineInRange("weaknessAttackPenalty", 0.15, 0.0, 1.0);
        HUMAN_DOSE_HEALING_MULTIPLIER = builder.comment("Healing is multiplied by this at a high dose").defineInRange("doseHealingMultiplier", 0.5, 0.0, 1.0);
        HUMAN_LAST_STAGE_HEALING_MULTIPLIER = builder.comment("Healing is multiplied by this at the last stage of the dose or the toxic load")
                .defineInRange("lastStageHealingMultiplier", 0.0, 0.0, 1.0);
        HUMAN_DOSE_HEALTH_PENALTY = builder.comment("Maximum health lost at a very high dose").defineInRange("doseHealthPenalty", 6.0, 0.0, 1000.0);
        HUMAN_SICKNESS_DAMAGE = builder.comment("Damage at the last stage of the dose or the toxic load").defineInRange("sicknessDamage", 1.0, 0.0, 1000.0);
        HUMAN_SICKNESS_DAMAGE_INTERVAL = builder.comment("Seconds between such damage").defineInRange("sicknessDamageInterval", 10, 1, 3600);
        HUMAN_NAUSEA_CHANCE = builder.comment("Chance per second of a fit of nausea").defineInRange("nauseaChance", 0.05, 0.0, 1.0);
        HUMAN_DUST_SLOWDOWN = builder.comment("Part of the speed lost in dust that reaches the person above environment.hud.dangerDust (1.5.0)")
                .defineInRange("dustSlowdown", 0.15, 0.0, 0.9);
        HUMAN_DUST_COUGH_CHANCE = builder.comment("Chance per second per unit of dust reaching the person of a fit of coughing (a short stronger slowdown)")
                .defineInRange("dustCoughChance", 0.02, 0.0, 1.0);
        HUMAN_TOXIN_PER_DUST = builder.comment("Toxic load gained per second per unit of dust reaching the person").defineInRange("toxinPerDust", 0.004, 0.0, 100.0);
        HUMAN_ACID_DAMAGE = builder.comment("Damage from acid rain on an unprotected person, per acid strength").defineInRange("acidDamage", 0.5, 0.0, 1000.0);
        HUMAN_ACID_DAMAGE_INTERVAL = builder.comment("Seconds between such damage").defineInRange("acidDamageInterval", 3, 1, 3600);
        HUMAN_KEPT_ON_DEATH = builder.comment("Share of the dose and the toxic load kept after death").defineInRange("keptOnDeath", 0.0, 0.0, 1.0);
        HUMAN_ANTIRADIN_DOSE = builder.comment("Dose removed by antiradin").defineInRange("antiradinDose", 80.0, 0.0, 100000.0);
        HUMAN_SORBENT_TOXIN = builder.comment("Toxic load removed by sorbent").defineInRange("sorbentToxin", 50.0, 0.0, 100000.0);
        HUMAN_PILOT_RADIATION_PROTECTION = builder.comment("Share of radiation that does not reach the pilot of a power armor").defineInRange("pilotRadiationProtection", 0.8, 0.0, 1.0);
        builder.pop();
        builder.comment("Protective gear. Breathing protection is the head piece's, while its filter lasts; acid and radiation are kept off by each worn piece by its share").push("protection");
        PROTECTION_HEAD_SHARE = builder.comment("Share of the body covered by the head piece").defineInRange("headShare", 0.2, 0.0, 1.0);
        PROTECTION_CHEST_SHARE = builder.comment("Share of the body covered by the chest piece").defineInRange("chestShare", 0.4, 0.0, 1.0);
        PROTECTION_LEGS_SHARE = builder.comment("Share of the body covered by the leg piece").defineInRange("legsShare", 0.25, 0.0, 1.0);
        PROTECTION_FEET_SHARE = builder.comment("Share of the body covered by the boots").defineInRange("feetShare", 0.15, 0.0, 1.0);
        PROTECTION_FILTER_LIFE = builder.comment("Life of one filter, in seconds of air of pollution 1").defineInRange("filterLife", 4800.0, 1.0, 1000000.0);
        PROTECTION_FILTER_SAFE_POLLUTION = builder.comment("Pollution up to which the air counts as safe and filters are not used: the ordinary background of the world")
                .defineInRange("filterSafePollution", 1.0, 0.0, 1000.0);
        PROTECTION_FILTER_DUST_MULTIPLIER = builder.comment("Dust clogs the filter this many times as fast as the same amount of polluted air (1.5.0)")
                .defineInRange("filterDustMultiplier", 3.0, 0.0, 1000.0);
        PROTECTION_FILTER_USE = builder.comment("Filter used per second per unit of pollution").defineInRange("filterUsePerPollution", 1.0, 0.0, 1000.0);
        PROTECTION_ACID_WEAR = builder.comment("Durability a piece loses to acid rain").defineInRange("acidWear", 1, 0, 1000);
        PROTECTION_ACID_WEAR_INTERVAL = builder.comment("Seconds in acid rain between such losses").defineInRange("acidWearInterval", 10, 1, 3600);
        defineProtectionKit(builder, "respirator", 0.9, 0.0, 0.0, 0.0, 0.0);
        defineProtectionKit(builder, "hazmat", 0.95, 1.0, 0.3, 0.25, 0.25);
        defineProtectionKit(builder, "heavy_suit", 1.0, 1.0, 0.85, 0.8, 0.8);
        builder.pop();

        builder.comment("Temperature (1.5.0): the temperature of a place in degrees Celsius, and the heat or cold stress of a human body,",
                "from -100 (cold) to 100 (heat). Off: no temperature, as in 1.4.1. Artificial bodies do not feel cold; heat goes into",
                "their own overheating ([body_heat]). The pilot of a power armor is protected").push("temperature");
        TEMP_ENABLED = builder.comment("The whole system (also needs environment.enabled)").define("enabled", true);
        TEMP_BIOME_SCALE = builder.comment("Degrees per unit of the biome's temperature (plains 0.8, desert and savanna 2.0, snowy plains 0.0, frozen peaks -0.7)")
                .defineInRange("biomeScale", 16.0, 0.0, 1000.0);
        TEMP_BIOME_OFFSET = builder.comment("Added to that: the temperature of a biome of temperature 0").defineInRange("biomeOffset", 8.0, -1000.0, 1000.0);
        TEMP_NIGHT = builder.comment("Degrees colder at night under the open sky (half that under a roof)").defineInRange("night", 8.0, 0.0, 1000.0);
        TEMP_ALTITUDE_FROM = builder.comment("Height above which it gets colder").defineInRange("altitudeFrom", 80, -64, 320);
        TEMP_ALTITUDE_PER_DEGREE = builder.comment("Blocks of height per degree colder above altitudeFrom").defineInRange("altitudePerDegree", 12.0, 0.1, 1000.0);
        TEMP_RAIN = builder.comment("Degrees colder in rain or snow").defineInRange("rain", 4.0, 0.0, 1000.0);
        TEMP_INDOOR = builder.comment("Under a roof the temperature moves towards this").defineInRange("indoor", 15.0, -100.0, 100.0);
        TEMP_INDOOR_BLEND = builder.comment("How far it moves under a roof (0 = not at all, 1 = all the way)").defineInRange("indoorBlend", 0.3, 0.0, 1.0);
        TEMP_UNDERGROUND = builder.comment("Deep underground the temperature moves towards this").defineInRange("underground", 12.0, -100.0, 100.0);
        TEMP_UNDERGROUND_BLEND = builder.comment("How far it moves deep underground").defineInRange("undergroundBlend", 0.7, 0.0, 1.0);
        TEMP_HOT_DIMENSION = builder.comment("The temperature of a dimension where water boils (the Nether)").defineInRange("hotDimension", 55.0, -100.0, 200.0);
        TEMP_SHELTER = builder.comment("Inside a sealed room of a shelter: its climate").defineInRange("shelter", 20.0, -100.0, 100.0);
        TEMP_FIRE = builder.comment("Degrees per block of fire, lava or magma around").defineInRange("fire", 5.0, 0.0, 1000.0);
        TEMP_WARM_BLOCK = builder.comment("Degrees per lit campfire, furnace, smoker or blast furnace around").defineInRange("warmBlock", 3.0, 0.0, 1000.0);
        TEMP_SOURCES_MAX = builder.comment("At most this many degrees from all of those").defineInRange("sourcesMax", 20.0, 0.0, 1000.0);
        TEMP_SOURCE_RADIUS = builder.comment("How far around a person they are looked for (once a second)").defineInRange("sourceRadius", 3, 0, 6);
        TEMP_COMFORT_MIN = builder.comment("Below this a person gets cold").defineInRange("comfortMin", 5.0, -100.0, 100.0);
        TEMP_COMFORT_MAX = builder.comment("Above this a person gets hot").defineInRange("comfortMax", 30.0, -100.0, 100.0);
        TEMP_HEAT_RATE = builder.comment("Heat stress per second per degree above comfortMax, before protection").defineInRange("heatRate", 0.01, 0.0, 100.0);
        TEMP_COLD_RATE = builder.comment("Cold stress per second per degree below comfortMin, before protection").defineInRange("coldRate", 0.02, 0.0, 100.0);
        TEMP_RECOVERY = builder.comment("Stress that goes away per second in comfort (also in an artificial body or a power armor)").defineInRange("recovery", 0.5, 0.0, 100.0);
        TEMP_WEAKNESS = builder.comment("Stress from which the person is weak (the speed and attack penalties of environment.human)").defineInRange("weakness", 30.0, 0.0, 100.0);
        TEMP_SLOW_HEALING = builder.comment("Stress from which healing is slower").defineInRange("slowHealing", 60.0, 0.0, 100.0);
        TEMP_DAMAGE = builder.comment("Stress from which it hurts (environment.human.sicknessDamage every sicknessDamageInterval)").defineInRange("damage", 90.0, 0.0, 100.0);
        TEMP_HEALING_MULTIPLIER = builder.comment("Healing is multiplied by this from slowHealing").defineInRange("healingMultiplier", 0.5, 0.0, 1.0);
        TEMP_HEAT_EXHAUSTION = builder.comment("Hunger exhaustion added per second while hot (from weakness)").defineInRange("heatExhaustion", 0.05, 0.0, 10.0);
        TEMP_COLD_EXHAUSTION_RELIEF = builder.comment("Hunger exhaustion taken away per second while cold (from weakness): cold slows hunger")
                .defineInRange("coldExhaustionRelief", 0.02, 0.0, 10.0);
        THERMAL_LINER_PROTECTION = builder.comment("Share of heat and cold a thermal liner keeps off, counted by the part's share like the suits")
                .defineInRange("linerProtection", 0.7, 0.0, 1.0);
        HEATER_WARMTH = builder.comment("Degrees a working heater adds around itself").defineInRange("heaterWarmth", 20.0, 0.0, 1000.0);
        HEATER_RADIUS = builder.comment("How far a heater warms, in blocks").defineInRange("heaterRadius", 6, 1, 16);
        HEATER_THERMOSTAT = builder.comment("A heater works only where it is colder than this without heaters").defineInRange("heaterThermostat", 18.0, -100.0, 100.0);
        HEATER_ENERGY = builder.comment("Energy a heater uses per tick while it works; none if energy.stationsNeedEnergy is false").defineInRange("heaterEnergyPerTick", 15, 0, 1_000_000);
        HEATER_CAPACITY = builder.comment("Energy a heater holds").defineInRange("heaterCapacity", 10_000, 1, 100_000_000);
        builder.pop();

        builder.comment("When the HUD marks a hazard as dangerous: above these values, after protection").push("hud");
        HUD_DANGER_POLLUTION = builder.comment("Polluted air").defineInRange("dangerPollution", 1.0, 0.0, 10000.0);
        HUD_DANGER_RADIATION = builder.comment("Radiation").defineInRange("dangerRadiation", 1.5, 0.0, 10000.0);
        HUD_DANGER_DUST = builder.comment("Dust (1.5.0); also from this the person is slowed down and the solar panel loses power").defineInRange("dangerDust", 1.0, 0.0, 10000.0);
        HUD_DANGER_ACID = builder.comment("Acid").defineInRange("dangerAcid", 0.0, 0.0, 10000.0);
        builder.pop();

        builder.comment("Artificial bodies and power armor. Bodies have no biology; acid wears them by [bodies.<class>] acidWearPercentPerSecond").push("machines");
        MACHINE_ACID_WEAR_INTERVAL = builder.comment("Seconds between the acid wear of a body (the wear of these seconds comes at once)").defineInRange("bodyAcidWearInterval", 5, 1, 3600);
        MACHINE_PILOT_RADIATION_PER_PLATE = builder.comment("Share of radiation each armor plating keeps off the pilot, on top of pilotRadiationProtection").defineInRange("pilotRadiationPerPlate", 0.03, 0.0, 1.0);
        MACHINE_ARMOR_ACID_WEAR = builder.comment("Whether acid rain wears power armor").define("armorAcidWear", false);
        MACHINE_BODY_DUST_WEAR = builder.comment("Wear (percent) dust adds per second per unit of dust to a body without a sealing module, before",
                "bodies.<class>.wearMultiplier (1.5.0)").defineInRange("bodyDustWearPerSecond", 0.002, 0.0, 100.0);
        MACHINE_BODY_DUST_HEAT = builder.comment("Heat dust adds per second per unit of dust to a controlled body without a sealing module: it clogs the cooling")
                .defineInRange("bodyDustHeat", 0.05, 0.0, 100.0);
        MACHINE_ARMOR_DUST_WEAR_PER_SECOND = builder.comment("Structural integrity dust takes from power armor per second per unit of dust; 0 = never (the default)")
                .defineInRange("armorDustWearPerSecond", 0.0, 0.0, 1000.0);
        MACHINE_ARMOR_ACID_WEAR_PER_SECOND = builder.comment("Structural integrity acid takes from power armor per second per unit of acid, if armorAcidWear is on").defineInRange("armorAcidWearPerSecond", 0.1, 0.0, 1000.0);
        builder.pop();

        builder.comment("Sealed rooms: the air around air filtration units. A room leaks at the open sky, at an unloaded chunk or past its largest volume").push("shelter");
        SHELTER_MAX_VOLUME_PER_UNIT = builder.comment("Largest room, in blocks of air, per air filtration unit in it").defineInRange("maxVolumePerUnit", 4096, 1, 1_000_000);
        SHELTER_MAX_UNITS_PER_ROOM = builder.comment("Most units one room counts; a scan goes through at most maxVolumePerUnit times this many blocks")
                .defineInRange("maxUnitsPerRoom", 8, 1, 256);
        SHELTER_SCAN_BLOCKS_PER_TICK = builder.comment("Blocks of air all scans of a dimension may go through in one tick; a larger room is scanned over several ticks")
                .defineInRange("scanBlocksPerTick", 2048, 64, 10_000_000);
        SHELTER_RECHECK_SECONDS = builder.comment("Seconds between full scans of a room; a changed border block (a broken wall, an opened door) is found within a second")
                .defineInRange("recheckSeconds", 5, 1, 3600);
        SHELTER_SEALED_RADIATION = builder.comment("Radiation is multiplied by this inside a sealed room, whatever the walls are made of")
                .defineInRange("sealedRadiationMultiplier", 0.5, 0.0, 1.0);
        SHELTER_SEALED_DRIFT = builder.comment("Share of the difference to the air around that comes into a sealed room per second")
                .defineInRange("sealedDriftPerSecond", 0.0005, 0.0, 1.0);
        SHELTER_PARTIAL_DOOR_LEAK = builder.comment("Blocks of air from around that each closed iron or copper door or hatch on the border lets in per second, whatever the size of the room (tag projectrebirth:sealing_partial)")
                .defineInRange("partialDoorAirPerSecond", 0.25, 0.0, 1000.0);
        SHELTER_LEAK_DRIFT = builder.comment("Share of that difference that comes in per second while the room leaks")
                .defineInRange("leakDriftPerSecond", 0.1, 0.0, 1.0);
        SHELTER_CLEAN_AIR_PER_SECOND = builder.comment("Blocks of air one working air filtration unit cleans per second")
                .defineInRange("cleanAirPerSecond", 40.0, 0.0, 1_000_000.0);
        SHELTER_CARTRIDGE_LIFE = builder.comment("Life of one filter cartridge, in units of use").defineInRange("cartridgeLife", 3600.0, 1.0, 1.0E9);
        SHELTER_CARTRIDGE_BASE_USE = builder.comment("Cartridge use per second of work, per 1000 blocks of air of the room (shared by its units)")
                .defineInRange("cartridgeUsePerThousandBlocks", 0.2, 0.0, 1.0E6);
        SHELTER_CARTRIDGE_WORK_USE = builder.comment("Cartridge use per unit of pollution taken out of one block of air: the more comes in (a storm, iron doors), the more is used")
                .defineInRange("cartridgeUsePerPollution", 0.5, 0.0, 1.0E6);
        SHELTER_ENERGY_BASE = builder.comment("Energy an air filtration unit uses per tick while it works; none if energy.stationsNeedEnergy is false")
                .defineInRange("energyPerTick", 4, 0, 1_000_000);
        SHELTER_ENERGY_PER_THOUSAND = builder.comment("Added to that per 1000 blocks of air of its share of the room")
                .defineInRange("energyPerTickPerThousandBlocks", 10, 0, 1_000_000);
        SHELTER_ENERGY_CAPACITY = builder.comment("Energy an air filtration unit holds").defineInRange("energyCapacity", 20000, 1, 1_000_000_000);
        SHELTER_ENERGY_RECEIVE = builder.comment("Energy an air filtration unit takes in per tick").defineInRange("energyReceivePerTick", 200, 1, 1_000_000_000);
        AIRLOCK_MAX_VOLUME = builder.comment("Largest closed space between airlock doors that counts as one airlock: its airlock doors and sealed hatches do not open at the same time")
                .defineInRange("airlockMaxVolume", 64, 1, 4096);
        SHELTER_ALERT_DELAY = builder.comment("Seconds a leak or a lack of energy lasts before the owner and their faction are warned (a door opened for a moment says nothing)")
                .defineInRange("alertDelaySeconds", 10, 0, 3600);
        builder.pop();

        builder.comment("External contamination: on people, the gear they wear, bodies and power armor; it is shed into the air of a sealed room").push("contamination");
        CONTAMINATION_MAX = builder.comment("Most contamination one carrier holds").defineInRange("max", 100.0, 1.0, 1.0E6);
        CONTAMINATION_PER_POLLUTION = builder.comment("Gained per second outside a sealed room per unit of polluted air above environment.protection.filterSafePollution")
                .defineInRange("perPollution", 0.02, 0.0, 1000.0);
        CONTAMINATION_PER_RADIATION = builder.comment("Gained per second per unit of radiation above environment.hud.dangerRadiation (fallout)").defineInRange("perRadiation", 0.002, 0.0, 1000.0);
        CONTAMINATION_PER_DUST = builder.comment("Gained per second per unit of dust: little, dust harms machines more than it contaminates (1.5.0)")
                .defineInRange("perDust", 0.002, 0.0, 1000.0);
        CONTAMINATION_PER_ACID = builder.comment("Gained per second per unit of acid").defineInRange("perAcid", 0.1, 0.0, 1000.0);
        CONTAMINATION_SHED_SHARE = builder.comment("Share of it shed into the air of a sealed room per second").defineInRange("shedPerSecond", 0.02, 0.0, 1.0);
        CONTAMINATION_AIR_PER_UNIT = builder.comment("Pollution one unit of it brings into one block of air (spread over the room's volume)").defineInRange("airPollutionPerUnit", 20.0, 0.0, 1.0E6);
        CONTAMINATION_DECAY = builder.comment("What comes off by itself per second").defineInRange("decayPerSecond", 0.01, 0.0, 1.0E6);
        builder.pop();

        builder.comment("Decontamination chamber and radiation purge station").push("decontamination");
        DECON_PER_SECOND = builder.comment("Contamination the chamber takes off one carrier per second").defineInRange("chamberPerSecond", 20.0, 0.0, 1.0E6);
        DECON_PER_SOLUTION = builder.comment("Contamination one decontamination solution washes off").defineInRange("chamberPerSolution", 100.0, 0.001, 1.0E6);
        DECON_ENERGY = builder.comment("Energy the chamber uses per tick while it washes; none if energy.stationsNeedEnergy is false").defineInRange("chamberEnergyPerTick", 40, 0, 1_000_000);
        DECON_CAPACITY = builder.comment("Energy the chamber holds").defineInRange("chamberEnergyCapacity", 20000, 1, 1_000_000_000);
        PURGE_DOSE_PER_SECOND = builder.comment("Radiation dose the purge station takes from a person standing on it, per second").defineInRange("purgeDosePerSecond", 0.5, 0.0, 1.0E6);
        PURGE_MAX_POLLUTION = builder.comment("The purge station works only in a sealed room whose air is at most this polluted").defineInRange("purgeMaxPollution", 1.0, 0.0, 1.0E6);
        PURGE_ENERGY = builder.comment("Energy the purge station uses per tick while it works; none if energy.stationsNeedEnergy is false").defineInRange("purgeEnergyPerTick", 80, 0, 1_000_000);
        PURGE_CAPACITY = builder.comment("Energy the purge station holds").defineInRange("purgeEnergyCapacity", 100000, 1, 1_000_000_000);
        PURGE_RECEIVE = builder.comment("Energy the purge station takes in per tick").defineInRange("purgeEnergyReceivePerTick", 1000, 1, 1_000_000_000);
        builder.pop();

        builder.comment("The catastrophes: strength per hazard at intensity 1, on top of the background").push("events");
        defineEnvironmentEvent(builder, "acid_rain", Map.of(Hazard.POLLUTION, 2.0, Hazard.ACID, 1.0), 0.5, 1.5, 240, 480);
        defineEnvironmentEvent(builder, "toxic_storm", Map.of(Hazard.POLLUTION, 20.0), 0.5, 1.5, 180, 360);
        defineEnvironmentEvent(builder, "radiation_burst", Map.of(Hazard.RADIATION, 120.0), 0.5, 1.5, 120, 240);
        // 1.5.0: stronger in deserts and badlands, weaker in forests (event_multipliers of the biome profiles)
        defineEnvironmentEvent(builder, "dust_storm", Map.of(Hazard.DUST, 10.0, Hazard.POLLUTION, 1.0), 0.5, 1.5, 180, 420);
        builder.pop();
        builder.pop();

        builder.comment("Guns of power armor; each has its own section. Damage is per bullet or pellet (20 ticks = 1 second)").push("weapons");
        WEAPON_HEAVY_HINT_INTERVAL = builder.comment("Outside power armor: ticks between the hints that a weapon is too heavy")
                .defineInRange("heavyHintIntervalTicks", 60, 1, 72000);
        WEAPON_HEAVY_WALK_MULTIPLIER = builder.comment("Outside power armor: walking speed is multiplied by this while a weapon is in hand; 1.0 = no slower")
                .defineInRange("heavyWalkSpeedMultiplier", 1.0, 0.1, 1.0);
        defineWeapon(builder, "assault_rifle", 30, 4.0, 64.0, 3, 1, 1.5, 0.6, 6.0, 0.25, 0.4, 64.0, 1.0, 50, 0.6, 0.3, 0.05, 0.3);
        defineWeapon(builder, "battle_rifle", 10, 13.0, 128.0, 10, 1, 0.5, 0.0, 0.0, 0.25, 0.2, 128.0, 1.0, 50, 0.5, 1.8, 0.0, 0.0);
        defineWeapon(builder, "combat_shotgun", 6, 2.8, 24.0, 20, 8, 6.0, 0.0, 0.0, 0.25, 0.7, 6.0, 0.25, 16, 0.6, 3.5, 0.0, 0.0);
        builder.pop();

        builder.comment("What the upgrades of power armor do").push("armor_upgrades");
        builder.comment("Plating, per hull part, added to the armor's maximum integrity").push("plating");
        defineArmorPlating(builder, "copper", 10.0, 0.0);
        defineArmorPlating(builder, "iron", 20.0, 0.0);
        defineArmorPlating(builder, "titanium", 30.0, 5.0);
        builder.pop();
        ARMOR_JUMP_PER_AIR_MODULE = builder
                .comment("Jump strength added by each compressed air module")
                .defineInRange("jumpPerAirModule", 0.15, 0.0, 3.0);
        ARMOR_MAX_JUMP_STRENGTH = builder
                .comment("Highest jump strength modules can reach, so that a mobile armor does not become a flying one.",
                        "A variant whose own jump is higher keeps it")
                .defineInRange("maxJumpStrength", 1.2, 0.0, 3.0);
        ARMOR_SAFE_FALL_PER_AIR_MODULE = builder
                .comment("Blocks of safe fall added by each module: a higher jump must be able to land")
                .defineInRange("safeFallPerAirModule", 3.0, 0.0, 256.0);
        FLOODLIGHT_ENABLED = builder
                .comment("Whether floodlights work. They place an invisible light block where the pilot looks and move it along")
                .define("floodlightEnabled", true);
        FLOODLIGHT_RANGE = builder
                .comment("How far a floodlight reaches, in blocks")
                .defineInRange("floodlightRange", 16, 1, 64);
        builder.pop();

        builder.comment("What the gadgets of artificial bodies do").push("gadgets");
        builder.comment("Armor plates, per piece, added to the armor of the body class").push("plating");
        definePlating(builder, "copper", 1.0, 0.0);
        definePlating(builder, "iron", 2.0, 0.0);
        definePlating(builder, "titanium", 3.0, 1.0);
        builder.pop();
        EXTRA_BATTERY_CAPACITY = builder
                .comment("Charge the extra battery gadget adds to a body, in FE")
                .defineInRange("extraBatteryCapacity", 40_000, 0, 100_000_000);
        COOLING_MODULE_COOLING = builder
                .comment("Heat the cooling module gadget takes away per second (before the class's coolingMultiplier)")
                .defineInRange("coolingModulePerSecond", 1.5, 0.0, 100.0);
        builder.comment("Jetpack: hold jump in the air").push("jetpack");
        JETPACK_THRUST_SECONDS = builder
                .comment("Seconds of flight on a full reserve")
                .defineInRange("thrustSeconds", 6.0, 0.5, 600.0);
        JETPACK_RECHARGE_SECONDS = builder
                .comment("Seconds on the ground to fill an empty reserve; it recharges only on the ground")
                .defineInRange("rechargeSeconds", 12.0, 0.5, 3600.0);
        JETPACK_RISE_SPEED = builder
                .comment("Speed of rising, blocks per tick")
                .defineInRange("riseSpeed", 0.3, 0.05, 2.0);
        JETPACK_MAX_HEIGHT = builder
                .comment("Highest flight, in blocks above the surface under the body")
                .defineInRange("maxHeight", 8, 1, 256);
        builder.pop();
        builder.pop();

        builder.comment("Charge of artificial bodies (1.5.0). Off: bodies need no charge, as in 1.4.1.",
                "Every amount below is multiplied by bodies.<class>.energyUseMultiplier").push("body_energy");
        BODY_ENERGY_ENABLED = builder.comment("The whole system").define("enabled", true);
        BODY_ENERGY_IDLE = builder.comment("FE per second a controlled body uses at all times").defineInRange("idlePerSecond", 10.0, 0.0, 1_000_000.0);
        BODY_ENERGY_WALK = builder.comment("FE per second more while it walks").defineInRange("walkPerSecond", 40.0, 0.0, 1_000_000.0);
        BODY_ENERGY_SPRINT = builder.comment("FE per second more while it runs (instead of walkPerSecond)").defineInRange("sprintPerSecond", 90.0, 0.0, 1_000_000.0);
        BODY_ENERGY_ATTACK = builder.comment("FE per blow").defineInRange("perAttack", 25.0, 0.0, 1_000_000.0);
        BODY_ENERGY_BLOCK = builder.comment("FE per block broken").defineInRange("perBlockBroken", 15.0, 0.0, 1_000_000.0);
        BODY_ENERGY_SELF_REPAIR = builder.comment("FE per point of integrity the body mends by itself").defineInRange("perSelfRepairPoint", 150.0, 0.0, 1_000_000.0);
        BODY_ENERGY_JETPACK = builder.comment("FE per second of jetpack thrust").defineInRange("jetpackPerSecond", 400.0, 0.0, 1_000_000.0);
        BODY_ENERGY_STANDING = builder.comment("FE per second an empty body standing in the world uses; 0 = it keeps its charge")
                .defineInRange("standingPerSecond", 0.0, 0.0, 1_000_000.0);
        BODY_ENERGY_LOW_SHARE = builder.comment("Below this part of the charge the body warns and slows down (0.2 = 20%)")
                .defineInRange("lowShare", 0.2, 0.0, 1.0);
        BODY_ENERGY_LOW_SLOWDOWN = builder.comment("Part of the speed lost on a low charge").defineInRange("lowSlowdown", 0.1, 0.0, 0.9);
        BODY_ENERGY_EMPTY_SLOWDOWN = builder.comment("Part of the speed lost in the emergency mode (no charge): no running, jumping or jetpack either,",
                "and no self-repair. The body stays under control and Z still returns to the human body")
                .defineInRange("emptySlowdown", 0.5, 0.0, 0.9);
        BODY_ENERGY_EMPTY_BREAK_SPEED = builder.comment("Breaking blocks is multiplied by this in the emergency mode")
                .defineInRange("emptyBreakSpeed", 0.3, 0.0, 1.0);
        CHARGING_PAD_CAPACITY = builder.comment("Energy a charging pad holds").defineInRange("padCapacity", 20_000, 1, 100_000_000);
        CHARGING_PAD_RECEIVE = builder.comment("Energy a charging pad takes in per tick").defineInRange("padReceivePerTick", 400, 1, 100_000_000);
        CHARGING_PAD_RATE = builder.comment("Charge a pad gives per tick to each body on it and to the battery in it").defineInRange("padChargePerTick", 200, 1, 100_000_000);
        CAPSULE_CHARGES = builder.comment("Whether the transfer capsule charges the body that leaves from it on transfer (off by default)").define("capsuleCharges", false);
        BODY_BATTERY_CAPACITY = builder.comment("Charge a portable body battery holds").defineInRange("batteryCapacity", 40_000, 1, 100_000_000);
        BODY_BATTERY_RECEIVE = builder.comment("Charge a body battery takes in per tick (pad or chargers of other mods)").defineInRange("batteryReceivePerTick", 400, 1, 100_000_000);
        BODY_BATTERY_USE_SECONDS = builder.comment("Seconds a body battery takes to hand its charge over; damage interrupts it")
                .defineInRange("batteryUseSeconds", 3.0, 0.25, 60.0);
        builder.pop();

        builder.comment("Heat of artificial bodies (1.5.0), a scale from 0 to 100, not degrees. Off: bodies never heat up, as in 1.4.1.",
                "Amounts are per second unless said otherwise. Cooling is multiplied by bodies.<class>.coolingMultiplier.",
                "A power armor does not heat up, and a body inside one only cools").push("body_heat");
        BODY_HEAT_ENABLED = builder.comment("The whole system").define("enabled", true);
        HEAT_SPRINT = builder.comment("Heat from running").defineInRange("sprint", 0.8, 0.0, 100.0);
        HEAT_ATTACK = builder.comment("Heat per blow").defineInRange("perAttack", 0.3, 0.0, 100.0);
        HEAT_BLOCK = builder.comment("Heat per block broken").defineInRange("perBlockBroken", 0.15, 0.0, 100.0);
        HEAT_JETPACK = builder.comment("Heat per second of jetpack thrust").defineInRange("jetpack", 3.0, 0.0, 100.0);
        HEAT_HOT_BIOME = builder.comment("Heat per degree of the biome's temperature above hotBiomeFrom (deserts and badlands are 2.0)")
                .defineInRange("hotBiome", 0.6, 0.0, 100.0);
        HEAT_HOT_BIOME_FROM = builder.comment("Biome temperature from which the climate heats a body").defineInRange("hotBiomeFrom", 1.0, -10.0, 10.0);
        HEAT_NETHER = builder.comment("Heat in a dimension where water boils (the Nether)").defineInRange("nether", 1.5, 0.0, 100.0);
        HEAT_NEAR_FIRE = builder.comment("Heat per block of fire, lava, magma or a lit campfire around the body").defineInRange("nearFire", 0.6, 0.0, 100.0);
        HEAT_NEAR_FIRE_MAX = builder.comment("At most this much heat from fire around").defineInRange("nearFireMax", 4.0, 0.0, 100.0);
        HEAT_FIRE_RADIUS = builder.comment("How far around the body fire is looked for, in blocks (checked once a second)").defineInRange("fireRadius", 2, 0, 5);
        HEAT_BURNING = builder.comment("Heat while the body itself burns").defineInRange("burning", 4.0, 0.0, 100.0);
        COOL_BASE = builder.comment("Cooling at all times").defineInRange("coolBase", 1.0, 0.0, 100.0);
        COOL_REST = builder.comment("More cooling while the body stands still and does no work").defineInRange("coolRest", 1.0, 0.0, 100.0);
        COOL_WATER = builder.comment("More cooling in water").defineInRange("coolWater", 6.0, 0.0, 100.0);
        COOL_RAIN = builder.comment("More cooling in rain").defineInRange("coolRain", 2.0, 0.0, 100.0);
        COOL_COLD_BIOME = builder.comment("More cooling in a cold biome").defineInRange("coolColdBiome", 1.0, 0.0, 100.0);
        COOL_COLD_BIOME_BELOW = builder.comment("Biome temperature below which a biome is cold").defineInRange("coldBiomeBelow", 0.3, -10.0, 10.0);
        COOL_SHADE = builder.comment("More cooling in the shade or indoors (no sky above)").defineInRange("coolShade", 0.5, 0.0, 100.0);
        HEAT_WARNING = builder.comment("From this heat the body warns").defineInRange("warningFrom", 70.0, 0.0, 100.0);
        HEAT_THROTTLE = builder.comment("Above this heat the body throttles: slower, weaker blows, no jetpack. At 100 it overheats,",
                "and stays throttled until it cools below this again. Overheating never destroys a body")
                .defineInRange("throttleFrom", 85.0, 0.0, 100.0);
        HEAT_THROTTLE_SLOWDOWN = builder.comment("Part of the speed lost while throttled").defineInRange("throttleSlowdown", 0.25, 0.0, 0.9);
        HEAT_THROTTLE_ATTACK = builder.comment("Part of the melee damage lost while throttled").defineInRange("throttleAttack", 0.3, 0.0, 1.0);
        builder.pop();

        builder.comment("Wear of artificial bodies (1.5.0), 0 to 100 percent: tiredness of the mechanisms, apart from integrity. It does not",
                "mend by itself and not with a repair kit, only at the maintenance station. A worn body is never destroyed by it.",
                "Off: no wear, as in 1.4.1 (acid then wears nothing either)").push("body_wear");
        BODY_WEAR_ENABLED = builder.comment("The whole system").define("enabled", true);
        WEAR_WORK = builder.comment("Wear per second while a player controls the body (before bodies.<class>.wearMultiplier);",
                "0.00833 = about 10% per game day").defineInRange("workPerSecond", 0.00833, 0.0, 100.0);
        WEAR_OVERHEAT = builder.comment("Wear per second while the body is overheated (reached heat 100 and has not cooled below the throttle level)")
                .defineInRange("overheatedPerSecond", 1.0, 0.0, 100.0);
        WEAR_ENERGY_AT_25 = builder.comment("From 25% wear: the body spends this much more charge (0.1 = 10%)").defineInRange("energyAt25", 0.1, 0.0, 10.0);
        WEAR_SLOWDOWN_AT_50 = builder.comment("From 50%: part of the speed lost").defineInRange("slowdownAt50", 0.1, 0.0, 0.9);
        WEAR_SELF_REPAIR_AT_50 = builder.comment("From 50%: self-repair is multiplied by this").defineInRange("selfRepairAt50", 0.5, 0.0, 1.0);
        WEAR_HEAT_AT_50 = builder.comment("From 50%: heating is multiplied by this").defineInRange("heatAt50", 1.25, 1.0, 10.0);
        WEAR_ATTACK_AT_75 = builder.comment("From 75%: part of the melee damage lost").defineInRange("attackAt75", 0.15, 0.0, 1.0);
        WEAR_FAULTS_AT_75 = builder.comment("From 75%: faults in an electromagnetic storm are this many times as frequent (the storm comes later in 1.5.0)")
                .defineInRange("faultsAt75", 1.5, 1.0, 10.0);
        WEAR_MAX_HEALTH_AT_100 = builder.comment("At 100% (critical wear): part of the maximum integrity lost; no self-repair at all")
                .defineInRange("maxIntegrityAt100", 0.5, 0.0, 0.9);
        WEAR_SLOWDOWN_AT_100 = builder.comment("At 100%: part of the speed lost (instead of slowdownAt50)").defineInRange("slowdownAt100", 0.3, 0.0, 0.9);
        builder.pop();

        builder.comment("Energy (NeoForge Energy, FE): generators, battery, cable and what the stations use").push("energy");
        STATIONS_NEED_ENERGY = builder
                .comment("Whether production and maintenance stations need energy. false = they work without it, as in 0.1.0")
                .define("stationsNeedEnergy", true);
        LIFE_SUPPORT_CHARGED_SHARE = builder
                .comment("Life support (air filtration and the like) gets energy from the cable first; other machines on the same cable get it only while every life support block holds at least this share of its buffer")
                .defineInRange("lifeSupportChargedShare", 0.9, 0.0, 1.0);
        BACKUP_START_SHARE = builder
                .comment("A backup battery feeds a life support block once that block holds less than this share of its buffer: the main power does not keep up")
                .defineInRange("backupStartShare", 0.5, 0.0, 1.0);
        BACKUP_CAPACITY = builder
                .comment("Energy the backup battery stores")
                .defineInRange("backupCapacity", 200000, 1, 1_000_000_000);
        BACKUP_TRANSFER = builder
                .comment("Energy per tick the backup battery takes in and gives out")
                .defineInRange("backupTransferPerTick", 200, 1, 100_000_000);
        STATION_ENERGY_CAPACITY = builder
                .comment("Energy buffer of every production and maintenance station")
                .defineInRange("stationCapacity", 20000, 1, 100_000_000);
        STATION_ENERGY_PER_TICK = builder
                .comment("Energy a production station uses per tick while it works (20 ticks = 1 second)")
                .defineInRange("stationEnergyPerTick", 20, 0, 1_000_000);
        MAINTENANCE_ENERGY_PER_TICK = builder
                .comment("Energy the maintenance station uses per tick while it repairs")
                .defineInRange("maintenanceEnergyPerTick", 20, 0, 1_000_000);
        COMBUSTION_ENERGY_PER_TICK = builder
                .comment("Energy the combustion generator makes per tick while it burns; it burns anything a furnace burns, for as long as a furnace would")
                .defineInRange("combustionEnergyPerTick", 40, 1, 1_000_000);
        COMBUSTION_CAPACITY = builder
                .comment("Energy buffer of the combustion generator")
                .defineInRange("combustionCapacity", 20000, 1, 100_000_000);
        SOLAR_DUST_FACTOR = builder
                .comment("A solar panel in a dust storm makes this part of its energy (1.5.0)")
                .defineInRange("solarDustFactor", 0.25, 0.0, 1.0);
        SOLAR_ENERGY_PER_TICK = builder
                .comment("Energy the solar panel makes per tick in daylight under the open sky (not in rain)")
                .defineInRange("solarEnergyPerTick", 8, 1, 1_000_000);
        SOLAR_CAPACITY = builder
                .comment("Energy buffer of the solar panel")
                .defineInRange("solarCapacity", 4000, 1, 100_000_000);
        BATTERY_CAPACITY = builder
                .comment("Energy the battery stores")
                .defineInRange("batteryCapacity", 500000, 1, 1_000_000_000);
        BATTERY_TRANSFER = builder
                .comment("Energy per tick the battery takes in and gives out (it gives out only through its front)")
                .defineInRange("batteryTransferPerTick", 1000, 1, 100_000_000);
        GENERATOR_OUTPUT = builder
                .comment("Energy per tick a generator gives to the blocks next to it")
                .defineInRange("generatorOutputPerTick", 200, 1, 100_000_000);
        CABLE_TRANSFER = builder
                .comment("Energy one cable network passes on per delivery (each generator or battery delivers once per tick)")
                .defineInRange("cableTransferPerTick", 2000, 1, 100_000_000);
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

    public static ArmorStats armorStats(ArmorVariant variant) {
        return ARMOR_STATS.get(variant);
    }

    public record ArmorStats(ModConfigSpec.DoubleValue maxStructural, ModConfigSpec.DoubleValue maxCritical,
                             ModConfigSpec.DoubleValue movementSpeed, ModConfigSpec.DoubleValue pilotAttackDamageBonus,
                             ModConfigSpec.DoubleValue jumpStrength, ModConfigSpec.DoubleValue safeFallDistance) {
    }

    private static void defineProtectionKit(ModConfigSpec.Builder builder, String id, double pollution, double acid, double radiation,
                                            double heat, double cold) {
        builder.push(id);
        PROTECTION_KITS.put(id, new ProtectionStats(
                builder.comment("Share of polluted air kept off by the head piece with a filter").defineInRange("pollution", pollution, 0.0, 1.0),
                builder.comment("Share of acid kept off by the full kit").defineInRange("acid", acid, 0.0, 1.0),
                builder.comment("Share of radiation kept off by the full kit").defineInRange("radiation", radiation, 0.0, 1.0),
                builder.comment("Share of heat kept off by the full kit (1.5.0)").defineInRange("heat", heat, 0.0, 1.0),
                builder.comment("Share of cold kept off by the full kit (1.5.0)").defineInRange("cold", cold, 0.0, 1.0)));
        builder.pop();
    }

    /** The numbers of a kit of protective gear, or null if the config does not know it. */
    public static ProtectionStats protectionKit(String id) {
        return PROTECTION_KITS.get(id);
    }

    public record ProtectionStats(ModConfigSpec.DoubleValue pollution, ModConfigSpec.DoubleValue acid, ModConfigSpec.DoubleValue radiation,
                                  ModConfigSpec.DoubleValue heat, ModConfigSpec.DoubleValue cold) {
        public float of(com.projectrebirth.environment.Hazard hazard) {
            return switch (hazard) {
                case POLLUTION -> pollution.get().floatValue();
                case ACID -> acid.get().floatValue();
                case RADIATION -> radiation.get().floatValue();
                // dust is kept off by the head piece's filter, like polluted air
                case DUST -> pollution.get().floatValue();
            };
        }
    }

    private static void defineEnvironmentEvent(ModConfigSpec.Builder builder, String id, Map<Hazard, Double> strengths, double minIntensity, double maxIntensity,
                                               int minSeconds, int maxSeconds) {
        builder.push(id);
        ModConfigSpec.BooleanValue enabled = builder.comment("Whether this catastrophe can happen").define("enabled", true);
        ModConfigSpec.IntValue weight = builder.comment("How often it comes compared with the others; 0 = never by itself (the command still starts it)").defineInRange("weight", 1, 0, 1000);
        Map<Hazard, ModConfigSpec.DoubleValue> hazards = new EnumMap<>(Hazard.class);
        for (Hazard hazard : Hazard.values()) {
            hazards.put(hazard, builder.defineInRange(hazard.id(), strengths.getOrDefault(hazard, 0.0), 0.0, 10000.0));
        }
        ENVIRONMENT_EVENTS.put(id, new EnvironmentEventStats(enabled, weight, hazards,
                builder.comment("Lowest intensity").defineInRange("minIntensity", minIntensity, 0.01, 10.0),
                builder.comment("Highest intensity").defineInRange("maxIntensity", maxIntensity, 0.01, 10.0),
                builder.comment("Shortest active phase, in seconds").defineInRange("minActiveSeconds", minSeconds, 1, 86400),
                builder.comment("Longest active phase, in seconds").defineInRange("maxActiveSeconds", maxSeconds, 1, 86400)));
        builder.pop();
    }

    /** The numbers of a catastrophe, or null if the config does not know it. */
    public static EnvironmentEventStats environmentEvent(String id) {
        return ENVIRONMENT_EVENTS.get(id);
    }

    public record EnvironmentEventStats(ModConfigSpec.BooleanValue enabled, ModConfigSpec.IntValue weight, Map<Hazard, ModConfigSpec.DoubleValue> hazards,
                                        ModConfigSpec.DoubleValue minIntensity, ModConfigSpec.DoubleValue maxIntensity,
                                        ModConfigSpec.IntValue minActiveSeconds, ModConfigSpec.IntValue maxActiveSeconds) {
        public float hazard(Hazard hazard) {
            return hazards.get(hazard).get().floatValue();
        }
    }

    private static void defineWeapon(ModConfigSpec.Builder builder, String id, int magazine, double damage, double range, int shotInterval, int pellets,
                                     double spread, double spreadPerShot, double maxExtraSpread, double spreadRecovery, double aimSpread,
                                     double falloffStart, double damageAtRange, int reloadTicks,
                                     double aimSpeed, double recoil, double recoilBuildup, double maxRecoilBuildup) {
        builder.push(id);
        WEAPONS.put(id, new WeaponStats(
                builder.comment("Rounds in the magazine").defineInRange("magazine", magazine, 1, 1000),
                builder.comment("Damage of one bullet or pellet").defineInRange("damage", damage, 0.0, 1000.0),
                builder.comment("How far it hits, in blocks").defineInRange("range", range, 1.0, 512.0),
                builder.comment("Ticks between shots").defineInRange("shotIntervalTicks", shotInterval, 1, 1200),
                builder.comment("Pellets per shot").defineInRange("pellets", pellets, 1, 64),
                builder.comment("Spread, in degrees from the aim").defineInRange("spreadDegrees", spread, 0.0, 45.0),
                builder.comment("Degrees of spread added by each shot of a burst").defineInRange("spreadPerShot", spreadPerShot, 0.0, 45.0),
                builder.comment("The most degrees a burst can add").defineInRange("maxExtraSpread", maxExtraSpread, 0.0, 45.0),
                builder.comment("Degrees of added spread lost per tick without firing").defineInRange("spreadRecoveryPerTick", spreadRecovery, 0.0, 45.0),
                builder.comment("Spread is multiplied by this while aiming").defineInRange("aimSpreadMultiplier", aimSpread, 0.0, 1.0),
                builder.comment("Distance in blocks up to which it does full damage").defineInRange("falloffStart", falloffStart, 0.0, 512.0),
                builder.comment("Share of the damage left at full range").defineInRange("damageAtRange", damageAtRange, 0.0, 1.0),
                builder.comment("Ticks to reload: the magazine, or one round if the weapon loads round by round").defineInRange("reloadTicks", reloadTicks, 1, 1200),
                builder.comment("The armor's speed is multiplied by this while aiming").defineInRange("aimSpeedMultiplier", aimSpeed, 0.0, 1.0),
                builder.comment("Degrees the view rises per shot; only on the pilot's screen").defineInRange("recoilDegrees", recoil, 0.0, 45.0),
                builder.comment("Degrees of recoil added by each shot of a burst").defineInRange("recoilBuildup", recoilBuildup, 0.0, 45.0),
                builder.comment("The most degrees a burst can add to the recoil of a shot").defineInRange("maxRecoilBuildup", maxRecoilBuildup, 0.0, 45.0)));
        builder.pop();
    }

    /** The numbers of a weapon, or null if the config does not know it. */
    public static WeaponStats weapon(String id) {
        return WEAPONS.get(id);
    }

    public record WeaponStats(ModConfigSpec.IntValue magazine, ModConfigSpec.DoubleValue damage, ModConfigSpec.DoubleValue range,
                              ModConfigSpec.IntValue shotIntervalTicks, ModConfigSpec.IntValue pellets, ModConfigSpec.DoubleValue spreadDegrees,
                              ModConfigSpec.DoubleValue spreadPerShot, ModConfigSpec.DoubleValue maxExtraSpread,
                              ModConfigSpec.DoubleValue spreadRecoveryPerTick, ModConfigSpec.DoubleValue aimSpreadMultiplier,
                              ModConfigSpec.DoubleValue falloffStart, ModConfigSpec.DoubleValue damageAtRange, ModConfigSpec.IntValue reloadTicks,
                              ModConfigSpec.DoubleValue aimSpeedMultiplier, ModConfigSpec.DoubleValue recoilDegrees,
                              ModConfigSpec.DoubleValue recoilBuildup, ModConfigSpec.DoubleValue maxRecoilBuildup) {
    }

    private static void defineArmorPlating(ModConfigSpec.Builder builder, String material, double structural, double critical) {
        builder.push(material);
        ARMOR_PLATING.put(material, new ArmorPlatingStats(
                builder.comment("Added maximum structural integrity per part").defineInRange("structural", structural, 0.0, 10000.0),
                builder.comment("Added maximum critical integrity per part").defineInRange("critical", critical, 0.0, 10000.0)));
        builder.pop();
    }

    /** The numbers of an armor plating material, or null if the config does not know it. */
    public static ArmorPlatingStats armorPlating(String material) {
        return ARMOR_PLATING.get(material);
    }

    public record ArmorPlatingStats(ModConfigSpec.DoubleValue structural, ModConfigSpec.DoubleValue critical) {
    }

    private static void definePlating(ModConfigSpec.Builder builder, String material, double armor, double toughness) {
        builder.push(material);
        PLATING.put(material, new PlatingStats(
                builder.comment("Armor points per piece").defineInRange("armor", armor, 0.0, 30.0),
                builder.comment("Armor toughness per piece").defineInRange("toughness", toughness, 0.0, 20.0)));
        builder.pop();
    }

    /** The numbers of a plate material, or null if the config does not know it (a plate added by a data pack). */
    public static PlatingStats plating(String material) {
        return PLATING.get(material);
    }

    public record PlatingStats(ModConfigSpec.DoubleValue armor, ModConfigSpec.DoubleValue toughness) {
    }

    public static BodyStats bodyStats(BodyClass bodyClass) {
        return BODY_STATS.get(bodyClass);
    }

    public record BodyStats(ModConfigSpec.DoubleValue maxHealth, ModConfigSpec.DoubleValue armor,
                            ModConfigSpec.DoubleValue speedMultiplier, ModConfigSpec.DoubleValue attackDamageBonus,
                            ModConfigSpec.DoubleValue selfRepairPerSecond, ModConfigSpec.IntValue selfRepairDelaySeconds,
                            ModConfigSpec.BooleanValue canConsume, ModConfigSpec.BooleanValue immuneToEffects,
                            ModConfigSpec.IntValue upgradeSlots, ModConfigSpec.DoubleValue acidWearPerSecond,
                            ModConfigSpec.IntValue energyCapacity, ModConfigSpec.DoubleValue energyUseMultiplier,
                            ModConfigSpec.DoubleValue coolingMultiplier, ModConfigSpec.DoubleValue wearMultiplier) {
    }
}
