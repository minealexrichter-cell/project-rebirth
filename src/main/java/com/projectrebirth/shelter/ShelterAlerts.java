package com.projectrebirth.shelter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.projectrebirth.factions.FactionData;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Warnings about a shelter: to the owner of the unit or battery (who placed it) and every member of their faction who is
 * online. Each one comes once when a trouble starts and once when it ends, so a door opened for a moment says nothing.
 */
public final class ShelterAlerts {
    /** What one room has said already; kept by the room's first unit, so a new scan of the room keeps it. */
    static final class State {
        long leakSince = -1;
        boolean leakTold;
        boolean cartridgeLowTold;
        boolean noCartridgeTold;
        long noEnergySince = -1;
        boolean noEnergyTold;
    }

    private ShelterAlerts() {
    }

    /** Who hears about something owned by this player. */
    public static Set<ServerPlayer> recipients(MinecraftServer server, @Nullable UUID owner) {
        Set<ServerPlayer> players = new HashSet<>();
        if (owner == null) {
            return players;
        }
        ServerPlayer self = server.getPlayerList().getPlayer(owner);
        if (self != null) {
            players.add(self);
        }
        FactionData.get(server).of(owner).ifPresent(faction -> {
            for (UUID member : faction.members().keySet()) {
                ServerPlayer online = server.getPlayerList().getPlayer(member);
                if (online != null) {
                    players.add(online);
                }
            }
        });
        return players;
    }

    /** Who hears about something owned by this player that stands in this dimension: only those in it (1.8.0). */
    public static Set<ServerPlayer> recipients(net.minecraft.server.level.ServerLevel level, @Nullable UUID owner) {
        Set<ServerPlayer> players = recipients(level.getServer(), owner);
        players.removeIf(player -> player.level() != level);
        return players;
    }

    /** Sends one warning about the shelter at a place to those in its dimension (1.8.0); returns how many heard it. */
    public static int tell(net.minecraft.server.level.ServerLevel level, @Nullable UUID owner, BlockPos where, String key, boolean bad, Object... args) {
        Object[] all = new Object[args.length + 3];
        all[0] = where.getX();
        all[1] = where.getY();
        all[2] = where.getZ();
        System.arraycopy(args, 0, all, 3, args.length);
        Component message = Component.translatable("message.projectrebirth.shelter_alert." + key, all)
                .withStyle(bad ? ChatFormatting.RED : ChatFormatting.GREEN);
        Set<ServerPlayer> players = recipients(level, owner);
        for (ServerPlayer player : players) {
            player.sendSystemMessage(message);
        }
        return players.size();
    }
}
