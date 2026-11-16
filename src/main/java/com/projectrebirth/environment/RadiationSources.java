package com.projectrebirth.environment;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Point sources of radiation and toxins (1.6.0): the blocks of {@link SourceKind}, uranium ore around a person and uranium in a
 * person's inventory. What they give at a place is added to the air by {@link Environment}, so the dosimeter, the HUD, the external
 * contamination, bodies and armor follow by themselves. Server only.
 *
 * <p>Nothing is scanned over a big volume: a source block's entity puts itself into a per-level index by chunk when it loads and
 * takes itself out when it is removed or its chunk unloads; a query looks only into the chunks within the longest reach. Uranium ore
 * has no block entity (the world generator places many): a small cube around the person is looked through instead.
 */
public final class RadiationSources {
    /** Ore blocks that radiate, and items that radiate in an inventory. */
    public static final TagKey<Block> RADIOACTIVE_BLOCKS = TagKey.create(Registries.BLOCK, ProjectRebirth.id("radioactive"));
    public static final TagKey<Item> RADIOACTIVE_ITEMS = TagKey.create(Registries.ITEM, ProjectRebirth.id("radioactive"));

    /** Per level: chunk, then the source blocks in it. Held weakly: a level that is gone takes its index with it. */
    private static final Map<ServerLevel, Map<Long, Map<BlockPos, SourceKind>>> INDEX = new WeakHashMap<>();

    private RadiationSources() {
    }

    public static boolean enabled() {
        return Environment.isEnabled() && RebirthConfig.SOURCES_ENABLED.get();
    }

    // ---------------------------------------------------------------- the index

    public static void register(ServerLevel level, BlockPos pos, SourceKind kind) {
        INDEX.computeIfAbsent(level, key -> new HashMap<>())
                .computeIfAbsent(ChunkPos.asLong(pos), key -> new HashMap<>()).put(pos.immutable(), kind);
    }

    public static void unregister(ServerLevel level, BlockPos pos) {
        Map<Long, Map<BlockPos, SourceKind>> chunks = INDEX.get(level);
        if (chunks == null) {
            return;
        }
        long key = ChunkPos.asLong(pos);
        Map<BlockPos, SourceKind> inChunk = chunks.get(key);
        if (inChunk != null) {
            inChunk.remove(pos);
            if (inChunk.isEmpty()) {
                chunks.remove(key);
            }
        }
    }

    /** How many source blocks the level knows of. */
    public static int count(ServerLevel level) {
        Map<Long, Map<BlockPos, SourceKind>> chunks = INDEX.get(level);
        return chunks == null ? 0 : chunks.values().stream().mapToInt(Map::size).sum();
    }

    /** Forgets all (what a reload of the world does; the loaded blocks come back by themselves). Public for tests. */
    public static void clear(ServerLevel level) {
        INDEX.remove(level);
    }

    public static boolean isRegistered(ServerLevel level, BlockPos pos) {
        Map<Long, Map<BlockPos, SourceKind>> chunks = INDEX.get(level);
        Map<BlockPos, SourceKind> inChunk = chunks == null ? null : chunks.get(ChunkPos.asLong(pos));
        return inChunk != null && inChunk.containsKey(pos);
    }

    // ---------------------------------------------------------------- what a place gets

    /** What the sources give at a place: the source blocks and the uranium ore around. Nothing with the system switched off. */
    public static ContaminationMap.Reading readingAt(ServerLevel level, BlockPos pos) {
        if (!enabled()) {
            return ContaminationMap.Reading.NONE;
        }
        float radiation = 0.0f;
        float toxin = 0.0f;
        Map<Long, Map<BlockPos, SourceKind>> chunks = INDEX.get(level);
        if (chunks != null && !chunks.isEmpty()) {
            int reach = 0;
            for (SourceKind kind : SourceKind.values()) {
                reach = Math.max(reach, kind.range());
            }
            int chunkReach = (reach + 15) >> 4;
            int cx = pos.getX() >> 4;
            int cz = pos.getZ() >> 4;
            for (int dx = -chunkReach; dx <= chunkReach; dx++) {
                for (int dz = -chunkReach; dz <= chunkReach; dz++) {
                    Map<BlockPos, SourceKind> inChunk = chunks.get(ChunkPos.asLong(cx + dx, cz + dz));
                    if (inChunk == null) {
                        continue;
                    }
                    for (Map.Entry<BlockPos, SourceKind> source : inChunk.entrySet()) {
                        SourceKind kind = source.getValue();
                        if (!kind.enabled()) {
                            continue;
                        }
                        float share = strengthShare(level, source.getKey(), pos, kind.range());
                        radiation += kind.radiation() * share;
                        toxin += kind.toxin() * share;
                    }
                }
            }
        }
        radiation += oreAround(level, pos);
        // 1.6.0, part B3c: the glowing ghouls and the bodies of brutes are sources too
        ContaminationMap.Reading creatures = com.projectrebirth.hostile.Auras.at(level, pos);
        radiation += creatures.radiation();
        toxin += creatures.toxin();
        return radiation <= 0.0f && toxin <= 0.0f ? ContaminationMap.Reading.NONE : new ContaminationMap.Reading(radiation, toxin);
    }

