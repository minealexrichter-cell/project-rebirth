package com.projectrebirth.garrison;

import javax.annotation.Nullable;

import com.projectrebirth.bodies.BodyGadgets;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.hostile.Gunnery;
import com.projectrebirth.hostile.MechRocketEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A defence turret of the player (1.7.0, block G; the concept's section 5.4): the guard turret of the ruins in service paint,
 * standing on a {@link TurretMountBlockEntity turret mount} that gives it energy and ammunition shot by shot. It looks for a target
 * every few ticks by the rules of targets, beeps, aims and fires: the machine gun in bursts through {@link Gunnery} (it never
 * hits its own), the rocket turret a rocket along an arc ({@link MechRocketEntity}; it breaks no blocks by default and is not
 * fired while one of its own is near the target). Out of energy or ammunition it stays silent and the console hears of it. Shut
 * down or a wreck, its barrel hangs. Upgrades (by the mounting tool): armor plating, an EMI screen, a sensor unit (range), a
 * stabilizer (spread). A dust storm spreads its fire, an EMI storm outside a shelter throws its aim off and now and then shuts
 * it down for a few seconds.
 */
public class DefenceTurretEntity extends MachineEntity implements GeoEntity {
    public static final String STABILIZER = "stabilizer";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.defence_turret.idle");
    private static final RawAnimation SHOT = RawAnimation.begin().thenPlay("animation.defence_turret.shot");
    private static final RawAnimation SHUTDOWN = RawAnimation.begin().thenPlayAndHold("animation.defence_turret.shutdown");
    private static final RawAnimation WRECKED = RawAnimation.begin().thenPlayAndHold("animation.defence_turret.wrecked");
    /** The height of the barrel's axis and how far ahead of the head the muzzle is, in blocks (as the guard turret's). */
    private static final double MUZZLE_HEIGHT = 1.5;
    private static final double MUZZLE_AHEAD = 1.55;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final TurretType turret;
    private int look;
    private int lockTicks;
    private int cooldown;
    private int shotsLeft;
    private int emiDown;
    private boolean lacking;
    @Nullable
    private LivingEntity lastTarget;

    public DefenceTurretEntity(EntityType<? extends DefenceTurretEntity> type, Level level, TurretType turret) {
        super(type, level, MachineKind.TURRET);
        this.turret = turret;
        if (RebirthConfig.SERVER_SPEC.isLoaded()) {
            machineCore().setLimits(turret.maxStructural(), turret.maxCritical());
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 48.0);
    }

    public TurretType turret() {
        return turret;
    }

    @Override
    protected Item packedItem() {
        return turret.rocket() ? GarrisonRegistry.ROCKET_TURRET_ITEM.get() : GarrisonRegistry.MACHINE_GUN_TURRET_ITEM.get();
    }

    // ------------------------------------------------------------------ bolted to the ground

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
        return RebirthConfig.SERVER_SPEC.isLoaded() ? RebirthConfig.TURRET_TURN.get().intValue() : 15;
    }

    @Override
    public boolean canBeCollidedWith() {
        return isAlive();
    }

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
    public void tick() {
        super.tick();
        yBodyRot = getYRot();
        yBodyRotO = getYRot();
        setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
    }

    // ------------------------------------------------------------------ the mount

    /** The mount it stands on, or null. */
    @Nullable
    public TurretMountBlockEntity mount() {
        BlockPos below = BlockPos.containing(getX(), getY() - 0.5, getZ());
        return level().getBlockEntity(below) instanceof TurretMountBlockEntity mount ? mount : null;
    }

    /** Whether it can shoot now: a mount with energy and ammunition for one shot. */
    public boolean supplied() {
        TurretMountBlockEntity mount = mount();
        return mount != null && mount.ready(turret.ammo(), turret.energyPerShot());
    }

    // ------------------------------------------------------------------ the mind

    private boolean has(String kind) {
        return BodyGadgets.has(registryAccess(), upgrades(), kind);
    }

    /** Its range: the type's, more with a sensor unit. */
    public double range() {
        return turret.range() + (has(com.projectrebirth.scouting.Scouting.SENSOR) ? RebirthConfig.TURRET_SENSOR_RANGE.get() : 0);
    }

    /** Its spread now: the machine gun's, less with a stabilizer, more in a dust storm and in an EMI storm. */
    public double spread() {
        MachineCore core = machineCore();
        double spread = turret.rocket() ? 1.0 : RebirthConfig.GUN_TURRET_SPREAD.get();
        if (has(STABILIZER)) {
            spread *= RebirthConfig.TURRET_STABILIZER_SPREAD.get();
        }
        if (core.dusty()) {
            spread *= RebirthConfig.TURRET_DUST_SPREAD.get();
        }
        if (core.emiFault()) {
            spread += RebirthConfig.TURRET_EMI_SPREAD.get();
        }
        return spread;
    }

    private boolean fair(@Nullable LivingEntity target) {
        if (target == null || !target.isAlive() || target.level() != level()) {
            return false;
        }
        double distance = distanceToSqr(target);
        double range = range();
        int min = turret.minRange();
        return distance <= range * range && distance >= (double) min * min && hasLineOfSight(target) && TargetRules.mayAttack(this, target);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        MachineCore core = machineCore();
        if (tickCount % 20 == 0) {
            second();
        }
        if (!core.active() || !RebirthConfig.TURRETS_ENABLED.get() || !RebirthConfig.GARRISON_ENABLED.get() || emiDown > 0) {
            if (emiDown > 0) {
                emiDown--;
            }
            setTarget(null);
            lockTicks = 0;
            return;
        }
        if (--look <= 0) {
            look = RebirthConfig.TURRET_LOOK_TICKS.get();
            if (!fair(getTarget())) {
                setTarget(nearestTarget());
            }
        }
        LivingEntity target = getTarget();
        if (!fair(target)) {
            setTarget(null);
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
            playSound(com.projectrebirth.core.ModSounds.GARRISON_TARGET_LOCK.get(), 1.0f, 1.0f);
        }
        if (lockTicks < turret.lockTicks()) {
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
        if (turret.rocket()) {
            if (launch(target)) {
                cooldown = RebirthConfig.ROCKET_TURRET_INTERVAL.get();
            } else {
                cooldown = 10;
            }
            return;
        }
        if (shotsLeft <= 0) {
            shotsLeft = RebirthConfig.GUN_TURRET_BURST.get();
        }
        if (!fire(target)) {
            shotsLeft = 0;
            cooldown = 20;
            return;
        }
        shotsLeft--;
        cooldown = shotsLeft > 0 ? RebirthConfig.GUN_TURRET_SHOT_TICKS.get() : RebirthConfig.GUN_TURRET_PAUSE_TICKS.get();
    }

    /** Once a second: the console hears whether it lacks energy or ammunition; an EMI storm may shut it down for a moment. */
    private void second() {
        MachineCore core = machineCore();
        boolean now = core.active() && !supplied();
        if (now != lacking) {
            lacking = now;
            ConsoleLink.supply(this, core, lacking);
        }
        if (core.active() && core.emiFault() && emiDown <= 0 && getRandom().nextDouble() < RebirthConfig.TURRET_EMI_SHUTDOWN_CHANCE.get()) {
            emiDown = 40 + getRandom().nextInt(21);
            if (level() instanceof ServerLevel server) {
                server.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.5, getZ(), 12, 0.3, 0.3, 0.3, 0.1);
            }
        }
    }

    /** The nearest target it may attack, can see and reach, or null. */
    @Nullable
    private LivingEntity nearestTarget() {
        double range = range();
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity found : level().getEntitiesOfClass(LivingEntity.class, new AABB(blockPosition()).inflate(range),
                found -> found != this && found.isAlive())) {
            double distance = distanceToSqr(found);
            if (distance < bestDistance && fair(found)) {
                best = found;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** At rest the head sweeps sixty degrees each way of the way the base looks. */
    private void sweep() {
        float yaw = getYRot() + 60.0f * Mth.sin(tickCount * 0.025f);
        double rad = yaw * Mth.DEG_TO_RAD;
        getLookControl().setLookAt(getX() - Math.sin(rad) * 8.0, getEyeY(), getZ() + Math.cos(rad) * 8.0, 3.0f, 10.0f);
    }

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

    /** One bullet, if the mount gives it; the bullet never hits its own. Public for tests. */
    public boolean fire(LivingEntity target) {
        TurretMountBlockEntity mount = mount();
        if (mount == null || !mount.takeShot(turret.ammo(), turret.energyPerShot())) {
            return false;
        }
        Gunnery.fire(this, muzzle(), target, spread(), RebirthConfig.GUN_TURRET_DAMAGE.get().floatValue(), range() + 4.0, 1.0f,
                e -> e != this && !TargetRules.friendly(this, e), SoundSource.NEUTRAL);
        triggerAnim("main", "shot");
        return true;
    }

    /** Whether one of its own is within the rocket's blast of the target. Public for tests. */
    public boolean friendNear(LivingEntity target) {
        double radius = RebirthConfig.ROCKET_TURRET_FRIEND_RADIUS.get();
        return !level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(radius),
                found -> found != target && found.isAlive() && (found == this || TargetRules.friendly(this, found))).isEmpty();
    }

    /** One rocket at the target's feet, unless one of its own is near it or the mount has nothing to give. Public for tests. */
    public boolean launch(LivingEntity target) {
        if (friendNear(target)) {
            return false;
        }
        TurretMountBlockEntity mount = mount();
        if (mount == null || !mount.takeShot(turret.ammo(), turret.energyPerShot())) {
            return false;
        }
        Vec3 from = muzzle();
        Vec3 to = target.position();
        int flight = 14 + (int) (from.distanceTo(to) * 0.6);
        level().addFreshEntity(new MechRocketEntity(level(), this, from, to, flight, RebirthConfig.ROCKET_TURRET_POWER.get().floatValue(),
                RebirthConfig.ROCKET_TURRET_BREAKS.get()));
        playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.4f, 0.7f);
        triggerAnim("main", "shot");
        return true;
    }

    // ------------------------------------------------------------------ saving

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        // the config's bars come first, so that what was saved is not cut by the kind's
        machineCore().setLimits(turret.maxStructural(), turret.maxCritical());
        super.readAdditionalSaveData(tag);
    }

    // ------------------------------------------------------------------ the look

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 1.7.1, block I: shut down the head tips over, a wreck slips half off its ring
        controllers.add(new AnimationController<>(this, "main", 2, state -> state.setAndContinue(switch (shownState()) {
            case WRECK -> WRECKED;
            case DISABLED -> SHUTDOWN;
            default -> IDLE;
        })).triggerableAnim("shot", SHOT));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
