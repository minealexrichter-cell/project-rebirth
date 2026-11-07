package com.projectrebirth.technology.energy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** What other packages may ask about the mod's cable. */
public final class Cables {
    private Cables() {
    }

    /** The machines on the cables next to a block (each once), and the blocks right next to it. */
    public static List<BlockPos> machinesNear(Level level, BlockPos pos) {
        Set<BlockPos> found = new LinkedHashSet<>();
        for (Direction side : Direction.values()) {
            BlockPos next = pos.relative(side);
            if (level.getBlockState(next).getBlock() instanceof CableBlock) {
                found.addAll(CableNetwork.at(level, next).machines());
            } else {
                found.add(next.immutable());
            }
        }
        found.remove(pos);
        return new ArrayList<>(found);
    }
}
