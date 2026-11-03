package com.projectrebirth.factions;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.HumanShellEntity;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.powerarmor.PowerArmorEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

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

    /** What a block on faction territory is, for the protection rules. */
    public enum Use {
        /** Chests, furnaces, production stations, transfer capsules: anything holding items or a machine. */
        CONTAINER,
        /** Doors, trapdoors, gates, buttons, levers. */
        DOOR,
        /** Everything else stays free to use. */
        FREE
    }

    // ---------- friendly fire ----------

    public static boolean friendlyFire(Faction faction) {
        return RebirthConfig.FACTION_LEADER_CAN_TOGGLE_FRIENDLY_FIRE.get()
                ? faction.friendlyFire() : RebirthConfig.FACTION_FRIENDLY_FIRE_DEFAULT.get();
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
     * Whether damage from {@code attacker} to something belonging to {@code victimOwner} must be blocked: both are
     * in the same faction or in allied factions, they are different players and the attacker's faction has
     * friendly fire off.
     */
    public static boolean blocksDamage(MinecraftServer server, @Nullable UUID attacker, @Nullable UUID victimOwner) {
        if (attacker == null || victimOwner == null || attacker.equals(victimOwner)) {
            return false;
        }
        FactionData data = FactionData.get(server);
        Optional<Faction> own = data.of(attacker);
        Optional<Faction> other = data.of(victimOwner);
        if (own.isEmpty() || other.isEmpty() || friendlyFire(own.get())) {
            return false;
        }
        return own.get() == other.get() || own.get().allies().contains(other.get().id());
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

    // ---------- territory ----------

    public static int coreRadius() {
        return RebirthConfig.FACTION_CORE_RADIUS.get();
    }

    public static Optional<Faction> territoryAt(MinecraftServer server, Level level, BlockPos pos) {
        return FactionData.get(server).territoryAt(level.dimension(), pos, coreRadius());
    }

    /**
     * Whether the player may use a block of this kind at this position. Inside a faction's territory: members use
     * everything except recruits, who only use doors; allies only use doors; everyone else neither. Operators in
     * creative mode are not limited.
     */
    public static boolean mayUse(ServerPlayer player, BlockPos pos, Use use) {
        if (use == Use.FREE || player.isCreative() && player.hasPermissions(2)) {
            return true;
        }
        Optional<Faction> owner = territoryAt(player.server, player.level(), pos);
        if (owner.isEmpty()) {
            return true;
        }
        Faction faction = owner.get();
        if (faction.isMember(player.getUUID())) {
            return use == Use.DOOR || faction.role(player.getUUID()).canUseContainers();
        }
        Optional<Faction> own = FactionData.get(player.server).of(player.getUUID());
        return use == Use.DOOR && own.isPresent() && faction.allies().contains(own.get().id());
    }

    /** Checks that the player may set a core for their faction here. */
    public static Result canPlaceCore(ServerPlayer player, Level level, BlockPos pos) {
        FactionData data = FactionData.get(player.server);
        Optional<Faction> own = data.of(player.getUUID());
        if (own.isEmpty()) {
            return Result.fail("not_member");
        }
        if (!own.get().role(player.getUUID()).canPlaceCore()) {
            return Result.fail("no_permission");
        }
        if (own.get().cores().size() >= RebirthConfig.FACTION_MAX_CORES.get()) {
            return Result.fail("too_many_cores", RebirthConfig.FACTION_MAX_CORES.get());
        }
        // the new square must not touch another faction's square
        for (Faction faction : data.all()) {
            if (faction == own.get()) {
                continue;
            }
            for (GlobalPos core : faction.cores()) {
                if (core.dimension().equals(level.dimension()) && Math.abs(core.pos().getX() - pos.getX()) <= 2 * coreRadius()
                        && Math.abs(core.pos().getZ() - pos.getZ()) <= 2 * coreRadius()) {
                    return Result.fail("territory_overlap", faction.name());
                }
            }
        }
        return Result.ok("core_placed", own.get().name());
    }

    /** Called once the core block is in the world (after {@link #canPlaceCore} allowed it). */
    public static void addCore(ServerPlayer player, Level level, BlockPos pos) {
        FactionData data = FactionData.get(player.server);
        data.of(player.getUUID()).ifPresent(faction -> {
            faction.cores().add(GlobalPos.of(level.dimension(), pos.immutable()));
            data.setDirty();
            changed(player.server);
        });
    }

    /** Called when a core block disappears, however it happened. */
    public static void removeCore(MinecraftServer server, Level level, BlockPos pos) {
        FactionData data = FactionData.get(server);
        GlobalPos key = GlobalPos.of(level.dimension(), pos);
        for (Faction faction : data.all()) {
            if (faction.cores().remove(key)) {
                data.setDirty();
                for (UUID member : faction.members().keySet()) {
                    ServerPlayer online = server.getPlayerList().getPlayer(member);
                    if (online != null) {
                        online.sendSystemMessage(Component.translatable("message.projectrebirth.faction.core_lost",
                                pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.RED));
                    }
                }
                changed(server);
                return;
            }
        }
    }

    // ---------- membership ----------

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

    public static Result invite(ServerPlayer actor, String targetName) {
        Optional<Faction> own = factionWith(actor, FactionRole::canInvite);
        if (own.isEmpty()) {
            return Result.fail("no_permission");
        }
        ServerPlayer target = actor.server.getPlayerList().getPlayerByName(targetName.trim());
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
        FactionData.get(actor.server).setDirty();
        target.sendSystemMessage(Component.translatable("message.projectrebirth.faction.invited_you",
                Component.literal(faction.name()).withStyle(faction.color()), actor.getGameProfile().getName()));
        changed(actor.server);
        return Result.ok("invite_sent", target.getGameProfile().getName());
    }

    /** New members start as recruits. */
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
        faction.get().setRole(player.getUUID(), FactionRole.RECRUIT);
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
        removeMember(data, own.get(), player.getUUID());
        changed(player.server);
        return Result.ok("left", own.get().name());
    }

    public static Result kick(ServerPlayer actor, UUID member) {
        FactionData data = FactionData.get(actor.server);
        Optional<Faction> own = data.of(actor.getUUID());
        if (own.isEmpty() || member.equals(actor.getUUID()) || !own.get().isMember(member)) {
            return Result.fail("not_in_faction");
        }
        Faction faction = own.get();
        if (!faction.role(actor.getUUID()).canKick(faction.role(member))) {
            return Result.fail("no_permission");
        }
        String name = faction.members().get(member);
        data.removeMember(faction, member);
        ServerPlayer kicked = actor.server.getPlayerList().getPlayer(member);
        if (kicked != null) {
            kicked.sendSystemMessage(Component.translatable("message.projectrebirth.faction.kicked_you", faction.name()));
        }
        changed(actor.server);
        return Result.ok("kicked", name);
    }

    /** Hands leadership over; the old leader becomes an officer. */
    public static Result promote(ServerPlayer leader, UUID member) {
        Optional<Faction> own = factionWith(leader, role -> role == FactionRole.LEADER);
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

    public static Result setRole(ServerPlayer actor, UUID member, FactionRole role) {
        FactionData data = FactionData.get(actor.server);
        Optional<Faction> own = data.of(actor.getUUID());
        if (own.isEmpty() || member.equals(actor.getUUID()) || !own.get().isMember(member)) {
            return Result.fail("not_in_faction");
        }
        Faction faction = own.get();
        if (!faction.role(actor.getUUID()).canAssign(faction.role(member), role)) {
            return Result.fail("no_permission");
        }
        faction.setRole(member, role);
        data.setDirty();
        changed(actor.server);
        return Result.ok("role_set", faction.members().get(member), Component.translatable(role.translationKey()));
    }

    public static Result setColor(ServerPlayer leader, ChatFormatting color) {
        Optional<Faction> own = factionWith(leader, role -> role == FactionRole.LEADER);
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
        Optional<Faction> own = factionWith(leader, role -> role == FactionRole.LEADER);
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

    // ---------- diplomacy (leaders only) ----------

    /** Proposes an alliance; if the other faction already proposed one, the alliance is made at once. */
    public static Result proposeAlliance(ServerPlayer leader, UUID otherId) {
        FactionData data = FactionData.get(leader.server);
        Optional<Faction> own = factionWith(leader, role -> role == FactionRole.LEADER);
        Optional<Faction> other = data.byId(otherId);
        if (own.isEmpty()) {
            return Result.fail("not_leader");
        }
        if (other.isEmpty() || other.get() == own.get()) {
            return Result.fail("no_such_faction", "?");
        }
        if (own.get().allies().contains(otherId)) {
            return Result.fail("already_allies", other.get().name());
        }
        if (own.get().enemies().contains(otherId)) {
            return Result.fail("at_war", other.get().name());
        }
        if (own.get().allyRequests().contains(otherId)) {
            return acceptAlliance(leader, otherId);
        }
        other.get().allyRequests().add(own.get().id());
        data.setDirty();
        notifyLeader(leader.server, other.get(), Component.translatable("message.projectrebirth.faction.alliance_offered_you",
                Component.literal(own.get().name()).withStyle(own.get().color())));
        changed(leader.server);
        return Result.ok("alliance_offered", other.get().name());
    }

    public static Result acceptAlliance(ServerPlayer leader, UUID otherId) {
        FactionData data = FactionData.get(leader.server);
        Optional<Faction> own = factionWith(leader, role -> role == FactionRole.LEADER);
        Optional<Faction> other = data.byId(otherId);
        if (own.isEmpty()) {
            return Result.fail("not_leader");
        }
        if (other.isEmpty() || !own.get().allyRequests().contains(otherId)) {
            return Result.fail("no_alliance_offer");
        }
        link(own.get(), other.get());
        data.setDirty();
        notifyLeader(leader.server, other.get(), Component.translatable("message.projectrebirth.faction.alliance_made", own.get().name()));
        changed(leader.server);
        return Result.ok("alliance_made", other.get().name());
    }

    public static Result breakAlliance(ServerPlayer leader, UUID otherId) {
        FactionData data = FactionData.get(leader.server);
        Optional<Faction> own = factionWith(leader, role -> role == FactionRole.LEADER);
        Optional<Faction> other = data.byId(otherId);
        if (own.isEmpty()) {
            return Result.fail("not_leader");
        }
        if (other.isEmpty()) {
            return Result.fail("no_such_faction", "?");
        }
        own.get().allies().remove(otherId);
        other.get().allies().remove(own.get().id());
        own.get().allyRequests().remove(otherId);
        other.get().allyRequests().remove(own.get().id());
        data.setDirty();
        changed(leader.server);
        return Result.ok("alliance_broken", other.get().name());
    }

    /** War is declared by one side and ends any alliance or alliance offer between the two. */
    public static Result declareEnemy(ServerPlayer leader, UUID otherId) {
        FactionData data = FactionData.get(leader.server);
        Optional<Faction> own = factionWith(leader, role -> role == FactionRole.LEADER);
        Optional<Faction> other = data.byId(otherId);
        if (own.isEmpty()) {
            return Result.fail("not_leader");
        }
        if (other.isEmpty() || other.get() == own.get()) {
            return Result.fail("no_such_faction", "?");
        }
        own.get().allies().remove(otherId);
        other.get().allies().remove(own.get().id());
        own.get().allyRequests().remove(otherId);
        other.get().allyRequests().remove(own.get().id());
        own.get().enemies().add(otherId);
        data.setDirty();
        notifyLeader(leader.server, other.get(), Component.translatable("message.projectrebirth.faction.war_declared_you",
                Component.literal(own.get().name()).withStyle(own.get().color())));
        changed(leader.server);
        return Result.ok("war_declared", other.get().name());
    }

    /** Takes back this side's declaration; if the other side declared war too, they stay enemies. */
    public static Result makePeace(ServerPlayer leader, UUID otherId) {
        FactionData data = FactionData.get(leader.server);
        Optional<Faction> own = factionWith(leader, role -> role == FactionRole.LEADER);
        Optional<Faction> other = data.byId(otherId);
        if (own.isEmpty()) {
            return Result.fail("not_leader");
        }
        if (other.isEmpty() || !own.get().enemies().remove(otherId)) {
            return Result.fail("not_at_war");
        }
        data.setDirty();
        changed(leader.server);
        return Result.ok("peace_made", other.get().name());
    }

    /** Enemies if either side has declared war on the other. */
    public static boolean areEnemies(Faction a, Faction b) {
        return a.enemies().contains(b.id()) || b.enemies().contains(a.id());
    }

    private static void link(Faction a, Faction b) {
        a.allies().add(b.id());
        b.allies().add(a.id());
        a.allyRequests().remove(b.id());
        b.allyRequests().remove(a.id());
        a.enemies().remove(b.id());
        b.enemies().remove(a.id());
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

    private static Optional<Faction> factionWith(ServerPlayer player, Predicate<FactionRole> allowed) {
        return FactionData.get(player.server).of(player.getUUID()).filter(f -> allowed.test(f.role(player.getUUID())));
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

    private static void notifyLeader(MinecraftServer server, Faction faction, Component message) {
        ServerPlayer leader = server.getPlayerList().getPlayer(faction.leader());
        if (leader != null) {
            leader.sendSystemMessage(message);
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
