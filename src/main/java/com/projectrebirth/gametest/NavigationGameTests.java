package com.projectrebirth.gametest;

import java.util.UUID;

import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyItems;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.garrison.AiCoreItem;
import com.projectrebirth.garrison.AndroidEntity;
import com.projectrebirth.garrison.Androids;
import com.projectrebirth.garrison.GarrisonRegistry;
import com.projectrebirth.garrison.GuardMechEntity;
import com.projectrebirth.garrison.MachineTask;
import com.projectrebirth.garrison.SmallRobotEntity;
import com.projectrebirth.shelter.ShelterRegistry;
import com.projectrebirth.weapons.WeaponRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.8.0, block H: machines find their way through a maze, round a long wall, a pit, water, up a stepped slope, through a narrow
 * passage (a mech that does not fit takes the wide way or reports that it is stuck), never through an airlock door, through a
 * wooden door, and round a wall put up on their way.
 * <p>
 * The floor (floor_24x24) is stone six rows deep (test y 1-6): the ground is y 6, machines stand at y 7.
 */
@GameTestHolder(PlanTests.INTERFACE)
@PrefixGameTestTemplate(false)
public class NavigationGameTests {
    private static final String TEMPLATE = "floor_24x24";
    private static final int GROUND = 6;
    private static final int STAND = 7;
    /** A mech and a small robot stop within three blocks of their point (an android within one and a half). */
    private static final double NEAR = 3.2;

