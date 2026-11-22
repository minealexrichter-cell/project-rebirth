package com.projectrebirth.garrison;

import java.util.Set;

import javax.annotation.Nullable;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;

/**
 * 1.7.2, block B: the ground navigation of the garrison's machines. A vanilla mob finds no path longer than its follow range (48
 * blocks for a mech), so a machine on one side of a long wall did not find the way round it and walked into the wall. Paths of
 * machines may be up to [garrison.movement] pathRange blocks long.
 * <p>
 * 1.8.0, block H: the ground is seen by {@link MachineNodeEvaluator} (wide machines where they really stand, airlock doors never
 * opened, places to keep away from); water, fire and other harm cost more; a search looks at no more than pathMaxNodes places,
 * and all machines of a world together search no more than pathSearchesPerTick times in a tick ({@link #mayLookForPath}).
 */
public class MachineNavigation extends GroundPathNavigation {
    /** The longest path of 1.7.2, in blocks (when the config is not there yet). */
    public static final int RANGE = 128;
    private static final int NODES = RANGE * 16;

    /** The tick and the count of path searches in it, for all machines of all worlds (the server has one thread). */
    private static long budgetTick = Long.MIN_VALUE;
    private static int budgetUsed;

    private MachineNodeEvaluator machineEvaluator;

    public MachineNavigation(Mob mob, Level level) {
        super(mob, level);
        setMaxVisitedNodesMultiplier(2.0f);
    }

    /** Whether the improved way finding of 1.8.0 is on (on when the config is not loaded, as on a client). */
    public static boolean improved() {
        try {
            return RebirthConfig.IMPROVED_NAVIGATION.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    private static int config(net.neoforged.neoforge.common.ModConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return fallback;
        }
    }

    /** The deepest drop a machine takes on its way. */
    public static int safeDrop() {
        return config(RebirthConfig.SAFE_DROP, 3);
    }

    /**
     * What machines pay for walking where: round water rather than through it, never into fire or lava, round thorns and the
     * like. Called once by each machine.
     */
    public static void setCosts(Mob mob) {
        mob.setPathfindingMalus(PathType.WATER, 16.0f);
        mob.setPathfindingMalus(PathType.WATER_BORDER, 4.0f);
        mob.setPathfindingMalus(PathType.DANGER_FIRE, 16.0f);
        mob.setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0f);
        mob.setPathfindingMalus(PathType.LAVA, -1.0f);
        mob.setPathfindingMalus(PathType.DANGER_OTHER, 8.0f);
        mob.setPathfindingMalus(PathType.DAMAGE_OTHER, -1.0f);
        mob.setPathfindingMalus(PathType.DAMAGE_CAUTIOUS, 8.0f);
        mob.setPathfindingMalus(PathType.DANGER_POWDER_SNOW, 8.0f);
        mob.setPathfindingMalus(PathType.POWDER_SNOW, -1.0f);
    }

    /**
     * Whether a machine may look for a path in this tick: all machines together look no more than pathSearchesPerTick times a
     * tick; one that may not waits for the next tick (with the improved way finding off, always).
     */
    public static boolean mayLookForPath(Level level) {
        if (!improved()) {
            return true;
        }
        long tick = level.getServer() != null ? level.getServer().getTickCount() : level.getGameTime();
        if (tick != budgetTick) {
            budgetTick = tick;
            budgetUsed = 0;
        }
        if (budgetUsed >= config(RebirthConfig.PATH_SEARCHES_PER_TICK, 4)) {
            return false;
        }
        budgetUsed++;
        return true;
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        machineEvaluator = new MachineNodeEvaluator();
        machineEvaluator.setCanPassDoors(true);
        nodeEvaluator = machineEvaluator;
        return new PathFinder(nodeEvaluator, Math.max(maxVisitedNodes, NODES));
    }

    /** The places to keep away from while looking for a path. */
    public MachineNodeEvaluator machineEvaluator() {
        return machineEvaluator;
    }

    @Override
    @Nullable
    protected Path createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy, float followRange) {
        if (improved()) {
            // the search looks at no more than pathMaxNodes places (the path finder was made for NODES, times the multiplier)
            setMaxVisitedNodesMultiplier(config(RebirthConfig.PATH_MAX_NODES, NODES) / (float) NODES);
            return super.createPath(targets, regionOffset, offsetUpward, accuracy, Math.max(followRange, config(RebirthConfig.PATH_RANGE, RANGE)));
        }
        setMaxVisitedNodesMultiplier(2.0f);
        return super.createPath(targets, regionOffset, offsetUpward, accuracy, Math.max(followRange, RANGE));
    }
}
