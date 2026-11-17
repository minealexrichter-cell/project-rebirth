package com.projectrebirth.gametest;

import java.util.UUID;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.garrison.GarrisonRegistry;
import com.projectrebirth.garrison.MachineTask;
import com.projectrebirth.garrison.ServicePostBlockEntity;
import com.projectrebirth.garrison.SmallRobotEntity;
import com.projectrebirth.weapons.WeaponRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 1.7.2, block B: machines find their way, notice when they are stuck and go to the post for ammunition. */
@GameTestHolder(PlanTests.GARRISON)
@PrefixGameTestTemplate(false)
public class MachineMovementGameTests {
    private static final String TEMPLATE = "floor_9x9";

    private static ServerPlayer player(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(id, "move_test_" + id.toString().substring(0, 6)));
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

    private static void wall(GameTestHelper helper, int x, int z0, int z1, int height) {
        for (int z = z0; z <= z1; z++) {
            for (int y = 2; y < 2 + height; y++) {
                helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
    }

    @GameTest(template = TEMPLATE)
    public static void theWayIsFreeOnlyWhereItsBodyGetsThrough(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        SmallRobotEntity robot = robot(helper, owner, new BlockPos(1, 2, 4));
        helper.assertTrue(robot.mover().clearWay(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(7, 2, 4)))), "Open floor: free");
        wall(helper, 4, 0, 8, 3);
        helper.assertFalse(robot.mover().clearWay(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(7, 2, 4)))), "A wall: not free");
        helper.setBlock(new BlockPos(4, 3, 4), Blocks.AIR);
        helper.setBlock(new BlockPos(4, 4, 4), Blocks.AIR);
        helper.assertFalse(robot.mover().clearWay(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(7, 2, 4)))), "A hole too low for it: not free");
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.AIR);
        helper.setBlock(new BlockPos(4, 2, 3), Blocks.AIR);
        helper.setBlock(new BlockPos(4, 3, 3), Blocks.AIR);
        helper.setBlock(new BlockPos(4, 4, 3), Blocks.AIR);
        helper.assertTrue(robot.mover().clearWay(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(7, 2, 4)))),
                "A gap two blocks wide and three high: free");
        robot.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500)
    public static void aRobotWalksRoundAWallToItsPoint(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        wall(helper, 4, 0, 6, 3);
        SmallRobotEntity robot = robot(helper, owner, new BlockPos(1, 2, 2));
        robot.load(new ItemStack(WeaponRegistry.ASSAULT_RIFLE_AMMO.get(), 64));
        robot.load(new ItemStack(WeaponRegistry.ASSAULT_RIFLE_AMMO.get(), 64));
        BlockPos point = helper.absolutePos(new BlockPos(7, 1, 2));
        robot.machineCore().setTask(MachineTask.at(MachineTask.Type.GUARD, point));
        helper.succeedWhen(() -> {
            helper.assertTrue(robot.distanceToSqr(Vec3.atBottomCenterOf(point.above())) < 9.0, "Round the wall to its point: " + robot.position());
            robot.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void aWalledInRobotKnowsItIsStuck(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        for (int x = 2; x <= 6; x++) {
            for (int z = 2; z <= 6; z++) {
                if (x == 2 || x == 6 || z == 2 || z == 6) {
                    for (int y = 2; y <= 5; y++) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                    }
                }
            }
        }
        SmallRobotEntity robot = robot(helper, owner, new BlockPos(4, 2, 4));
        robot.load(new ItemStack(WeaponRegistry.ASSAULT_RIFLE_AMMO.get(), 64));
        robot.load(new ItemStack(WeaponRegistry.ASSAULT_RIFLE_AMMO.get(), 64));
        robot.machineCore().setTask(MachineTask.at(MachineTask.Type.GUARD, helper.absolutePos(new BlockPos(8, 1, 8))));
        helper.assertFalse(robot.mover().stuck(), "Not stuck at first");
        helper.succeedWhen(() -> {
            helper.assertTrue(robot.mover().stuck(), "It notices it does not get on");
            robot.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void aRobotOutOfRoundsGoesToThePostToLoad(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                helper.setBlock(new BlockPos(6 + dx, 1, 6 + dz), GarrisonRegistry.SERVICE_POST_PLATE.get());
            }
        }
        helper.setBlock(new BlockPos(5, 1, 6), GarrisonRegistry.SERVICE_POST.get());
        helper.setBlock(new BlockPos(4, 1, 6), Blocks.CHEST);
        ServicePostBlockEntity post = (ServicePostBlockEntity) helper.getBlockEntity(new BlockPos(5, 1, 6));
        post.energy().generate(RebirthConfig.POST_ENERGY_CAPACITY.get());
        Container chest = (Container) helper.getBlockEntity(new BlockPos(4, 1, 6));
        chest.setItem(0, new ItemStack(WeaponRegistry.ASSAULT_RIFLE_AMMO.get(), 64));
        SmallRobotEntity robot = robot(helper, owner, new BlockPos(1, 2, 1));
        helper.assertTrue(robot.rounds() == 0 && robot.needsAmmo(), "Empty: it needs rounds");
        helper.succeedWhen(() -> {
            helper.assertTrue(robot.rounds() > 0, "It walked to the post and loaded: " + robot.rounds() + " at " + robot.position());
            robot.discard();
        });
    }
}
