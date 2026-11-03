package com.projectrebirth.factions;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

/**
 * One faction: name, colour, leader, members (with their last known names, so offline members can be listed),
 * pending invitations and the friendly fire rule. Server-side state; clients get a copy through {@link FactionView}.
 */
public class Faction {
    private final UUID id;
    private String name;
    private ChatFormatting color;
    private UUID leader;
    /** Members in joining order, with the name they had when last seen. */
    private final Map<UUID, String> members = new LinkedHashMap<>();
    private final Set<UUID> invited = new LinkedHashSet<>();
    private boolean friendlyFire;

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

    void setLeader(UUID leader) {
        this.leader = leader;
    }

    public Map<UUID, String> members() {
        return members;
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
            memberList.add(member);
        });
        tag.put("Members", memberList);
        ListTag invitedList = new ListTag();
        invited.forEach(uuid -> invitedList.add(NbtUtils.createUUID(uuid)));
        tag.put("Invited", invitedList);
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
            faction.members.put(member.getUUID("Id"), member.getString("Name"));
        }
        if (!faction.members.containsKey(leader) && !faction.members.isEmpty()) {
            faction.leader = faction.members.keySet().iterator().next();
        }
        for (Tag entry : tag.getList("Invited", Tag.TAG_INT_ARRAY)) {
            faction.invited.add(NbtUtils.loadUUID(entry));
        }
        return faction;
    }
}
