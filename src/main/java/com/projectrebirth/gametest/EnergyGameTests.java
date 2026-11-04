package com.projectrebirth.gametest;

import java.util.List;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.technology.FabricationRecipe;
import com.projectrebirth.technology.Metal;
import com.projectrebirth.technology.Station;
import com.projectrebirth.technology.StationBlockEntity;
import com.projectrebirth.technology.TechBlocks;
import com.projectrebirth.technology.TechItems;
import com.projectrebirth.technology.TechRecipes;
import com.projectrebirth.technology.energy.BatteryBlock;
import com.projectrebirth.technology.energy.BatteryBlockEntity;
import com.projectrebirth.technology.energy.CombustionGeneratorBlockEntity;
import com.projectrebirth.technology.energy.SolarPanelBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Energy: generators, cable, battery and stations that wait for energy. Run with {@code .\gradlew runGameTestServer}. */
@GameTestHolder(ProjectRebirth.MOD_ID)
@PrefixGameTestTemplate(false)
public class EnergyGameTests {
    // 5x5x5 with a stone floor at y = 1.
    private static final String TEMPLATE = "floor_5x5";

    private static StationBlockEntity station(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, TechBlocks.STATIONS.get(Station.ELECTRONICS_FABRICATOR).get());
        return helper.getBlockEntity(pos);
    }

    private static CombustionGeneratorBlockEntity generator(GameTestHelper helper, BlockPos pos, int coal) {
        helper.setBlock(pos, TechBlocks.COMBUSTION_GENERATOR.get());
        CombustionGeneratorBlockEntity generator = helper.getBlockEntity(pos);
        generator.items().setStackInSlot(CombustionGeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, coal));
        return generator;
    }

    /** Puts the materials of two circuit boards in and picks that recipe; returns its production time. */
    private static int startCircuitBoards(GameTestHelper helper, StationBlockEntity station) {
        station.items().setStackInSlot(0, new ItemStack(Items.COPPER_INGOT, 3));
        station.items().setStackInSlot(1, new ItemStack(TechItems.INGOTS.get(Metal.SILVER).get(), 1));
        station.items().setStackInSlot(2, new ItemStack(Items.REDSTONE, 2));
        List<RecipeHolder<FabricationRecipe>> recipes = TechRecipes.recipesFor(helper.getLevel(), Station.ELECTRONICS_FABRICATOR);
        for (int i = 0; i < recipes.size(); i++) {
            if (recipes.get(i).id().getPath().equals("circuit_board")) {
                station.select(i);
                return StationBlockEntity.productionTime(recipes.get(i).value());
            }
        }
        throw new IllegalStateException("No circuit board recipe");
    }

    @GameTest(template = TEMPLATE)
    public static void combustionGeneratorBurnsFuelAndChargesTheStationNextToIt(GameTestHelper helper) {
        CombustionGeneratorBlockEntity generator = generator(helper, new BlockPos(1, 2, 2), 2);
        StationBlockEntity station = station(helper, new BlockPos(2, 2, 2));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(generator.getBlockState().getValue(com.projectrebirth.technology.energy.CombustionGeneratorBlock.LIT),
                    "The generator must be burning");
            helper.assertTrue(generator.items().getStackInSlot(0).getCount() == 1, "One coal must be in use");
            helper.assertTrue(station.energy().getEnergyStored() > 0, "The station next to the generator must be charged");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void fullGeneratorDoesNotWasteFuel(GameTestHelper helper) {
        CombustionGeneratorBlockEntity generator = generator(helper, new BlockPos(2, 2, 2), 3);
        generator.energy().generate(Integer.MAX_VALUE);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(generator.items().getStackInSlot(0).getCount() == 3, "A full generator with nowhere to send energy must not burn");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void cableCarriesEnergyToEveryStationOnIt(GameTestHelper helper) {
        generator(helper, new BlockPos(0, 2, 2), 2);
        for (int x = 1; x <= 3; x++) {
            helper.setBlock(new BlockPos(x, 2, 2), TechBlocks.CABLE.get());
        }
        StationBlockEntity end = station(helper, new BlockPos(4, 2, 2));
        StationBlockEntity side = station(helper, new BlockPos(2, 2, 3));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(end.energy().getEnergyStored() > 0, "The station at the end of the cable must be charged");
            helper.assertTrue(side.energy().getEnergyStored() > 0, "The station beside the cable must be charged too");
            IEnergyStorage cable = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,
                    helper.absolutePos(new BlockPos(2, 2, 2)), Direction.UP);
            helper.assertTrue(cable != null && cable.getEnergyStored() == 0, "The cable itself stores nothing");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void stationWithoutEnergyWaitsAndContinues(GameTestHelper helper) {
        StationBlockEntity station = station(helper, new BlockPos(2, 2, 2));
        int time = startCircuitBoards(helper, station);
        int perTick = StationBlockEntity.energyPerTick();
        helper.assertTrue(perTick > 0, "Stations must need energy by default");
        int half = time / 2;
        station.energy().generate(half * perTick);
        helper.runAfterDelay(half + 20, () -> {
            helper.assertTrue(station.progress() == half, "Out of energy the station must stop and keep its progress, got "
                    + station.progress() + " of " + time);
            helper.assertTrue(station.items().getStackInSlot(StationBlockEntity.OUTPUT_SLOT).isEmpty(), "Nothing is made yet");
            station.energy().generate(time * perTick);
            helper.runAfterDelay(time - half + 5, () -> {
                ItemStack output = station.items().getStackInSlot(StationBlockEntity.OUTPUT_SLOT);
                helper.assertTrue(output.is(TechItems.CIRCUIT_BOARD.get()) && output.getCount() == 2,
                        "With energy again the station must finish what it started, got " + output);
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE)
    public static void batteryGivesEnergyOnlyThroughItsFront(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 2, 2);
        helper.setBlock(at, TechBlocks.BATTERY.get().defaultBlockState().setValue(BatteryBlock.FACING, Direction.EAST));
        BatteryBlockEntity battery = helper.getBlockEntity(at);
        StationBlockEntity front = station(helper, at.east());
        StationBlockEntity back = station(helper, at.west());
        battery.energy().generate(10000);
        helper.assertTrue(battery.forSide(Direction.EAST).receiveEnergy(100, true) == 0, "The front takes nothing in");
        helper.assertTrue(battery.forSide(Direction.WEST).receiveEnergy(100, true) == 100, "Other sides take energy in");
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(front.energy().getEnergyStored() > 0, "The station at the front must be charged");
            helper.assertTrue(back.energy().getEnergyStored() == 0, "The station behind must get nothing");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void solarPanelNeedsDaylightAndOpenSky(GameTestHelper helper) {
        BlockPos open = new BlockPos(1, 2, 1);
        BlockPos covered = new BlockPos(3, 2, 3);
        helper.setBlock(open, TechBlocks.SOLAR_PANEL.get());
        helper.setBlock(covered, TechBlocks.SOLAR_PANEL.get());
        helper.setBlock(covered.above(), Blocks.STONE);
        SolarPanelBlockEntity openPanel = helper.getBlockEntity(open);
        SolarPanelBlockEntity coveredPanel = helper.getBlockEntity(covered);
        // Sky light and daylight update during the next ticks, not at once.
        long time = helper.getLevel().getDayTime();
        helper.getLevel().setDayTime(6000);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(openPanel.seesSun(), "An open panel must see the sun at noon");
            helper.assertFalse(coveredPanel.seesSun(), "A covered panel must not");
            helper.getLevel().setDayTime(18000);
            helper.runAfterDelay(5, () -> {
                boolean midnight = openPanel.seesSun();
                helper.getLevel().setDayTime(time);
                helper.assertFalse(midnight, "Nothing at midnight");
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE)
    public static void solarPanelHasItsEnergySideOnlyAtTheBottom(GameTestHelper helper) {
        BlockPos panelPos = new BlockPos(2, 3, 2);
        BlockPos below = panelPos.below();
        BlockPos beside = panelPos.east();
        // The cables come first: a block set by a test only connects when a neighbour changes.
        helper.setBlock(below, TechBlocks.CABLE.get());
        helper.setBlock(beside, TechBlocks.CABLE.get());
        helper.setBlock(panelPos, TechBlocks.SOLAR_PANEL.get());
        BlockPos absolute = helper.absolutePos(panelPos);
        helper.assertTrue(helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, absolute, Direction.DOWN) != null,
                "The bottom of the panel must be an energy side");
        for (Direction side : List.of(Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            helper.assertTrue(helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, absolute, side) == null,
                    "The panel must have no energy on side " + side);
        }
        helper.assertBlockProperty(below, PipeBlock.UP, true);
        helper.assertBlockProperty(beside, PipeBlock.WEST, false);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void stationEnergyIsSaved(GameTestHelper helper) {
        StationBlockEntity station = station(helper, new BlockPos(2, 2, 2));
        station.energy().generate(1234);
        var tag = station.saveWithFullMetadata(helper.getLevel().registryAccess());
        var loaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(station.getBlockPos(), station.getBlockState(), tag,
                helper.getLevel().registryAccess());
        helper.assertTrue(loaded instanceof StationBlockEntity copy && copy.energy().getEnergyStored() == 1234,
                "The station must keep its energy when saved and loaded");
        helper.assertTrue(RebirthConfig.STATION_ENERGY_CAPACITY.get() >= 1234, "Default capacity holds it");
        helper.succeed();
    }
}
