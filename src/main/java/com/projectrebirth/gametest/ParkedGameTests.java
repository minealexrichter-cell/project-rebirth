package com.projectrebirth.gametest;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyEntities;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.powerarmor.PowerArmorRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Checks that bodies and armors nobody controls stand like solid objects: nothing pushes or turns them, players
 * and mobs collide with them, but they still fall. Run with {@code .\gradlew runGameTestServer}.
 */
@GameTestHolder(ProjectRebirth.MOD_ID)
@PrefixGameTestTemplate(false)
public class ParkedGameTests {
    // 5x5x5 with a stone floor at y = 1.
    private static final String TEMPLATE = "floor_5x5";
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    private static ArtificialBodyEntity spawnBody(GameTestHelper helper) {
        return helper.spawn(BodyEntities.type(BodyClass.BASIC_ROBOT), POS);
    }

    private static PowerArmorEntity spawnArmor(GameTestHelper helper) {
        return helper.spawn(PowerArmorRegistry.POWER_ARMOR.get(), POS);
    }

    /** Fails unless the entity is still exactly where it was. */
    private static void assertStayed(GameTestHelper helper, LivingEntity entity, Vec3 start, String what) {
        helper.assertTrue(entity.position().distanceTo(start) < 0.001,
                what + ": expected to stay at " + start + ", but is at " + entity.position());
    }

    /** Throws the entity around the way knockback, explosions and fishing rods do. */
    private static void shove(LivingEntity entity) {
        entity.knockback(2.0, 1.0, 0.5);
        entity.setDeltaMovement(entity.getDeltaMovement().add(1.5, 0.8, -1.2));
        entity.push(0.7, 0.0, 0.7);
        entity.hurtMarked = true;
    }

    @GameTest(template = TEMPLATE)
    public static void bodyIsSolidAndCannotBeShovedOrTurned(GameTestHelper helper) {
        ArtificialBodyEntity body = spawnBody(helper);
        helper.assertTrue(body.canBeCollidedWith(), "A body must be solid");
        helper.assertFalse(body.isPushable(), "A body must not be pushable");
        helper.runAfterDelay(5, () -> {
            Vec3 start = body.position();
            body.setYRot(90.0f);
            shove(body);
            body.yHeadRot = 0.0f;
            helper.runAfterDelay(15, () -> {
                assertStayed(helper, body, start, "Shoved body");
                helper.assertTrue(body.yHeadRot == 90.0f && body.yBodyRot == 90.0f,
                        "A body must keep facing its own direction, head " + body.yHeadRot + ", body " + body.yBodyRot);
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE)
    public static void mobsBumpIntoABodyAndCanStandOnIt(GameTestHelper helper) {
        ArtificialBodyEntity body = spawnBody(helper);
        Pig walker = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(0.7, 2.0, 2.5));
        Pig climber = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(2.5, 2.0 + body.getBbHeight() + 0.5, 2.5));
        helper.runAfterDelay(20, () -> {
            walker.move(MoverType.SELF, new Vec3(2.5, 0.0, 0.0));
            helper.assertTrue(walker.getBoundingBox().maxX <= body.getBoundingBox().minX + 0.001,
                    "A mob must stop at the body, pig max x " + walker.getBoundingBox().maxX + ", body min x " + body.getBoundingBox().minX);
            helper.assertTrue(Math.abs(climber.getY() - body.getBoundingBox().maxY) < 0.01 && climber.onGround(),
                    "A mob must stand on top of the body, pig y " + climber.getY() + ", body top " + body.getBoundingBox().maxY);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120)
    public static void flowingWaterDoesNotMoveABody(GameTestHelper helper) {
        ArtificialBodyEntity body = spawnBody(helper);
        helper.runAfterDelay(5, () -> {
            Vec3 start = body.position();
            helper.setBlock(POS.east(), Blocks.WATER);
            helper.runAfterDelay(60, () -> {
                helper.assertTrue(body.isInWater(), "The water must reach the body");
                Vec3 now = body.position();
                helper.assertTrue(Math.abs(now.x - start.x) < 0.001 && Math.abs(now.z - start.z) < 0.001,
                        "Water must not carry the body away: from " + start + " to " + now);
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE)
    public static void pistonDoesNotMoveABody(GameTestHelper helper) {
        ArtificialBodyEntity body = spawnBody(helper);
        helper.runAfterDelay(5, () -> {
            Vec3 start = body.position();
            helper.setBlock(POS.west(), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
            helper.setBlock(POS.west(2), Blocks.REDSTONE_BLOCK);
            helper.runAfterDelay(10, () -> {
                helper.assertBlockProperty(POS.west(), PistonBaseBlock.EXTENDED, true);
                assertStayed(helper, body, start, "Body in front of a piston");
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE)
    public static void bodyStillFalls(GameTestHelper helper) {
        ArtificialBodyEntity body = helper.spawn(BodyEntities.type(BodyClass.BASIC_ROBOT), new Vec3(2.5, 4.0, 2.5));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(body.onGround() && Math.abs(body.getY() - helper.absoluteVec(new Vec3(0, 2, 0)).y) < 0.01,
                    "A body in the air must fall to the floor, y " + body.getY());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void emptyArmorIsParkedButPilotedArmorIsNot(GameTestHelper helper) {
        PowerArmorEntity armor = spawnArmor(helper);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(armor.canBeCollidedWith(), "An empty armor must be solid");
            Vec3 start = armor.position();
            shove(armor);
            armor.move(MoverType.PISTON, new Vec3(0.5, 0.0, 0.0));
            helper.runAfterDelay(15, () -> {
                assertStayed(helper, armor, start, "Shoved empty armor");

                Player pilot = helper.makeMockPlayer(GameType.SURVIVAL);
                helper.assertTrue(pilot.startRiding(armor, true), "The pilot must be able to get in");
                helper.assertFalse(armor.canBeCollidedWith(), "A piloted armor must not block its pilot");
                armor.move(MoverType.PISTON, new Vec3(0.5, 0.0, 0.0));
                helper.assertTrue(armor.getX() > start.x + 0.4, "A piloted armor moves as before");

                pilot.stopRiding();
                helper.assertTrue(armor.canBeCollidedWith(), "The armor is solid again after the pilot leaves");
                helper.succeed();
            });
        });
    }
}
