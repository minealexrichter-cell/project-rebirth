package com.projectrebirth.hostile;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.structures.StructureBlocks;
import com.projectrebirth.structures.TurretBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The guard turret of the ruins (1.6.0, part B3a): it does not move; its base keeps the way it was set, its head turns all
 * round and its barrel tilts. At rest it sweeps its sector. A player it can see within range gets a warning beep, a moment of
 * aiming, then bursts of bullets, which work like the mod's weapons (a ray with spread; a pilot is hit through the armor).
 * Destroyed, it leaves the broken turret's three blocks where it stood and drops scrap. Numbers in the config ([hostile]).
 */
public class GuardTurretEntity extends Monster implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.guard_turret.idle");
    private static final RawAnimation SHOT = RawAnimation.begin().thenPlay("animation.guard_turret.shot");
    /** The height of the barrel's axis and how far ahead of the head the muzzle is, in blocks. */
    private static final double MUZZLE_HEIGHT = 1.5;
    private static final double MUZZLE_AHEAD = 1.55;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean configured;
    private int lockTicks;
    private int cooldown;
    private int shotsLeft;
    private LivingEntity lastTarget;

    public GuardTurretEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 20.0);
    }

    /** Whether turrets fight at all now: both switches of the config. */
    public static boolean enabled() {
        return !RebirthConfig.SERVER_SPEC.isLoaded() || (RebirthConfig.HOSTILE_ENABLED.get() && RebirthConfig.TURRET_ENABLED.get());
    }

    @Override
    protected void registerGoals() {
        // players are looked for by the turret itself (see findTarget); what hurts it is answered
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    /** The body never turns after the head: the base stays as it was set. */
    @Override
    protected BodyRotationControl createBodyControl() {
        return new BodyRotationControl(this) {
            @Override
            public void clientTick() {
            }
        };
    }

    @Override
    public int getMaxHeadYRot() {
        return 180;
    }

    @Override
    public int getMaxHeadXRot() {
        return 60;
    }

    @Override
    public int getHeadRotSpeed() {
        return RebirthConfig.SERVER_SPEC.isLoaded() ? RebirthConfig.TURRET_TURN_SPEED.get().intValue() : 12;
    }

    // a machine bolted to the ground: no pushing, no fall damage, no leash, no despawning

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    /** The mechanisms do not hurt each other; a stray bullet of another one does nothing. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (Gunnery.isMechanism(source.getEntity())) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void tick() {
        super.tick();
        yBodyRot = getYRot();
        yBodyRotO = getYRot();
        setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!configured) {
            configure();
        }
        if (!enabled()) {
            becomeWreck(false);
            return;
        }
        double range = RebirthConfig.TURRET_RANGE.get();
        if (tickCount % 5 == 0) {
            findTarget(range);
        }
        LivingEntity target = getTarget();
        boolean valid = target != null && target.isAlive() && distanceToSqr(target) <= range * range && hasLineOfSight(target)
                && !(target instanceof Player player && (player.isCreative() || player.isSpectator()));
        if (!valid) {
            lockTicks = 0;
            shotsLeft = 0;
            lastTarget = null;
            sweep();
            return;
        }
        getLookControl().setLookAt(target.getX(), target.getEyeY() - 0.2, target.getZ(), getHeadRotSpeed(), getMaxHeadXRot());
        if (target != lastTarget) {
            lastTarget = target;
            lockTicks = 0;
            playSound(SoundEvents.NOTE_BLOCK_BIT.value(), 1.2f, 1.8f); // the warning: it has seen you
        }
        if (lockTicks < RebirthConfig.TURRET_LOCK_TICKS.get()) {
            lockTicks++;
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (!aimedAt(target, 10.0)) {
            return;
        }
        if (shotsLeft <= 0) {
            shotsLeft = RebirthConfig.TURRET_BURST.get();
        }
        fire(target);
        shotsLeft--;
        cooldown = shotsLeft > 0 ? RebirthConfig.TURRET_SHOT_INTERVAL.get() : RebirthConfig.TURRET_BURST_PAUSE.get();
    }

    /** The nearest player in range it can see, not in creative or spectating; keeps its target while that one stays fair. */
    private void findTarget(double range) {
        LivingEntity current = getTarget();
        if (current != null && current.isAlive() && distanceToSqr(current) <= range * range && hasLineOfSight(current)
                && !(current instanceof Player player && (player.isCreative() || player.isSpectator()))) {
            return;
        }
        setTarget(Gunnery.nearestVisiblePlayer(this, range));
    }

    /** Health, armor and sight from the config, once. */
    private void configure() {
        configured = true;
        boolean fresh = getHealth() >= getMaxHealth();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(RebirthConfig.TURRET_HEALTH.get());
        getAttribute(Attributes.ARMOR).setBaseValue(RebirthConfig.TURRET_ARMOR.get());
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(RebirthConfig.TURRET_RANGE.get());
        if (fresh) {
            setHealth(getMaxHealth());
        }
    }

    /** At rest the head sweeps sixty degrees each way of the way the base looks. */
    private void sweep() {
        float yaw = getYRot() + 60.0f * Mth.sin(tickCount * 0.025f);
        double rad = yaw * Mth.DEG_TO_RAD;
        getLookControl().setLookAt(getX() - Math.sin(rad) * 8.0, getEyeY(), getZ() + Math.cos(rad) * 8.0, 3.0f, 10.0f);
    }

    /** The way the barrel points: the head's turn and the barrel's tilt. */
    private Vec3 barrelDirection() {
        return Vec3.directionFromRotation(getXRot(), getYHeadRot());
    }

    public Vec3 muzzle() {
        return position().add(0.0, MUZZLE_HEIGHT, 0.0).add(barrelDirection().scale(MUZZLE_AHEAD));
    }

    private boolean aimedAt(LivingEntity target, double degrees) {
        Vec3 to = target.getEyePosition().subtract(position().add(0.0, MUZZLE_HEIGHT, 0.0)).normalize();
        return barrelDirection().dot(to) >= Math.cos(degrees * Mth.DEG_TO_RAD);
    }

    /** One bullet of the burst (see Gunnery). */
    private void fire(LivingEntity target) {
        Gunnery.fire(this, muzzle(), target, RebirthConfig.TURRET_SPREAD.get(), RebirthConfig.TURRET_DAMAGE.get().floatValue(),
                RebirthConfig.TURRET_RANGE.get() + 4.0, 0.9f);
        triggerAnim("main", "shot");
    }

    /** Killed: smoke and sparks, then the broken turret's blocks where it stood. */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide) {
            becomeWreck(true);
        }
    }

    /**
     * Leaves the broken turret in its place (base, tower over it, barrel in front of the tower, where there is room) and goes.
     * With {@code killed} false (the config switched turrets off) it goes quietly, without loot.
     */
    public void becomeWreck(boolean killed) {
        Level level = level();
        BlockPos base = blockPosition();
        Direction facing = Direction.fromYRot(getYRot());
        BlockState state = StructureBlocks.WRECKED_TURRET.get().defaultBlockState().setValue(TurretBlock.FACING, facing);
        if (level.getBlockState(base).canBeReplaced() && level.getBlockState(TurretBlock.towerOf(base)).canBeReplaced()
                && level.getBlockState(TurretBlock.barrelOf(base, facing)).canBeReplaced()) {
            level.setBlock(base, state.setValue(TurretBlock.PART, TurretBlock.Part.BASE), 3);
            level.setBlock(TurretBlock.towerOf(base), state.setValue(TurretBlock.PART, TurretBlock.Part.TOWER), 3);
            level.setBlock(TurretBlock.barrelOf(base, facing), state.setValue(TurretBlock.PART, TurretBlock.Part.BARREL), 3);
        }
        if (level instanceof ServerLevel server && killed) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.2, getZ(), 20, 0.4, 0.5, 0.4, 0.02);
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.4, getZ(), 15, 0.4, 0.4, 0.4, 0.2);
            level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8f, 1.4f);
        }
        discard();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Configured", configured);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // the config is applied again after loading, in case it changed; health is kept
        configured = false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 2, state -> state.setAndContinue(IDLE))
                .triggerableAnim("shot", SHOT));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
