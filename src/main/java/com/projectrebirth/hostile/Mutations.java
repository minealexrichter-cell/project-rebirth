package com.projectrebirth.hostile;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.AABB;

/**
 * 1.9.0, block C: animals in contaminated places mutate. Which animal becomes what is read from
 * data/&lt;namespace&gt;/projectrebirth/mutation/*.json ({@code {"from": "minecraft:cow", "to": "projectrebirth:mutated_cow"}}); a
 * wolf becomes a mutated hound. An animal counts its seconds in contaminated chunks or the contamination zone while a player
 * is near (the world around players is the only one that ticks); after [hostile.mutants] mutationSeconds it turns. Young,
 * tamed, named and leashed animals do not.
 */
public final class Mutations {
    public static final ResourceKey<Registry<Mutation>> REGISTRY = ResourceKey.createRegistryKey(ProjectRebirth.id("mutation"));
    private static final String SECONDS = ProjectRebirth.MOD_ID + ":mutation_seconds";
    private static final String COUNTED = ProjectRebirth.MOD_ID + ":mutation_counted";

    public record Mutation(EntityType<?> from, EntityType<?> to) {
        public static final Codec<Mutation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("from").forGetter(Mutation::from),
                BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("to").forGetter(Mutation::to)
        ).apply(instance, Mutation::new));
    }

    private Mutations() {
    }

    /** What this kind of creature mutates into, if anything. */
    public static Optional<EntityType<?>> into(ServerLevel level, EntityType<?> from) {
        return level.registryAccess().registry(REGISTRY).flatMap(registry -> registry.stream()
                .filter(mutation -> mutation.from() == from).map(Mutation::to).findFirst());
    }

    /** Whether this creature may mutate at all. */
    public static boolean canMutate(Mob mob) {
        return !(mob instanceof AgeableMob ageable && ageable.isBaby()) && !(mob instanceof TamableAnimal tame && tame.isTame())
                && !mob.hasCustomName() && !mob.isLeashed();
    }

    /** Seconds the animals near a player spend in contaminated places; those that have had enough mutate. */
    static void second(ServerLevel level, ServerPlayer player, int seconds) {
        if (!RebirthConfig.MUTATION_ENABLED.get() || level.registryAccess().registry(REGISTRY).map(Registry::size).orElse(0) == 0) {
            return;
        }
        double radius = RebirthConfig.MUTATION_RADIUS.get();
        long now = level.getGameTime();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(player.blockPosition()).inflate(radius), mob -> mob.isAlive() && !(mob instanceof MutantEntity))) {
            Optional<EntityType<?>> into = into(level, mob.getType());
            if (into.isEmpty() || !canMutate(mob)) {
                continue;
            }
            CompoundTag data = mob.getPersistentData();
            // two players near the same animal count its time once
            if (now - data.getLong(COUNTED) < seconds * 20L - 1) {
                continue;
            }
            data.putLong(COUNTED, now);
            if (!MutantSpawner.contaminated(level, mob.blockPosition())) {
                continue;
            }
            int total = data.getInt(SECONDS) + seconds;
            data.putInt(SECONDS, total);
            if (total >= RebirthConfig.MUTATION_SECONDS.get()) {
                mutate(level, mob, into.get());
            }
        }
    }

    /** Turns a creature into its mutant. Public for tests. */
    @SuppressWarnings("unchecked")
    public static Mob mutate(ServerLevel level, Mob mob, EntityType<?> into) {
        Mob mutant = mob.convertTo((EntityType<Mob>) into, false);
        if (mutant != null) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNEEZE, mutant.getX(), mutant.getY() + mutant.getBbHeight() * 0.6,
                    mutant.getZ(), 12, 0.4, 0.4, 0.4, 0.02);
            mutant.playSound(net.minecraft.sounds.SoundEvents.ZOMBIE_VILLAGER_CONVERTED, 0.8f, 0.6f);
        }
        return mutant;
    }
}
