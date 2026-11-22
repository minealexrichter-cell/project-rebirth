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
    // 1.5.0 block E: better bodies are less sensitive to electromagnetic interference
    private static final double[] DEFAULT_EMI = {1.0, 0.85, 0.7, 0.55, 0.4};
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
    public static final ModConfigSpec.BooleanValue ARMOR_OPENING_SHELL;
    public static final ModConfigSpec.DoubleValue ARMOR_WRECK_PILOT_DAMAGE;
    public static final ModConfigSpec.DoubleValue ARMOR_REPAIR_STRUCTURAL_PER_INGOT;
    public static final ModConfigSpec.DoubleValue ARMOR_REPAIR_CRITICAL_PER_PLATE;
    public static final ModConfigSpec.DoubleValue ARMOR_PILOT_KNOCKBACK_BONUS;
    public static final ModConfigSpec.DoubleValue ARMOR_PILOT_ATTACK_SPEED;
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
    public static final ModConfigSpec.DoubleValue EMI_UNDERGROUND;
    public static final ModConfigSpec.DoubleValue EMI_THRESHOLD;
    public static final ModConfigSpec.DoubleValue EMI_SHIELD;
    public static final ModConfigSpec.DoubleValue EMI_CHARGE;
    public static final ModConfigSpec.DoubleValue EMI_FAULT_CHANCE;
    public static final ModConfigSpec.DoubleValue EMI_FAULT_WEAR;
    public static final ModConfigSpec.IntValue EMI_FAULT_TICKS;
    public static final ModConfigSpec.DoubleValue EMI_JETPACK_DROPOUT;
    public static final ModConfigSpec.DoubleValue EMI_FLOODLIGHT_FLICKER;
    public static final ModConfigSpec.DoubleValue EMI_SPREAD;
    public static final ModConfigSpec.DoubleValue EMI_STATION_FACTOR;
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
    /** 1.6.0: contaminated zones (residual spots of catastrophes). */
    public static final ModConfigSpec.BooleanValue ZONES_ENABLED;
    public static final ModConfigSpec.IntValue ZONES_RADIATION_MIN_SPOTS;
    public static final ModConfigSpec.IntValue ZONES_RADIATION_MAX_SPOTS;
    public static final ModConfigSpec.IntValue ZONES_RADIATION_MIN_RADIUS;
    public static final ModConfigSpec.IntValue ZONES_RADIATION_MAX_RADIUS;
    public static final ModConfigSpec.DoubleValue ZONES_RADIATION_STRENGTH;
    public static final ModConfigSpec.DoubleValue ZONES_RADIATION_HALF_LIFE_DAYS;
    public static final ModConfigSpec.IntValue ZONES_TOXIC_MIN_SPOTS;
    public static final ModConfigSpec.IntValue ZONES_TOXIC_MAX_SPOTS;
    public static final ModConfigSpec.IntValue ZONES_TOXIC_MIN_RADIUS;
    public static final ModConfigSpec.IntValue ZONES_TOXIC_MAX_RADIUS;
    public static final ModConfigSpec.DoubleValue ZONES_TOXIC_STRENGTH;
    public static final ModConfigSpec.DoubleValue ZONES_TOXIC_HALF_LIFE_DAYS;
    public static final ModConfigSpec.DoubleValue ZONES_TOXIC_LOWLAND_WEIGHT;
    public static final ModConfigSpec.IntValue ZONES_LOWLAND_HEIGHT;
    public static final ModConfigSpec.IntValue ZONES_MIN_DISTANCE;
    public static final ModConfigSpec.IntValue ZONES_MAX_DISTANCE;
    public static final ModConfigSpec.BooleanValue ZONES_ON_TERRITORY;
    public static final ModConfigSpec.DoubleValue ZONES_MAX_PER_CHUNK;
    public static final ModConfigSpec.DoubleValue ZONES_FORGET_BELOW;
    public static final ModConfigSpec.DoubleValue ZONES_MARK_THRESHOLD;
    public static final ModConfigSpec.BooleanValue SOURCES_ENABLED;
    public static final ModConfigSpec.BooleanValue STRUCTURES_ENABLED;
    public static final ModConfigSpec.IntValue STRUCTURES_SAFE_RADIUS;
    public static final ModConfigSpec.BooleanValue STRUCTURES_LAB;
    public static final ModConfigSpec.BooleanValue STRUCTURES_RUINS;
    public static final ModConfigSpec.BooleanValue STRUCTURES_REACTOR;
    public static final ModConfigSpec.BooleanValue STRUCTURES_MILITARY;
    public static final ModConfigSpec.BooleanValue STRUCTURES_SCIENCE;
    public static final ModConfigSpec.BooleanValue STRUCTURES_FACTORY;
    public static final ModConfigSpec.BooleanValue STRUCTURES_COMM;
    public static final ModConfigSpec.BooleanValue STRUCTURES_OUTPOST;
    public static final ModConfigSpec.BooleanValue STRUCTURES_ZONE;
    public static final ModConfigSpec.BooleanValue HOSTILE_ENABLED;
    public static final ModConfigSpec.BooleanValue WATER_ENABLED;
    public static final ModConfigSpec.DoubleValue WATER_SWIM_TOXIN;
    public static final ModConfigSpec.DoubleValue WATER_SWIM_CONTAMINATION;
    public static final ModConfigSpec.DoubleValue WATER_DRINK_TOXIN;
    public static final ModConfigSpec.BooleanValue WATER_STORMS;
    public static final ModConfigSpec.IntValue PURIFIER_TICKS;
    public static final ModConfigSpec.IntValue PURIFIER_ENERGY_PER_TICK;
    public static final ModConfigSpec.IntValue PURIFIER_ENERGY_CAPACITY;
    public static final ModConfigSpec.DoubleValue PURIFIER_CARTRIDGE_SHARE;
    public static final ModConfigSpec.IntValue PURIFIER_BUCKET_BOTTLES;
    public static final ModConfigSpec.IntValue WATER_BOTTLE_STACK;
    public static final ModConfigSpec.BooleanValue GARRISON_ENABLED;
    public static final ModConfigSpec.BooleanValue MACHINES_IMMUNE_TO_EFFECTS;
    // 1.7.1, block E: the small robot
    public static final ModConfigSpec.BooleanValue SMALL_ROBOT_ENABLED;
    public static final ModConfigSpec.DoubleValue SMALL_ROBOT_SPEED;
    public static final ModConfigSpec.IntValue SMALL_ROBOT_ENERGY_CAPACITY;
    public static final ModConfigSpec.DoubleValue SMALL_ROBOT_ENERGY_IDLE;
    public static final ModConfigSpec.DoubleValue SMALL_ROBOT_ENERGY_MOVING;
    public static final ModConfigSpec.IntValue SMALL_ROBOT_ENERGY_SHOT;
    public static final ModConfigSpec.IntValue SMALL_ROBOT_ROUNDS;
    public static final ModConfigSpec.IntValue SMALL_ROBOT_ROCKETS;
    public static final ModConfigSpec.IntValue SMALL_ROBOT_RANGE;
    public static final ModConfigSpec.DoubleValue SMALL_ROBOT_DAMAGE;
    public static final ModConfigSpec.DoubleValue SMALL_ROBOT_SPREAD;
    public static final ModConfigSpec.IntValue SMALL_ROBOT_BURST;
    public static final ModConfigSpec.IntValue SMALL_ROBOT_SHOT_TICKS;
    public static final ModConfigSpec.IntValue SMALL_ROBOT_PAUSE_TICKS;
    public static final ModConfigSpec.IntValue SMALL_ROBOT_ROCKET_TICKS;
    public static final ModConfigSpec.DoubleValue SMALL_ROBOT_LOW_ENERGY;
    public static final ModConfigSpec.DoubleValue SMALL_ROBOT_CHARGED;
    public static final ModConfigSpec.IntValue SMALL_ROBOT_GUARD_RADIUS;
    public static final ModConfigSpec.DoubleValue DOCK_ROLLOUT_SPEED;
    public static final ModConfigSpec.BooleanValue MULTIBLOCKS_ENABLED;
    public static final ModConfigSpec.IntValue MULTIBLOCK_ENERGY_CAPACITY;
    public static final ModConfigSpec.IntValue MULTIBLOCK_ENERGY_RECEIVE;
    public static final ModConfigSpec.IntValue MULTIBLOCK_HOLOGRAM_SECONDS;
    // 1.7.0, block C: the AI core and the base of the player's machines
    public static final ModConfigSpec.BooleanValue MACHINES_ENABLED;
    public static final ModConfigSpec.DoubleValue DAMAGED_CORE_PATROL_ROBOT;
    public static final ModConfigSpec.DoubleValue DAMAGED_CORE_MECH;
    public static final ModConfigSpec.IntValue MACHINE_REGEN_DELAY_SECONDS;
    public static final ModConfigSpec.DoubleValue MACHINE_REGEN_PER_SECOND;
    public static final ModConfigSpec.DoubleValue MACHINE_CRITICAL_EXPLOSION;
    public static final ModConfigSpec.DoubleValue MACHINE_CRITICAL_FIRE;
    public static final ModConfigSpec.DoubleValue MACHINE_RESTART_FRACTION;
    public static final ModConfigSpec.DoubleValue MACHINE_REPAIR_PER_INGOT;
    public static final ModConfigSpec.DoubleValue MACHINE_REPAIR_CRITICAL_PER_PLATE;
    public static final ModConfigSpec.DoubleValue MACHINE_WEAR_PER_ACTUATOR;
    public static final ModConfigSpec.DoubleValue MACHINE_ACID_WEAR_PER_SECOND;
    public static final ModConfigSpec.DoubleValue MACHINE_DUST_WEAR_PER_SECOND;
    public static final ModConfigSpec.IntValue MACHINE_REGISTRY_SECONDS;
    public static final ModConfigSpec.IntValue MACHINE_SERVER_LIMIT;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> MACHINE_MANAGER_ROLES;
    public static final java.util.Map<com.projectrebirth.garrison.MachineKind, ModConfigSpec.IntValue> MACHINE_PLAYER_LIMIT = new java.util.EnumMap<>(com.projectrebirth.garrison.MachineKind.class);
    public static final java.util.Map<com.projectrebirth.garrison.MachineKind, ModConfigSpec.IntValue> MACHINE_FACTION_LIMIT = new java.util.EnumMap<>(com.projectrebirth.garrison.MachineKind.class);
    public static final java.util.Map<com.projectrebirth.garrison.MachineKind, ModConfigSpec.DoubleValue> MACHINE_MAX_STRUCTURAL = new java.util.EnumMap<>(com.projectrebirth.garrison.MachineKind.class);
    public static final java.util.Map<com.projectrebirth.garrison.MachineKind, ModConfigSpec.DoubleValue> MACHINE_MAX_CRITICAL = new java.util.EnumMap<>(com.projectrebirth.garrison.MachineKind.class);
    public static final java.util.Map<com.projectrebirth.garrison.MachineKind, ModConfigSpec.IntValue> MACHINE_UPGRADE_SLOTS = new java.util.EnumMap<>(com.projectrebirth.garrison.MachineKind.class);
    public static final ModConfigSpec.BooleanValue ALLOW_INTRUDER_MODE;
    // 1.7.0, block D: the console and the tactical tablet
    public static final ModConfigSpec.BooleanValue CONSOLE_ENABLED;
    public static final ModConfigSpec.IntValue CONSOLE_RANGE;
    public static final ModConfigSpec.IntValue CONSOLE_MAX_MACHINES;
    public static final ModConfigSpec.IntValue CONSOLE_LINK_RADIUS;
    public static final ModConfigSpec.IntValue CONSOLE_ENERGY_CAPACITY;
    public static final ModConfigSpec.IntValue CONSOLE_ENERGY_PER_TICK;
    public static final ModConfigSpec.IntValue CONSOLE_COMBAT_SECONDS;
    public static final ModConfigSpec.IntValue CONSOLE_INTRUDER_RADIUS;
    public static final ModConfigSpec.IntValue CONSOLE_WEATHER_RADIUS;
    public static final ModConfigSpec.IntValue CONSOLE_WEATHER_SECONDS;
    public static final ModConfigSpec.IntValue CONSOLE_MAP_STATION_RADIUS;
    public static final ModConfigSpec.IntValue TABLET_ROUTE_POINTS;
    public static final ModConfigSpec.IntValue WORK_AREA_MINE;
    public static final ModConfigSpec.IntValue WORK_AREA_MINE_DEPTH;
    public static final ModConfigSpec.IntValue WORK_AREA_LUMBER;
    public static final ModConfigSpec.IntValue WORK_AREA_FARM;
    // 1.7.0, block E: androids
    public static final ModConfigSpec.DoubleValue ANDROID_CRITICAL_SHARE;
    public static final ModConfigSpec.DoubleValue ANDROID_ENERGY_ACTIVE;
    public static final ModConfigSpec.DoubleValue ANDROID_ENERGY_MOVING;
    public static final ModConfigSpec.DoubleValue ANDROID_LOW_CHARGE;
    public static final ModConfigSpec.DoubleValue ANDROID_CHARGED;
    public static final ModConfigSpec.IntValue ANDROID_PAD_RADIUS;
    public static final ModConfigSpec.IntValue ANDROID_GUARD_RADIUS;
    public static final ModConfigSpec.IntValue ANDROID_HOLD_RADIUS;
    public static final ModConfigSpec.IntValue ANDROID_THINK_TICKS;
    public static final java.util.Map<com.projectrebirth.bodies.BodyClass, AndroidStats> ANDROID_STATS = new java.util.EnumMap<>(com.projectrebirth.bodies.BodyClass.class);

    /** How an android of one body class moves and fights (from the worst to the best). */
    public record AndroidStats(ModConfigSpec.DoubleValue speed, ModConfigSpec.IntValue reaction, ModConfigSpec.DoubleValue inaccuracy,
                               ModConfigSpec.IntValue sight, ModConfigSpec.IntValue meleeCooldown, ModConfigSpec.DoubleValue workSpeed) {
    }

    public static final ModConfigSpec.BooleanValue WORK_MINE;
    public static final ModConfigSpec.BooleanValue WORK_LUMBER;
    public static final ModConfigSpec.BooleanValue WORK_FARM;
    public static final ModConfigSpec.DoubleValue WORK_ENERGY_PER_BLOCK;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> WORK_ORE_BLOCKS;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> WORK_FILLER_BLOCKS;
    public static final ModConfigSpec.BooleanValue WORK_REPLANT_SAPLINGS;
    public static final ModConfigSpec.BooleanValue WORK_CUT_LEAVES;
    public static final ModConfigSpec.BooleanValue WORK_TILL;
    public static final ModConfigSpec.IntValue WORK_KEEP_SEEDS;
    public static final ModConfigSpec.IntValue WORK_REST_SECONDS;
    public static final ModConfigSpec.IntValue WORK_FLEE_SECONDS;

    public static final ModConfigSpec.BooleanValue TURRETS_ENABLED;
    public static final ModConfigSpec.IntValue TURRET_MOUNT_CAPACITY;
    public static final ModConfigSpec.DoubleValue TURRET_TURN;
    public static final ModConfigSpec.IntValue TURRET_LOOK_TICKS;
    public static final ModConfigSpec.IntValue TURRET_SENSOR_RANGE;
    public static final ModConfigSpec.DoubleValue TURRET_STABILIZER_SPREAD;
    public static final ModConfigSpec.DoubleValue TURRET_DUST_SPREAD;
    public static final ModConfigSpec.DoubleValue TURRET_EMI_SPREAD;
    public static final ModConfigSpec.DoubleValue TURRET_EMI_SHUTDOWN_CHANCE;
    public static final ModConfigSpec.DoubleValue MACHINE_PLATING_COPPER;
    public static final ModConfigSpec.DoubleValue MACHINE_PLATING_IRON;
    public static final ModConfigSpec.DoubleValue MACHINE_PLATING_TITANIUM;
    public static final ModConfigSpec.IntValue GUN_TURRET_RANGE;
    public static final ModConfigSpec.DoubleValue GUN_TURRET_DAMAGE;
    public static final ModConfigSpec.DoubleValue GUN_TURRET_SPREAD;
    public static final ModConfigSpec.IntValue GUN_TURRET_BURST;
    public static final ModConfigSpec.IntValue GUN_TURRET_SHOT_TICKS;
    public static final ModConfigSpec.IntValue GUN_TURRET_PAUSE_TICKS;
    public static final ModConfigSpec.IntValue GUN_TURRET_LOCK_TICKS;
    public static final ModConfigSpec.IntValue GUN_TURRET_ENERGY;
    public static final ModConfigSpec.IntValue ROCKET_TURRET_RANGE;
    public static final ModConfigSpec.IntValue ROCKET_TURRET_MIN_RANGE;
    public static final ModConfigSpec.DoubleValue ROCKET_TURRET_POWER;
    public static final ModConfigSpec.BooleanValue ROCKET_TURRET_BREAKS;
    public static final ModConfigSpec.IntValue ROCKET_TURRET_INTERVAL;
    public static final ModConfigSpec.IntValue ROCKET_TURRET_LOCK_TICKS;
    public static final ModConfigSpec.IntValue ROCKET_TURRET_ENERGY;
    public static final ModConfigSpec.DoubleValue ROCKET_TURRET_FRIEND_RADIUS;
    public static final ModConfigSpec.DoubleValue ROCKET_TURRET_STRUCTURAL;
    public static final ModConfigSpec.DoubleValue ROCKET_TURRET_CRITICAL;

    public static final ModConfigSpec.BooleanValue MECHS_ENABLED;
    public static final ModConfigSpec.DoubleValue GUARD_MECH_SPEED;
    public static final ModConfigSpec.IntValue GUARD_MECH_ENERGY_CAPACITY;
    public static final ModConfigSpec.DoubleValue GUARD_MECH_ENERGY_IDLE;
    public static final ModConfigSpec.DoubleValue GUARD_MECH_ENERGY_MOVING;
    public static final ModConfigSpec.IntValue GUARD_MECH_ENERGY_SHOT;
    public static final ModConfigSpec.IntValue GUARD_MECH_ROUNDS;
    public static final ModConfigSpec.IntValue GUARD_MECH_ROCKETS;
    public static final ModConfigSpec.IntValue GUARD_MECH_RANGE;
    public static final ModConfigSpec.DoubleValue GUARD_MECH_DAMAGE;
    public static final ModConfigSpec.DoubleValue GUARD_MECH_SPREAD;
    public static final ModConfigSpec.IntValue GUARD_MECH_BURST;
    public static final ModConfigSpec.IntValue GUARD_MECH_SHOT_TICKS;
    public static final ModConfigSpec.IntValue GUARD_MECH_PAUSE_TICKS;
    public static final ModConfigSpec.IntValue GUARD_MECH_ROCKET_TICKS;
    public static final ModConfigSpec.DoubleValue GUARD_MECH_LOW_ENERGY;
    public static final ModConfigSpec.DoubleValue GUARD_MECH_CHARGED;
    public static final ModConfigSpec.IntValue GUARD_MECH_GUARD_RADIUS;

    public static final ModConfigSpec.BooleanValue POST_ENABLED;
    public static final ModConfigSpec.IntValue POST_ENERGY_CAPACITY;
    public static final ModConfigSpec.IntValue POST_CHARGE_PER_SECOND;
    public static final ModConfigSpec.IntValue POST_REPAIR_SECONDS;
    public static final ModConfigSpec.DoubleValue POST_PLATE_REPAIR;
    public static final ModConfigSpec.IntValue POST_RADIUS;
    public static final ModConfigSpec.BooleanValue GUARD_MECH_JETPACK;
    public static final ModConfigSpec.DoubleValue GUARD_MECH_FLIGHT_ENERGY;
    public static final ModConfigSpec.IntValue GUARD_MECH_FLIGHT_SECONDS;
    public static final ModConfigSpec.IntValue GUARD_MECH_FLIGHT_REST;
    public static final ModConfigSpec.DoubleValue GUARD_MECH_HOVER_HEIGHT;
    public static final ModConfigSpec.IntValue GUARD_MECH_HOP_RANGE;
    public static final ModConfigSpec.IntValue GUARD_MECH_MAX_RISE;
    public static final ModConfigSpec.IntValue GUARD_MECH_COMBAT_FLIGHT_CHANCE;
    public static final ModConfigSpec.DoubleValue GUARD_MECH_STOMP_DAMAGE;
    public static final ModConfigSpec.DoubleValue GUARD_MECH_STOMP_RADIUS;
    public static final ModConfigSpec.IntValue GUARD_MECH_STOMP_COOLDOWN;
    public static final ModConfigSpec.DoubleValue POST_RELOAD_SHARE;
    public static final ModConfigSpec.IntValue POST_AMMO_WAIT_SECONDS;
    public static final ModConfigSpec.IntValue MOVE_STUCK_TICKS;
    public static final ModConfigSpec.DoubleValue MOVE_STUCK_DISTANCE;
    public static final ModConfigSpec.DoubleValue MOVE_STRAIGHT_DISTANCE;
    public static final ModConfigSpec.IntValue MOVE_GIVE_UP_CHECKS;
    public static final ModConfigSpec.IntValue AREA_PATROL_RADIUS;
    public static final ModConfigSpec.IntValue AREA_PATROL_PAUSE;

    public static AndroidStats androidStats(com.projectrebirth.bodies.BodyClass bodyClass) {
        return ANDROID_STATS.get(bodyClass);
    }
    public static final ModConfigSpec.IntValue SELF_DEFENCE_SECONDS;
    public static final ModConfigSpec.IntValue SELF_DEFENCE_RADIUS;
    public static final ModConfigSpec.BooleanValue SCOUTING_ENABLED;
    public static final ModConfigSpec.IntValue SCOUTING_UPDATE_SECONDS;
    public static final ModConfigSpec.IntValue ANALYZER_RADIUS;
    public static final ModConfigSpec.DoubleValue ANALYZER_CLICK_FROM;
    public static final java.util.Map<com.projectrebirth.bodies.BodyClass, ModConfigSpec.IntValue> BODY_SENSOR_RANGE = new java.util.EnumMap<>(com.projectrebirth.bodies.BodyClass.class);
    public static final ModConfigSpec.IntValue ARMOR_SENSOR_RANGE;
    public static final ModConfigSpec.BooleanValue MONITORING_ENABLED;
    public static final ModConfigSpec.IntValue MONITORING_RADIUS;
    public static final ModConfigSpec.IntValue MONITORING_SCAN_SECONDS;
    public static final ModConfigSpec.IntValue MONITORING_HISTORY_DAYS;
    public static final ModConfigSpec.IntValue MONITORING_COMPARATOR_CHUNKS;
    public static final ModConfigSpec.DoubleValue MONITORING_COMPARATOR_FULL;
    public static final ModConfigSpec.IntValue MONITORING_ENERGY_PER_TICK;
    public static final ModConfigSpec.IntValue MONITORING_PRINT_ENERGY;
    public static final ModConfigSpec.IntValue MONITORING_ENERGY_CAPACITY;
    public static final ModConfigSpec.BooleanValue WEATHER_STATION_ENABLED;
    public static final ModConfigSpec.IntValue WEATHER_STATION_FORECAST_MINUTES;
    public static final ModConfigSpec.IntValue WEATHER_STATION_ROUND_MINUTES;
    public static final ModConfigSpec.IntValue WEATHER_STATION_LONG_FORECAST_MINUTES;
    public static final ModConfigSpec.IntValue WEATHER_STATION_RADIUS;
    public static final ModConfigSpec.IntValue WEATHER_STATION_SIGNAL_MINUTES;
    public static final ModConfigSpec.IntValue WEATHER_STATION_ENERGY_PER_TICK;
    public static final ModConfigSpec.IntValue WEATHER_STATION_MODULE_ENERGY_PER_TICK;
    public static final ModConfigSpec.IntValue WEATHER_STATION_ENERGY_CAPACITY;
    public static final ModConfigSpec.IntValue POTION_STACK;
    public static final ModConfigSpec.BooleanValue TURRET_ENABLED;
    public static final ModConfigSpec.DoubleValue TURRET_HEALTH;
    public static final ModConfigSpec.DoubleValue TURRET_ARMOR;
    public static final ModConfigSpec.DoubleValue TURRET_RANGE;
    public static final ModConfigSpec.IntValue TURRET_LOCK_TICKS;
    public static final ModConfigSpec.IntValue TURRET_BURST;
    public static final ModConfigSpec.IntValue TURRET_SHOT_INTERVAL;
    public static final ModConfigSpec.IntValue TURRET_BURST_PAUSE;
    public static final ModConfigSpec.DoubleValue TURRET_DAMAGE;
    public static final ModConfigSpec.DoubleValue TURRET_SPREAD;
    public static final ModConfigSpec.DoubleValue TURRET_TURN_SPEED;
    public static final ModConfigSpec.BooleanValue ROBOT_ENABLED;
    public static final ModConfigSpec.DoubleValue ROBOT_HEALTH;
    public static final ModConfigSpec.DoubleValue ROBOT_ARMOR;
    public static final ModConfigSpec.DoubleValue ROBOT_RANGE;
    public static final ModConfigSpec.DoubleValue ROBOT_MELEE;
    public static final ModConfigSpec.DoubleValue ROBOT_DAMAGE;
    public static final ModConfigSpec.IntValue ROBOT_BURST;
    public static final ModConfigSpec.IntValue ROBOT_SHOT_INTERVAL;
    public static final ModConfigSpec.IntValue ROBOT_BURST_PAUSE;
    public static final ModConfigSpec.DoubleValue ROBOT_SPREAD;
    public static final ModConfigSpec.DoubleValue ROBOT_SPEED;
    public static final ModConfigSpec.IntValue ROBOT_RADIUS;
    public static final ModConfigSpec.BooleanValue MECH_ENABLED;
    public static final ModConfigSpec.DoubleValue MECH_HEALTH;
    public static final ModConfigSpec.DoubleValue MECH_ARMOR;
    public static final ModConfigSpec.DoubleValue MECH_RANGE;
    public static final ModConfigSpec.DoubleValue MECH_DAMAGE;
    public static final ModConfigSpec.IntValue MECH_BURST;
    public static final ModConfigSpec.IntValue MECH_SHOT_INTERVAL;
    public static final ModConfigSpec.IntValue MECH_BURST_PAUSE;
    public static final ModConfigSpec.DoubleValue MECH_SPREAD;
    public static final ModConfigSpec.DoubleValue MECH_SPEED;
    public static final ModConfigSpec.IntValue MECH_RADIUS;
    public static final ModConfigSpec.IntValue MECH_ROCKET_INTERVAL;
    public static final ModConfigSpec.IntValue MECH_ROCKET_WARNING;
    public static final ModConfigSpec.IntValue MECH_ROCKETS;
    public static final ModConfigSpec.DoubleValue MECH_ROCKET_POWER;
    public static final ModConfigSpec.BooleanValue MECH_ROCKETS_BREAK;
    public static final ModConfigSpec.DoubleValue MECH_STOMP_DAMAGE;
    public static final ModConfigSpec.IntValue MECH_BOSS_BAR_RANGE;
    public static final ModConfigSpec.DoubleValue MECH_STOMP_RADIUS;
    public static final ModConfigSpec.BooleanValue MUTANTS_ENABLED;
    public static final ModConfigSpec.IntValue MUTANTS_INTERVAL;
    public static final ModConfigSpec.DoubleValue MUTANTS_CHANCE;
    public static final ModConfigSpec.IntValue MUTANTS_CAP;
    public static final ModConfigSpec.IntValue MUTANTS_HOUND_WEIGHT;
    public static final ModConfigSpec.IntValue MUTANTS_GHOUL_WEIGHT;
    public static final ModConfigSpec.IntValue MUTANTS_BRUTE_WEIGHT;
    public static final ModConfigSpec.IntValue MUTANTS_PACK_MIN;
    public static final ModConfigSpec.IntValue MUTANTS_PACK_MAX;
    public static final ModConfigSpec.DoubleValue HOUND_HEALTH;
    public static final ModConfigSpec.DoubleValue HOUND_DAMAGE;
    public static final ModConfigSpec.DoubleValue HOUND_SPEED;
    public static final ModConfigSpec.DoubleValue GHOUL_HEALTH;
    public static final ModConfigSpec.DoubleValue GHOUL_DAMAGE;
    public static final ModConfigSpec.DoubleValue GHOUL_SPEED;
    public static final ModConfigSpec.DoubleValue GHOUL_AURA;
    public static final ModConfigSpec.IntValue GHOUL_AURA_RANGE;
    public static final ModConfigSpec.DoubleValue BRUTE_HEALTH;
    public static final ModConfigSpec.DoubleValue BRUTE_DAMAGE;
    public static final ModConfigSpec.DoubleValue BRUTE_SPEED;
    public static final ModConfigSpec.DoubleValue GHOUL_TOXIN_AURA;
    public static final ModConfigSpec.DoubleValue GHOUL_SPIT_DAMAGE;
    public static final ModConfigSpec.DoubleValue GHOUL_SPIT_TOXIN;
    public static final ModConfigSpec.IntValue GHOUL_SPIT_INTERVAL;
    public static final ModConfigSpec.DoubleValue PUDDLE_TOXIN;
    public static final ModConfigSpec.IntValue PUDDLE_RANGE;
    public static final ModConfigSpec.IntValue PUDDLE_LIFETIME;
    public static final ModConfigSpec.DoubleValue BRUTE_CHARGE_DAMAGE;
    public static final ModConfigSpec.IntValue BRUTE_CHARGE_COOLDOWN;
    public static final ModConfigSpec.IntValue BRUTE_LEAP_COOLDOWN;
    public static final ModConfigSpec.DoubleValue BRUTE_TOSS;
    public static final ModConfigSpec.DoubleValue CORPSE_RADIATION;
    public static final ModConfigSpec.IntValue CORPSE_RANGE;
    public static final ModConfigSpec.IntValue CORPSE_LIFETIME;
    public static final ModConfigSpec.DoubleValue SOURCES_WALL_FACTOR;
    public static final ModConfigSpec.DoubleValue DEBRIS_RADIATION;
    public static final ModConfigSpec.IntValue DEBRIS_RANGE;
    public static final ModConfigSpec.DoubleValue SLUDGE_RADIATION;
    public static final ModConfigSpec.IntValue SLUDGE_RANGE;
    public static final ModConfigSpec.DoubleValue BARREL_TOXIN;
    public static final ModConfigSpec.IntValue BARREL_RANGE;
    public static final ModConfigSpec.BooleanValue URANIUM_ORE_RADIATES;
    public static final ModConfigSpec.DoubleValue URANIUM_ORE_RADIATION;
    public static final ModConfigSpec.IntValue URANIUM_ORE_RANGE;
    public static final ModConfigSpec.DoubleValue URANIUM_ORE_MAX;
    public static final ModConfigSpec.BooleanValue URANIUM_ITEMS_RADIATE;
    public static final ModConfigSpec.DoubleValue URANIUM_ITEM_RADIATION;
    public static final ModConfigSpec.DoubleValue URANIUM_ITEMS_MAX;
    public static final ModConfigSpec.BooleanValue ISOTOPE_RADIATES;
    public static final ModConfigSpec.DoubleValue ISOTOPE_RADIATION;
    public static final ModConfigSpec.IntValue ISOTOPE_RANGE;
    public static final ModConfigSpec.IntValue ISOTOPE_ENERGY_PER_TICK;
    public static final ModConfigSpec.IntValue ISOTOPE_CAPACITY;
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
        ARMOR_OPENING_SHELL = builder
                .comment("The armor opens before the pilot gets in and out (1.5.2; the combat armor for now). False: in and out at once, as before")
                .define("openingShell", true);
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
        ARMOR_PILOT_ATTACK_SPEED = builder
                .comment("Strikes per second of the pilot's empty armored hands (1.5.2): like a sword (1.6). A click before the next strike is ready does nothing")
                .defineInRange("pilotAttackSpeed", 1.6, 0.1, 20.0);
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
                    .comment("Whether no status effects at all apply to the body (splash and lingering potions, beacons, mobs). Off for the premium body only")
                    .define("immuneToEffects", !bodyClass.defaultLivingBody());
            ModConfigSpec.DoubleValue acidWear = builder
                    .comment("Wear (percent) acid rain adds per second per unit of acid to a body without a sealing module (1.5.0: acid no",
                            "longer takes integrity); better bodies wear more slowly")
                    .defineInRange("acidWearPercentPerSecond", DEFAULT_ACID_WEAR[bodyClass.ordinal()], 0.0, 100.0);
            ModConfigSpec.DoubleValue wear = builder
                    .comment("Wear from work and overheating (see [body_wear]) is multiplied by this: better bodies wear more slowly")
                    .defineInRange("wearMultiplier", DEFAULT_WEAR[bodyClass.ordinal()], 0.0, 100.0);
            ModConfigSpec.DoubleValue emi = builder
                    .comment("Electromagnetic interference reaching the body is multiplied by this (see [environment.emi]): better bodies resist more")
                    .defineInRange("emiSensitivity", DEFAULT_EMI[bodyClass.ordinal()], 0.0, 100.0);
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
                    upgradeSlots, acidWear, energyCapacity, energyUse, cooling, wear, emi));
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
        HUMAN_LAST_STAGE_HEALING_MULTIPLIER = builder.comment("Healing is multiplied by this at the last stage of the dose, the toxic load or the heat or cold stress (1.5.0)")
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

        builder.comment("Contaminated zones (1.6.0): a radiation burst and a toxic storm leave spots of residual contamination around the players,",
                "which fade by themselves. Strengths are in units of the hazard at the centre of a spot (radiation, polluted air). Off: no new spots",
                "and no effect, as in 1.5.2 (what is saved stays and works again when it is switched on)").push("zones");
        ZONES_ENABLED = builder.comment("The whole system (also needs environment.enabled)").define("enabled", true);
        ZONES_RADIATION_MIN_SPOTS = builder.comment("Fewest spots a radiation burst leaves").defineInRange("radiationMinSpots", 3, 0, 64);
        ZONES_RADIATION_MAX_SPOTS = builder.comment("Most spots a radiation burst leaves").defineInRange("radiationMaxSpots", 6, 0, 64);
        ZONES_RADIATION_MIN_RADIUS = builder.comment("Smallest radius of a radiation spot, in chunks").defineInRange("radiationMinRadius", 1, 0, 8);
        ZONES_RADIATION_MAX_RADIUS = builder.comment("Largest radius of a radiation spot, in chunks").defineInRange("radiationMaxRadius", 3, 0, 8);
        ZONES_RADIATION_STRENGTH = builder.comment("Radiation at the centre of a spot per unit of the burst's intensity (the dosimeter marks danger from environment.hud.dangerRadiation)")
                .defineInRange("radiationStrength", 8.0, 0.0, 10000.0);
        ZONES_RADIATION_HALF_LIFE_DAYS = builder.comment("Half-life of residual radiation, in game days (24000 ticks); about 2.4 half-lives until a spot is no longer dangerous")
                .defineInRange("radiationHalfLifeDays", 1.7, 0.01, 1000.0);
        ZONES_TOXIC_MIN_SPOTS = builder.comment("Fewest spots a toxic storm leaves").defineInRange("toxicMinSpots", 2, 0, 64);
        ZONES_TOXIC_MAX_SPOTS = builder.comment("Most spots a toxic storm leaves").defineInRange("toxicMaxSpots", 4, 0, 64);
        ZONES_TOXIC_MIN_RADIUS = builder.comment("Smallest radius of a toxic spot, in chunks").defineInRange("toxicMinRadius", 1, 0, 8);
        ZONES_TOXIC_MAX_RADIUS = builder.comment("Largest radius of a toxic spot, in chunks").defineInRange("toxicMaxRadius", 2, 0, 8);
        ZONES_TOXIC_STRENGTH = builder.comment("Polluted air at the centre of a spot per unit of the storm's intensity (safe air is up to environment.protection.filterSafePollution)")
                .defineInRange("toxicStrength", 6.0, 0.0, 10000.0);
        ZONES_TOXIC_HALF_LIFE_DAYS = builder.comment("Half-life of residual toxins, in game days").defineInRange("toxicHalfLifeDays", 0.6, 0.01, 1000.0);
        ZONES_TOXIC_LOWLAND_WEIGHT = builder.comment("How many times likelier a toxic spot lands in a swamp, by a river or in a lowland than elsewhere (1 = no preference)")
                .defineInRange("toxicLowlandWeight", 3.0, 1.0, 100.0);
        ZONES_LOWLAND_HEIGHT = builder.comment("Blocks above sea level up to which the ground counts as a lowland").defineInRange("lowlandHeight", 8, 0, 256);
        ZONES_MIN_DISTANCE = builder.comment("Spots land at least this far (blocks) from the player they are placed around").defineInRange("minDistance", 32, 0, 1024);
        ZONES_MAX_DISTANCE = builder.comment("... and at most this far, so that players meet them").defineInRange("maxDistance", 128, 1, 4096);
        ZONES_ON_TERRITORY = builder.comment("Whether a spot may land on the territory of a faction (by default not, so that a catastrophe does not make a base unfit)")
                .define("onFactionTerritory", false);
        ZONES_MAX_PER_CHUNK = builder.comment("Most radiation or toxins one chunk holds").defineInRange("maxPerChunk", 40.0, 0.1, 100000.0);
        ZONES_FORGET_BELOW = builder.comment("A chunk whose radiation and toxins both fell below this is forgotten").defineInRange("forgetBelow", 0.05, 0.0001, 100.0);
        ZONES_MARK_THRESHOLD = builder.comment("From this much radiation or toxins a chunk counts as a contaminated zone for the HUD line and the visuals")
                .defineInRange("markThreshold", 0.5, 0.0, 1000.0);
        builder.pop();

        builder.comment("Sources of radiation and toxins (1.6.0): the radioactive debris, the leaking waste barrel, uranium ore, uranium items in the inventory",
                "and the radioisotope generator. Their strength falls off linearly to nothing at the range; each full solid block on the line",
                "between a source and the person lessens it. Units are those of the hazards (radiation, polluted air). Off: no effect, as in 1.5.2.",
                "The uranium ore in the world can be switched off by a data pack (override neoforge/biome_modifier/add_uranium.json with type neoforge:none)").push("sources");
        SOURCES_ENABLED = builder.comment("The block sources (debris, barrel, generator) and uranium (also needs environment.enabled)").define("enabled", true);
        SOURCES_WALL_FACTOR = builder.comment("Each full solid block between a source and a person multiplies the strength by this (0 = one block stops it)")
                .defineInRange("wallFactor", 0.5, 0.0, 1.0);
        DEBRIS_RADIATION = builder.comment("Radiation of radioactive debris right next to it").defineInRange("debrisRadiation", 8.0, 0.0, 10000.0);
        DEBRIS_RANGE = builder.comment("Reach of radioactive debris in blocks").defineInRange("debrisRange", 10, 1, 48);
        SLUDGE_RADIATION = builder.comment("Radiation of one block of irradiated sludge (contamination zone) right next to it; a pool adds up all its blocks").defineInRange("sludgeRadiation", 0.5, 0.0, 10000.0);
        SLUDGE_RANGE = builder.comment("Reach of irradiated sludge in blocks").defineInRange("sludgeRange", 5, 1, 48);
        BARREL_TOXIN = builder.comment("Polluted air right next to a leaking waste barrel").defineInRange("barrelToxin", 5.0, 0.0, 10000.0);
        BARREL_RANGE = builder.comment("Reach of a leaking waste barrel in blocks").defineInRange("barrelRange", 7, 1, 48);
        URANIUM_ORE_RADIATES = builder.comment("Whether uranium ore radiates nearby (weak, short range, no block entity)").define("uraniumOreRadiates", true);
        URANIUM_ORE_RADIATION = builder.comment("Radiation of one uranium ore block right next to it").defineInRange("uraniumOreRadiation", 1.0, 0.0, 10000.0);
        URANIUM_ORE_RANGE = builder.comment("Reach of uranium ore in blocks (a cube around the person is looked through once a second, so at most 3)")
                .defineInRange("uraniumOreRange", 3, 0, 3);
        URANIUM_ORE_MAX = builder.comment("Most radiation all the ore around one place adds up to").defineInRange("uraniumOreMax", 2.5, 0.0, 10000.0);
        URANIUM_ITEMS_RADIATE = builder.comment("Whether raw uranium and uranium ingots (item tag projectrebirth:radioactive) radiate in a person's inventory").define("uraniumItemsRadiate", true);
        URANIUM_ITEM_RADIATION = builder.comment("Radiation each such item adds to the person who carries it").defineInRange("uraniumItemRadiation", 0.03, 0.0, 10000.0);
        URANIUM_ITEMS_MAX = builder.comment("Most radiation the carried items add up to").defineInRange("uraniumItemsMax", 4.0, 0.0, 10000.0);
        ISOTOPE_RADIATES = builder.comment("Whether the radioisotope generator radiates around itself").define("generatorRadiates", true);
        ISOTOPE_RADIATION = builder.comment("Radiation of the radioisotope generator right next to it").defineInRange("generatorRadiation", 3.0, 0.0, 10000.0);
        ISOTOPE_RANGE = builder.comment("Reach of the radioisotope generator in blocks").defineInRange("generatorRange", 6, 1, 48);
        builder.pop();
        builder.comment("Structures (1.6.0): an abandoned laboratory, industrial ruins, a ruined reactor and the large structures (military base, science complex,",
                "abandoned factory, communication station, defensive outpost, contamination zone) in newly generated chunks.",
                "Switched off, none is placed in new chunks and /locate finds none; what was generated stays. How often and in which biomes: data",
                "(worldgen/structure_set, tags/worldgen/biome/has_structure). A data pack can also remove a structure set.").push("structures");
        STRUCTURES_ENABLED = builder.comment("All the structures of 1.6.0").define("enabled", true);
        STRUCTURES_SAFE_RADIUS = builder.comment("No structure of the mod starts within this many blocks of the middle of the world (x 0, z 0), where",
                "the world spawn is: a new player does not walk into turrets, mutants or a reactor in the first minutes. 0 = anywhere").defineInRange("safeRadius", 256, 0, 10000);
        STRUCTURES_LAB = builder.comment("Abandoned laboratory (small)").define("abandonedLab", true);
        STRUCTURES_RUINS = builder.comment("Industrial ruins (medium)").define("industrialRuins", true);
        STRUCTURES_REACTOR = builder.comment("Ruined reactor (large, rare)").define("ruinedReactor", true);
        STRUCTURES_MILITARY = builder.comment("Military base (large, a fortified compound with a garage of wrecked armors)").define("militaryBase", true);
        STRUCTURES_SCIENCE = builder.comment("Science complex (large, laboratories, a server room and an observatory)").define("scienceComplex", true);
        STRUCTURES_FACTORY = builder.comment("Abandoned factory (large, a smelting hall and two chimneys)").define("abandonedFactory", true);
        STRUCTURES_COMM = builder.comment("Communication station (large, a lattice mast and dishes)").define("commStation", true);
        STRUCTURES_OUTPOST = builder.comment("Defensive outpost (medium-large, walls and two towers)").define("defensiveOutpost", true);
        STRUCTURES_ZONE = builder.comment("Contamination zone (large, an ancient arch over irradiated sludge)").define("contaminationZone", true);
        builder.pop();

        builder.comment("Polluted water (1.6.0, block C): water in contaminated chunks, and under the open sky during a toxic storm or acid rain,",
                "is polluted. Swimming in it poisons; bottles and buckets filled there hold polluted water; a water purifier cleans it.").push("water");
        WATER_ENABLED = builder.comment("Polluted water at all").define("enabled", true);
        WATER_STORMS = builder.comment("Whether water under the open sky is polluted during a toxic storm or acid rain").define("stormsPollute", true);
        WATER_SWIM_TOXIN = builder.comment("Toxic load a person takes per second of swimming in polluted water (less through a protective suit)").defineInRange("swimToxin", 4.0, 0.0, 1000.0);
        WATER_SWIM_CONTAMINATION = builder.comment("External contamination gathered per second of swimming in polluted water").defineInRange("swimContamination", 2.0, 0.0, 1000.0);
        WATER_DRINK_TOXIN = builder.comment("Toxic load of one drunk bottle of polluted water").defineInRange("drinkToxin", 15.0, 0.0, 1000.0);
        WATER_BOTTLE_STACK = builder.comment("How many bottles of water (and of polluted water) stack together").defineInRange("waterBottleStack", 16, 1, 99);
        POTION_STACK = builder.comment("How many potions (drinkable, splash, lingering) stack together; the brewing stand still takes one per slot").defineInRange("potionStack", 4, 1, 99);
        builder.push("purifier");
        PURIFIER_TICKS = builder.comment("Ticks a water purifier needs to clean one bottle").defineInRange("ticksPerBottle", 80, 1, 24000);
        PURIFIER_ENERGY_PER_TICK = builder.comment("Energy a water purifier takes per tick of work").defineInRange("energyPerTick", 15, 0, 1_000_000);
        PURIFIER_ENERGY_CAPACITY = builder.comment("Energy a water purifier holds").defineInRange("energyCapacity", 10000, 1, 1_000_000_000);
        PURIFIER_CARTRIDGE_SHARE = builder.comment("Share of a filter cartridge's life one bottle uses (0.02: fifty bottles a cartridge)").defineInRange("cartridgePerBottle", 0.02, 0.0, 1.0);
        PURIFIER_BUCKET_BOTTLES = builder.comment("How many bottles of work one bucket is").defineInRange("bucketBottles", 3, 1, 64);
        builder.pop(2);

        builder.comment("The garrison (1.7.0): multiblock assembly, the AI core and the player's own machines. Switched off, the mod plays as 1.6.0.").push("garrison");
        GARRISON_ENABLED = builder.comment("The whole garrison").define("enabled", true);
        MACHINES_IMMUNE_TO_EFFECTS = builder.comment("1.7.1: no status effects apply to machines (androids, turrets, mechs, power armor, hostile robots)")
                .define("machinesImmuneToEffects", true);
        builder.comment("Multiblocks (block A): a controller checks its construction from a plan in the data, takes energy and materials through its",
                "ports and builds a recipe stage by stage.").push("multiblocks");
        MULTIBLOCKS_ENABLED = builder.comment("Whether multiblocks work").define("enabled", true);
        MULTIBLOCK_ENERGY_CAPACITY = builder.comment("Energy a controller holds").defineInRange("energyCapacity", 200000, 1, 1_000_000_000);
        MULTIBLOCK_ENERGY_RECEIVE = builder.comment("Energy a controller takes per tick through its energy ports").defineInRange("energyReceive", 2000, 1, 1_000_000_000);
        MULTIBLOCK_HOLOGRAM_SECONDS = builder.comment("Seconds the hologram of a construction stays on unless switched off").defineInRange("hologramSeconds", 60, 1, 3600);
        DOCK_ROLLOUT_SPEED = builder.comment("1.7.1: blocks per tick a mech or a turret crate rolls out of the heavy dock").defineInRange("rolloutSpeed", 0.05, 0.01, 1.0);
        builder.pop();
        builder.comment("The player's machines (block C): the AI core, integrity, wrecks, packing, limits and the rules of targets.").push("machines");
        MACHINES_ENABLED = builder.comment("Whether machines can be placed (packed ones stay items when off)").define("enabled", true);
        DAMAGED_CORE_PATROL_ROBOT = builder.comment("Chance that the wreck of a patrol robot of the ruins gives a damaged AI core").defineInRange("damagedCorePatrolRobot", 0.1, 0.0, 1.0);
        DAMAGED_CORE_MECH = builder.comment("Chance that the wreck of the mech of the ruins gives a damaged AI core").defineInRange("damagedCoreMech", 1.0, 0.0, 1.0);
        MACHINE_REGEN_DELAY_SECONDS = builder.comment("Seconds without damage before structural integrity comes back by itself").defineInRange("regenDelaySeconds", 10, 0, 3600);
        MACHINE_REGEN_PER_SECOND = builder.comment("Structural integrity that comes back per second after that (0: none)").defineInRange("regenPerSecond", 1.0, 0.0, 1000.0);
        MACHINE_CRITICAL_EXPLOSION = builder.comment("Critical integrity an explosion takes per point of damage").defineInRange("criticalExplosion", 1.0, 0.0, 100.0);
        MACHINE_CRITICAL_FIRE = builder.comment("Critical integrity fire takes per point of damage").defineInRange("criticalFire", 0.5, 0.0, 100.0);
        MACHINE_RESTART_FRACTION = builder.comment("A shut-down machine starts again once its structural integrity is back to this share").defineInRange("restartFraction", 0.25, 0.0, 1.0);
        MACHINE_REPAIR_PER_INGOT = builder.comment("Structural integrity one iron ingot gives (repair wrench, maintenance station)").defineInRange("repairPerIngot", 10.0, 0.1, 1000.0);
        MACHINE_REPAIR_CRITICAL_PER_PLATE = builder.comment("Critical integrity one armor plate gives at the maintenance station").defineInRange("repairCriticalPerPlate", 10.0, 0.1, 1000.0);
        MACHINE_WEAR_PER_ACTUATOR = builder.comment("Wear (percent) one actuator takes off at the maintenance station").defineInRange("wearPerActuator", 25.0, 0.1, 100.0);
        MACHINE_ACID_WEAR_PER_SECOND = builder.comment("Wear (percent) per second per unit of acid rain on a machine outside").defineInRange("acidWearPerSecond", 0.02, 0.0, 100.0);
        MACHINE_DUST_WEAR_PER_SECOND = builder.comment("Wear (percent) per second per unit of dust on a machine outside").defineInRange("dustWearPerSecond", 0.01, 0.0, 100.0);
        MACHINE_REGISTRY_SECONDS = builder.comment("Seconds between two updates of a machine's line in the world's machine register").defineInRange("registrySeconds", 5, 1, 600);
        MACHINE_SERVER_LIMIT = builder.comment("Machines of all players on the server at most").defineInRange("serverLimit", 200, 0, 100000);
        MACHINE_MANAGER_ROLES = builder.comment("Faction roles that may pack up, repair and command the machines of other members (leader, officer, member, recruit)")
                .defineListAllowEmpty("managerRoles", java.util.List.of("leader", "officer"), () -> "officer", value -> value instanceof String);
        int[][] limits = {{6, 12}, {10, 20}, {200, 200}, {8, 16}, {4, 8}};
        double[][] integrity = {{30.0, 15.0}, {80.0, 40.0}, {250.0, 125.0}, {50.0, 25.0}, {40.0, 20.0}};
        int[] slots = {0, 2, 3, 1, 3};
        for (com.projectrebirth.garrison.MachineKind kind : com.projectrebirth.garrison.MachineKind.values()) {
            builder.comment("Machines of this kind: " + kind.description()).push(kind.id());
            MACHINE_PLAYER_LIMIT.put(kind, builder.comment("At most per player").defineInRange("playerLimit", limits[kind.ordinal()][0], 0, 10000));
            MACHINE_FACTION_LIMIT.put(kind, builder.comment("At most per faction (its members together)").defineInRange("factionLimit", limits[kind.ordinal()][1], 0, 10000));
            MACHINE_MAX_STRUCTURAL.put(kind, builder.comment("Structural integrity").defineInRange("maxStructural", integrity[kind.ordinal()][0], 1.0, 100000.0));
            MACHINE_MAX_CRITICAL.put(kind, builder.comment("Critical integrity").defineInRange("maxCritical", integrity[kind.ordinal()][1], 1.0, 100000.0));
            MACHINE_UPGRADE_SLOTS.put(kind, builder.comment("Upgrade slots (armor upgrades by the mounting tool; androids use their body's)").defineInRange("upgradeSlots", slots[kind.ordinal()], 0, 12));
            builder.pop();
        }
        builder.pop();
        builder.comment("Defence turrets (block G): a machine gun and a rocket turret on a turret mount, which takes energy from cables and ammunition from hoppers.",
                "The machine gun's integrity is [garrison.machines.turret]; the rocket turret has its own below.").push("turrets");
        TURRETS_ENABLED = builder.comment("Whether the player's turrets shoot at all").define("enabled", true);
        TURRET_MOUNT_CAPACITY = builder.comment("Energy a turret mount holds").defineInRange("mountCapacity", 20000, 1, 1_000_000_000);
        TURRET_TURN = builder.comment("Degrees the head turns per tick").defineInRange("turnSpeed", 15.0, 1.0, 180.0);
        TURRET_LOOK_TICKS = builder.comment("Ticks between two looks for a target").defineInRange("lookTicks", 10, 1, 100);
        TURRET_SENSOR_RANGE = builder.comment("Blocks of range a sensor unit adds").defineInRange("sensorRange", 8, 0, 64);
        TURRET_STABILIZER_SPREAD = builder.comment("The spread times this with a stabilizer").defineInRange("stabilizerSpread", 0.5, 0.0, 1.0);
        TURRET_DUST_SPREAD = builder.comment("The spread times this in a dust storm").defineInRange("dustSpread", 1.6, 1.0, 10.0);
        TURRET_EMI_SPREAD = builder.comment("Degrees of spread an EMI storm adds outside a shelter (no EMI screen)").defineInRange("emiSpread", 12.0, 0.0, 90.0);
        TURRET_EMI_SHUTDOWN_CHANCE = builder.comment("Chance each second that an EMI storm shuts a turret down for 2 to 3 seconds").defineInRange("emiShutdownChance", 0.08, 0.0, 1.0);
        MACHINE_PLATING_COPPER = builder.comment("Share of damage armor plating keeps off a turret or a mech: copper").defineInRange("platingCopper", 0.1, 0.0, 0.9);
        MACHINE_PLATING_IRON = builder.comment("... iron").defineInRange("platingIron", 0.2, 0.0, 0.9);
        MACHINE_PLATING_TITANIUM = builder.comment("... titanium").defineInRange("platingTitanium", 0.3, 0.0, 0.9);
        builder.push("machine_gun");
        GUN_TURRET_RANGE = builder.comment("Blocks it sees and shoots at").defineInRange("range", 24, 4, 128);
        GUN_TURRET_DAMAGE = builder.comment("Damage of one bullet").defineInRange("damage", 4.0, 0.0, 1000.0);
        GUN_TURRET_SPREAD = builder.comment("Spread of its bullets in degrees").defineInRange("spread", 3.0, 0.0, 45.0);
        GUN_TURRET_BURST = builder.comment("Bullets in a burst").defineInRange("burst", 5, 1, 100);
        GUN_TURRET_SHOT_TICKS = builder.comment("Ticks between two bullets of a burst").defineInRange("shotTicks", 3, 1, 100);
        GUN_TURRET_PAUSE_TICKS = builder.comment("Ticks between two bursts").defineInRange("pauseTicks", 20, 1, 1000);
        GUN_TURRET_LOCK_TICKS = builder.comment("Ticks it aims at a new target before the first shot").defineInRange("lockTicks", 10, 0, 200);
        GUN_TURRET_ENERGY = builder.comment("Energy one bullet takes from the mount (and one round of assault rifle ammunition)").defineInRange("energyPerShot", 20, 0, 100000);
        builder.pop();
        builder.push("rocket");
        ROCKET_TURRET_RANGE = builder.comment("Blocks it sees and shoots at").defineInRange("range", 32, 8, 128);
        ROCKET_TURRET_MIN_RANGE = builder.comment("It does not shoot closer than this").defineInRange("minRange", 6, 0, 64);
        ROCKET_TURRET_POWER = builder.comment("Power of a rocket's blast (TNT is 4)").defineInRange("power", 2.5, 0.5, 10.0);
        ROCKET_TURRET_BREAKS = builder.comment("Whether its rockets break blocks").define("breaksBlocks", false);
        ROCKET_TURRET_INTERVAL = builder.comment("Ticks between two rockets").defineInRange("intervalTicks", 60, 5, 2000);
        ROCKET_TURRET_LOCK_TICKS = builder.comment("Ticks it aims at a new target before the first rocket").defineInRange("lockTicks", 20, 0, 200);
        ROCKET_TURRET_ENERGY = builder.comment("Energy one rocket takes from the mount (and one rocket)").defineInRange("energyPerShot", 100, 0, 100000);
        ROCKET_TURRET_FRIEND_RADIUS = builder.comment("It holds fire while one of its own (player, ally, machine) is this close to the target").defineInRange("friendRadius", 5.0, 0.0, 32.0);
        ROCKET_TURRET_STRUCTURAL = builder.comment("Structural integrity").defineInRange("maxStructural", 100.0, 1.0, 100000.0);
        ROCKET_TURRET_CRITICAL = builder.comment("Critical integrity").defineInRange("maxCritical", 50.0, 1.0, 100000.0);
        builder.pop(2);
        builder.comment("The guard mech (block H): integrity in [garrison.machines.mech]; two weapon nodes, its own energy and magazine.").push("mech");
        MECHS_ENABLED = builder.comment("Whether the player's mechs act at all").define("enabled", true);
        GUARD_MECH_SPEED = builder.comment("Movement speed (attribute)").defineInRange("speed", 0.2, 0.05, 1.0);
        GUARD_MECH_ENERGY_CAPACITY = builder.comment("Energy (FE) it holds").defineInRange("energyCapacity", 60000, 1, 1_000_000_000);
        GUARD_MECH_ENERGY_IDLE = builder.comment("Energy it uses per second while working").defineInRange("energyPerSecond", 10.0, 0.0, 100000.0);
        GUARD_MECH_ENERGY_MOVING = builder.comment("Energy more per second while it walks").defineInRange("energyMovingPerSecond", 30.0, 0.0, 100000.0);
        GUARD_MECH_ENERGY_SHOT = builder.comment("Energy a bullet or a rocket takes").defineInRange("energyPerShot", 20, 0, 100000);
        GUARD_MECH_ROUNDS = builder.comment("Assault rifle rounds its magazine holds").defineInRange("rounds", 512, 0, 100000);
        GUARD_MECH_ROCKETS = builder.comment("Rockets its magazine holds").defineInRange("rockets", 16, 0, 10000);
        GUARD_MECH_RANGE = builder.comment("Blocks it sees and shoots at").defineInRange("range", 28, 4, 128);
        GUARD_MECH_DAMAGE = builder.comment("Damage of one bullet").defineInRange("damage", 5.0, 0.0, 1000.0);
        GUARD_MECH_SPREAD = builder.comment("Spread of its bullets in degrees").defineInRange("spread", 3.0, 0.0, 45.0);
        GUARD_MECH_BURST = builder.comment("Bullets in a burst of one machine gun node").defineInRange("burst", 6, 1, 100);
        GUARD_MECH_SHOT_TICKS = builder.comment("Ticks between two bullets of a burst").defineInRange("shotTicks", 3, 1, 100);
        GUARD_MECH_PAUSE_TICKS = builder.comment("Ticks between two bursts").defineInRange("pauseTicks", 20, 1, 1000);
        GUARD_MECH_ROCKET_TICKS = builder.comment("Ticks between two rockets of one rocket node").defineInRange("rocketTicks", 70, 5, 2000);
        GUARD_MECH_LOW_ENERGY = builder.comment("Below this share of energy it goes to a service post").defineInRange("lowEnergy", 0.2, 0.0, 1.0);
        GUARD_MECH_CHARGED = builder.comment("It leaves the post at this share").defineInRange("charged", 0.9, 0.05, 1.0);
        GUARD_MECH_GUARD_RADIUS = builder.comment("Blocks around the point it guards in which it fights").defineInRange("guardRadius", 16, 1, 64);
        // 1.7.2, block C: the jetpack and the stomp of its prototype
        GUARD_MECH_JETPACK = builder.comment("Whether it uses its jetpack: to fly over what it cannot walk past, and now and then in a fight").define("jetpack", true);
        GUARD_MECH_FLIGHT_ENERGY = builder.comment("Energy (FE) its jetpack uses per second of flight").defineInRange("flightEnergyPerSecond", 600.0, 0.0, 1_000_000.0);
        GUARD_MECH_FLIGHT_SECONDS = builder.comment("Longest flight in seconds (over a fight it circles its target this long)").defineInRange("flightSeconds", 8, 1, 120);
        GUARD_MECH_FLIGHT_REST = builder.comment("Seconds between two flights").defineInRange("flightRestSeconds", 15, 0, 3600);
        GUARD_MECH_HOVER_HEIGHT = builder.comment("Blocks over its target it hovers in a fight").defineInRange("hoverHeight", 5.0, 1.0, 32.0);
        GUARD_MECH_HOP_RANGE = builder.comment("Blocks it flies at most in one hop over an obstacle").defineInRange("hopRange", 16, 2, 64);
        GUARD_MECH_MAX_RISE = builder.comment("Blocks it rises at most to clear an obstacle; higher than that it does not take off").defineInRange("maxRise", 16, 2, 64);
        GUARD_MECH_COMBAT_FLIGHT_CHANCE = builder.comment("In a fight, one chance in this many each tick that it takes off over its target (it always does",
                "when the target is out of its reach on foot); 0 = never on a whim").defineInRange("combatFlightChance", 200, 0, 100000);
        GUARD_MECH_STOMP_DAMAGE = builder.comment("Damage of its stomp (up close, and when it lands from a flight)").defineInRange("stompDamage", 14.0, 0.0, 1000.0);
        GUARD_MECH_STOMP_RADIUS = builder.comment("Blocks from its body the stomp reaches").defineInRange("stompRadius", 3.0, 0.5, 16.0);
        GUARD_MECH_STOMP_COOLDOWN = builder.comment("Ticks between two stomps").defineInRange("stompCooldownTicks", 60, 5, 2000);
        builder.pop();
        builder.comment("The small robot (1.7.1): a cheap soldier of the heavy dock on the mech's logic, with a built-in machine gun. Integrity in",
                "[garrison.machines.robot]; it charges at a service post or a charging pad.").push("small_robot");
        SMALL_ROBOT_ENABLED = builder.comment("Whether the player's small robots act at all").define("enabled", true);
        SMALL_ROBOT_SPEED = builder.comment("Movement speed (attribute)").defineInRange("speed", 0.27, 0.05, 1.0);
        SMALL_ROBOT_ENERGY_CAPACITY = builder.comment("Energy (FE) it holds").defineInRange("energyCapacity", 20000, 1, 1_000_000_000);
        SMALL_ROBOT_ENERGY_IDLE = builder.comment("Energy it uses per second while working").defineInRange("energyPerSecond", 4.0, 0.0, 100000.0);
        SMALL_ROBOT_ENERGY_MOVING = builder.comment("Energy more per second while it walks").defineInRange("energyMovingPerSecond", 10.0, 0.0, 100000.0);
        SMALL_ROBOT_ENERGY_SHOT = builder.comment("Energy a bullet takes").defineInRange("energyPerShot", 10, 0, 100000);
        SMALL_ROBOT_ROUNDS = builder.comment("Assault rifle rounds its magazine holds").defineInRange("rounds", 192, 0, 100000);
        SMALL_ROBOT_ROCKETS = builder.comment("Rockets (it has no rocket launcher; kept at 0)").defineInRange("rockets", 0, 0, 0);
        SMALL_ROBOT_RANGE = builder.comment("Blocks it sees and shoots at").defineInRange("range", 20, 4, 128);
        SMALL_ROBOT_DAMAGE = builder.comment("Damage of one bullet").defineInRange("damage", 3.5, 0.0, 1000.0);
        SMALL_ROBOT_SPREAD = builder.comment("Spread of its bullets in degrees").defineInRange("spread", 4.0, 0.0, 45.0);
        SMALL_ROBOT_BURST = builder.comment("Bullets in a burst").defineInRange("burst", 4, 1, 100);
        SMALL_ROBOT_SHOT_TICKS = builder.comment("Ticks between two bullets of a burst").defineInRange("shotTicks", 3, 1, 100);
        SMALL_ROBOT_PAUSE_TICKS = builder.comment("Ticks between two bursts").defineInRange("pauseTicks", 24, 1, 1000);
        SMALL_ROBOT_ROCKET_TICKS = builder.comment("Unused (no rockets)").defineInRange("rocketTicks", 70, 5, 2000);
        SMALL_ROBOT_LOW_ENERGY = builder.comment("Below this share of energy it goes to charge").defineInRange("lowEnergy", 0.25, 0.0, 1.0);
        SMALL_ROBOT_CHARGED = builder.comment("It goes back to its order at this share").defineInRange("charged", 0.9, 0.05, 1.0);
        SMALL_ROBOT_GUARD_RADIUS = builder.comment("Blocks around the point it guards in which it fights").defineInRange("guardRadius", 12, 1, 64);
        builder.pop();
        builder.comment("The service post (block H): four post plates and a post console. A machine on the plates is charged from the console's",
                "energy, has its structural integrity mended with iron or armor plates from a chest next to the console and its magazine filled from it.").push("service_post");
        POST_ENABLED = builder.comment("Whether service posts work").define("enabled", true);
        POST_ENERGY_CAPACITY = builder.comment("Energy the post console holds").defineInRange("energyCapacity", 100000, 1, 1_000_000_000);
        POST_CHARGE_PER_SECOND = builder.comment("Energy it gives a machine per second").defineInRange("chargePerSecond", 2000, 1, 1_000_000);
        POST_REPAIR_SECONDS = builder.comment("Seconds between two pieces it uses for repair").defineInRange("repairSeconds", 2, 1, 600);
        POST_PLATE_REPAIR = builder.comment("An armor plate mends this many times what an iron ingot does").defineInRange("plateRepair", 3.0, 1.0, 100.0);
        // 1.7.2: posts (and charging pads) are found among the loaded ones in this radius; the old key "radius" (64) is gone
        POST_RADIUS = builder.comment("Blocks around in which machines look for a service post or a charging pad (among the loaded ones);",
                "with none there they go to their console and report it").defineInRange("searchRadius", 256, 1, 1024);
        POST_RELOAD_SHARE = builder.comment("A mech or a small robot goes to the post when its rounds or rockets fall to this share of its magazine")
                .defineInRange("reloadShare", 0.1, 0.0, 1.0);
        POST_AMMO_WAIT_SECONDS = builder.comment("Seconds it waits on the post for ammunition that does not come (an empty chest) before it goes back")
                .defineInRange("ammoWaitSeconds", 10, 1, 600);
        builder.pop();
        builder.comment("How machines of the garrison find their way (1.7.2): a machine that does not get on is first made to jump, then to step",
                "aside, then (a guard mech) to fly over on its jetpack; when nothing helps it reports to its console that it is stuck.").push("movement");
        MOVE_STUCK_TICKS = builder.comment("Ticks between two checks whether a machine gets on").defineInRange("stuckCheckTicks", 40, 10, 400);
        MOVE_STUCK_DISTANCE = builder.comment("A machine that moved less than this many blocks between two checks is stuck").defineInRange("stuckDistance", 0.5, 0.05, 5.0);
        MOVE_STRAIGHT_DISTANCE = builder.comment("Within this many blocks of its goal a machine walks straight when the way is free (no path needed)")
                .defineInRange("straightDistance", 8.0, 0.0, 32.0);
        MOVE_GIVE_UP_CHECKS = builder.comment("Checks in a row without getting on after which the machine reports that it is stuck").defineInRange("stuckReportChecks", 4, 1, 100);
        AREA_PATROL_RADIUS = builder.comment("The order \"area patrol\" (1.7.2): blocks round the group's middle in which its machines spread out and walk")
                .defineInRange("areaPatrolRadius", 50, 4, 256);
        AREA_PATROL_PAUSE = builder.comment("Longest pause in seconds a machine of an area patrol makes at a point before it walks on").defineInRange("areaPatrolPauseSeconds", 6, 0, 600);
        builder.pop();
        builder.comment("The rules by which machines choose targets.").push("targeting");
        ALLOW_INTRUDER_MODE = builder.comment("Whether a console may turn on the intruder mode: attack any outsider on the faction's own land, in peace too").define("allowIntruderMode", true);
        SELF_DEFENCE_SECONDS = builder.comment("Seconds a machine remembers who attacked it, its owner or a member of its faction nearby").defineInRange("selfDefenceSeconds", 30, 1, 3600);
        SELF_DEFENCE_RADIUS = builder.comment("Blocks around an attacked member within which machines of the faction take note").defineInRange("selfDefenceRadius", 24, 1, 128);
        builder.pop();
        builder.comment("The console and the tactical tablet (block D): linking machines, orders, groups, alarms.").push("console");
        CONSOLE_ENABLED = builder.comment("Whether consoles work").define("enabled", true);
        CONSOLE_RANGE = builder.comment("Blocks between a console and its machines (and a tablet giving orders) at most; the same dimension only").defineInRange("range", 256, 16, 4096);
        CONSOLE_MAX_MACHINES = builder.comment("Machines one console commands at most").defineInRange("maxMachines", 64, 1, 1024);
        CONSOLE_LINK_RADIUS = builder.comment("A machine set up this close to a console of its owner's (or faction's) links to it by itself").defineInRange("linkRadius", 32, 0, 256);
        CONSOLE_ENERGY_CAPACITY = builder.comment("Energy a console holds").defineInRange("energyCapacity", 20000, 1, 1_000_000_000);
        CONSOLE_ENERGY_PER_TICK = builder.comment("Energy a console uses per tick; without it no orders go out and the window shows the last known state").defineInRange("energyPerTick", 4, 0, 100000);
        CONSOLE_COMBAT_SECONDS = builder.comment("Seconds without damage after which a machine's fight is over (the alarm ends)").defineInRange("combatSeconds", 20, 1, 3600);
        CONSOLE_INTRUDER_RADIUS = builder.comment("Blocks around a machine on its faction's land in which an outsider raises the intruder alarm").defineInRange("intruderRadius", 16, 1, 128);
        CONSOLE_WEATHER_RADIUS = builder.comment("Blocks around a console in which it finds a weather station (for its 'take cover' link)").defineInRange("weatherRadius", 48, 1, 256);
        CONSOLE_WEATHER_SECONDS = builder.comment("Seconds before a forecast catastrophe the linked console orders its machines to take cover").defineInRange("weatherSeconds", 60, 0, 3600);
        CONSOLE_MAP_STATION_RADIUS = builder.comment("Blocks around a console in which a monitoring station lends its contamination layer to the console's map").defineInRange("mapStationRadius", 64, 0, 512);
        TABLET_ROUTE_POINTS = builder.comment("Points a patrol route holds at most").defineInRange("routePoints", 16, 2, 64);
        WORK_AREA_MINE = builder.comment("A miner's area at most this many blocks each side").defineInRange("mineArea", 16, 1, 128);
        WORK_AREA_MINE_DEPTH = builder.comment("A miner's area at most this many blocks deep").defineInRange("mineDepth", 64, 1, 384);
        WORK_AREA_LUMBER = builder.comment("A lumberjack's area at most this many blocks each side").defineInRange("lumberArea", 32, 1, 128);
        WORK_AREA_FARM = builder.comment("A farmer's area at most this many blocks each side").defineInRange("farmArea", 32, 1, 128);
        builder.pop();
        builder.comment("Androids (block E): artificial bodies run by an AI core. Their structural integrity is their body's integrity.").push("androids");
        ANDROID_CRITICAL_SHARE = builder.comment("Critical integrity as a share of the body's integrity").defineInRange("criticalShare", 0.5, 0.05, 2.0);
        ANDROID_ENERGY_ACTIVE = builder.comment("Charge (FE) a working android uses per second, times its body class's use").defineInRange("energyPerSecond", 5.0, 0.0, 100000.0);
        ANDROID_ENERGY_MOVING = builder.comment("Charge (FE) more per second while it moves").defineInRange("energyMovingPerSecond", 15.0, 0.0, 100000.0);
        ANDROID_LOW_CHARGE = builder.comment("Below this share of charge an android goes to the nearest charging pad").defineInRange("lowCharge", 0.2, 0.0, 1.0);
        ANDROID_CHARGED = builder.comment("It leaves the pad at this share").defineInRange("charged", 0.9, 0.05, 1.0);
        ANDROID_PAD_RADIUS = builder.comment("Unused since 1.7.2: pads are looked for as far as service posts ([garrison.service_post] searchRadius)").defineInRange("padRadius", 32, 1, 128);
        ANDROID_GUARD_RADIUS = builder.comment("Blocks around the point it guards in which it fights").defineInRange("guardRadius", 12, 1, 64);
        ANDROID_HOLD_RADIUS = builder.comment("Blocks it may leave its place to fight when it holds a position").defineInRange("holdRadius", 4, 0, 32);
        ANDROID_THINK_TICKS = builder.comment("Ticks between two looks for a target").defineInRange("thinkTicks", 10, 1, 100);
        double[] speeds = {0.24, 0.26, 0.28, 0.30, 0.32};
        int[] reactions = {16, 13, 10, 7, 4};
        double[] inaccuracies = {10.0, 8.0, 6.0, 4.0, 2.0};
        int[] sights = {16, 20, 24, 28, 32};
        int[] cooldowns = {24, 22, 20, 18, 16};
        double[] workSpeeds = {0.6, 0.75, 0.9, 1.05, 1.2};
        for (com.projectrebirth.bodies.BodyClass bodyClass : com.projectrebirth.bodies.BodyClass.values()) {
            int i = Math.min(bodyClass.ordinal(), speeds.length - 1);
            builder.push(bodyClass.getSerializedName());
            ANDROID_STATS.put(bodyClass, new AndroidStats(
                    builder.comment("Movement speed (attribute)").defineInRange("speed", speeds[i], 0.05, 1.0),
                    builder.comment("Ticks it takes to answer a new target").defineInRange("reaction", reactions[i], 0, 100),
                    builder.comment("Spread of its arrows (0: dead on)").defineInRange("inaccuracy", inaccuracies[i], 0.0, 30.0),
                    builder.comment("Blocks it sees targets at").defineInRange("sight", sights[i], 4, 64),
                    builder.comment("Ticks between two blows").defineInRange("meleeCooldown", cooldowns[i], 4, 100),
                    builder.comment("Work speed (mining, felling, harvesting) as a share of a player's with the same tool").defineInRange("workSpeed", workSpeeds[i], 0.05, 10.0)));
            builder.pop();
        }
        builder.comment("Work androids (block F): a miner, a lumberjack and a farmer in an area marked with the tactical tablet.").push("work");
        WORK_MINE = builder.comment("Whether work androids mine").define("mine", true);
        WORK_LUMBER = builder.comment("Whether work androids fell trees").define("lumber", true);
        WORK_FARM = builder.comment("Whether work androids farm").define("farm", true);
        WORK_ENERGY_PER_BLOCK = builder.comment("Charge (FE) a block broken or harvested takes, times the body class's use").defineInRange("energyPerBlock", 2.0, 0.0, 100000.0);
        WORK_ORE_BLOCKS = builder.comment("Blocks the miner keeps in the 'ores only' work besides the tag c:ores").defineListAllowEmpty("oreBlocks",
                java.util.List.of("minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:amethyst_cluster", "minecraft:raw_iron_block",
                        "minecraft:raw_copper_block", "minecraft:raw_gold_block"), () -> "minecraft:obsidian", o -> o instanceof String);
        WORK_FILLER_BLOCKS = builder.comment("Blocks of its gear the miner plugs water and lava with (it keeps a stack of them)").defineListAllowEmpty("fillerBlocks",
                java.util.List.of("minecraft:cobblestone", "minecraft:cobbled_deepslate", "minecraft:dirt", "minecraft:netherrack", "minecraft:stone",
                        "minecraft:deepslate", "minecraft:andesite", "minecraft:diorite", "minecraft:granite", "minecraft:tuff"), () -> "minecraft:cobblestone",
                o -> o instanceof String);
        WORK_REPLANT_SAPLINGS = builder.comment("Whether the lumberjack plants a sapling where a tree stood").define("replantSaplings", true);
        WORK_CUT_LEAVES = builder.comment("Whether the lumberjack cuts the leaves of a felled tree (else they fall by themselves and it picks up what drops)").define("cutLeaves", true);
        WORK_TILL = builder.comment("Whether the farmer tills dirt and grass in its area with a hoe in hand (off: it only harvests and replants)").define("till", false);
        WORK_KEEP_SEEDS = builder.comment("Seeds, saplings or fillers of one kind it keeps in its gear when it hands the rest in").defineInRange("keepSeeds", 16, 0, 64);
        WORK_REST_SECONDS = builder.comment("Seconds it waits before it looks again when there is nothing to do, the chest is full or it has no tool").defineInRange("restSeconds", 30, 1, 3600);
        WORK_FLEE_SECONDS = builder.comment("Seconds it runs to its chest when attacked").defineInRange("fleeSeconds", 10, 1, 600);
        builder.pop();
        builder.pop(2);

        builder.comment("Scouting in the field (1.6.0, block F): the field analyzer in hand, the sensor module of bodies and the sensor unit of",
                "power armor show the contamination around on a small map; the sensors also mark radiation sources and ruins.").push("scouting");
        SCOUTING_ENABLED = builder.comment("Whether the analyzer and the sensors work at all").define("enabled", true);
        SCOUTING_UPDATE_SECONDS = builder.comment("Seconds between two readings sent to a player").defineInRange("updateSeconds", 2, 1, 60);
        ANALYZER_RADIUS = builder.comment("Chunks each way the field analyzer shows around the player (2: a grid of 5 by 5)").defineInRange("analyzerRadius", 2, 0, 6);
        ANALYZER_CLICK_FROM = builder.comment("From this much radiation of sources (blocks, ore, creatures) at the player the analyzer clicks").defineInRange("analyzerClickFrom", 0.05, 0.0, 1000.0);
        builder.comment("Chunks each way the sensor module of a body shows, by body class, from the worst to the best").push("body_sensor_range");
        int[] ranges = {2, 3, 4, 5, 6};
        for (com.projectrebirth.bodies.BodyClass bodyClass : com.projectrebirth.bodies.BodyClass.values()) {
            BODY_SENSOR_RANGE.put(bodyClass, builder.defineInRange(bodyClass.getSerializedName(), ranges[Math.min(bodyClass.ordinal(), ranges.length - 1)], 1, 8));
        }
        builder.pop();
        ARMOR_SENSOR_RANGE = builder.comment("Chunks each way the sensor unit of power armor shows").defineInRange("armorSensorRange", 5, 1, 8);
        builder.pop();

        builder.comment("Environmental monitoring station (1.6.0, block E): with energy it scans the contamination of the chunks around",
                "it, shows them on a map with the players and shelters, keeps the peaks of the last days and prints maps on paper.").push("monitoring_station");
        MONITORING_ENABLED = builder.comment("Whether monitoring stations scan at all").define("enabled", true);
        MONITORING_RADIUS = builder.comment("How many chunks around it a station scans, each way (8: a square of 17 by 17 chunks)").defineInRange("radiusChunks", 8, 1, 12);
        MONITORING_SCAN_SECONDS = builder.comment("Seconds between two scans").defineInRange("scanSeconds", 5, 1, 600);
        MONITORING_HISTORY_DAYS = builder.comment("How many game days of peaks the graph keeps").defineInRange("historyDays", 8, 1, 30);
        MONITORING_COMPARATOR_CHUNKS = builder.comment("A comparator reads the strongest contamination this many chunks around the station, each way").defineInRange("comparatorChunks", 1, 0, 12);
        MONITORING_COMPARATOR_FULL = builder.comment("At this much radiation or toxins the comparator gives 15").defineInRange("comparatorFull", 6.0, 0.1, 1000.0);
        MONITORING_ENERGY_PER_TICK = builder.comment("Energy a monitoring station takes per tick").defineInRange("energyPerTick", 15, 0, 1_000_000);
        MONITORING_PRINT_ENERGY = builder.comment("Energy one printed map takes").defineInRange("printEnergy", 500, 0, 1_000_000_000);
        MONITORING_ENERGY_CAPACITY = builder.comment("Energy a monitoring station holds").defineInRange("energyCapacity", 20000, 1, 1_000_000_000);
        builder.pop();

        builder.comment("Weather station (1.6.0, block D): with energy and the open sky above it, it foresees the next catastrophe of its",
                "dimension and tells its owner, the owner's faction and the players around it in the HUD; it gives a redstone signal before",
                "and during a catastrophe.").push("weather_station");
        WEATHER_STATION_ENABLED = builder.comment("Whether weather stations forecast at all").define("enabled", true);
        WEATHER_STATION_FORECAST_MINUTES = builder.comment("How many minutes ahead a station foresees the kind of the next catastrophe (its time only roughly)").defineInRange("forecastMinutes", 10, 1, 600);
        WEATHER_STATION_ROUND_MINUTES = builder.comment("How rough the time of a forecast without the long range module is: a span of this many minutes").defineInRange("roughMinutes", 5, 1, 60);
        WEATHER_STATION_LONG_FORECAST_MINUTES = builder.comment("How many minutes ahead a station with the long range forecast module foresees the catastrophe, with its exact time, intensity and length").defineInRange("longForecastMinutes", 30, 1, 600);
        WEATHER_STATION_RADIUS = builder.comment("Players this many blocks around a station see its forecast in the HUD").defineInRange("radius", 64, 0, 4096);
        WEATHER_STATION_SIGNAL_MINUTES = builder.comment("A station gives a full redstone signal this many minutes before a catastrophe it foresees, and during it").defineInRange("signalMinutes", 5, 0, 600);
        WEATHER_STATION_ENERGY_PER_TICK = builder.comment("Energy a weather station takes per tick").defineInRange("energyPerTick", 10, 0, 1_000_000);
        WEATHER_STATION_MODULE_ENERGY_PER_TICK = builder.comment("Energy the long range forecast module takes on top, per tick").defineInRange("moduleEnergyPerTick", 20, 0, 1_000_000);
        WEATHER_STATION_ENERGY_CAPACITY = builder.comment("Energy a weather station holds").defineInRange("energyCapacity", 20000, 1, 1_000_000_000);
        builder.pop();

        builder.comment("Hostile mechanisms and creatures (1.6.0, part B3): guard turrets of the ruins, sentries, mutants.",
                "Switched off, a mechanism standing in a ruin turns into its broken block and creatures do not appear.").push("hostile");
        HOSTILE_ENABLED = builder.comment("All the hostile mechanisms and creatures").define("enabled", true);
        builder.push("guard_turret");
        TURRET_ENABLED = builder.comment("Guard turrets in the ruins; off, they stand there broken").define("enabled", true);
        TURRET_HEALTH = builder.comment("Health of a guard turret").defineInRange("health", 40.0, 1.0, 10000.0);
        TURRET_ARMOR = builder.comment("Armor points of a guard turret").defineInRange("armor", 8.0, 0.0, 30.0);
        TURRET_RANGE = builder.comment("How far a turret sees and shoots, in blocks (it needs a clear line of sight)").defineInRange("range", 20.0, 2.0, 64.0);
        TURRET_LOCK_TICKS = builder.comment("Ticks from spotting a target (a warning beep) to the first shot").defineInRange("lockTicks", 20, 0, 200);
        TURRET_BURST = builder.comment("Shots in one burst").defineInRange("burst", 3, 1, 20);
        TURRET_SHOT_INTERVAL = builder.comment("Ticks between the shots of a burst").defineInRange("shotInterval", 4, 1, 100);
        TURRET_BURST_PAUSE = builder.comment("Ticks between bursts").defineInRange("burstPause", 30, 1, 400);
        TURRET_DAMAGE = builder.comment("Damage of one bullet").defineInRange("damage", 3.0, 0.0, 1000.0);
        TURRET_SPREAD = builder.comment("Spread of the bullets in degrees").defineInRange("spreadDegrees", 3.0, 0.0, 45.0);
        TURRET_TURN_SPEED = builder.comment("How fast the head turns to its target, degrees per tick").defineInRange("turnSpeed", 12.0, 1.0, 180.0);
        builder.pop();
        builder.comment("Patrol robots: walk round the military base and the outpost, shoot from afar and hit up close").push("patrol_robot");
        ROBOT_ENABLED = builder.comment("Patrol robots in the ruins").define("enabled", true);
        ROBOT_HEALTH = builder.comment("Health").defineInRange("health", 30.0, 1.0, 10000.0);
        ROBOT_ARMOR = builder.comment("Armor points").defineInRange("armor", 6.0, 0.0, 30.0);
        ROBOT_RANGE = builder.comment("How far it sees and shoots, in blocks").defineInRange("range", 16.0, 2.0, 64.0);
        ROBOT_MELEE = builder.comment("Damage of a blow up close").defineInRange("meleeDamage", 5.0, 0.0, 1000.0);
        ROBOT_DAMAGE = builder.comment("Damage of one bullet").defineInRange("damage", 2.5, 0.0, 1000.0);
        ROBOT_BURST = builder.comment("Shots in one burst").defineInRange("burst", 3, 1, 20);
        ROBOT_SHOT_INTERVAL = builder.comment("Ticks between the shots of a burst").defineInRange("shotInterval", 5, 1, 100);
        ROBOT_BURST_PAUSE = builder.comment("Ticks between bursts").defineInRange("burstPause", 40, 1, 400);
        ROBOT_SPREAD = builder.comment("Spread of the bullets in degrees").defineInRange("spreadDegrees", 4.0, 0.0, 45.0);
        ROBOT_SPEED = builder.comment("Walking speed (attribute value; a zombie has 0.23)").defineInRange("speed", 0.25, 0.0, 2.0);
        ROBOT_RADIUS = builder.comment("How far from its post it patrols and follows, in blocks").defineInRange("patrolRadius", 16, 2, 128);
        builder.pop();
        builder.comment("The mech: one heavy walker guarding the military base; a machine gun, rockets with a warning, a stomp up close").push("mech");
        MECH_ENABLED = builder.comment("The mech in the military base").define("enabled", true);
        MECH_HEALTH = builder.comment("Health").defineInRange("health", 300.0, 1.0, 100000.0);
        MECH_ARMOR = builder.comment("Armor points").defineInRange("armor", 15.0, 0.0, 30.0);
        MECH_RANGE = builder.comment("How far it sees and shoots, in blocks").defineInRange("range", 24.0, 2.0, 64.0);
        MECH_DAMAGE = builder.comment("Damage of one machine gun bullet").defineInRange("damage", 3.0, 0.0, 1000.0);
        MECH_BURST = builder.comment("Shots in one machine gun burst").defineInRange("burst", 6, 1, 40);
        MECH_SHOT_INTERVAL = builder.comment("Ticks between the shots of a burst").defineInRange("shotInterval", 3, 1, 100);
        MECH_BURST_PAUSE = builder.comment("Ticks between bursts").defineInRange("burstPause", 50, 1, 400);
        MECH_SPREAD = builder.comment("Spread of the bullets in degrees").defineInRange("spreadDegrees", 5.0, 0.0, 45.0);
        MECH_SPEED = builder.comment("Walking speed (attribute value)").defineInRange("speed", 0.18, 0.0, 2.0);
        MECH_RADIUS = builder.comment("How far from its post it walks and follows, in blocks").defineInRange("guardRadius", 20, 2, 128);
        MECH_ROCKET_INTERVAL = builder.comment("Ticks between rocket salvos").defineInRange("rocketInterval", 160, 20, 2400);
        MECH_ROCKET_WARNING = builder.comment("Ticks of warning (an alarm and a red mark on the ground) before the rockets fly").defineInRange("rocketWarning", 25, 0, 200);
        MECH_ROCKETS = builder.comment("Rockets in a salvo").defineInRange("rockets", 2, 1, 10);
        MECH_ROCKET_POWER = builder.comment("Explosion power of a rocket (a creeper has 3)").defineInRange("rocketPower", 2.0, 0.0, 10.0);
        MECH_ROCKETS_BREAK = builder.comment("Whether the rockets break blocks").define("rocketsBreakBlocks", false);
        MECH_BOSS_BAR_RANGE = builder.comment("Players within this many blocks of the mech see its health bar").defineInRange("bossBarRange", 32, 1, 256);
        MECH_STOMP_DAMAGE = builder.comment("Damage of its stomp to players close by").defineInRange("stompDamage", 10.0, 0.0, 1000.0);
        MECH_STOMP_RADIUS = builder.comment("Reach of the stomp in blocks").defineInRange("stompRadius", 3.5, 0.5, 16.0);
        builder.pop();
        builder.comment("Mutants: they appear around players in contaminated chunks (spots left by catastrophes) and in the contamination zone").push("mutants");
        MUTANTS_ENABLED = builder.comment("Mutants appear at all").define("enabled", true);
        MUTANTS_INTERVAL = builder.comment("Ticks between tries to bring mutants near each player in a contaminated place").defineInRange("spawnInterval", 200, 20, 24000);
        MUTANTS_CHANCE = builder.comment("Chance of one try to succeed").defineInRange("spawnChance", 0.5, 0.0, 1.0);
        MUTANTS_CAP = builder.comment("No new mutants while this many are within 48 blocks of the player").defineInRange("cap", 6, 0, 64);
        MUTANTS_HOUND_WEIGHT = builder.comment("How often a try brings a pack of hounds (weight)").defineInRange("houndWeight", 50, 0, 1000);
        MUTANTS_GHOUL_WEIGHT = builder.comment("How often a try brings a glowing ghoul (weight)").defineInRange("ghoulWeight", 40, 0, 1000);
        MUTANTS_BRUTE_WEIGHT = builder.comment("How often a try brings a brute (weight)").defineInRange("bruteWeight", 10, 0, 1000);
        MUTANTS_PACK_MIN = builder.comment("Fewest hounds in a pack").defineInRange("packMin", 3, 1, 16);
        MUTANTS_PACK_MAX = builder.comment("Most hounds in a pack").defineInRange("packMax", 5, 1, 16);
        HOUND_HEALTH = builder.comment("Mutated hound: health").defineInRange("houndHealth", 24.0, 1.0, 10000.0);
        HOUND_DAMAGE = builder.comment("Mutated hound: bite damage").defineInRange("houndDamage", 5.0, 0.0, 1000.0);
        HOUND_SPEED = builder.comment("Mutated hound: speed (a wolf has 0.3)").defineInRange("houndSpeed", 0.36, 0.0, 2.0);
        GHOUL_HEALTH = builder.comment("Glowing ghoul: health").defineInRange("ghoulHealth", 45.0, 1.0, 10000.0);
        GHOUL_DAMAGE = builder.comment("Glowing ghoul: damage of a blow").defineInRange("ghoulDamage", 7.0, 0.0, 1000.0);
        GHOUL_SPEED = builder.comment("Glowing ghoul: speed (a zombie has 0.23)").defineInRange("ghoulSpeed", 0.19, 0.0, 2.0);
        GHOUL_AURA = builder.comment("Glowing ghoul: radiation right next to it (radioactive debris has 8)").defineInRange("ghoulAura", 6.0, 0.0, 10000.0);
        GHOUL_AURA_RANGE = builder.comment("Glowing ghoul: reach of its radiation in blocks").defineInRange("ghoulAuraRange", 6, 1, 32);
        BRUTE_HEALTH = builder.comment("Brute: health").defineInRange("bruteHealth", 160.0, 1.0, 10000.0);
        BRUTE_DAMAGE = builder.comment("Brute: damage of a blow").defineInRange("bruteDamage", 18.0, 0.0, 1000.0);
        BRUTE_SPEED = builder.comment("Brute: speed").defineInRange("bruteSpeed", 0.21, 0.0, 2.0);
        GHOUL_TOXIN_AURA = builder.comment("Glowing ghoul: polluted air right next to it, within the same reach").defineInRange("ghoulToxinAura", 4.0, 0.0, 10000.0);
        GHOUL_SPIT_DAMAGE = builder.comment("Glowing ghoul: damage of its toxic spit").defineInRange("ghoulSpitDamage", 4.0, 0.0, 1000.0);
        GHOUL_SPIT_TOXIN = builder.comment("Glowing ghoul: toxic load one spit gives a person (less through a protective suit)").defineInRange("ghoulSpitToxin", 12.0, 0.0, 1000.0);
        GHOUL_SPIT_INTERVAL = builder.comment("Glowing ghoul: ticks between spits").defineInRange("ghoulSpitInterval", 70, 10, 1200);
        PUDDLE_TOXIN = builder.comment("Toxic puddle a ghoul leaves: polluted air right next to it").defineInRange("puddleToxin", 5.0, 0.0, 10000.0);
        PUDDLE_RANGE = builder.comment("Toxic puddle: reach in blocks").defineInRange("puddleRange", 4, 1, 32);
        PUDDLE_LIFETIME = builder.comment("Toxic puddle: ticks until it dries up (3600 = 3 minutes)").defineInRange("puddleLifetime", 3600, 20, 240000);
        BRUTE_CHARGE_DAMAGE = builder.comment("Brute: damage of its charge").defineInRange("bruteChargeDamage", 16.0, 0.0, 1000.0);
        BRUTE_CHARGE_COOLDOWN = builder.comment("Brute: ticks between charges").defineInRange("bruteChargeCooldown", 140, 20, 2400);
        BRUTE_LEAP_COOLDOWN = builder.comment("Brute: ticks between leaps").defineInRange("bruteLeapCooldown", 180, 20, 2400);
        BRUTE_TOSS = builder.comment("Brute: how high its blow throws a player (upward speed; 1.0 is about 4 blocks)").defineInRange("bruteToss", 1.05, 0.0, 4.0);
        CORPSE_RADIATION = builder.comment("Body of a brute: radiation right next to it").defineInRange("corpseRadiation", 7.0, 0.0, 10000.0);
        CORPSE_RANGE = builder.comment("Body of a brute: reach of its radiation in blocks").defineInRange("corpseRange", 7, 1, 32);
        CORPSE_LIFETIME = builder.comment("Body of a brute: ticks until it rots away (6000 = 5 minutes)").defineInRange("corpseLifetime", 6000, 20, 240000);
        builder.pop(2);

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

        builder.comment("Electromagnetic interference of an electromagnetic storm (1.5.0). People hardly notice it, apart from their devices;",
                "it disturbs artificial bodies, power armor and the stations of the mod. Inside a sealed room there is none").push("emi");
        EMI_UNDERGROUND = builder.comment("Interference is multiplied by this deep underground (environment.undergroundDepth)").defineInRange("underground", 0.3, 0.0, 1.0);
        EMI_THRESHOLD = builder.comment("From this interference (after shielding and the body's class) it disturbs a body or an armor and the HUD marks it")
                .defineInRange("threshold", 1.0, 0.0, 10000.0);
        EMI_SHIELD = builder.comment("Interference is multiplied by this with the electronics shielding of a body or the EMI screen of an armor")
                .defineInRange("shieldFactor", 0.1, 0.0, 1.0);
        EMI_CHARGE = builder.comment("Extra charge (FE, before the class's energyUseMultiplier) a disturbed body uses per second per unit of interference")
                .defineInRange("chargePerUnit", 2.0, 0.0, 10000.0);
        EMI_FAULT_CHANCE = builder.comment("Chance per second per unit of interference of a short fault of a disturbed body (more at 75% wear)")
                .defineInRange("faultChance", 0.01, 0.0, 1.0);
        EMI_FAULT_WEAR = builder.comment("Wear (percent, before the class's wearMultiplier) each fault adds").defineInRange("faultWear", 0.5, 0.0, 100.0);
        EMI_FAULT_TICKS = builder.comment("How long a fault slows the body down (it never takes control away)").defineInRange("faultTicks", 30, 1, 200);
        EMI_JETPACK_DROPOUT = builder.comment("Chance per tick that a disturbed jetpack gives no thrust").defineInRange("jetpackDropout", 0.35, 0.0, 1.0);
        EMI_FLOODLIGHT_FLICKER = builder.comment("Chance that a disturbed floodlight is dark at each of its updates").defineInRange("floodlightFlicker", 0.5, 0.0, 1.0);
        EMI_SPREAD = builder.comment("A disturbed pilot's weapon spread grows by this part per unit of interference (at most twice the spread)")
                .defineInRange("spreadPerUnit", 0.05, 0.0, 10.0);
        EMI_STATION_FACTOR = builder.comment("How fast the stations of the mod work in an electromagnetic storm outside a sealed room: 0 = they stand still",
                "(progress and materials are kept, no energy is used), 1 = as usual. Generators, batteries, cables, solar panels and life support never stop")
                .defineInRange("stationFactor", 0.0, 0.0, 1.0);
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
        defineEnvironmentEvent(builder, "emi_storm", Map.of(Hazard.EMI, 10.0), 0.5, 1.5, 120, 300);
        builder.pop();
        builder.pop();

        builder.comment("Guns of power armor; each has its own section. Damage is per bullet or pellet (20 ticks = 1 second)").push("weapons");
        WEAPON_HEAVY_HINT_INTERVAL = builder.comment("Outside power armor: ticks between the hints that a weapon is too heavy")
                .defineInRange("heavyHintIntervalTicks", 60, 1, 72000);
        WEAPON_HEAVY_WALK_MULTIPLIER = builder.comment("Outside power armor: walking speed is multiplied by this while a weapon is in hand; 1.0 = no slower")
                .defineInRange("heavyWalkSpeedMultiplier", 1.0, 0.1, 1.0);
        defineWeapon(builder, "assault_rifle", 30, 6.0, 64.0, 3, 1, 1.5, 0.6, 6.0, 0.25, 0.4, 64.0, 1.0, 50, 0.6, 0.3, 0.05, 0.3);
        defineWeapon(builder, "battle_rifle", 10, 18.0, 128.0, 10, 1, 0.5, 0.0, 0.0, 0.25, 0.2, 128.0, 1.0, 50, 0.5, 1.8, 0.0, 0.0);
        defineWeapon(builder, "combat_shotgun", 6, 4.0, 24.0, 20, 8, 6.0, 0.0, 0.0, 0.25, 0.7, 6.0, 0.25, 16, 0.6, 3.5, 0.0, 0.0);
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
        ISOTOPE_ENERGY_PER_TICK = builder
                .comment("Energy the radioisotope generator makes per tick, always, without fuel (1.6.0)")
                .defineInRange("isotopeEnergyPerTick", 24, 1, 1_000_000);
        ISOTOPE_CAPACITY = builder
                .comment("Energy buffer of the radioisotope generator")
                .defineInRange("isotopeCapacity", 40000, 1, 100_000_000);
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
                // gear does nothing against interference: it is about electronics, not the body
                case EMI -> 0.0f;
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
                            ModConfigSpec.DoubleValue coolingMultiplier, ModConfigSpec.DoubleValue wearMultiplier,
                            ModConfigSpec.DoubleValue emiSensitivity) {
    }
}
