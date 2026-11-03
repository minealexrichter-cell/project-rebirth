package com.projectrebirth.bodies;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Server-wide list of every player's artificial bodies, so the capsule window can show bodies that are far away,
 * in unloaded chunks or destroyed. It only describes the bodies: the body entities (and the player, for the
 * controlled one) stay the source of truth for inventory and integrity.
 * <p>
 * A body keeps its id (the entity UUID) when the player enters and leaves it. Bodies from worlds made before this
 * list existed are added the first time their chunk loads.
 */
public class BodyRegistry extends SavedData {
    private static final String NAME = ProjectRebirth.MOD_ID + "_bodies";

    public enum State {
        /** Standing in the world, nobody inside. */
        STANDING,
        /** Its owner is in it right now. */
        CONTROLLED,
        /** Destroyed; kept so the owner learns about it, until they remove it from the list. */
        DESTROYED
    }

    public record BodyRecord(UUID id, BodyClass bodyClass, ResourceKey<Level> dimension, Vec3 position,
                             float health, float maxHealth, State state) {
    }

    private final Map<UUID, Map<UUID, BodyRecord>> byOwner = new HashMap<>();

    public static BodyRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BodyRegistry::new, BodyRegistry::load, null), NAME);
    }

    public List<BodyRecord> bodiesOf(UUID owner) {
        return new ArrayList<>(byOwner.getOrDefault(owner, Map.of()).values());
    }

    public Optional<BodyRecord> find(UUID owner, UUID id) {
        return Optional.ofNullable(byOwner.getOrDefault(owner, Map.of()).get(id));
    }

    /** The body the owner is in now, if it is known. */
    public Optional<BodyRecord> controlled(UUID owner) {
        return byOwner.getOrDefault(owner, Map.of()).values().stream().filter(r -> r.state() == State.CONTROLLED).findFirst();
    }

    /** Adds or updates a standing body. Cheap when nothing changed, so a body may call it every second. */
    public void updateStanding(ArtificialBodyEntity body) {
        UUID owner = body.getOwner();
        // A dying body stays in the world for its death animation; it must not turn back into a standing one.
        if (owner == null || body.level().isClientSide || !body.isAlive()) {
            return;
        }
        BodyRecord record = new BodyRecord(body.getUUID(), body.getBodyClass(), body.level().dimension(), body.position(),
                body.getHealth(), body.getMaxHealth(), State.STANDING);
        put(owner, record);
    }

    /** The owner moved into this body: it stays in the list as occupied, with its last place. */
    public void markControlled(UUID owner, ArtificialBodyEntity body) {
        put(owner, new BodyRecord(body.getUUID(), body.getBodyClass(), body.level().dimension(), body.position(),
                body.getHealth(), body.getMaxHealth(), State.CONTROLLED));
    }

    public void markDestroyed(UUID owner, UUID id, ResourceKey<Level> dimension, Vec3 position) {
        find(owner, id).ifPresent(record -> put(owner,
                new BodyRecord(id, record.bodyClass(), dimension, position, 0.0f, record.maxHealth(), State.DESTROYED)));
    }

    public void remove(UUID owner, UUID id) {
        Map<UUID, BodyRecord> bodies = byOwner.get(owner);
        if (bodies != null && bodies.remove(id) != null) {
            if (bodies.isEmpty()) {
                byOwner.remove(owner);
            }
            setDirty();
        }
    }

    private void put(UUID owner, BodyRecord record) {
        Map<UUID, BodyRecord> bodies = byOwner.computeIfAbsent(owner, key -> new LinkedHashMap<>());
        BodyRecord old = bodies.put(record.id(), record);
        if (!record.equals(old)) {
            setDirty();
        }
    }

    // ---------------------------------------------------------------- saving

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        byOwner.forEach((owner, bodies) -> bodies.values().forEach(record -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Owner", owner);
            entry.putUUID("Id", record.id());
            entry.putString("Class", record.bodyClass().id());
            entry.putString("Dimension", record.dimension().location().toString());
            entry.putDouble("X", record.position().x);
            entry.putDouble("Y", record.position().y);
            entry.putDouble("Z", record.position().z);
            entry.putFloat("Health", record.health());
            entry.putFloat("MaxHealth", record.maxHealth());
            entry.putString("State", record.state().name());
            list.add(entry);
        }));
        tag.put("Bodies", list);
        return tag;
    }

    private static BodyRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        BodyRegistry registry = new BodyRegistry();
        for (Tag element : tag.getList("Bodies", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            BodyClass bodyClass = BodyClass.byId(entry.getString("Class"));
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
            if (bodyClass == null || dimension == null) {
                continue;
            }
            State state;
            try {
                state = State.valueOf(entry.getString("State"));
            } catch (IllegalArgumentException e) {
                continue;
            }
            BodyRecord record = new BodyRecord(entry.getUUID("Id"), bodyClass, ResourceKey.create(Registries.DIMENSION, dimension),
                    new Vec3(entry.getDouble("X"), entry.getDouble("Y"), entry.getDouble("Z")),
                    entry.getFloat("Health"), entry.getFloat("MaxHealth"), state);
            registry.byOwner.computeIfAbsent(entry.getUUID("Owner"), key -> new LinkedHashMap<>()).put(record.id(), record);
        }
        return registry;
    }
}
