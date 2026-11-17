package com.projectrebirth.garrison;

import javax.annotation.Nullable;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * 1.7.2, block D: how a machine walks the order "area patrol" (one logic for the mech, the small robot and the android). The group
 * got the middle of its machines as the centre; each machine has its own sector of the circle round it (a slice, so that they
 * spread out) and walks from one random place in its sector to another, pausing a little at each. A place is on the ground
 * (not in water), in a loaded chunk; one it does not reach (it got stuck, or it took a minute) is given up for another. After a
 * fight it simply walks on in its sector. The radius and the pause in the config ([garrison.movement]).
 */
public final class AreaPatrol {
    private static final int TRIES = 10;

    private final PathfinderMob mob;
    private final MachineMover mover;
    @Nullable
    private MachineTask task;
    @Nullable
    private Vec3 point;
    private long chosenAt;
    private long waitUntil;

    AreaPatrol(PathfinderMob mob, MachineMover mover) {
        this.mob = mob;
        this.mover = mover;
    }

    /** Where to walk now, or null while it pauses. */
    @Nullable
    Vec3 goal(MachineTask given) {
        long now = mob.level().getGameTime();
        if (!given.equals(task)) {
            task = given;
            point = null;
            waitUntil = 0;
        }
        if (now < waitUntil) {
            return null;
        }
        if (point == null || now - chosenAt > 1200 || now - chosenAt > 100 && mover.stuck()) {
            point = pick(given);
            chosenAt = now;
        }
        return point;
    }

    /** It is at its place: a pause, then the next one. */
    void arrived() {
        point = null;
        int pause = RebirthConfig.AREA_PATROL_PAUSE.get() * 20;
        waitUntil = mob.level().getGameTime() + (pause > 0 ? 20 + mob.getRandom().nextInt(pause) : 0);
    }

    /** A random place on the ground in its sector, or the centre when none is found. */
    @Nullable
    private Vec3 pick(MachineTask given) {
        return pick(mob, given);
    }

    /** A random place on the ground in the sector of an area patrol, or its centre when none is found. Public for tests. */
    @Nullable
    public static Vec3 pick(PathfinderMob mob, MachineTask given) {
        if (given.point().isEmpty()) {
            return null;
        }
        BlockPos centre = given.point().get();
        int[] sector = given.sector();
        double radius = RebirthConfig.AREA_PATROL_RADIUS.get();
        double slice = Math.PI * 2.0 / sector[1];
        for (int i = 0; i < TRIES; i++) {
            double angle = slice * (sector[0] + mob.getRandom().nextDouble());
            // the far part of the slice more often than its tip, so that the machines stand apart
            double distance = radius * (sector[1] == 1 ? Math.sqrt(mob.getRandom().nextDouble()) : 0.3 + 0.7 * Math.sqrt(mob.getRandom().nextDouble()));
            int x = Mth.floor(centre.getX() + 0.5 + Math.cos(angle) * distance);
            int z = Mth.floor(centre.getZ() + 0.5 + Math.sin(angle) * distance);
            if (!mob.level().hasChunkAt(new BlockPos(x, centre.getY(), z))) {
                continue;
            }
            int y = mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos ground = new BlockPos(x, y - 1, z);
            if (!mob.level().getFluidState(ground).isEmpty() || Math.abs(y - centre.getY()) > radius) {
                continue;
            }
            return Vec3.atBottomCenterOf(ground.above());
        }
        return Vec3.atBottomCenterOf(centre);
    }

    /** Whether a target is in the patrolled area (with some room round it to fight). */
    static boolean covers(MachineTask task, BlockPos target) {
        if (task.point().isEmpty()) {
            return true;
        }
        double reach = RebirthConfig.AREA_PATROL_RADIUS.get() + 8.0;
        BlockPos centre = task.point().get();
        double dx = target.getX() - centre.getX(), dz = target.getZ() - centre.getZ();
        return dx * dx + dz * dz <= reach * reach;
    }
}
