package com.projectrebirth.gametest;

import java.util.UUID;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.BodyAttachments;
import com.projectrebirth.bodies.BodyBlocks;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyEntities;
import com.projectrebirth.bodies.HumanShellEntity;
import com.projectrebirth.bodies.PossessionState;
import com.projectrebirth.bodies.ShellRegistry;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.Biology;
import com.projectrebirth.environment.Environment;
import com.projectrebirth.environment.EnvironmentRegistry;
import com.projectrebirth.environment.Exposure;
import com.projectrebirth.environment.Hazard;
import com.projectrebirth.environment.HumanBody;
import com.projectrebirth.powerarmor.ArmorVariant;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.powerarmor.PowerArmorRegistry;
import com.projectrebirth.technology.Station;
import com.projectrebirth.technology.TechRecipes;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The dose, the toxic load and the symptoms of a human body. Run with {@code .\gradlew runGameTestServer}. */
@GameTestHolder(PlanTests.ATMOSPHERE)
@PrefixGameTestTemplate(false)
public class HumanBodyGameTests {
    private static final String TEMPLATE = "floor_5x5";
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    private static final float EPSILON = 0.0001f;

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static Exposure exposure(float pollution, float radiation, float acid) {
        return Exposure.builder().add(Hazard.POLLUTION, pollution).add(Hazard.RADIATION, radiation).add(Hazard.ACID, acid).build();
    }

