package com.projectrebirth.gametest;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.hostile.GuardTurretEntity;
import com.projectrebirth.hostile.HostileRegistry;
import com.projectrebirth.structures.StructureBlocks;
import com.projectrebirth.structures.TurretBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * The guard turret of the ruins (1.6.0, part B3a): it shoots a player it can see, not one behind a wall nor one in creative;
 * destroyed, it leaves the broken turret's blocks and drops scrap; switched off in the config it turns into the broken turret.
 */
@GameTestHolder(PlanTests.SCOUTING)
@PrefixGameTestTemplate(false)
public class GuardTurretGameTests {
    private static final String TEMPLATE = "floor_9x9";
    private static final BlockPos TURRET = new BlockPos(4, 2, 1);

    /** A turret at the north end of the floor, looking south, toward the player. */
    private static GuardTurretEntity turret(GameTestHelper helper) {
        GuardTurretEntity turret = helper.spawn(HostileRegistry.GUARD_TURRET.get(), TURRET);
        turret.setYRot(0.0f);
        turret.setYHeadRot(0.0f);
        turret.yBodyRot = 0.0f;
        return turret;
    }

    /**
     * A player in the level whose game mode is real: the game test's own mock player always counts as creative, and a turret
     * rightly leaves such a player alone. Made the same way as the mock player, without that.
     */
    static ServerPlayer player(GameTestHelper helper, GameType mode) {
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-turret-target"), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(mode);
        player.getInventory().clearContent();
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 2.0, 7.5));
        player.moveTo(at.x, at.y, at.z, 180.0f, 0.0f);
        return player;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = "scouting_turret_shoot")
    public static void aTurretShootsAPlayerItSees(GameTestHelper helper) {
        GuardTurretEntity turret = turret(helper);
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        float before = player.getHealth();
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(turret.isAlive(), "The turret stands");
            helper.assertTrue(player.getHealth() < before, "The player in its sight was hit: " + before + " -> " + player.getHealth());
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = "scouting_turret_wall")
    public static void aWallKeepsThePlayerOutOfSight(GameTestHelper helper) {
        GuardTurretEntity turret = turret(helper);
        for (int x = 0; x <= 8; x++) {
            for (int y = 2; y <= 5; y++) {
                helper.setBlock(new BlockPos(x, y, 4), Blocks.STONE);
            }
        }
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        float before = player.getHealth();
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(player.getHealth() >= before, "Behind a wall nothing reaches the player");
            helper.assertTrue(turret.getTarget() == null || !turret.hasLineOfSight(player), "The turret cannot see through the wall");
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = "scouting_turret_creative")
    public static void aTurretLeavesACreativePlayerAlone(GameTestHelper helper) {
        turret(helper);
        ServerPlayer player = player(helper, GameType.CREATIVE);
        float before = player.getHealth();
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(player.getHealth() >= before, "A player in creative is not shot");
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "scouting_turret_dead")
    public static void aDestroyedTurretLeavesTheBrokenTurretAndScrap(GameTestHelper helper) {
        GuardTurretEntity turret = turret(helper);
        turret.kill();
        helper.runAfterDelay(2, () -> {
            BlockPos base = TURRET;
            helper.assertTrue(helper.getBlockState(base).is(StructureBlocks.WRECKED_TURRET.get())
                    && helper.getBlockState(base).getValue(TurretBlock.PART) == TurretBlock.Part.BASE, "The base of the broken turret is where it stood");
            helper.assertTrue(helper.getBlockState(base.above()).is(StructureBlocks.WRECKED_TURRET.get()), "And its tower");
            helper.assertTrue(helper.getBlockState(base.above().relative(Direction.SOUTH)).is(StructureBlocks.WRECKED_TURRET.get()),
                    "And its barrel, on the side it looked to");
            AABB around = new AABB(helper.absolutePos(base)).inflate(3.0);
            helper.assertTrue(!helper.getLevel().getEntitiesOfClass(ItemEntity.class, around).isEmpty(), "It drops scrap");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "scouting_turret_off")
    public static void switchedOffATurretIsJustBroken(GameTestHelper helper) {
        boolean before = RebirthConfig.TURRET_ENABLED.get();
        RebirthConfig.TURRET_ENABLED.set(false);
        GuardTurretEntity turret = turret(helper);
        helper.runAfterDelay(5, () -> {
            try {
                helper.assertFalse(turret.isAlive(), "Switched off, the turret is gone");
                helper.assertTrue(helper.getBlockState(TURRET).is(StructureBlocks.WRECKED_TURRET.get()), "And the broken turret stands there");
                AABB around = new AABB(helper.absolutePos(TURRET)).inflate(3.0);
                helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, around).isEmpty(), "Without loot");
            } finally {
                RebirthConfig.TURRET_ENABLED.set(before);
            }
            helper.succeed();
        });
    }
}
