package com.projectrebirth.shelter;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.Exposure;
import com.projectrebirth.environment.Hazard;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One room of air around one or more air filtration units, as the last scan found it, and the pollution of its air. Kept in
 * the server's memory only; the units save the pollution and the room is scanned anew after a restart.
 */
public final class Room {
    private final RoomScan.Leak leak;
    private final BlockPos leakPos;
    private final LongOpenHashSet air;
    private final Long2ObjectOpenHashMap<BlockState> border;
    private final LongOpenHashSet units;
    private final int partialLeaks;
    /** Pollution of the air inside, in the units of {@link Hazard#POLLUTION}. */
    private float pollution;
    long scannedAt;
    boolean dirty;

    Room(RoomScan.Leak leak, BlockPos leakPos, LongOpenHashSet air, Long2ObjectOpenHashMap<BlockState> border, LongOpenHashSet units,
            int partialLeaks) {
        this.leak = leak;
        this.leakPos = leakPos;
        this.air = air;
        this.border = border;
        this.units = units;
        this.partialLeaks = partialLeaks;
    }

    public boolean isSealed() {
        return leak == RoomScan.Leak.NONE;
    }

    public RoomScan.Leak leak() {
        return leak;
    }

    /** Where the air got out, if it did. */
    public BlockPos leakPos() {
        return leakPos;
    }

    public int volume() {
        return air.size();
    }

    public LongSet air() {
        return air;
    }

    Long2ObjectMap<BlockState> border() {
        return border;
    }

    public LongSet units() {
        return units;
    }

    /** Closed iron and copper doors and hatches on the border. */
    public int partialLeaks() {
        return partialLeaks;
    }

    public float pollution() {
        return pollution;
    }

    public void setPollution(float pollution) {
        this.pollution = Math.max(0.0f, pollution);
    }

    public boolean contains(BlockPos pos) {
        return air.contains(pos.asLong());
    }

    /**
     * One second of the air: it moves towards the air around the room (slowly through the walls, a little more for each
     * partial door, fast through a leak), and the units clean it. Public for tests.
     *
     * @param outside pollution of the air around, as it would be here without the room
     * @param cleaningUnits units that clean now; a unit that worked part of the second counts by that part
     * @return pollution the cleaning took out of each block of air
     */
    public float airSecond(float outside, float cleaningUnits) {
        double drift = isSealed()
                ? RebirthConfig.SHELTER_SEALED_DRIFT.get() + partialLeaks * RebirthConfig.SHELTER_PARTIAL_DOOR_LEAK.get()
                : RebirthConfig.SHELTER_LEAK_DRIFT.get();
        float next = pollution + (outside - pollution) * (float) Math.min(1.0, drift);
        float removed = 0.0f;
        if (isSealed() && volume() > 0 && cleaningUnits > 0.0f) {
            double cleaned = cleaningUnits * RebirthConfig.SHELTER_CLEAN_AIR_PER_SECOND.get() / volume();
            float after = next * (float) (1.0 - Math.min(1.0, cleaned));
            removed = Math.max(0.0f, next) - Math.max(0.0f, after);
            next = after;
        }
        setPollution(next);
        return removed;
    }

    /** What a sealed room makes of the environment inside it: its own air, weaker radiation, no acid. */
    public Exposure inside(Exposure around) {
        return Exposure.builder()
                .add(Hazard.POLLUTION, pollution)
                .add(Hazard.RADIATION, around.get(Hazard.RADIATION) * RebirthConfig.SHELTER_SEALED_RADIATION.get().floatValue())
                .build();
    }
}
