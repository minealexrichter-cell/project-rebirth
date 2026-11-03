package com.projectrebirth.factions;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Server-wide list of factions, saved with the world (data/projectrebirth_factions.dat). */
public class FactionData extends SavedData {
    private static final String NAME = ProjectRebirth.MOD_ID + "_factions";

    private final Map<UUID, Faction> factions = new LinkedHashMap<>();
    /** Player -> faction index, rebuilt on load; every player is in at most one faction. */
    private final Map<UUID, UUID> byMember = new HashMap<>();

    public static FactionData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(FactionData::new, FactionData::load, null), NAME);
    }

    public Collection<Faction> all() {
        return factions.values();
    }

    public Optional<Faction> byId(UUID id) {
        return Optional.ofNullable(factions.get(id));
    }

    public Optional<Faction> of(@Nullable UUID player) {
        return player == null ? Optional.empty() : Optional.ofNullable(byMember.get(player)).map(factions::get);
    }

    public Optional<Faction> byName(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        return factions.values().stream().filter(f -> f.name().toLowerCase(Locale.ROOT).equals(key)).findFirst();
    }

    void add(Faction faction) {
        factions.put(faction.id(), faction);
        faction.members().keySet().forEach(member -> byMember.put(member, faction.id()));
        setDirty();
    }

    void remove(Faction faction) {
        factions.remove(faction.id());
        faction.members().keySet().forEach(byMember::remove);
        setDirty();
    }

    void addMember(Faction faction, UUID player, String name) {
        faction.members().put(player, name);
        faction.invited().remove(player);
        byMember.put(player, faction.id());
        setDirty();
    }

    void removeMember(Faction faction, UUID player) {
        faction.members().remove(player);
        byMember.remove(player);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        factions.values().forEach(faction -> list.add(faction.save()));
        tag.put("Factions", list);
        return tag;
    }

    private static FactionData load(CompoundTag tag, HolderLookup.Provider registries) {
        FactionData data = new FactionData();
        for (Tag entry : tag.getList("Factions", Tag.TAG_COMPOUND)) {
            Faction faction = Faction.load((CompoundTag) entry);
            if (!faction.members().isEmpty()) {
                data.factions.put(faction.id(), faction);
                faction.members().keySet().forEach(member -> data.byMember.put(member, faction.id()));
            }
        }
        return data;
    }

    /** Copy through save/load, for tests of persistence. */
    public static FactionData reload(FactionData data, HolderLookup.Provider registries) {
        return load(data.save(new CompoundTag(), registries), registries);
    }
}
