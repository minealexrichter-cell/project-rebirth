package com.projectrebirth.gametest;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.ContaminationMap;
import com.projectrebirth.environment.RadiationSources;
import com.projectrebirth.hostile.GlowingGhoulEntity;
import com.projectrebirth.hostile.HostileRegistry;
import com.projectrebirth.hostile.MutantEntity;
import com.projectrebirth.hostile.MutantSpawner;
import com.projectrebirth.hostile.MutatedHoundEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The mutants of the contaminated lands (1.6.0, part B3c): the ghoul gives radiation around it; mutants come to contaminated
 * ground only, never more than the cap; a hound goes for a player.
 */
@GameTestHolder(PlanTests.SCOUTING)
@PrefixGameTestTemplate(false)
public class MutantsGameTests {
    private static final String TEMPLATE = "floor_9x9";

    @GameTest(template = TEMPLATE, batch = "scouting_mutants_aura")
    public static void aGhoulIsAWalkingSourceOfRadiation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos probe = helper.absolutePos(new BlockPos(8, 2, 8));
        float before = RadiationSources.readingAt(level, probe).radiation();
        GlowingGhoulEntity ghoul = helper.spawnWithNoFreeWill(HostileRegistry.GLOWING_GHOUL.get(), new BlockPos(0, 2, 0));
        float near = RadiationSources.readingAt(level, helper.absolutePos(new BlockPos(1, 2, 0))).radiation();
        float far = RadiationSources.readingAt(level, probe).radiation();
        helper.assertTrue(near > 1.0f, "Next to the ghoul there is radiation: " + near);
        helper.assertTrue(far == before, "Out of its reach nothing changes: " + before + " -> " + far);
        ghoul.discard();
        helper.assertTrue(RadiationSources.readingAt(level, helper.absolutePos(new BlockPos(1, 2, 0))).radiation() < near, "Gone, it gives nothing");
        helper.succeed();
    }

    /** 1.7.3: with the game rule doMobSpawning off no mutants come either (found in block D: they came all the same). */
    @GameTest(template = TEMPLATE, batch = "scouting_mutants_rule")
    public static void mutantsKeepTheSpawningGameRule(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.world.level.GameRules.BooleanValue rule = level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING);
        boolean before = rule.get();
        net.minecraft.world.Difficulty difficulty = level.getDifficulty();
        try {
            level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
            rule.set(true, level.getServer());
            helper.assertTrue(MutantSpawner.spawningAllowed(level), "Mutants may come while spawning is on");
            rule.set(false, level.getServer());
            helper.assertFalse(MutantSpawner.spawningAllowed(level), "No mutants while doMobSpawning is off");
        } finally {
            rule.set(before, level.getServer());
            level.getServer().setDifficulty(difficulty, true);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "scouting_mutants_ground")
    public static void mutantsComeOnlyToContaminatedGround(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(new BlockPos(4, 2, 4));
        ContaminationMap map = ContaminationMap.get(level);
        map.clearAround(centre, 1);
        helper.assertFalse(MutantSpawner.contaminated(level, centre), "Clean ground has no mutants");
        map.addSpot(centre, 0, 5.0f, 0.0f, level.getGameTime());
        try {
            helper.assertTrue(MutantSpawner.contaminated(level, centre), "A contaminated chunk has them");
        } finally {
            map.clearAround(centre, 1);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "scouting_mutants_spawn")
    public static void aTryBringsMutantsUpToTheCap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(new BlockPos(4, 2, 4));
        AABB around = new AABB(centre).inflate(48.0);
        level.getEntitiesOfClass(MutantEntity.class, around).forEach(Entity::discard);
        // 1.9.0: the rules of hounds, ghouls and brutes (data mutant_spawns) want contaminated ground where the player stands
        ContaminationMap map = ContaminationMap.get(level);
        map.addSpot(centre, 0, 5.0f, 0.0f, level.getGameTime());
        int came = 0;
        for (int i = 0; i < 10 && came == 0; i++) {
            came = MutantSpawner.trySpawn(level, centre, level.random);
        }
        helper.assertTrue(came > 0, "A try brings mutants onto the ground around");
        for (MutantEntity mutant : level.getEntitiesOfClass(MutantEntity.class, around)) {
            double distance = Math.sqrt(mutant.distanceToSqr(centre.getX(), mutant.getY(), centre.getZ()));
            helper.assertTrue(distance >= 12.0 && distance <= 36.0, "They come 16 to 32 blocks away: " + distance);
        }
        int cap = RebirthConfig.MUTANTS_CAP.get();
        for (int i = level.getEntitiesOfClass(MutantEntity.class, around).size(); i < cap; i++) {
            helper.spawnWithNoFreeWill(HostileRegistry.MUTATED_HOUND.get(), new BlockPos(4, 2, 4));
        }
        helper.assertTrue(MutantSpawner.trySpawn(level, centre, level.random) == 0, "With the cap reached nobody more comes");
        level.getEntitiesOfClass(MutantEntity.class, around).forEach(Entity::discard);
        map.clearAround(centre, 1);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 140, batch = "scouting_mutants_bite")
    public static void aHoundGoesForAPlayer(GameTestHelper helper) {
        MutatedHoundEntity hound = helper.spawn(HostileRegistry.MUTATED_HOUND.get(), new BlockPos(4, 2, 1));
        ServerPlayer player = GuardTurretGameTests.player(helper, GameType.SURVIVAL);
        float before = player.getHealth();
        helper.runAfterDelay(120, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            hound.discard();
            helper.assertTrue(player.getHealth() < before, "The hound bit the player: " + before + " -> " + player.getHealth());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "scouting_mutants_ghoul_toxin")
    public static void aGhoulPollutesTheAirAndLeavesAToxicPuddle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GlowingGhoulEntity ghoul = helper.spawnWithNoFreeWill(HostileRegistry.GLOWING_GHOUL.get(), new BlockPos(4, 2, 4));
        helper.assertTrue(RadiationSources.readingAt(level, helper.absolutePos(new BlockPos(5, 2, 4))).toxin() > 1.0f, "Next to the ghoul the air is polluted");
        ghoul.kill();
        helper.runAfterDelay(2, () -> {
            int puddles = 0;
            for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(2, 1, 2)), helper.absolutePos(new BlockPos(6, 4, 6)))) {
                if (level.getBlockState(pos).is(com.projectrebirth.environment.SourceRegistry.TOXIC_PUDDLE.get())) {
                    puddles++;
                }
            }
            helper.assertTrue(puddles > 0, "Dead, it leaves a toxic puddle");
            helper.assertTrue(RadiationSources.readingAt(level, helper.absolutePos(new BlockPos(4, 2, 4))).toxin() > 1.0f, "Which pollutes the air");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "scouting_mutants_brute_body")
    public static void aDeadBruteLeavesABodyThatRadiates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        com.projectrebirth.hostile.BruteEntity brute = helper.spawnWithNoFreeWill(HostileRegistry.BRUTE.get(), new BlockPos(4, 2, 4));
        float before = RadiationSources.readingAt(level, helper.absolutePos(new BlockPos(6, 2, 4))).radiation();
        brute.kill();
        helper.runAfterDelay(3, () -> {
            java.util.List<com.projectrebirth.hostile.RemainsEntity> bodies = level.getEntitiesOfClass(com.projectrebirth.hostile.RemainsEntity.class,
                    new AABB(helper.absolutePos(new BlockPos(4, 2, 4))).inflate(3.0));
            helper.assertTrue(bodies.size() == 1 && bodies.get(0).kind() == com.projectrebirth.hostile.RemainsEntity.Kind.BRUTE, "Its body lies there");
            float after = RadiationSources.readingAt(level, helper.absolutePos(new BlockPos(6, 2, 4))).radiation();
            helper.assertTrue(after > before + 1.0f, "And radiates: " + before + " -> " + after);
            bodies.get(0).discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = "scouting_mutants_body_rots")
    public static void aBrutesBodyRotsAway(GameTestHelper helper) {
        int before = RebirthConfig.CORPSE_LIFETIME.get();
        RebirthConfig.CORPSE_LIFETIME.set(40);
        com.projectrebirth.hostile.BruteEntity brute = helper.spawnWithNoFreeWill(HostileRegistry.BRUTE.get(), new BlockPos(4, 2, 4));
        brute.kill();
        AABB around = new AABB(helper.absolutePos(new BlockPos(4, 2, 4))).inflate(3.0);
        helper.runAfterDelay(5, () -> helper.assertTrue(!helper.getLevel().getEntitiesOfClass(com.projectrebirth.hostile.RemainsEntity.class, around).isEmpty(), "The body lies there"));
        helper.runAfterDelay(80, () -> {
            try {
                helper.assertTrue(helper.getLevel().getEntitiesOfClass(com.projectrebirth.hostile.RemainsEntity.class, around).isEmpty(), "After its time it is gone");
            } finally {
                RebirthConfig.CORPSE_LIFETIME.set(before);
            }
            helper.succeed();
        });
    }
}
