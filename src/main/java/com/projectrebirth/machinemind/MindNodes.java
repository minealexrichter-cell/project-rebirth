package com.projectrebirth.machinemind;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.projectrebirth.ProjectRebirth;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 1.9.0, block F: the nodes of the machine mind that woke in the world, alive and destroyed (a destroyed one keeps its place in
 * the count, so that no new one wakes instead of it). Kept with the Overworld.
 */
public final class MindNodes extends SavedData {
    private static final String NAME = ProjectRebirth.MOD_ID + "_mind_nodes";

    /** @param number its short name in commands and on maps */
    public record Node(UUID id, int number, ResourceKey<Level> dimension, BlockPos pos, int stage, boolean alive) {
    }

    private final Map<UUID, Node> nodes = new LinkedHashMap<>();

    public static MindNodes get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(MindNodes::new, MindNodes::load), NAME);
    }

    public static MindNodes load(CompoundTag tag, HolderLookup.Provider registries) {
        MindNodes out = new MindNodes();
        for (Tag entry : tag.getList("nodes", Tag.TAG_COMPOUND)) {
            CompoundTag node = (CompoundTag) entry;
            ResourceLocation dimension = ResourceLocation.tryParse(node.getString("dimension"));
            if (dimension != null && node.hasUUID("id")) {
                UUID id = node.getUUID("id");
                out.nodes.put(id, new Node(id, node.getInt("number"), ResourceKey.create(Registries.DIMENSION, dimension),
                        BlockPos.of(node.getLong("pos")), node.getInt("stage"), node.getBoolean("alive")));
            }
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Node node : nodes.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", node.id());
            entry.putInt("number", node.number());
            entry.putString("dimension", node.dimension().location().toString());
            entry.putLong("pos", node.pos().asLong());
            entry.putInt("stage", node.stage());
            entry.putBoolean("alive", node.alive());
            list.add(entry);
        }
        tag.put("nodes", list);
        return tag;
    }

    /** A node wakes: enters it, or says no when the world has as many as it may. A forced one (a command) always enters. */
    public boolean wake(UUID id, ServerLevel level, BlockPos pos, int stage, int max, boolean forced) {
        if (nodes.containsKey(id)) {
            return nodes.get(id).alive();
        }
        if (!forced && nodes.size() >= max) {
            return false;
        }
        int number = nodes.values().stream().mapToInt(Node::number).max().orElse(0) + 1;
        nodes.put(id, new Node(id, number, level.dimension(), pos.immutable(), stage, true));
        setDirty();
        return true;
    }

    public void update(UUID id, int stage, boolean alive) {
        Node node = nodes.get(id);
        if (node != null && (node.stage() != stage || node.alive() != alive)) {
            nodes.put(id, new Node(id, node.number(), node.dimension(), node.pos(), stage, alive));
            setDirty();
        }
    }

    @Nullable
    public Node byId(UUID id) {
        return nodes.get(id);
    }

    @Nullable
    public Node byNumber(int number) {
        return nodes.values().stream().filter(node -> node.number() == number).findFirst().orElse(null);
    }

    public boolean alive(UUID id) {
        Node node = nodes.get(id);
        return node != null && node.alive();
    }

    public Collection<Node> all() {
        return nodes.values();
    }

    /** Forgets a node (tests). */
    public void forget(UUID id) {
        if (nodes.remove(id) != null) {
            setDirty();
        }
    }

    /** The living nodes of a dimension within a square of blocks around a place. */
    public List<Node> near(Level level, int x, int z, int range) {
        List<Node> out = new ArrayList<>();
        for (Node node : nodes.values()) {
            if (node.alive() && node.dimension() == level.dimension() && Math.abs(node.pos().getX() - x) <= range && Math.abs(node.pos().getZ() - z) <= range) {
                out.add(node);
            }
        }
        return out;
    }
}
