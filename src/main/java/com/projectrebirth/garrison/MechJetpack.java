package com.projectrebirth.garrison;

import javax.annotation.Nullable;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * 1.7.2, block C: the guard mech's jetpack, after its prototype's. Two kinds of flight:
 * <ul>
 * <li>a hop: when it cannot get on on foot (its mover asks, see {@link MachineMover.Lift}), it rises over the highest ground on
 * the way and flies to a place past the obstacle (at most hopRange blocks on), then drops;</li>
 * <li>over a fight: when its target is out of its reach on foot, or now and then, it rises over the target and circles it, its
 * guns firing, for flightSeconds.</li>
 * </ul>
 * Either way it lands with a stomp. Flight uses energy; with too little it does not take off, and it comes down when its energy or
 * its machine core fails. Between two flights it rests. Numbers in the config ([garrison.mech]).
 */
final class MechJetpack {
    enum Mode {
        NONE, HOP, COMBAT
    }

    private static final double CLIMB = 0.35;
    private static final double CRUISE = 0.45;

    private final GuardMechEntity mech;
    private Mode mode = Mode.NONE;
    private Vec3 destination = Vec3.ZERO;
    private double cruiseY;
    private int ticks;
    private int rest;
    private double orbit;
    private boolean landing;

    MechJetpack(GuardMechEntity mech) {
        this.mech = mech;
    }

    boolean flying() {
        return mode != Mode.NONE;
    }

    Mode mode() {
        return mode;
    }

    /** Whether it may take off now: it can fly, rests no more, stands on the ground and has energy for a flight to spare. */
    boolean ready() {
        if (!mech.canFly() || flying() || rest > 0 || !mech.onGround() || !mech.working() || mech.isRollingOut()) {
            return false;
        }
        double flight = RebirthConfig.GUARD_MECH_FLIGHT_ENERGY.get() * RebirthConfig.GUARD_MECH_FLIGHT_SECONDS.get();
        return mech.energy() - flight > mech.capacity() * mech.lowEnergyShare();
    }