    private static ServerPlayer player(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(id, "nav_test_" + id.toString().substring(0, 6)));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.setShiftKeyDown(false);
        return player;
    }

    private static AndroidEntity android(GameTestHelper helper, ServerPlayer owner, int x, int z) {
        ItemStack core = new ItemStack(GarrisonRegistry.AI_CORE.get());
        AiCoreItem.bind(core, owner);
        ItemStack stack = new ItemStack(BodyItems.item(BodyClass.ADVANCED_SYNTHETIC));
        stack.set(GarrisonRegistry.ANDROID_PARTS.get(), new GarrisonRegistry.AndroidParts(core, new ItemStack(GarrisonRegistry.COMBAT_MODULE.get())));
        AndroidEntity android = Androids.place(helper.getLevel(), owner, stack, BodyClass.ADVANCED_SYNTHETIC, helper.absolutePos(new BlockPos(x, STAND, z)), 0.0f);
        helper.assertTrue(android != null, "The android stands");
        return android;
    }

    private static SmallRobotEntity robot(GameTestHelper helper, ServerPlayer owner, int x, int z) {
        ItemStack stack = new ItemStack(GarrisonRegistry.SMALL_ROBOT_ITEM.get());
        SmallRobotEntity robot = (SmallRobotEntity) GarrisonRegistry.SMALL_ROBOT_ITEM.get().place(helper.getLevel(), owner, stack,
                helper.absolutePos(new BlockPos(x, STAND, z)), 0.0f);
        helper.assertTrue(robot != null, "The robot stands");
        // with rounds, else it would go to a post to load first
        robot.load(new ItemStack(WeaponRegistry.ASSAULT_RIFLE_AMMO.get(), 64));
        robot.load(new ItemStack(WeaponRegistry.ASSAULT_RIFLE_AMMO.get(), 64));
        return robot;
    }

    private static GuardMechEntity mech(GameTestHelper helper, ServerPlayer owner, int x, int z) {
        ItemStack stack = new ItemStack(GarrisonRegistry.GUARD_MECH_ITEM.get());
        stack.set(GarrisonRegistry.MECH_LOADOUT.get(), new GuardMechEntity.Loadout(1, 1, GuardMechEntity.Loadout.FULL, RebirthConfig.GUARD_MECH_ROUNDS.get(), 0));
        GuardMechEntity mech = (GuardMechEntity) GarrisonRegistry.GUARD_MECH_ITEM.get().place(helper.getLevel(), owner, stack,
                helper.absolutePos(new BlockPos(x, STAND, z)), 0.0f);
        helper.assertTrue(mech != null, "The mech stands");
        return mech;
    }

    /** Orders it to guard the ground block (x, ground, z) and returns where it should stand. */
    private static Vec3 sendTo(GameTestHelper helper, PathfinderMob machine, int x, int z) {
        BlockPos point = helper.absolutePos(new BlockPos(x, GROUND, z));
        ((com.projectrebirth.garrison.GarrisonMachine) machine).machineCore().setTask(MachineTask.at(MachineTask.Type.GUARD, point));
        return Vec3.atBottomCenterOf(point.above());
    }

    private static double flat(PathfinderMob machine, Vec3 place) {
        return machine.position().subtract(place).horizontalDistance();
    }

    /** A wall of stone along x from z0 to z1 (both included), `height` blocks over the ground. */
    private static void wallX(GameTestHelper helper, int x, int z0, int z1, int height) {
        for (int z = z0; z <= z1; z++) {
            for (int y = STAND; y < STAND + height; y++) {
                helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
    }

    private static void door(GameTestHelper helper, int x, int z, Block block) {
        BlockState lower = block.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        helper.setBlock(new BlockPos(x, STAND, z), lower);
        helper.setBlock(new BlockPos(x, STAND + 1, z), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static boolean doorOpen(GameTestHelper helper, int x, int z) {
        return helper.getBlockState(new BlockPos(x, STAND, z)).getValue(DoorBlock.OPEN);
    }

    private static String describe(PathfinderMob machine) {
        net.minecraft.world.level.pathfinder.Path path = machine.getNavigation().getPath();
        return (path == null ? "none" : path.getNodeCount() + " points, reaches " + path.canReach() + ", end " + path.getEndNode())
                + ", doors " + ((net.minecraft.world.entity.ai.navigation.GroundPathNavigation) machine.getNavigation()).canOpenDoors();
    }

    // ------------------------------------------------------------------ the tests

    @GameTest(template = TEMPLATE, timeoutTicks = 1600)
    public static void anAndroidFindsItsWayThroughAMaze(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        // three walls, the openings at the far end, the near end, the far end: a way of some 80 blocks
        wallX(helper, 6, 0, 18, 3);
        wallX(helper, 12, 5, 23, 3);
        wallX(helper, 18, 0, 18, 3);
        AndroidEntity android = android(helper, owner, 2, 2);
        Vec3 place = sendTo(helper, android, 21, 2);
        helper.succeedWhen(() -> {
            helper.assertTrue(flat(android, place) < 1.5, "Through the maze: " + android.position());
            android.discard();
        });
    }

    /**
     * A mech round a long wall. Its path ends on its point (1.8.0: before, the path of a wide machine was a block off toward +X/+Z);
     * it stops within three blocks of it, as always.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 1000)
    public static void aMechWalksRoundALongWallOntoItsPoint(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        wallX(helper, 12, 0, 18, 4);
        GuardMechEntity mech = mech(helper, owner, 5, 5);
        BlockPos point = helper.absolutePos(new BlockPos(19, GROUND, 5));
        helper.runAfterDelay(20, () -> pathOfAMech(helper, mech, point));
    }

    private static void pathOfAMech(GameTestHelper helper, GuardMechEntity mech, BlockPos point) {
        net.minecraft.world.level.pathfinder.Path path = mech.getNavigation().createPath(point.above(), 0);
        helper.assertTrue(path != null && path.canReach(), "A path round the wall: " + (path == null ? "none" : path.getEndNode() + " of " + path.getNodeCount()) + ", stands " + mech.position() + " on ground " + mech.onGround());
        Vec3 end = path.getEntityPosAtNode(mech, path.getNodeCount() - 1);
        helper.assertTrue(end.subtract(Vec3.atBottomCenterOf(point.above())).horizontalDistance() < 0.75, "The path ends on the point: " + end);
        Vec3 start = path.getEntityPosAtNode(mech, 0);
        helper.assertTrue(start.subtract(mech.position()).horizontalDistance() < 1.0, "The path starts where it stands: " + start + " / " + mech.position());
        Vec3 place = sendTo(helper, mech, 19, 5);
        helper.succeedWhen(() -> {
            helper.assertTrue(flat(mech, place) < NEAR, "Round the wall to its point: " + mech.position());
            mech.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 800)
    public static void aRobotGoesRoundAPitNotIntoIt(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        // a pit five deep across most of the floor; the way round at z 20-23
        for (int x = 10; x <= 12; x++) {
            for (int z = 0; z <= 19; z++) {
                for (int y = 2; y <= GROUND; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        SmallRobotEntity robot = robot(helper, owner, 4, 4);
        Vec3 place = sendTo(helper, robot, 20, 4);
        double floorY = helper.absolutePos(new BlockPos(0, STAND, 0)).getY();
        helper.onEachTick(() -> helper.assertTrue(robot.getY() > floorY - 0.5, "It never falls into the pit: " + robot.position()));
        helper.succeedWhen(() -> {
            helper.assertTrue(flat(robot, place) < NEAR, "Round the pit: " + robot.position());
            robot.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 800)
    public static void aRobotClimbsAStepSlope(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        // four steps of a block across the whole floor, then a terrace four blocks up
        for (int x = 8; x <= 23; x++) {
            int height = Math.min(4, x - 7);
            for (int z = 0; z <= 23; z++) {
                for (int y = STAND; y < STAND + height; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        SmallRobotEntity robot = robot(helper, owner, 3, 8);
        BlockPos point = helper.absolutePos(new BlockPos(18, GROUND + 4, 8));
        robot.machineCore().setTask(MachineTask.at(MachineTask.Type.GUARD, point));
        Vec3 place = Vec3.atBottomCenterOf(point.above());
        helper.succeedWhen(() -> {
            helper.assertTrue(robot.position().distanceTo(place) < NEAR, "Up on the terrace: " + robot.position());
            robot.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1000)
    public static void anAndroidGoesRoundWaterNotThroughIt(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        // a pool across the floor, dry at z 18-23
        for (int x = 9; x <= 13; x++) {
            for (int z = 0; z <= 17; z++) {
                helper.setBlock(new BlockPos(x, GROUND, z), Blocks.WATER);
            }
        }
        AndroidEntity android = android(helper, owner, 3, 4);
        Vec3 place = sendTo(helper, android, 20, 4);
        helper.onEachTick(() -> helper.assertFalse(android.isInWater(), "It keeps out of the water: " + android.position()));
        helper.succeedWhen(() -> {
            helper.assertTrue(flat(android, place) < 1.5, "Round the pool: " + android.position());
            android.discard();
        });
    }

    /** A wall with an opening one block wide at z 6, and (when `wide`) one three blocks wide at z 18-20. */
    private static void narrowWall(GameTestHelper helper, boolean wide) {
        wallX(helper, 12, 0, 23, 4);
        for (int y = STAND; y < STAND + 3; y++) {
            helper.setBlock(new BlockPos(12, y, 6), Blocks.AIR);
            if (wide) {
                for (int z = 18; z <= 20; z++) {
                    helper.setBlock(new BlockPos(12, y + 1, z), Blocks.AIR);
                    helper.setBlock(new BlockPos(12, y, z), Blocks.AIR);
                }
            }
        }
        if (wide) {
            for (int z = 18; z <= 20; z++) {
                helper.setBlock(new BlockPos(12, STAND + 3, z), Blocks.AIR);
            }
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 800)
    public static void anAndroidSlipsThroughANarrowPassage(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        narrowWall(helper, false);
        AndroidEntity android = android(helper, owner, 4, 6);
        Vec3 place = sendTo(helper, android, 20, 6);
        helper.succeedWhen(() -> {
            helper.assertTrue(flat(android, place) < 1.5, "Through the gap: " + android.position());
            android.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public static void aMechTakesTheWideWayRoundANarrowPassage(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        narrowWall(helper, true);
        GuardMechEntity mech = mech(helper, owner, 5, 6);
        Vec3 place = sendTo(helper, mech, 19, 6);
        helper.succeedWhen(() -> {
            helper.assertTrue(flat(mech, place) < NEAR, "Round by the wide opening: " + mech.position());
            mech.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 900)
    public static void aMechThatDoesNotFitReportsItIsStuck(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        narrowWall(helper, false);
        // the wall up to a roof over its side: its jetpack does not take it over
        wallX(helper, 12, 0, 23, 7);
        for (int y = STAND; y < STAND + 3; y++) {
            helper.setBlock(new BlockPos(12, y, 6), Blocks.AIR);
        }
        for (int x = 0; x < 12; x++) {
            for (int z = 0; z <= 23; z++) {
                helper.setBlock(new BlockPos(x, STAND + 7, z), Blocks.STONE);
            }
        }
        // its console: the alarm "stuck" comes there (with where it is)
        BlockPos consolePos = helper.absolutePos(new BlockPos(1, STAND, 20));
        helper.getLevel().setBlockAndUpdate(consolePos, GarrisonRegistry.CONSOLE.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        com.projectrebirth.garrison.ConsoleBlockEntity console = (com.projectrebirth.garrison.ConsoleBlockEntity) helper.getLevel().getBlockEntity(consolePos);
        console.setOwner(owner.getUUID());
        console.energy().load(console.energy().getMaxEnergyStored());
        console.serverTick();
        GuardMechEntity mech = mech(helper, owner, 5, 6);
        helper.assertTrue(console.knows(mech.getUUID()), "Linked to its console");
        // the order through the console (a linked machine takes its console's orders)
        console.order(mech.getUUID(), MachineTask.at(MachineTask.Type.GUARD, helper.absolutePos(new BlockPos(19, GROUND, 6))));
        Vec3 place = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(19, STAND, 6)));
        double wallX = helper.absolutePos(new BlockPos(12, 0, 0)).getX();
        helper.onEachTick(() -> helper.assertTrue(mech.getX() < wallX, "It does not get through a gap a block wide: " + mech.position()));
        helper.succeedWhen(() -> {
            helper.assertTrue(mech.mover().stuck(), "It knows that it is stuck: " + mech.mover().failedChecks() + " at " + mech.position() + ", " + describe(mech) + ", task " + mech.machineCore().task().type());
            helper.assertTrue(console.alarmOn(mech.getUUID(), com.projectrebirth.garrison.ConsoleAlarm.STUCK), "The console has the alarm");
            helper.assertTrue(flat(mech, place) > 4.0, "Still on its side");
            mech.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 900)
    public static void anAndroidNeverOpensAnAirlockDoor(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        wallX(helper, 12, 0, 23, 4);
        door(helper, 12, 6, ShelterRegistry.AIRLOCK_DOOR.get());
        AndroidEntity android = android(helper, owner, 4, 6);
        sendTo(helper, android, 20, 6);
        double wallX = helper.absolutePos(new BlockPos(12, 0, 0)).getX();
        helper.onEachTick(() -> {
            helper.assertFalse(doorOpen(helper, 12, 6), "The airlock stays shut");
            helper.assertTrue(android.getX() < wallX, "It stays on its side: " + android.position());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(android.mover().stuck(), "It reports that it is stuck");
            android.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 900)
    public static void anAndroidOpensAWoodenDoorOnItsWay(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        wallX(helper, 12, 0, 23, 4);
        door(helper, 12, 6, Blocks.OAK_DOOR);
        AndroidEntity android = android(helper, owner, 4, 6);
        Vec3 place = sendTo(helper, android, 20, 6);
        helper.succeedWhen(() -> {
            helper.assertTrue(flat(android, place) < 1.5, "Through the door: " + android.position() + ", path " + describe(android));
            android.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 900)
    public static void aRobotFindsANewWayWhenAWallGoesUpOnItsPath(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        SmallRobotEntity robot = robot(helper, owner, 3, 4);
        Vec3 place = sendTo(helper, robot, 21, 4);
        // once it is on its way, a wall across its path, open at z 17-23
        helper.runAfterDelay(30, () -> wallX(helper, 13, 0, 16, 3));
        helper.runAfterDelay(31, () -> helper.assertTrue(robot.getX() < helper.absolutePos(new BlockPos(12, 0, 0)).getX(),
                "The wall went up in front of it: " + robot.position()));
        helper.succeedWhen(() -> {
            helper.assertTrue(flat(robot, place) < NEAR, "Round the new wall: " + robot.position());
            robot.discard();
        });
    }
    /** 1.8.0 (owner): a line of cable across the plot (its top 11 pixels up, over a step of 0.6): machines get over it. */
    private static void cableLine(GameTestHelper helper, int x) {
        for (int z = 0; z <= 23; z++) {
            helper.setBlock(new BlockPos(x, STAND, z), com.projectrebirth.technology.TechBlocks.CABLE.get());
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void aRobotGetsOverACable(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        cableLine(helper, 10);
        SmallRobotEntity robot = robot(helper, owner, 5, 12);
        Vec3 place = sendTo(helper, robot, 16, 12);
        helper.succeedWhen(() -> {
            helper.assertTrue(flat(robot, place) < NEAR, "Over the cable at its point: " + robot.position());
            robot.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void anAndroidGetsOverACable(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        cableLine(helper, 10);
        AndroidEntity android = android(helper, owner, 5, 12);
        Vec3 place = sendTo(helper, android, 16, 12);
        helper.succeedWhen(() -> {
            helper.assertTrue(flat(android, place) < 1.5, "Over the cable at its point: " + android.position());
            android.discard();
        });
    }
}
