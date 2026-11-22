package com.projectrebirth.gametest;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.config.WastelandConfig;
import com.projectrebirth.core.RebirthGameRules;
import com.projectrebirth.environment.Biology;
import com.projectrebirth.environment.ContaminatedFood;
import com.projectrebirth.environment.Environment;
import com.projectrebirth.environment.EnvironmentRegistry;
import com.projectrebirth.environment.Exposure;
import com.projectrebirth.environment.HumanBody;
import com.projectrebirth.environment.Wasteland;
import com.projectrebirth.environment.WastelandAir;
import com.projectrebirth.environment.WoundInfection;
import com.projectrebirth.water.WaterPurifierBlockEntity;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.9.0, block B: the hostile world for a human. The air of the Wasteland, the stages and their game rules, the accumulated
 * dose, contaminated food, wound infection, sleep. Each test does its work in one go, so that what it switches for itself
 * (a game rule, the stage) is back before any other test runs.
 */
@GameTestHolder(PlanTests.WASTELAND)
@PrefixGameTestTemplate(false)
public class WastelandGameTests {
    private static final String TEMPLATE = "floor_5x5";

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    /** Seconds until the toxic load of an unprotected (or protected) person outside reaches a level, in an ordinary biome. */
    private static int secondsTo(float level, float protection, int stage) {
        float air = WastelandAir.toxin(1.0f, stage, new Environment.Shelter(false, false), false) * (1.0f - protection);
        float clearance = WastelandAir.clearance(air);
        // the ordinary background of [environment]: pollution 1 in an ordinary biome; a respirator keeps 90% of it off with a filter
        float pollution = protection > 0.0f ? 0.1f : 1.0f;
        Biology biology = Biology.CLEAN;
        for (int second = 1; second < 100_000; second++) {
            biology = HumanBody.afterSecond(biology, 1.0f, pollution, 0.0f, 0.0f, air, 0.0f, clearance);
            if (biology.toxin() >= level) {
                return second;
            }
        }
        return Integer.MAX_VALUE;
    }

