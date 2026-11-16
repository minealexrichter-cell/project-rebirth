package com.projectrebirth.hostile;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * The brute (1.6.0, part B3c): rare, slow and very strong. Its blow throws a player up into the air. From a distance it roars and
 * charges in a straight line, knocking down whoever it runs into (and stunning itself against a wall), or leaps onto its target
 * and lands with a shock that hurts and throws everyone around. Dead, it leaves its body, which radiates and rots away.
 */
public class BruteEntity extends MutantEntity {
    private static final int ROAR_TICKS = 16;
    private static final int CHARGE_TICKS = 36;
    private static final int STUN_TICKS = 40;

    private int chargeCooldown = 60;
    private int leapCooldown = 100;

    public BruteEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, "brute");
        xpReward = 30;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 160.0)
                .add(Attributes.ATTACK_DAMAGE, 18.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.21)
                .add(Attributes.STEP_HEIGHT, 1.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected List<String> extraAnimations() {
        return List.of("roar", "charge", "leap", "land", "stun");
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(1, new ChargeGoal());
        goalSelector.addGoal(1, new LeapGoal());
    }

    @Override
    protected void applyConfig() {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(RebirthConfig.BRUTE_HEALTH.get());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(RebirthConfig.BRUTE_DAMAGE.get());
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(RebirthConfig.BRUTE_SPEED.get());
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (chargeCooldown > 0) {
            chargeCooldown--;
        }
        if (leapCooldown > 0) {
            leapCooldown--;
        }
    }

    /** Its blow throws the player up and back. */
    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            toss(target, 0.6);
            playSound(SoundEvents.RAVAGER_ATTACK, 1.0f, 0.8f);
        }
        return hit;
    }

    private void toss(Entity target, double back) {
        double up = RebirthConfig.SERVER_SPEC.isLoaded() ? RebirthConfig.BRUTE_TOSS.get() : 1.05;
        Vec3 away = target.position().subtract(position()).multiply(1.0, 0.0, 1.0);
        away = away.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0.0f, getYRot()) : away.normalize();
        target.setDeltaMovement(away.x * back, up, away.z * back);
        target.hurtMarked = true;
    }

    private boolean fair(LivingEntity target) {
        return target != null && target.isAlive() && !(target instanceof Player player && (player.isCreative() || player.isSpectator()));
    }

    /** Roars, then runs straight at where the target was, hitting whoever is in its way; a wall stuns it. */
    private class ChargeGoal extends Goal {
        private int phase;
        private Vec3 direction = Vec3.ZERO;

        ChargeGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            if (chargeCooldown > 0 || !fair(target) || !onGround()) {
                return false;
            }
            double distance = distanceTo(target);
            return distance > 5.0 && distance < 18.0 && getSensing().hasLineOfSight(target) && Math.abs(target.getY() - getY()) < 2.5;
        }

        @Override
        public boolean canContinueToUse() {
            return phase > 0 && isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            phase = ROAR_TICKS + CHARGE_TICKS + STUN_TICKS;
            getNavigation().stop();
            triggerAnim("action", "roar");
            playSound(SoundEvents.RAVAGER_ROAR, 2.0f, 0.7f);
        }

        @Override
        public void stop() {
            phase = 0;
            chargeCooldown = RebirthConfig.SERVER_SPEC.isLoaded() ? RebirthConfig.BRUTE_CHARGE_COOLDOWN.get() : 140;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            int left = phase--;
            if (left > CHARGE_TICKS + STUN_TICKS) {
                // the roar: it turns to the target and paws the ground
                if (target != null) {
                    getLookControl().setLookAt(target, 30.0f, 30.0f);
                    Vec3 to = target.position().subtract(position()).multiply(1.0, 0.0, 1.0);
                    if (to.lengthSqr() > 1.0e-4) {
                        direction = to.normalize();
                        setYRot((float) (Math.atan2(-direction.x, direction.z) * 180.0 / Math.PI));
                        yBodyRot = getYRot();
                        yHeadRot = getYRot();
                    }
                }
                if (left == CHARGE_TICKS + STUN_TICKS + 1) {
                    triggerAnim("action", "charge");
                }
                return;
            }
            if (left > STUN_TICKS) {
                setDeltaMovement(direction.x * 0.75, getDeltaMovement().y, direction.z * 0.75);
                setYRot((float) (Math.atan2(-direction.x, direction.z) * 180.0 / Math.PI));
                yBodyRot = getYRot();
                if (tickCount % 4 == 0 && level() instanceof ServerLevel server) {
                    BlockState ground = level().getBlockState(blockPosition().below());
                    server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground.isAir() ? Blocks.DIRT.defaultBlockState() : ground),
                            getX(), getY() + 0.1, getZ(), 8, 0.5, 0.05, 0.5, 0.1);
                    playSound(SoundEvents.RAVAGER_STEP, 1.0f, 0.7f);
                }
                for (LivingEntity hit : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.4),
                        e -> e != BruteEntity.this && fair(e) && !(e instanceof MutantEntity))) {
                    float damage = RebirthConfig.SERVER_SPEC.isLoaded() ? RebirthConfig.BRUTE_CHARGE_DAMAGE.get().floatValue() : 16.0f;
                    hit.hurt(damageSources().mobAttack(BruteEntity.this), damage);
                    toss(hit, 1.6);
                    playSound(SoundEvents.RAVAGER_ATTACK, 1.5f, 0.6f);
                    phase = 0;
                    return;
                }
                if (horizontalCollision) {
                    // into a wall: it staggers, dazed
                    playSound(SoundEvents.ANVIL_LAND, 1.0f, 0.5f);
                    triggerAnim("action", "stun");
                    phase = STUN_TICKS - 1;
                    setDeltaMovement(-direction.x * 0.3, 0.2, -direction.z * 0.3);
                }
                return;
            }
            // dazed after a wall, or catching its breath after a miss
            getNavigation().stop();
            if (left == STUN_TICKS) {
                phase = Math.min(phase, 12);
            }
        }
    }

    /** Leaps onto a target that is too far or too high, and lands with a shock. */
    private class LeapGoal extends Goal {
        private boolean flying;
        private int ticks;

        LeapGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            if (leapCooldown > 0 || !fair(target) || !onGround()) {
                return false;
            }
            double distance = distanceTo(target);
            double rise = target.getY() - getY();
            return getSensing().hasLineOfSight(target) && (distance > 6.0 && distance < 14.0 && random.nextInt(3) == 0 || rise > 2.5 && distance < 12.0);
        }

        @Override
        public boolean canContinueToUse() {
            return flying && isAlive() && ticks < 60;
        }

        @Override
        public void start() {
            LivingEntity target = getTarget();
            Vec3 to = target.position().subtract(position());
            double flat = Math.sqrt(to.x * to.x + to.z * to.z);
            double time = Math.max(10.0, flat / 0.55);
            double up = Math.min(1.5, 0.08 * time * 0.5 + Math.max(0.0, to.y) / time + 0.4);
            setDeltaMovement(to.x / time, up, to.z / time);
            hasImpulse = true;
            flying = true;
            ticks = 0;
            getNavigation().stop();
            triggerAnim("action", "leap");
            playSound(SoundEvents.RAVAGER_ROAR, 1.6f, 1.0f);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            ticks++;
            if (ticks > 4 && onGround()) {
                land();
                flying = false;
            }
        }

        @Override
        public void stop() {
            flying = false;
            leapCooldown = RebirthConfig.SERVER_SPEC.isLoaded() ? RebirthConfig.BRUTE_LEAP_COOLDOWN.get() : 180;
        }

        private void land() {
            triggerAnim("action", "land");
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 0.6f);
            if (level() instanceof ServerLevel server) {
                BlockState ground = level().getBlockState(blockPosition().below());
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground.isAir() ? Blocks.DIRT.defaultBlockState() : ground),
                        getX(), getY() + 0.1, getZ(), 50, 1.5, 0.1, 1.5, 0.2);
            }
            float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.7f;
            for (LivingEntity hit : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(2.5, 0.5, 2.5),
                    e -> e != BruteEntity.this && fair(e) && !(e instanceof MutantEntity))) {
                hit.hurt(damageSources().mobAttack(BruteEntity.this), damage);
                toss(hit, 0.9);
            }
        }
    }

    /** Dead, it leaves its body, which radiates for a while. */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide && enabled()) {
            RemainsEntity.leave(this, RemainsEntity.Kind.BRUTE);
            dropExperience(source.getEntity());
            discard();
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.RAVAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.RAVAGER_STEP, 0.6f, 0.9f);
    }
}
