package com.projectrebirth.garrison;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.factions.Faction;
import com.projectrebirth.factions.FactionData;
import com.projectrebirth.factions.FactionService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Between a machine and its console (1.7.0, block D): once a second a loaded machine within range of its console (same dimension,
 * the console's chunk loaded) reports to it and takes its order and its intruder mode; its alarms start and end with what
 * happens to it. Linking: a machine set up near a console of its owner (or of a faction manager) links to it by itself; the
 * tactical tablet links others.
 */
public final class ConsoleLink {
    private ConsoleLink() {
    }

    /** One second of a machine (server). */
    static void second(Entity self, MachineCore core) {
        if (!(self.level() instanceof ServerLevel level) || core.console() == null) {
            return;
        }
        ConsoleBlockEntity console = consoleFor(level, self, core.console());
        if (console == null) {
            return;
        }
        UUID id = self.getUUID();
        ConsoleBlockEntity.Link link = console.link(id);
        if (link == null) {
            // the console let it go
            core.setConsole(null);
            return;
        }
        if (console.powered()) {
            core.setTask(console.taskFor(id));
            core.setIntruderMode(console.intruderModeFor(id));
        }
        console.heard(self, core, link);
        console.alarm(id, ConsoleAlarm.LOST, core.wreck());
        console.alarm(id, ConsoleAlarm.DISABLED, core.state() == MachineCore.State.DISABLED);
        console.alarm(id, ConsoleAlarm.COMBAT, !core.wreck() && core.fighting());
        console.alarm(id, ConsoleAlarm.INTRUDER, !core.wreck() && intruderNear(level, self, core));
    }

    /** The machines of later blocks raise and end the alarm of no energy or no ammunition here. */
    public static void supply(Entity self, MachineCore core, boolean lacking) {
        if (self.level() instanceof ServerLevel level && core.console() != null) {
            ConsoleBlockEntity console = consoleFor(level, self, core.console());
            if (console != null) {
                console.alarm(self.getUUID(), ConsoleAlarm.NO_SUPPLY, lacking);
            }
        }
    }

    /** The machine is destroyed for good: the console logs it lost and lets it go. */
    static void lost(Entity self, MachineCore core) {
        if (self.level() instanceof ServerLevel level && core.console() != null) {
            ConsoleBlockEntity console = consoleFor(level, self, core.console());
            if (console != null) {
                console.alarm(self.getUUID(), ConsoleAlarm.LOST, true);
                console.unlink(self.getUUID());
            }
        }
    }

    /** The machine is packed up: the console lets it go quietly (set up again, it links anew). */
    static void packed(Entity self, MachineCore core) {
        if (self.level() instanceof ServerLevel level && core.console() != null) {
            ConsoleBlockEntity console = consoleAt(level, core.console());
            if (console != null) {
                console.unlink(self.getUUID());
            }
            core.setConsole(null);
        }
    }

    /** The console at that place if its chunk is loaded. */
    @Nullable
    public static ConsoleBlockEntity consoleAt(ServerLevel level, GlobalPos where) {
        MinecraftServer server = level.getServer();
        ServerLevel dimension = server.getLevel(where.dimension());
        if (dimension == null || !dimension.isLoaded(where.pos())) {
            return null;
        }
        return dimension.getBlockEntity(where.pos()) instanceof ConsoleBlockEntity console ? console : null;
    }

    /** The console of a machine if it is loaded, in the machine's dimension and within range. */
    @Nullable
    static ConsoleBlockEntity consoleFor(ServerLevel level, Entity self, GlobalPos where) {
        if (!where.dimension().equals(level.dimension()) || !inRange(self.blockPosition(), where.pos())) {
            return null;
        }
        return consoleAt(level, where);
    }

    public static boolean inRange(BlockPos a, BlockPos b) {
        double range = RebirthConfig.CONSOLE_RANGE.get();
        return a.distSqr(b) <= range * range;
    }

    /** An outsider (not the owner's faction, not an ally, not in creative or spectator mode) near a machine on its faction's land. */
    static boolean intruderNear(ServerLevel level, Entity self, MachineCore core) {
        UUID owner = core.owner();
        if (owner == null) {
            return false;
        }
        MinecraftServer server = level.getServer();
        Optional<Faction> ours = FactionData.get(server).of(owner);
        if (ours.isEmpty()) {
            return false;
        }
        Optional<Faction> land = FactionService.territoryAt(server, level, self.blockPosition());
        if (land.isEmpty() || !land.get().id().equals(ours.get().id())) {
            return false;
        }
        double radius = RebirthConfig.CONSOLE_INTRUDER_RADIUS.get();
        for (Player player : level.players()) {
            if (player.isCreative() || player.isSpectator() || player.distanceToSqr(self) > radius * radius) {
                continue;
            }
            Optional<Faction> theirs = FactionData.get(server).of(player.getUUID());
            boolean friend = player.getUUID().equals(owner) || theirs.isPresent()
                    && (theirs.get().id().equals(ours.get().id()) || ours.get().allies().contains(theirs.get().id()));
            if (!friend) {
                return true;
            }
        }
        return false;
    }

    /**
     * Links a machine to a console (the tablet, or setting it up near one): the player must command both, the machine must be in
     * range in the same dimension, the console must have room. Returns whether it is linked now.
     */
    public static boolean link(ConsoleBlockEntity console, Entity machine, MachineCore core, Player player) {
        if (console.getLevel() == null || !console.getLevel().dimension().equals(machine.level().dimension())
                || !inRange(machine.blockPosition(), console.getBlockPos()) || !core.mayManage(player) || !console.mayCommand(player)) {
            return false;
        }
        if (core.console() != null && !core.console().equals(console.globalPos()) && machine.level() instanceof ServerLevel level) {
            ConsoleBlockEntity old = consoleAt(level, core.console());
            if (old != null) {
                old.unlink(machine.getUUID());
            }
        }
        return console.link(machine, core);
    }

    /** A machine just set up: it links to the nearest console within the link radius that the player commands. */
    public static void linkNearest(ServerLevel level, Entity machine, MachineCore core, Player player) {
        int radius = RebirthConfig.CONSOLE_LINK_RADIUS.get();
        if (radius <= 0) {
            return;
        }
        ConsoleBlockEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        int chunks = (radius >> 4) + 1;
        int cx = machine.blockPosition().getX() >> 4, cz = machine.blockPosition().getZ() >> 4;
        for (int dx = -chunks; dx <= chunks; dx++) {
            for (int dz = -chunks; dz <= chunks; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity found : chunk.getBlockEntities().values()) {
                    double distance = found.getBlockPos().distSqr(machine.blockPosition());
                    if (found instanceof ConsoleBlockEntity console && distance <= (double) radius * radius && distance < bestDistance
                            && console.mayCommand(player)) {
                        best = console;
                        bestDistance = distance;
                    }
                }
            }
        }
        if (best != null) {
            link(best, machine, core, player);
        }
    }
}
