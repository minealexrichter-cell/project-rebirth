package com.projectrebirth.garrison;

import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;

/**
 * 1.7.2, block B: the ground navigation of the garrison's machines. A vanilla mob finds no path longer than its follow range (48
 * blocks for a mech), so a machine on one side of a long wall did not find the way round it and walked into the wall. Paths of
 * machines may be up to {@link #RANGE} blocks long, with the steps tried to match.
 */
public class MachineNavigation extends GroundPathNavigation {
    /** The longest path, in blocks. */
    public static final int RANGE = 128;

    public MachineNavigation(Mob mob, Level level) {
        super(mob, level);
        setMaxVisitedNodesMultiplier(2.0f);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        return super.createPathFinder(Math.max(maxVisitedNodes, RANGE * 16));
    }

    @Override
    @Nullable
    protected Path createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy, float followRange) {
        return super.createPath(targets, regionOffset, offsetUpward, accuracy, Math.max(followRange, RANGE));
    }
}
