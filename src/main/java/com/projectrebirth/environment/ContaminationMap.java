package com.projectrebirth.environment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The residual contamination of one dimension (1.6.0), saved with the world. It is kept per chunk and only where there is some;
 * a world from 1.5.2 has none, which is clean. How much there is now is counted from the time that has passed when a chunk is
 * read (a half-life each for radiation and toxins), never by going over all chunks; a chunk that has faded is forgotten when it is
 * read. Everything here is plain data on the server: the effect on the air is added in {@link Environment}.
 */
public class ContaminationMap extends SavedData {
    private static final String NAME = "projectrebirth_contamination";
    /** Ticks in one game day. */
    public static final double TICKS_PER_DAY = 24000.0;

    /** What a chunk holds at a moment: radiation and polluted air at the same units as the hazards. */
    public record Reading(float radiation, float toxin) {
        public static final Reading NONE = new Reading(0.0f, 0.0f);

        public boolean isNone() {
            return radiation <= 0.0f && toxin <= 0.0f;
        }
    }

    /** A chunk of the list command. */
    public record Spot(int chunkX, int chunkZ, float radiation, float toxin) {
    }

    private static final class Entry {
        float radiation;
        float toxin;
        /** The most either one reached, for the list. */
        float peak;
        /** The game time the two numbers are for. */
        long time;
    }

    private final Map<Long, Entry> chunks = new HashMap<>();

    public static ContaminationMap get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(ContaminationMap::new, ContaminationMap::load, null), NAME);
    }

    public int size() {
        return chunks.size();
    }

    /** How much is left after the time, with the given half-life in game days. Public for tests. */
    public static float decayed(float amount, long elapsedTicks, double halfLifeDays) {
        if (amount <= 0.0f || elapsedTicks <= 0L) {
            return Math.max(0.0f, amount);
        }
        return (float) (amount * Math.pow(0.5, elapsedTicks / (halfLifeDays * TICKS_PER_DAY)));
    }

    private static float radiationNow(Entry entry, long now) {
        return decayed(entry.radiation, now - entry.time, RebirthConfig.ZONES_RADIATION_HALF_LIFE_DAYS.get());
    }

    private static float toxinNow(Entry entry, long now) {
        return decayed(entry.toxin, now - entry.time, RebirthConfig.ZONES_TOXIC_HALF_LIFE_DAYS.get());
    }

    /** What the chunk holds at that time; a chunk that has faded is forgotten here. Not switched by the config: that is the caller's. */
    public Reading readingAt(int chunkX, int chunkZ, long now) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        Entry entry = chunks.get(key);
        if (entry == null) {
            return Reading.NONE;
        }
        float radiation = radiationNow(entry, now);
        float toxin = toxinNow(entry, now);
        float forget = RebirthConfig.ZONES_FORGET_BELOW.get().floatValue();
        if (radiation < forget && toxin < forget) {
            chunks.remove(key);
            setDirty();
            return Reading.NONE;
        }
        return new Reading(radiation, toxin);
    }

    public Reading readingAt(BlockPos pos, long now) {
        return readingAt(pos.getX() >> 4, pos.getZ() >> 4, now);
    }

    /**
     * One spot: the chunks within the radius (in chunks) of the centre chunk get the strength falling off with the distance from the
     * centre, from the full strength in the middle to nothing just outside the radius. Spots add up. The API for every source.
     */
    public void addSpot(BlockPos centre, int radiusChunks, float radiation, float toxin, long now) {
        int cx = centre.getX() >> 4;
        int cz = centre.getZ() >> 4;
        int radius = Math.max(0, radiusChunks);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                float distance = Mth.sqrt(dx * dx + dz * dz);
                if (distance > radius) {
                    continue;
                }
                float share = 1.0f - distance / (radius + 1.0f);
                addToChunk(cx + dx, cz + dz, radiation * share, toxin * share, now);
            }
        }
    }

    /** Adds to one chunk, after bringing what it holds up to that time. */
    public void addToChunk(int chunkX, int chunkZ, float radiation, float toxin, long now) {
        if (radiation <= 0.0f && toxin <= 0.0f) {
            return;
        }
        long key = ChunkPos.asLong(chunkX, chunkZ);
        Entry entry = chunks.get(key);
        if (entry == null) {
            entry = new Entry();
            chunks.put(key, entry);
        } else {
            entry.radiation = radiationNow(entry, now);
            entry.toxin = toxinNow(entry, now);
        }
        float max = RebirthConfig.ZONES_MAX_PER_CHUNK.get().floatValue();
        entry.radiation = Math.min(max, entry.radiation + Math.max(0.0f, radiation));
        entry.toxin = Math.min(max, entry.toxin + Math.max(0.0f, toxin));
        entry.peak = Math.max(entry.peak, Math.max(entry.radiation, entry.toxin));
        entry.time = now;
        setDirty();
    }

    /** Forgets everything. */
    public int clearAll() {
        int count = chunks.size();
        if (count > 0) {
            chunks.clear();
            setDirty();
        }
        return count;
    }

    /** Forgets the chunks within the radius (in chunks, a square) of the chunk of the position. */
    public int clearAround(BlockPos centre, int radiusChunks) {
        int cx = centre.getX() >> 4;
        int cz = centre.getZ() >> 4;
        int removed = 0;
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                if (chunks.remove(ChunkPos.asLong(cx + dx, cz + dz)) != null) {
                    removed++;
                }
            }
        }
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    /** The contaminated chunks within the radius (in chunks, a square) of the position, now, the strongest first. */
    public List<Spot> near(BlockPos centre, int radiusChunks, long now) {
        int cx = centre.getX() >> 4;
        int cz = centre.getZ() >> 4;
        List<Spot> found = new ArrayList<>();
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                Reading reading = readingAt(cx + dx, cz + dz, now);
                if (!reading.isNone()) {
                    found.add(new Spot(cx + dx, cz + dz, reading.radiation(), reading.toxin()));
                }
            }
        }
        found.sort((a, b) -> Float.compare(b.radiation() + b.toxin(), a.radiation() + a.toxin()));
        return found;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        chunks.forEach((key, entry) -> {
            CompoundTag one = new CompoundTag();
            one.putInt("X", ChunkPos.getX(key));
            one.putInt("Z", ChunkPos.getZ(key));
            one.putFloat("Radiation", entry.radiation);
            one.putFloat("Toxin", entry.toxin);
            one.putFloat("Peak", entry.peak);
            one.putLong("Time", entry.time);
            list.add(one);
        });
        tag.put("Chunks", list);
        return tag;
    }

    public static ContaminationMap load(CompoundTag tag, HolderLookup.Provider registries) {
        ContaminationMap map = new ContaminationMap();
        for (Tag raw : tag.getList("Chunks", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) raw;
            Entry entry = new Entry();
            entry.radiation = one.getFloat("Radiation");
            entry.toxin = one.getFloat("Toxin");
            entry.peak = one.getFloat("Peak");
            entry.time = one.getLong("Time");
            map.chunks.put(ChunkPos.asLong(one.getInt("X"), one.getInt("Z")), entry);
        }
        return map;
    }
}
