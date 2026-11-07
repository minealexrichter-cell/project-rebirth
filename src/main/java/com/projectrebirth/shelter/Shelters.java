package com.projectrebirth.shelter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.Environment;
import com.projectrebirth.environment.Hazard;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The rooms of one level, on the server. Units ask for a scan when they load and when something on a room's border changes;
 * scans share a budget of blocks per tick, so a large base or many of them do not stall a tick. Once a second each room's
 * air moves and the border is compared with what the scan saw.
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class Shelters {
    private static final Map<ResourceKey<Level>, Shelters> BY_LEVEL = new HashMap<>();
    private static final int TICKS_PER_SECOND = 20;

    private final ServerLevel level;
    private final LongOpenHashSet units = new LongOpenHashSet();
    /** Pollution a unit had saved, for the first scan after it loads. */
    private final Long2FloatOpenHashMap savedPollution = new Long2FloatOpenHashMap();
    private final Long2ObjectOpenHashMap<Room> roomOfUnit = new Long2ObjectOpenHashMap<>();
    /** The air of sealed rooms only: a leaking room changes nothing for those inside it. */
    private final Long2ObjectOpenHashMap<Room> sealedAt = new Long2ObjectOpenHashMap<>();
    private final LongLinkedOpenHashSet waiting = new LongLinkedOpenHashSet();
    @Nullable
    private RoomScan scan;
    /** Ticks the last finished scan took, and blocks it went through: for the status command and the load test. */
    private int lastScanTicks;
    private int lastScanBlocks;
    private long scanStartedAt;
    private int scanBlocks;

    private Shelters(ServerLevel level) {
        this.level = level;
    }

    public static Shelters of(ServerLevel level) {
        return BY_LEVEL.computeIfAbsent(level.dimension(), key -> new Shelters(level));
    }

    /** The manager if this level has any unit; null is quick, for the environment's calculation everywhere. */
    @Nullable
    public static Shelters find(ServerLevel level) {
        Shelters found = BY_LEVEL.get(level.dimension());
        return found != null && found.level == level ? found : null;
    }

    /** The sealed room around a place, if any. */
    @Nullable
    public static Room sealedRoomAt(ServerLevel level, BlockPos pos) {
        Shelters shelters = find(level);
        return shelters == null || shelters.sealedAt.isEmpty() ? null : shelters.sealedAt.get(pos.asLong());
    }

    @Nullable
    public Room roomOf(BlockPos unit) {
        return roomOfUnit.get(unit.asLong());
    }

    public boolean isScanning(BlockPos unit) {
        long key = unit.asLong();
        return waiting.contains(key) || scan != null && scan.origin() == key;
    }

    public int lastScanTicks() {
        return lastScanTicks;
    }

    public int lastScanBlocks() {
        return lastScanBlocks;
    }

    void addUnit(BlockPos pos, float pollution) {
        long key = pos.asLong();
        units.add(key);
        if (!Float.isNaN(pollution)) {
            savedPollution.put(key, pollution);
        }
        // a unit placed into a room joins it: that room is scanned again from its own start
        Room around = null;
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            Room room = sealedAt.get(pos.relative(direction).asLong());
            if (room != null) {
                around = room;
                break;
            }
        }
        if (around != null) {
            around.dirty = true;
        }
        waiting.add(key);
    }

    void removeUnit(BlockPos pos) {
        long key = pos.asLong();
        units.remove(key);
        savedPollution.remove(key);
        waiting.remove(key);
        if (scan != null && scan.origin() == key) {
            scan = null;
        }
        Room room = roomOfUnit.remove(key);
        if (room != null) {
            forget(room);
            for (LongIterator it = room.units().iterator(); it.hasNext(); ) {
                long other = it.nextLong();
                if (other != key && units.contains(other)) {
                    roomOfUnit.remove(other);
                    savedPollution.put(other, room.pollution());
                    waiting.add(other);
                }
            }
        }
    }

    /** Asks for a new scan of the room around a unit (a door opened, a block broken). */
    public void markDirty(BlockPos unit) {
        Room room = roomOfUnit.get(unit.asLong());
        if (room != null) {
            room.dirty = true;
        } else if (units.contains(unit.asLong())) {
            waiting.add(unit.asLong());
        }
    }

    private void forget(Room room) {
        if (room.isSealed()) {
            for (LongIterator it = room.air().iterator(); it.hasNext(); ) {
                long key = it.nextLong();
                if (sealedAt.get(key) == room) {
                    sealedAt.remove(key);
                }
            }
        }
    }

    private void tick() {
        int budget = RebirthConfig.SHELTER_SCAN_BLOCKS_PER_TICK.get();
        while (budget > 0) {
            if (scan == null) {
                if (waiting.isEmpty()) {
                    break;
                }
                long next = waiting.removeFirstLong();
                if (!units.contains(next)) {
                    continue;
                }
                scan = new RoomScan(BlockPos.of(next), RebirthConfig.SHELTER_MAX_VOLUME_PER_UNIT.get(), RebirthConfig.SHELTER_MAX_UNITS_PER_ROOM.get());
                scanStartedAt = level.getGameTime();
                scanBlocks = 0;
            }
            int used = scan.step(space(), budget);
            budget -= Math.max(1, used);
            scanBlocks += used;
            if (scan.isDone()) {
                finishScan(scan);
                scan = null;
            }
        }
        if (level.getGameTime() % TICKS_PER_SECOND == 0) {
            second();
        }
    }

    /** Runs the scan of one unit to its end at once. For tests and the status command. */
    public Room scanNow(BlockPos unit) {
        RoomScan now = new RoomScan(unit, RebirthConfig.SHELTER_MAX_VOLUME_PER_UNIT.get(), RebirthConfig.SHELTER_MAX_UNITS_PER_ROOM.get());
        long started = System.nanoTime();
        int blocks = 0;
        while (!now.isDone()) {
            blocks += now.step(space(), Integer.MAX_VALUE);
        }
        lastScanBlocks = blocks;
        lastScanTicks = 0;
        lastScanNanos = System.nanoTime() - started;
        if (scan != null && scan.origin() == unit.asLong()) {
            scan = null;
        }
        waiting.remove(unit.asLong());
        finishScan(now);
        return roomOfUnit.get(unit.asLong());
    }

    private long lastScanNanos;

    /** Time the last scan made by {@link #scanNow} took, in nanoseconds. */
    public long lastScanNanos() {
        return lastScanNanos;
    }

    private void finishScan(RoomScan done) {
        Room found = done.result();
        found.scannedAt = level.getGameTime();
        if (scan == done) {
            lastScanTicks = (int) (level.getGameTime() - scanStartedAt) + 1;
            lastScanBlocks = scanBlocks;
        }
        // the air of the new room is the air of the rooms it replaces, by their volume
        Set<Room> old = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        double weighted = 0.0;
        int volume = 0;
        float saved = Float.NaN;
        for (LongIterator it = found.units().iterator(); it.hasNext(); ) {
            long unit = it.nextLong();
            if (!units.contains(unit)) {
                it.remove(); // a unit block whose entity is not loaded yet; it will scan by itself
                continue;
            }
            Room before = roomOfUnit.get(unit);
            if (before != null && old.add(before)) {
                weighted += (double) before.pollution() * Math.max(1, before.volume());
                volume += Math.max(1, before.volume());
            }
            if (Float.isNaN(saved) && savedPollution.containsKey(unit)) {
                saved = savedPollution.get(unit);
            }
        }
        if (!units.contains(done.origin())) {
            return;
        }
        found.units().add(done.origin());
        float pollution = volume > 0 ? (float) (weighted / volume) : !Float.isNaN(saved) ? saved
                : Environment.ordinaryExposureAt(level, BlockPos.of(done.origin())).get(Hazard.POLLUTION);
        found.setPollution(pollution);
        for (Room room : old) {
            forget(room);
            for (LongIterator it = room.units().iterator(); it.hasNext(); ) {
                long unit = it.nextLong();
                if (!found.units().contains(unit) && units.contains(unit)) {
                    roomOfUnit.remove(unit);
                    savedPollution.put(unit, room.pollution());
                    waiting.add(unit); // cut off from this room now: a room of its own
                }
            }
        }
        for (LongIterator it = found.units().iterator(); it.hasNext(); ) {
            long unit = it.nextLong();
            roomOfUnit.put(unit, found);
            savedPollution.remove(unit);
            waiting.remove(unit);
        }
        if (found.isSealed()) {
            for (LongIterator it = found.air().iterator(); it.hasNext(); ) {
                sealedAt.put(it.nextLong(), found);
            }
        }
        tellUnits(found);
    }

    private void second() {
        if (roomOfUnit.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        int recheck = RebirthConfig.SHELTER_RECHECK_SECONDS.get() * TICKS_PER_SECOND;
        List<Room> rooms = new ArrayList<>();
        Set<Room> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Room room : roomOfUnit.values()) {
            if (seen.add(room)) {
                rooms.add(room);
            }
        }
        for (Room room : rooms) {
            long first = room.units().iterator().nextLong();
            if (!room.dirty && borderChanged(room)) {
                room.dirty = true;
            }
            boolean due = now - room.scannedAt >= recheck;
            if ((room.dirty && now - room.scannedAt >= TICKS_PER_SECOND || due) && !isScanning(BlockPos.of(first))) {
                waiting.add(first);
            }
            float outside = Environment.isEnabled() ? Environment.ordinaryExposureAt(level, BlockPos.of(first)).get(Hazard.POLLUTION) : 0.0f;
            room.airSecond(outside, room.units().size());
            tellUnits(room);
        }
    }

    /** Whether any block that kept the air in is not what it was (broken, opened, replaced). */
    private boolean borderChanged(Room room) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Long2ObjectMap.Entry<BlockState> entry : room.border().long2ObjectEntrySet()) {
            pos.set(entry.getLongKey());
            if (!level.isLoaded(pos) || level.getBlockState(pos) != entry.getValue()) {
                return true;
            }
        }
        return false;
    }

    private void tellUnits(Room room) {
        for (LongIterator it = room.units().iterator(); it.hasNext(); ) {
            BlockPos pos = BlockPos.of(it.nextLong());
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof AirFiltrationUnitBlockEntity unit) {
                unit.remember(room.pollution());
            }
        }
    }

    private RoomScan.Space space() {
        return new RoomScan.Space() {
            @Override
            public boolean loaded(BlockPos pos) {
                return level.isLoaded(pos);
            }

            @Override
            public BlockState state(BlockPos pos) {
                return level.getBlockState(pos);
            }

            @Override
            public boolean openSky(BlockPos pos) {
                return level.isOutsideBuildHeight(pos)
                        || level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) <= pos.getY();
            }

            @Override
            public boolean isUnit(BlockState state) {
                return state.getBlock() instanceof AirFiltrationUnitBlock;
            }
        };
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Shelters shelters = find(level);
            if (shelters != null) {
                shelters.tick();
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        BY_LEVEL.clear();
    }
}
