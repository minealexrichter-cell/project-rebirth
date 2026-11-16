package com.projectrebirth.hostile;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

/**
 * A walking machine with a gun (1.6.0, part B3b): the patrol robot and the mech. It keeps to its post (the place the ruins set
 * it, saved with it), walks round it at rest, and goes for a player it sees: closes in to shooting distance, then fires bursts
 * like the guard turret (see {@link Gunnery}). It never leaves its post further than its radius. Each kind adds its own blows
 * up close and specials through {@link #closeCombat}. The mechanisms do not hurt each other.
 */
public abstract class WalkingGunner extends Monster implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean configured;
    private BlockPos post;
    private int cooldown;
    private int shotsLeft;
    private int lockTicks;
    private LivingEntity lastTarget;

    protected WalkingGunner(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    // what each kind sets

    protected abstract boolean enabled();

    /** Health, armor, speed and the rest of the attributes from the config. */
    protected abstract void applyConfig();

    protected abstract double range();

    protected abstract int postRadius();

    protected abstract double spread();

    protected abstract float bulletDamage();

    protected abstract int burst();

    protected abstract int shotInterval();

    protected abstract int burstPause();

    /** Ticks of aiming after it first sees a target, before the first burst. */
    protected abstract int lockTicks();

    protected abstract Vec3 muzzle();

    protected abstract float shotPitch();

    /** Called when a bullet leaves the gun; the kind plays its animation. */
    protected abstract void onShot();

    /**
     * Its own blows and specials against the target, every tick of the fight. Returns true when it is busy with them, so it
     * neither walks nor shoots this tick.
     */
    protected boolean closeCombat(LivingEntity target, double distance, boolean sees) {
        return false;
    }

    /** At this distance and closer it stops to shoot; further it walks closer. */
    protected double shootingDistance() {
        return range() * 0.6;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new FightGoal());
        goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.8));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6, 0.002f));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (Gunnery.isMechanism(source.getEntity()) || Gunnery.isMechanism(source.getDirectEntity())) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!configured) {
            configured = true;
            boolean fresh = getHealth() >= getMaxHealth();
            applyConfig();
            if (fresh) {
                setHealth(getMaxHealth());
            }
            if (post == null) {
                post = blockPosition();
            }
            restrictTo(post, postRadius());
        }
        if (!enabled()) {
            discard();
            return;
        }
        if (tickCount % 5 == 0) {
            findTarget();
        }
    }

    /** Whether a target is still worth fighting: alive, fair, within range of it and not too far from its post. */
    protected boolean fair(LivingEntity target) {
        if (target == null || !target.isAlive() || target instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        double reach = postRadius() + range();
        return distanceToSqr(target) <= range() * range() * 2.25 && (post == null || target.distanceToSqr(Vec3.atCenterOf(post)) <= reach * reach);
    }

    private void findTarget() {
        LivingEntity current = getTarget();
        if (current != null && fair(current)) {
            return;
        }
        Player seen = Gunnery.nearestVisiblePlayer(this, range());
        setTarget(seen != null && fair(seen) ? seen : null);
    }

    /** Whether its gun points at the target closely enough to fire. */
    protected boolean aimedAt(LivingEntity target, double degrees) {
        Vec3 to = target.getEyePosition().subtract(getEyePosition());
        double wanted = Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG - 90.0;
        return Math.abs(Mth.wrapDegrees(wanted - getYHeadRot())) <= degrees;
    }

    /** The direction "to the right" of where it faces, for placing its guns. */
    protected Vec3 right() {
        double yaw = yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
    }

    protected Vec3 forward() {
        double yaw = yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
    }

    public BlockPos post() {
        return post;
    }

    /** The fight: walk closer, stop at shooting distance, look at the target and fire bursts. */
    private class FightGoal extends Goal {
        FightGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return fair(getTarget());
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            lastTarget = null;
            lockTicks = 0;
            shotsLeft = 0;
            getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) {
                return;
            }
            double distance = Math.sqrt(distanceToSqr(target));
            boolean sees = getSensing().hasLineOfSight(target);
            getLookControl().setLookAt(target, 30.0f, 30.0f);
            if (target != lastTarget) {
                lastTarget = target;
                lockTicks = 0;
                playSound(SoundEvents.NOTE_BLOCK_BIT.value(), 1.2f, 1.5f);
            }
            if (closeCombat(target, distance, sees)) {
                getNavigation().stop();
                return;
            }
            boolean inPost = post == null || target.distanceToSqr(Vec3.atCenterOf(post)) <= (double) postRadius() * postRadius();
            if (!sees || distance > shootingDistance()) {
                if (inPost || !sees) {
                    getNavigation().moveTo(target, 1.0);
                } else {
                    getNavigation().stop();
                }
            } else {
                getNavigation().stop();
            }
            if (!sees || distance > range()) {
                lockTicks = 0;
                return;
            }
            if (lockTicks < lockTicks()) {
                lockTicks++;
                return;
            }
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            if (!aimedAt(target, 15.0)) {
                return;
            }
            if (shotsLeft <= 0) {
                shotsLeft = burst();
            }
            Gunnery.fire(WalkingGunner.this, muzzle(), target, spread(), bulletDamage(), range() + 4.0, shotPitch());
            onShot();
            shotsLeft--;
            cooldown = shotsLeft > 0 ? shotInterval() : burstPause();
        }
    }

    /** What it leaves when destroyed. */
    protected abstract RemainsEntity.Kind remainsKind();

    /** Destroyed, it leaves its wreck where it stood (taken apart with the wrench for parts) instead of falling over. */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide && isRemoved() == false) {
            RemainsEntity.leave(this, remainsKind());
            dropExperience(source.getEntity());
            discard();
        }
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
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 0.6f, 1.3f);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (post != null) {
            tag.put("Post", NbtUtils.writeBlockPos(post));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        post = NbtUtils.readBlockPos(tag, "Post").orElse(null);
        configured = false; // the config is applied again after loading, in case it changed; health is kept
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** Whether the hostile mechanisms are switched on at all. */
    protected static boolean hostileOn() {
        return !RebirthConfig.SERVER_SPEC.isLoaded() || RebirthConfig.HOSTILE_ENABLED.get();
    }
}
