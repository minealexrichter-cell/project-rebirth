package com.projectrebirth.shelter;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The flood of air from one air filtration unit, a little at a time: {@link #step} goes on where it stopped. Air passes from
 * a block to its neighbour unless the side of either one seals ({@link Sealing}). The flood stops with a leak at the open sky,
 * at an unloaded chunk or past the largest volume of the units it found; otherwise the room is sealed. Other units the air touches join the room
 * and add to its largest volume.
 */
public final class RoomScan {
    /** What the scan reads from the world; a test can give its own. */
    public interface Space {
        boolean loaded(BlockPos pos);

        BlockState state(BlockPos pos);

        /** Nothing above blocks the sky, or the place is outside the height of the world. */
        boolean openSky(BlockPos pos);

        boolean isUnit(BlockState state);
    }

    public enum Leak {
        NONE, SKY, UNLOADED, TOO_LARGE;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private final long origin;
    private final int volumePerUnit;
    private final int maxUnits;
    private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
    private final LongOpenHashSet air = new LongOpenHashSet();
    /** The blocks whose sides keep the air in, as they were: a change of any of them means a new scan. */
    private final Long2ObjectOpenHashMap<BlockState> border = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet units = new LongOpenHashSet();
    private final LongOpenHashSet partialLeaks = new LongOpenHashSet();
    private Leak leak = Leak.NONE;
    private long leakPos;
    private boolean started;
    private boolean done;

    /**
     * @param maxUnits most units one room counts: the flood goes on to that many units' volume, since a unit far inside may
     *                 be found late, and only then decides whether the units found hold the room
     */
    public RoomScan(BlockPos origin, int volumePerUnit, int maxUnits) {
        this.origin = origin.asLong();
        this.volumePerUnit = volumePerUnit;
        this.maxUnits = Math.max(1, maxUnits);
        units.add(this.origin);
    }

    public long origin() {
        return origin;
    }

    public boolean isDone() {
        return done;
    }

    /** Works through at most {@code budget} blocks; returns the blocks used. */
    public int step(Space space, int budget) {
        if (done) {
            return 0;
        }
        int used = 0;
        BlockPos.MutableBlockPos from = new BlockPos.MutableBlockPos();
        if (!started) {
            started = true;
            // the unit itself is a full block: the air starts at all its sides
            from.set(origin);
            for (Direction direction : Direction.values()) {
                if (visit(space, from, null, direction)) {
                    return used;
                }
            }
        }
        while (!queue.isEmpty() && used < budget) {
            long here = queue.dequeueLong();
            used++;
            from.set(here);
            BlockState state = space.state(from);
            for (Direction direction : Direction.values()) {
                if (visit(space, from, state, direction)) {
                    return used;
                }
            }
        }
        if (queue.isEmpty()) {
            done = true;
            if (air.size() > (long) volumePerUnit * Math.min(units.size(), maxUnits)) {
                leak = Leak.TOO_LARGE;
                leakPos = origin;
            }
        }
        return used;
    }

    /** Looks at the neighbour of {@code from}; true when the scan ended with a leak. */
    private boolean visit(Space space, BlockPos from, BlockState fromState, Direction direction) {
        BlockPos to = from.relative(direction);
        long key = to.asLong();
        if (air.contains(key)) {
            return false;
        }
        if (fromState != null && Sealing.seals(fromState, direction)) {
            border.put(from.asLong(), fromState);
            return false;
        }
        if (!space.loaded(to)) {
            return finish(Leak.UNLOADED, key);
        }
        BlockState toState = space.state(to);
        if (space.isUnit(toState)) {
            units.add(key);
        }
        if (Sealing.seals(toState, direction.getOpposite())) {
            border.put(key, toState);
            if (Sealing.isPartialLeak(toState)) {
                partialLeaks.add(key);
            }
            return false;
        }
        if (space.openSky(to)) {
            return finish(Leak.SKY, key);
        }
        air.add(key);
        if (air.size() > (long) volumePerUnit * maxUnits) {
            return finish(Leak.TOO_LARGE, key);
        }
        queue.enqueue(key);
        return false;
    }

    private boolean finish(Leak found, long where) {
        leak = found;
        leakPos = where;
        done = true;
        return true;
    }

    /** The room found; only once the scan is done. */
    public Room result() {
        if (!done) {
            throw new IllegalStateException("The scan is not done");
        }
        return new Room(leak, BlockPos.of(leakPos), air, border, units, partialLeaks.size());
    }
}
