package com.projectrebirth.gametest;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.shelter.AirFiltrationUnitBlockEntity;
import com.projectrebirth.shelter.CartridgeItem;
import com.projectrebirth.shelter.Room;
import com.projectrebirth.shelter.ShelterRegistry;
import com.projectrebirth.shelter.Shelters;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Block B of plan 1.4: the air filtration unit works with energy and filter cartridges. The same sealed box as in
 * {@link ShelterGameTests}; the unit's own ticks and the seconds of the rooms run as in the game.
 */
@GameTestHolder(PlanTests.SHELTER)
@PrefixGameTestTemplate(false)
public class FiltrationGameTests {
    private static final String TEMPLATE = "floor_9x9";
    private static final float EPSILON = 1.0e-3f;

    private static AirFiltrationUnitBlockEntity unit(GameTestHelper helper) {
        return (AirFiltrationUnitBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(ShelterGameTests.UNIT));
    }

    private static Room scan(GameTestHelper helper) {
        return Shelters.of(helper.getLevel()).scanNow(helper.absolutePos(ShelterGameTests.UNIT));
    }

    private static void charge(AirFiltrationUnitBlockEntity unit) {
        unit.energy().generate(unit.energy().getMaxEnergyStored());
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void withoutACartridgeNothingIsCleaned(GameTestHelper helper) {
        ShelterGameTests.box(helper, Blocks.STONE);
        helper.runAfterDelay(2, () -> {
            Room room = scan(helper);
            charge(unit(helper));
            room.setPollution(10.0f);
            helper.runAfterDelay(45, () -> {
                helper.assertTrue(room.pollution() > 9.0f, "The air stays dirty: " + room.pollution());
                helper.assertTrue(unit(helper).state() == AirFiltrationUnitBlockEntity.STATE_NO_CARTRIDGE, "The unit says there is no cartridge");
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void withoutEnergyNothingIsCleaned(GameTestHelper helper) {
        ShelterGameTests.box(helper, Blocks.STONE);
        helper.runAfterDelay(2, () -> {
            Room room = scan(helper);
            unit(helper).items().setStackInSlot(AirFiltrationUnitBlockEntity.ACTIVE_SLOT, new ItemStack(ShelterRegistry.FILTER_CARTRIDGE.get()));
            room.setPollution(10.0f);
            helper.runAfterDelay(45, () -> {
                helper.assertTrue(RebirthConfig.STATIONS_NEED_ENERGY.get(), "Machines need energy by default");
                helper.assertTrue(room.pollution() > 9.0f, "The air stays dirty: " + room.pollution());
                helper.assertTrue(unit(helper).state() == AirFiltrationUnitBlockEntity.STATE_NO_ENERGY, "The unit says energy is short");
                helper.assertTrue(CartridgeItem.lifeShare(unit(helper).items().getStackInSlot(AirFiltrationUnitBlockEntity.ACTIVE_SLOT)) == 1.0f,
                        "The cartridge is not used");
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void withEnergyAndACartridgeTheAirIsCleaned(GameTestHelper helper) {
        ShelterGameTests.box(helper, Blocks.STONE);
        helper.runAfterDelay(2, () -> {
            Room room = scan(helper);
            AirFiltrationUnitBlockEntity unit = unit(helper);
            unit.items().setStackInSlot(AirFiltrationUnitBlockEntity.SPARE_SLOT, new ItemStack(ShelterRegistry.FILTER_CARTRIDGE.get(), 2));
            charge(unit);
            int full = unit.energy().getEnergyStored();
            room.setPollution(10.0f);
            helper.runAfterDelay(45, () -> {
                helper.assertTrue(room.pollution() < 0.5f, "The air is clean: " + room.pollution());
                helper.assertTrue(unit.state() == AirFiltrationUnitBlockEntity.STATE_WORKING, "Working: " + unit.state());
                helper.assertTrue(unit.items().getStackInSlot(AirFiltrationUnitBlockEntity.SPARE_SLOT).getCount() == 1, "One spare went into use");
                float left = CartridgeItem.lifeShare(unit.items().getStackInSlot(AirFiltrationUnitBlockEntity.ACTIVE_SLOT));
                helper.assertTrue(left > 0.0f && left < 1.0f, "The cartridge in use is worn: " + left);
                helper.assertTrue(unit.energy().getEnergyStored() < full, "Energy was used");
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void aLeakingRoomIsNotWorkedOn(GameTestHelper helper) {
        ShelterGameTests.box(helper, Blocks.STONE);
        helper.setBlock(ShelterGameTests.WALL, Blocks.AIR);
        helper.runAfterDelay(2, () -> {
            scan(helper);
            AirFiltrationUnitBlockEntity unit = unit(helper);
            unit.items().setStackInSlot(AirFiltrationUnitBlockEntity.ACTIVE_SLOT, new ItemStack(ShelterRegistry.FILTER_CARTRIDGE.get()));
            charge(unit);
            int full = unit.energy().getEnergyStored();
            helper.runAfterDelay(30, () -> {
                helper.assertTrue(unit.state() == AirFiltrationUnitBlockEntity.STATE_LEAK_SKY, "The unit shows the leak: " + unit.state());
                helper.assertTrue(unit.energy().getEnergyStored() == full, "No energy is wasted on a leaking room");
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void aStormWearsTheCartridgeFaster(GameTestHelper helper) {
        ShelterGameTests.box(helper, Blocks.STONE);
        helper.runAfterDelay(2, () -> {
            Room room = scan(helper);
            // the room in balance with its unit, under the ordinary background and in a strong storm
            float calm = 0.0f;
            room.setPollution(0.0f);
            for (int second = 0; second < 600; second++) {
                calm = room.airSecond(0.75f, 1.0f);
            }
            float storm = 0.0f;
            room.setPollution(0.0f);
            for (int second = 0; second < 600; second++) {
                storm = room.airSecond(23.25f, 1.0f);
            }
            helper.assertTrue(storm > calm * 20.0f, "Much more is taken out of the air in a storm: " + storm + " vs " + calm);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void twoUnitsCleanFaster(GameTestHelper helper) {
        ShelterGameTests.box(helper, Blocks.STONE);
        helper.runAfterDelay(2, () -> {
            Room room = scan(helper);
            room.setPollution(10.0f);
            room.airSecond(10.0f, 0.2f);
            float one = room.pollution();
            room.setPollution(10.0f);
            room.airSecond(10.0f, 0.4f);
            helper.assertTrue(room.pollution() < one, "Twice the units, cleaner air: " + room.pollution() + " vs " + one);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void aUsedUpCartridgeGoesToTheSpentSlot(GameTestHelper helper) {
        ShelterGameTests.box(helper, Blocks.STONE);
        helper.runAfterDelay(2, () -> {
            AirFiltrationUnitBlockEntity unit = unit(helper);
            ItemStack worn = new ItemStack(ShelterRegistry.FILTER_CARTRIDGE.get());
            CartridgeItem.setLife(worn, 1.0f);
            unit.items().setStackInSlot(AirFiltrationUnitBlockEntity.ACTIVE_SLOT, worn);
            unit.items().setStackInSlot(AirFiltrationUnitBlockEntity.SPARE_SLOT, new ItemStack(ShelterRegistry.FILTER_CARTRIDGE.get()));
            unit.useCartridge(5.0f);
            helper.assertTrue(unit.items().getStackInSlot(AirFiltrationUnitBlockEntity.SPENT_SLOT).is(ShelterRegistry.SPENT_FILTER_CARTRIDGE.get()),
                    "A spent cartridge comes out");
            helper.assertTrue(CartridgeItem.lifeShare(unit.items().getStackInSlot(AirFiltrationUnitBlockEntity.ACTIVE_SLOT)) == 1.0f,
                    "The spare one is in use");
            helper.assertTrue(unit.items().getStackInSlot(AirFiltrationUnitBlockEntity.SPARE_SLOT).isEmpty(), "No spare left");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void hoppersBringCartridgesAndTakeSpentOnes(GameTestHelper helper) {
        ShelterGameTests.box(helper, Blocks.STONE);
        helper.runAfterDelay(2, () -> {
            var automation = unit(helper).automation();
            ItemStack cartridges = new ItemStack(ShelterRegistry.FILTER_CARTRIDGE.get(), 4);
            helper.assertTrue(automation.insertItem(AirFiltrationUnitBlockEntity.SPENT_SLOT, cartridges.copy(), false).getCount() == 4,
                    "Nothing goes into the spent slot");
            helper.assertTrue(automation.insertItem(AirFiltrationUnitBlockEntity.SPARE_SLOT, new ItemStack(Items.DIRT), false).getCount() == 1,
                    "Only cartridges go in");
            helper.assertTrue(automation.insertItem(AirFiltrationUnitBlockEntity.SPARE_SLOT, cartridges.copy(), false).isEmpty(), "Cartridges go in");
            helper.assertTrue(automation.extractItem(AirFiltrationUnitBlockEntity.SPARE_SLOT, 1, false).isEmpty(), "Fresh ones are not taken out");
            unit(helper).items().setStackInSlot(AirFiltrationUnitBlockEntity.SPENT_SLOT, new ItemStack(ShelterRegistry.SPENT_FILTER_CARTRIDGE.get(), 3));
            helper.assertTrue(automation.extractItem(AirFiltrationUnitBlockEntity.SPENT_SLOT, 3, false).getCount() == 3, "Spent ones are taken out");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void energyGrowsWithTheRoom(GameTestHelper helper) {
        ShelterGameTests.box(helper, Blocks.STONE);
        helper.runAfterDelay(2, () -> {
            scan(helper);
            int small = unit(helper).energyPerTick();
            helper.assertTrue(small == RebirthConfig.SHELTER_ENERGY_BASE.get(), "A room of 26 blocks costs the base: " + small);
            int large = RebirthConfig.SHELTER_ENERGY_BASE.get() + RebirthConfig.SHELTER_ENERGY_PER_THOUSAND.get() * 4096 / 1000;
            helper.assertTrue(large > small * 5, "A room of 4096 blocks costs much more: " + large);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void cartridgesAndEnergyAreSaved(GameTestHelper helper) {
        ShelterGameTests.box(helper, Blocks.STONE);
        helper.runAfterDelay(2, () -> {
            AirFiltrationUnitBlockEntity unit = unit(helper);
            ItemStack worn = new ItemStack(ShelterRegistry.FILTER_CARTRIDGE.get());
            CartridgeItem.setLife(worn, 1234.0f);
            unit.items().setStackInSlot(AirFiltrationUnitBlockEntity.ACTIVE_SLOT, worn);
            unit.items().setStackInSlot(AirFiltrationUnitBlockEntity.SPENT_SLOT, new ItemStack(ShelterRegistry.SPENT_FILTER_CARTRIDGE.get(), 2));
            unit.energy().generate(5000);
            CompoundTag saved = unit.saveWithoutMetadata(helper.getLevel().registryAccess());
            AirFiltrationUnitBlockEntity copy = new AirFiltrationUnitBlockEntity(BlockPos.ZERO, unit.getBlockState());
            copy.loadWithComponents(saved, helper.getLevel().registryAccess());
            helper.assertTrue(Math.abs(CartridgeItem.life(copy.items().getStackInSlot(AirFiltrationUnitBlockEntity.ACTIVE_SLOT)) - 1234.0f) < EPSILON,
                    "The worn cartridge keeps its life");
            helper.assertTrue(copy.items().getStackInSlot(AirFiltrationUnitBlockEntity.SPENT_SLOT).getCount() == 2, "Spent ones are kept");
            helper.assertTrue(copy.energy().getEnergyStored() == 5000, "Energy is kept");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void theRecipesAreThere(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        helper.assertTrue(recipes.byKey(ProjectRebirth.id("filter_cartridge")).isPresent(), "A new cartridge on the crafting table");
        helper.assertTrue(recipes.byKey(ProjectRebirth.id("filter_cartridge_refill")).isPresent(), "A spent one refilled at the assembly station");
        helper.assertTrue(recipes.byKey(ProjectRebirth.id("air_filtration_unit")).isPresent(), "The unit");
        helper.succeed();
    }
}
