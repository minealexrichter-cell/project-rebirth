package com.projectrebirth.gametest;

import java.util.function.Predicate;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.Biology;
import com.projectrebirth.environment.Catastrophes;
import com.projectrebirth.environment.Environment;
import com.projectrebirth.environment.EnvironmentRegistry;
import com.projectrebirth.environment.Exposure;
import com.projectrebirth.environment.Hazard;
import com.projectrebirth.environment.HumanBody;
import com.projectrebirth.factions.FactionEvents;
import com.projectrebirth.factions.FactionService;
import com.projectrebirth.shelter.AirFiltrationUnitBlock;
import com.projectrebirth.shelter.Room;
import com.projectrebirth.shelter.RoomScan;
import com.projectrebirth.shelter.ShelterRegistry;
import com.projectrebirth.shelter.Shelters;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Block A of plan 1.4: sealed rooms. A box of 5x5x5 blocks around a 3x3x3 room with an air filtration unit in its middle,
 * on a floor of 9x9 with free air around the box (the test frame has barrier walls) and the open sky above, so a hole leaks
 * to the sky; single walls are changed to see what seals.
 */
@GameTestHolder(PlanTests.SHELTER)
@PrefixGameTestTemplate(false)
public class ShelterGameTests {
    private static final String TEMPLATE = "floor_9x9";
    private static final BlockPos UNIT = new BlockPos(4, 2, 4);
    /** The middle of the west wall, at head height. */
    private static final BlockPos WALL = new BlockPos(2, 3, 4);
    private static final BlockPos DOOR_LOW = new BlockPos(2, 2, 4);
    private static final int INSIDE = 3 * 3 * 3 - 1;
    private static final float EPSILON = 1.0e-4f;

