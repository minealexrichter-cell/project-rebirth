package com.projectrebirth.hostile;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.Biology;
import com.projectrebirth.environment.Hazard;
import com.projectrebirth.environment.HumanBody;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Vector3f;

/**
 * The toxic spit of a glowing ghoul (1.6.0, part B3c): a gob of green slime that flies in an arc. A person it hits takes some
 * damage and a load of toxins (less through a protective suit, like polluted air); it bursts in a green splash.
 */
public class ToxicSpitEntity extends ThrowableProjectile {
    private static final DustParticleOptions SLIME = new DustParticleOptions(new Vector3f(0.45f, 0.9f, 0.2f), 1.2f);

    public ToxicSpitEntity(EntityType<? extends ToxicSpitEntity> type, Level level) {
        super(type, level);
    }

    public ToxicSpitEntity(Level level, LivingEntity owner) {
        super(HostileRegistry.TOXIC_SPIT.get(), owner, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected double getDefaultGravity() {
        return 0.025;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            level().addParticle(SLIME, getX(), getY() + 0.1, getZ(), 0.0, 0.0, 0.0);
            if (random.nextInt(2) == 0) {
                level().addParticle(ParticleTypes.ITEM_SLIME, getX(), getY() + 0.1, getZ(), 0.0, 0.0, 0.0);
            }
        }
        if (tickCount > 100) {
            discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !(target instanceof MutantEntity);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity target = result.getEntity();
        float damage = RebirthConfig.SERVER_SPEC.isLoaded() ? RebirthConfig.GHOUL_SPIT_DAMAGE.get().floatValue() : 4.0f;
        target.hurt(damageSources().mobProjectile(this, getOwner() instanceof LivingEntity owner ? owner : null), damage);
        if (target instanceof Player player && RebirthConfig.SERVER_SPEC.isLoaded()) {
            float load = RebirthConfig.GHOUL_SPIT_TOXIN.get().floatValue() * (1.0f - HumanBody.protection(player, Hazard.POLLUTION));
            Biology now = HumanBody.biology(player);
            HumanBody.setBiology(player, new Biology(now.dose(), now.toxin() + Math.max(0.0f, load)));
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(SLIME, getX(), getY(), getZ(), 14, 0.3, 0.2, 0.3, 0.0);
            server.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY(), getZ(), 8, 0.2, 0.2, 0.2, 0.05);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 0.8f, 1.4f);
            discard();
        }
    }
}
