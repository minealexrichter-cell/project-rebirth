package com.projectrebirth.multiblock;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Which controllers look after which bounds (1.7.0, block A). A block that changes inside the bounds of a construction (placed,
 * broken, blown up, moved by a piston, flowed over: anything that tells its neighbours) marks that controller to look again on
 * its next tick. No construction is polled every tick.
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class MultiblockTracker {
    private static final Map<ServerLevel, Map<BlockPos, BoundingBox>> BOUNDS = new WeakHashMap<>();

    private MultiblockTracker() {
    }

    public static void track(ServerLevel level, BlockPos controller, BoundingBox bounds) {
        BOUNDS.computeIfAbsent(level, key -> new HashMap<>()).put(controller.immutable(), bounds);
    }

    public static void untrack(ServerLevel level, BlockPos controller) {
        Map<BlockPos, BoundingBox> map = BOUNDS.get(level);
        if (map != null) {
            map.remove(controller);
        }
    }

    @SubscribeEvent
    static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Map<BlockPos, BoundingBox> map = BOUNDS.get(level);
        if (map == null || map.isEmpty()) {
            return;
        }
        BlockPos pos = event.getPos();
        for (Map.Entry<BlockPos, BoundingBox> entry : map.entrySet()) {
            if (entry.getValue().isInside(pos) && level.getBlockEntity(entry.getKey()) instanceof PortHost controller) {
                controller.markDirty();
            }
        }
    }
}
