package com.projectrebirth.logistics;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.projectrebirth.factions.Faction;
import com.projectrebirth.factions.FactionData;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * 1.8.0, block J: the whole storages of each loaded world (a storage in an unloaded chunk counts for nobody: machines do not load
 * chunks). Kept by the storages themselves as they are checked, loaded and removed.
 */
public final class Storages {
    private static final Map<ServerLevel, Set<BlockPos>> WHOLE = new WeakHashMap<>();

    private Storages() {
    }

    static void add(ServerLevel level, BlockPos pos) {
        WHOLE.computeIfAbsent(level, key -> new HashSet<>()).add(pos.immutable());
    }

    static void remove(ServerLevel level, BlockPos pos) {
        Set<BlockPos> set = WHOLE.get(level);
        if (set != null) {
            set.remove(pos);
        }
    }

    /** The places of the whole storages of that world. */
    public static List<BlockPos> all(ServerLevel level) {
        Set<BlockPos> set = WHOLE.get(level);
        return set == null ? List.of() : List.copyOf(set);
    }

    /** Whether a storage belongs to that player: theirs, or of a member of their faction. */
    public static boolean ours(ServerLevel level, StorageControllerBlockEntity storage, @Nullable UUID player) {
        UUID owner = storage.owner();
        if (player == null || owner == null) {
            return false;
        }
        if (owner.equals(player)) {
            return true;
        }
        FactionData data = FactionData.get(level.getServer());
        Optional<Faction> mine = data.of(player);
        Optional<Faction> theirs = data.of(owner);
        return mine.isPresent() && theirs.isPresent() && mine.get().id().equals(theirs.get().id());
    }

    /** The nearest whole storage of that player's (or their faction's) within range, or null. */
    @Nullable
    public static StorageControllerBlockEntity nearest(ServerLevel level, BlockPos from, @Nullable UUID player, double range) {
        StorageControllerBlockEntity best = null;
        double bestDistance = range * range;
        for (BlockPos pos : all(level)) {
            double distance = pos.distSqr(from);
            if (distance > bestDistance || !level.isLoaded(pos)) {
                continue;
            }
            if (level.getBlockEntity(pos) instanceof StorageControllerBlockEntity storage && storage.whole() && ours(level, storage, player)) {
                best = storage;
                bestDistance = distance;
            }
        }
        return best;
    }
}