    /** The part of a source's strength that reaches a place: linear falloff to nothing at the range, times the factor of each full block on the line. */
    public static float strengthShare(ServerLevel level, BlockPos source, BlockPos target, int range) {
        double distance = Vec3.atCenterOf(source).distanceTo(Vec3.atCenterOf(target));
        if (distance >= range) {
            return 0.0f;
        }
        float share = (float) (1.0 - distance / range);
        float wall = RebirthConfig.SOURCES_WALL_FACTOR.get().floatValue();
        if (wall < 1.0f && !source.equals(target)) {
            int blocks = solidBlocksBetween(level, source, target);
            share *= (float) Math.pow(wall, blocks);
        }
        return share;
    }

    /** Full solid blocks on the line between two blocks, not counting the two themselves. Blocks of unloaded chunks count as air. Public for tests. */
    public static int solidBlocksBetween(ServerLevel level, BlockPos from, BlockPos to) {
        Vec3 start = Vec3.atCenterOf(from);
        Vec3 end = Vec3.atCenterOf(to);
        double length = start.distanceTo(end);
        int steps = (int) Math.ceil(length / 0.5);
        int count = 0;
        BlockPos last = null;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            cursor.set(Math.floor(start.x + (end.x - start.x) * t), Math.floor(start.y + (end.y - start.y) * t), Math.floor(start.z + (end.z - start.z) * t));
            if (cursor.equals(from) || cursor.equals(to) || cursor.equals(last) || !level.isLoaded(cursor)) {
                continue;
            }
            last = cursor.immutable();
            BlockState state = level.getBlockState(cursor);
            if (!state.isAir() && state.isCollisionShapeFullBlock(level, cursor)) {
                count++;
            }
        }
        return count;
    }

    /** Radiation of the uranium ore within reach of a place: a small cube, one look per block, once a second per person at most. */
    public static float oreAround(ServerLevel level, BlockPos pos) {
        int reach = RebirthConfig.URANIUM_ORE_RANGE.get();
        if (!RebirthConfig.URANIUM_ORE_RADIATES.get() || reach <= 0) {
            return 0.0f;
        }
        if (!level.isLoaded(pos.offset(-reach, 0, -reach)) || !level.isLoaded(pos.offset(reach, 0, reach))
                || !level.isLoaded(pos.offset(-reach, 0, reach)) || !level.isLoaded(pos.offset(reach, 0, -reach))) {
            return 0.0f;
        }
        float perBlock = RebirthConfig.URANIUM_ORE_RADIATION.get().floatValue();
        float total = 0.0f;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (distance > reach) {
                        continue;
                    }
                    cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (level.getBlockState(cursor).is(RADIOACTIVE_BLOCKS)) {
                        total += perBlock * (float) (1.0 - distance / (reach + 1));
                    }
                }
            }
        }
        return Math.min(total, RebirthConfig.URANIUM_ORE_MAX.get().floatValue());
    }

    /** Radiation of the uranium a person carries, by the count of items, up to the limit. */
    public static float carriedBy(Player player) {
        if (!enabled() || !RebirthConfig.URANIUM_ITEMS_RADIATE.get()) {
            return 0.0f;
        }
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(RADIOACTIVE_ITEMS)) {
                count += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(RADIOACTIVE_ITEMS)) {
                count += stack.getCount();
            }
        }
        return Math.min(count * RebirthConfig.URANIUM_ITEM_RADIATION.get().floatValue(), RebirthConfig.URANIUM_ITEMS_MAX.get().floatValue());
    }
}
