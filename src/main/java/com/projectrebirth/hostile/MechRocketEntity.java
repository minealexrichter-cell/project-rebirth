package com.projectrebirth.hostile;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * A rocket of the mech (1.6.0, part B3b): it rises from the pod and falls on the marked point along an arc, in a fixed number of
 * ticks, trailing smoke; it bursts there, or on the first block or player in its way. The blast does not hurt the mechanisms and
 * breaks no blocks unless the config says so. It is not saved: a rocket in flight when the world closes is simply gone.
 */
public class MechRocketEntity extends Entity implements software.bernie.geckolib.animatable.GeoEntity {
    private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache cache = software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);
    private Vec3 start = Vec3.ZERO;
    private Vec3 end = Vec3.ZERO;
    private int flight = 20;
    private UUID owner;
    /** 1.7.0: a rocket of a player's turret has its own blast (power above 0); the mech's takes the mech's config. */
    private float power = -1.0f;
    private boolean breaks;

    public MechRocketEntity(EntityType<? extends MechRocketEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public MechRocketEntity(Level level, LivingEntity owner, Vec3 start, Vec3 end, int flight) {
        this(HostileRegistry.MECH_ROCKET.get(), level);
        this.owner = owner.getUUID();
        this.start = start;
        this.end = end;
        this.flight = Math.max(4, flight);
        setPos(start);
    }

    /** A rocket of a player's machine (1.7.0): its blast and whether it breaks blocks. */
    public MechRocketEntity(Level level, LivingEntity owner, Vec3 start, Vec3 end, int flight, float power, boolean breaks) {
        this(level, owner, start, end, flight);
        this.power = power;
        this.breaks = breaks;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    /** 1.8.0: where it comes down (machines run from there). */
    public Vec3 end() {
        return end;
    }

    /** 1.8.0: who fired it, or null. */
    @javax.annotation.Nullable
    public UUID owner() {
        return owner;
    }

    /** Where it is at the given tick of its flight: on the line from the pod to the mark, lifted by an arc. */
    private Vec3 at(double tick) {
        double t = Mth.clamp(tick / flight, 0.0, 1.0);
        double lift = Math.max(4.0, start.distanceTo(end) * 0.45);
        return start.lerp(end, t).add(0.0, Math.sin(t * Math.PI) * lift, 0.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            Vec3 back = new Vec3(xo - getX(), yo - getY(), zo - getZ()).normalize().scale(0.4);
            level().addParticle(ParticleTypes.LARGE_SMOKE, getX() + back.x, getY() + back.y, getZ() + back.z, 0.0, 0.02, 0.0);
            level().addParticle(ParticleTypes.FLAME, getX() + back.x, getY() + back.y, getZ() + back.z, 0.0, 0.0, 0.0);
            return;
        }
        Vec3 from = position();
        Vec3 to = at(tickCount);
        setDeltaMovement(to.subtract(from));
        Vec3 motion = getDeltaMovement();
        if (motion.lengthSqr() > 1.0e-6) {
            setYRot((float) (Mth.atan2(-motion.x, motion.z) * Mth.RAD_TO_DEG));
            setXRot((float) (Mth.atan2(motion.y, motion.horizontalDistance()) * Mth.RAD_TO_DEG));
        }
        // halfway down it can hit what is in its way; before that it is still climbing out of the pod
        if (tickCount > flight / 2) {
            HitResult block = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (block.getType() != HitResult.Type.MISS) {
                burst(block.getLocation());
                return;
            }
            Entity hit = level().getEntities(this, getBoundingBox().expandTowards(motion).inflate(0.3),
                    e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && (power > 0.0f ? mayHit(e) : !Gunnery.isMechanism(e)))
                    .stream().findFirst().orElse(null);
            if (hit != null) {
                burst(hit.position());
                return;
            }
        }
        setPos(to);
        if (tickCount >= flight) {
            burst(to);
        } else if (tickCount > flight + 40) {
            discard();
        }
    }

    /** A rocket of a player's machine bursts on the way only on what that machine may attack. */
    private boolean mayHit(Entity e) {
        Entity shooter = owner != null && level() instanceof net.minecraft.server.level.ServerLevel server ? server.getEntity(owner) : null;
        return shooter instanceof com.projectrebirth.garrison.GarrisonMachine machine && com.projectrebirth.garrison.TargetRules.mayAttack(machine, e);
    }

    private void burst(Vec3 at) {
        Entity source = owner != null && level() instanceof net.minecraft.server.level.ServerLevel server ? server.getEntity(owner) : null;
        discard();
        boolean own = this.power > 0.0f;
        boolean breaks = own ? this.breaks : RebirthConfig.SERVER_SPEC.isLoaded() && RebirthConfig.MECH_ROCKETS_BREAK.get();
        float power = own ? this.power : RebirthConfig.SERVER_SPEC.isLoaded() ? RebirthConfig.MECH_ROCKET_POWER.get().floatValue() : 2.0f;
        level().explode(source, at.x, at.y, at.z, power, breaks ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new software.bernie.geckolib.animation.AnimationController<>(this, "main", 0,
                state -> state.setAndContinue(software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.mech_rocket.idle"))));
    }

    @Override
    public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
