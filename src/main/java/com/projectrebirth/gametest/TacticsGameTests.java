package com.projectrebirth.gametest;

import java.util.List;
import java.util.UUID;

import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyItems;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.garrison.AiCoreItem;
import com.projectrebirth.garrison.AndroidEntity;
import com.projectrebirth.garrison.Androids;
import com.projectrebirth.garrison.DefenceTurretEntity;
import com.projectrebirth.garrison.GarrisonRegistry;
import com.projectrebirth.garrison.GuardMechEntity;
import com.projectrebirth.garrison.MachineTask;
import com.projectrebirth.garrison.PackedMachineItem;
import com.projectrebirth.garrison.SmallRobotEntity;
import com.projectrebirth.garrison.TestMachineEntity;
import com.projectrebirth.garrison.TurretMountBlockEntity;
import com.projectrebirth.garrison.tactics.Tactics;
import com.projectrebirth.hostile.MechRocketEntity;
import com.projectrebirth.weapons.WeaponRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.8.0, block I: the tactics of the combat machines. The floor (floor_24x24) is stone six rows deep: the ground is y 6,
 * machines and monsters stand at y 7.
 */
@GameTestHolder(PlanTests.INTERFACE)
@PrefixGameTestTemplate(false)
public class TacticsGameTests {
    private static final String TEMPLATE = "floor_24x24";
    private static final int GROUND = 6;
    private static final int STAND = 7;

