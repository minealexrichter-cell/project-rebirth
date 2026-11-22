package com.projectrebirth.garrison;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.factions.Faction;
import com.projectrebirth.factions.FactionData;
import com.projectrebirth.factions.FactionService;
import com.projectrebirth.logistics.Storages;
import com.projectrebirth.shelter.Shelters;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 1.8.0, block J: the mine a miner of its own digs: a quarry, a square of mineSize (a whole chunk by default, on its borders) dug
 * layer by layer from the ground down to mineDepth, with a spiral stair along its wall (one block left standing a layer, each a
 * step lower, AndroidWork#isStep), and a torch on the stair every mineTorchEvery layers. Water and lava in it are plugged with
 * fillers that never fall ({@link #safeFiller}). The layer it got to is kept with the world ({@link WorkSites}), so it goes on
 * after a restart.
 * <p>
 * The place ({@link #findSite}): on dry solid ground, in loaded chunks, off the land of every faction, at least mineBaseDistance
 * from its faction's cores, shelters and storages, in no structure.
 */
public final class MineShaft {
    private MineShaft() {
    }

    /** Whether a block placed into a mine can never fall: no Fallable block, a full cube, no block entity. */
    public static boolean safeFiller(BlockState state) {
        return !(state.getBlock() instanceof Fallable) && !state.hasBlockEntity() && state.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                && !state.is(AndroidWork.PROTECTED);
    }

    static boolean falling(BlockState state) {
        return state.getBlock() instanceof Fallable;
    }

    /** The side of a quarry (the config's, at most a chunk). */
    public static int size() {
        return RebirthConfig.MINE_SIZE.get();
    }

    // ------------------------------------------------------------------ the place

    /**
     * A place for a quarry near `from` (its storage, or itself), or null: tries a few dozen places on rings from mineBaseDistance
     * to twice that out, takes the nearest that fits.
     */
    @Nullable
    public static BlockPos findSite(ServerLevel level, BlockPos from, @Nullable UUID owner, RandomSource random) {
        int base = RebirthConfig.MINE_BASE_DISTANCE.get();
        List<BlockPos> keepOff = keepOff(level, owner);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int attempt = 0; attempt < 48; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = base + 8 + random.nextDouble() * (base + 16);
            // on the borders of a chunk: a whole chunk dug out
            int x = Mth.floor(from.getX() + Math.cos(angle) * distance) & ~15;
            int z = Mth.floor(from.getZ() + Math.sin(angle) * distance) & ~15;
            BlockPos site = siteAt(level, x, z, keepOff);
            if (site != null && site.distSqr(from) < bestDistance) {
                best = site;
                bestDistance = site.distSqr(from);
            }
        }
        return best;
    }

    /** The places a mine keeps away from: the faction's cores, the shelters and the storages of the world. */
    private static List<BlockPos> keepOff(ServerLevel level, @Nullable UUID owner) {
        List<BlockPos> out = new ArrayList<>(Storages.all(level));
        // a world with no shelter has no shelter manager (find gives null)
        Shelters shelters = Shelters.find(level);
        if (shelters != null) {
            for (long unit : shelters.unitPositions()) {
                out.add(BlockPos.of(unit));
            }
        }
        Optional<Faction> faction = FactionData.get(level.getServer()).of(owner);
        faction.ifPresent(f -> {
            for (GlobalPos core : f.cores()) {
                if (core.dimension().equals(level.dimension())) {
                    out.add(core.pos());
                }
            }
        });
        return out;
    }

    /** The level over the ground of a column (barriers count as air: the walls round the plots of game tests). */
    private static int surface(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x, y - 1, z);
        while (at.getY() > level.getMinBuildHeight() && (level.getBlockState(at).isAir() || level.getBlockState(at).is(Blocks.BARRIER))) {
            at.move(0, -1, 0);
        }
        return at.getY() + 1;
    }

    /**
     * The quarry with its north-west corner at that column, or null when it does not fit: its corner at the level of its highest
     * ground (the first layer it digs). Public for tests.
     */
    @Nullable
    public static BlockPos siteAt(ServerLevel level, int x, int z, List<BlockPos> keepOff) {
        int base = RebirthConfig.MINE_BASE_DISTANCE.get();
        int size = size();
        BlockPos probe = new BlockPos(x, level.getSeaLevel(), z);
        if (!level.hasChunkAt(probe) || !level.hasChunkAt(probe.offset(size - 1, 0, size - 1))) {
            return null;
        }
        BlockPos middle = new BlockPos(x + size / 2, surface(level, x + size / 2, z + size / 2), z + size / 2);
        for (BlockPos off : keepOff) {
            if (off.distSqr(middle) < (double) base * base) {
                return null;
            }
        }
        // the whole square: solid dry ground, no faction's land, no structure, not much up and down
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (int dx = 0; dx < size; dx += 2) {
            for (int dz = 0; dz < size; dz += 2) {
                int cx = x + Math.min(dx, size - 1), cz = z + Math.min(dz, size - 1);
                int top = surface(level, cx, cz);
                BlockPos ground = new BlockPos(cx, top - 1, cz);
                BlockState state = level.getBlockState(ground);
                if (!state.isCollisionShapeFullBlock(level, ground) || !level.getFluidState(ground).isEmpty()
                        || !level.getFluidState(ground.above()).isEmpty() || state.is(BlockTags.LEAVES)) {
                    return null;
                }
                if (FactionService.territoryAt(level.getServer(), level, ground).isPresent() || level.structureManager().hasAnyStructureAt(ground)) {
                    return null;
                }
                low = Math.min(low, ground.getY());
                high = Math.max(high, ground.getY());
            }
        }
        if (high - low > 8 || high <= RebirthConfig.MINE_DEPTH.get()) {
            return null;
        }
        return new BlockPos(x, high, z);
    }
}
