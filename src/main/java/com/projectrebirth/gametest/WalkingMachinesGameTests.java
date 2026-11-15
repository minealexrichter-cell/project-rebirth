package com.projectrebirth.gametest;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.hostile.HostileRegistry;
import com.projectrebirth.hostile.MechEntity;
import com.projectrebirth.hostile.MechRocketEntity;
import com.projectrebirth.hostile.PatrolRobotEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The walking machines of the ruins (1.6.0, part B3b): the patrol robot shoots a player it sees and drops parts; the mech
 * shows a boss bar's health, its rockets burst on their mark without breaking blocks, and the machines do not hurt each other.
 */
@GameTestHolder(PlanTests.SCOUTING)
@PrefixGameTestTemplate(false)
public class WalkingMachinesGameTests {
    private static final String TEMPLATE = "floor_9x9";

    @GameTest(template = TEMPLATE, timeoutTicks = 160, batch = "scouting_robot_shoot")
    public static void aPatrolRobotShootsAPlayerItSees(GameTestHelper helper) {
        PatrolRobotEntity robot = helper.spawn(HostileRegistry.PATROL_ROBOT.get(), new BlockPos(4, 2, 1));
        robot.setYRot(0.0f);
        robot.setYHeadRot(0.0f);
        ServerPlayer player = GuardTurretGameTests.player(helper, GameType.SURVIVAL);
        float before = player.getHealth();
        helper.runAfterDelay(140, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(robot.isAlive(), "The robot stands");
            helper.assertTrue(player.getHealth() < before, "The player in its sight was hurt: " + before + " -> " + player.getHealth());
            helper.assertTrue(robot.post() != null, "It keeps a post");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = "scouting_robot_creative")
    public static void aPatrolRobotLeavesACreativePlayerAlone(GameTestHelper helper) {
        helper.spawn(HostileRegistry.PATROL_ROBOT.get(), new BlockPos(4, 2, 1));
        ServerPlayer player = GuardTurretGameTests.player(helper, GameType.CREATIVE);
        float before = player.getHealth();
        helper.runAfterDelay(100, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(player.getHealth() >= before, "A player in creative is not shot");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "scouting_robot_dead")
    public static void aDestroyedRobotDropsParts(GameTestHelper helper) {
        PatrolRobotEntity robot = helper.spawn(HostileRegistry.PATROL_ROBOT.get(), new BlockPos(4, 2, 4));
        ServerPlayer player = GuardTurretGameTests.player(helper, GameType.SURVIVAL);
        robot.hurt(helper.getLevel().damageSources().playerAttack(player), 10000.0f);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.runAfterDelay(25, () -> {
            helper.assertFalse(robot.isAlive(), "The robot is destroyed");
            AABB around = new AABB(helper.absolutePos(new BlockPos(4, 2, 4))).inflate(3.0);
            helper.assertTrue(!helper.getLevel().getEntitiesOfClass(ItemEntity.class, around).isEmpty(), "It drops parts");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "scouting_machines_friends")
    public static void theMachinesDoNotHurtEachOther(GameTestHelper helper) {
        MechEntity mech = helper.spawn(HostileRegistry.MECH.get(), new BlockPos(2, 2, 2));
        PatrolRobotEntity robot = helper.spawn(HostileRegistry.PATROL_ROBOT.get(), new BlockPos(6, 2, 6));
        float before = robot.getHealth();
        helper.assertFalse(robot.hurt(helper.getLevel().damageSources().mobAttack(mech), 10.0f), "The mech's blow does nothing to a robot");
        helper.assertFalse(mech.hurt(helper.getLevel().damageSources().explosion(robot, robot), 10.0f), "Nor a robot's to the mech");
        helper.assertTrue(robot.getHealth() == before, "The robot is whole");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "scouting_mech_rocket")
    public static void aRocketBurstsOnItsMarkWithoutBreakingBlocks(GameTestHelper helper) {
        boolean breaks = RebirthConfig.MECH_ROCKETS_BREAK.get();
        RebirthConfig.MECH_ROCKETS_BREAK.set(false);
        MechEntity mech = helper.spawn(HostileRegistry.MECH.get(), new BlockPos(1, 2, 1));
        mech.setNoAi(true);
        for (int x = 5; x <= 7; x++) {
            for (int z = 5; z <= 7; z++) {
                helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
            }
        }
        Vec3 mark = helper.absoluteVec(new Vec3(6.5, 3.05, 6.5));
        MechRocketEntity rocket = new MechRocketEntity(helper.getLevel(), mech, helper.absoluteVec(new Vec3(1.5, 5.0, 1.5)), mark, 20);
        helper.getLevel().addFreshEntity(rocket);
        helper.runAfterDelay(40, () -> {
            try {
                helper.assertFalse(rocket.isAlive(), "The rocket has burst");
                for (int x = 5; x <= 7; x++) {
                    for (int z = 5; z <= 7; z++) {
                        helper.assertBlockPresent(Blocks.STONE, new BlockPos(x, 2, z));
                    }
                }
                helper.assertTrue(mech.getHealth() == mech.getMaxHealth(), "Its own rocket does not hurt the mech");
            } finally {
                RebirthConfig.MECH_ROCKETS_BREAK.set(breaks);
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = "scouting_mech_shoot")
    public static void theMechFightsAPlayerItSees(GameTestHelper helper) {
        MechEntity mech = helper.spawn(HostileRegistry.MECH.get(), new BlockPos(4, 2, 1));
        mech.setYRot(0.0f);
        mech.setYHeadRot(0.0f);
        ServerPlayer player = GuardTurretGameTests.player(helper, GameType.SURVIVAL);
        float before = player.getHealth();
        helper.runAfterDelay(100, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(mech.getMaxHealth() == RebirthConfig.MECH_HEALTH.get().floatValue(), "Its health comes from the config");
            helper.assertTrue(player.getHealth() < before, "The player near it was hurt: " + before + " -> " + player.getHealth());
            helper.succeed();
        });
    }
}
