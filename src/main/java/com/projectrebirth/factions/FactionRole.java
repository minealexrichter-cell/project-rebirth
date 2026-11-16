package com.projectrebirth.factions;

import net.minecraft.ChatFormatting;

/**
 * Fixed faction roles, from highest to lowest. What each role may do is decided here, in one place.
 */
public enum FactionRole {
    LEADER(ChatFormatting.GOLD),
    OFFICER(ChatFormatting.AQUA),
    MEMBER(ChatFormatting.WHITE),
    RECRUIT(ChatFormatting.GRAY);

    private final ChatFormatting color;

    FactionRole(ChatFormatting color) {
        this.color = color;
    }

    public ChatFormatting color() {
        return color;
    }

    public String translationKey() {
        return "faction_role.projectrebirth." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public static FactionRole byId(int id) {
        FactionRole[] values = values();
        return id >= 0 && id < values.length ? values[id] : RECRUIT;
    }

    public boolean canInvite() {
        return this == LEADER || this == OFFICER;
    }

    /** Leaders may kick anyone; officers only members and recruits. */
    public boolean canKick(FactionRole target) {
        return this == LEADER && target != LEADER || this == OFFICER && (target == MEMBER || target == RECRUIT);
    }

    /** Leaders give any role below leader; officers move people between member and recruit. */
    public boolean canAssign(FactionRole from, FactionRole to) {
        if (to == LEADER || from == LEADER) {
            return false;
        }
        return this == LEADER || this == OFFICER && from != OFFICER && to != OFFICER;
    }

    public boolean canPlaceCore() {
        return this == LEADER || this == OFFICER;
    }

    /**
     * Pack up, repair and command the machines of other members (1.7.0); by default leaders and officers
     * ([garrison.machines] managerRoles). Factions made before 1.7.0 get it the same way: it goes by role.
     */
    public boolean canManageMachines() {
        return com.projectrebirth.config.RebirthConfig.MACHINE_MANAGER_ROLES.get().contains(name().toLowerCase(java.util.Locale.ROOT));
    }

    /** Chests, furnaces, stations and capsules on the faction's own territory. */
    public boolean canUseContainers() {
        return this != RECRUIT;
    }
}
