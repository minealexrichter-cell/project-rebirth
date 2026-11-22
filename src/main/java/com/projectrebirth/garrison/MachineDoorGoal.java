package com.projectrebirth.garrison;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.shelter.AirlockDoorBlock;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;

/**
 * 1.8.0, block H: an android opens a wooden door on its way and closes it behind it, as a villager does; never the airlock door
 * of a shelter (for vanilla that is a door one opens by hand too). Off with [garrison.movement] androidsOpenDoors.
 */
public class MachineDoorGoal extends OpenDoorGoal {
    public MachineDoorGoal(Mob mob) {
        super(mob, true);
    }

    @Override
    public boolean canUse() {
        return RebirthConfig.MACHINES_OPEN_DOORS.get() && MachineNavigation.improved() && super.canUse()
                && !(mob.level().getBlockState(doorPos).getBlock() instanceof AirlockDoorBlock);
    }
}
