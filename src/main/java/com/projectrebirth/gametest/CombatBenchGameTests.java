package com.projectrebirth.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.projectrebirth.ProjectRebirth;
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
import com.projectrebirth.garrison.tactics.Tactics;
import com.projectrebirth.hostile.HostileRegistry;
import com.projectrebirth.weapons.WeaponRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.8.0, block I: the load of 20 machines fighting mutants, measured with spark (only by hand, group projectrebirth_bench). In the
 * middle of a floor of 48 × 48 with a few blocks of cover, 4 guard mechs, 8 small robots and 8 androids (swords and shields,
 * bows) guard the middle; hounds, ghouls and brutes come from the edges, about 15 at a time, new ones as they fall. After 100
 * ticks spark profiles the server thread for 6000 ticks and saves the profile (config/spark); the log gets the time per tick and
 * how many mutants fell. The machines' bars are filled each second, so that the fight goes on. Runs only with the environment
 * variable REBIRTH_BENCH=combat (one measurement at a time).
 */
@GameTestHolder(PlanTests.BENCH)
@PrefixGameTestTemplate(false)
public class CombatBenchGameTests {
    private static final int START = 100;
    private static final int TICKS = 6000;
    private static final int MUTANTS = 15;

    private static ServerPlayer player(GameTestHelper helper, int index) {
        UUID id = UUID.randomUUID();
        ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(id, "fight_" + index + "_" + id.toString().substring(0, 4)));
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    @GameTest(template = "floor_48x48", timeoutTicks = START + TICKS + 100000)
    public static void twentyMachinesFightMutants(GameTestHelper helper) {
        if (!"combat".equals(System.getenv("REBIRTH_BENCH"))) {
            // one measurement at a time: this one with REBIRTH_BENCH=combat
            helper.succeed();
            return;
        }
        // a few low walls to take cover behind
        for (int[] w : new int[][] {{18, 18}, {29, 18}, {18, 29}, {29, 29}}) {
            for (int d = 0; d < 3; d++) {
                helper.setBlock(new BlockPos(w[0] + d, 2, w[1]), Blocks.STONE);
                helper.setBlock(new BlockPos(w[0] + d, 3, w[1]), Blocks.STONE);
            }
        }
        List<Entity> machines = new ArrayList<>();
        BlockPos middle = helper.absolutePos(new BlockPos(24, 1, 24));
        for (int i = 0; i < 20; i++) {
            // mechs at the four corners of the middle, the others in a grid between them
            int[][] corners = {{15, 15}, {33, 15}, {15, 33}, {33, 33}};
            int k = i - 4;
            BlockPos at = helper.absolutePos(i < 4 ? new BlockPos(corners[i][0], 2, corners[i][1]) : new BlockPos(19 + (k % 4) * 3, 2, 19 + (k / 4) * 3));
            ServerPlayer owner = player(helper, i);
            Entity machine;
            if (i < 4) {
                ItemStack stack = new ItemStack(GarrisonRegistry.GUARD_MECH_ITEM.get());
                stack.set(GarrisonRegistry.MECH_LOADOUT.get(), new GuardMechEntity.Loadout(1, 2, GuardMechEntity.Loadout.FULL,
                        RebirthConfig.GUARD_MECH_ROUNDS.get(), RebirthConfig.GUARD_MECH_ROCKETS.get()));
                machine = GarrisonRegistry.GUARD_MECH_ITEM.get().place(helper.getLevel(), owner, stack, at, 0.0f);
            } else if (i < 12) {
                SmallRobotEntity robot = (SmallRobotEntity) GarrisonRegistry.SMALL_ROBOT_ITEM.get().place(helper.getLevel(), owner,
                        new ItemStack(GarrisonRegistry.SMALL_ROBOT_ITEM.get()), at, 0.0f);
                robot.load(new ItemStack(WeaponRegistry.ASSAULT_RIFLE_AMMO.get(), 64));
                robot.load(new ItemStack(WeaponRegistry.ASSAULT_RIFLE_AMMO.get(), 64));
                machine = robot;
            } else {
                ItemStack core = new ItemStack(GarrisonRegistry.AI_CORE.get());
                AiCoreItem.bind(core, owner);
                ItemStack body = new ItemStack(BodyItems.item(BodyClass.ADVANCED_SYNTHETIC));
                body.set(GarrisonRegistry.ANDROID_PARTS.get(), new GarrisonRegistry.AndroidParts(core, new ItemStack(GarrisonRegistry.COMBAT_MODULE.get())));
                AndroidEntity android = Androids.place(helper.getLevel(), owner, body, BodyClass.ADVANCED_SYNTHETIC, at, 0.0f);
                if (i % 2 == 0) {
                    android.gear().setItem(0, new ItemStack(Items.IRON_SWORD));
                    android.gear().setItem(AndroidEntity.OFFHAND, new ItemStack(Items.SHIELD));
                } else {
                    android.gear().setItem(0, new ItemStack(Items.BOW));
                    android.gear().setItem(5, new ItemStack(Items.ARROW, 64));
                }
                machine = android;
            }
            helper.assertTrue(machine != null, "Machine " + i + " stands");
            ((com.projectrebirth.garrison.GarrisonMachine) machine).machineCore().setTask(MachineTask.at(MachineTask.Type.GUARD, middle));
            machines.add(machine);
        }
        List<Mob> mutants = new ArrayList<>();
        int[] fallen = new int[1];
        MinecraftServer server = helper.getLevel().getServer();
        helper.onEachTick(() -> {
            mutants.removeIf(m -> {
                if (!m.isAlive()) {
                    fallen[0]++;
                    return true;
                }
                return false;
            });
            long tick = helper.getLevel().getGameTime();
            if (mutants.size() < MUTANTS && tick % 10 == 0) {
                int side = helper.getLevel().random.nextInt(4);
                int along = 2 + helper.getLevel().random.nextInt(44);
                BlockPos at = switch (side) {
                    case 0 -> new BlockPos(along, 2, 1);
                    case 1 -> new BlockPos(along, 2, 46);
                    case 2 -> new BlockPos(1, 2, along);
                    default -> new BlockPos(46, 2, along);
                };
                EntityType<? extends Mob> type = switch (helper.getLevel().random.nextInt(3)) {
                    case 0 -> HostileRegistry.MUTATED_HOUND.get();
                    case 1 -> HostileRegistry.GLOWING_GHOUL.get();
                    default -> HostileRegistry.BRUTE.get();
                };
                Mob mutant = helper.spawn(type, at);
                mutant.setPersistenceRequired();
                mutants.add(mutant);
            }
            if (tick % 20 == 0) {
                // the machines stay in the fight: their bars, rounds and charge are filled
                for (Entity e : machines) {
                    var core = ((com.projectrebirth.garrison.GarrisonMachine) e).machineCore();
                    if (!core.wreck()) {
                        core.setIntegrity(core.maxStructural(), core.maxCritical());
                    }
                    if (e instanceof SmallRobotEntity robot && robot.rounds() < 32) {
                        robot.load(new ItemStack(WeaponRegistry.ASSAULT_RIFLE_AMMO.get(), 64));
                    }
                    if (e instanceof AndroidEntity android) {
                        android.setCharge(1.0e9f);
                    }
                }
            }
        });
        long[] startedAt = new long[1];
        long[] stoppedAt = new long[1];
        int[] fallenAtStart = new int[1];
        helper.runAfterDelay(START, () -> {
            startedAt[0] = System.nanoTime();
            fallenAtStart[0] = fallen[0];
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "spark profiler start --timeout 3600");
        });
        helper.runAfterDelay(START + TICKS, () -> {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "spark profiler stop --save-to-file");
            double seconds = (System.nanoTime() - startedAt[0]) / 1.0e9;
            long alive = machines.stream().filter(e -> e.isAlive() && !((com.projectrebirth.garrison.GarrisonMachine) e).machineCore().wreck()).count();
            ProjectRebirth.LOGGER.info("BENCH combat tactics={} : {} ticks in {} s = {} ms per tick, mutants fallen {}, machines standing {} of 20",
                    Tactics.on(), TICKS, String.format("%.1f", seconds), String.format("%.3f", seconds * 1000.0 / TICKS),
                    fallen[0] - fallenAtStart[0], alive);
            machines.forEach(Entity::discard);
            mutants.forEach(Entity::discard);
            stoppedAt[0] = System.nanoTime();
        });
        helper.succeedWhen(() -> helper.assertTrue(stoppedAt[0] != 0 && System.nanoTime() - stoppedAt[0] > 5_000_000_000L, "spark saves"));
    }
}
