package com.projectrebirth.factions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

/**
 * One faction: name, colour, leader, members with roles (and their last known names, so offline members can be
 * listed), pending invitations, friendly fire, relations to other factions and the territory cores.
 * Server-side state; clients get a copy through {@link FactionView}.
 */
public class Faction {
    private final UUID id;
    private final String name;
    private ChatFormatting color;
    private UUID leader;
    /** Members in joining order, with the name they had when last seen. */
    private final Map<UUID, String> members = new LinkedHashMap<>();
    /** Roles of members other than the leader; a missing entry means {@link FactionRole#MEMBER}. */
    private final Map<UUID, FactionRole> roles = new HashMap<>();
    private final Set<UUID> invited = new LinkedHashSet<>();
    private boolean friendlyFire;
    private final Set<UUID> allies = new LinkedHashSet<>();
    /** Factions this one is at war with (always on both sides), with the real time the war was declared. */
    private final Map<UUID, Long> warStarts = new LinkedHashMap<>();
    /** Factions that offered peace to this one. */
    private final Set<UUID> peaceOffers = new LinkedHashSet<>();
    /** Factions that proposed an alliance to this one. */
    private final Set<UUID> allyRequests = new LinkedHashSet<>();
    private final List<GlobalPos> cores = new ArrayList<>();

    public Faction(UUID id, String name, ChatFormatting color, UUID leader, String leaderName, boolean friendlyFire) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.leader = leader;
        this.friendlyFire = friendlyFire;
        members.put(leader, leaderName);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public ChatFormatting color() {
        return color;
    }

    void setColor(ChatFormatting color) {
        this.color = color;
    }

    public UUID leader() {
        return leader;
    }

    /** The old leader becomes an officer. */
    void setLeader(UUID newLeader) {
        if (members.containsKey(leader)) {
            roles.put(leader, FactionRole.OFFICER);
        }
        leader = newLeader;
        roles.remove(newLeader);
    }

    public Map<UUID, String> members() {
        return members;
    }

    public FactionRole role(UUID player) {
        if (player.equals(leader)) {
            return FactionRole.LEADER;
        }
        return roles.getOrDefault(player, FactionRole.MEMBER);
    }

    void setRole(UUID player, FactionRole role) {
        roles.put(player, role);
    }

    void forget(UUID player) {
        roles.remove(player);
    }

    public Set<UUID> invited() {
        return invited;
    }

    public boolean friendlyFire() {
        return friendlyFire;
    }

    void setFriendlyFire(boolean friendlyFire) {
        this.friendlyFire = friendlyFire;
    }

    public boolean isMember(UUID player) {
        return members.containsKey(player);
    }

    public Set<UUID> allies() {
        return allies;
    }

    /** Enemies; removing from this view ends the war on this side, adding goes through {@link #warStarts}. */
    public Set<UUID> enemies() {
        return warStarts.keySet();
    }

    public Map<UUID, Long> warStarts() {
        return warStarts;
    }

    public Set<UUID> peaceOffers() {
        return peaceOffers;
    }

    public Set<UUID> allyRequests() {
        return allyRequests;
    }

    public List<GlobalPos> cores() {
        return cores;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.putString("Color", color.getName());
        tag.putUUID("Leader", leader);
        tag.putBoolean("FriendlyFire", friendlyFire);
        ListTag memberList = new ListTag();
        members.forEach((uuid, memberName) -> {
            CompoundTag member = new CompoundTag();
            member.putUUID("Id", uuid);
            member.putString("Name", memberName);
            member.putString("Role", role(uuid).name());
            memberList.add(member);
        });
        tag.put("Members", memberList);
        tag.put("Invited", uuids(invited));
        tag.put("Allies", uuids(allies));
        ListTag warList = new ListTag();
        warStarts.forEach((other, start) -> {
            CompoundTag war = new CompoundTag();
            war.putUUID("Id", other);
            war.putLong("Start", start);
            warList.add(war);
        });
        tag.put("Wars", warList);
        tag.put("PeaceOffers", uuids(peaceOffers));
        tag.put("AllyRequests", uuids(allyRequests));
        ListTag coreList = new ListTag();
        cores.forEach(core -> GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, core).result().ifPresent(coreList::add));
        tag.put("Cores", coreList);
        return tag;
    }

    static Faction load(CompoundTag tag) {
        ChatFormatting color = ChatFormatting.getByName(tag.getString("Color"));
        UUID leader = tag.getUUID("Leader");
        Faction faction = new Faction(tag.getUUID("Id"), tag.getString("Name"), color != null ? color : ChatFormatting.WHITE,
                leader, "", tag.getBoolean("FriendlyFire"));
        faction.members.clear();
        for (Tag entry : tag.getList("Members", Tag.TAG_COMPOUND)) {
            CompoundTag member = (CompoundTag) entry;
            UUID uuid = member.getUUID("Id");
            faction.members.put(uuid, member.getString("Name"));
            // stage 5 saves have no roles: everyone but the leader stays a member
            if (member.contains("Role")) {
                try {
                    FactionRole role = FactionRole.valueOf(member.getString("Role"));
                    if (role != FactionRole.LEADER) {
                        faction.roles.put(uuid, role);
                    }
                } catch (IllegalArgumentException ignored) {
                    // unknown role name: keep the default
                }
            }
        }
        if (!faction.members.containsKey(leader) && !faction.members.isEmpty()) {
            faction.leader = faction.members.keySet().iterator().next();
            faction.roles.remove(faction.leader);
        }
        readUuids(tag.getList("Invited", Tag.TAG_INT_ARRAY), faction.invited);
        readUuids(tag.getList("Allies", Tag.TAG_INT_ARRAY), faction.allies);
        for (Tag entry : tag.getList("Wars", Tag.TAG_COMPOUND)) {
            CompoundTag war = (CompoundTag) entry;
            faction.warStarts.put(war.getUUID("Id"), war.getLong("Start"));
        }
        // earlier saves kept one-sided "Enemies" without a start: such wars count as declared now
        Set<UUID> oldEnemies = new LinkedHashSet<>();
        readUuids(tag.getList("Enemies", Tag.TAG_INT_ARRAY), oldEnemies);
        oldEnemies.forEach(other -> faction.warStarts.putIfAbsent(other, System.currentTimeMillis()));
        readUuids(tag.getList("PeaceOffers", Tag.TAG_INT_ARRAY), faction.peaceOffers);
        readUuids(tag.getList("AllyRequests", Tag.TAG_INT_ARRAY), faction.allyRequests);
        for (Tag entry : tag.getList("Cores", Tag.TAG_COMPOUND)) {
            GlobalPos.CODEC.parse(NbtOps.INSTANCE, entry).result().ifPresent(faction.cores::add);
        }
        return faction;
    }

    private static ListTag uuids(Set<UUID> set) {
        ListTag list = new ListTag();
        set.forEach(uuid -> list.add(NbtUtils.createUUID(uuid)));
        return list;
    }

    private static void readUuids(ListTag list, Set<UUID> into) {
        for (Tag entry : list) {
            into.add(NbtUtils.loadUUID(entry));
        }
    }
}
