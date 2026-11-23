package com.projectrebirth.hostile;

import java.util.EnumSet;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 1.9.0, block C: a mutant keeps away from fire, a lit campfire and lava ([hostile.mutants] fleeFire). Looked for twice a
 * second in a small box around it; when found, it runs a few blocks away from it, also in the middle of a chase.
 */
public class FleeFireGoal extends Goal {
    private static final int RADIUS = 4;
    private final PathfinderMob mob;
    private Vec3 away;

    public FleeFireGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    /** The nearest fire within the box, or null. Public for tests. */
    public static BlockPos fireNear(PathfinderMob mob) {
        BlockPos centre = mob.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-RADIUS, -1, -RADIUS), centre.offset(RADIUS, 2, RADIUS))) {
            BlockState state = mob.level().getBlockState(pos);
            if (state.is(BlockTags.FIRE) || CampfireBlock.isLitCampfire(state) || state.getFluidState().is(FluidTags.LAVA)) {
                double distance = pos.distSqr(centre);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }

    @Override
    public boolean canUse() {
        if (mob.tickCount % 10 != 0 || RebirthConfig.SERVER_SPEC.isLoaded() && !RebirthConfig.MUTANTS_FLEE_FIRE.get()) {
            return false;
        }
        BlockPos fire = fireNear(mob);
        if (fire == null) {
            return false;
        }
        away = DefaultRandomPos.getPosAway(mob, 10, 5, Vec3.atCenterOf(fire));
        return away != null;
    }

    @Override
    public void start() {
        mob.getNavigation().moveTo(away.x, away.y, away.z, 1.3);
    }

    @Override
    public boolean canContinueToUse() {
        return !mob.getNavigation().isDone();
    }
}
