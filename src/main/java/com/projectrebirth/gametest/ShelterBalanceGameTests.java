package com.projectrebirth.gametest;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.shelter.Room;
import com.projectrebirth.shelter.ShelterRegistry;
import com.projectrebirth.shelter.Shelters;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Block G of plan 1.4: the goals of the balance, with the numbers the game uses. A day is 1200 seconds; the air around a
 * shelter under a roof is the background (1) or a toxic storm of intensity 1 (21) times the roof (0.75).
 */
@GameTestHolder(PlanTests.SHELTER)
@PrefixGameTestTemplate(false)
public class ShelterBalanceGameTests {
    private static final String TEMPLATE = "floor_9x9";
    private static final int DAY = 1200;
    private static final int STORM_SECONDS = 270;

    /** Cartridge use per second of a room of this volume, with one unit, in balance with the air around (as Room and Shelters count it). */
    private static double usePerSecond(int volume, double around) {
        double drift = RebirthConfig.SHELTER_SEALED_DRIFT.get();
        double cleaned = Math.min(1.0, RebirthConfig.SHELTER_CLEAN_AIR_PER_SECOND.get() / volume);
        double pollution = around * drift * (1 - cleaned) / (1 - (1 - drift) * (1 - cleaned));
        double removed = (pollution + (around - pollution) * drift) * cleaned;
        return RebirthConfig.SHELTER_CARTRIDGE_BASE_USE.get() * volume / 1000.0 + RebirthConfig.SHELTER_CARTRIDGE_WORK_USE.get() * removed * volume;
    }

    private static double roofed(double pollution) {
        return pollution * RebirthConfig.ENVIRONMENT_ROOF_POLLUTION.get();
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void aSmallShelterIsCheapToKeep(GameTestHelper helper) {
        double life = RebirthConfig.SHELTER_CARTRIDGE_LIFE.get();
        double smallDays = life / (usePerSecond(100, roofed(1.0)) * DAY);
        helper.assertTrue(smallDays >= 30.0, "A cartridge lasts a room of 100 blocks a month at least: " + smallDays + " days");
        double mediumDays = life / (usePerSecond(500, roofed(1.0)) * DAY);
        helper.assertTrue(mediumDays >= 7.0, "And a room of 500 blocks a week: " + mediumDays + " days");
        double storm = usePerSecond(500, roofed(21.0)) * STORM_SECONDS / life;
        helper.assertTrue(storm < 0.25, "A toxic storm costs a room of 500 blocks under a quarter of a cartridge: " + storm);
        int energy = RebirthConfig.SHELTER_ENERGY_BASE.get() + RebirthConfig.SHELTER_ENERGY_PER_THOUSAND.get() * 500 / 1000;
        helper.assertTrue(energy <= 40, "One combustion generator (40 FE/t) runs it: " + energy + " FE/t");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void theUnitIsMadeWithoutStations(GameTestHelper helper) {
        var recipe = helper.getLevel().getRecipeManager().byKey(ProjectRebirth.id("air_filtration_unit")).orElseThrow().value();
        for (Ingredient ingredient : recipe.getIngredients()) {
            for (ItemStack stack : ingredient.getItems()) {
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                helper.assertTrue(id.startsWith("minecraft:") || id.equals("projectrebirth:filter"),
                        "Only vanilla things and respirator filters in an early shelter: " + id);
            }
        }
        var cartridge = helper.getLevel().getRecipeManager().byKey(ProjectRebirth.id("filter_cartridge")).orElseThrow().value();
        for (Ingredient ingredient : cartridge.getIngredients()) {
            for (ItemStack stack : ingredient.getItems()) {
                helper.assertTrue(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("minecraft"), "Cartridges from vanilla things");
            }
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void anIronDoorLetsInTheSameAirInAnyRoom(GameTestHelper helper) {
        ShelterGameTests.box(helper, Blocks.STONE);
        helper.runAfterDelay(2, () -> {
            Room plain = Shelters.of(helper.getLevel()).scanNow(helper.absolutePos(ShelterGameTests.UNIT));
            plain.setPollution(0.0f);
            plain.airSecond(10.0f, 0.0f);
            double withoutDoor = plain.pollution() * plain.volume();
            BlockState lower = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
            helper.setBlock(new BlockPos(2, 2, 4), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            helper.setBlock(new BlockPos(2, 3, 4), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            Room door = Shelters.of(helper.getLevel()).scanNow(helper.absolutePos(ShelterGameTests.UNIT));
            helper.assertTrue(door.partialLeaks() == 1, "One iron door");
            door.setPollution(0.0f);
            door.airSecond(10.0f, 0.0f);
            double through = door.pollution() * door.volume() - withoutDoor;
            double expected = 10.0 * RebirthConfig.SHELTER_PARTIAL_DOOR_LEAK.get();
            helper.assertTrue(Math.abs(through - expected) < 0.05,
                    "The door lets in its blocks of air per second, not a share of the room: " + through + " vs " + expected);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void washingAndPurgingCostWhatTheyShould(GameTestHelper helper) {
        helper.assertTrue(RebirthConfig.DECON_PER_SOLUTION.get() >= RebirthConfig.CONTAMINATION_MAX.get(),
                "One bottle of solution washes off the most anybody carries");
        // a radiation burst of intensity 1 in the open without protection, then the purge station
        double dose = RebirthConfig.environmentEvent("radiation_burst").hazard(com.projectrebirth.environment.Hazard.RADIATION)
                * RebirthConfig.HUMAN_DOSE_PER_RADIATION.get() * 180;
        double seconds = dose / RebirthConfig.PURGE_DOSE_PER_SECOND.get();
        double energy = seconds * 20 * RebirthConfig.PURGE_ENERGY.get();
        helper.assertTrue(seconds < 300, "A burst's dose goes in under five minutes: " + seconds + " s");
        helper.assertTrue(energy < RebirthConfig.BATTERY_CAPACITY.get(), "For less than one battery: " + energy + " FE");
        helper.assertTrue(RebirthConfig.PURGE_ENERGY.get() > RebirthConfig.SHELTER_ENERGY_BASE.get() * 10, "But much more than air filtration");
        helper.succeed();
    }
}
