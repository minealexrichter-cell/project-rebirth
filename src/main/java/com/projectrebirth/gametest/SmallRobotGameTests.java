package com.projectrebirth.gametest;

import java.util.UUID;

import com.projectrebirth.bodies.power.BodyPowerRegistry;
import com.projectrebirth.bodies.power.ChargingPadBlockEntity;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.garrison.GarrisonRegistry;
import com.projectrebirth.garrison.GuardMechEntity;
import com.projectrebirth.garrison.SmallRobotEntity;
import com.projectrebirth.weapons.WeaponRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 1.7.1, block E: the small robot, the cheap soldier of the heavy dock, on the guard mech's logic with its own numbers. */
@GameTestHolder(PlanTests.GARRISON)
@PrefixGameTestTemplate(false)
public class SmallRobotGameTests {
    private static final String TEMPLATE = "floor_9x9";

    private static ServerPlayer player(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(id, "robot_test_" + id.toString().substring(0, 6)));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.setShiftKeyDown(false);
        return player;
    }

    private static SmallRobotEntity robot(GameTestHelper helper, ServerPlayer owner, BlockPos at) {
        ItemStack stack = new ItemStack(GarrisonRegistry.SMALL_ROBOT_ITEM.get());
        SmallRobotEntity robot = (SmallRobotEntity) GarrisonRegistry.SMALL_ROBOT_ITEM.get().place(helper.getLevel(), owner, stack, helper.absolutePos(at), 0.0f);
        helper.assertTrue(robot != null, "The robot stands");
        return robot;
    }

    @GameTest(template = TEMPLATE)
    public static void aRobotHasItsOwnNumbersAndAFixedGun(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        SmallRobotEntity robot = robot(helper, owner, new BlockPos(4, 2, 4));
        helper.assertTrue(robot.machineCore().maxStructural() == 50.0f && robot.machineCore().maxCritical() == 25.0f, "Integrity 50 / 25");
        helper.assertTrue(robot.capacity() == RebirthConfig.SMALL_ROBOT_ENERGY_CAPACITY.get() && robot.energy() == robot.capacity(), "Its own battery, full");
        helper.assertTrue(robot.node(GuardMechEntity.RIGHT) == GuardMechEntity.Node.GUN && robot.node(GuardMechEntity.LEFT) == GuardMechEntity.Node.NONE,
                "A gun on the right only");
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GarrisonRegistry.ROCKET_LAUNCHER_NODE.get()));
        robot.interact(owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(robot.node(GuardMechEntity.RIGHT) == GuardMechEntity.Node.GUN && owner.getMainHandItem().getCount() == 1,
                "Its gun cannot be swapped");
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(WeaponRegistry.ASSAULT_RIFLE_AMMO.get(), 64));
        for (int i = 0; i < 4; i++) {
            ItemStack more = new ItemStack(WeaponRegistry.ASSAULT_RIFLE_AMMO.get(), 64);
            owner.setItemInHand(InteractionHand.MAIN_HAND, more);
            robot.interact(owner, InteractionHand.MAIN_HAND);
        }
        helper.assertTrue(robot.rounds() == RebirthConfig.SMALL_ROBOT_ROUNDS.get(), "The magazine holds 192, got " + robot.rounds());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500)
    public static void aLowRobotChargesOnAPad(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        helper.setBlock(new BlockPos(7, 2, 7), BodyPowerRegistry.CHARGING_PAD.get());
        ChargingPadBlockEntity pad = (ChargingPadBlockEntity) helper.getBlockEntity(new BlockPos(7, 2, 7));
        pad.energy().generate(RebirthConfig.CHARGING_PAD_CAPACITY.get());
        SmallRobotEntity robot = robot(helper, owner, new BlockPos(2, 2, 2));
        float low = robot.capacity() * 0.1f;
        robot.setEnergy(low);
        helper.runAfterDelay(460, () -> {
            helper.assertTrue(robot.energy() > low + 1000, "It walked to the pad and charged: " + robot.energy() + " at " + robot.blockPosition());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void aRobotShootsAMonster(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        SmallRobotEntity robot = robot(helper, owner, new BlockPos(1, 2, 4));
        robot.load(new ItemStack(WeaponRegistry.ASSAULT_RIFLE_AMMO.get(), 64));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(7, 2, 4));
        zombie.setNoAi(true);
        helper.runAfterDelay(260, () -> {
            helper.assertTrue(robot.rounds() < 64, "It fired: " + robot.rounds() + " rounds left");
            helper.assertTrue(!zombie.isAlive() || zombie.getHealth() < zombie.getMaxHealth(), "And hit the zombie");
            helper.succeed();
        });
    }
}
