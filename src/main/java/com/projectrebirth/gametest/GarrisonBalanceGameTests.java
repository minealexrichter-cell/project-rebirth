package com.projectrebirth.gametest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyItems;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.garrison.AiCoreItem;
import com.projectrebirth.garrison.AndroidEntity;
import com.projectrebirth.garrison.Androids;
import com.projectrebirth.garrison.ConsoleAlarm;
import com.projectrebirth.garrison.DefenceTurretEntity;
import com.projectrebirth.garrison.GarrisonMachine;
import com.projectrebirth.garrison.GarrisonRegistry;
import com.projectrebirth.garrison.GuardMechEntity;
import com.projectrebirth.garrison.MachineCore;
import com.projectrebirth.garrison.MachineTask;
import com.projectrebirth.garrison.TurretItem;
import com.projectrebirth.garrison.TurretMountBlockEntity;
import com.projectrebirth.hostile.HostileRegistry;
import com.projectrebirth.weapons.WeaponRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;

/**
 * The balance of the garrison (1.7.0, block J), measured: every machine (an android of each body class with an iron sword, both
 * turrets, the guard mech) against each enemy of the ruins (hound, ghoul, brute, patrol robot, mech), the miner of each body class
 * on the same quarry, and the server's tick time with twenty machines fighting. Each test logs a line "BALANCE ..." that the
 * tables in docs/STATUS.md are made of; it passes when the fight or the work comes to an end (or the time is up), so a change of
 * the numbers never breaks the build, but a machine that never ends its work does.
 */
@GameTestHolder(PlanTests.GARRISON)
public class GarrisonBalanceGameTests {
    private static final String TEMPLATE = PlanTests.GARRISON + ":floor_9x9";
    private static final int FIGHT_TICKS = 2400;

    private static ServerPlayer player(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(id, "balance_" + id.toString().substring(0, 6)));
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static AndroidEntity android(GameTestHelper helper, BodyClass bodyClass, boolean worker, BlockPos at) {
        ServerPlayer owner = player(helper);
        ItemStack core = new ItemStack(GarrisonRegistry.AI_CORE.get());
        AiCoreItem.bind(core, owner);
        ItemStack stack = new ItemStack(BodyItems.item(bodyClass));
        stack.set(GarrisonRegistry.ANDROID_PARTS.get(), new GarrisonRegistry.AndroidParts(core,
                new ItemStack(worker ? GarrisonRegistry.WORK_MODULE.get() : GarrisonRegistry.COMBAT_MODULE.get())));
        return Androids.place(helper.getLevel(), owner, stack, bodyClass, helper.absolutePos(at), 0.0f);
    }

    private static DefenceTurretEntity turret(GameTestHelper helper, TurretItem item, BlockPos mountAt) {
        helper.setBlock(mountAt, GarrisonRegistry.TURRET_MOUNT.get());
        TurretMountBlockEntity mount = (TurretMountBlockEntity) helper.getBlockEntity(mountAt);
        mount.energy().generate(RebirthConfig.TURRET_MOUNT_CAPACITY.get());
        mount.ammo().insertItem(0, new ItemStack(item.turret().ammo(), 64), false);
        mount.ammo().insertItem(1, new ItemStack(item.turret().ammo(), 64), false);
        return (DefenceTurretEntity) item.place(helper.getLevel(), player(helper), new ItemStack(item), helper.absolutePos(mountAt.above()), 0.0f);
    }

    private static GuardMechEntity mech(GameTestHelper helper, BlockPos at) {
        ItemStack stack = new ItemStack(GarrisonRegistry.GUARD_MECH_ITEM.get());
        stack.set(GarrisonRegistry.MECH_LOADOUT.get(), new GuardMechEntity.Loadout(1, 2, 60000, 300, 16));
        return (GuardMechEntity) GarrisonRegistry.GUARD_MECH_ITEM.get().place(helper.getLevel(), player(helper), stack, helper.absolutePos(at), 0.0f);
    }

    private record Enemy(String name, EntityType<? extends Mob> type) {
    }

    private static List<Enemy> enemies() {
        return List.of(new Enemy("hound", HostileRegistry.MUTATED_HOUND.get()), new Enemy("ghoul", HostileRegistry.GLOWING_GHOUL.get()),
                new Enemy("brute", HostileRegistry.BRUTE.get()), new Enemy("patrol_robot", HostileRegistry.PATROL_ROBOT.get()),
                new Enemy("enemy_mech", HostileRegistry.MECH.get()));
    }

    private static TestFunction test(String batch, String name, int ticks, Consumer<GameTestHelper> body) {
        return new TestFunction(batch, "garrisonbalance." + name, TEMPLATE, ticks, 0L, true, body);
    }

    /** One fight to the end: the enemy dead, the machine down, or the time up. */
    private static void fight(GameTestHelper helper, String label, Entity machine, Mob enemy) {
        MachineCore core = ((GarrisonMachine) machine).machineCore();
        enemy.setPersistenceRequired();
        long start = helper.getTick();
        float enemyMax = enemy.getMaxHealth();
        helper.onEachTick(() -> {
            long ticks = helper.getTick() - start;
            boolean won = !enemy.isAlive();
            boolean lost = !core.active();
            if (won || lost || ticks >= FIGHT_TICKS - 20) {
                ProjectRebirth.LOGGER.info(String.format(Locale.ROOT, "BALANCE fight %s: %s after %.1f s; machine %.0f/%.0f + %.0f/%.0f; enemy %.0f/%.0f",
                        label, won ? "WON" : lost ? "LOST" : "TIME", ticks / 20.0, core.structural(), core.maxStructural(), core.critical(),
                        core.maxCritical(), Math.max(0, enemy.getHealth()), enemyMax));
                machine.discard();
                enemy.discard();
                helper.succeed();
            }
        });
    }

