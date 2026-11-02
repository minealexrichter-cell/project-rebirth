package com.projectrebirth.bodies;

import java.util.HashMap;
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
 * Server-wide record of human bodies left in transfer capsules, one per player.
 * <p>
 * This is the only place that holds a human body's inventory and health while its owner controls an
 * artificial body. The {@link HumanShellEntity} in the world is just its visible, vulnerable part:
 * it checks this registry and removes itself once its record is gone. Keeping the data here (not in the
 * entity) means the owner can return even if the capsule's chunk is not loaded, and items cannot be duplicated.
 */
public class ShellRegistry extends SavedData {
    private static final String NAME = ProjectRebirth.MOD_ID + "_shells";

    private final Map<UUID, ShellRecord> byOwner = new HashMap<>();

    public static ShellRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ShellRegistry::new, ShellRegistry::load, null), NAME);
    }

    public Optional<ShellRecord> get(UUID owner) {
        return Optional.ofNullable(byOwner.get(owner));
    }

    public boolean isCurrentShell(UUID owner, UUID shellId) {
        ShellRecord record = byOwner.get(owner);
        return record != null && record.shellId().equals(shellId);
    }

    public void put(UUID owner, ShellRecord record) {
        byOwner.put(owner, record);
        setDirty();
    }

    public Optional<ShellRecord> remove(UUID owner) {
        ShellRecord removed = byOwner.remove(owner);
        if (removed != null) {
            setDirty();
        }
        return Optional.ofNullable(removed);
    }

    public void updateHealth(UUID owner, UUID shellId, float health) {
        ShellRecord record = byOwner.get(owner);
        if (record != null && record.shellId().equals(shellId)) {
            byOwner.put(owner, record.withHealth(health));
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        byOwner.forEach((owner, record) -> {
            CompoundTag entry = record.toTag();
            entry.putUUID("Owner", owner);
            list.add(entry);
        });
        tag.put("Shells", list);
        return tag;
    }

    private static ShellRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        ShellRegistry registry = new ShellRegistry();
        for (Tag element : tag.getList("Shells", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            registry.byOwner.put(entry.getUUID("Owner"), ShellRecord.fromTag(entry));
        }
        return registry;
    }

    /** Everything needed to put a player back into their human body. */
    public record ShellRecord(UUID shellId, ResourceKey<Level> dimension, Vec3 position, float yaw,
                              float health, int foodLevel, float saturation, ListTag inventory) {

        public ShellRecord withHealth(float newHealth) {
            return new ShellRecord(shellId, dimension, position, yaw, newHealth, foodLevel, saturation, inventory);
        }

        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("ShellId", shellId);
            tag.putString("Dimension", dimension.location().toString());
            tag.putDouble("X", position.x);
            tag.putDouble("Y", position.y);
            tag.putDouble("Z", position.z);
            tag.putFloat("Yaw", yaw);
            tag.putFloat("Health", health);
            tag.putInt("Food", foodLevel);
            tag.putFloat("Saturation", saturation);
            tag.put("Inventory", inventory);
            return tag;
        }

        static ShellRecord fromTag(CompoundTag tag) {
            return new ShellRecord(
                    tag.getUUID("ShellId"),
                    ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("Dimension"))),
                    new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")),
                    tag.getFloat("Yaw"),
                    tag.getFloat("Health"),
                    tag.getInt("Food"),
                    tag.getFloat("Saturation"),
                    tag.getList("Inventory", Tag.TAG_COMPOUND));
        }
    }
}
