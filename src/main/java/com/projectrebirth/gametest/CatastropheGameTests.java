package com.projectrebirth.gametest;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.Catastrophes;
import com.projectrebirth.environment.Environment;
import com.projectrebirth.environment.EnvironmentState;
import com.projectrebirth.environment.EventPhase;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The catastrophes come by themselves: calm, warning, active phase, aftermath (block E of plan 1.3). */
@GameTestHolder(PlanTests.ATMOSPHERE)
@PrefixGameTestTemplate(false)
public class CatastropheGameTests {
    private static final String TEMPLATE = "floor_5x5";

    private static RandomSource random() {
        return RandomSource.create(42L);
    }

    @GameTest(template = TEMPLATE)
    public static void aNewWorldFirstWaitsAWholePause(GameTestHelper helper) {
        EnvironmentState state = new EnvironmentState();
        helper.assertFalse(state.scheduled(), "Nothing counted yet");
        helper.assertTrue(state.tickSecond(random(), true) == EnvironmentState.Change.NONE, "No catastrophe on the first second");
        int min = RebirthConfig.ENVIRONMENT_MIN_CALM_SECONDS.get();
        int max = RebirthConfig.ENVIRONMENT_MAX_CALM_SECONDS.get();
        helper.assertTrue(state.phase() == EventPhase.CALM && state.scheduled() && state.secondsLeft() >= min && state.secondsLeft() <= max,
                "A whole pause first, got " + state.secondsLeft() + " s");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aPauseEndsInAWarningThenTheCatastrophe(GameTestHelper helper) {
        EnvironmentState state = new EnvironmentState();
        state.startCalm(1);
        helper.assertTrue(state.tickSecond(random(), true) == EnvironmentState.Change.WARNING, "The pause ends in a warning");
        String event = state.event();
        helper.assertTrue(Environment.EVENTS.contains(event), "A known catastrophe: " + event);
        RebirthConfig.EnvironmentEventStats stats = RebirthConfig.environmentEvent(event);
        helper.assertTrue(state.intensity() >= stats.minIntensity().get() && state.intensity() <= stats.maxIntensity().get(), "Intensity within the config");
        helper.assertTrue(state.pendingActiveSeconds() >= stats.minActiveSeconds().get() && state.pendingActiveSeconds() <= stats.maxActiveSeconds().get(),
                "Length within the config");
        helper.assertTrue(state.strengthNow() == 0.0f, "A warning does not act yet");
        int warning = state.secondsLeft();
        helper.assertTrue(warning == RebirthConfig.ENVIRONMENT_WARNING_SECONDS.get(), "The warning time");
        EnvironmentState.Change change = EnvironmentState.Change.NONE;
        for (int i = 0; i < warning; i++) {
            change = state.tickSecond(random(), true);
        }
        helper.assertTrue(change == EnvironmentState.Change.ACTIVE && state.phase() == EventPhase.ACTIVE && event.equals(state.event()), "Then it comes");
        int active = state.secondsLeft();
        for (int i = 0; i < active; i++) {
            change = state.tickSecond(random(), true);
        }
        helper.assertTrue(change == EnvironmentState.Change.AFTERMATH, "Then the aftermath");
        int aftermath = state.secondsLeft();
        for (int i = 0; i < aftermath; i++) {
            change = state.tickSecond(random(), true);
        }
        helper.assertTrue(change == EnvironmentState.Change.ENDED && state.phase() == EventPhase.CALM && state.scheduled()
                && state.secondsLeft() >= RebirthConfig.ENVIRONMENT_MIN_CALM_SECONDS.get(), "Then a new pause");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void noCatastrophesWhereTheDimensionHasNone(GameTestHelper helper) {
        EnvironmentState state = new EnvironmentState();
        state.startCalm(1);
        helper.assertTrue(state.tickSecond(random(), false) == EnvironmentState.Change.NONE && state.phase() == EventPhase.CALM, "Only calm");
        helper.assertTrue(state.secondsLeft() >= RebirthConfig.ENVIRONMENT_MIN_CALM_SECONDS.get(), "Another pause");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void onlyAllowedCatastrophesAreChosen(GameTestHelper helper) {
        RandomSource random = random();
        boolean[] seen = new boolean[Environment.EVENTS.size()];
        for (int i = 0; i < 300; i++) {
            String any = EnvironmentState.chooseEvent(random, id -> true);
            seen[Environment.EVENTS.indexOf(any)] = true;
            helper.assertTrue(Environment.TOXIC_STORM.equals(EnvironmentState.chooseEvent(random, Environment.TOXIC_STORM::equals)), "Only the allowed one");
        }
        for (int i = 0; i < seen.length; i++) {
            helper.assertTrue(seen[i], "Each comes sometimes: " + Environment.EVENTS.get(i));
        }
        helper.assertTrue(EnvironmentState.chooseEvent(random, id -> false) == null, "None allowed, none comes");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aWarningSurvivesARestart(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        EnvironmentState state = new EnvironmentState();
        state.startWarning(Environment.RADIATION_BURST, 1.2f, 150, 90);
        EnvironmentState loaded = EnvironmentState.load(state.save(new CompoundTag(), registries), registries);
        helper.assertTrue(loaded.phase() == EventPhase.WARNING && Environment.RADIATION_BURST.equals(loaded.event()) && loaded.secondsLeft() == 90
                && loaded.pendingActiveSeconds() == 150 && loaded.scheduled(), "The warning comes back whole");
        CompoundTag old = state.save(new CompoundTag(), registries);
        old.remove("Scheduled");
        old.putString("Phase", "calm");
        helper.assertFalse(EnvironmentState.load(old, registries).scheduled(), "A world from before 1.3.0 waits a whole pause");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void acidRainBringsRainAndItsEndClearsIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EnvironmentState state = new EnvironmentState();
        state.startActive(Environment.ACID_RAIN, 1.0f, 60);
        Catastrophes.onSecond(level, state, EnvironmentState.Change.ACTIVE, Environment.ACID_RAIN);
        helper.assertTrue(level.getLevelData().isRaining(), "It rains");
        state.clear();
        Catastrophes.onSecond(level, state, EnvironmentState.Change.ENDED, Environment.ACID_RAIN);
        helper.assertFalse(level.getLevelData().isRaining(), "The rain stops with it");
        helper.succeed();
    }
}