    @GameTestGenerator
    public static Collection<TestFunction> fights() {
        List<TestFunction> tests = new ArrayList<>();
        List<BiFunction<GameTestHelper, String, Entity>> machines = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (BodyClass bodyClass : BodyClass.values()) {
            names.add("android_" + bodyClass.id());
            machines.add((helper, unused) -> {
                AndroidEntity android = android(helper, bodyClass, false, new BlockPos(1, 2, 4));
                android.gear().setItem(0, new ItemStack(Items.IRON_SWORD));
                android.gear().setItem(AndroidEntity.OFFHAND, new ItemStack(Items.SHIELD));
                return android;
            });
        }
        names.add("machine_gun_turret");
        machines.add((helper, unused) -> turret(helper, GarrisonRegistry.MACHINE_GUN_TURRET_ITEM.get(), new BlockPos(0, 1, 4)));
        names.add("rocket_turret");
        machines.add((helper, unused) -> turret(helper, GarrisonRegistry.ROCKET_TURRET_ITEM.get(), new BlockPos(0, 1, 4)));
        names.add("guard_mech");
        machines.add((helper, unused) -> mech(helper, new BlockPos(1, 2, 4)));
        for (int m = 0; m < machines.size(); m++) {
            BiFunction<GameTestHelper, String, Entity> make = machines.get(m);
            String machineName = names.get(m);
            for (Enemy enemy : enemies()) {
                tests.add(test("garrison_balance", machineName + "_vs_" + enemy.name(), FIGHT_TICKS, helper -> {
                    Entity machine = make.apply(helper, enemy.name());
                    helper.assertTrue(machine != null, "The machine stands");
                    // the rocket turret does not shoot closer than its minimum range: the enemy comes from the far corner
                    Mob foe = helper.spawn(enemy.type(), new BlockPos(8, 2, machineName.startsWith("rocket") ? 8 : 4));
                    fight(helper, machineName + " vs " + enemy.name(), machine, foe);
                }));
            }
        }
        // the miner of each class on the same quarry: 5 by 5 by 3 of stone, an iron pickaxe
        for (BodyClass bodyClass : BodyClass.values()) {
            tests.add(test("garrison_balance", "miner_" + bodyClass.id(), 6000, helper -> {
                for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(2, 2, 2), new BlockPos(6, 4, 6))) {
                    helper.setBlock(pos, Blocks.STONE);
                }
                helper.setBlock(new BlockPos(8, 2, 8), Blocks.CHEST);
                AndroidEntity miner = android(helper, bodyClass, true, new BlockPos(1, 2, 1));
                miner.gear().setItem(0, new ItemStack(Items.IRON_PICKAXE));
                miner.machineCore().setTask(MachineTask.work("mine", helper.absolutePos(new BlockPos(2, 2, 2)), helper.absolutePos(new BlockPos(6, 4, 6)),
                        helper.absolutePos(new BlockPos(8, 2, 8))));
                long start = helper.getTick();
                helper.onEachTick(() -> {
                    if (miner.workReported(ConsoleAlarm.WORK_DONE)) {
                        int left = 0;
                        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(2, 2, 2), new BlockPos(6, 4, 6))) {
                            left += helper.getBlockState(pos).isAir() ? 0 : 1;
                        }
                        double seconds = (helper.getTick() - start) / 20.0;
                        int mined = 75 - left;
                        ProjectRebirth.LOGGER.info(String.format(Locale.ROOT, "BALANCE miner %s: %d blocks in %.1f s = %.0f blocks per hour",
                                bodyClass.id(), mined, seconds, mined / seconds * 3600.0));
                        miner.discard();
                        helper.succeed();
                    }
                });
            }));
        }
        // the load: twenty machines fighting twenty zombies at once (alone in their batch, so the tick time is theirs)
        tests.add(test("garrison_load_idle", "load_idle", 300, helper -> helper.runAfterDelay(260, () -> {
            ProjectRebirth.LOGGER.info(String.format(Locale.ROOT, "BALANCE load idle: %.2f ms per tick",
                    helper.getLevel().getServer().getAverageTickTimeNanos() / 1.0e6));
            helper.succeed();
        })));
        tests.add(test("garrison_load_fight", "load_twenty_machines", 400, helper -> {
            List<Entity> all = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                AndroidEntity a = android(helper, BodyClass.values()[i % 5], false, new BlockPos(1 + (i % 4) * 2, 2, 1 + i / 4 * 2));
                if (a != null) {
                    a.gear().setItem(0, new ItemStack(Items.IRON_SWORD));
                    all.add(a);
                }
            }
            for (int i = 0; i < 8; i++) {
                DefenceTurretEntity t = turret(helper, GarrisonRegistry.MACHINE_GUN_TURRET_ITEM.get(), new BlockPos(i < 4 ? 0 : 8, 1, 1 + (i % 4) * 2));
                if (t != null) {
                    all.add(t);
                }
            }
            for (int i = 0; i < 20; i++) {
                Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1 + i % 7, 2, 7 + (i / 7) % 2));
                zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                all.add(zombie);
            }
            int count = (int) all.stream().filter(e -> e instanceof GarrisonMachine).count();
            helper.runAfterDelay(300, () -> {
                long zombies = all.stream().filter(e -> e instanceof Zombie && e.isAlive()).count();
                ProjectRebirth.LOGGER.info(String.format(Locale.ROOT, "BALANCE load %d machines vs 20 zombies: %.2f ms per tick, %d zombies left after 15 s",
                        count, helper.getLevel().getServer().getAverageTickTimeNanos() / 1.0e6, zombies));
                all.forEach(Entity::discard);
                helper.succeed();
            });
        }));
        return tests;
    }
}
