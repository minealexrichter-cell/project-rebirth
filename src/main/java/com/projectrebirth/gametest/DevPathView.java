package com.projectrebirth.gametest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joml.Vector3f;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.garrison.GarrisonMachine;
import com.projectrebirth.garrison.MachineMover;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 1.8.0, block H, development environment only ({@code /rbdev path [seconds]}): the paths of the machines near the player as
 * particles, every half second, only to that player. Green dust: the points of the path still ahead; a flame: where it wants to
 * be; red dust over its head: it has not got on at the last checks. Not in the released jar (package {@code gametest}).
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class DevPathView {
    private static final double RADIUS = 48.0;
    private static final DustParticleOptions AHEAD = new DustParticleOptions(new Vector3f(0.2f, 1.0f, 0.3f), 1.0f);
    private static final DustParticleOptions STUCK = new DustParticleOptions(new Vector3f(1.0f, 0.15f, 0.1f), 1.5f);
    /** The players who watch, and until which server tick. */
    private static final Map<UUID, Integer> WATCHING = new HashMap<>();

    private DevPathView() {
    }

    /** Shows the paths to the player for that many seconds (0: stops). */
    static void watch(ServerPlayer player, int seconds) {
        if (seconds <= 0) {
            WATCHING.remove(player.getUUID());
        } else {
            WATCHING.put(player.getUUID(), player.getServer().getTickCount() + seconds * 20);
        }
    }

    @SubscribeEvent
    static void tick(ServerTickEvent.Post event) {
        if (WATCHING.isEmpty() || event.getServer().getTickCount() % 10 != 0) {
            return;
        }
        int now = event.getServer().getTickCount();
        WATCHING.entrySet().removeIf(e -> e.getValue() < now);
        for (UUID id : WATCHING.keySet()) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                show(player);
            }
        }
    }

    private static void show(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<Entity> machines = level.getEntities((Entity) null, player.getBoundingBox().inflate(RADIUS),
                e -> e instanceof GarrisonMachine machine && machine.mover() != null);
        for (Entity entity : machines) {
            MachineMover mover = ((GarrisonMachine) entity).mover();
            Vec3 last = entity.position();
            for (Vec3 point : mover.pathPoints()) {
                // a line of dust between the points, a point every half block
                Vec3 step = point.subtract(last);
                int parts = Math.max(1, (int) (step.length() * 2));
                for (int i = 1; i <= parts; i++) {
                    Vec3 at = last.add(step.scale((double) i / parts));
                    level.sendParticles(player, AHEAD, true, at.x, at.y + 0.2, at.z, 1, 0, 0, 0, 0);
                }
                last = point;
            }
            Vec3 goal = mover.goal();
            if (goal != null) {
                level.sendParticles(player, ParticleTypes.FLAME, true, goal.x, goal.y + 0.3, goal.z, 3, 0.1, 0.1, 0.1, 0);
            }
            if (mover.failedChecks() > 0) {
                level.sendParticles(player, STUCK, true, entity.getX(), entity.getY() + entity.getBbHeight() + 0.5, entity.getZ(),
                        mover.failedChecks(), 0.2, 0.2, 0.2, 0);
            }
        }
    }
}
