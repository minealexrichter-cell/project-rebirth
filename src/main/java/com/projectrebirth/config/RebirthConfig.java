package com.projectrebirth.config;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.powerarmor.ArmorVariant;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side balance config (config/projectrebirth-server.toml).
 */
public final class RebirthConfig {
    public static final ModConfigSpec SERVER_SPEC;
    private static final Map<BodyClass, BodyStats> BODY_STATS = new EnumMap<>(BodyClass.class);
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
    public static final ModConfigSpec.DoubleValue WEAPON_HEAVY_WALK_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue ARMOR_JUMP_PER_AIR_MODULE;
    public static final ModConfigSpec.DoubleValue ARMOR_MAX_JUMP_STRENGTH;
    public static final ModConfigSpec.DoubleValue ARMOR_SAFE_FALL_PER_AIR_MODULE;
    public static final ModConfigSpec.BooleanValue FLOODLIGHT_ENABLED;
    public static final ModConfigSpec.IntValue FLOODLIGHT_RANGE;
    public static final ModConfigSpec.DoubleValue JETPACK_THRUST_SECONDS;
    public static final ModConfigSpec.DoubleValue JETPACK_RECHARGE_SECONDS;
    public static final ModConfigSpec.DoubleValue JETPACK_RISE_SPEED;
    public static final ModConfigSpec.IntValue JETPACK_MAX_HEIGHT;
    public static final ModConfigSpec.IntValue BODY_MODIFICATION_INSTALL_TICKS;
    public static final ModConfigSpec.IntValue BODY_MODIFICATION_ENERGY_PER_TICK;
    public static final ModConfigSpec.DoubleValue MAINTENANCE_BODY_REPAIR_PER_ITEM;
    public static final ModConfigSpec.BooleanValue STATIONS_NEED_ENERGY;
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
            ModConfigSpec.IntValue upgradeSlots = builder
                    .comment("Upgrade slots of the body. If lowered, what is already installed stays, but nothing more fits")
                    .defineInRange("upgradeSlots", bodyClass.defaultUpgradeSlots(), 0, 16);
            builder.pop();
            BODY_STATS.put(bodyClass, new BodyStats(maxHealth, armor, speed, attack, selfRepair, selfRepairDelay, canConsume, immuneToEffects, upgradeSlots));
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

        builder.comment("Guns of power armor; each has its own section. Damage is per bullet or pellet (20 ticks = 1 second)").push("weapons");
        WEAPON_HEAVY_HINT_INTERVAL = builder.comment("Outside power armor: ticks between the hints that a weapon is too heavy")
                .defineInRange("heavyHintIntervalTicks", 60, 1, 72000);
        WEAPON_HEAVY_WALK_MULTIPLIER = builder.comment("Outside power armor: walking speed is multiplied by this while a weapon is in hand; 1.0 = no slower")
                .defineInRange("heavyWalkSpeedMultiplier", 1.0, 0.1, 1.0);
        defineWeapon(builder, "assault_rifle", 30, 2.5, 64.0, 3, 1, 1.5, 0.6, 6.0, 0.25, 0.4, 64.0, 1.0, 50, 0.6, 0.3, 0.05, 0.3);
        defineWeapon(builder, "battle_rifle", 10, 8.0, 128.0, 10, 1, 0.5, 0.0, 0.0, 0.25, 0.2, 128.0, 1.0, 50, 0.5, 1.8, 0.0, 0.0);
        defineWeapon(builder, "combat_shotgun", 6, 1.75, 24.0, 20, 8, 6.0, 0.0, 0.0, 0.25, 0.7, 6.0, 0.25, 16, 0.6, 3.5, 0.0, 0.0);
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

        builder.comment("Energy (NeoForge Energy, FE): generators, battery, cable and what the stations use").push("energy");
        STATIONS_NEED_ENERGY = builder
                .comment("Whether production and maintenance stations need energy. false = they work without it, as in 0.1.0")
                .define("stationsNeedEnergy", true);
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
                            ModConfigSpec.IntValue upgradeSlots) {
    }
}
