package com.projectrebirth.garrison;

import javax.annotation.Nullable;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 1.7.2, block B: how a machine of the garrison (a mech, a small robot, an android) gets to a place. One logic for all of them.
 * <ul>
 * <li>Near the place, with the way free (its whole body fits along the line, steps no higher than it climbs, no drop deeper than
 * three blocks), it walks straight; else it follows a path, found again only when the place moved or the path ended.</li>
 * <li>Every few seconds it checks whether it got on. A machine that did not is first made to jump, then to step aside, then (when
 * it has a {@link Lift}, the guard mech's jetpack) to fly over; after some checks in a row it has given up and reports it
 * ({@link #stuck()}), and keeps trying.</li>
 * </ul>
 * Numbers in the config ([garrison.movement]).
 */
public final class MachineMover {
    /** A way over what stands in the way (the guard mech's jetpack): whether it took off. */
    public interface Lift {
        boolean lift(Vec3 goal);
    }

    private static final int ASIDE_TICKS = 20;
    private static final int MAX_DROP = 3;

    private final PathfinderMob mob;
    @Nullable
    private Lift lift;
    @Nullable
    private Vec3 goal;
    @Nullable
    private Vec3 pathedTo;
    private long nextPath;
    private Vec3 checkPlace = Vec3.ZERO;
    private long nextCheck;
    private int failedChecks;
    @Nullable
    private Vec3 aside;
    private int asideTicks;
    /** The nearest it has come to its place since it set out (or the place moved far): getting nearer is getting on. */
    private double bestDistance = Double.MAX_VALUE;

    public MachineMover(PathfinderMob mob) {
        this.mob = mob;
    }

    public void setLift(@Nullable Lift lift) {
        this.lift = lift;
    }

    /** Whether it has not got on for a while (the console hears of it). */
    public boolean stuck() {
        return failedChecks >= RebirthConfig.MOVE_GIVE_UP_CHECKS.get();
    }

    /** Called every tick while it wants to get to the place. */
    public void walk(Vec3 place, double speed) {
        long now = mob.level().getGameTime();
        if (goal == null || goal.distanceToSqr(place) > 4.0) {
            bestDistance = Double.MAX_VALUE;
        }
        if (goal == null) {
            checkPlace = mob.position();
            nextCheck = now + RebirthConfig.MOVE_STUCK_TICKS.get();
        }
        goal = place;
        if (asideTicks > 0 && aside != null) {
            asideTicks--;
            mob.getMoveControl().setWantedPosition(aside.x, aside.y, aside.z, speed);
            if (asideTicks == 0) {
                pathedTo = null;
            }
        } else {
            PathNavigation navigation = mob.getNavigation();
            double straight = RebirthConfig.MOVE_STRAIGHT_DISTANCE.get();
            if (mob.distanceToSqr(place) < straight * straight && clearWay(place)) {
                navigation.stop();
                pathedTo = null;
                mob.getMoveControl().setWantedPosition(place.x, place.y, place.z, speed);
            } else if (now >= nextPath && (pathedTo == null || navigation.isDone() || pathedTo.distanceToSqr(place) > 2.25)) {
                navigation.moveTo(place.x, place.y, place.z, speed);
                pathedTo = place;
                nextPath = now + 20;
            }
        }
        if (now >= nextCheck) {
            nextCheck = now + RebirthConfig.MOVE_STUCK_TICKS.get();
            check(speed);
        }
    }

    /** It is where it wanted to be, or does not want to go anywhere: it stands, and the checks start anew. */
    public void stop() {
        if (goal != null || asideTicks > 0) {
            mob.getNavigation().stop();
        }
        goal = null;
        pathedTo = null;
        asideTicks = 0;
        failedChecks = 0;
    }

    private void check(double speed) {
        Vec3 here = mob.position();
        double moved = here.subtract(checkPlace).horizontalDistance();
        checkPlace = here;
        double step = RebirthConfig.MOVE_STUCK_DISTANCE.get();
        double distance = here.subtract(goal).horizontalDistance();
        boolean nearer = distance < bestDistance - step;
        if (nearer) {
            bestDistance = distance;
        }
        // walking on a path that does not reach the place (the search gave up short of it), and no nearer than before, is not
        // getting on either: it would walk to the same dead end again and again
        net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().getPath();
        boolean deadEnd = path != null && !path.canReach() && !nearer;
        if (moved >= step && !deadEnd || mob.isPassenger() || goal == null) {
            failedChecks = 0;
            return;
        }
        failedChecks++;
        pathedTo = null;
        switch (failedChecks) {
            case 1 -> {
                if (mob.onGround()) {
                    mob.getJumpControl().jump();
                }
            }
            case 2 -> stepAside(speed);
            default -> {
                if (lift == null || !lift.lift(goal)) {
                    if (failedChecks % 2 == 0) {
                        stepAside(speed);
                    } else if (mob.onGround()) {
                        mob.getJumpControl().jump();
                    }
                }
            }
        }
    }

    /** A couple of blocks to the side (whichever side is free) and a little back, to get round a corner. */
    private void stepAside(double speed) {
        Vec3 to = goal.subtract(mob.position()).multiply(1, 0, 1);
        if (to.lengthSqr() < 1.0e-4) {
            return;
        }
        to = to.normalize();
        Vec3 side = new Vec3(-to.z, 0, to.x);
        double reach = 2.0 + mob.getBbWidth();
        for (int sign : mob.getRandom().nextBoolean() ? new int[] {1, -1} : new int[] {-1, 1}) {
            Vec3 point = mob.position().add(side.scale(sign * reach)).subtract(to.scale(1.0));
            if (clearWay(point)) {
                aside = point;
                asideTicks = ASIDE_TICKS;
                mob.getNavigation().stop();
                mob.getMoveControl().setWantedPosition(point.x, point.y, point.z, speed);
                return;
            }
        }
    }

    /** Whether no block stands in the box (other machines and bodies do not count: they move, or are what it goes to). */
    private boolean free(AABB box) {
        return !mob.level().getBlockCollisions(mob, box).iterator().hasNext();
    }

    /**
     * Whether its body gets along the line to the place on foot: in half-block steps, it may climb as high as its step and drop
     * no deeper than three blocks.
     */
    public boolean clearWay(Vec3 place) {
        Vec3 line = place.subtract(mob.position()).multiply(1, 0, 1);
        double length = line.horizontalDistance();
        if (length < 1.0e-3) {
            return true;
        }
        AABB box = mob.getBoundingBox().deflate(0.05, 0.0, 0.05).move(0, 0.05, 0);
        double up = mob.maxUpStep();
        double y = 0.0;
        int steps = (int) Math.ceil(length / 0.5);
        for (int i = 1; i <= steps; i++) {
            Vec3 at = line.scale((double) i / steps);
            AABB moved = box.move(at.x, y, at.z);
            if (!free(moved)) {
                if (!free(moved.move(0, up, 0))) {
                    return false;
                }
                y += up;
                moved = moved.move(0, up, 0);
            }
            int drop = 0;
            while (drop < MAX_DROP && free(moved.move(0, -1.0, 0))) {
                moved = moved.move(0, -1.0, 0);
                y -= 1.0;
                drop++;
            }
            if (drop >= MAX_DROP) {
                return false;
            }
        }
        return true;
    }
}
