package com.projectrebirth.factions;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The client's copy of the factions, filled by {@link FactionSync}. Plain data with no client-only classes, so the
 * common name-tag code may read it; on a dedicated server it simply stays empty.
 */
public final class ClientFactionCache {
    private static List<FactionView> factions = List.of();
    private static List<UUID> invites = List.of();
    private static boolean friendlyFireLocked;
    private static int coreRadius;
    @Nullable
    private static UUID localPlayer;
    @Nullable
    private static Component lastMessage;
    private static boolean lastMessageSuccess;
    /** Grows on every update, so open windows know when to rebuild. */
    private static int version;

    private ClientFactionCache() {
    }

    static void update(List<FactionView> newFactions, List<UUID> newInvites, boolean locked, int radius, UUID player) {
        factions = List.copyOf(newFactions);
        invites = List.copyOf(newInvites);
        friendlyFireLocked = locked;
        coreRadius = radius;
        localPlayer = player;
        version++;
    }

    static void setMessage(Component message, boolean success) {
        lastMessage = message;
        lastMessageSuccess = success;
        version++;
    }

    public static Optional<FactionView> of(UUID player) {
        return factions.stream().filter(faction -> faction.isMember(player)).findFirst();
    }

    public static Optional<FactionView> byId(UUID id) {
        return factions.stream().filter(faction -> faction.id().equals(id)).findFirst();
    }

    public static List<FactionView> all() {
        return factions;
    }

    public static int coreRadius() {
        return coreRadius;
    }

    /** The faction of the player this client plays as. */
    public static Optional<FactionView> local() {
        return localPlayer == null ? Optional.empty() : of(localPlayer);
    }

    /** Same rule as the server: a square of {@link #coreRadius} around a core. */
    public static Optional<FactionView> territoryAt(ResourceKey<Level> dimension, BlockPos pos) {
        for (FactionView faction : factions) {
            for (GlobalPos core : faction.cores()) {
                if (core.dimension().equals(dimension) && Math.abs(core.pos().getX() - pos.getX()) <= coreRadius
                        && Math.abs(core.pos().getZ() - pos.getZ()) <= coreRadius) {
                    return Optional.of(faction);
                }
            }
        }
        return Optional.empty();
    }

    public static List<UUID> invites() {
        return invites;
    }

    public static boolean friendlyFireLocked() {
        return friendlyFireLocked;
    }

    @Nullable
    public static Component lastMessage() {
        return lastMessage;
    }

    public static boolean lastMessageSuccess() {
        return lastMessageSuccess;
    }

    public static int version() {
        return version;
    }

    /** Forgets everything when leaving a server. */
    public static void clear() {
        factions = List.of();
        invites = List.of();
        lastMessage = null;
        version++;
    }
}
