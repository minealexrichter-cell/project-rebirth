package com.projectrebirth.config;

import java.util.EnumMap;
import java.util.Map;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 1.9.0 «Wasteland»: the numbers of the hostile world, a section [wasteland] of the server config (written into the same
 * file by {@link RebirthConfig}). Whether the escalation, the mutant events, the machine mind and the wanderers happen in a
 * world is decided per world by the game rules of the mod ({@link com.projectrebirth.core.RebirthGameRules}); the defaults
 * here are what a new world starts with.
 */
public final class WastelandConfig {
    /** How hard the Wasteland is: the presets change only numbers. A new value goes to the end of the list. */
    public enum Preset {
        GENTLE, WASTELAND, HELL
    }

    public static ModConfigSpec.EnumValue<Preset> PRESET;
    public static final Map<Preset, PresetStats> PRESETS = new EnumMap<>(Preset.class);

    public static ModConfigSpec.BooleanValue DEFAULT_ESCALATION;
    public static ModConfigSpec.IntValue DEFAULT_MAX_STAGE;
    public static ModConfigSpec.BooleanValue DEFAULT_MUTANT_EVENTS;
    public static ModConfigSpec.BooleanValue DEFAULT_MACHINE_MIND;
    public static ModConfigSpec.IntValue DEFAULT_MIND_MAX_STAGE;
    public static ModConfigSpec.BooleanValue DEFAULT_WANDERERS;

    public static ModConfigSpec.IntValue STAGE2_DAY;
    public static ModConfigSpec.IntValue STAGE3_DAY;
    public static ModConfigSpec.IntValue STAGE4_DAY;
    public static ModConfigSpec.IntValue OLD_WORLD_STAGE;
    /** By stage I–IV: the background of the air, how often catastrophes come, how strong they are. */
    public static final ModConfigSpec.DoubleValue[] STAGE_BACKGROUND = new ModConfigSpec.DoubleValue[4];
    public static final ModConfigSpec.DoubleValue[] STAGE_CATASTROPHE_FREQUENCY = new ModConfigSpec.DoubleValue[4];
    public static final ModConfigSpec.DoubleValue[] STAGE_CATASTROPHE_STRENGTH = new ModConfigSpec.DoubleValue[4];
    /** 1.9.0, block C: how many mutants come by stage (the chance of a try and the limit near a player). */
    public static final ModConfigSpec.DoubleValue[] STAGE_MUTANTS = new ModConfigSpec.DoubleValue[4];

    public static ModConfigSpec.BooleanValue AIR_ENABLED;
    public static ModConfigSpec.DoubleValue AIR_TOXIN;
    public static ModConfigSpec.DoubleValue AIR_ROOF;
    public static ModConfigSpec.DoubleValue AIR_UNDERGROUND;
    public static ModConfigSpec.DoubleValue AIR_NIGHT;
    public static ModConfigSpec.DoubleValue AIR_EXPOSED_CLEARANCE;
    public static ModConfigSpec.IntValue AIR_DEEP_ROCK_Y;
    public static ModConfigSpec.DoubleValue AIR_DEEP_ROCK_DOSE;
    public static ModConfigSpec.DoubleValue AIR_FILTER_USE;
    public static final Map<String, KitStats> KITS = new java.util.HashMap<>();

    public static ModConfigSpec.BooleanValue DOSE_ENABLED;
    public static ModConfigSpec.DoubleValue DOSE_RESIDUAL_SHARE;
    public static ModConfigSpec.DoubleValue DOSE_HEALTH_PER_RESIDUAL;
    public static ModConfigSpec.DoubleValue DOSE_MAX_HEALTH_PENALTY;
    public static ModConfigSpec.DoubleValue DOSE_ANTIRADIN_RESIDUAL;
    public static ModConfigSpec.DoubleValue DOSE_PURGE_RESIDUAL_SHARE;

    public static ModConfigSpec.BooleanValue FOOD_ENABLED;
    public static ModConfigSpec.DoubleValue FOOD_CROP_TOXIN;
    public static ModConfigSpec.DoubleValue FOOD_MEAT_TOXIN;
    public static ModConfigSpec.DoubleValue FOOD_COOKED_SHARE;
    public static ModConfigSpec.DoubleValue FOOD_ZONE_THRESHOLD;
    public static ModConfigSpec.DoubleValue FOOD_CHAMBER_SOLUTION;