    private static ServerPlayer player(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(id, "tac_test_" + id.toString().substring(0, 6)));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        return player;
    }

    private static AndroidEntity android(GameTestHelper helper, ServerPlayer owner, BodyClass bodyClass, int x, int z) {
        ItemStack core = new ItemStack(GarrisonRegistry.AI_CORE.get());
        AiCoreItem.bind(core, owner);
        ItemStack stack = new ItemStack(BodyItems.item(bodyClass));
        stack.set(GarrisonRegistry.ANDROID_PARTS.get(), new GarrisonRegistry.AndroidParts(core, new ItemStack(GarrisonRegistry.COMBAT_MODULE.get())));
        AndroidEntity android = Androids.place(helper.getLevel(), owner, stack, bodyClass, helper.absolutePos(new BlockPos(x, STAND, z)), 0.0f);
        helper.assertTrue(android != null, "The android stands");
        return android;
    }

    private static SmallRobotEntity robot(GameTestHelper helper, ServerPlayer owner, int x, int z) {
        SmallRobotEntity robot = (SmallRobotEntity) GarrisonRegistry.SMALL_ROBOT_ITEM.get().place(helper.getLevel(), owner,
                new ItemStack(GarrisonRegistry.SMALL_ROBOT_ITEM.get()), helper.absolutePos(new BlockPos(x, STAND, z)), 0.0f);
        helper.assertTrue(robot != null, "The robot stands");
        robot.load(new ItemStack(WeaponRegistry.ASSAULT_RIFLE_AMMO.get(), 64));
        robot.load(new ItemStack(WeaponRegistry.ASSAULT_RIFLE_AMMO.get(), 64));
        return robot;
    }

    /** A zombie that does not burn in the sun; with noAi it stands still (a target). */
    private static Zombie zombie(GameTestHelper helper, int x, int z, boolean noAi) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(x, STAND, z));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        zombie.setPersistenceRequired();
        zombie.setNoAi(noAi);
        return zombie;
    }

    /** A machine of the owner that only stands (one of its own, for the line of fire and the rockets). */
    private static TestMachineEntity friend(GameTestHelper helper, ServerPlayer owner, Vec3 at) {
        PackedMachineItem item = GarrisonRegistry.TEST_MACHINE_ITEM.get();
        TestMachineEntity machine = (TestMachineEntity) item.place(helper.getLevel(), owner, new ItemStack(item), BlockPos.containing(at), 0.0f);
        helper.assertTrue(machine != null, "The friend stands");
        machine.setNoAi(true);
        machine.moveTo(at.x, at.y, at.z);
        return machine;
    }

    private static double flat(Vec3 a, Vec3 b) {
        return a.subtract(b).horizontalDistance();
    }

    // ------------------------------------------------------------------ the combat android

    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void anAndroidRaisesItsShieldAndLowersItToStrike(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        AndroidEntity android = android(helper, owner, BodyClass.PREMIUM_SYNTHETIC, 8, 12);
        android.gear().setItem(0, new ItemStack(Items.IRON_SWORD));
        android.gear().setItem(AndroidEntity.OFFHAND, new ItemStack(Items.SHIELD));
        Zombie zombie = zombie(helper, 12, 12, false);
        zombie.setTarget(android);
        boolean[] raised = new boolean[1];
        helper.onEachTick(() -> {
            if (android.isUsingItem() && android.getUseItem().is(Items.SHIELD)) {
                raised[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(raised[0], "The shield went up");
            helper.assertTrue(zombie.getLastHurtByMob() == android, "And it struck (the shield down)");
            android.discard();
            zombie.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void anAndroidWithABowStepsBackFromAZombie(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        AndroidEntity android = android(helper, owner, BodyClass.PREMIUM_SYNTHETIC, 12, 12);
        android.gear().setItem(0, new ItemStack(Items.BOW));
        android.gear().setItem(5, new ItemStack(Items.ARROW, 64));
        Zombie zombie = zombie(helper, 16, 12, false);
        zombie.setTarget(android);
        double startX = android.getX();
        helper.succeedWhen(() -> {
            helper.assertTrue(android.getX() < startX - 2.0, "It stepped back from the zombie: " + (android.getX() - startX));
            helper.assertTrue(android.gear().getItem(5).getCount() < 64, "And shot");
            android.discard();
            zombie.discard();
        });
    }

    // ------------------------------------------------------------------ the small robot

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void aSmallRobotCirclesItsTarget(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        Zombie target = zombie(helper, 12, 12, true);
        SmallRobotEntity robot = robot(helper, owner, 12, 4);
        double start = Tactics.angleFrom(target, robot.position());
        double[] turned = new double[1];
        helper.onEachTick(() -> turned[0] = Math.max(turned[0],
                Math.abs(net.minecraft.util.Mth.wrapDegrees((Tactics.angleFrom(target, robot.position()) - start) * net.minecraft.util.Mth.RAD_TO_DEG))));
        helper.succeedWhen(() -> {
            helper.assertTrue(turned[0] > 40.0, "It went round the target: " + turned[0] + " degrees");
            helper.assertTrue(robot.distanceTo(target) > 3.0, "At a distance: " + robot.distanceTo(target));
            robot.discard();
            target.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void aSmallRobotBacksAwayFromAZombieNotIntoThePitBehindIt(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        // a pit five deep right behind it (west), across the floor
        for (int x = 5; x <= 8; x++) {
            for (int z = 0; z < 24; z++) {
                for (int y = 2; y <= GROUND; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        SmallRobotEntity robot = robot(helper, owner, 10, 12);
        Zombie zombie = zombie(helper, 14, 12, false);
        zombie.setTarget(robot);
        double floorY = helper.absolutePos(new BlockPos(0, STAND, 0)).getY();
        Vec3 start = robot.position();
        double[] moved = new double[1];
        helper.onEachTick(() -> {
            helper.assertTrue(robot.getY() > floorY - 0.5, "It never falls into the pit: " + robot.position());
            moved[0] = Math.max(moved[0], flat(robot.position(), start));
        });
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(moved[0] > 2.0, "It moved off the zombie: " + moved[0]);
            robot.discard();
            zombie.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void aRobotHoldsFireWhileOneOfItsOwnIsOnTheLine(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        Zombie target = zombie(helper, 18, 12, true);
        SmallRobotEntity robot = robot(helper, owner, 6, 12);
        // its own machine between them
        friend(helper, owner, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(12, STAND, 12))));
        int full = robot.rounds();
        int[] last = {full};
        helper.onEachTick(() -> {
            int now = robot.rounds();
            if (now < last[0]) {
                helper.assertTrue(Tactics.clearLine(robot, robot, robot.muzzle(GuardMechEntity.RIGHT), target),
                        "A shot only with the line clear: " + robot.position());
            }
            last[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(robot.rounds() < full, "It stepped aside and fired");
            robot.discard();
            target.discard();
        });
    }

    // ------------------------------------------------------------------ the mech

    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void aMechFiresNoRocketNearItsOwn(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        ItemStack stack = new ItemStack(GarrisonRegistry.GUARD_MECH_ITEM.get());
        stack.set(GarrisonRegistry.MECH_LOADOUT.get(), new GuardMechEntity.Loadout(GuardMechEntity.Node.NONE.ordinal(), GuardMechEntity.Node.ROCKET.ordinal(),
                GuardMechEntity.Loadout.FULL, 0, RebirthConfig.GUARD_MECH_ROCKETS.get()));
        GuardMechEntity mech = (GuardMechEntity) GarrisonRegistry.GUARD_MECH_ITEM.get().place(helper.getLevel(), owner, stack,
                helper.absolutePos(new BlockPos(4, STAND, 12)), 0.0f);
        helper.assertTrue(mech != null, "The mech stands");
        // two zombies together (a group: worth a rocket), one of its own beside them
        Zombie a = zombie(helper, 18, 12, true);
        Zombie b = zombie(helper, 18, 14, true);
        TestMachineEntity own = friend(helper, owner, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(19, STAND, 13))));
        helper.assertTrue(mech.rocketWorthy(a), "Two together are worth a rocket");
        AABB plot = new AABB(helper.absolutePos(new BlockPos(0, 0, 0))).inflate(40);
        helper.onEachTick(() -> {
            if (own.isAlive()) {
                helper.assertTrue(helper.getLevel().getEntitiesOfClass(MechRocketEntity.class, plot).isEmpty(), "No rocket while its own is there");
            }
        });
        helper.runAfterDelay(160, own::discard);
        helper.succeedWhen(() -> {
            helper.assertTrue(!own.isAlive() && !helper.getLevel().getEntitiesOfClass(MechRocketEntity.class, plot).isEmpty(),
                    "With its own gone, a rocket goes: target " + mech.getTarget() + ", rockets " + mech.rockets() + ", at " + mech.position()
                            + ", distance " + (mech.getTarget() == null ? -1 : mech.distanceTo(mech.getTarget())) + ", worthy " + mech.rocketWorthy(a)
                            + ", friend near " + mech.friendNear(a) + ", energy " + mech.energy());
            mech.discard();
            a.discard();
            b.discard();
        });
    }

    // ------------------------------------------------------------------ all of them

    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void aBadlyHurtRobotFallsBackAndComesBackRepaired(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        Zombie target = zombie(helper, 18, 12, true);
        SmallRobotEntity robot = robot(helper, owner, 10, 12);
        var core = robot.machineCore();
        core.setIntegrity(core.maxStructural() * 0.2f, core.maxCritical());
        double start = robot.distanceTo(target);
        double[] farthest = new double[1];
        helper.onEachTick(() -> farthest[0] = Math.max(farthest[0], robot.distanceTo(target)));
        helper.runAfterDelay(140, () -> {
            helper.assertTrue(robot.tactics().retreating, "Below 30 % it falls back");
            helper.assertTrue(farthest[0] > start + 3.0, "Away from the fight: " + start + " -> " + farthest[0]);
            core.repairStructural(core.maxStructural());
        });
        int[] roundsAt = new int[1];
        helper.runAfterDelay(141, () -> roundsAt[0] = robot.rounds());
        helper.runAfterDelay(160, () -> helper.succeedWhen(() -> {
            helper.assertFalse(robot.tactics().retreating, "Repaired, it stops falling back");
            helper.assertTrue(robot.rounds() < roundsAt[0], "And fights again");
            robot.discard();
            target.discard();
        }));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 700)
    public static void aGuardingRobotDoesNotChasePastItsAreaAndComesBack(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        BlockPos point = helper.absolutePos(new BlockPos(4, GROUND, 12));
        SmallRobotEntity robot = robot(helper, owner, 4, 12);
        robot.machineCore().setTask(MachineTask.at(MachineTask.Type.GUARD, point));
        double radius = RebirthConfig.SMALL_ROBOT_GUARD_RADIUS.get();
        // a zombie that runs off to the far side of the plot (after a villager-like lure: a target far away)
        Zombie zombie = zombie(helper, 10, 12, false);
        robot.hurt(robot.damageSources().mobAttack(zombie), 1.0f);
        Vec3 centre = Vec3.atBottomCenterOf(point.above());
        double[] farthest = new double[1];
        helper.onEachTick(() -> {
            farthest[0] = Math.max(farthest[0], flat(robot.position(), centre));
            if (zombie.isAlive()) {
                zombie.getNavigation().moveTo(helper.absolutePos(new BlockPos(23, STAND, 12)).getX(), zombie.getY(), zombie.getZ(), 1.4);
            }
        });
        helper.runAfterDelay(300, zombie::discard);
        helper.runAfterDelay(320, () -> helper.succeedWhen(() -> {
            helper.assertTrue(farthest[0] <= radius + 2.0, "Never past its area: " + farthest[0] + " of " + radius);
            helper.assertTrue(flat(robot.position(), centre) < 3.5, "Back on its point: " + flat(robot.position(), centre));
            robot.discard();
        }));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void aMachineCallsItsGroupToItsTarget(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        // B stands behind a wall: it does not see the zombie, A does
        for (int z = 0; z < 24; z++) {
            for (int y = STAND; y < STAND + 4; y++) {
                helper.setBlock(new BlockPos(6, y, z), Blocks.STONE);
            }
        }
        Zombie zombie = zombie(helper, 16, 12, true);
        SmallRobotEntity a = robot(helper, owner, 10, 12);
        SmallRobotEntity b = robot(helper, owner, 3, 12);
        helper.succeedWhen(() -> {
            helper.assertTrue(b.getTarget() == zombie, "The other one came to the same target: " + b.getTarget());
            a.discard();
            b.discard();
            zombie.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void anAndroidRunsFromAHissingCreeper(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        AndroidEntity android = android(helper, owner, BodyClass.ADVANCED_SYNTHETIC, 12, 12);
        android.gear().setItem(0, new ItemStack(Items.IRON_SWORD));
        Creeper creeper = helper.spawn(EntityType.CREEPER, new BlockPos(14, STAND, 12));
        // a long fuse and no blast: the test watches where the android goes
        CompoundTag tag = new CompoundTag();
        creeper.addAdditionalSaveData(tag);
        tag.putShort("Fuse", (short) 200);
        tag.putByte("ExplosionRadius", (byte) 0);
        creeper.readAdditionalSaveData(tag);
        creeper.setNoAi(true);
        creeper.ignite();
        double radius = RebirthConfig.TACTICS_FLEE_RADIUS.get();
        helper.succeedWhen(() -> {
            helper.assertTrue(android.distanceTo(creeper) > radius + 1.0, "Out of the blast: " + android.distanceTo(creeper));
            android.discard();
            creeper.discard();
        });
    }

    // ------------------------------------------------------------------ the turrets

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void aRocketTurretAimsAheadOfARunningTarget(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        BlockPos at = new BlockPos(4, GROUND, 4);
        helper.setBlock(at, GarrisonRegistry.TURRET_MOUNT.get());
        TurretMountBlockEntity mount = (TurretMountBlockEntity) helper.getBlockEntity(at);
        mount.energy().generate(RebirthConfig.TURRET_MOUNT_CAPACITY.get());
        mount.ammo().insertItem(0, new ItemStack(GarrisonRegistry.ROCKET.get(), 8), false);
        DefenceTurretEntity turret = (DefenceTurretEntity) GarrisonRegistry.ROCKET_TURRET_ITEM.get().place(helper.getLevel(), owner,
                new ItemStack(GarrisonRegistry.ROCKET_TURRET_ITEM.get()), helper.absolutePos(at.above()), 0.0f);
        helper.assertTrue(turret != null, "The turret stands");
        Zombie target = zombie(helper, 18, 4, true);
        helper.runAfterDelay(4, () -> {
            // it runs along z: 0.3 blocks since the last tick
            target.xo = target.getX();
            target.yo = target.getY();
            target.zo = target.getZ();
            target.setPos(target.getX(), target.getY(), target.getZ() + 0.3);
            double z = target.getZ();
            helper.assertTrue(turret.launch(target), "A rocket goes");
            List<MechRocketEntity> rockets = helper.getLevel().getEntitiesOfClass(MechRocketEntity.class, new AABB(turret.blockPosition()).inflate(8));
            helper.assertTrue(rockets.size() == 1, "One rocket: " + rockets.size());
            double ahead = rockets.get(0).end().z - z;
            helper.assertTrue(ahead > 2.0, "It comes down ahead of the target: " + ahead + " blocks");
            rockets.forEach(MechRocketEntity::discard);
            turret.discard();
            target.discard();
            helper.succeed();
        });
    }
}
