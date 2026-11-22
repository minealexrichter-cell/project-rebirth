package com.projectrebirth.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyItems;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.garrison.AiCoreItem;
import com.projectrebirth.garrison.Androids;
import com.projectrebirth.garrison.GarrisonMachine;
import com.projectrebirth.garrison.GarrisonRegistry;
import com.projectrebirth.garrison.GuardMechEntity;
import com.projectrebirth.garrison.MachineNavigation;
import com.projectrebirth.garrison.MachineTask;
import com.projectrebirth.garrison.SmallRobotEntity;
import com.projectrebirth.weapons.WeaponRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.8.0, block H: the load of 20 walking machines, measured with spark (only by hand, group projectrebirth_bench). On a floor of
 * 48 × 48 with three long walls, 4 guard mechs, 8 small robots and 8 androids patrol between the two far sides, so that every
 * leg of their routes goes round the walls. After 100 ticks spark profiles the server thread for 6000 ticks and saves the profile
 * to a file (config/spark of the run folder); the log gets the time per tick and how far the machines walked. The game test
 * server does not wait between ticks, so the window is a few seconds of wall time; the test ends 5 s after the stop, so that
 * spark has saved the file. The profile is read
 * with SparkTop (see docs/STATUS.md, block H).
 */
@GameTestHolder(PlanTests.BENCH)
@PrefixGameTestTemplate(false)
public class NavigationBenchGameTests {
    private static final int START = 100;
    private static final int TICKS = 6000;

    private static ServerPlayer player(GameTestHelper helper, int index) {
        UUID id = UUID.randomUUID();
        ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(id, "bench_" + index + "_" + id.toString().substring(0, 4)));
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static void command(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    @GameTest(template = "floor_48x48", timeoutTicks = START + TICKS + 100000)
    public static void twentyMachinesWalkRoundWalls(GameTestHelper helper) {
        // walls across the floor at x 12, 24, 36: open at the far end, the near end, the far end
        for (int z = 0; z < 48; z++) {
            for (int y = 2; y <= 4; y++) {
                if (z < 40) {
                    helper.setBlock(new BlockPos(12, y, z), Blocks.STONE);
                    helper.setBlock(new BlockPos(36, y, z), Blocks.STONE);
                }
                if (z >= 8) {
                    helper.setBlock(new BlockPos(24, y, z), Blocks.STONE);
                }
            }
        }
        List<Entity> machines = new ArrayList<>();
        int owner = 0;
        for (int i = 0; i < 20; i++) {
            int z = 2 + i * 2;
            BlockPos at = helper.absolutePos(new BlockPos(i % 2 == 0 ? 3 : 44, 2, z));
            Entity machine;
            if (i < 4) {
                // one mech for each owner (the limit of a player)
                ItemStack stack = new ItemStack(GarrisonRegistry.GUARD_MECH_ITEM.get());
                stack.set(GarrisonRegistry.MECH_LOADOUT.get(), new GuardMechEntity.Loadout(1, 1, GuardMechEntity.Loadout.FULL, RebirthConfig.GUARD_MECH_ROUNDS.get(), 0));
                machine = GarrisonRegistry.GUARD_MECH_ITEM.get().place(helper.getLevel(), player(helper, owner++), stack, at, 0.0f);
            } else if (i < 12) {
                SmallRobotEntity robot = (SmallRobotEntity) GarrisonRegistry.SMALL_ROBOT_ITEM.get().place(helper.getLevel(), player(helper, owner++),
                        new ItemStack(GarrisonRegistry.SMALL_ROBOT_ITEM.get()), at, 0.0f);
                robot.load(new ItemStack(WeaponRegistry.ASSAULT_RIFLE_AMMO.get(), 64));
                robot.load(new ItemStack(WeaponRegistry.ASSAULT_RIFLE_AMMO.get(), 64));
                machine = robot;
            } else {
                ServerPlayer player = player(helper, owner++);
                ItemStack core = new ItemStack(GarrisonRegistry.AI_CORE.get());
                AiCoreItem.bind(core, player);
                ItemStack body = new ItemStack(BodyItems.item(BodyClass.ADVANCED_SYNTHETIC));
                body.set(GarrisonRegistry.ANDROID_PARTS.get(), new GarrisonRegistry.AndroidParts(core, new ItemStack(GarrisonRegistry.COMBAT_MODULE.get())));
                machine = Androids.place(helper.getLevel(), player, body, BodyClass.ADVANCED_SYNTHETIC, at, 0.0f);
            }
            helper.assertTrue(machine != null, "Machine " + i + " stands");
            BlockPos west = helper.absolutePos(new BlockPos(3, 1, z));
            BlockPos east = helper.absolutePos(new BlockPos(44, 1, 46 - z));
            ((GarrisonMachine) machine).machineCore().setTask(MachineTask.patrol(i % 2 == 0 ? List.of(east, west) : List.of(west, east)));
            machines.add(machine);
        }
        MinecraftServer server = helper.getLevel().getServer();
        Vec3[] last = new Vec3[machines.size()];
        double[] walked = new double[1];
        helper.onEachTick(() -> {
            for (int i = 0; i < machines.size(); i++) {
                Vec3 here = machines.get(i).position();
                if (last[i] != null) {
                    walked[0] += here.subtract(last[i]).horizontalDistance();
                }
                last[i] = here;
            }
        });
        long[] startedAt = new long[1];
        long[] stoppedAt = new long[1];
        helper.runAfterDelay(START, () -> {
            startedAt[0] = System.nanoTime();
            walked[0] = 0;
            command(server, "spark profiler start --timeout 3600");
        });
        helper.runAfterDelay(START + TICKS, () -> {
            command(server, "spark profiler stop --save-to-file");
            double seconds = (System.nanoTime() - startedAt[0]) / 1.0e9;
            ProjectRebirth.LOGGER.info("BENCH navigation improved={} : {} ticks in {} s = {} ms per tick, mean of the last 100 ticks {} ms, machines walked {} blocks",
                    MachineNavigation.improved(), TICKS, String.format("%.1f", seconds), String.format("%.3f", seconds * 1000.0 / TICKS),
                    String.format("%.3f", server.getAverageTickTimeNanos() / 1.0e6), String.format("%.0f", walked[0]));
            machines.forEach(Entity::discard);
            stoppedAt[0] = System.nanoTime();
        });
        helper.succeedWhen(() -> helper.assertTrue(stoppedAt[0] != 0 && System.nanoTime() - stoppedAt[0] > 5_000_000_000L, "spark saves"));
    }
}
