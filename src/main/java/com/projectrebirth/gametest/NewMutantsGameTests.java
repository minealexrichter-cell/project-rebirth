package com.projectrebirth.gametest;

import java.util.List;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.garrison.tactics.Tactics;
import com.projectrebirth.hostile.CrawlerEntity;
import com.projectrebirth.hostile.DrownerEntity;
import com.projectrebirth.hostile.HostileRegistry;
import com.projectrebirth.hostile.HunterEntity;
import com.projectrebirth.hostile.MutantEntity;
import com.projectrebirth.hostile.MutantSpawnRule;
import com.projectrebirth.hostile.MutantSpawner;
import com.projectrebirth.hostile.RatEntity;
import com.projectrebirth.hostile.ScreamerEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.9.0, block D: the five new mutants. Each comes by its rules of data, each can be switched off, and each does its own
 * thing: the crawler bursts from the ground, the screamer calls the others, a rat swarm counts as about one mutant, the
 * hunter backs from strong light, the drowner breaks a boat from below.
 */
@GameTestHolder(PlanTests.WASTELAND)
@PrefixGameTestTemplate(false)
public class NewMutantsGameTests {
    private static final String TEMPLATE = "floor_9x9";

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    @GameTest(template = TEMPLATE)
    public static void eachNewMutantHasItsRules(GameTestHelper helper) {
        List<MutantSpawnRule> rules = MutantSpawner.rules(helper.getLevel());
        helper.assertTrue(rules.stream().anyMatch(r -> r.entity() == HostileRegistry.CRAWLER.get() && MutantSpawnRule.UNDERGROUND.equals(r.placement()) && r.maxY() <= 0),
                "The crawler in deep caves");
        helper.assertTrue(rules.stream().anyMatch(r -> r.entity() == HostileRegistry.SCREAMER.get() && "night".equals(r.time())), "The screamer at night");
        helper.assertTrue(rules.stream().anyMatch(r -> r.entity() == HostileRegistry.RAT.get() && r.structures().isPresent() && r.minStage() >= 3 && r.groupMin() >= 6),
                "Rat swarms in ruins and villages from stage III");
        helper.assertTrue(rules.stream().anyMatch(r -> r.entity() == HostileRegistry.HUNTER.get() && r.minStage() >= 2), "The hunter from stage II");
        helper.assertTrue(rules.stream().anyMatch(r -> r.entity() == HostileRegistry.DROWNER.get() && MutantSpawnRule.WATER.equals(r.placement())), "The drowner in water");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aSwitchedOffKindDoesNotCome(GameTestHelper helper) {
        var stats = RebirthConfig.MUTANT_SPECIES.get("screamer");
        boolean before = stats.enabled().get();
        BlockPos centre = helper.absolutePos(new BlockPos(4, 2, 4));
        var map = com.projectrebirth.environment.ContaminationMap.get(helper.getLevel());
        map.addSpot(centre, 0, 5.0f, 0.0f, helper.getLevel().getGameTime());
        try {
            stats.enabled().set(false);
            helper.assertFalse(MutantSpawner.fitting(helper.getLevel(), centre).stream().anyMatch(r -> r.entity() == HostileRegistry.SCREAMER.get()),
                    "No screamer rule while the screamer is off");
        } finally {
            stats.enabled().set(before);
            map.clearAround(centre, 1);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theCrawlerBurstsOutWithAStrongerBlowAndIsWeakInTheOpen(GameTestHelper helper) {
        // a villager: a fresh player is invulnerable for its first three seconds
        var player = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(4, 2, 4));
        CrawlerEntity crawler = helper.spawnWithNoFreeWill(HostileRegistry.CRAWLER.get(), new BlockPos(4, 2, 5));
        crawler.setBurrowed(true);
        helper.assertTrue(crawler.isBurrowed() && !crawler.doHurtTarget(player), "Under the ground it does not bite");
        float before = player.getHealth();
        crawler.emergeAt(player);
        helper.assertFalse(crawler.isBurrowed(), "It came out");
        float expected = (float) crawler.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                * RebirthConfig.CRAWLER_AMBUSH.get().floatValue();
        helper.assertTrue(before - player.getHealth() >= expected * 0.9f, "Its blow from below is stronger: " + (before - player.getHealth()));
        if (helper.getLevel().canSeeSky(crawler.blockPosition())) {
            float health = crawler.getHealth();
            crawler.hurt(helper.getLevel().damageSources().generic(), 2.0f);
            helper.assertTrue(health - crawler.getHealth() >= 2.0f * RebirthConfig.CRAWLER_OPEN_DAMAGE.get().floatValue() - 0.01f,
                    "Under the open sky it takes more damage");
            helper.assertFalse(crawler.canBurrowHere(), "and cannot burrow");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theScreamCallsMutantsAndReelsAHuman(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 1.5)));
        ScreamerEntity screamer = helper.spawnWithNoFreeWill(HostileRegistry.SCREAMER.get(), new BlockPos(7, 2, 7));
        MutantEntity hound = helper.spawnWithNoFreeWill(HostileRegistry.MUTATED_HOUND.get(), new BlockPos(7, 2, 1));
        helper.assertTrue(hound.getTarget() == null, "The hound has no prey");
        int called = screamer.scream(player);
        helper.assertTrue(called >= 1 && hound.getTarget() == player, "The scream sends the hound at the player");
        helper.assertTrue(player.hasEffect(MobEffects.CONFUSION), "The human's head reels (players near: "
                + helper.getLevel().getEntitiesOfClass(ServerPlayer.class, screamer.getBoundingBox().inflate(24.0)).size() + ", creative " + player.isCreative()
                + ", at " + player.blockPosition() + " / " + screamer.blockPosition() + ")");
        helper.assertTrue(ScreamerEntity.interference(player) == 0.0f, "A human gets no interference: there are no electronics in a human");
        helper.assertTrue(HostileRegistry.SCREAMER.get().is(Tactics.FIRST), "Machines take the screamer first");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aRatSwarmCountsAsAboutOneMutant(GameTestHelper helper) {
        for (int i = 0; i < 10; i++) {
            helper.spawnWithNoFreeWill(HostileRegistry.RAT.get(), new BlockPos(1 + i % 7, 2, 1 + i / 7));
        }
        AABB box = new AABB(helper.absolutePos(new BlockPos(4, 2, 4))).inflate(8.0);
        float population = 0.0f;
        for (RatEntity rat : helper.getLevel().getEntitiesOfClass(RatEntity.class, box)) {
            population += rat.populationWeight();
        }
        helper.assertTrue(Math.abs(population - 2.0f) < 0.01f, "Ten rats count as two mutants: " + population);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theHunterBacksFromStrongLightAndShowsWhenHit(GameTestHelper helper) {
        HunterEntity hunter = helper.spawnWithNoFreeWill(HostileRegistry.HUNTER.get(), new BlockPos(4, 2, 4));
        helper.setBlock(new BlockPos(5, 2, 4), Blocks.SEA_LANTERN.defaultBlockState());
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(hunter.inStrongLight(), "Next to a lamp the light drives it off");
            hunter.hurt(helper.getLevel().damageSources().generic(), 1.0f);
            helper.assertFalse(hunter.isCloaked() || hunter.wouldCloak(), "A blow shows it");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void theDrownerBreaksABoatFromBelowAndDoesNotDrown(GameTestHelper helper) {
        DrownerEntity drowner = helper.spawnWithNoFreeWill(HostileRegistry.DROWNER.get(), new BlockPos(4, 2, 4));
        helper.assertFalse(drowner.canDrownInFluidType(net.neoforged.neoforge.common.NeoForgeMod.WATER_TYPE.value()), "It breathes water");
        Boat boat = helper.spawn(EntityType.BOAT, new BlockPos(2, 2, 2));
        drowner.strikeBoat(boat);
        drowner.strikeBoat(boat);
        helper.assertTrue(boat.isRemoved(), "Two blows in a row break a boat");
        helper.succeed();
    }
}