    public static ModConfigSpec.BooleanValue WOUND_ENABLED;
    public static ModConfigSpec.IntValue WOUND_SECONDS;
    public static ModConfigSpec.DoubleValue WOUND_TOXIN;
    public static ModConfigSpec.DoubleValue ANTISEPTIC_TOXIN;

    public static ModConfigSpec.DoubleValue SLEEP_SHELTER_TOXIN_SHARE;

    public static ModConfigSpec.BooleanValue WANDERERS_ENABLED;
    public static ModConfigSpec.IntValue WANDERERS_INTERVAL;
    public static ModConfigSpec.DoubleValue WANDERERS_CHANCE;
    public static ModConfigSpec.IntValue WANDERERS_NEAR_RADIUS;
    public static ModConfigSpec.IntValue WANDERERS_MAX_NEAR;
    public static ModConfigSpec.IntValue WANDERERS_WORLD_CAP;
    public static ModConfigSpec.IntValue WANDERERS_DESPAWN_SECONDS;
    public static ModConfigSpec.IntValue WANDERERS_HOSTILE_MINUTES;
    public static ModConfigSpec.DoubleValue WANDERERS_HEALTH_PER_STAGE;
    public static ModConfigSpec.DoubleValue WANDERERS_DAMAGE_PER_STAGE;
    public static ModConfigSpec.DoubleValue WANDERERS_GUN_DAMAGE;
    public static ModConfigSpec.IntValue WANDERERS_GUN_INTERVAL;
    public static ModConfigSpec.IntValue WANDERERS_GUN_RANGE;
    public static ModConfigSpec.IntValue WANDERERS_OFFERS_MIN;
    public static ModConfigSpec.IntValue WANDERERS_OFFERS_MAX;
    public static ModConfigSpec.DoubleValue WANDERERS_DROP_CHANCE;
    public static ModConfigSpec.BooleanValue WANDERERS_CAMPFIRE;
    public static ModConfigSpec.IntValue WANDERERS_CAMPFIRE_MINUTES;

    public static ModConfigSpec.IntValue ANTITOXIN_SECONDS;
    public static ModConfigSpec.DoubleValue ANTITOXIN_SHARE;
    public static ModConfigSpec.DoubleValue BIOFILTER_LIFE;

    private WastelandConfig() {
    }

