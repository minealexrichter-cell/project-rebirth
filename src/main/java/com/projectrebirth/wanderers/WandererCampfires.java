package com.projectrebirth.wanderers;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.WastelandConfig;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * 1.9.0, block E: the campfires wanderers lit, and until when each stays. A fire burns at least [wasteland.wanderers]
 * campfireMinutes, whatever becomes of the group; the head of a camp keeps it longer. Kept in the world, so that a fire whose
 * group is gone is taken away after a restart too. A fire a player broke or put out is forgotten.
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class WandererCampfires extends SavedData {
    private static final String NAME = ProjectRebirth.MOD_ID + "_wanderer_campfires";
    /** How long past its last "keep" a fire stays, so that a camp's fire does not go out between two of them. */
    private static final long KEEP_TICKS = 100L;

    private final Long2LongOpenHashMap until = new Long2LongOpenHashMap();

    public static WandererCampfires get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(WandererCampfires::new, WandererCampfires::load), NAME);
    }

    private static WandererCampfires load(CompoundTag tag, HolderLookup.Provider registries) {
        WandererCampfires fires = new WandererCampfires();
        long[] positions = tag.getLongArray("positions"), times = tag.getLongArray("until");
        for (int i = 0; i < Math.min(positions.length, times.length); i++) {
            fires.until.put(positions[i], times[i]);
        }
        return fires;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] positions = until.keySet().toLongArray(), times = new long[positions.length];
        for (int i = 0; i < positions.length; i++) {
            times[i] = until.get(positions[i]);
        }
        tag.putLongArray("positions", positions);
        tag.putLongArray("until", times);
        return tag;
    }

    /** A fire was lit: it stays its shortest time. */
    public void lit(BlockPos pos, long now) {
        until.put(pos.asLong(), now + WastelandConfig.WANDERERS_CAMPFIRE_MINUTES.get() * 60L * 20L);
        setDirty();
    }

    /** The camp still needs its fire. A fire of a world from before this list is taken into it. */
    public void keep(ServerLevel level, BlockPos pos, long now) {
        if (!until.containsKey(pos.asLong())) {
            if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.CAMPFIRE)) {
                lit(pos, now);
            }
        } else if (until.get(pos.asLong()) < now + KEEP_TICKS) {
            until.put(pos.asLong(), now + KEEP_TICKS);
            setDirty();
        }
    }

    public boolean has(BlockPos pos) {
        return until.containsKey(pos.asLong());
    }

    /** Takes away the fires whose time is over (those in loaded places). Public for tests. */
    public void update(ServerLevel level, long now) {
        var entries = until.long2LongEntrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            BlockPos pos = BlockPos.of(entry.getLongKey());
            if (!level.isLoaded(pos)) {
                continue;
            }
            boolean there = level.getBlockState(pos).is(Blocks.CAMPFIRE);
            if (there && now < entry.getLongValue()) {
                continue;
            }
            if (there) {
                level.removeBlock(pos, false);
            }
            entries.remove();
            setDirty();
        }
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % 20L == 0L
                && com.projectrebirth.config.RebirthConfig.SERVER_SPEC.isLoaded()) {
            WandererCampfires fires = get(level);
            if (!fires.until.isEmpty()) {
                fires.update(level, level.getGameTime());
            }
        }
    }
}
