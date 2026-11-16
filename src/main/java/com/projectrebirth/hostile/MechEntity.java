package com.projectrebirth.hostile;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.ArrayList;
import java.util.List;

/**
 * The mech (1.6.0, part B3b): one heavy walker guards the military base; a boss with a health bar. A machine gun on its right
 * arm fires long bursts; the rocket pod on its left shoulder now and then marks the ground under a player with red light and an
 * alarm, and a moment later rockets fall there (they do not break blocks unless the config says so); a player who comes up
 * close is thrown back by a stomp. Its steps shake the ground. Numbers in the config ([hostile.mech]).
 */
public class MechEntity extends WalkingGunner {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.mech.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.mech.walk");
    private static final RawAnimation SHOT = RawAnimation.begin().thenPlay("animation.mech.shot");
    private static final RawAnimation ROCKETS = RawAnimation.begin().thenPlay("animation.mech.rockets");
    private static final RawAnimation STOMP = RawAnimation.begin().thenPlay("animation.mech.stomp");
    private static final RawAnimation TRANSFORM = RawAnimation.begin().thenPlay("animation.mech.transform");
    private static final RawAnimation DASH = RawAnimation.begin().thenPlay("animation.mech.dash");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.mech.run");
    private static final RawAnimation IDLE_BERSERK = RawAnimation.begin().thenLoop("animation.mech.idle_berserk");
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> PHASE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(MechEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    private static final int TRANSFORM_TICKS = 60;
    private static final RawAnimation FOLDED = RawAnimation.begin().thenLoop("animation.mech.folded");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.mech.fly");
    private static final RawAnimation GATLING = RawAnimation.begin().thenPlay("animation.mech.gatling");
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> FLYING =
            net.minecraft.network.syncher.SynchedEntityData.defineId(MechEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    /** Flight in the second phase: how long it stays up, the rest between flights, how high over its target it hovers. */
    private static final int FLIGHT_TICKS = 160;
    private static final int FLIGHT_REST = 220;
    private static final double HOVER_HEIGHT = 5.0;
    private static final DustParticleOptions MARK = new DustParticleOptions(new Vector3f(1.0f, 0.1f, 0.05f), 1.6f);
    /** Ticks from the start of the stomp to the blow. */
    private static final int STOMP_WINDUP = 12;

    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private int rocketCooldown = 100;
    private int transform;
    private int dash;
    private int dashCooldown = 40;
    private int flight;
    private int flightCooldown = 60;
    private double orbit;
    private Vec3 dashDirection = Vec3.ZERO;
    private int warning = -1;
    private final List<Vec3> marks = new ArrayList<>();
    private int stompCooldown;
    private int stompWindup = -1;

    public MechEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 80;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0)
                .add(Attributes.ARMOR, 15.0)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.18)
                .add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.1)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected RemainsEntity.Kind remainsKind() {
        return RemainsEntity.Kind.MECH;
    }

    // ---------------------------------------------------------------- the second phase

    /** 0: armored, with the rocket pod; 1: shedding its armor (invulnerable for the while); 2: stripped, fast and furious. */
    public int phase() {
        return entityData.get(PHASE);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHASE, 0);
        builder.define(FLYING, false);
    }

    private void beginTransform() {
        entityData.set(PHASE, 1);
        transform = TRANSFORM_TICKS;
        warning = -1;
        marks.clear();
        stompWindup = -1;
        getNavigation().stop();
        triggerAnim("action", "transform");
        playSound(SoundEvents.IRON_GOLEM_DAMAGE, 3.0f, 0.4f);
        bossBar.setColor(BossEvent.BossBarColor.YELLOW);
    }

    private void tickTransform() {
        getNavigation().stop();
        setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
        if (level() instanceof ServerLevel server) {
            if (transform % 6 == 0) {
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 2.5, getZ(), 12, 1.0, 0.8, 1.0, 0.3);
                server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 2.5, getZ(), 4, 0.8, 0.6, 0.8, 0.02);
            }
            if (transform == TRANSFORM_TICKS - 30) {
                // the plates and the rocket pod blow off
                server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 2.6, getZ(), 4, 1.0, 0.6, 1.0, 0.0);
                playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.5f, 0.8f);
            }
        }
        if (--transform <= 0) {
            entityData.set(PHASE, 2);
            bossBar.setColor(BossEvent.BossBarColor.RED);
            double speed = RebirthConfig.MECH_SPEED.get() * 1.5;
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
            getAttribute(Attributes.ARMOR).setBaseValue(Math.max(0.0, RebirthConfig.MECH_ARMOR.get() - 7.0));
            playSound(SoundEvents.WARDEN_ROAR, 2.5f, 1.4f);
        }
    }

    public boolean isFlying() {
        return entityData.get(FLYING);
    }

    @Override
    protected boolean hovering() {
        return isFlying();
    }

    /**
     * Flight on the jetpack: it rises over its target and circles it there, firing the gatling, then comes down with a shock
     * that hurts and throws whoever stands below. It takes off when its target climbs out of reach or now and then anyway.
     */
    private boolean fly(LivingEntity target, boolean sees) {
        if (isFlying()) {
            flight--;
            orbit += 0.035;
            Vec3 goal = target.position().add(Math.cos(orbit) * 6.0, HOVER_HEIGHT, Math.sin(orbit) * 6.0);
            Vec3 to = goal.subtract(position());
            Vec3 velocity = getDeltaMovement().scale(0.85).add(to.scale(0.025));
            double speed = velocity.length();
            if (speed > 0.45) {
                velocity = velocity.scale(0.45 / speed);
            }
            setDeltaMovement(velocity);
            getLookControl().setLookAt(target, 30.0f, 30.0f);
            yBodyRot = yHeadRot;
            setYRot(yHeadRot);
            fallDistance = 0.0f;
            if (flight <= 0 || !target.isAlive()) {
                land();
            }
            return false;
        }
        if (flightCooldown > 0) {
            flightCooldown--;
            return false;
        }
        boolean outOfReach = target.getY() - getY() > 2.5;
        if (outOfReach || sees && random.nextInt(60) == 0) {
            entityData.set(FLYING, true);
            setNoGravity(true);
            flight = FLIGHT_TICKS;
            orbit = Math.atan2(getZ() - target.getZ(), getX() - target.getX());
            setDeltaMovement(getDeltaMovement().add(0.0, 0.7, 0.0));
            getNavigation().stop();
            playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 3.0f, 0.5f);
            playSound(SoundEvents.BLAZE_SHOOT, 2.0f, 0.5f);
        }
        return false;
    }

    private void land() {
        entityData.set(FLYING, false);
        setNoGravity(false);
        flightCooldown = FLIGHT_REST;
        landing = true;
    }

    private boolean landing;

    /** In the second phase it closes in, sprays long bursts and rams its target. */
    private boolean berserk(LivingEntity target, double distance, boolean sees) {
        fly(target, sees);
        if (isFlying()) {
            return false;
        }
        if (dash > 0) {
            dash--;
            setDeltaMovement(dashDirection.x * 0.62, getDeltaMovement().y, dashDirection.z * 0.62);
            setYRot((float) (Math.atan2(-dashDirection.x, dashDirection.z) * Mth.RAD_TO_DEG));
            yBodyRot = getYRot();
            if (tickCount % 3 == 0) {
                playSound(SoundEvents.IRON_GOLEM_STEP, 2.0f, 0.6f);
            }
            for (LivingEntity hit : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.5),
                    e -> e != this && Gunnery.fair(e))) {
                hit.hurt(damageSources().mobAttack(this), RebirthConfig.MECH_STOMP_DAMAGE.get().floatValue() * 1.2f);
                Vec3 away = hit.position().subtract(position()).multiply(1.0, 0.0, 1.0).normalize();
                hit.setDeltaMovement(away.x * 1.8, 0.7, away.z * 1.8);
                hit.hurtMarked = true;
                playSound(SoundEvents.ANVIL_LAND, 1.5f, 0.5f);
                dash = 0;
            }
            if (horizontalCollision) {
                dash = 0;
            }
            return true;
        }
        if (dashCooldown > 0) {
            dashCooldown--;
        } else if (sees && distance > 6.0 && distance < 20.0 && onGround()) {
            Vec3 to = target.position().subtract(position()).multiply(1.0, 0.0, 1.0);
            dashDirection = to.normalize();
            dash = 22;
            dashCooldown = 120;
            triggerAnim("action", "dash");
            playSound(SoundEvents.WARDEN_SONIC_CHARGE, 2.0f, 0.8f);
            return true;
        }
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (phase() == 1 && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    protected boolean enabled() {
        return hostileOn() && (!RebirthConfig.SERVER_SPEC.isLoaded() || RebirthConfig.MECH_ENABLED.get());
    }

    @Override
    protected void applyConfig() {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(RebirthConfig.MECH_HEALTH.get());
        getAttribute(Attributes.ARMOR).setBaseValue(RebirthConfig.MECH_ARMOR.get());
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(RebirthConfig.MECH_SPEED.get());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(RebirthConfig.MECH_STOMP_DAMAGE.get());
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(RebirthConfig.MECH_RANGE.get() + 8.0);
        if (phase() == 2) {
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(RebirthConfig.MECH_SPEED.get() * 1.5);
            getAttribute(Attributes.ARMOR).setBaseValue(Math.max(0.0, RebirthConfig.MECH_ARMOR.get() - 7.0));
        }
    }

    @Override
    protected double range() {
        return RebirthConfig.MECH_RANGE.get();
    }

    @Override
    protected int postRadius() {
        return RebirthConfig.MECH_RADIUS.get();
    }

    @Override
    protected double spread() {
        return RebirthConfig.MECH_SPREAD.get();
    }

    @Override
    protected float bulletDamage() {
        return RebirthConfig.MECH_DAMAGE.get().floatValue();
    }

    @Override
    protected int burst() {
        return phase() == 2 ? RebirthConfig.MECH_BURST.get() * 2 : RebirthConfig.MECH_BURST.get();
    }

    @Override
    protected int shotInterval() {
        return phase() == 2 ? Math.max(1, RebirthConfig.MECH_SHOT_INTERVAL.get() - 1) : RebirthConfig.MECH_SHOT_INTERVAL.get();
    }

    @Override
    protected int burstPause() {
        return phase() == 2 ? RebirthConfig.MECH_BURST_PAUSE.get() / 2 : RebirthConfig.MECH_BURST_PAUSE.get();
    }

    @Override
    protected int lockTicks() {
        return 20;
    }

    @Override
    protected double shootingDistance() {
        return phase() == 2 ? 5.0 : range() * 0.5;
    }

    /** The machine gun under its right arm; in the second phase the gatling over its left shoulder. */
    @Override
    protected Vec3 muzzle() {
        if (phase() == 2) {
            return position().add(0.0, 3.31, 0.0).add(right().scale(-0.59)).add(forward().scale(1.35));
        }
        return position().add(0.0, 2.22, 0.0).add(right().scale(0.9)).add(forward().scale(1.8));
    }

    /** The rocket pod on its left shoulder. */
    private Vec3 pod() {
        return position().add(0.0, 3.05, 0.0).add(right().scale(-1.15)).add(forward().scale(-0.4));
    }

    @Override
    protected float shotPitch() {
        return 0.7f;
    }

    @Override
    protected void onShot() {
        triggerAnim("action", phase() == 2 ? "gatling" : "shot");
    }

    @Override
    protected boolean closeCombat(LivingEntity target, double distance, boolean sees) {
        if (phase() == 1) {
            return true;
        }
        if (stompCooldown > 0) {
            stompCooldown--;
        }
        if (stompWindup >= 0) {
            if (--stompWindup < 0) {
                stomp();
            }
            return true;
        }
        if (!isFlying() && distance <= RebirthConfig.MECH_STOMP_RADIUS.get() + getBbWidth() * 0.5 && stompCooldown <= 0) {
            stompWindup = STOMP_WINDUP;
            stompCooldown = phase() == 2 ? 30 : 60;
            triggerAnim("action", "stomp");
            playSound(SoundEvents.IRON_GOLEM_REPAIR, 1.5f, 0.5f);
            return true;
        }
        if (phase() == 2) {
            return berserk(target, distance, sees);
        }
        rockets(target, sees);
        return false;
    }

    /** The salvo: marks with a warning first, then the rockets, which fly whatever the target does. */
    private void rockets(LivingEntity target, boolean sees) {
        if (warning >= 0) {
            if (warning % 4 == 0 && level() instanceof ServerLevel server) {
                for (Vec3 mark : marks) {
                    ring(server, mark);
                }
                server.playSound(null, getX(), getY() + 3.0, getZ(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.HOSTILE, 2.0f, 2.0f);
            }
            if (--warning < 0) {
                launch();
            }
            return;
        }
        if (rocketCooldown > 0) {
            rocketCooldown--;
            return;
        }
        if (!sees) {
            return;
        }
        marks.clear();
        Vec3 at = ground(target.position());
        marks.add(at);
        for (int i = 1; i < RebirthConfig.MECH_ROCKETS.get(); i++) {
            double a = getRandom().nextDouble() * Math.PI * 2.0, r = 1.5 + getRandom().nextDouble() * 2.0;
            marks.add(ground(at.add(Math.cos(a) * r, 0.0, Math.sin(a) * r)));
        }
        warning = RebirthConfig.MECH_ROCKET_WARNING.get();
        rocketCooldown = RebirthConfig.MECH_ROCKET_INTERVAL.get();
        playSound(SoundEvents.NOTE_BLOCK_BIT.value(), 3.0f, 0.6f);
    }

    /** The top of the ground under a point: where the rocket will land. */
    private Vec3 ground(Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        for (int i = 0; i < 6 && level().getBlockState(pos.below()).getCollisionShape(level(), pos.below()).isEmpty(); i++) {
            pos = pos.below();
        }
        return new Vec3(point.x, pos.getY() + 0.05, point.z);
    }

    private static void ring(ServerLevel server, Vec3 mark) {
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8.0;
            server.sendParticles(MARK, mark.x + Math.cos(a) * 1.4, mark.y + 0.1, mark.z + Math.sin(a) * 1.4, 1, 0.0, 0.0, 0.0, 0.0);
        }
        server.sendParticles(MARK, mark.x, mark.y + 0.1, mark.z, 4, 0.3, 0.0, 0.3, 0.0);
    }

    private void launch() {
        triggerAnim("action", "rockets");
        Vec3 from = pod();
        for (int i = 0; i < marks.size(); i++) {
            MechRocketEntity rocket = new MechRocketEntity(level(), this, from.add(0.0, 0.0, 0.0), marks.get(i), 22 + i * 5);
            level().addFreshEntity(rocket);
        }
        playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 3.0f, 0.6f);
        marks.clear();
    }

    /** The stomp: the players close by are hurt and thrown away from it. */
    private void stomp() {
        double radius = RebirthConfig.MECH_STOMP_RADIUS.get() + getBbWidth() * 0.5;
        Vec3 centre = position().add(forward().scale(0.6));
        float damage = RebirthConfig.MECH_STOMP_DAMAGE.get().floatValue();
        for (Player player : level().getEntitiesOfClass(Player.class, new AABB(centre, centre).inflate(radius, 2.0, radius), Gunnery::fair)) {
            if (player.distanceToSqr(centre) > radius * radius + 2.0) {
                continue;
            }
            player.hurt(damageSources().mobAttack(this), damage);
            Vec3 away = player.position().subtract(position()).multiply(1.0, 0.0, 1.0);
            away = away.lengthSqr() < 1.0e-4 ? forward() : away.normalize();
            player.push(away.x * 1.4, 0.55, away.z * 1.4);
            player.hurtMarked = true;
        }
        if (level() instanceof ServerLevel server) {
            BlockState under = level().getBlockState(blockPosition().below());
            if (under.isAir()) {
                under = Blocks.STONE.defaultBlockState();
            }
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), centre.x, getY() + 0.1, centre.z, 60, radius * 0.5, 0.1, radius * 0.5, 0.2);
            server.sendParticles(ParticleTypes.EXPLOSION, centre.x, getY() + 0.3, centre.z, 2, 0.6, 0.0, 0.6, 0.0);
        }
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.6f);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 2.0f, 0.5f);
        playSound(SoundEvents.ANVIL_LAND, 0.25f, 0.5f);
    }

    @Override
    protected float nextStep() {
        return moveDist + 1.6f; // long, heavy steps
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (phase() == 0 && getHealth() <= getMaxHealth() * 0.5f && isAlive()) {
            beginTransform();
        }
        if (phase() == 1) {
            tickTransform();
        }
        if (isFlying() && (getTarget() == null || phase() != 2)) {
            land();
        }
        if (landing && onGround()) {
            landing = false;
            stomp();
        }
        if (isFlying() && level() instanceof ServerLevel server && tickCount % 2 == 0) {
            for (int side = -1; side <= 1; side += 2) {
                Vec3 jet = position().add(0.0, 1.85, 0.0).add(forward().scale(-0.85)).add(right().scale(side * 0.37));
                server.sendParticles(ParticleTypes.FLAME, jet.x, jet.y, jet.z, 3, 0.05, 0.05, 0.05, 0.02);
                server.sendParticles(ParticleTypes.LARGE_SMOKE, jet.x, jet.y - 0.3, jet.z, 1, 0.1, 0.1, 0.1, 0.01);
            }
            if (tickCount % 10 == 0) {
                playSound(SoundEvents.BLAZE_BURN, 1.5f, 0.5f);
            }
        }
        bossBar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0.0f, 1.0f));
        if (getTarget() == null) {
            warning = -1;
            stompWindup = -1;
            marks.clear();
        }
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Phase", phase() == 1 ? 2 : phase());
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(PHASE, tag.getInt("Phase"));
        setNoGravity(false); // a flight is not saved: it comes down where it was
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    /** Killed: it bursts in smoke and sparks. */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 2.0, getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 2.0, getZ(), 40, 1.0, 1.2, 1.0, 0.03);
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 2.5, getZ(), 30, 1.0, 1.0, 1.0, 0.3);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0f, 0.7f);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5,
                state -> state.setAndContinue(isFlying() ? FLY : state.isMoving() ? (phase() == 2 ? RUN : WALK) : (phase() == 2 ? IDLE_BERSERK : IDLE))));
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("shot", SHOT)
                .triggerableAnim("rockets", ROCKETS)
                .triggerableAnim("stomp", STOMP)
                .triggerableAnim("transform", TRANSFORM)
                .triggerableAnim("dash", DASH)
                .triggerableAnim("gatling", GATLING));
        // the gatling stays folded behind the back until the transform raises it
        controllers.add(new AnimationController<>(this, "gun", 0, state -> phase() == 0 ? state.setAndContinue(FOLDED) : PlayState.STOP));
    }
}