    /** Called by {@link RebirthConfig} while it builds the server spec. */
    static void define(ModConfigSpec.Builder builder) {
        builder.comment("1.9.0 «Wasteland»: the hostile world. What happens in a world is decided by the game rules of the mod",
                "(rebirthWastelandEscalation and others: the create world screen, More, Game Rules; or /gamerule with cheats).").push("wasteland");
        PRESET = builder.comment("How hard the Wasteland is: GENTLE, WASTELAND or HELL. The presets change only numbers (below, in [wasteland.presets])")
                .defineEnum("preset", Preset.WASTELAND);

        builder.comment("What the game rules of a new world start with (a world keeps its own rules afterwards)").push("defaults");
        DEFAULT_ESCALATION = builder.comment("rebirthWastelandEscalation: the stages of the Wasteland grow with the days of the world").define("escalation", true);
        DEFAULT_MAX_STAGE = builder.comment("rebirthWastelandMaxStage: the highest stage, 1 to 4").defineInRange("maxStage", 4, 1, 4);
        DEFAULT_MUTANT_EVENTS = builder.comment("rebirthMutantEvents: waves, packs, the tide after a burst, the Behemoth").define("mutantEvents", true);
        DEFAULT_MACHINE_MIND = builder.comment("rebirthMachineMind: the machine mind appears and grows").define("machineMind", true);
        DEFAULT_MIND_MAX_STAGE = builder.comment("rebirthMachineMindMaxStage: the highest stage of the machine mind, 1 to 5").defineInRange("machineMindMaxStage", 5, 1, 5);
        DEFAULT_WANDERERS = builder.comment("rebirthWanderers: neutral groups of wanderers walk the world").define("wanderers", true);
        builder.pop();

        builder.comment("Stages I–IV of the Wasteland: Stagnation, Leak, Rot, Decay. They grow with the days of the world (sleeping counts;",
                "/time set does not turn them back)").push("stages");
        STAGE2_DAY = builder.comment("Day from which stage II comes").defineInRange("stage2Day", 10, 0, 100000);
        STAGE3_DAY = builder.comment("Day from which stage III comes").defineInRange("stage3Day", 30, 0, 100000);
        STAGE4_DAY = builder.comment("Day from which stage IV comes").defineInRange("stage4Day", 60, 0, 100000);
        OLD_WORLD_STAGE = builder.comment("A world made before 1.9.0 starts at no higher stage than this, and the days count on from there")
                .defineInRange("oldWorldStage", 2, 1, 4);
        double[] background = {1.0, 1.25, 1.5, 2.0};
        double[] frequency = {1.0, 1.2, 1.4, 1.7};
        double[] strength = {1.0, 1.1, 1.25, 1.4};
        double[] mutants = {1.0, 1.25, 1.5, 2.0};
        for (int i = 0; i < 4; i++) {
            builder.push("stage" + (i + 1));
            STAGE_BACKGROUND[i] = builder.comment("The background of the air is multiplied by this").defineInRange("background", background[i], 0.0, 100.0);
            STAGE_CATASTROPHE_FREQUENCY[i] = builder.comment("Catastrophes come this many times as often (the calm between them is shorter)")
                    .defineInRange("catastropheFrequency", frequency[i], 0.01, 100.0);
            STAGE_CATASTROPHE_STRENGTH[i] = builder.comment("The strength of a catastrophe is multiplied by this").defineInRange("catastropheStrength", strength[i], 0.0, 100.0);
            STAGE_MUTANTS[i] = builder.comment("Mutants: the chance of a try to bring them and the limit near a player are multiplied by this (block C)")
                    .defineInRange("mutants", mutants[i], 0.0, 100.0);
            builder.pop();
        }
        builder.pop();

        builder.comment("The air of the Wasteland: outside a sealed room a person gathers toxins everywhere, on top of the ordinary background",
                "of [environment] and of catastrophes. Each biome multiplies it (the field \"wasteland\" of its environment profile)").push("air");
        AIR_ENABLED = builder.comment("The whole system (also needs environment.enabled)").define("enabled", true);
        AIR_TOXIN = builder.comment("Toxic load per second in the open air of an ordinary biome at stage I").defineInRange("toxinPerSecond", 0.02, 0.0, 100.0);
        AIR_ROOF = builder.comment("Multiplied by this under a roof that is not a sealed room").defineInRange("roofMultiplier", 0.75, 0.0, 1.0);
        AIR_UNDERGROUND = builder.comment("Multiplied by this underground: the air is cleaner there").defineInRange("undergroundMultiplier", 0.25, 0.0, 1.0);
        AIR_NIGHT = builder.comment("Multiplied by this at night in the open: the toxic fog by the ground").defineInRange("nightMultiplier", 1.3, 0.0, 10.0);
        AIR_EXPOSED_CLEARANCE = builder.comment("The body clears its toxins and dose by itself at this share of [environment.human] doseClearance and",
                "toxinClearance while it breathes this air; in a sealed room, in a capsule and in an artificial body at the full rate").defineInRange("exposedClearance", 0.0, 0.0, 1.0);
        AIR_DEEP_ROCK_Y = builder.comment("Underground below this height the deep rock gives off radiation").defineInRange("deepRockY", 0, -64, 320);
        AIR_DEEP_ROCK_DOSE = builder.comment("Dose per second there").defineInRange("deepRockDose", 0.004, 0.0, 100.0);
        AIR_FILTER_USE = builder.comment("Filter used per second in this air (in seconds of filter life): a filter lasts [environment.protection] filterLife / this outside")
                .defineInRange("filterUsePerSecond", 1.0, 0.0, 1000.0);
        defineKit(builder, "respirator", 0.45, 0.0);
        defineKit(builder, "hazmat", 0.5, 0.25);
        defineKit(builder, "heavy_suit", 0.6, 0.35);
        builder.pop();

        builder.comment("The accumulated dose: a share of every dose stays in the bones and lowers the maximum health until the radiation purge",
                "station or antiradin takes it away").push("dose");
        DOSE_ENABLED = builder.comment("The whole system").define("enabled", true);
        DOSE_RESIDUAL_SHARE = builder.comment("Share of each dose gained that accumulates").defineInRange("residualShare", 0.5, 0.0, 1.0);
        DOSE_HEALTH_PER_RESIDUAL = builder.comment("Maximum health lost per unit of accumulated dose").defineInRange("healthPerResidual", 0.1, 0.0, 100.0);
        DOSE_MAX_HEALTH_PENALTY = builder.comment("At most this much maximum health is lost to the dose (with the lower maximum of a very high dose); 16 = 8 hearts")
                .defineInRange("maxHealthPenalty", 16.0, 0.0, 1000.0);
        DOSE_ANTIRADIN_RESIDUAL = builder.comment("Accumulated dose removed by antiradin").defineInRange("antiradinResidual", 40.0, 0.0, 100000.0);
        DOSE_PURGE_RESIDUAL_SHARE = builder.comment("The radiation purge station takes away the accumulated dose at this share of its dose rate")
                .defineInRange("purgeResidualShare", 0.5, 0.0, 100.0);
        builder.pop();

        builder.comment("Contaminated food: a harvest under the open sky during a catastrophe or in a contaminated zone, meat of animals from a zone.",
                "Cooking takes away a part; the water purifier and the decontamination chamber wash it clean").push("food");
        FOOD_ENABLED = builder.comment("The whole system").define("enabled", true);
        FOOD_CROP_TOXIN = builder.comment("Toxic load of contaminated produce when eaten").defineInRange("cropToxin", 15.0, 0.0, 1000.0);
        FOOD_MEAT_TOXIN = builder.comment("Toxic load of contaminated meat when eaten").defineInRange("meatToxin", 25.0, 0.0, 1000.0);
        FOOD_COOKED_SHARE = builder.comment("Share of the toxic load left after cooking").defineInRange("cookedShare", 0.4, 0.0, 1.0);
        FOOD_ZONE_THRESHOLD = builder.comment("Contamination of the ground (radiation + toxins of a zone) from which food from there is contaminated")
                .defineInRange("zoneThreshold", 0.5, 0.0, 1000.0);
        FOOD_CHAMBER_SOLUTION = builder.comment("The decontamination chamber washes one stack of food in the inventory of a person inside each second, for this much",
                "of its solution (in units of contamination: one decontamination solution is [shelter] chamberPerSolution)").defineInRange("chamberSolutionPerStack", 5.0, 0.0, 1000.0);
        builder.pop();

        builder.comment("Wound infection: a blow of a mutant carries the agent into the wound").push("wounds");
        WOUND_ENABLED = builder.comment("The whole system").define("enabled", true);
        WOUND_SECONDS = builder.comment("Seconds an infection lasts (a new blow starts it again)").defineInRange("seconds", 60, 1, 3600);
        WOUND_TOXIN = builder.comment("Toxic load per second while it lasts").defineInRange("toxinPerSecond", 0.15, 0.0, 100.0);
        ANTISEPTIC_TOXIN = builder.comment("Toxic load removed by the antiseptic (it also stops the infection, like the sorbent)").defineInRange("antisepticToxin", 10.0, 0.0, 1000.0);
        builder.pop();

        builder.comment("Sleep").push("sleep");
        SLEEP_SHELTER_TOXIN_SHARE = builder.comment("Share of the toxic load a night of sleep in a sealed room takes away (outside it takes none)")
                .defineInRange("shelterToxinShare", 0.3, 0.0, 1.0);
        builder.pop();

        builder.comment("What is made of the loot of mutants (block C)").push("mutant_loot");
        ANTITOXIN_SECONDS = builder.comment("Seconds of toxin resistance an antitoxin gives (it also stops a wound infection)").defineInRange("antitoxinSeconds", 300, 1, 36000);
        ANTITOXIN_SHARE = builder.comment("Share of the toxins gained that still reach the body under toxin resistance").defineInRange("antitoxinShare", 0.5, 0.0, 1.0);
        BIOFILTER_LIFE = builder.comment("A biofilter lasts this many times as long as an ordinary filter").defineInRange("biofilterLife", 2.0, 0.1, 100.0);
        builder.pop();

        builder.comment("Wanderers (block E): neutral groups of people, androids and robots that walk the world, fight mutants and trade.",
                "Which groups and what they trade is in the data (projectrebirth/wanderer_groups, projectrebirth/wanderer_trades); the game rule",
                "rebirthWanderers switches them per world").push("wanderers");
        WANDERERS_ENABLED = builder.comment("The whole system").define("enabled", true);
        WANDERERS_INTERVAL = builder.comment("Ticks between tries to bring a group near each player").defineInRange("spawnInterval", 1200, 20, 1_000_000);
        WANDERERS_CHANCE = builder.comment("Chance of one try").defineInRange("spawnChance", 0.25, 0.0, 1.0);
        WANDERERS_NEAR_RADIUS = builder.comment("Blocks around a player counted for the limit of groups near him, and beyond which a group with nobody near goes away")
                .defineInRange("nearRadius", 160, 32, 1024);
        WANDERERS_MAX_NEAR = builder.comment("No new group while this many are near a player").defineInRange("maxNear", 1, 0, 16);
        WANDERERS_WORLD_CAP = builder.comment("No new group while this many are loaded in the dimension").defineInRange("worldCap", 6, 0, 256);
        WANDERERS_DESPAWN_SECONDS = builder.comment("A group with no player within nearRadius for this long goes away (as the wandering trader)").defineInRange("despawnSeconds", 60, 1, 36000);
        WANDERERS_HOSTILE_MINUTES = builder.comment("Minutes a group stays hostile to a player who hit one of them (other groups do not care)").defineInRange("hostileMinutes", 10, 0, 1440);
        WANDERERS_HEALTH_PER_STAGE = builder.comment("Health added per stage of the Wasteland above I (a share: 0.15 = +15%)").defineInRange("healthPerStage", 0.15, 0.0, 10.0);
        WANDERERS_DAMAGE_PER_STAGE = builder.comment("Damage added per stage above I").defineInRange("damagePerStage", 0.1, 0.0, 10.0);
        WANDERERS_GUN_DAMAGE = builder.comment("Damage of a shot of an armor's or a robot's gun").defineInRange("gunDamage", 5.0, 0.0, 1000.0);
        WANDERERS_GUN_INTERVAL = builder.comment("Ticks between shots").defineInRange("gunInterval", 16, 1, 1200);
        WANDERERS_GUN_RANGE = builder.comment("Range of the gun, blocks").defineInRange("gunRange", 24, 4, 128);
        WANDERERS_OFFERS_MIN = builder.comment("Fewest offers of a group").defineInRange("offersMin", 4, 0, 16);
        WANDERERS_OFFERS_MAX = builder.comment("Most offers of a group").defineInRange("offersMax", 8, 0, 16);
        WANDERERS_DROP_CHANCE = builder.comment("Chance of each piece of gear of a fallen wanderer to drop").defineInRange("gearDropChance", 0.3, 0.0, 1.0);
        WANDERERS_CAMPFIRE = builder.comment("A camp at evening lights a campfire (taken away after the camp, not before campfireMinutes)").define("campfire", true);
        WANDERERS_CAMPFIRE_MINUTES = builder.comment("The shortest time a campfire of wanderers stays, in minutes, whatever becomes of the group")
                .defineInRange("campfireMinutes", 5, 0, 1440);
        builder.pop();

        builder.comment("The numbers of the presets").push("presets");
        definePreset(builder, Preset.GENTLE, 0.5, 2.0, 0.75);
        definePreset(builder, Preset.WASTELAND, 1.0, 1.0, 1.0);
        definePreset(builder, Preset.HELL, 1.5, 0.5, 1.3);
        builder.pop();
        builder.pop();
    }

