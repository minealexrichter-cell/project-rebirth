package com.projectrebirth.garrison;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.projectrebirth.factions.Faction;
import com.projectrebirth.factions.FactionData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The register of the players' machines (1.7.0), saved with the world: for each machine standing in the world its kind, owner,
 * name, where it is, its state and when it was last seen. Machines do not load chunks, so a console shows the last known line of
 * a machine far away. The limits count from here. A machine's line goes when it is packed up, dismantled or destroyed for good,
 * not when its chunk unloads.
 */
public class MachineRegistry extends SavedData {
    private static final String NAME = "projectrebirth_machines";

    /** One machine as last seen. */
    public record Entry(UUID id, MachineKind kind, @Nullable UUID owner, String name, ResourceKey<Level> dimension, BlockPos pos,
                        MachineCore.State state, long lastSeen) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", id);
            tag.putString("kind", kind.id());
            if (owner != null) {
                tag.putUUID("owner", owner);
            }
            tag.putString("name", name);
            tag.putString("dimension", dimension.location().toString());
            tag.putLong("pos", pos.asLong());
            tag.putInt("state", state.ordinal());
            tag.putLong("seen", lastSeen);
            return tag;
        }

        static Entry load(CompoundTag tag) {
            return new Entry(tag.getUUID("id"), MachineKind.byId(tag.getString("kind")), tag.hasUUID("owner") ? tag.getUUID("owner") : null,
                    tag.getString("name"), ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("dimension"))),
                    BlockPos.of(tag.getLong("pos")), MachineCore.State.byId(tag.getInt("state")), tag.getLong("seen"));
        }
    }

    private final Map<UUID, Entry> machines = new LinkedHashMap<>();

    public static MachineRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(MachineRegistry::new, MachineRegistry::load), NAME);
    }

    private static MachineRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        MachineRegistry registry = new MachineRegistry();
        for (Tag entry : tag.getList("machines", Tag.TAG_COMPOUND)) {
            Entry loaded = Entry.load((CompoundTag) entry);
            registry.machines.put(loaded.id(), loaded);
        }
        return registry;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        machines.values().forEach(entry -> list.add(entry.save()));
        tag.put("machines", list);
        return tag;
    }

    /** The machine as it is now (called by the machine every few seconds and when it is placed). */
    public void update(Entity machine, MachineCore core) {
        machines.put(machine.getUUID(), new Entry(machine.getUUID(), core.kind(), core.owner(), core.name(), machine.level().dimension(),
                machine.blockPosition(), core.state(), System.currentTimeMillis()));
        setDirty();
    }

    /** The machine is gone from the world (packed up, dismantled, destroyed). */
    public void remove(UUID id) {
        if (machines.remove(id) != null) {
            setDirty();
        }
    }

    public Optional<Entry> entry(UUID id) {
        return Optional.ofNullable(machines.get(id));
    }

    public Collection<Entry> all() {
        return Collections.unmodifiableCollection(machines.values());
    }

    /** Machines of this kind the player owns. */
    public int countOwned(UUID owner, MachineKind kind) {
        return (int) machines.values().stream().filter(entry -> owner.equals(entry.owner()) && entry.kind() == kind).count();
    }

    /** Machines of this kind the members of a faction own together. */
    public int countFaction(Faction faction, MachineKind kind) {
        return (int) machines.values().stream()
                .filter(entry -> entry.owner() != null && faction.members().containsKey(entry.owner()) && entry.kind() == kind).count();
    }

    public int total() {
        return machines.size();
    }

    /**
     * Why this player may not set up one more machine of this kind (a message key and its numbers), or null if they may. The
     * player's own limit, their faction's, the server's.
     */
    @Nullable
    public net.minecraft.network.chat.Component limitProblem(MinecraftServer server, UUID owner, MachineKind kind) {
        int own = countOwned(owner, kind);
        if (own >= kind.playerLimit()) {
            return net.minecraft.network.chat.Component.translatable("message.projectrebirth.machine.limit_player", kind.playerLimit(),
                    net.minecraft.network.chat.Component.translatable(kind.translationKey()));
        }
        Optional<Faction> faction = FactionData.get(server).of(owner);
        if (faction.isPresent() && countFaction(faction.get(), kind) >= kind.factionLimit()) {
            return net.minecraft.network.chat.Component.translatable("message.projectrebirth.machine.limit_faction", kind.factionLimit(),
                    net.minecraft.network.chat.Component.translatable(kind.translationKey()));
        }
        if (total() >= com.projectrebirth.config.RebirthConfig.MACHINE_SERVER_LIMIT.get()) {
            return net.minecraft.network.chat.Component.translatable("message.projectrebirth.machine.limit_server",
                    com.projectrebirth.config.RebirthConfig.MACHINE_SERVER_LIMIT.get());
        }
        return null;
    }
}
