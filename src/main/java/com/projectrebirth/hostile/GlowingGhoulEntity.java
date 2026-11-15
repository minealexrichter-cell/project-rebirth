package com.projectrebirth.hostile;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The glowing ghoul (1.6.0, part B3c): slow, its sores glow green in the dark, and it is a walking source of radiation: near it
 * a player takes radiation like near radioactive debris, through the dosimeter and the suit as usual ({@link #auraAt}).
 */
public class GlowingGhoulEntity extends MutantEntity {
    private static final DustParticleOptions GLOW = new DustParticleOptions(new Vector3f(0.45f, 1.0f, 0.3f), 0.9f);

    public GlowingGhoulEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, "glowing_ghoul");
        xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 26.0)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.MOVEMENT_SPEED, 0.19)
                .add(Attributes.FOLLOW_RANGE, 20.0);
    }

    @Override
    protected void applyConfig() {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(RebirthConfig.GHOUL_HEALTH.get());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(RebirthConfig.GHOUL_DAMAGE.get());
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(RebirthConfig.GHOUL_SPEED.get());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && random.nextInt(3) == 0) {
            level().addParticle(GLOW, getRandomX(0.6), getRandomY(), getRandomZ(0.6), 0.0, 0.02, 0.0);
        }
    }

    /** The radiation the ghouls about give at a place: linear falloff from the aura at the ghoul to nothing at its range. */
    public static float auraAt(ServerLevel level, BlockPos pos) {
        if (!enabled() || !RebirthConfig.SERVER_SPEC.isLoaded()) {
            return 0.0f;
        }
        double range = RebirthConfig.GHOUL_AURA_RANGE.get();
        float aura = RebirthConfig.GHOUL_AURA.get().floatValue();
        if (aura <= 0.0f) {
            return 0.0f;
        }
        Vec3 at = Vec3.atCenterOf(pos);
        float total = 0.0f;
        for (GlowingGhoulEntity ghoul : level.getEntitiesOfClass(GlowingGhoulEntity.class, new AABB(pos).inflate(range), GlowingGhoulEntity::isAlive)) {
            double distance = ghoul.position().add(0.0, ghoul.getBbHeight() * 0.5, 0.0).distanceTo(at);
            if (distance < range) {
                total += aura * (float) (1.0 - distance / range);
            }
        }
        return total;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ZOMBIE_VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_VILLAGER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.75f + random.nextFloat() * 0.1f;
    }
}
