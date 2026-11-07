package com.projectrebirth.gametest;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.factions.FactionEvents;
import com.projectrebirth.factions.FactionService;
import com.projectrebirth.shelter.AirFiltrationUnitBlockEntity;
import com.projectrebirth.shelter.ShelterRegistry;
import com.projectrebirth.technology.Station;
import com.projectrebirth.technology.StationBlockEntity;
import com.projectrebirth.technology.TechBlocks;
import com.projectrebirth.technology.energy.BackupBatteryBlockEntity;
import com.projectrebirth.technology.energy.CableInput;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Block C of plan 1.4: life support comes first on the cable, and the backup battery feeds only life support, only when
 * the main power does not keep up. A line of cable on the test floor: the unit at one end, a station at the other.
 */
@GameTestHolder(PlanTests.SHELTER)
@PrefixGameTestTemplate(false)
public class LifeSupportGameTests {
    private static final String TEMPLATE = "floor_9x9";
    private static final BlockPos UNIT = new BlockPos(1, 1, 4);
    private static final BlockPos STATION = new BlockPos(5, 1, 4);
    private static final BlockPos BACKUP = new BlockPos(3, 1, 5);

    /** Unit, three cables, station; nothing seals, so the unit does not work and keeps its energy. */
    private static void line(GameTestHelper helper) {
        helper.setBlock(UNIT, ShelterRegistry.AIR_FILTRATION_UNIT.get());
        for (int x = 2; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, 1, 4), TechBlocks.CABLE.get());
        }
        helper.setBlock(STATION, TechBlocks.STATIONS.get(Station.ELECTRONICS_FABRICATOR).get());
    }

    private static AirFiltrationUnitBlockEntity unit(GameTestHelper helper) {
        return helper.getBlockEntity(UNIT);
    }

    private static StationBlockEntity station(GameTestHelper helper) {
        return helper.getBlockEntity(STATION);
    }

    private static int feed(GameTestHelper helper, int amount) {
        return new CableInput(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 4)), null).receiveEnergy(amount, false);
    }

    @GameTest(template = TEMPLATE)
    public static void lifeSupportIsChargedFirst(GameTestHelper helper) {
        line(helper);
        helper.runAfterDelay(2, () -> {
            feed(helper, 1000);
            helper.assertTrue(unit(helper).energy().getEnergyStored() > 0, "The unit takes energy");
            helper.assertTrue(station(helper).energy().getEnergyStored() == 0, "The station waits while the unit is short");
            unit(helper).energy().generate(unit(helper).energy().getMaxEnergyStored());
            feed(helper, 1000);
            helper.assertTrue(station(helper).energy().getEnergyStored() > 0, "Once the unit is charged the station gets energy");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void aNearlyChargedUnitLetsTheStationHaveEnergy(GameTestHelper helper) {
        line(helper);
        helper.runAfterDelay(2, () -> {
            var energy = unit(helper).energy();
            energy.generate((int) Math.ceil(energy.getMaxEnergyStored() * RebirthConfig.LIFE_SUPPORT_CHARGED_SHARE.get()) + 10);
            feed(helper, 1000);
            helper.assertTrue(station(helper).energy().getEnergyStored() > 0, "A unit above the charged share does not hold the station back");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void theBackupWaitsWhileTheMainPowerKeepsUp(GameTestHelper helper) {
        line(helper);
        helper.setBlock(BACKUP, ShelterRegistry.BACKUP_BATTERY.get());
        helper.runAfterDelay(2, () -> {
            BackupBatteryBlockEntity backup = helper.getBlockEntity(BACKUP);
            backup.energy().generate(100_000);
            unit(helper).energy().generate(unit(helper).energy().getMaxEnergyStored());
            for (int tick = 0; tick < 20; tick++) {
                backup.serverTick();
            }
            helper.assertTrue(backup.energy().getEnergyStored() == 100_000, "The backup gives nothing to a charged unit");
            helper.assertTrue(backup.supported() == 1, "It sees the unit on its cable");
            unit(helper).energy().take(unit(helper).energy().getEnergyStored() - 1000, false);
            backup.serverTick();
            helper.assertTrue(unit(helper).energy().getEnergyStored() > 1000, "It feeds the unit once the main power falls short");
            helper.assertTrue(backup.energy().getEnergyStored() < 100_000, "And pays for it");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void theBackupNeverFeedsAStation(GameTestHelper helper) {
        helper.setBlock(STATION, TechBlocks.STATIONS.get(Station.ELECTRONICS_FABRICATOR).get());
        helper.setBlock(STATION.east(), ShelterRegistry.BACKUP_BATTERY.get());
        helper.runAfterDelay(2, () -> {
            BackupBatteryBlockEntity backup = helper.getBlockEntity(STATION.east());
            backup.energy().generate(100_000);
            for (int tick = 0; tick < 40; tick++) {
                backup.serverTick();
            }
            helper.assertTrue(station(helper).energy().getEnergyStored() == 0, "The station next to it gets nothing");
            helper.assertTrue(backup.input().extractEnergy(1000, false) == 0, "Nobody can draw energy out of it");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void theBackupChargesFromTheCableAfterLifeSupport(GameTestHelper helper) {
        line(helper);
        helper.setBlock(new BlockPos(3, 1, 3), ShelterRegistry.BACKUP_BATTERY.get());
        helper.runAfterDelay(2, () -> {
            BackupBatteryBlockEntity backup = helper.getBlockEntity(new BlockPos(3, 1, 3));
            feed(helper, 150);
            helper.assertTrue(backup.energy().getEnergyStored() == 0, "Not while the unit is short");
            unit(helper).energy().generate(unit(helper).energy().getMaxEnergyStored());
            feed(helper, 1000);
            helper.assertTrue(backup.energy().getEnergyStored() > 0, "Like any machine once life support is charged");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void theBackupBatteryIsProtectedByTerritory(GameTestHelper helper) {
        helper.setBlock(BACKUP, ShelterRegistry.BACKUP_BATTERY.get());
        helper.assertTrue(FactionEvents.useOf(helper.getLevel(), helper.absolutePos(BACKUP)) == FactionService.Use.CONTAINER, "A machine");
        helper.succeed();
    }
}
