package com.projectrebirth.gametest;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.environment.EnvironmentStatusPayload;
import com.projectrebirth.shelter.AirFiltrationUnitBlockEntity;
import com.projectrebirth.shelter.Room;
import com.projectrebirth.shelter.ShelterAlerts;
import com.projectrebirth.shelter.ShelterControlPanelBlockEntity;
import com.projectrebirth.shelter.ShelterRegistry;
import com.projectrebirth.shelter.Shelters;
import com.projectrebirth.technology.TechBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Block E of plan 1.4: the shelter control panel, its redstone signal, the shelter state for the HUD and who hears the
 * warnings. The sealed box of {@link ShelterGameTests}; its unit reaches the panel outside through a cable in the wall.
 */
@GameTestHolder(PlanTests.SHELTER)
@PrefixGameTestTemplate(false)
public class ShelterPanelGameTests {
    private static final String TEMPLATE = "floor_9x9";
    /** The cable in the east wall next to the unit (4, 2, 4), the panel outside it. */
    private static final BlockPos CABLE = new BlockPos(5, 2, 4);
    private static final BlockPos PANEL = new BlockPos(7, 2, 4);

    private static ShelterControlPanelBlockEntity build(GameTestHelper helper) {
        ShelterGameTests.box(helper, Blocks.STONE);
        // the unit stands in the middle of the room; a cable from it through the east wall to the panel
        helper.setBlock(CABLE, TechBlocks.CABLE.get());
        helper.setBlock(new BlockPos(6, 2, 4), TechBlocks.CABLE.get());
        helper.setBlock(PANEL, ShelterRegistry.SHELTER_CONTROL_PANEL.get());
        return helper.getBlockEntity(PANEL);
    }

    private static AirFiltrationUnitBlockEntity unit(GameTestHelper helper) {
        return helper.getBlockEntity(ShelterGameTests.UNIT);
    }

    private static Room scan(GameTestHelper helper) {
        return Shelters.of(helper.getLevel()).scanNow(helper.absolutePos(ShelterGameTests.UNIT));
    }

    @GameTest(template = TEMPLATE, skyAccess = true, timeoutTicks = 200)
    public static void thePanelSeesTheRoomAndItsSupplies(GameTestHelper helper) {
        ShelterControlPanelBlockEntity panel = build(helper);
        helper.runAfterDelay(2, () -> {
            Room room = scan(helper);
            helper.assertTrue(room.isSealed(), "The box is sealed with the cable in its wall");
            room.setPollution(0.0f);
            panel.update();
            helper.assertTrue(panel.rooms().size() == 1, "The panel watches the room of the unit on its cable");
            helper.assertTrue(panel.danger() == ShelterControlPanelBlockEntity.DANGER_NO_SUPPLY, "No cartridge: danger " + panel.danger());
            unit(helper).items().setStackInSlot(AirFiltrationUnitBlockEntity.ACTIVE_SLOT, new ItemStack(ShelterRegistry.FILTER_CARTRIDGE.get()));
            unit(helper).energy().generate(unit(helper).energy().getMaxEnergyStored());
            helper.runAfterDelay(5, () -> {
                panel.update();
                helper.assertTrue(panel.danger() == 0, "All well: danger " + panel.danger());
                BlockState state = helper.getLevel().getBlockState(helper.absolutePos(PANEL));
                helper.assertTrue(state.getSignal(helper.getLevel(), helper.absolutePos(PANEL), Direction.UP) == 0, "No signal when all is well");
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void aLeakGivesTheFullSignalAndIsFound(GameTestHelper helper) {
        ShelterControlPanelBlockEntity panel = build(helper);
        helper.setBlock(ShelterGameTests.WALL, Blocks.AIR);
        helper.runAfterDelay(2, () -> {
            scan(helper);
            panel.update();
            helper.assertTrue(panel.danger() == ShelterControlPanelBlockEntity.DANGER_LEAK, "A leak: danger " + panel.danger());
            BlockPos at = helper.absolutePos(PANEL);
            BlockState state = helper.getLevel().getBlockState(at);
            helper.assertTrue(state.getSignal(helper.getLevel(), at, Direction.UP) == 15, "Redstone signal 15");
            helper.assertTrue(state.getAnalogOutputSignal(helper.getLevel(), at) == 15, "Comparator 15");
            helper.assertTrue(!panel.leaks().isEmpty(), "It knows where the air gets out");
            BlockPos leak = panel.leaks().get(0);
            helper.assertTrue(leak.closerThan(helper.absolutePos(ShelterGameTests.WALL), 2.5), "Next to the hole: " + leak);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void theHudIsToldWhereThePlayerIs(GameTestHelper helper) {
        ShelterGameTests.box(helper, Blocks.STONE);
        helper.runAfterDelay(2, () -> {
            Room room = scan(helper);
            BlockPos inside = helper.absolutePos(new BlockPos(4, 3, 4));
            room.setPollution(0.2f);
            helper.assertTrue(Shelters.stateAt(helper.getLevel(), inside) == EnvironmentStatusPayload.SHELTER_CLEAN, "Clean air");
            room.setPollution(5.0f);
            helper.assertTrue(Shelters.stateAt(helper.getLevel(), inside) == EnvironmentStatusPayload.SHELTER_POLLUTED, "Polluted air");
            helper.assertTrue(Shelters.stateAt(helper.getLevel(), helper.absolutePos(new BlockPos(0, 3, 0))) == EnvironmentStatusPayload.SHELTER_NONE,
                    "Outside: no shelter");
            helper.setBlock(ShelterGameTests.WALL, Blocks.AIR);
            scan(helper);
            helper.assertTrue(Shelters.stateAt(helper.getLevel(), inside) == EnvironmentStatusPayload.SHELTER_LEAK, "A leaking shelter");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void theOwnerHearsTheWarnings(GameTestHelper helper) {
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(ShelterAlerts.recipients(helper.getLevel().getServer(), null).isEmpty(), "Nobody for a unit placed by no one");
        helper.assertTrue(ShelterAlerts.recipients(helper.getLevel().getServer(), owner.getUUID()).contains(owner), "The owner, while online");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, skyAccess = true)
    public static void thePanelIsProtectedAndHasARecipe(GameTestHelper helper) {
        helper.setBlock(PANEL, ShelterRegistry.SHELTER_CONTROL_PANEL.get());
        helper.assertTrue(com.projectrebirth.factions.FactionEvents.useOf(helper.getLevel(), helper.absolutePos(PANEL))
                == com.projectrebirth.factions.FactionService.Use.CONTAINER, "A machine");
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(ProjectRebirth.id("shelter_control_panel")).isPresent(), "Recipe");
        helper.succeed();
    }
}
