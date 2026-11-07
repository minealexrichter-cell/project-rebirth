package com.projectrebirth.gametest;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.Biology;
import com.projectrebirth.environment.Exposure;
import com.projectrebirth.environment.Hazard;
import com.projectrebirth.environment.HumanBody;
import com.projectrebirth.factions.FactionEvents;
import com.projectrebirth.factions.FactionService;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.powerarmor.PowerArmorRegistry;
import com.projectrebirth.shelter.Airlock;
import com.projectrebirth.shelter.Contamination;
import com.projectrebirth.shelter.DecontaminationChamberBlock;
import com.projectrebirth.shelter.DecontaminationChamberBlockEntity;
import com.projectrebirth.shelter.RadiationPurgeStationBlockEntity;
import com.projectrebirth.shelter.Room;
import com.projectrebirth.shelter.ShelterRegistry;
import com.projectrebirth.shelter.Shelters;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Block D of plan 1.4: external contamination, the decontamination chamber, the radiation purge station, the airlock. */
@GameTestHolder(PlanTests.SHELTER)
@PrefixGameTestTemplate(false)
public class DecontaminationGameTests {
    private static final String TEMPLATE = "floor_9x9";
    private static final BlockPos CHAMBER = new BlockPos(4, 1, 4);
    private static final float EPSILON = 1.0e-3f;

    private static Exposure exposure(float pollution, float radiation, float acid) {
        return Exposure.builder().add(Hazard.POLLUTION, pollution).add(Hazard.RADIATION, radiation).add(Hazard.ACID, acid).build();
    }

