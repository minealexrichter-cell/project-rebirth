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
                .add(Attributes.MAX_HEALTH, 45.0)
                .add(Attributes.ATTACK_DAMAGE, 7.0)
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
    protected java.util.List<String> extraAnimations() {
        return java.util.List.of("spit");
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(1, new SpitGoal());
    }

    /** From a distance it spits toxic slime; up close it claws as before. */
    private class SpitGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private int cooldown = 40;
        private int windup = -1;

        SpitGoal() {
            setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            net.minecraft.world.entity.LivingEntity target = getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            double distance = distanceTo(target);
            return distance > 3.5 && distance < 16.0 && getSensing().hasLineOfSight(target);
        }

        @Override
        public boolean canContinueToUse() {
            return windup >= 0 && getTarget() != null && getTarget().isAlive();
        }

        @Override
        public void start() {
            windup = 12;
            getNavigation().stop();
            triggerAnim("action", "spit");
            playSound(SoundEvents.ZOMBIE_VILLAGER_CONVERTED, 0.7f, 1.6f);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            net.minecraft.world.entity.LivingEntity target = getTarget();
            if (target == null) {
                windup = -1;
                return;
            }
            getLookControl().setLookAt(target, 30.0f, 30.0f);
            if (--windup < 0) {
                ToxicSpitEntity spit = new ToxicSpitEntity(level(), GlowingGhoulEntity.this);
                spit.setPos(getX(), getEyeY() - 0.2, getZ());
                double dx = target.getX() - getX(), dz = target.getZ() - getZ();
                double dy = target.getY(0.5) - spit.getY();
                double flat = Math.sqrt(dx * dx + dz * dz);
                spit.shoot(dx, dy + flat * 0.12, dz, 1.1f, 4.0f);
                level().addFreshEntity(spit);
                playSound(SoundEvents.LLAMA_SPIT, 1.0f, 0.6f);
                cooldown = RebirthConfig.SERVER_SPEC.isLoaded() ? RebirthConfig.GHOUL_SPIT_INTERVAL.get() : 70;
            }
        }
    }

    /** Dead, it leaves a toxic puddle that dries up by itself. */
    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server && enabled()) {
            net.minecraft.world.level.block.state.BlockState puddle = com.projectrebirth.environment.SourceRegistry.TOXIC_PUDDLE.get().defaultBlockState();
            BlockPos centre = blockPosition();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if ((dx != 0 || dz != 0) && random.nextInt(3) == 0) {
                        continue;
                    }
                    for (int dy = 1; dy >= -2; dy--) {
                        BlockPos at = centre.offset(dx, dy, dz);
                        if (server.getBlockState(at).canBeReplaced() && server.getFluidState(at).isEmpty() && puddle.canSurvive(server, at)) {
                            server.setBlock(at, puddle, 3);
                            break;
                        }
                    }
                }
            }
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_DEATH, net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 0.6f);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && random.nextInt(3) == 0) {
            level().addParticle(GLOW, getRandomX(0.6), getRandomY(), getRandomZ(0.6), 0.0, 0.02, 0.0);
        }
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
