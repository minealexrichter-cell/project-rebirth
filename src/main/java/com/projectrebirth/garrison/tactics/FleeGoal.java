package com.projectrebirth.garrison.tactics;

import java.util.EnumSet;
import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import com.projectrebirth.garrison.GarrisonMachine;
import com.projectrebirth.garrison.MachineMover;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * 1.8.0, block I: a machine gets out of a blast's way before anything else — a hissing creeper, where an enemy rocket comes down,
 * a toxic puddle (see {@link Tactics#danger}). It runs to a place it gets to on foot, out of the radius, for at most three
 * seconds; then it fights on.
 */
public class FleeGoal extends Goal {
    private final Mob mob;
    private final GarrisonMachine machine;
    private final BooleanSupplier able;
    @Nullable
    private Vec3 to;
    private int ticks;
    private int wait;

    /** The mover is asked of the machine when needed: goals are made before the fields of the entity. */
    public FleeGoal(Mob mob, GarrisonMachine machine, BooleanSupplier able) {
        this.mob = mob;
        this.machine = machine;
        this.able = able;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    private MachineMover mover() {
        return machine.mover();
    }

    @Override
    public boolean canUse() {
        if (--wait > 0 || !able.getAsBoolean()) {
            return false;
        }
        wait = 2;
        Vec3 danger = Tactics.danger(mob, machine);
        if (danger == null) {
            return false;
        }
        to = Tactics.fleePoint(mob, mover(), danger);
        ticks = 60;
        return to != null;
    }

    @Override
    public boolean canContinueToUse() {
        return ticks > 0 && to != null && mob.distanceToSqr(to) > 1.0 && able.getAsBoolean();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        ticks--;
        if (to != null) {
            mover().walk(to, 1.35);
        }
    }

    @Override
    public void stop() {
        mover().stop();
        to = null;
    }
}
