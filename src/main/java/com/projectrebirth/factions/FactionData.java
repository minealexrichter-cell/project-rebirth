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

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
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

    /** Removes the faction and every reference other factions hold to it. Its core blocks stay but protect nothing. */
    void remove(Faction faction) {
        factions.remove(faction.id());
        faction.members().keySet().forEach(byMember::remove);
        for (Faction other : factions.values()) {
            other.allies().remove(faction.id());
            other.enemies().remove(faction.id());
            other.allyRequests().remove(faction.id());
            other.peaceOffers().remove(faction.id());
        }
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
        faction.forget(player);
        byMember.remove(player);
        setDirty();
    }

    /**
     * The faction whose territory contains the position: a square of the given radius around one of its cores,
     * over the full height of the world. There are few cores, so a plain scan is enough.
     */
    public Optional<Faction> territoryAt(ResourceKey<Level> dimension, BlockPos pos, int radius) {
        for (Faction faction : factions.values()) {
            for (GlobalPos core : faction.cores()) {
                if (core.dimension().equals(dimension) && Math.abs(core.pos().getX() - pos.getX()) <= radius
                        && Math.abs(core.pos().getZ() - pos.getZ()) <= radius) {
                    return Optional.of(faction);
                }
            }
        }
        return Optional.empty();
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
        // a war is always on both sides (earlier saves kept it one-sided); wars with removed factions are dropped
        for (Faction faction : data.factions.values()) {
            faction.enemies().removeIf(other -> !data.factions.containsKey(other));
            faction.warStarts().forEach((other, start) -> data.factions.get(other).warStarts().putIfAbsent(faction.id(), start));
        }
        return data;
    }

    /** Copy through save/load, for tests of persistence. */
    public static FactionData reload(FactionData data, HolderLookup.Provider registries) {
        return load(data.save(new CompoundTag(), registries), registries);
    }
}
