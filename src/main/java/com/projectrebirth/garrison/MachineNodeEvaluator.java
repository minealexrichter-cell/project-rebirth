package com.projectrebirth.garrison;

import java.util.ArrayList;
import java.util.List;

import com.projectrebirth.shelter.AirlockDoorBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.Target;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * 1.8.0, block H: how the garrison's machines see the ground when they look for a path. On top of the vanilla walking rules:
 * <ul>
 * <li><b>Wide machines are where they stand.</b> Vanilla takes a node as the corner of the square a wide mob fills (3 × 3 blocks
 * for the guard mech), but starts the path at the mob's middle block and aims at the goal block as if it were a corner, so the
 * whole path of a mech was one block off toward +X/+Z: it scraped walls on the other side and stopped a block beside its goal.
 * Here the start and the goal are shifted to the corner of the square round the mob and round the goal.</li>
 * <li><b>The airlock doors of shelters are never opened</b> (for vanilla they are doors one opens by hand, as wooden ones):
 * closed, they are a wall.</li>
 * <li><b>A machine exactly two blocks high fits in two</b> (vanilla: three), so androids go through doors.</li>
 * <li><b>Places to keep away from</b> for a while ({@link #avoid}): where it got stuck, so that the next search finds another
 * way.</li>
 * </ul>
 */
public class MachineNodeEvaluator extends WalkNodeEvaluator {
    private record Avoid(int x, int y, int z, int radius, long until) {
    }

    private final List<Avoid> avoided = new ArrayList<>();
    /** The game time of the search going on (for the places to avoid). */
    private long now;

    /** Keeps the path search off the blocks round that place for that many ticks. */
    public void avoid(BlockPos at, int radius, long until) {
        avoided.removeIf(a -> a.until() <= now);
        if (avoided.size() >= 8) {
            avoided.remove(0);
        }
        avoided.add(new Avoid(at.getX(), at.getY(), at.getZ(), radius, until));
    }

    public void forgetAvoided() {
        avoided.clear();
    }

    public boolean avoids() {
        return !avoided.isEmpty();
    }

    @Override
    public void prepare(net.minecraft.world.level.PathNavigationRegion level, net.minecraft.world.entity.Mob mob) {
        super.prepare(level, mob);
        if (MachineNavigation.improved()) {
            // vanilla takes a mob 2.0 high for three blocks (floor(height + 1)): an android never went through a door or a tunnel
            // two blocks high. A body of exactly two blocks fits in two.
            entityHeight = Math.max(1, Mth.ceil(mob.getBbHeight()));
        }
        now = mob.level().getGameTime();
        avoided.removeIf(a -> a.until() <= now);
    }

    /** How far the corner of the square of a wide mob lies from its middle, in blocks (0 for a mob narrower than a block). */
    private double cornerShift() {
        return (entityWidth - 1) * 0.5;
    }

    @Override
    public Node getStart() {
        Node start = super.getStart();
        if (entityWidth <= 1 || !MachineNavigation.improved()) {
            return start;
        }
        // the corner of the square of blocks round the middle of the mob; when that square does not stand free (a wide mob next
        // to a wall fills less than its square), the next corner along that does
        int x = Mth.floor(mob.getX() - entityWidth * 0.5 + 0.5);
        int z = Mth.floor(mob.getZ() - entityWidth * 0.5 + 0.5);
        int[][] tries = {{0, 0}, {1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        for (int[] t : tries) {
            BlockPos at = new BlockPos(x + t[0], start.y, z + t[1]);
            if (canStartAt(at)) {
                return getStartNode(at);
            }
        }
        return getStartNode(new BlockPos(x, start.y, z));
    }

    @Override
    public Target getTarget(double x, double y, double z) {
        if (entityWidth <= 1 || !MachineNavigation.improved()) {
            return super.getTarget(x, y, z);
        }
        return super.getTarget(x - cornerShift(), y, z - cornerShift());
    }

    @Override
    protected PathType getCachedPathType(int x, int y, int z) {
        if (!avoided.isEmpty() && inAvoided(x, y, z)) {
            return PathType.BLOCKED;
        }
        return super.getCachedPathType(x, y, z);
    }

    private boolean inAvoided(int x, int y, int z) {
        for (Avoid a : avoided) {
            if (a.until() <= now || Math.abs(y - a.y()) > 2) {
                continue;
            }
            // the square of the mob at this node against the square round the place
            if (x + entityWidth - 1 >= a.x() - a.radius() && x <= a.x() + a.radius()
                    && z + entityDepth - 1 >= a.z() - a.radius() && z <= a.z() + a.radius()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        PathType type = super.getPathType(context, x, y, z);
        if (type == PathType.DOOR_WOOD_CLOSED) {
            BlockState state = context.getBlockState(new BlockPos(x, y, z));
            if (state.getBlock() instanceof AirlockDoorBlock && !state.getValue(DoorBlock.OPEN)) {
                return PathType.BLOCKED;
            }
        }
        return type;
    }
}
