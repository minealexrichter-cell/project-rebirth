package com.projectrebirth.hostile;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;

/**
 * 1.9.0, block C: when and where a kind of mutant appears, read from
 * data/&lt;namespace&gt;/projectrebirth/mutant_spawns/*.json, so a data pack can change it ({@link MutantSpawner} uses them).
 *
 * @param entity       the mutant
 * @param biomes       where (usually a tag); absent: anywhere
 * @param contaminated true: only where the player stands in a contaminated chunk or the contamination zone; false: only where
 *                     the ground is clean; absent: either
 * @param time         "day", "night" or "any"
 * @param maxLight     the brightest place it appears at, 0 to 15
 * @param minY         lowest height of the place
 * @param maxY         highest height of the place
 * @param placement    "ground" (on the surface), "underground" (in a cave near the player's height) or "water" (in water)
 * @param catastrophes only during one of these catastrophes; empty: any time
 * @param minStage     from this stage of the Wasteland
 * @param maxStage     up to this stage
 * @param groupMin     fewest in a group
 * @param groupMax     most in a group
 * @param weight       how often among the rules that fit
 * @param chance       how likely a try that picked it brings it, 0 to 1
 * @param maxNearby    no more while this many of it are within 48 blocks; 0: only the common limit
 */
public record MutantSpawnRule(EntityType<?> entity, Optional<HolderSet<Biome>> biomes, Optional<Boolean> contaminated, String time,
                              int maxLight, int minY, int maxY, String placement, List<String> catastrophes, int minStage, int maxStage,
                              int groupMin, int groupMax, int weight, float chance, int maxNearby) {
    public static final Codec<MutantSpawnRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity").forGetter(MutantSpawnRule::entity),
            RegistryCodecs.homogeneousList(Registries.BIOME).optionalFieldOf("biomes").forGetter(MutantSpawnRule::biomes),
            Codec.BOOL.optionalFieldOf("contaminated").forGetter(MutantSpawnRule::contaminated),
            Codec.STRING.optionalFieldOf("time", "any").forGetter(MutantSpawnRule::time),
            Codec.INT.optionalFieldOf("max_light", 15).forGetter(MutantSpawnRule::maxLight),
            Codec.INT.optionalFieldOf("min_y", -64).forGetter(MutantSpawnRule::minY),
            Codec.INT.optionalFieldOf("max_y", 320).forGetter(MutantSpawnRule::maxY),
            Codec.STRING.optionalFieldOf("placement", "ground").forGetter(MutantSpawnRule::placement),
            Codec.STRING.listOf().optionalFieldOf("catastrophes", List.of()).forGetter(MutantSpawnRule::catastrophes),
            Codec.INT.optionalFieldOf("min_stage", 1).forGetter(MutantSpawnRule::minStage),
            Codec.INT.optionalFieldOf("max_stage", 4).forGetter(MutantSpawnRule::maxStage),
            Codec.INT.optionalFieldOf("group_min", 1).forGetter(MutantSpawnRule::groupMin),
            Codec.INT.optionalFieldOf("group_max", 1).forGetter(MutantSpawnRule::groupMax),
            Codec.INT.optionalFieldOf("weight", 1).forGetter(MutantSpawnRule::weight),
            Codec.FLOAT.optionalFieldOf("chance", 1.0f).forGetter(MutantSpawnRule::chance),
            Codec.INT.optionalFieldOf("max_nearby", 0).forGetter(MutantSpawnRule::maxNearby)
    ).apply(instance, MutantSpawnRule::new));

    public static final String GROUND = "ground";
    public static final String UNDERGROUND = "underground";
    public static final String WATER = "water";

    /** Whether it fits the time, stage, catastrophe and contamination of the player's place now. */
    public boolean fitsNow(boolean night, int stage, String catastrophe, boolean contaminatedHere) {
        if ("day".equals(time) && night || "night".equals(time) && !night) {
            return false;
        }
        if (stage < minStage || stage > maxStage) {
            return false;
        }
        if (!catastrophes.isEmpty() && (catastrophe == null || !catastrophes.contains(catastrophe))) {
            return false;
        }
        return contaminated.map(wanted -> wanted == contaminatedHere).orElse(true);
    }
}
