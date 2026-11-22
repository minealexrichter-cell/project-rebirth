package com.projectrebirth.garrison;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 1.7.2, block B: how a machine of the garrison (a mech, a small robot, an android) gets to a place. One logic for all of them.
 * <ul>
 * <li>Near the place, with the way free (its whole body fits along the line, steps no higher than it climbs, no drop deeper than
 * it takes, 1.8.0: no water, lava, fire or thorns on it), it walks straight; else it follows a path, found again when the place
 * moved, the path ended or (1.8.0) a block now stands on the next stretch of it.</li>
 * <li>Every few seconds it checks whether it got on (1.8.0: whether it got nearer, or further along its path: walking to and fro
 * at a corner is not getting on). A machine that did not tries, in this order (1.8.0): a step back and to the side, a jump,
 * another way (the search keeps off the place where it got stuck for a while), and, when it has a {@link Lift} (the guard
 * mech's jetpack, an android's), to fly over; then it has given up and reports it ({@link #stuck()}), and keeps trying.</li>
 * <li>1.8.0: a path that does not reach the place is searched for again later and later (1, 2, 4, 8 seconds), unless the place
 * moved; all machines of a world search together no more than a few times a tick ({@link MachineNavigation#mayLookForPath}).</li>
 * </ul>
 * Numbers in the config ([garrison.movement]); with improvedNavigation off it moves as in 1.7.2.
 */
public final class MachineMover {
    /** A way over what stands in the way (the guard mech's jetpack): whether it took off. */
    public interface Lift {
        boolean lift(Vec3 goal);
    }

    private static final int ASIDE_TICKS = 20;
    /** The ways out of being stuck, tried one after another (1.8.0). */
    private static final int WAYS_OUT = 4;
    private static final int LOOK_AHEAD_TICKS = 10;
    private static final int CLEAR_WAY_TICKS = 5;

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
    /** 1.8.0: searches in a row that found no path to the place (each waits twice as long). */
    private int shortPaths;
    /** 1.8.0: the path and how far along it it was at the last check. */
    @Nullable
    private Path checkedPath;
    private int checkedNode;
    @Nullable
    private Vec3 checkedGoal;
    /** 1.8.0: the last answer whether the way is free, and for which place and until when it holds. */
    private boolean clearWayAnswer;
    @Nullable
    private Vec3 clearWayPlace;
    private long clearWayUntil;
    private long nextLookAhead;

    public MachineMover(PathfinderMob mob) {
        this.mob = mob;
    }

    public void setLift(@Nullable Lift lift) {
        this.lift = lift;
    }

    /** Whether it has not got on for a while (the console hears of it): 1.8.0, not before all ways out were tried. */
    public boolean stuck() {
        int giveUp = RebirthConfig.MOVE_GIVE_UP_CHECKS.get();
        return failedChecks >= (MachineNavigation.improved() ? Math.max(giveUp, WAYS_OUT + 1) : giveUp);
    }

    /** For the development command that shows the way: the path it follows, or null. */
    @Nullable
    public Path path() {
        return mob.getNavigation().getPath();
    }

    /** For the development command: where it wants to be, or null. */
    @Nullable
    public Vec3 goal() {
        return goal;
    }

    /** For the development command and tests: checks in a row it has not got on. */
    public int failedChecks() {
        return failedChecks;
    }

    /** Called every tick while it wants to get to the place. */
    public void walk(Vec3 place, double speed) {
        long now = mob.level().getGameTime();
        boolean improved = MachineNavigation.improved();
        if (goal == null || goal.distanceToSqr(place) > 4.0) {
            bestDistance = Double.MAX_VALUE;
        }
        if (goal != null && goal.distanceToSqr(place) > 16.0) {
            // a new place: whatever the last one taught about short paths does not hold
            shortPaths = 0;
            nextPath = Math.min(nextPath, now);
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
            if (mob.distanceToSqr(place) < straight * straight && clearWayCached(place, now)) {
                navigation.stop();
                pathedTo = null;
                mob.getMoveControl().setWantedPosition(place.x, place.y, place.z, speed);
            } else {
                if (improved && now >= nextLookAhead) {
                    nextLookAhead = now + LOOK_AHEAD_TICKS;
                    if (blockedAhead(navigation.getPath())) {
                        pathedTo = null;
                        nextPath = now;
                    }
                }
                if (now >= nextPath && (pathedTo == null || navigation.isDone() || pathedTo.distanceToSqr(place) > 2.25)
                        && MachineNavigation.mayLookForPath(mob.level())) {
                    navigation.moveTo(place.x, place.y, place.z, speed);
                    pathedTo = place;
                    Path path = navigation.getPath();
                    if (improved && (path == null || !path.canReach())) {
                        // no way all the way there: looking again at once finds the same; wait longer each time
                        shortPaths = Math.min(shortPaths + 1, 4);
                        nextPath = now + (20L << (shortPaths - 1));
                    } else {
                        shortPaths = 0;
                        nextPath = now + 20;
                    }
                }
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
        shortPaths = 0;
        checkedPath = null;
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
        Path path = mob.getNavigation().getPath();
        boolean deadEnd = path != null && !path.canReach() && !nearer;
        boolean gettingOn = moved >= step && !deadEnd;
        if (MachineNavigation.improved()) {
            // 1.8.0: moving is not enough: nearer, further along its path, or after a place that moves (a target that runs)
            boolean along = path != null && (path != checkedPath || path.getNextNodeIndex() > checkedNode);
            boolean placeMoved = checkedGoal != null && checkedGoal.distanceToSqr(goal) > step * step;
            gettingOn &= nearer || along || placeMoved;
            checkedPath = path;
            checkedNode = path == null ? 0 : path.getNextNodeIndex();
            checkedGoal = goal;
        }
        if (gettingOn || mob.isPassenger() || goal == null) {
            failedChecks = 0;
            return;
        }
        failedChecks++;
        pathedTo = null;
        if (!MachineNavigation.improved()) {
            oldWaysOut(speed);
            return;
        }
        // 1.8.0, the ways out in this order: back and round, a jump, another way, the jetpack; then again from the start
        switch ((failedChecks - 1) % WAYS_OUT) {
            case 0 -> stepAside(speed, true);
            case 1 -> {
                if (mob.onGround()) {
                    mob.getJumpControl().jump();
                }
            }
            case 2 -> takeAnotherWay();
            default -> {
                if (lift == null || !lift.lift(goal)) {
                    stepAside(speed, true);
                }
            }
        }
    }

    /** The order of 1.7.2: a jump, a step aside, then the jetpack or by turns a step aside and a jump. */
    private void oldWaysOut(double speed) {
        switch (failedChecks) {
            case 1 -> {
                if (mob.onGround()) {
                    mob.getJumpControl().jump();
                }
            }
            case 2 -> stepAside(speed, false);
            default -> {
                if (lift == null || !lift.lift(goal)) {
                    if (failedChecks % 2 == 0) {
                        stepAside(speed, false);
                    } else if (mob.onGround()) {
                        mob.getJumpControl().jump();
                    }
                }
            }
        }
    }

    /**
     * 1.8.0: the search keeps off the place it could not get past (the next point of its path, or the spot just ahead of it toward
     * its place) for a while, and looks again at once.
     */
    private void takeAnotherWay() {
        if (!(mob.getNavigation() instanceof MachineNavigation navigation) || navigation.machineEvaluator() == null) {
            return;
        }
        Path path = navigation.getPath();
        BlockPos block;
        if (path != null && !path.isDone()) {
            block = path.getNextNodePos();
        } else {
            Vec3 toward = goal.subtract(mob.position()).multiply(1, 0, 1);
            if (toward.lengthSqr() < 1.0e-4) {
                return;
            }
            Vec3 ahead = mob.position().add(toward.normalize().scale(mob.getBbWidth() * 0.5 + 1.0));
            block = BlockPos.containing(ahead);
        }
        // never the place it wants to get to
        if (Vec3.atBottomCenterOf(block).distanceToSqr(goal) < 4.0) {
            return;
        }
        navigation.machineEvaluator().avoid(block, mob.getBbWidth() > 1.0f ? 1 : 0,
                mob.level().getGameTime() + RebirthConfig.REROUTE_SECONDS.get() * 20L);
        navigation.stop();
        pathedTo = null;
        nextPath = 0;
        shortPaths = 0;
    }

    /**
     * A couple of blocks to the side (whichever side is free) and a little back, to get round a corner; 1.8.0: first straight
     * back a little when there is no room to the side.
     */
    private void stepAside(double speed, boolean thenBack) {
        Vec3 to = goal.subtract(mob.position()).multiply(1, 0, 1);
        if (to.lengthSqr() < 1.0e-4) {
            return;
        }
        to = to.normalize();
        Vec3 side = new Vec3(-to.z, 0, to.x);
        double reach = 2.0 + mob.getBbWidth();
        for (int sign : mob.getRandom().nextBoolean() ? new int[] {1, -1} : new int[] {-1, 1}) {
            Vec3 point = mob.position().add(side.scale(sign * reach)).subtract(to.scale(1.0 + (thenBack ? mob.getBbWidth() * 0.5 : 0.0)));
            if (clearWay(point)) {
                goAside(point, speed);
                return;
            }
        }
        if (thenBack) {
            Vec3 back = mob.position().subtract(to.scale(1.5 + mob.getBbWidth()));
            if (clearWay(back)) {
                goAside(back, speed);
            }
        }
    }

    private void goAside(Vec3 point, double speed) {
        aside = point;
        asideTicks = ASIDE_TICKS;
        mob.getNavigation().stop();
        mob.getMoveControl().setWantedPosition(point.x, point.y, point.z, speed);
    }

    /** 1.8.0: whether a block now stands where its body would be at one of the next two points of its path. */
    private boolean blockedAhead(@Nullable Path path) {
        if (path == null || path.isDone()) {
            return false;
        }
        AABB box = mob.getBoundingBox().deflate(0.1, 0.0, 0.1);
        for (int i = path.getNextNodeIndex(); i < Math.min(path.getNodeCount(), path.getNextNodeIndex() + 2); i++) {
            if (path.getNode(i).type == PathType.WALKABLE_DOOR) {
                // a door it opens on its way
                continue;
            }
            Vec3 at = path.getEntityPosAtNode(mob, i);
            AABB there = box.move(at.x - mob.getX(), at.y - mob.getY() + 0.1, at.z - mob.getZ());
            if (!free(there) && !free(there.move(0, mob.maxUpStep(), 0))) {
                return true;
            }
        }
        return false;
    }

    /** Whether no block stands in the box (other machines and bodies do not count: they move, or are what it goes to). */
    private boolean free(AABB box) {
        return !mob.level().getBlockCollisions(mob, box).iterator().hasNext();
    }

    /** 1.8.0: the answer of {@link #clearWay} holds a few ticks for about the same place (it is asked every tick). */
    private boolean clearWayCached(Vec3 place, long now) {
        if (!MachineNavigation.improved()) {
            return clearWay(place);
        }
        if (clearWayPlace == null || now >= clearWayUntil || clearWayPlace.distanceToSqr(place) > 0.25) {
            clearWayAnswer = clearWay(place);
            clearWayPlace = place;
            clearWayUntil = now + CLEAR_WAY_TICKS;
        }
        return clearWayAnswer;
    }

    /**
     * Whether its body gets along the line to the place on foot: in half-block steps, it may climb as high as its step and drop
     * no deeper than it takes ([garrison.movement] safeDropBlocks); 1.8.0: and walks into no water, lava, fire or thorns.
     */
    public boolean clearWay(Vec3 place) {
        Vec3 line = place.subtract(mob.position()).multiply(1, 0, 1);
        double length = line.horizontalDistance();
        if (length < 1.0e-3) {
            return true;
        }
        boolean improved = MachineNavigation.improved();
        int maxDrop = improved ? MachineNavigation.safeDrop() : 3;
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
            while (drop < maxDrop && free(moved.move(0, -1.0, 0))) {
                moved = moved.move(0, -1.0, 0);
                y -= 1.0;
                drop++;
            }
            if (drop >= maxDrop) {
                return false;
            }
            if (improved && harmful(moved)) {
                return false;
            }
        }
        return true;
    }

    /** 1.8.0: whether its feet or the ground under them in the box are water, lava, fire or something that hurts. */
    private boolean harmful(AABB box) {
        int y = Mth.floor(box.minY);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
            for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
                for (int dy = -1; dy <= 0; dy++) {
                    pos.set(x, y + dy, z);
                    BlockState state = mob.level().getBlockState(pos);
                    FluidState fluid = state.getFluidState();
                    if (!fluid.isEmpty()) {
                        return true;
                    }
                    if (!state.isAir() && hurts(state)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Fire, magma, a lit campfire, cactus, a berry bush, powder snow, a wither rose, dripstone: what hurts to walk on or into. */
    private static boolean hurts(BlockState state) {
        return net.minecraft.world.level.pathfinder.NodeEvaluator.isBurningBlock(state) || state.is(net.minecraft.world.level.block.Blocks.CACTUS)
                || state.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH) || state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                || state.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE) || state.is(net.minecraft.world.level.block.Blocks.POINTED_DRIPSTONE);
    }

    /** For the development command: the points of its path as positions of its body (empty without a path). */
    public List<Vec3> pathPoints() {
        List<Vec3> points = new ArrayList<>();
        Path path = path();
        if (path != null) {
            for (int i = path.getNextNodeIndex(); i < path.getNodeCount(); i++) {
                points.add(path.getEntityPosAtNode(mob, i));
            }
        }
        return points;
    }
}