    /** A hop over what stands in the way to the goal; whether it took off. */
    boolean hop(Vec3 goal) {
        if (!ready()) {
            return false;
        }
        Vec3 flat = goal.subtract(mech.position()).multiply(1, 0, 1);
        double length = flat.horizontalDistance();
        if (length < 1.0) {
            return false;
        }
        double range = RebirthConfig.GUARD_MECH_HOP_RANGE.get();
        Vec3 to = length > range ? mech.position().add(flat.scale(range / length)) : new Vec3(goal.x, mech.getY(), goal.z);
        // the highest ground under the way, as wide as its body, and where it comes down
        double highest = mech.getY();
        int steps = (int) Math.ceil(Math.min(length, range));
        Vec3 side = new Vec3(-flat.z, 0, flat.x).normalize().scale(mech.getBbWidth() * 0.5);
        for (int i = 0; i <= steps; i++) {
            Vec3 at = mech.position().add(flat.scale(Math.min(1.0, (double) i / Math.max(1, steps)) * Math.min(length, range) / length));
            for (int k = -1; k <= 1; k++) {
                Vec3 point = at.add(side.scale(k));
                highest = Math.max(highest, mech.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(point.x), Mth.floor(point.z)));
            }
        }
        BlockPos down = BlockPos.containing(to.x, mech.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(to.x), Mth.floor(to.z)) - 1, to.z);
        if (!mech.level().getFluidState(down).isEmpty() || highest - mech.getY() > RebirthConfig.GUARD_MECH_MAX_RISE.get()) {
            return false;
        }
        destination = new Vec3(to.x, down.getY() + 1, to.z);
        cruiseY = highest + 1.0;
        start(Mode.HOP);
        return true;
    }

    /** A flight over its target; whether it took off. */
    boolean overFight(LivingEntity target) {
        if (!ready()) {
            return false;
        }
        orbit = Math.atan2(mech.getZ() - target.getZ(), mech.getX() - target.getX());
        start(Mode.COMBAT);
        return true;
    }

    private void start(Mode how) {
        mode = how;
        ticks = 0;
        mech.mover().stop();
        mech.setNoGravity(true);
        mech.setFlyingShown(true);
        mech.setDeltaMovement(mech.getDeltaMovement().add(0.0, 0.6, 0.0));
        mech.playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 2.5f, 0.5f);
        mech.playSound(SoundEvents.BLAZE_SHOOT, 1.6f, 0.5f);
    }

    /** It comes down: gravity again; the stomp follows when it touches the ground. */
    void land() {
        if (!flying()) {
            return;
        }
        mode = Mode.NONE;
        mech.setNoGravity(false);
        mech.setFlyingShown(false);
        rest = RebirthConfig.GUARD_MECH_FLIGHT_REST.get() * 20;
        landing = true;
    }

    /** Lands at once without a stomp and forgets the flight (loaded from a save, packed up). */
    void reset() {
        mode = Mode.NONE;
        landing = false;
        mech.setNoGravity(false);
        mech.setFlyingShown(false);
    }

    /** Every tick on the server. */
    void tick() {
        if (!flying()) {
            if (rest > 0) {
                rest--;
            }
            if (landing && mech.onGround()) {
                landing = false;
                mech.stomp();
            }
            return;
        }
        ticks++;
        mech.fallDistance = 0.0f;
        mech.setZza(0.0f);
        mech.setXxa(0.0f);
        mech.useEnergy((float) (RebirthConfig.GUARD_MECH_FLIGHT_ENERGY.get() / 20.0));
        if (!mech.machineCore().active() || mech.energy() <= 0.0f || ticks > RebirthConfig.GUARD_MECH_FLIGHT_SECONDS.get() * 20 + 100) {
            land();
            return;
        }
        if (mode == Mode.HOP) {
            hopTick();
        } else {
            fightTick();
        }
        if (mech.level() instanceof ServerLevel server && ticks % 2 == 0) {
            for (int side = -1; side <= 1; side += 2) {
                Vec3 jet = mech.jetNozzle(side);
                server.sendParticles(ParticleTypes.FLAME, jet.x, jet.y, jet.z, 3, 0.05, 0.05, 0.05, 0.02);
                server.sendParticles(ParticleTypes.LARGE_SMOKE, jet.x, jet.y - 0.3, jet.z, 1, 0.1, 0.1, 0.1, 0.01);
            }
        }
        if (ticks % 10 == 0) {
            mech.playSound(SoundEvents.BLAZE_BURN, 1.4f, 0.5f);
        }
    }

    private void hopTick() {
        Vec3 flat = destination.subtract(mech.position()).multiply(1, 0, 1);
        double left = flat.horizontalDistance();
        if (left < 0.8) {
            mech.setDeltaMovement(0.0, Math.min(0.0, mech.getDeltaMovement().y), 0.0);
            land();
            return;
        }
        // up over the highest ground first; a wall met on the way raises the flight
        if (mech.horizontalCollision && cruiseY - mech.getY() < RebirthConfig.GUARD_MECH_MAX_RISE.get()) {
            cruiseY += 1.0;
        }
        Vec3 direction = flat.normalize();
        double rise = cruiseY - mech.getY();
        double horizontal = rise > 0.5 ? 0.08 : Math.min(CRUISE, left * 0.25);
        double vertical = Mth.clamp(rise * 0.25, -0.2, CLIMB);
        mech.setDeltaMovement(direction.x * horizontal, vertical, direction.z * horizontal);
        float yaw = (float) (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG) - 90.0f;
        mech.setYRot(yaw);
        mech.yBodyRot = yaw;
        mech.yHeadRot = yaw;
    }

    private void fightTick() {
        LivingEntity target = mech.getTarget();
        if (target == null || !target.isAlive() || ticks > RebirthConfig.GUARD_MECH_FLIGHT_SECONDS.get() * 20) {
            land();
            return;
        }
        orbit += 0.035;
        Vec3 goal = target.position().add(Math.cos(orbit) * 6.0, RebirthConfig.GUARD_MECH_HOVER_HEIGHT.get(), Math.sin(orbit) * 6.0);
        Vec3 velocity = mech.getDeltaMovement().scale(0.85).add(goal.subtract(mech.position()).scale(0.025));
        double speed = velocity.length();
        if (speed > CRUISE) {
            velocity = velocity.scale(CRUISE / speed);
        }
        mech.setDeltaMovement(velocity);
        mech.getLookControl().setLookAt(target, 30.0f, 30.0f);
        mech.yBodyRot = mech.yHeadRot;
        mech.setYRot(mech.yHeadRot);
    }

    @Nullable
    Vec3 destination() {
        return mode == Mode.HOP ? destination : null;
    }
}