    /** The box of stone, the unit inside; then the wall changes. */
    private static void box(GameTestHelper helper, Block wall) {
        for (int x = 0; x <= 4; x++) {
            for (int y = 1; y <= 5; y++) {
                for (int z = 0; z <= 4; z++) {
                    boolean shell = x == 0 || x == 4 || y == 1 || y == 5 || z == 0 || z == 4;
                    helper.setBlock(new BlockPos(x + 2, y, z + 2), shell ? wall.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        helper.setBlock(UNIT, ShelterRegistry.AIR_FILTRATION_UNIT.get().defaultBlockState());
    }

    private static void door(GameTestHelper helper, Block door, boolean open) {
        BlockState lower = door.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST).setValue(DoorBlock.OPEN, open);
        helper.setBlock(DOOR_LOW, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(DOOR_LOW.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** Scans after the unit has loaded (its entity joins the level at the next tick) and checks the room. */
    private static void scanThen(GameTestHelper helper, Predicate<Room> check, String what) {
        helper.runAfterDelay(2, () -> {
            Room room = Shelters.of(helper.getLevel()).scanNow(helper.absolutePos(UNIT));
            helper.assertTrue(room != null, "The unit has a room");
            helper.assertTrue(check.test(room), what + " (sealed " + room.isSealed() + ", leak " + room.leak() + ", volume " + room.volume()
                    + ", partial " + room.partialLeaks() + ")");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void aClosedBoxIsSealed(GameTestHelper helper) {
        box(helper, Blocks.STONE);
        scanThen(helper, room -> room.isSealed() && room.volume() == INSIDE && room.partialLeaks() == 0, "A closed stone box is sealed, 26 blocks of air");
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void aHoleLeaksToTheSky(GameTestHelper helper) {
        box(helper, Blocks.STONE);
        helper.setBlock(WALL, Blocks.AIR);
        scanThen(helper, room -> room.leak() == RoomScan.Leak.SKY, "A hole in the wall leaks to the sky");
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void glassSeals(GameTestHelper helper) {
        box(helper, Blocks.GLASS);
        scanThen(helper, Room::isSealed, "Glass walls seal");
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void leavesLeak(GameTestHelper helper) {
        box(helper, Blocks.STONE);
        helper.setBlock(WALL, Blocks.OAK_LEAVES);
        scanThen(helper, room -> !room.isSealed(), "Leaves in the wall let the air out");
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void aFenceAndAPaneLeak(GameTestHelper helper) {
        box(helper, Blocks.STONE);
        helper.setBlock(WALL, Blocks.GLASS_PANE);
        helper.setBlock(new BlockPos(6, 3, 4), Blocks.OAK_FENCE);
        scanThen(helper, room -> !room.isSealed(), "A glass pane and a fence do not close a wall");
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void aSlabSealsOnlyByAWholeFace(GameTestHelper helper) {
        box(helper, Blocks.STONE);
        // a bottom slab in the roof: its bottom face is whole and faces the room
        helper.setBlock(new BlockPos(4, 5, 4), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        helper.runAfterDelay(2, () -> {
            Room roofed = Shelters.of(helper.getLevel()).scanNow(helper.absolutePos(UNIT));
            helper.assertTrue(roofed.isSealed(), "A bottom slab closes the roof with its whole lower face");
            // a slab standing in the wall has only half a side: the air goes over it
            helper.setBlock(WALL, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            Room walled = Shelters.of(helper.getLevel()).scanNow(helper.absolutePos(UNIT));
            helper.assertFalse(walled.isSealed(), "A half slab in the wall leaks");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void stairsSealByTheirBack(GameTestHelper helper) {
        box(helper, Blocks.STONE);
        helper.setBlock(WALL, Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
        helper.setBlock(new BlockPos(6, 3, 4), Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
        scanThen(helper, Room::isSealed, "Stairs in a wall seal by their whole back, either way round");
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void anAirlockDoorSealsWhenClosed(GameTestHelper helper) {
        box(helper, Blocks.STONE);
        door(helper, ShelterRegistry.AIRLOCK_DOOR.get(), false);
        scanThen(helper, room -> room.isSealed() && room.partialLeaks() == 0, "A closed airlock door seals fully");
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void anOpenAirlockDoorLeaks(GameTestHelper helper) {
        box(helper, Blocks.STONE);
        door(helper, ShelterRegistry.AIRLOCK_DOOR.get(), true);
        scanThen(helper, room -> !room.isSealed(), "An open airlock door leaks");
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void anIronDoorSealsWithASmallLeak(GameTestHelper helper) {
        box(helper, Blocks.STONE);
        door(helper, Blocks.IRON_DOOR, false);
        scanThen(helper, room -> room.isSealed() && room.partialLeaks() == 1, "A closed iron door seals, with one small leak");
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void aWoodenDoorDoesNotSeal(GameTestHelper helper) {
        box(helper, Blocks.STONE);
        door(helper, Blocks.OAK_DOOR, false);
        scanThen(helper, room -> !room.isSealed(), "A closed wooden door does not seal");
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void hatchesFollowTheTags(GameTestHelper helper) {
        box(helper, Blocks.STONE);
        helper.setBlock(new BlockPos(4, 5, 4), ShelterRegistry.SEALED_HATCH.get().defaultBlockState());
        helper.setBlock(new BlockPos(3, 5, 3), Blocks.IRON_TRAPDOOR.defaultBlockState());
        scanThen(helper, room -> room.isSealed() && room.partialLeaks() == 1, "A sealed hatch seals, an iron one seals with a small leak");
    }

    @GameTest(template = TEMPLATE, skyAccess = true, timeoutTicks = 200)
    public static void breakingAWallIsNoticed(GameTestHelper helper) {
        box(helper, Blocks.STONE);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(Shelters.of(helper.getLevel()).scanNow(helper.absolutePos(UNIT)).isSealed(), "Sealed first");
            helper.setBlock(WALL, Blocks.AIR);
            helper.succeedWhen(() -> {
                Room room = Shelters.of(helper.getLevel()).roomOf(helper.absolutePos(UNIT));
                helper.assertTrue(room != null && !room.isSealed(), "The broken wall is found by itself");
            });
        });
    }

    @GameTest(template = TEMPLATE, skyAccess = true, timeoutTicks = 200)
    public static void openingADoorIsNoticed(GameTestHelper helper) {
        box(helper, Blocks.STONE);
        door(helper, ShelterRegistry.AIRLOCK_DOOR.get(), false);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(Shelters.of(helper.getLevel()).scanNow(helper.absolutePos(UNIT)).isSealed(), "Sealed first");
            door(helper, ShelterRegistry.AIRLOCK_DOOR.get(), true);
            helper.succeedWhen(() -> {
                Room room = Shelters.of(helper.getLevel()).roomOf(helper.absolutePos(UNIT));
                helper.assertTrue(room != null && !room.isSealed(), "The opened door is found by itself");
                helper.assertTrue(Shelters.sealedRoomAt(helper.getLevel(), helper.absolutePos(new BlockPos(4, 3, 4))) == null,
                        "Nobody counts as inside a leaking room");
            });
        });
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void theUnitCleansASealedRoomAndALeakLetsTheAirIn(GameTestHelper helper) {
        box(helper, Blocks.STONE);
        helper.runAfterDelay(2, () -> {
            Room sealed = Shelters.of(helper.getLevel()).scanNow(helper.absolutePos(UNIT));
            sealed.setPollution(20.0f);
            sealed.airSecond(20.0f, 1);
            helper.assertTrue(sealed.pollution() < 1.0f, "One unit cleans a small sealed room quickly: " + sealed.pollution());
            sealed.airSecond(20.0f, 0);
            float slow = sealed.pollution();
            helper.assertTrue(slow > 0.0f && slow < 0.1f, "Without cleaning the walls let the air in slowly: " + slow);
            helper.setBlock(WALL, Blocks.AIR);
            Room leaking = Shelters.of(helper.getLevel()).scanNow(helper.absolutePos(UNIT));
            helper.assertFalse(leaking.isSealed(), "Now it leaks");
            for (int second = 0; second < 30; second++) {
                leaking.airSecond(20.0f, 1);
            }
            helper.assertTrue(leaking.pollution() > 18.0f, "Through a leak the air becomes as it is around: " + leaking.pollution());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void insideASealedRoomItsOwnAirHalfTheRadiationNoAcid(GameTestHelper helper) {
        box(helper, Blocks.STONE);
        helper.runAfterDelay(2, () -> {
            Room room = Shelters.of(helper.getLevel()).scanNow(helper.absolutePos(UNIT));
            room.setPollution(0.3f);
            BlockPos inside = helper.absolutePos(new BlockPos(4, 3, 4));
            Exposure ordinary = Environment.ordinaryExposureAt(helper.getLevel(), inside);
            Exposure here = Environment.exposureAt(helper.getLevel(), inside);
            helper.assertTrue(Math.abs(here.get(Hazard.POLLUTION) - 0.3f) < EPSILON, "The room's own air");
            helper.assertTrue(Math.abs(here.get(Hazard.RADIATION) - ordinary.get(Hazard.RADIATION)
                    * RebirthConfig.SHELTER_SEALED_RADIATION.get().floatValue()) < EPSILON, "Radiation is halved by the walls");
            helper.assertTrue(here.get(Hazard.ACID) == 0.0f, "No acid inside");
            helper.assertTrue(Math.abs(RebirthConfig.SHELTER_SEALED_RADIATION.get() - 0.5) < EPSILON, "Halved by default");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void aPersonInCleanAirGainsNoToxins(GameTestHelper helper) {
        box(helper, Blocks.STONE);
        helper.runAfterDelay(2, () -> {
            Room room = Shelters.of(helper.getLevel()).scanNow(helper.absolutePos(UNIT));
            room.setPollution(0.0f);
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            BlockPos floor = helper.absolutePos(new BlockPos(3, 2, 3));
            player.moveTo(floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5);
            HumanBody.setBiology(player, new Biology(0.0f, 10.0f));
            for (int second = 0; second < 60; second++) {
                Environment.updatePlayer(player);
                HumanBody.onSecond(player, Environment.lastExposure(player));
            }
            helper.assertTrue(Environment.lastExposure(player).get(Hazard.POLLUTION) == 0.0f, "Clean air around the person");
            helper.assertTrue(HumanBody.biology(player).toxin() < 10.0f, "The toxic load goes down: " + HumanBody.biology(player).toxin());
            HumanBody.setBiology(player, Biology.CLEAN);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void aRoofProtectsLessNow(GameTestHelper helper) {
        helper.assertTrue(Math.abs(RebirthConfig.ENVIRONMENT_ROOF_POLLUTION.get() - 0.75) < EPSILON, "A roof lowers pollution by 25% only");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void playersFrom130AreToldOnceAboutTheWeakerRoof(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setData(EnvironmentRegistry.NOTICE_SEEN, true);
        player.setData(EnvironmentRegistry.SHELTER_NOTICE_SEEN, false);
        helper.assertTrue(Catastrophes.tellOnce(player), "A player from 1.3.0 is told");
        helper.assertFalse(Catastrophes.tellOnce(player), "Once");
        player.setData(EnvironmentRegistry.NOTICE_SEEN, false);
        player.setData(EnvironmentRegistry.SHELTER_NOTICE_SEEN, false);
        helper.assertTrue(Catastrophes.tellOnce(player), "A new player gets the first notice");
        helper.assertTrue(player.getData(EnvironmentRegistry.SHELTER_NOTICE_SEEN), "Which already says it");
        helper.assertFalse(Catastrophes.tellOnce(player), "And nothing more");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void theUnitAndTheDoorsAreProtectedByTerritory(GameTestHelper helper) {
        box(helper, Blocks.STONE);
        door(helper, ShelterRegistry.AIRLOCK_DOOR.get(), false);
        helper.setBlock(new BlockPos(4, 5, 4), ShelterRegistry.SEALED_HATCH.get().defaultBlockState());
        helper.assertTrue(FactionEvents.useOf(helper.getLevel(), helper.absolutePos(UNIT)) == FactionService.Use.CONTAINER, "The unit is a machine");
        helper.assertTrue(FactionEvents.useOf(helper.getLevel(), helper.absolutePos(DOOR_LOW)) == FactionService.Use.DOOR, "The airlock door is a door");
        helper.assertTrue(FactionEvents.useOf(helper.getLevel(), helper.absolutePos(new BlockPos(4, 5, 4))) == FactionService.Use.DOOR, "The hatch is a door");
        helper.succeed();
    }

    // ---- the flood alone, in a made-up world ----

    /** A stone box from min to max (its shell), air inside and around; units where given (the scan starts at the first); loaded within the limit. */
    private static RoomScan.Space fakeBox(int min, int max, int loadedRadius, BlockPos... units) {
        BlockState unit = ShelterRegistry.AIR_FILTRATION_UNIT.get().defaultBlockState();
        return new RoomScan.Space() {
            @Override
            public boolean loaded(BlockPos pos) {
                return Math.abs(pos.getX()) <= loadedRadius && Math.abs(pos.getY()) <= loadedRadius && Math.abs(pos.getZ()) <= loadedRadius;
            }

            @Override
            public BlockState state(BlockPos pos) {
                for (BlockPos at : units) {
                    if (at.equals(pos)) {
                        return unit;
                    }
                }
                boolean within = pos.getX() >= min && pos.getX() <= max && pos.getY() >= min && pos.getY() <= max && pos.getZ() >= min && pos.getZ() <= max;
                boolean shell = within && (pos.getX() == min || pos.getX() == max || pos.getY() == min || pos.getY() == max
                        || pos.getZ() == min || pos.getZ() == max);
                return shell ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
            }

            @Override
            public boolean openSky(BlockPos pos) {
                return false;
            }

            @Override
            public boolean isUnit(BlockState state) {
                return state.getBlock() instanceof AirFiltrationUnitBlock;
            }
        };
    }

    private static Room scan(RoomScan.Space space, BlockPos start) {
        RoomScan scan = new RoomScan(start, RebirthConfig.SHELTER_MAX_VOLUME_PER_UNIT.get(), RebirthConfig.SHELTER_MAX_UNITS_PER_ROOM.get());
        while (!scan.isDone()) {
            scan.step(space, 1000);
        }
        return scan.result();
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void anUnloadedChunkLeaks(GameTestHelper helper) {
        // no walls at all within the loaded part
        Room room = scan(fakeBox(100, 101, 6, BlockPos.ZERO), BlockPos.ZERO);
        helper.assertTrue(room.leak() == RoomScan.Leak.UNLOADED, "The air reaches the unloaded area: " + room.leak());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void aRoomTooLargeLeaksAndASecondUnitHoldsIt(GameTestHelper helper) {
        // 20x20x20 of air inside: 8000 blocks
        Room one = scan(fakeBox(-10, 11, 64, BlockPos.ZERO), BlockPos.ZERO);
        helper.assertTrue(one.leak() == RoomScan.Leak.TOO_LARGE, "One unit holds 4096 blocks: " + one.leak());
        Room two = scan(fakeBox(-10, 11, 64, BlockPos.ZERO, new BlockPos(5, 5, 5)), BlockPos.ZERO);
        helper.assertTrue(two.isSealed() && two.units().size() == 2, "Two units in one room add up: " + two.leak() + ", units " + two.units().size());
        helper.assertTrue(two.volume() == 8000 - 2, "All the air: " + two.volume());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void aLargeRoomIsScannedQuickly(GameTestHelper helper) {
        // the largest room of one unit: 16x16x16 inside, and of four: 25x25x25
        long start = System.nanoTime();
        Room one = scan(fakeBox(-8, 9, 64, BlockPos.ZERO), BlockPos.ZERO);
        long oneNanos = System.nanoTime() - start;
        start = System.nanoTime();
        Room four = scan(fakeBox(-12, 14, 64, BlockPos.ZERO, new BlockPos(5, 5, 5), new BlockPos(-5, -5, -5), new BlockPos(5, -5, 5)), BlockPos.ZERO);
        long fourNanos = System.nanoTime() - start;
        ProjectRebirth.LOGGER.info("Shelter scan: {} blocks in {} ms, {} blocks in {} ms", one.volume(), oneNanos / 1_000_000.0,
                four.volume(), fourNanos / 1_000_000.0);
        helper.assertTrue(one.isSealed() && four.isSealed(), "Both sealed: " + one.leak() + ", " + four.leak());
        helper.assertTrue(oneNanos < 50_000_000L, "A room of 4096 blocks in well under a tick's time at once: " + oneNanos / 1_000_000.0 + " ms");
        helper.succeed();
    }
}
