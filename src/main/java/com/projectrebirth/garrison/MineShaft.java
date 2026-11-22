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
import net.minecraft.core.Direction;
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
 * 1.8.0, block J: the mine a miner of its own digs, step by step, the same every time from its start (so it goes on after a
 * restart from the steps done, {@link WorkSites}).
 * <ul>
 * <li><b>The stair:</b> a spiral going down round a square of 7 × 7, each side six steps down and a landing, three blocks high,
 * down to mineDepth;</li>
 * <li><b>the tunnels:</b> at the bottom a main tunnel of mineCorridor blocks, two high, and every third block a side tunnel of
 * mineBranch blocks each way, to come across ore.</li>
 * </ul>
 * Each step is a column of actions ({@link #column}): a liquid next to it is plugged first; a falling block (sand, gravel,
 * concrete powder: anything {@link Fallable}) over its top is replaced with a filler before the top is dug, so nothing falls on
 * the miner; the floor is made solid with a filler where it is not; now and then a torch. Fillers never fall
 * ({@link #safeFiller}).
 * <p>
 * The place ({@link #findSite}): on dry solid ground, in a loaded chunk, off the land of every faction, at least mineBaseDistance
 * from its faction's cores, shelters and storages, in no structure.
 */
public final class MineShaft {
    static final int SIDE = 6;
    static final int STAIR_HEIGHT = 3;
    static final int TUNNEL_HEIGHT = 2;

    public enum Op {
        /** Break the block (keep what drops). */
        DIG,
        /** Make the cell solid with a filler (break what is there first, unless it is air or a liquid). */
        FILL,
        /** A torch on the floor of the cell. */
        TORCH
    }

    public record Action(Op op, BlockPos pos) {
    }

    /** Where the miner stands at a step, the way it faces, how high the passage is; null past the end of the mine. */
    public record Step(BlockPos feet, Direction facing, int height) {
    }

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

    /** The step of that index, or null after the last one. */
    @Nullable
    public static Step step(BlockPos origin, Direction first, int index) {
        int depth = RebirthConfig.MINE_DEPTH.get();
        int corridor = RebirthConfig.MINE_CORRIDOR.get();
        int branch = RebirthConfig.MINE_BRANCH.get();
        // the stair: index 0 is the first column, at its start
        int x = origin.getX(), y = origin.getY(), z = origin.getZ();
        Direction dir = first;
        if (index == 0) {
            return new Step(origin, dir, STAIR_HEIGHT);
        }
        int i = 0;
        while (y > depth) {
            int k = i % (SIDE + 1);
            dir = rotate(first, i / (SIDE + 1));
            x += dir.getStepX();
            z += dir.getStepZ();
            if (k < SIDE) {
                y--;
            }
            i++;
            if (i == index) {
                return new Step(new BlockPos(x, y, z), dir, STAIR_HEIGHT);
            }
        }
        // the tunnels, from the last column of the stair
        int rest = index - i - 1;
        Direction left = dir.getCounterClockWise(), right = dir.getClockWise();
        for (int j = 1; j <= corridor; j++) {
            BlockPos main = new BlockPos(x + dir.getStepX() * j, y, z + dir.getStepZ() * j);
            if (rest-- == 0) {
                return new Step(main, dir, TUNNEL_HEIGHT);
            }
            if (j % 3 == 0) {
                for (Direction side : new Direction[] {left, right}) {
                    for (int b = 1; b <= branch; b++) {
                        if (rest-- == 0) {
                            return new Step(main.relative(side, b), side, TUNNEL_HEIGHT);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static Direction rotate(Direction dir, int times) {
        Direction out = dir;
        for (int i = 0; i < (times & 3); i++) {
            out = out.getClockWise();
        }
        return out;
    }

    /** What to do at a step, in order (see the class). */
    public static List<Action> column(ServerLevel level, Step step, int index) {
        List<Action> out = new ArrayList<>();
        BlockPos feet = step.feet();
        BlockPos top = feet.above(step.height() - 1);
        // liquids round the passage and over it: plugged first
        for (int h = 0; h < step.height(); h++) {
            BlockPos cell = feet.above(h);
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos near = cell.relative(side);
                if (!level.getFluidState(near).isEmpty() && !inPassage(near, step)) {
                    out.add(new Action(Op.FILL, near.immutable()));
                }
            }
        }
        BlockState over = level.getBlockState(top.above());
        if (!level.getFluidState(top.above()).isEmpty() || falling(over)) {
            // a liquid or something that would fall over the top: a filler holds it up
            out.add(new Action(Op.FILL, top.above()));
        }
        // the passage, from the top down (what is above a dug cell is air or held up)
        for (int h = step.height() - 1; h >= 0; h--) {
            BlockPos cell = feet.above(h);
            if (!level.getFluidState(cell).isEmpty()) {
                out.add(new Action(Op.FILL, cell));
            }
            BlockState there = level.getBlockState(cell);
            if (!there.isAir() && !there.is(Blocks.TORCH) || !level.getFluidState(cell).isEmpty()) {
                out.add(new Action(Op.DIG, cell));
            }
        }
        // a floor that holds: never air, a liquid, half a block, or a falling block over a hollow
        BlockPos floor = feet.below();
        BlockState ground = level.getBlockState(floor);
        boolean holds = ground.isCollisionShapeFullBlock(level, floor) && level.getFluidState(floor).isEmpty()
                && !(falling(ground) && level.getBlockState(floor.below()).isAir());
        if (!holds) {
            out.add(new Action(Op.FILL, floor));
        }
        int every = RebirthConfig.MINE_TORCH_EVERY.get();
        if (every > 0 && index > 0 && index % every == 0 && !level.getBlockState(feet).is(Blocks.TORCH)) {
            out.add(new Action(Op.TORCH, feet));
        }
        return out;
    }

    /** Whether a cell is part of this step's passage (not to be plugged). */
    private static boolean inPassage(BlockPos pos, Step step) {
        return pos.getX() == step.feet().getX() && pos.getZ() == step.feet().getZ() && pos.getY() >= step.feet().getY()
                && pos.getY() < step.feet().getY() + step.height();
    }

    // ------------------------------------------------------------------ the place

    /**
     * A place for a mine near `from` (its storage, or itself), or null: tries a few dozen places on rings from mineBaseDistance
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
            int x = Mth.floor(from.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(from.getZ() + Math.sin(angle) * distance);
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
        for (long unit : Shelters.find(level).unitPositions()) {
            out.add(BlockPos.of(unit));
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

    /** The place of a mine at that column (where the miner stands to start), or null when it does not fit. Public for tests. */
    @Nullable
    public static BlockPos siteAt(ServerLevel level, int x, int z, List<BlockPos> keepOff) {
        int base = RebirthConfig.MINE_BASE_DISTANCE.get();
        BlockPos probe = new BlockPos(x, level.getSeaLevel(), z);
        if (!level.hasChunkAt(probe) || !level.hasChunkAt(probe.offset(SIDE + 1, 0, SIDE + 1))) {
            return null;
        }
        int y = surface(level, x, z);
        BlockPos feet = new BlockPos(x, y, z);
        for (BlockPos off : keepOff) {
            if (off.distSqr(feet) < (double) base * base) {
                return null;
            }
        }
        // the whole square of the stair: solid dry ground, no faction's land, no structure, not much up and down
        for (int dx = -1; dx <= SIDE + 1; dx += 2) {
            for (int dz = -1; dz <= SIDE + 1; dz += 2) {
                BlockPos column = new BlockPos(x + dx, 0, z + dz);
                int top = surface(level, column.getX(), column.getZ());
                BlockPos ground = new BlockPos(column.getX(), top - 1, column.getZ());
                BlockState state = level.getBlockState(ground);
                if (Math.abs(top - y) > 3 || !state.isCollisionShapeFullBlock(level, ground) || !level.getFluidState(ground).isEmpty()
                        || !level.getFluidState(ground.above()).isEmpty() || state.is(BlockTags.LEAVES) || falling(state)) {
                    return null;
                }
                if (FactionService.territoryAt(level.getServer(), level, ground).isPresent() || level.structureManager().hasAnyStructureAt(ground)) {
                    return null;
                }
            }
        }
        return feet;
    }
}