    @GameTest(template = TEMPLATE)
    public static void radiationAndPollutionRaiseTheirOwnNumbers(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        HumanBody.onSecond(player, exposure(0.0f, 100.0f, 0.0f));
        Biology afterRadiation = HumanBody.biology(player);
        helper.assertTrue(afterRadiation.dose() > 0.0f && afterRadiation.toxin() == 0.0f, "Radiation only adds dose: " + afterRadiation);
        HumanBody.setBiology(player, Biology.CLEAN);
        HumanBody.onSecond(player, exposure(100.0f, 0.0f, 0.0f));
        Biology afterPollution = HumanBody.biology(player);
        helper.assertTrue(afterPollution.toxin() > 0.0f && afterPollution.dose() == 0.0f, "Pollution only adds toxins: " + afterPollution);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void toxinsLeaveByThemselvesAndTheDoseBarely(GameTestHelper helper) {
        float clearance = RebirthConfig.HUMAN_TOXIN_CLEARANCE.get().floatValue();
        float doseClearance = RebirthConfig.HUMAN_DOSE_CLEARANCE.get().floatValue();
        Biology after = HumanBody.afterSecond(new Biology(50.0f, 50.0f), 0.0f, 0.0f, 0.0f);
        helper.assertTrue(Math.abs(after.toxin() - (50.0f - clearance)) < EPSILON, "Toxins clear: " + after);
        helper.assertTrue(Math.abs(after.dose() - (50.0f - doseClearance)) < EPSILON, "The dose hardly clears: " + after);
        helper.assertTrue(doseClearance < clearance, "The dose stays much longer than the toxins");
        helper.assertTrue(HumanBody.afterSecond(Biology.CLEAN, 0.0f, 0.0f, 0.0f).equals(Biology.CLEAN), "Never below zero");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aPersonIsInDangerOnlyAfterHoursOfTheBackground(GameTestHelper helper) {
        // Background of the ordinary world: 1 and 1.
        Biology biology = Biology.CLEAN;
        for (int second = 0; second < 3600; second++) {
            biology = HumanBody.afterSecond(biology, 1.0f, 1.0f, 0.0f);
        }
        helper.assertTrue(HumanBody.doseStage(biology.dose()) == 0, "An hour in the open is no dose yet: " + biology);
        helper.assertTrue(HumanBody.toxinStage(biology.toxin()) <= 1, "And at most nausea: " + biology);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void stagesFollowTheThresholds(GameTestHelper helper) {
        helper.assertTrue(HumanBody.doseStage(0.0f) == 0, "Clean");
        helper.assertTrue(HumanBody.doseStage(RebirthConfig.HUMAN_DOSE_WEAKNESS.get().floatValue()) == 1, "Weakness");
        helper.assertTrue(HumanBody.doseStage(RebirthConfig.HUMAN_DOSE_SLOW_HEALING.get().floatValue()) == 2, "Slow healing");
        helper.assertTrue(HumanBody.doseStage(RebirthConfig.HUMAN_DOSE_MAX_HEALTH.get().floatValue()) == 3, "Lower maximum health");
        helper.assertTrue(HumanBody.doseStage(RebirthConfig.HUMAN_DOSE_DAMAGE.get().floatValue()) == 4, "Damage");
        helper.assertTrue(HumanBody.toxinStage(RebirthConfig.HUMAN_TOXIN_NAUSEA.get().floatValue()) == 1, "Nausea");
        helper.assertTrue(HumanBody.toxinStage(RebirthConfig.HUMAN_TOXIN_WEAKNESS.get().floatValue()) == 2, "Weakness");
        helper.assertTrue(HumanBody.toxinStage(RebirthConfig.HUMAN_TOXIN_DAMAGE.get().floatValue()) == 3, "Damage");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void symptomsComeWithTheStagesGoAwayAndMilkDoesNotHelp(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        double speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double health = player.getAttributeValue(Attributes.MAX_HEALTH);
        HumanBody.setBiology(player, new Biology(RebirthConfig.HUMAN_DOSE_MAX_HEALTH.get().floatValue() + 1.0f, 0.0f));
        HumanBody.onSecond(player, Exposure.NONE);
        helper.assertTrue(player.hasEffect(EnvironmentRegistry.RADIATION_SICKNESS), "The symptom shows");
        helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) < speed, "Slower");
        helper.assertTrue(player.getAttributeValue(Attributes.MAX_HEALTH) < health, "Lower maximum health");
        player.removeEffectsCuredBy(EffectCures.MILK);
        helper.assertTrue(player.hasEffect(EnvironmentRegistry.RADIATION_SICKNESS), "Milk does not cure it");
        HumanBody.setBiology(player, Biology.CLEAN);
        HumanBody.onSecond(player, Exposure.NONE);
        helper.assertFalse(player.hasEffect(EnvironmentRegistry.RADIATION_SICKNESS), "A clean body has no symptom");
        helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) == speed, "The speed comes back");
        helper.assertTrue(player.getAttributeValue(Attributes.MAX_HEALTH) == health, "The health comes back");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void slowHealingHalvesWhatIsHealed(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.setHealth(10.0f);
        player.heal(2.0f);
        float normal = player.getHealth() - 10.0f;
        HumanBody.setBiology(player, new Biology(RebirthConfig.HUMAN_DOSE_SLOW_HEALING.get().floatValue(), 0.0f));
        player.setHealth(10.0f);
        player.heal(2.0f);
        float slow = player.getHealth() - 10.0f;
        helper.assertTrue(Math.abs(slow - normal * RebirthConfig.HUMAN_DOSE_HEALING_MULTIPLIER.get().floatValue()) < EPSILON, "Healed " + slow + " instead of " + normal);
        HumanBody.setBiology(player, new Biology(0.0f, RebirthConfig.HUMAN_TOXIN_DAMAGE.get().floatValue()));
        player.setHealth(10.0f);
        player.heal(2.0f);
        float last = player.getHealth() - 10.0f;
        helper.assertTrue(Math.abs(last - normal * RebirthConfig.HUMAN_LAST_STAGE_HEALING_MULTIPLIER.get().floatValue()) < EPSILON, "At the last stage it heals " + last);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void theLastStageHurtsTheBody(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        HumanBody.setBiology(player, new Biology(0.0f, RebirthConfig.HUMAN_MAX_TOXIN.get().floatValue()));
        float health = player.getHealth();
        helper.succeedWhen(() -> {
            player.invulnerableTime = 0;
            HumanBody.onSecond(player, Exposure.NONE);
            helper.assertTrue(player.getHealth() < health, "The toxic load must hurt within its interval");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void acidHurtsAnUnprotectedPerson(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        float health = player.getHealth();
        helper.succeedWhen(() -> {
            player.invulnerableTime = 0;
            HumanBody.onSecond(player, exposure(0.0f, 0.0f, 1.0f));
            helper.assertTrue(player.getHealth() < health, "Acid must hurt within its interval");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void aCreativePlayerAndAnArtificialBodyGainNothing(GameTestHelper helper) {
        ServerPlayer creative = player(helper);
        creative.setGameMode(GameType.CREATIVE);
        HumanBody.onSecond(creative, exposure(100.0f, 100.0f, 100.0f));
        helper.assertTrue(HumanBody.biology(creative).equals(Biology.CLEAN), "Creative: " + HumanBody.biology(creative));

        ServerPlayer body = player(helper);
        body.setData(BodyAttachments.POSSESSION, new PossessionState(java.util.Optional.of(BodyClass.BASIC_ROBOT)));
        float dose = 100.0f;
        HumanBody.setBiology(body, new Biology(dose, 0.0f));
        HumanBody.onSecond(body, exposure(100.0f, 100.0f, 100.0f));
        helper.assertTrue(HumanBody.biology(body).dose() <= dose, "An artificial body gains no dose");
        helper.assertTrue(HumanBody.biology(body).toxin() == 0.0f, "And no toxins");
        helper.assertFalse(body.hasEffect(EnvironmentRegistry.RADIATION_SICKNESS), "And has no symptoms");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aPilotOnlyGetsWeakenedRadiation(GameTestHelper helper) {
        ServerPlayer pilot = player(helper);
        PowerArmorEntity armor = helper.spawn(PowerArmorRegistry.POWER_ARMOR.get(), POS);
        armor.setVariant(ArmorVariant.COMBAT);
        pilot.startRiding(armor, true);
        HumanBody.onSecond(pilot, exposure(100.0f, 100.0f, 100.0f));
        Biology biology = HumanBody.biology(pilot);
        helper.assertTrue(biology.toxin() == 0.0f, "The cockpit is sealed: " + biology);
        float full = HumanBody.afterSecond(Biology.CLEAN, 100.0f, 0.0f, 0.0f).dose();
        helper.assertTrue(biology.dose() > 0.0f && biology.dose() < full, "Radiation is weakened: " + biology + ", full " + full);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theBodyInACapsuleIsSafeUntilTheCapsuleIsGone(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        BlockPos pos = helper.absolutePos(POS);
        helper.setBlock(POS, BodyBlocks.TRANSFER_CAPSULE.get().defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        HumanShellEntity shell = helper.spawn(BodyEntities.HUMAN_SHELL.get(), POS);
        shell.setup(owner.getUUID(), UUID.randomUUID(), Component.literal("Owner"));
        helper.assertTrue(HumanBody.inIntactCapsule(helper.getLevel(), shell), "The capsule is there at " + pos);
        HumanBody.tickShell(shell, helper.getLevel(), owner);
        helper.assertTrue(HumanBody.biology(owner).equals(Biology.CLEAN), "In a capsule nothing reaches the body");
        helper.setBlock(POS, net.minecraft.world.level.block.Blocks.AIR);
        HumanBody.setBiology(owner, new Biology(10.0f, 10.0f));
        for (int i = 0; i < 100; i++) {
            HumanBody.tickShell(shell, helper.getLevel(), owner);
        }
        Biology after = HumanBody.biology(owner);
        helper.assertTrue(after.dose() > 10.0f || after.toxin() > 10.0f, "Without the capsule the air reaches it: " + after);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void medicinesLowerTheirOwnNumber(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        HumanBody.setBiology(player, new Biology(100.0f, 50.0f));
        ItemStack antiradin = new ItemStack(EnvironmentRegistry.ANTIRADIN.get(), 2);
        EnvironmentRegistry.ANTIRADIN.get().take(antiradin, player);
        helper.assertTrue(HumanBody.biology(player).dose() == 100.0f - RebirthConfig.HUMAN_ANTIRADIN_DOSE.get().floatValue(), "Antiradin lowers the dose");
        helper.assertTrue(HumanBody.biology(player).toxin() == 50.0f, "But not the toxins");
        helper.assertTrue(antiradin.getCount() == 1, "One is used");
        ItemStack sorbent = new ItemStack(EnvironmentRegistry.SORBENT.get());
        EnvironmentRegistry.SORBENT.get().take(sorbent, player);
        helper.assertTrue(HumanBody.biology(player).toxin() == Math.max(0.0f, 50.0f - RebirthConfig.HUMAN_SORBENT_TOXIN.get().floatValue()), "Sorbent lowers the toxins");
        helper.assertTrue(sorbent.isEmpty(), "It is used up");
        var recipes = TechRecipes.recipesFor(helper.getLevel(), Station.ELECTRONICS_FABRICATOR);
        helper.assertTrue(recipes.stream().anyMatch(recipe -> recipe.value().result().is(EnvironmentRegistry.ANTIRADIN.get())), "Antiradin has a recipe");
        var assembly = TechRecipes.recipesFor(helper.getLevel(), Station.ASSEMBLY_STATION);
        helper.assertTrue(assembly.stream().anyMatch(recipe -> recipe.value().result().is(EnvironmentRegistry.SORBENT.get())), "Sorbent has a recipe");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theNumbersStayInTheSavedData(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        HumanBody.setBiology(player, new Biology(123.0f, 45.0f));
        var ops = helper.getLevel().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        var saved = Biology.CODEC.encodeStart(ops, HumanBody.biology(player)).getOrThrow();
        helper.assertTrue(Biology.CODEC.parse(ops, saved).getOrThrow().equals(new Biology(123.0f, 45.0f)), "They come back");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "human_body_off")
    public static void withTheSystemOffNothingChanges(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        HumanBody.setBiology(player, new Biology(RebirthConfig.HUMAN_DOSE_DAMAGE.get().floatValue(), 0.0f));
        HumanBody.onSecond(player, Exposure.NONE);
        helper.assertTrue(player.hasEffect(EnvironmentRegistry.RADIATION_SICKNESS), "On: the symptom shows");
        RebirthConfig.ENVIRONMENT_ENABLED.set(false);
        Biology before = HumanBody.biology(player);
        HumanBody.onSecond(player, exposure(100.0f, 100.0f, 100.0f));
        boolean sick = player.hasEffect(EnvironmentRegistry.RADIATION_SICKNESS);
        boolean same = HumanBody.biology(player).equals(before);
        RebirthConfig.ENVIRONMENT_ENABLED.set(true);
        helper.assertFalse(sick, "Off: no symptoms");
        helper.assertTrue(same, "Off: the numbers wait");
        helper.succeed();
    }
}
