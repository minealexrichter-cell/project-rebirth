package com.projectrebirth.core;

import java.util.List;

import com.projectrebirth.config.WastelandConfig;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameRules;

/**
 * 1.9.0: the game rules of the mod, one per world. They are seen in the create world screen (More, Game Rules) and changed with
 * {@code /gamerule} where cheats are on; {@code /rebirth world} lists them. A new world takes the defaults of the server config
 * ([wasteland.defaults], applied once by {@link com.projectrebirth.environment.Wasteland}); after that the world keeps its own.
 */
public final class RebirthGameRules {
    public static final GameRules.Key<GameRules.BooleanValue> WASTELAND_ESCALATION = GameRules.register("rebirthWastelandEscalation",
            GameRules.Category.MISC, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.IntegerValue> WASTELAND_MAX_STAGE = GameRules.register("rebirthWastelandMaxStage",
            GameRules.Category.MISC, GameRules.IntegerValue.create(4));
    public static final GameRules.Key<GameRules.BooleanValue> MUTANT_EVENTS = GameRules.register("rebirthMutantEvents",
            GameRules.Category.SPAWNING, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> MACHINE_MIND = GameRules.register("rebirthMachineMind",
            GameRules.Category.SPAWNING, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.IntegerValue> MACHINE_MIND_MAX_STAGE = GameRules.register("rebirthMachineMindMaxStage",
            GameRules.Category.SPAWNING, GameRules.IntegerValue.create(5));
    public static final GameRules.Key<GameRules.BooleanValue> WANDERERS = GameRules.register("rebirthWanderers",
            GameRules.Category.SPAWNING, GameRules.BooleanValue.create(true));

    /** All of them, in the order {@code /rebirth world} shows them. */
    public static final List<GameRules.Key<?>> ALL = List.of(WASTELAND_ESCALATION, WASTELAND_MAX_STAGE, MUTANT_EVENTS, MACHINE_MIND,
            MACHINE_MIND_MAX_STAGE, WANDERERS);

    private RebirthGameRules() {
    }

    /** Loads the class, which registers the rules: called once from the mod's constructor. */
    public static void init() {
    }

    public static boolean on(MinecraftServer server, GameRules.Key<GameRules.BooleanValue> rule) {
        return server.getGameRules().getBoolean(rule);
    }

    /** The highest stage of the Wasteland, 1 to 4. */
    public static int wastelandMaxStage(MinecraftServer server) {
        return Mth.clamp(server.getGameRules().getInt(WASTELAND_MAX_STAGE), 1, 4);
    }

    /** The highest stage of the machine mind, 1 to 5. */
    public static int machineMindMaxStage(MinecraftServer server) {
        return Mth.clamp(server.getGameRules().getInt(MACHINE_MIND_MAX_STAGE), 1, 5);
    }

    /**
     * Gives the rules of a new world the defaults of the server config, where they differ from the built-in ones (a rule the
     * player set on the create world screen to something else than the built-in default stays as it is).
     */
    public static void applyConfigDefaults(MinecraftServer server) {
        GameRules rules = server.getGameRules();
        setIfDefault(server, rules.getRule(WASTELAND_ESCALATION), true, WastelandConfig.DEFAULT_ESCALATION.get());
        setIfDefault(server, rules.getRule(MUTANT_EVENTS), true, WastelandConfig.DEFAULT_MUTANT_EVENTS.get());
        setIfDefault(server, rules.getRule(MACHINE_MIND), true, WastelandConfig.DEFAULT_MACHINE_MIND.get());
        setIfDefault(server, rules.getRule(WANDERERS), true, WastelandConfig.DEFAULT_WANDERERS.get());
        GameRules.IntegerValue max = rules.getRule(WASTELAND_MAX_STAGE);
        if (max.get() == 4) {
            max.set(WastelandConfig.DEFAULT_MAX_STAGE.get(), server);
        }
        GameRules.IntegerValue mind = rules.getRule(MACHINE_MIND_MAX_STAGE);
        if (mind.get() == 5) {
            mind.set(WastelandConfig.DEFAULT_MIND_MAX_STAGE.get(), server);
        }
    }

    private static void setIfDefault(MinecraftServer server, GameRules.BooleanValue rule, boolean builtIn, boolean wanted) {
        if (rule.get() == builtIn && wanted != builtIn) {
            rule.set(wanted, server);
        }
    }
}
