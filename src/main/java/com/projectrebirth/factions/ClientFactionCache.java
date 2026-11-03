package com.projectrebirth.factions;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;

/**
 * The client's copy of the factions, filled by {@link FactionSync}. Plain data with no client-only classes, so the
 * common name-tag code may read it; on a dedicated server it simply stays empty.
 */
public final class ClientFactionCache {
    private static List<FactionView> factions = List.of();
    private static List<UUID> invites = List.of();
    private static boolean friendlyFireLocked;
    @Nullable
    private static Component lastMessage;
    private static boolean lastMessageSuccess;
    /** Grows on every update, so open windows know when to rebuild. */
    private static int version;

    private ClientFactionCache() {
    }

    static void update(List<FactionView> newFactions, List<UUID> newInvites, boolean locked) {
        factions = List.copyOf(newFactions);
        invites = List.copyOf(newInvites);
        friendlyFireLocked = locked;
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
