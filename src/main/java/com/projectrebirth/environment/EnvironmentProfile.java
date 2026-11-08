package com.projectrebirth.environment;

import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;

/**
 * The environment of some biomes, read from data/&lt;namespace&gt;/projectrebirth/environment/*.json, so a data pack can change it.
 * Of all profiles that name a biome, the one with the highest priority counts.
 *
 * @param biomes           the biomes, usually a tag
 * @param priority         which profile wins where several name a biome
 * @param background       hazards that are always there; a hazard not named here is 0
 * @param eventMultipliers how strongly a catastrophe acts here, by its id; a catastrophe not named here has 1
 * @param temperatureOffset degrees added to the temperature of the place here (1.5.0): savannas are as warm as deserts in
 *                          vanilla, and get a little cooler so that only true deserts are hot
 */
public record EnvironmentProfile(HolderSet<Biome> biomes, int priority, Map<Hazard, Float> background, Map<String, Float> eventMultipliers,
                                 float temperatureOffset) {
    public static final Codec<EnvironmentProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("biomes").forGetter(EnvironmentProfile::biomes),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(EnvironmentProfile::priority),
            Codec.unboundedMap(Hazard.CODEC, Codec.FLOAT).optionalFieldOf("background", Map.of()).forGetter(EnvironmentProfile::background),
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("event_multipliers", Map.of()).forGetter(EnvironmentProfile::eventMultipliers),
            Codec.FLOAT.optionalFieldOf("temperature_offset", 0.0f).forGetter(EnvironmentProfile::temperatureOffset)
    ).apply(instance, EnvironmentProfile::new));

    public boolean covers(Holder<Biome> biome) {
        return biomes.contains(biome);
    }

    public float background(Hazard hazard) {
        return background.getOrDefault(hazard, 0.0f);
    }

    public float eventMultiplier(String event) {
        return eventMultipliers.getOrDefault(event, 1.0f);
    }
}