    @GameTest(template = TEMPLATE)
    public static void theAirIsStrongestInTheOpenAndWeakestUnderground(GameTestHelper helper) {
        float open = WastelandAir.toxin(1.0f, 1, new Environment.Shelter(false, false), false);
        float night = WastelandAir.toxin(1.0f, 1, new Environment.Shelter(false, false), true);
        float roof = WastelandAir.toxin(1.0f, 1, new Environment.Shelter(true, false), true);
        float underground = WastelandAir.toxin(1.0f, 1, new Environment.Shelter(true, true), false);
        float stage4 = WastelandAir.toxin(1.0f, 4, new Environment.Shelter(false, false), false);
        float swamp = WastelandAir.toxin(1.6f, 1, new Environment.Shelter(false, false), false);
        helper.assertTrue(open > 0.0f, "The air of the Wasteland is in the open: " + open);
        helper.assertTrue(night > open, "Stronger at night in the open: " + night);
        helper.assertTrue(roof < open, "Weaker under a roof, also at night: " + roof);
        helper.assertTrue(underground < roof, "Weakest underground: " + underground);
        helper.assertTrue(stage4 > open && swamp > open, "Stronger at a later stage and in a swamp");
        helper.assertTrue(WastelandAir.clearance(open) < 1.0f && WastelandAir.clearance(0.0f) == 1.0f,
                "The body clears nothing by itself while it breathes it, everything without it");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void anUnprotectedPersonLivesAboutFortyMinutesOutside(GameTestHelper helper) {
        int nausea = secondsTo(RebirthConfig.HUMAN_TOXIN_NAUSEA.get().floatValue(), 0.0f, 1);
        int weakness = secondsTo(RebirthConfig.HUMAN_TOXIN_WEAKNESS.get().floatValue(), 0.0f, 1);
        int damage = secondsTo(RebirthConfig.HUMAN_TOXIN_DAMAGE.get().floatValue(), 0.0f, 1);
        helper.assertTrue(nausea >= 8 * 60 && nausea <= 15 * 60, "Nausea in about 10 minutes: " + nausea + " s");
        helper.assertTrue(weakness >= 20 * 60 && weakness <= 30 * 60, "Weakness in about 25 minutes: " + weakness + " s");
        helper.assertTrue(damage >= 30 * 60 && damage <= 45 * 60, "Damage before about 45 minutes: " + damage + " s");
        int respirator = secondsTo(RebirthConfig.HUMAN_TOXIN_DAMAGE.get().floatValue(), WastelandConfig.kit("respirator").breath().get().floatValue(), 1);
        float ratio = (float) respirator / damage;
        helper.assertTrue(ratio >= 2.5f && ratio <= 3.5f, "A respirator lasts about three times as long: ×" + ratio);
        int stage4 = secondsTo(RebirthConfig.HUMAN_TOXIN_DAMAGE.get().floatValue(), 0.0f, 4);
        helper.assertTrue(stage4 < damage, "Stage IV is faster: " + stage4 + " s");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void gearKeepsOffPartOfTheAirWhileTheFilterLasts(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        helper.assertTrue(WastelandAir.protection(player) == 0.0f, "Nothing without gear");
        ItemStack respirator = new ItemStack(EnvironmentRegistry.RESPIRATOR.get());
        respirator.set(EnvironmentRegistry.FILTER_LIFE.get(), 10.0f);
        player.setItemSlot(EquipmentSlot.HEAD, respirator);
        float withFilter = WastelandAir.protection(player);
        helper.assertTrue(withFilter > 0.0f, "A respirator with a filter keeps some off: " + withFilter);
        WastelandAir.useFilter(player, 1.0f);
        helper.assertTrue(com.projectrebirth.environment.Protection.filterLeft(player.getItemBySlot(EquipmentSlot.HEAD)) < 10.0f,
                "The air of the Wasteland uses the filter");
        player.getItemBySlot(EquipmentSlot.HEAD).set(EnvironmentRegistry.FILTER_LIFE.get(), 0.0f);
        helper.assertTrue(WastelandAir.protection(player) == 0.0f, "A spent filter keeps nothing off");
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(EnvironmentRegistry.HAZMAT_SUIT.get()));
        helper.assertTrue(WastelandAir.protection(player) > 0.0f, "A suit keeps some off through the skin");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void stagesGrowWithTheDaysUpToTheGameRules(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(Wasteland.stageOfDay(0) == 1 && Wasteland.stageOfDay(Wasteland.startDay(2)) == 2
                && Wasteland.stageOfDay(Wasteland.startDay(3)) == 3 && Wasteland.stageOfDay(Wasteland.startDay(4) + 100) == 4, "Stages by the days");
        helper.assertTrue(Wasteland.startDay(2) == 10 && Wasteland.startDay(3) == 30 && Wasteland.startDay(4) == 60, "Days 10, 30, 60 by default");
        int before = Wasteland.stage(server);
        try {
            Wasteland.setStage(server, 4);
            helper.assertTrue(Wasteland.stage(server) == 4, "The command puts the world at stage IV");
            server.getGameRules().getRule(RebirthGameRules.WASTELAND_MAX_STAGE).set(2, server);
            helper.assertTrue(Wasteland.stage(server) == 2, "The game rule caps it at II");
            server.getGameRules().getRule(RebirthGameRules.WASTELAND_ESCALATION).set(false, server);
            helper.assertTrue(Wasteland.stage(server) == 1, "Without escalation it is I");
            helper.assertTrue(Wasteland.daysToNextStage(server) < 0, "And no stage comes");
        } finally {
            server.getGameRules().getRule(RebirthGameRules.WASTELAND_ESCALATION).set(true, server);
            server.getGameRules().getRule(RebirthGameRules.WASTELAND_MAX_STAGE).set(4, server);
            Wasteland.setStage(server, before);
        }
        helper.assertTrue(Wasteland.stage(server) == before, "Back as it was");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void gameRulesAreRegisteredAndChangedByCommand(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        for (var key : RebirthGameRules.ALL) {
            helper.assertTrue(server.getGameRules().getRule(key) != null, "The rule " + key.getId());
        }
        var source = server.createCommandSourceStack().withSuppressedOutput();
        try {
            server.getCommands().performPrefixedCommand(source, "gamerule rebirthWastelandEscalation false");
            helper.assertFalse(RebirthGameRules.on(server, RebirthGameRules.WASTELAND_ESCALATION), "/gamerule switches it off");
            server.getCommands().performPrefixedCommand(source, "gamerule rebirthWastelandMaxStage 9");
            helper.assertTrue(RebirthGameRules.wastelandMaxStage(server) == 4, "A number above 4 counts as 4");
        } finally {
            server.getGameRules().getRule(RebirthGameRules.WASTELAND_ESCALATION).set(true, server);
            server.getGameRules().getRule(RebirthGameRules.WASTELAND_MAX_STAGE).set(4, server);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void anOldWorldStartsNoHigherThanStageTwo(GameTestHelper helper) {
        for (long day : new long[]{0, 5, 12, 45, 100, 5000}) {
            long offset = Wasteland.oldWorldOffset(day);
            int stage = Wasteland.stageOfDay(day - offset);
            helper.assertTrue(stage <= WastelandConfig.OLD_WORLD_STAGE.get(), "A world of day " + day + " starts at stage " + stage);
        }
        helper.assertTrue(Wasteland.stageOfDay(100 - Wasteland.oldWorldOffset(100) + 20) == 3, "And goes on growing from there");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void presetsChangeTheNumbers(GameTestHelper helper) {
        WastelandConfig.Preset before = WastelandConfig.PRESET.get();
        try {
            WastelandConfig.PRESET.set(WastelandConfig.Preset.HELL);
            float hell = WastelandAir.toxin(1.0f, 1, new Environment.Shelter(false, false), false);
            long hellDay = Wasteland.startDay(2);
            WastelandConfig.PRESET.set(WastelandConfig.Preset.GENTLE);
            float gentle = WastelandAir.toxin(1.0f, 1, new Environment.Shelter(false, false), false);
            long gentleDay = Wasteland.startDay(2);
            helper.assertTrue(hell > gentle, "Hell has a stronger air: " + hell + " / " + gentle);
            helper.assertTrue(hellDay < gentleDay, "and earlier stages: day " + hellDay + " / " + gentleDay);
        } finally {
            WastelandConfig.PRESET.set(before);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theAccumulatedDoseLowersMaxHealthUntilAntiradin(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        Biology gained = HumanBody.afterSecond(Biology.CLEAN, 100.0f, 0.0f, 0.0f, 0.0f);
        helper.assertTrue(gained.residual() > 0.0f, "A share of the dose accumulates: " + gained);
        Biology cleared = HumanBody.afterSecond(gained.withDose(0.0f), 0.0f, 0.0f, 0.0f, 0.0f);
        helper.assertTrue(cleared.residual() == gained.residual(), "The body does not clear it by itself");
        HumanBody.setBiology(player, new Biology(0.0f, 0.0f, 40.0f));
        HumanBody.onSecond(player, Exposure.NONE);
        double max = player.getAttributeValue(Attributes.MAX_HEALTH);
        helper.assertTrue(Math.abs(max - (20.0 - 40.0 * WastelandConfig.DOSE_HEALTH_PER_RESIDUAL.get())) < 0.6, "Max health is lower: " + max);
        HumanBody.setBiology(player, new Biology(0.0f, 0.0f, 10_000.0f));
        helper.assertTrue(HumanBody.maxHealthPenalty(HumanBody.biology(player)) <= WastelandConfig.DOSE_MAX_HEALTH_PENALTY.get(), "At most 8 hearts");
        HumanBody.setBiology(player, new Biology(0.0f, 0.0f, 40.0f));
        EnvironmentRegistry.ANTIRADIN.get().take(new ItemStack(EnvironmentRegistry.ANTIRADIN.get()), player);
        helper.assertTrue(HumanBody.biology(player).residual() < 40.0f, "Antiradin takes it away: " + HumanBody.biology(player));
        HumanBody.onSecond(player, Exposure.NONE);
        helper.assertTrue(player.getAttributeValue(Attributes.MAX_HEALTH) > max, "and the health comes back");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void contaminatedFoodGivesToxinsAndCookingLeavesAShare(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ItemStack beef = new ItemStack(Items.BEEF);
        ContaminatedFood.mark(beef, 25.0f);
        ItemStack stone = new ItemStack(Items.STONE);
        ContaminatedFood.mark(stone, 25.0f);
        helper.assertTrue(ContaminatedFood.isContaminated(beef) && !ContaminatedFood.isContaminated(stone), "Only food is marked");
        HumanBody.setBiology(player, Biology.CLEAN);
        ContaminatedFood.eaten(player, beef);
        helper.assertTrue(Math.abs(HumanBody.biology(player).toxin() - 25.0f) < 0.01f, "Eating it gives its toxins: " + HumanBody.biology(player));
        var level = helper.getLevel();
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(beef), level).orElseThrow();
        ItemStack cooked = recipe.value().assemble(new SingleRecipeInput(beef), level.registryAccess());
        float share = WastelandConfig.FOOD_COOKED_SHARE.get().floatValue();
        helper.assertTrue(Math.abs(ContaminatedFood.toxin(cooked) - 25.0f * share) < 0.01f, "Cooking leaves a share: " + ContaminatedFood.toxin(cooked));
        ItemStack cleanCooked = recipe.value().assemble(new SingleRecipeInput(new ItemStack(Items.BEEF)), level.registryAccess());
        helper.assertFalse(ContaminatedFood.isContaminated(cleanCooked), "Clean meat cooks clean");
        helper.assertTrue(WaterPurifierBlockEntity.work(beef) == 1 && !ContaminatedFood.isContaminated(WaterPurifierBlockEntity.cleaned(beef))
                && WaterPurifierBlockEntity.cleaned(beef).is(Items.BEEF), "The water purifier washes it");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aMutantBlowInfectsTheWoundUntilTheAntiseptic(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        HumanBody.setBiology(player, Biology.CLEAN);
        WoundInfection.infect(player);
        helper.assertTrue(WoundInfection.infected(player) && WoundInfection.toxinPerSecond(player) > 0.0f, "The wound is infected");
        HumanBody.onSecond(player, Exposure.NONE);
        float infected = HumanBody.biology(player).toxin();
        helper.assertTrue(infected >= WastelandConfig.WOUND_TOXIN.get().floatValue() * 0.99f, "It brings toxins: " + infected);
        EnvironmentRegistry.ANTISEPTIC.get().take(new ItemStack(EnvironmentRegistry.ANTISEPTIC.get()), player);
        helper.assertFalse(WoundInfection.infected(player), "The antiseptic stops it");
        WoundInfection.infect(player);
        EnvironmentRegistry.SORBENT.get().take(new ItemStack(EnvironmentRegistry.SORBENT.get()), player);
        helper.assertFalse(WoundInfection.infected(player), "So does the sorbent");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void sleepOutsideTakesNoToxins(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        HumanBody.setBiology(player, new Biology(0.0f, 50.0f));
        Wasteland.sleptThroughNight(player);
        helper.assertTrue(HumanBody.biology(player).toxin() == 50.0f, "Sleep outside a sealed room takes nothing away");
        helper.succeed();
    }
}
