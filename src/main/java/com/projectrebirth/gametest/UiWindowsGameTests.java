package com.projectrebirth.gametest;

import java.util.List;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyEntities;
import com.projectrebirth.bodies.BodyRegistry;
import com.projectrebirth.bodies.CapsuleNetwork;
import com.projectrebirth.shelter.ShelterRegistry;
import com.projectrebirth.technology.TechBlocks;
import com.projectrebirth.technology.energy.EnergyPanelMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Version 1.8.0, block B: what the new windows need from the server. The solar panel, the radioisotope generator, the
 * battery and the backup battery open a window of their kind (before, they only wrote a line in the action bar); the
 * capsule's rows carry the charge and wear of a body standing in a loaded place (unknown, -1, otherwise).
 */
@GameTestHolder(PlanTests.INTERFACE)
@PrefixGameTestTemplate(false)
public class UiWindowsGameTests {
    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos standAt = helper.absolutePos(new BlockPos(1, 2, 1));
        player.moveTo(standAt.getX() + 0.5, standAt.getY(), standAt.getZ() + 0.5);
        return player;
    }

    private static void use(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    @GameTest(template = "floor_3x3")
    public static void energyBlocksOpenTheirWindows(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        List<Block> blocks = List.of(TechBlocks.SOLAR_PANEL.get(), TechBlocks.RADIOISOTOPE_GENERATOR.get(), TechBlocks.BATTERY.get(),
                ShelterRegistry.BACKUP_BATTERY.get());
        List<EnergyPanelMenu.Kind> kinds = List.of(EnergyPanelMenu.Kind.SOLAR, EnergyPanelMenu.Kind.ISOTOPE, EnergyPanelMenu.Kind.BATTERY,
                EnergyPanelMenu.Kind.BACKUP);
        for (int i = 0; i < blocks.size(); i++) {
            BlockPos at = new BlockPos(i % 2 * 2, 2, i / 2 * 2);
            helper.setBlock(at, blocks.get(i));
            // the mock player cannot receive the window, so the test asks the same provider the block's click uses
            var provider = EnergyPanelMenu.providerFor(helper.getLevel(), helper.absolutePos(at));
            helper.assertTrue(provider != null, blocks.get(i) + " has the energy window");
            helper.assertTrue(provider.createMenu(0, player.getInventory(), player) instanceof EnergyPanelMenu, blocks.get(i) + " opens the energy window");
            EnergyPanelMenu menu = (EnergyPanelMenu) provider.createMenu(0, player.getInventory(), player);
            helper.assertTrue(menu.kind() == kinds.get(i), blocks.get(i) + " opens the window of its kind, got " + menu.kind());
            helper.assertTrue(menu.energyCapacity() > 0, blocks.get(i) + " tells its capacity");
            helper.assertTrue(menu.stillValid(player), "the window stays open next to the block");
        }
        helper.succeed();
    }

    /**
     * 1.8.0 block D: the heater, the decontamination chamber and the radiation purge station open the same window as the
     * energy blocks (before, they only wrote a line in the action bar), each of its kind, with what it tells: the heater
     * its thermostat, the purge station why it would not purge this person (no dose here).
     */
    @GameTest(template = "floor_3x3")
    public static void shelterDevicesOpenTheirWindows(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        List<Block> blocks = List.of(com.projectrebirth.environment.TemperatureRegistry.HEATER.get(), ShelterRegistry.DECONTAMINATION_CHAMBER.get(),
                ShelterRegistry.RADIATION_PURGE_STATION.get());
        List<EnergyPanelMenu.Kind> kinds = List.of(EnergyPanelMenu.Kind.HEATER, EnergyPanelMenu.Kind.DECON, EnergyPanelMenu.Kind.PURGE);
        for (int i = 0; i < blocks.size(); i++) {
            BlockPos at = new BlockPos(i * 2 % 4, 2, i / 2 * 2);
            helper.setBlock(at, blocks.get(i));
            var provider = EnergyPanelMenu.providerFor(helper.getLevel(), helper.absolutePos(at), player);
            helper.assertTrue(provider != null, blocks.get(i) + " has the device window");
            EnergyPanelMenu menu = (EnergyPanelMenu) provider.createMenu(0, player.getInventory(), player);
            helper.assertTrue(menu.kind() == kinds.get(i), blocks.get(i) + " opens the window of its kind, got " + menu.kind());
            helper.assertTrue(menu.energyCapacity() > 0, blocks.get(i) + " tells its capacity");
            if (kinds.get(i) == EnergyPanelMenu.Kind.HEATER) {
                int thermostat = (int) Math.round(com.projectrebirth.config.RebirthConfig.HEATER_THERMOSTAT.get() * 10);
                helper.assertTrue(menu.extra2() == thermostat, "the heater tells its thermostat, got " + menu.extra2());
            }
            if (kinds.get(i) == EnergyPanelMenu.Kind.PURGE) {
                helper.assertTrue(menu.state() == com.projectrebirth.shelter.RadiationPurgeStationBlockEntity.Reason.NO_DOSE.ordinal(),
                        "the purge station tells it has nothing to purge, got " + menu.state());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "floor_5x5")
    public static void capsuleRowsCarryChargeAndWear(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ArtificialBodyEntity body = helper.spawn(BodyEntities.type(BodyClass.BASIC_ROBOT), new BlockPos(3, 2, 3));
        body.setOwner(player.getUUID());
        body.setCharge(body.charge() / 2.0f);
        BodyRegistry.get(player.server).updateStanding(body);
        List<CapsuleNetwork.Entry> entries = CapsuleNetwork.entriesFor(player, helper.getLevel(), helper.absolutePos(new BlockPos(0, 2, 0)));
        CapsuleNetwork.Entry entry = entries.stream().filter(e -> e.id().equals(body.getUUID())).findFirst().orElse(null);
        helper.assertTrue(entry != null, "the body is in the capsule's list");
        helper.assertTrue(entry.charge() > 0.4f && entry.charge() < 0.6f, "the row tells the charge of a loaded body, got " + entry.charge());
        helper.assertTrue(entry.wear() >= 0.0f, "the row tells the wear of a loaded body");
        helper.succeed();
    }

    /** 1.8.0 block C: the helmet's compass gets the pilot's own standing bodies of this dimension, and nobody else's. */
    @GameTest(template = "floor_5x5")
    public static void helmetMarksShowOwnStandingBodies(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ArtificialBodyEntity own = helper.spawn(BodyEntities.type(BodyClass.BASIC_ROBOT), new BlockPos(3, 2, 3));
        own.setOwner(player.getUUID());
        BodyRegistry.get(player.server).updateStanding(own);
        ArtificialBodyEntity other = helper.spawn(BodyEntities.type(BodyClass.BASIC_ROBOT), new BlockPos(1, 2, 3));
        other.setOwner(java.util.UUID.randomUUID());
        BodyRegistry.get(player.server).updateStanding(other);
        var marks = com.projectrebirth.powerarmor.HelmetMarks.marksFor(player);
        helper.assertTrue(marks.stream().anyMatch(m -> m.x() == own.getBlockX() && m.z() == own.getBlockZ()), "the own body is marked");
        helper.assertTrue(marks.stream().noneMatch(m -> m.x() == other.getBlockX() && m.z() == other.getBlockZ()), "another's body is not marked");
        helper.succeed();
    }
}
