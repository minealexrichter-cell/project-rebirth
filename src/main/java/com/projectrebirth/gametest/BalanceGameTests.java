package com.projectrebirth.gametest;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.Biology;
import com.projectrebirth.environment.Catastrophes;
import com.projectrebirth.environment.Hazard;
import com.projectrebirth.environment.HumanBody;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The goals of the balance of 1.3.0 (block H): hours without protection on the background, a minute or two in a
 * catastrophe, and protection that holds through a whole one. Counted with the same numbers the game uses.
 */
@GameTestHolder(PlanTests.ATMOSPHERE)
@PrefixGameTestTemplate(false)
public class BalanceGameTests {
    private static final String TEMPLATE = "floor_5x5";
    private static final int LIMIT = 100_000;

    /** Seconds until the toxic load reaches a level, or LIMIT. */
    private static int secondsToToxin(float pollution, float acid, float level) {
        Biology biology = Biology.CLEAN;
        for (int s = 1; s < LIMIT; s++) {
            biology = HumanBody.afterSecond(biology, 0.0f, pollution, acid);
            if (biology.toxin() >= level) {
                return s;
            }
        }
        return LIMIT;
    }

    private static int secondsToDose(float radiation, float level) {
        Biology biology = Biology.CLEAN;
        for (int s = 1; s < LIMIT; s++) {
            biology = HumanBody.afterSecond(biology, radiation, 0.0f, 0.0f);
            if (biology.dose() >= level) {
                return s;
            }
        }
        return LIMIT;
    }

    private static float event(String id, Hazard hazard) {
        return RebirthConfig.environmentEvent(id).hazard(hazard);
    }

    @GameTest(template = TEMPLATE)
    public static void hoursOnTheBackgroundMinutesInACatastrophe(GameTestHelper helper) {
        float nausea = RebirthConfig.HUMAN_TOXIN_NAUSEA.get().floatValue();
        float weakness = RebirthConfig.HUMAN_DOSE_WEAKNESS.get().floatValue();
        helper.assertTrue(secondsToToxin(1.0f, 0.0f, nausea) >= 3600, "The overworld background: an hour at least before nausea");
        helper.assertTrue(secondsToDose(1.0f, weakness) >= 3600, "And before the first stage of the dose");
        int storm = secondsToToxin(1.0f + event("toxic_storm", Hazard.POLLUTION), 0.0f, nausea);
        helper.assertTrue(storm <= 180, "A toxic storm of intensity 1 without protection: nausea within a few minutes, got " + storm + " s");
        int burst = secondsToDose(1.0f + event("radiation_burst", Hazard.RADIATION), weakness);
        helper.assertTrue(burst <= 180, "A radiation burst: the first stage within a few minutes, got " + burst + " s");
        int rain = secondsToToxin(1.0f + event("acid_rain", Hazard.POLLUTION), event("acid_rain", Hazard.ACID), nausea);
        helper.assertTrue(rain <= 180, "Acid rain in the open: nausea within a few minutes, got " + rain + " s");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aRespiratorHoldsThroughTheLongestStrongestStorm(GameTestHelper helper) {
        RebirthConfig.EnvironmentEventStats storm = RebirthConfig.environmentEvent("toxic_storm");
        float pollution = 1.0f + storm.hazard(Hazard.POLLUTION) * storm.maxIntensity().get().floatValue();
        float kept = RebirthConfig.protectionKit("respirator").pollution().get().floatValue();
        int nausea = secondsToToxin(pollution * (1.0f - kept), 0.0f, RebirthConfig.HUMAN_TOXIN_NAUSEA.get().floatValue());
        int longest = storm.maxActiveSeconds().get() + RebirthConfig.ENVIRONMENT_AFTERMATH_SECONDS.get();
        helper.assertTrue(nausea > longest, "No nausea in a respirator through a whole storm: " + nausea + " s vs " + longest + " s");
        float perFilter = RebirthConfig.PROTECTION_FILTER_LIFE.get().floatValue() / (pollution * RebirthConfig.PROTECTION_FILTER_USE.get().floatValue());
        helper.assertTrue(perFilter >= 120.0f, "A filter lasts two minutes of the strongest storm at least: " + perFilter + " s");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theFirstCatastropheLeavesTimeForARespirator(GameTestHelper helper) {
        helper.assertTrue(RebirthConfig.ENVIRONMENT_MIN_CALM_SECONDS.get() >= 1200, "At least one game day before the first catastrophe");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theNoticeComesOncePerPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // Joining may already have told it; start from a player who has not seen it.
        player.setData(com.projectrebirth.environment.EnvironmentRegistry.NOTICE_SEEN, false);
        helper.assertTrue(Catastrophes.tellOnce(player), "The first time");
        helper.assertFalse(Catastrophes.tellOnce(player), "Not again");
        helper.succeed();
    }
}
