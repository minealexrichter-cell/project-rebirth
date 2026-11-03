package com.projectrebirth.factions;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.HumanShellEntity;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.powerarmor.PowerArmorEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * All faction rules. Runs only on the server: every request from a client window is checked here again.
 * Each operation returns the message to show to the player who asked.
 */
public final class FactionService {
    /** 2 to 16 letters (any alphabet), digits, spaces, '_' or '-'. */
    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N}_\\- ]{2,16}");

    private FactionService() {
    }

    public record Result(boolean success, Component message) {
        static Result ok(String key, Object... args) {
            return new Result(true, Component.translatable("message.projectrebirth.faction." + key, args));
        }

        static Result fail(String key, Object... args) {
            return new Result(false, Component.translatable("message.projectrebirth.faction." + key, args));
        }
    }

    public static boolean friendlyFire(Faction faction) {
        return RebirthConfig.FACTION_LEADER_CAN_TOGGLE_FRIENDLY_FIRE.get()
                ? faction.friendlyFire() : RebirthConfig.FACTION_FRIENDLY_FIRE_DEFAULT.get();
    }

    /**
     * Whether damage from {@code attacker} to something belonging to {@code victimOwner} must be blocked:
     * both are in the same faction, they are different players and the faction has friendly fire off.
     */
    public static boolean blocksDamage(MinecraftServer server, @Nullable UUID attacker, @Nullable UUID victimOwner) {
        if (attacker == null || victimOwner == null || attacker.equals(victimOwner)) {
            return false;
        }
        FactionData data = FactionData.get(server);
        Optional<Faction> faction = data.of(attacker);
        return faction.isPresent() && faction.get().isMember(victimOwner) && !friendlyFire(faction.get());
    }

    /** Friendly fire check for damage to any entity: the player behind the damage against the owner of the target. */
    public static boolean blocksDamage(Entity victim, DamageSource source) {
        MinecraftServer server = victim.getServer();
        if (server == null || victim.level().isClientSide) {
            return false;
        }
        UUID attacker = source.getEntity() instanceof Player player ? player.getUUID() : null;
        return blocksDamage(server, attacker, ownerOf(victim));
    }

    /**
     * The player whose faction protects this entity, if any: the player itself, the pilot of a power armor (or its
     * owner when empty), the owner of an artificial body or of a human body left in a capsule.
     */
    @Nullable
    public static UUID ownerOf(Entity entity) {
        if (entity instanceof Player player) {
            return player.getUUID();
        }
        if (entity instanceof PowerArmorEntity armor) {
            Player pilot = armor.getPilot();
            return pilot != null ? pilot.getUUID() : armor.getOwnerId();
        }
        if (entity instanceof ArtificialBodyEntity body) {
            return body.getOwner();
        }
        if (entity instanceof HumanShellEntity shell) {
            return shell.getOwnerId().orElse(null);
        }
        return null;
    }

    public static Result create(ServerPlayer player, String rawName, ChatFormatting color) {
        FactionData data = FactionData.get(player.server);
        String name = rawName.trim();
        if (data.of(player.getUUID()).isPresent()) {
            return Result.fail("already_member");
        }
        if (!NAME.matcher(name).matches()) {
            return Result.fail("bad_name");
        }
        if (data.byName(name).isPresent()) {
            return Result.fail("name_taken", name);
        }
        Faction faction = new Faction(UUID.randomUUID(), name, color.isColor() ? color : ChatFormatting.WHITE,
                player.getUUID(), player.getGameProfile().getName(), RebirthConfig.FACTION_FRIENDLY_FIRE_DEFAULT.get());
        data.add(faction);
        changed(player.server);
        return Result.ok("created", name);
    }

    public static Result invite(ServerPlayer leader, String targetName) {
        Optional<Faction> own = ledFaction(leader);
        if (own.isEmpty()) {
            return Result.fail("not_leader");
        }
        ServerPlayer target = leader.server.getPlayerList().getPlayerByName(targetName.trim());
        if (target == null) {
            return Result.fail("player_not_online", targetName.trim());
        }
        Faction faction = own.get();
        if (faction.isMember(target.getUUID())) {
            return Result.fail("already_in_this", target.getGameProfile().getName());
        }
        if (isFull(faction)) {
            return Result.fail("full");
        }
        faction.invited().add(target.getUUID());
        FactionData.get(leader.server).setDirty();
        target.sendSystemMessage(Component.translatable("message.projectrebirth.faction.invited_you",
                Component.literal(faction.name()).withStyle(faction.color()), leader.getGameProfile().getName()));
        changed(leader.server);
        return Result.ok("invite_sent", target.getGameProfile().getName());
    }

    public static Result accept(ServerPlayer player, UUID factionId) {
        FactionData data = FactionData.get(player.server);
        Optional<Faction> faction = data.byId(factionId);
        if (faction.isEmpty() || !faction.get().invited().contains(player.getUUID())) {
            return Result.fail("no_invite");
        }
        if (data.of(player.getUUID()).isPresent()) {
            return Result.fail("already_member");
        }
        if (isFull(faction.get())) {
            return Result.fail("full");
        }
        data.addMember(faction.get(), player.getUUID(), player.getGameProfile().getName());
        changed(player.server);
        return Result.ok("joined", faction.get().name());
    }

    public static Result decline(ServerPlayer player, UUID factionId) {
        FactionData data = FactionData.get(player.server);
        data.byId(factionId).ifPresent(faction -> {
            faction.invited().remove(player.getUUID());
            data.setDirty();
        });
        changed(player.server);
        return Result.ok("declined");
    }

    /** A leaving leader hands over to the longest-standing member; the last member disbands the faction. */
    public static Result leave(ServerPlayer player) {
        FactionData data = FactionData.get(player.server);
        Optional<Faction> own = data.of(player.getUUID());
        if (own.isEmpty()) {
            return Result.fail("not_member");
        }
        Faction faction = own.get();
        removeMember(data, faction, player.getUUID());
        changed(player.server);
        return Result.ok("left", faction.name());
    }

    public static Result kick(ServerPlayer leader, UUID member) {
        Optional<Faction> own = ledFaction(leader);
        if (own.isEmpty()) {
            return Result.fail("not_leader");
        }
        if (member.equals(leader.getUUID()) || !own.get().isMember(member)) {
            return Result.fail("not_in_faction");
        }
        String name = own.get().members().get(member);
        FactionData.get(leader.server).removeMember(own.get(), member);
        ServerPlayer kicked = leader.server.getPlayerList().getPlayer(member);
        if (kicked != null) {
            kicked.sendSystemMessage(Component.translatable("message.projectrebirth.faction.kicked_you", own.get().name()));
        }
        changed(leader.server);
        return Result.ok("kicked", name);
    }

    public static Result promote(ServerPlayer leader, UUID member) {
        Optional<Faction> own = ledFaction(leader);
        if (own.isEmpty()) {
            return Result.fail("not_leader");
        }
        if (member.equals(leader.getUUID()) || !own.get().isMember(member)) {
            return Result.fail("not_in_faction");
        }
        own.get().setLeader(member);
        FactionData.get(leader.server).setDirty();
        changed(leader.server);
        return Result.ok("promoted", own.get().members().get(member));
    }

    public static Result setColor(ServerPlayer leader, ChatFormatting color) {
        Optional<Faction> own = ledFaction(leader);
        if (own.isEmpty()) {
            return Result.fail("not_leader");
        }
        if (!color.isColor()) {
            return Result.fail("bad_color");
        }
        own.get().setColor(color);
        FactionData.get(leader.server).setDirty();
        changed(leader.server);
        return Result.ok("color_changed");
    }

    public static Result setFriendlyFire(ServerPlayer leader, boolean enabled) {
        Optional<Faction> own = ledFaction(leader);
        if (own.isEmpty()) {
            return Result.fail("not_leader");
        }
        if (!RebirthConfig.FACTION_LEADER_CAN_TOGGLE_FRIENDLY_FIRE.get()) {
            return Result.fail("friendly_fire_locked");
        }
        own.get().setFriendlyFire(enabled);
        FactionData.get(leader.server).setDirty();
        changed(leader.server);
        return Result.ok(enabled ? "friendly_fire_on" : "friendly_fire_off");
    }

    // ---------- administrator operations (commands) ----------

    public static Result adminDisband(MinecraftServer server, String name) {
        FactionData data = FactionData.get(server);
        Optional<Faction> faction = data.byName(name);
        if (faction.isEmpty()) {
            return Result.fail("no_such_faction", name);
        }
        data.remove(faction.get());
        changed(server);
        return Result.ok("disbanded", faction.get().name());
    }

    public static Result adminSetMember(MinecraftServer server, ServerPlayer player, String factionName) {
        FactionData data = FactionData.get(server);
        Optional<Faction> faction = data.byName(factionName);
        if (faction.isEmpty()) {
            return Result.fail("no_such_faction", factionName);
        }
        data.of(player.getUUID()).ifPresent(old -> removeMember(data, old, player.getUUID()));
        data.addMember(faction.get(), player.getUUID(), player.getGameProfile().getName());
        changed(server);
        return Result.ok("joined", faction.get().name());
    }

    public static Result adminRemoveMember(MinecraftServer server, ServerPlayer player) {
        FactionData data = FactionData.get(server);
        Optional<Faction> faction = data.of(player.getUUID());
        if (faction.isEmpty()) {
            return Result.fail("not_member");
        }
        removeMember(data, faction.get(), player.getUUID());
        changed(server);
        return Result.ok("left", faction.get().name());
    }

    public static Result adminSetFriendlyFire(MinecraftServer server, String name, boolean enabled) {
        FactionData data = FactionData.get(server);
        Optional<Faction> faction = data.byName(name);
        if (faction.isEmpty()) {
            return Result.fail("no_such_faction", name);
        }
        faction.get().setFriendlyFire(enabled);
        data.setDirty();
        changed(server);
        return Result.ok(enabled ? "friendly_fire_on" : "friendly_fire_off");
    }

    // ---------- helpers ----------

    private static Optional<Faction> ledFaction(ServerPlayer player) {
        return FactionData.get(player.server).of(player.getUUID()).filter(f -> f.leader().equals(player.getUUID()));
    }

    private static boolean isFull(Faction faction) {
        int max = RebirthConfig.FACTION_MAX_MEMBERS.get();
        return max > 0 && faction.members().size() >= max;
    }

    private static void removeMember(FactionData data, Faction faction, UUID player) {
        data.removeMember(faction, player);
        if (faction.members().isEmpty()) {
            data.remove(faction);
        } else if (faction.leader().equals(player)) {
            faction.setLeader(faction.members().keySet().iterator().next());
        }
    }

    /** Keeps the remembered member name up to date (called on login). */
    static void rememberName(ServerPlayer player) {
        FactionData data = FactionData.get(player.server);
        data.of(player.getUUID()).ifPresent(faction -> {
            String name = player.getGameProfile().getName();
            if (!name.equals(faction.members().get(player.getUUID()))) {
                faction.members().put(player.getUUID(), name);
                data.setDirty();
            }
        });
    }

    /** Sends the new state to every player and refreshes the names shown above heads, in chat and in the tab list. */
    static void changed(MinecraftServer server) {
        FactionSync.sendToAll(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.refreshDisplayName();
            player.refreshTabListName();
        }
    }
}
