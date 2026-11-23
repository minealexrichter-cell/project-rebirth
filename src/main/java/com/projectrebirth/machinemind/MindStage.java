package com.projectrebirth.machinemind;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

/**
 * 1.9.0, block F: a stage of the machine mind, from data (projectrebirth/mind_stages/&lt;n&gt;.json): what the drones build in it (a
 * structure template with the computer in its middle at height 0), and what the factory makes from this stage on.
 *
 * @param stage    1 to 5
 * @param template the structure added to the base in this stage
 * @param robots   what is made (a later stage adds to the earlier ones; a later entry of the same kind replaces the earlier)
 */
public record MindStage(int stage, Optional<ResourceLocation> template, List<Production> robots) {
    public static final ResourceKey<Registry<MindStage>> REGISTRY = ResourceKey.createRegistryKey(ProjectRebirth.id("mind_stages"));

    /** One kind of machine: at most {@code cap} of it at a node, one each {@code seconds}, made in {@code module}. */
    public record Production(EntityType<?> entity, int cap, int seconds, int cost, Block module) {
        public static final Codec<Production> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity").forGetter(Production::entity),
                Codec.intRange(0, 64).fieldOf("cap").forGetter(Production::cap),
                Codec.intRange(1, 86400).optionalFieldOf("seconds", 60).forGetter(Production::seconds),
                Codec.intRange(0, 10000).optionalFieldOf("cost", 8).forGetter(Production::cost),
                BuiltInRegistries.BLOCK.byNameCodec().fieldOf("module").forGetter(Production::module)).apply(i, Production::new));
    }

    public static final Codec<MindStage> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1, 5).fieldOf("stage").forGetter(MindStage::stage),
            ResourceLocation.CODEC.optionalFieldOf("template").forGetter(MindStage::template),
            Production.CODEC.listOf().optionalFieldOf("robots", List.of()).forGetter(MindStage::robots)).apply(i, MindStage::new));

    /** The stages up to this one, in order. */
    public static List<MindStage> upTo(RegistryAccess access, int stage) {
        return access.registry(REGISTRY).map(registry -> registry.stream().filter(s -> s.stage() <= stage)
                .sorted(Comparator.comparingInt(MindStage::stage)).toList()).orElse(List.of());
    }
}
