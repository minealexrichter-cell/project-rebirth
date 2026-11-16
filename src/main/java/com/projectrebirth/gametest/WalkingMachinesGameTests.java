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
    public static void aDestroyedRobotLeavesAWreckThatComesApartForParts(GameTestHelper helper) {
        PatrolRobotEntity robot = helper.spawn(HostileRegistry.PATROL_ROBOT.get(), new BlockPos(4, 2, 4));
        robot.kill();
        helper.runAfterDelay(3, () -> {
            helper.assertFalse(robot.isAlive(), "The robot is destroyed");
            AABB around = new AABB(helper.absolutePos(new BlockPos(4, 2, 4))).inflate(3.0);
            java.util.List<com.projectrebirth.hostile.RemainsEntity> wrecks = helper.getLevel().getEntitiesOfClass(com.projectrebirth.hostile.RemainsEntity.class, around);
            helper.assertTrue(wrecks.size() == 1 && wrecks.get(0).kind() == com.projectrebirth.hostile.RemainsEntity.Kind.PATROL_ROBOT, "Its wreck lies where it stood");
            helper.getLevel().getEntitiesOfClass(ItemEntity.class, around).forEach(net.minecraft.world.entity.Entity::discard);
            wrecks.get(0).dismantle(helper.getLevel(), null);
            helper.assertFalse(wrecks.get(0).isAlive(), "Taken apart, the wreck is gone");
            helper.assertTrue(!helper.getLevel().getEntitiesOfClass(ItemEntity.class, around).isEmpty(), "And gives its parts");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 140, batch = "scouting_mech_phase")
    public static void atHalfHealthTheMechShedsItsArmor(GameTestHelper helper) {
        MechEntity mech = helper.spawn(HostileRegistry.MECH.get(), new BlockPos(4, 2, 4));
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(mech.phase() == 0, "It starts armored");
            mech.setHealth(mech.getMaxHealth() * 0.45f);
        });
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(mech.phase() == 1, "Below half it sheds its armor");
            float before = mech.getHealth();
            helper.assertFalse(mech.hurt(helper.getLevel().damageSources().generic(), 10.0f), "While it does, it takes no damage");
            helper.assertTrue(mech.getHealth() == before, "Its health is kept");
        });
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(mech.phase() == 2, "Then it fights stripped");
            helper.assertTrue(mech.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) > RebirthConfig.MECH_SPEED.get(), "And faster");
            mech.discard();
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

    @GameTest(template = TEMPLATE, batch = "scouting_machines_solid")
    public static void theMachinesAreSolid(GameTestHelper helper) {
        PatrolRobotEntity robot = helper.spawnWithNoFreeWill(HostileRegistry.PATROL_ROBOT.get(), new BlockPos(2, 2, 2));
        MechEntity mech = helper.spawnWithNoFreeWill(HostileRegistry.MECH.get(), new BlockPos(6, 2, 6));
        com.projectrebirth.hostile.GuardTurretEntity turret = helper.spawnWithNoFreeWill(HostileRegistry.GUARD_TURRET.get(), new BlockPos(6, 2, 2));
        helper.assertTrue(robot.canBeCollidedWith() && mech.canBeCollidedWith() && turret.canBeCollidedWith(), "One bumps into them");
        helper.assertFalse(robot.isPushable() || mech.isPushable() || turret.isPushable(), "And cannot push them about");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 260, batch = "scouting_mech_flight")
    public static void inTheSecondPhaseTheMechFliesToATargetOutOfReach(GameTestHelper helper) {
        MechEntity mech = helper.spawn(HostileRegistry.MECH.get(), new BlockPos(4, 2, 4));
        ServerPlayer player = GuardTurretGameTests.player(helper, GameType.SURVIVAL);
        net.minecraft.world.phys.Vec3 high = helper.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 7.0, 8.5));
        for (int x = 3; x <= 5; x++) {
            for (int z = 7; z <= 9; z++) {
                helper.setBlock(new BlockPos(x, 6, z), net.minecraft.world.level.block.Blocks.STONE);
            }
        }
        player.moveTo(high.x, high.y, high.z, 180.0f, 0.0f);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 400, 4));
        helper.runAfterDelay(2, () -> mech.setHealth(mech.getMaxHealth() * 0.45f));
        helper.succeedWhen(() -> {
            if (mech.isFlying()) {
                helper.getLevel().getServer().getPlayerList().remove(player);
                mech.discard();
                return;
            }
            helper.assertTrue(false, "Not flying yet (phase " + mech.phase() + ")");
        });
    }
}
