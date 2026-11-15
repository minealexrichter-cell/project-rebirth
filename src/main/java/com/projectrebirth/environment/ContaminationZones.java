package com.projectrebirth.environment;

import java.util.List;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.factions.FactionService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Contaminated zones (1.6.0): the logic around {@link ContaminationMap}. A radiation burst and a toxic storm leave spots of
 * residual contamination around the players; what the map holds at a place is added to the air there by {@link Environment}.
 * Server only.
 */
public final class ContaminationZones {
    /** Bits of {@link #mask}. */
    public static final int RADIATION_BIT = 1;
    public static final int TOXIC_BIT = 2;
    private static final int ATTEMPTS_PER_SPOT = 12;

    private ContaminationZones() {
    }

    public static boolean enabled() {
        return Environment.isEnabled() && RebirthConfig.ZONES_ENABLED.get();
    }

    /** What the ground holds at the place now; nothing with the system switched off. */
    public static ContaminationMap.Reading readingAt(ServerLevel level, BlockPos pos) {
        if (!enabled()) {
            return ContaminationMap.Reading.NONE;
        }
        return ContaminationMap.get(level).readingAt(pos, level.getGameTime());
    }

    /** Which kinds of contamination the place has to speak of (the HUD line and the visuals): {@link #RADIATION_BIT}, {@link #TOXIC_BIT}. */
    public static int mask(ServerLevel level, BlockPos pos) {
        return mask(readingAt(level, pos));
    }

    public static int mask(ContaminationMap.Reading reading) {
        float threshold = RebirthConfig.ZONES_MARK_THRESHOLD.get().floatValue();
        return (reading.radiation() > threshold ? RADIATION_BIT : 0) | (reading.toxin() > threshold ? TOXIC_BIT : 0);
    }

    /**
     * A catastrophe has ended: a radiation burst and a toxic storm leave their spots around the players in the dimension.
     *
     * @return how many spots were left
     */
    public static int leaveSpots(ServerLevel level, String event, float intensity) {
        return leaveSpots(level, event, intensity, level.players());
    }

    /** The same around the given players (public for tests). */
    public static int leaveSpots(ServerLevel level, String event, float intensity, List<ServerPlayer> online) {
        boolean radiation = Environment.RADIATION_BURST.equals(event);
        boolean toxic = Environment.TOXIC_STORM.equals(event);
        if (!enabled() || !radiation && !toxic) {
            return 0;
        }
        List<ServerPlayer> players = online.stream().filter(player -> player.isAlive() && !player.isSpectator()).toList();
        if (players.isEmpty()) {
            return 0;
        }
        RandomSource random = level.getRandom();
        int count = between(random, radiation ? RebirthConfig.ZONES_RADIATION_MIN_SPOTS.get() : RebirthConfig.ZONES_TOXIC_MIN_SPOTS.get(),
                radiation ? RebirthConfig.ZONES_RADIATION_MAX_SPOTS.get() : RebirthConfig.ZONES_TOXIC_MAX_SPOTS.get());
        int placed = 0;
        for (int i = 0; i < count; i++) {
            for (int attempt = 0; attempt < ATTEMPTS_PER_SPOT; attempt++) {
                ServerPlayer around = players.get(random.nextInt(players.size()));
                int min = RebirthConfig.ZONES_MIN_DISTANCE.get();
                int max = Math.max(min, RebirthConfig.ZONES_MAX_DISTANCE.get());
                double angle = random.nextDouble() * Math.PI * 2.0;
                double distance = min + random.nextDouble() * (max - min);
                BlockPos centre = BlockPos.containing(around.getX() + Math.cos(angle) * distance, level.getSeaLevel(), around.getZ() + Math.sin(angle) * distance);
                int radius = radiation ? between(random, RebirthConfig.ZONES_RADIATION_MIN_RADIUS.get(), RebirthConfig.ZONES_RADIATION_MAX_RADIUS.get())
                        : between(random, RebirthConfig.ZONES_TOXIC_MIN_RADIUS.get(), RebirthConfig.ZONES_TOXIC_MAX_RADIUS.get());
                if (!RebirthConfig.ZONES_ON_TERRITORY.get() && onTerritory(level, centre, radius)) {
                    continue;
                }
                if (toxic && random.nextFloat() >= toxicChance(level, centre)) {
                    continue;
                }
                float strength = (0.75f + 0.25f * random.nextFloat()) * intensity;
                ContaminationMap.get(level).addSpot(centre, radius,
                        radiation ? strength * RebirthConfig.ZONES_RADIATION_STRENGTH.get().floatValue() : 0.0f,
                        toxic ? strength * RebirthConfig.ZONES_TOXIC_STRENGTH.get().floatValue() : 0.0f, level.getGameTime());
                placed++;
                break;
            }
        }
        return placed;
    }

    /** Whether a spot there would touch the territory of a faction: the spot's square (its radius plus a core's reach) is compared with every core of the dimension. */
    public static boolean onTerritory(ServerLevel level, BlockPos centre, int radiusChunks) {
        int reach = (radiusChunks + 1) * 16 + FactionService.coreRadius();
        for (com.projectrebirth.factions.Faction faction : com.projectrebirth.factions.FactionData.get(level.getServer()).all()) {
            for (net.minecraft.core.GlobalPos core : faction.cores()) {
                if (core.dimension().equals(level.dimension()) && Math.abs(core.pos().getX() - centre.getX()) <= reach
                        && Math.abs(core.pos().getZ() - centre.getZ()) <= reach) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The chance that a toxic spot is accepted at a place: 1 in a swamp, by a river or in a lowland, and 1 divided by
     * the configured weight elsewhere. A place that is not loaded is not asked (no chunk is loaded for this) and counts as elsewhere.
     */
    private static float toxicChance(ServerLevel level, BlockPos centre) {
        float weight = RebirthConfig.ZONES_TOXIC_LOWLAND_WEIGHT.get().floatValue();
        if (weight <= 1.0f) {
            return 1.0f;
        }
        if (!level.hasChunk(centre.getX() >> 4, centre.getZ() >> 4)) {
            return 1.0f / weight;
        }
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, centre.getX(), centre.getZ());
        Holder<Biome> biome = level.getBiome(new BlockPos(centre.getX(), surface, centre.getZ()));
        boolean favoured = biome.is(BiomeTags.IS_RIVER) || biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP)
                || surface <= level.getSeaLevel() + RebirthConfig.ZONES_LOWLAND_HEIGHT.get();
        return favoured ? 1.0f : 1.0f / weight;
    }

    private static int between(RandomSource random, int min, int max) {
        return max <= min ? min : min + random.nextInt(max - min + 1);
    }

    /** The share that tints the air and thickens the particles, 0 to 1, from how strong the strongest contamination here is. */
    public static float visualStrength(float radiation, float toxin) {
        return Mth.clamp(Math.max(radiation, toxin) / 6.0f, 0.0f, 1.0f);
    }
}