    private static ServerPlayer player(GameTestHelper helper, BlockPos at) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos abs = helper.absolutePos(at);
        player.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
        return player;
    }

    private static DecontaminationChamberBlockEntity chamber(GameTestHelper helper) {
        BlockState lower = ShelterRegistry.DECONTAMINATION_CHAMBER.get().defaultBlockState().setValue(DecontaminationChamberBlock.FACING, Direction.NORTH);
        helper.setBlock(CHAMBER, lower);
        helper.setBlock(CHAMBER.above(), lower.setValue(DecontaminationChamberBlock.HALF, DoubleBlockHalf.UPPER));
        return helper.getBlockEntity(CHAMBER);
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void contaminationGathersInCatastrophesNotOnTheBackground(GameTestHelper helper) {
        helper.assertTrue(Contamination.gainPerSecond(exposure(0.75f, 1.0f, 0.0f)) == 0.0f, "The ordinary background leaves none");
        helper.assertTrue(Contamination.gainPerSecond(exposure(23.25f, 1.0f, 0.0f)) > 0.3f, "A toxic storm leaves a lot");
        helper.assertTrue(Contamination.gainPerSecond(exposure(3.0f, 1.0f, 1.0f)) > 0.1f, "Acid rain leaves some");
        helper.assertTrue(Contamination.gainPerSecond(exposure(0.75f, 181.0f, 0.0f)) > 0.3f, "A radiation burst leaves fallout");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void itComesOffSlowlyByItself(GameTestHelper helper) {
        ServerPlayer player = player(helper, new BlockPos(1, 1, 1));
        Contamination.set(player, 50.0f);
        for (int second = 0; second < 10; second++) {
            Contamination.second(player);
        }
        float left = Contamination.of(player);
        helper.assertTrue(left < 50.0f && left > 49.0f, "Very slowly: " + left);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void itIsShedIntoTheAirOfASealedRoom(GameTestHelper helper) {
        ShelterGameTests.box(helper, Blocks.STONE);
        helper.runAfterDelay(2, () -> {
            Room room = Shelters.of(helper.getLevel()).scanNow(helper.absolutePos(ShelterGameTests.UNIT));
            room.setPollution(0.0f);
            ServerPlayer player = player(helper, new BlockPos(3, 2, 3));
            Contamination.set(player, 50.0f);
            for (int second = 0; second < 10; second++) {
                Contamination.second(player);
            }
            helper.assertTrue(room.pollution() > 1.0f, "A contaminated person dirties a small room: " + room.pollution());
            helper.assertTrue(Contamination.of(player) < 45.0f, "And loses what they shed: " + Contamination.of(player));
            Contamination.set(player, 0.0f);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, skyAccess = true, timeoutTicks = 200)
    public static void theChamberWashesAPerson(GameTestHelper helper) {
        DecontaminationChamberBlockEntity chamber = chamber(helper);
        chamber.energy().generate(chamber.energy().getMaxEnergyStored());
        chamber.insert(new ItemStack(ShelterRegistry.DECONTAMINATION_SOLUTION.get(), 2));
        ServerPlayer player = player(helper, CHAMBER);
        Contamination.set(player, 50.0f);
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(Contamination.of(player) < 1.0f, "Washed off in a few seconds: " + Contamination.of(player));
            helper.assertTrue(chamber.items().getStackInSlot(0).getCount() == 1, "One solution used for 50 units");
            helper.assertTrue(chamber.energy().getEnergyStored() < chamber.energy().getMaxEnergyStored(), "Energy used");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, skyAccess = true, timeoutTicks = 200)
    public static void withoutSolutionTheChamberDoesNotWash(GameTestHelper helper) {
        DecontaminationChamberBlockEntity chamber = chamber(helper);
        chamber.energy().generate(chamber.energy().getMaxEnergyStored());
        ServerPlayer player = player(helper, CHAMBER);
        Contamination.set(player, 50.0f);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(Contamination.of(player) > 49.0f, "Nothing washed: " + Contamination.of(player));
            Contamination.set(player, 0.0f);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, skyAccess = true, timeoutTicks = 200)
    public static void theChamberWashesAPowerArmorInFrontOfIt(GameTestHelper helper) {
        DecontaminationChamberBlockEntity chamber = chamber(helper);
        chamber.energy().generate(chamber.energy().getMaxEnergyStored());
        chamber.insert(new ItemStack(ShelterRegistry.DECONTAMINATION_SOLUTION.get(), 2));
        PowerArmorEntity armor = helper.spawn(PowerArmorRegistry.POWER_ARMOR.get(), CHAMBER.north());
        Contamination.set(armor, 60.0f);
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(Contamination.of(armor) < 1.0f, "The armor standing in front is washed: " + Contamination.of(armor));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void hoppersBringSolution(GameTestHelper helper) {
        DecontaminationChamberBlockEntity chamber = chamber(helper);
        helper.assertTrue(chamber.automation().insertItem(0, new ItemStack(Items.DIRT), false).getCount() == 1, "Only solution goes in");
        helper.assertTrue(chamber.automation().insertItem(0, new ItemStack(ShelterRegistry.DECONTAMINATION_SOLUTION.get(), 3), false).isEmpty(), "Solution goes in");
        helper.assertTrue(chamber.automation().extractItem(0, 1, false).isEmpty(), "And is not taken out");
        helper.succeed();
    }

    private static RadiationPurgeStationBlockEntity purgeStation(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at, ShelterRegistry.RADIATION_PURGE_STATION.get());
        RadiationPurgeStationBlockEntity station = helper.getBlockEntity(at);
        station.energy().generate(station.energy().getMaxEnergyStored());
        return station;
    }

    @GameTest(template = TEMPLATE, skyAccess = true, timeoutTicks = 200)
    public static void thePurgeStationLowersTheDoseInASealedCleanRoom(GameTestHelper helper) {
        ShelterGameTests.box(helper, Blocks.STONE);
        RadiationPurgeStationBlockEntity station = purgeStation(helper, new BlockPos(3, 2, 3));
        helper.runAfterDelay(2, () -> {
            Room room = Shelters.of(helper.getLevel()).scanNow(helper.absolutePos(ShelterGameTests.UNIT));
            helper.assertTrue(room.isSealed(), "The platform keeps the room sealed");
            room.setPollution(0.0f);
            ServerPlayer player = player(helper, new BlockPos(3, 2, 3));
            player.moveTo(player.getX(), player.getY() + 0.25, player.getZ());
            HumanBody.setBiology(player, new Biology(50.0f, 0.0f));
            helper.runAfterDelay(40, () -> {
                float dose = HumanBody.biology(player).dose();
                helper.assertTrue(dose < 50.0f - RebirthConfig.PURGE_DOSE_PER_SECOND.get().floatValue(), "The dose goes down: " + dose);
                // the room may have been scanned again meanwhile: take the one there now
                Shelters.sealedRoomAt(helper.getLevel(), helper.absolutePos(new BlockPos(3, 3, 3))).setPollution(5.0f);
                helper.assertTrue(station.reasonFor(player) == RadiationPurgeStationBlockEntity.Reason.DIRTY_AIR, "Not in polluted air");
                HumanBody.setBiology(player, Biology.CLEAN);
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, skyAccess = true, timeoutTicks = 200)
    public static void thePurgeStationDoesNotWorkOutside(GameTestHelper helper) {
        RadiationPurgeStationBlockEntity station = purgeStation(helper, new BlockPos(4, 1, 4));
        ServerPlayer player = player(helper, new BlockPos(4, 1, 4));
        player.moveTo(player.getX(), player.getY() + 0.25, player.getZ());
        HumanBody.setBiology(player, new Biology(50.0f, 0.0f));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(Math.abs(HumanBody.biology(player).dose() - 50.0f) < 0.5f, "No purge outside a sealed room");
            helper.assertTrue(station.reasonFor(player) == RadiationPurgeStationBlockEntity.Reason.NOT_SEALED, "And it says why");
            HumanBody.setBiology(player, Biology.CLEAN);
            helper.succeed();
        });
    }

    /** A space of one by two blocks between two airlock doors along x, walled at the sides and roofed. */
    private static void airlock(GameTestHelper helper) {
        for (int y = 1; y <= 2; y++) {
            helper.setBlock(new BlockPos(4, y, 3), Blocks.STONE);
            helper.setBlock(new BlockPos(4, y, 5), Blocks.STONE);
            for (int x : new int[] {3, 5}) {
                helper.setBlock(new BlockPos(x, y, 3), Blocks.STONE);
                helper.setBlock(new BlockPos(x, y, 5), Blocks.STONE);
            }
        }
        for (int x = 3; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 3, 4), Blocks.STONE);
        }
        door(helper, 3, false);
        door(helper, 5, false);
    }

    private static void door(GameTestHelper helper, int x, boolean open) {
        BlockState lower = ShelterRegistry.AIRLOCK_DOOR.get().defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST).setValue(DoorBlock.OPEN, open);
        helper.setBlock(new BlockPos(x, 1, 4), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(new BlockPos(x, 2, 4), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void theDoorsOfAnAirlockDoNotOpenTogether(GameTestHelper helper) {
        airlock(helper);
        BlockPos outer = helper.absolutePos(new BlockPos(3, 1, 4));
        helper.assertTrue(Airlock.mayOpen(helper.getLevel(), outer), "Both closed: the outer one opens");
        door(helper, 5, true);
        helper.assertFalse(Airlock.mayOpen(helper.getLevel(), outer), "The inner one open: the outer one does not");
        helper.assertFalse(Airlock.mayOpen(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 4))), "Not by its upper half either");
        door(helper, 5, false);
        helper.assertTrue(Airlock.mayOpen(helper.getLevel(), outer), "Closed again: it opens");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void theNewBlocksAreProtectedAndHaveRecipes(GameTestHelper helper) {
        chamber(helper);
        helper.setBlock(new BlockPos(1, 1, 1), ShelterRegistry.RADIATION_PURGE_STATION.get());
        helper.assertTrue(FactionEvents.useOf(helper.getLevel(), helper.absolutePos(CHAMBER)) == FactionService.Use.CONTAINER, "The chamber is a machine");
        helper.assertTrue(FactionEvents.useOf(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))) == FactionService.Use.CONTAINER, "The station too");
        var recipes = helper.getLevel().getRecipeManager();
        for (String id : new String[] {"decontamination_chamber", "decontamination_solution", "radiation_purge_station"}) {
            helper.assertTrue(recipes.byKey(ProjectRebirth.id(id)).isPresent(), "Recipe " + id);
        }
        helper.succeed();
    }
}
