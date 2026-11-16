package com.projectrebirth.gametest;

import java.util.Optional;

import com.projectrebirth.bodies.BodyAttachments;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.PossessionState;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.ContaminationMap;
import com.projectrebirth.environment.RadiationSources;
import com.projectrebirth.environment.SourceKind;
import com.projectrebirth.monitoring.Snapshot;
import com.projectrebirth.powerarmor.ArmorVariant;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.powerarmor.PowerArmorRegistry;
import com.projectrebirth.scouting.ScoutPayload;
import com.projectrebirth.scouting.Scouting;
import com.projectrebirth.upgrades.ArmorSlot;
import com.projectrebirth.upgrades.InstalledUpgrades;
import com.projectrebirth.upgrades.UpgradeRegistry;
import com.projectrebirth.upgrades.Upgrades;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Scouting in the field (1.6.0, block F): the analyzer reads the ground and the sources; the sensor module's reach grows with the
 * body class; the sensor unit of power armor works for its pilot and marks the source blocks.
 */
@GameTestHolder(PlanTests.SCOUTING)
@PrefixGameTestTemplate(false)
public class ScoutingGameTests {
    private static final String TEMPLATE = "floor_5x5";
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        BlockPos at = helper.absolutePos(POS);
        player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return player;
    }

    @GameTest(template = TEMPLATE, batch = "scouting_field_analyzer")
    public static void theAnalyzerReadsTheGroundAround(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ContaminationMap map = ContaminationMap.get(level);
        map.clearAll();
        ServerPlayer player = player(helper);
        try {
            helper.assertTrue(Scouting.read(player) == null, "Nothing without a tool");
            map.addSpot(player.blockPosition(), 0, 4.0f, 0.0f, level.getGameTime());
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Scouting.FIELD_ANALYZER.get()));
            ScoutPayload reading = Scouting.read(player);
            helper.assertTrue(reading != null && reading.analyzer() && !reading.sensor(), "The analyzer reads, no sensor");
            helper.assertTrue(reading.snapshot().radius() == RebirthConfig.ANALYZER_RADIUS.get(), "Its small grid");
            helper.assertTrue(Math.abs(reading.snapshot().radiationAt(0, 0) - 4.0f) < 0.11f, "The ground here: " + reading.snapshot().radiationAt(0, 0));
            helper.assertTrue(reading.snapshot().markers().stream().noneMatch(m -> m.kind() == Snapshot.SOURCE || m.kind() == Snapshot.STRUCTURE),
                    "The analyzer marks nothing");
        } finally {
            map.clearAll();
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "scouting_field_body")
    public static void theSensorModuleReachesFurtherOnBetterBodies(GameTestHelper helper) {
        int previous = 0;
        for (BodyClass bodyClass : BodyClass.values()) {
            ServerPlayer player = player(helper);
            player.setData(BodyAttachments.POSSESSION, PossessionState.of(bodyClass));
            helper.assertTrue(Scouting.sensorRange(player) == 0, bodyClass + ": no module, no sensor");
            player.setData(UpgradeRegistry.CONTROLLED_BODY, InstalledUpgrades.EMPTY.with(
                    new InstalledUpgrades.Installed(Optional.empty(), new ItemStack(UpgradeRegistry.SENSOR_MODULE.get()))));
            int range = Scouting.sensorRange(player);
            helper.assertTrue(range == RebirthConfig.BODY_SENSOR_RANGE.get(bodyClass).get() && range > previous,
                    bodyClass + " reaches " + range + ", the class before " + previous);
            previous = range;
            ScoutPayload reading = Scouting.read(player);
            helper.assertTrue(reading != null && reading.sensor() && reading.snapshot().radius() == range, "The map of its reach");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "scouting_field_armor")
    public static void theSensorUnitWorksForThePilotAndMarksSources(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PowerArmorEntity armor = helper.spawn(PowerArmorRegistry.POWER_ARMOR.get(), POS);
        armor.setVariant(ArmorVariant.COMBAT);
        helper.assertTrue(Upgrades.install(level.registryAccess(), armor, new ItemStack(UpgradeRegistry.SENSOR_UNIT.get()), ArmorSlot.HEAD) == null,
                "The unit goes on the helmet");
        ServerPlayer pilot = player(helper);
        pilot.startRiding(armor, true);
        BlockPos source = helper.absolutePos(new BlockPos(0, 2, 0));
        RadiationSources.register(level, source, SourceKind.values()[0]);
        try {
            helper.assertTrue(Scouting.sensorRange(pilot) == RebirthConfig.ARMOR_SENSOR_RANGE.get(), "The armor's reach");
            ScoutPayload reading = Scouting.read(pilot);
            helper.assertTrue(reading != null && reading.sensor(), "The sensor reads");
            helper.assertTrue(reading.snapshot().markers().stream().anyMatch(m -> m.kind() == Snapshot.SOURCE && m.x() == source.getX() && m.z() == source.getZ()),
                    "The source block is marked");
        } finally {
            RadiationSources.unregister(level, source);
        }
        helper.succeed();
    }
}
