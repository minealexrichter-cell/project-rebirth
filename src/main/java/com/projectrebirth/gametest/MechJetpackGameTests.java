package com.projectrebirth.gametest;

import java.util.UUID;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.garrison.GarrisonRegistry;
import com.projectrebirth.garrison.GuardMechEntity;
import com.projectrebirth.garrison.MachineTask;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 1.7.2, block C: the guard mech's stomp and jetpack. */
@GameTestHolder(PlanTests.GARRISON)
@PrefixGameTestTemplate(false)
public class MechJetpackGameTests {
    private static final String TEMPLATE = "floor_9x9";

    private static ServerPlayer player(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(id, "jet_test_" + id.toString().substring(0, 6)));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.setShiftKeyDown(false);
        return player;
    }

    /** A charged mech with no weapon nodes (it fights with its feet only, and needs no ammunition). */
    private static GuardMechEntity unarmed(GameTestHelper helper, ServerPlayer owner, BlockPos at) {
        ItemStack stack = new ItemStack(GarrisonRegistry.GUARD_MECH_ITEM.get());
        stack.set(GarrisonRegistry.MECH_LOADOUT.get(), new GuardMechEntity.Loadout(0, 0, GuardMechEntity.Loadout.FULL, 0, 0));
        GuardMechEntity mech = (GuardMechEntity) GarrisonRegistry.GUARD_MECH_ITEM.get().place(helper.getLevel(), owner, stack, helper.absolutePos(at), 0.0f);
        helper.assertTrue(mech != null, "The mech stands");
        return mech;
    }

    @GameTest(template = TEMPLATE)
    public static void theStompHurtsEnemiesButNotItsOwn(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        GuardMechEntity mech = unarmed(helper, owner, new BlockPos(4, 2, 4));
        GuardMechEntity friend = unarmed(helper, owner, new BlockPos(7, 2, 4));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 6));
        zombie.setNoAi(true);
        float zombieBefore = zombie.getHealth();
        float friendBefore = friend.machineCore().structural();
        mech.stomp();
        helper.assertTrue(zombie.getHealth() < zombieBefore || !zombie.isAlive(), "The zombie is hurt: " + zombie.getHealth());
        helper.assertTrue(friend.machineCore().structural() == friendBefore, "Its own mech is not");
        mech.discard();
        friend.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void aMonsterUpCloseIsStomped(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        GuardMechEntity mech = unarmed(helper, owner, new BlockPos(4, 2, 4));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 6));
        zombie.setNoAi(true);
        float before = zombie.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(!zombie.isAlive() || zombie.getHealth() < before, "It stomped the zombie next to it");
            mech.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500)
    public static void aMechHopsOverAWallItCannotWalkRound(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        for (int z = 0; z <= 8; z++) {
            for (int y = 2; y <= 4; y++) {
                helper.setBlock(new BlockPos(4, y, z), Blocks.STONE);
            }
        }
        GuardMechEntity mech = unarmed(helper, owner, new BlockPos(2, 2, 4));
        BlockPos point = helper.absolutePos(new BlockPos(7, 1, 4));
        mech.machineCore().setTask(MachineTask.at(MachineTask.Type.GUARD, point));
        boolean[] flew = new boolean[1];
        helper.onEachTick(() -> flew[0] |= mech.flying());
        helper.succeedWhen(() -> {
            helper.assertTrue(flew[0], "It took off");
            helper.assertTrue(!mech.flying() && mech.onGround() && mech.distanceToSqr(Vec3.atBottomCenterOf(point.above())) < 9.0,
                    "Over the wall and down at its point: " + mech.position());
            mech.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void aMechTakesOffOverATargetOutOfReach(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        for (int y = 2; y <= 6; y++) {
            helper.setBlock(new BlockPos(7, y, 7), Blocks.STONE);
        }
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(7, 7, 7));
        zombie.setNoAi(true);
        GuardMechEntity mech = unarmed(helper, owner, new BlockPos(2, 2, 2));
        helper.succeedWhen(() -> {
            helper.assertTrue(mech.flying(), "It flies up to the zombie on the pillar");
            mech.discard();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void noFlightWithoutEnergyToSpare(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        GuardMechEntity mech = unarmed(helper, owner, new BlockPos(4, 2, 4));
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(mech.jetpackReady(), "Charged on the ground: ready");
            mech.setEnergy(mech.capacity() * (float) (RebirthConfig.GUARD_MECH_LOW_ENERGY.get() + 0.01));
            helper.assertFalse(mech.jetpackReady(), "Just over its low mark: it does not take off");
            mech.discard();
            helper.succeed();
        });
    }
}
