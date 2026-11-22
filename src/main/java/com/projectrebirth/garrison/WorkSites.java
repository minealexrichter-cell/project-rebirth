package com.projectrebirth.garrison;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 1.8.0, block J: the places work androids found for themselves, kept with the world: each miner's mine (where it starts, which
 * way the stair first runs, how far it has got) and where a lumberjack or a farmer last found its work. A miner goes on with its
 * mine after a restart; the console shows these places on its map.
 */
public class WorkSites extends SavedData {
    private static final String NAME = "projectrebirth_work_sites";

    /** One place: what (mine, lumber, farm), where, and for a mine its first way and progress (steps done). */
    public record Site(String kind, ResourceKey<Level> dimension, BlockPos pos, Direction facing, int progress) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("kind", kind);
            tag.putString("dimension", dimension.location().toString());
            tag.put("pos", NbtUtils.writeBlockPos(pos));
            tag.putInt("facing", facing.get2DDataValue());
            tag.putInt("progress", progress);
            return tag;
        }

        @Nullable
        static Site load(CompoundTag tag) {
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
            Optional<BlockPos> pos = NbtUtils.readBlockPos(tag, "pos");
            if (dimension == null || pos.isEmpty()) {
                return null;
            }
            return new Site(tag.getString("kind"), ResourceKey.create(Registries.DIMENSION, dimension), pos.get(),
                    Direction.from2DDataValue(tag.getInt("facing")), tag.getInt("progress"));
        }

        public Site withProgress(int progress) {
            return new Site(kind, dimension, pos, facing, progress);
        }
    }

    private final Map<UUID, Site> sites = new HashMap<>();

    public static WorkSites get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(WorkSites::new, WorkSites::load), NAME);
    }

    private static WorkSites load(CompoundTag tag, HolderLookup.Provider registries) {
        WorkSites out = new WorkSites();
        CompoundTag all = tag.getCompound("sites");
        for (String key : all.getAllKeys()) {
            try {
                Site site = Site.load(all.getCompound(key));
                if (site != null) {
                    out.sites.put(UUID.fromString(key), site);
                }
            } catch (IllegalArgumentException ignored) {
                // a broken entry is left out
            }
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        sites.forEach((id, site) -> all.put(id.toString(), site.save()));
        tag.put("sites", all);
        return tag;
    }

    @Nullable
    public Site of(UUID machine) {
        return sites.get(machine);
    }

    public void set(UUID machine, @Nullable Site site) {
        if (site == null) {
            if (sites.remove(machine) != null) {
                setDirty();
            }
        } else if (!site.equals(sites.put(machine, site))) {
            setDirty();
        }
    }

    public Map<UUID, Site> all() {
        return Map.copyOf(sites);
    }
}
