package com.projectrebirth.garrison;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 1.7.2, block B: the loaded service posts and charging pads of each world, so that a machine finds the nearest one far away
 * without looking through every chunk around it. A block entity enters when it is loaded on the server and leaves when it is
 * removed or its chunk unloads. A place is checked again when it is found (a stale one, left from another world of the same
 * game, is dropped), so the list need not be exact.
 */
public final class LoadedSites {
    public enum Kind {
        SERVICE_POST, CHARGING_PAD
    }

    private static final Map<Kind, Map<ResourceKey<Level>, Set<BlockPos>>> SITES = new HashMap<>();

    private LoadedSites() {
    }

    public static void add(Kind kind, BlockEntity site) {
        if (site.getLevel() instanceof ServerLevel level) {
            SITES.computeIfAbsent(kind, k -> new HashMap<>()).computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(site.getBlockPos().immutable());
        }
    }

    public static void remove(Kind kind, BlockEntity site) {
        if (site.getLevel() instanceof ServerLevel level) {
            Map<ResourceKey<Level>, Set<BlockPos>> byLevel = SITES.get(kind);
            Set<BlockPos> sites = byLevel == null ? null : byLevel.get(level.dimension());
            if (sites != null) {
                sites.remove(site.getBlockPos());
            }
        }
    }

    /** 1.8.0: the places of a kind listed within the radius (not checked again: for a map). */
    public static java.util.List<BlockPos> within(ServerLevel level, Kind kind, BlockPos from, int radius) {
        Map<ResourceKey<Level>, Set<BlockPos>> byLevel = SITES.get(kind);
        Set<BlockPos> sites = byLevel == null ? null : byLevel.get(level.dimension());
        if (sites == null) {
            return java.util.List.of();
        }
        return sites.stream().filter(pos -> pos.distSqr(from) <= (double) radius * radius).toList();
    }

    /** The nearest loaded site of a kind within the radius that passes the test, or null. */
    @Nullable
    public static <T extends BlockEntity> T nearest(ServerLevel level, Kind kind, Class<T> type, BlockPos from, int radius, Predicate<T> test) {
        Map<ResourceKey<Level>, Set<BlockPos>> byLevel = SITES.get(kind);
        Set<BlockPos> sites = byLevel == null ? null : byLevel.get(level.dimension());
        if (sites == null) {
            return null;
        }
        T best = null;
        double bestDistance = (double) radius * radius;
        // a copy: looking up a block entity may load it, which adds to the list
        for (BlockPos pos : java.util.List.copyOf(sites)) {
            double distance = pos.distSqr(from);
            if (distance >= bestDistance) {
                continue;
            }
            BlockEntity found = level.isLoaded(pos) ? level.getBlockEntity(pos) : null;
            if (!type.isInstance(found)) {
                sites.remove(pos);
                continue;
            }
            T site = type.cast(found);
            if (test.test(site)) {
                best = site;
                bestDistance = distance;
            }
        }
        return best;
    }
}
