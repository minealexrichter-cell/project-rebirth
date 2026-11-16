package com.projectrebirth.gametest;

import java.util.List;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.ContaminationMap;
import com.projectrebirth.monitoring.MonitoringRegistry;
import com.projectrebirth.monitoring.MonitoringStationBlockEntity;
import com.projectrebirth.monitoring.Snapshot;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The environmental monitoring station (1.6.0, block E): it scans the contaminated chunks around it, keeps the peaks by day,
 * gives the comparator the contamination near it, needs energy, and prints a map that keeps the scan.
 */
@GameTestHolder(PlanTests.SCOUTING)
@PrefixGameTestTemplate(false)
public class MonitoringGameTests {
    private static final String TEMPLATE = "floor_5x5";

    private static MonitoringStationBlockEntity station(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, MonitoringRegistry.MONITORING_STATION.get());
        return (MonitoringStationBlockEntity) helper.getBlockEntity(pos);
    }

    @GameTest(template = TEMPLATE, batch = "scouting_monitoring_scan")
    public static void theStationScansTheChunksAroundIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ContaminationMap map = ContaminationMap.get(level);
        map.clearAll();
        MonitoringStationBlockEntity station = station(helper);
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        try {
            map.addSpot(at, 0, 5.0f, 0.0f, level.getGameTime());
            map.addSpot(at.offset(16 * 3, 0, -16 * 2), 0, 0.0f, 2.0f, level.getGameTime());
            station.scan(level);
            Snapshot snapshot = station.snapshot();
            helper.assertTrue(snapshot != null && snapshot.radius() == RebirthConfig.MONITORING_RADIUS.get(), "A scan of the configured square");
            helper.assertTrue(Math.abs(snapshot.radiationAt(0, 0) - 5.0f) < 0.11f && snapshot.toxinAt(0, 0) == 0.0f, "Its own chunk: " + snapshot.radiationAt(0, 0));
            helper.assertTrue(Math.abs(snapshot.toxinAt(3, -2) - 2.0f) < 0.11f, "The toxic chunk away from it: " + snapshot.toxinAt(3, -2));
            helper.assertTrue(snapshot.radiationAt(1, 1) == 0.0f, "A clean chunk");
            helper.assertTrue(snapshot.markers().stream().anyMatch(m -> m.kind() == Snapshot.STATION && m.x() == at.getX() && m.z() == at.getZ()), "The station marked");
            int expected = Mth.clamp(Mth.ceil(15.0f * 5.0f / RebirthConfig.MONITORING_COMPARATOR_FULL.get().floatValue()), 1, 15);
            helper.assertTrue(station.comparator() == expected, "Comparator " + station.comparator() + ", expected " + expected);
            helper.assertTrue(station.history().size() == 1 && station.history().get(0).radiation() >= 4.9f && station.history().get(0).toxin() >= 1.9f,
                    "Today's peaks");
            station.scan(level);
            helper.assertTrue(station.history().size() == 1, "The same day stays one entry");
        } finally {
            map.clearAll();
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "scouting_monitoring_print", timeoutTicks = 100)
    public static void theStationPrintsAMapThatKeepsTheScan(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ContaminationMap.get(level).clearAll();
        MonitoringStationBlockEntity station = station(helper);
        station.items().setStackInSlot(MonitoringStationBlockEntity.PAPER_SLOT, new ItemStack(Items.PAPER, 2));
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(station.state() == MonitoringStationBlockEntity.STATE_NO_ENERGY && !station.print(), "Without energy it neither scans nor prints");
            station.energy().load(station.energy().getMaxEnergyStored());
        });
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(station.state() == MonitoringStationBlockEntity.STATE_WORKING && station.snapshot() != null, "With energy it works");
            helper.assertTrue(station.print(), "It prints");
            ItemStack map = station.items().getStackInSlot(MonitoringStationBlockEntity.MAP_SLOT);
            Snapshot kept = map.get(MonitoringRegistry.SNAPSHOT.get());
            helper.assertTrue(map.is(MonitoringRegistry.CONTAMINATION_MAP.get()) && kept != null && kept.centerX() == station.snapshot().centerX(), "A map of the scan");
            helper.assertTrue(kept.markers().stream().noneMatch(m -> m.kind() == Snapshot.PLAYER), "Without the players");
            helper.assertTrue(station.items().getStackInSlot(MonitoringStationBlockEntity.PAPER_SLOT).getCount() == 1, "One sheet used");
            helper.assertFalse(station.print(), "Not while the printed map is still in its slot");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "scouting_monitoring_codec")
    public static void aSnapshotKeepsItsDataWhenSavedAndSent(GameTestHelper helper) {
        byte[] radiation = new byte[9], toxin = new byte[9];
        radiation[4] = 50;
        toxin[0] = (byte) 200;
        Snapshot snapshot = new Snapshot(3, -7, 1, radiation, toxin, 12L, List.of(new Snapshot.Marker(Snapshot.PLAYER, 40, -100, "Tester")));
        Tag saved = Snapshot.CODEC.encodeStart(NbtOps.INSTANCE, snapshot).getOrThrow();
        Snapshot loaded = Snapshot.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        Snapshot.STREAM_CODEC.encode(buffer, snapshot);
        Snapshot sent = Snapshot.STREAM_CODEC.decode(buffer);
        for (Snapshot copy : new Snapshot[] {loaded, sent}) {
            helper.assertTrue(copy.centerX() == 3 && copy.centerZ() == -7 && copy.day() == 12L && copy.markers().equals(snapshot.markers()), "Fields kept");
            helper.assertTrue(copy.radiationAt(0, 0) == 5.0f && copy.toxinAt(-1, -1) == 20.0f, "Values kept");
        }
        helper.succeed();
    }
}
