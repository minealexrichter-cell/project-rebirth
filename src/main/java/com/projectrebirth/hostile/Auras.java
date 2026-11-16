package com.projectrebirth.hostile;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.ContaminationMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Creatures and remains that are themselves sources (1.6.0, part B3c): the glowing ghoul gives radiation and polluted air, the
 * body of a brute gives radiation. Strength falls off linearly to nothing at the reach, like the source blocks.
 */
public final class Auras {
    private static final double MAX_REACH = 32.0;

    private Auras() {
    }

    public static ContaminationMap.Reading at(ServerLevel level, BlockPos pos) {
        if (!RebirthConfig.SERVER_SPEC.isLoaded() || !MutantEntity.enabled()) {
            return ContaminationMap.Reading.NONE;
        }
        Vec3 at = Vec3.atCenterOf(pos);
        float radiation = 0.0f, toxin = 0.0f;
        for (Entity entity : level.getEntities((Entity) null, new AABB(pos).inflate(MAX_REACH), e -> e instanceof GlowingGhoulEntity || e instanceof RemainsEntity)) {
            double distance = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0).distanceTo(at);
            if (entity instanceof GlowingGhoulEntity ghoul && ghoul.isAlive()) {
                double reach = RebirthConfig.GHOUL_AURA_RANGE.get();
                if (distance < reach) {
                    float share = (float) (1.0 - distance / reach);
                    radiation += RebirthConfig.GHOUL_AURA.get().floatValue() * share;
                    toxin += RebirthConfig.GHOUL_TOXIN_AURA.get().floatValue() * share;
                }
            } else if (entity instanceof RemainsEntity remains && remains.kind() == RemainsEntity.Kind.BRUTE) {
                double reach = RebirthConfig.CORPSE_RANGE.get();
                if (distance < reach) {
                    radiation += RebirthConfig.CORPSE_RADIATION.get().floatValue() * (float) (1.0 - distance / reach);
                }
            }
        }
        return radiation <= 0.0f && toxin <= 0.0f ? ContaminationMap.Reading.NONE : new ContaminationMap.Reading(radiation, toxin);
    }
}