    private static void defineKit(ModConfigSpec.Builder builder, String id, double breath, double skin) {
        builder.push(id);
        KITS.put(id, new KitStats(
                builder.comment("Share of the air of the Wasteland kept off by the head piece while its filter lasts").defineInRange("breath", breath, 0.0, 1.0),
                builder.comment("Share kept off through the skin by the chest piece, the trousers and the boots together").defineInRange("skin", skin, 0.0, 1.0)));
        builder.pop();
    }

    private static void definePreset(ModConfigSpec.Builder builder, Preset preset, double background, double days, double catastrophes) {
        builder.push(preset.name().toLowerCase(java.util.Locale.ROOT));
        PRESETS.put(preset, new PresetStats(
                builder.comment("The background of the air is multiplied by this").defineInRange("background", background, 0.0, 100.0),
                builder.comment("The days of the stages are multiplied by this").defineInRange("stageDays", days, 0.01, 100.0),
                builder.comment("Catastrophes come this many times as often").defineInRange("catastropheFrequency", catastrophes, 0.01, 100.0)));
        builder.pop();
    }

    public static PresetStats preset() {
        return PRESETS.get(PRESET.get());
    }

    /** A value of a per-stage table, for stage 1 to 4. */
    public static float byStage(ModConfigSpec.DoubleValue[] table, int stage) {
        return table[Math.max(1, Math.min(4, stage)) - 1].get().floatValue();
    }

    public static KitStats kit(String id) {
        return KITS.get(id);
    }

    public record KitStats(ModConfigSpec.DoubleValue breath, ModConfigSpec.DoubleValue skin) {
    }

    public record PresetStats(ModConfigSpec.DoubleValue background, ModConfigSpec.DoubleValue stageDays, ModConfigSpec.DoubleValue catastropheFrequency) {
    }
}
