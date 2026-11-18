package com.projectrebirth.garrison;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.power.BodyPower;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.factions.Faction;
import com.projectrebirth.factions.FactionService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * The work of a work android (1.7.0, block F; the concept's section 5.3): a miner, a lumberjack or a farmer in the area of its
 * order, handing in at the order's chest. Everything runs on the server, as fast as a player with the same tool times its body
 * class's work speed, and only in loaded chunks.
 *
 * <ul>
 *   <li>Common: the area is scanned a few hundred cells a tick; what it gets goes into its gear (never the hand, the armor or the
 *   off hand); full, or done, it carries it to the chest and keeps a few seeds, saplings or fillers. The tool is the one in hand
 *   and wears; a miner without a pickaxe fetches one from the chest. Attacked, it runs to the chest (the console hears of the
 *   fight).</li>
 *   <li>Safety: no block with a block entity (containers, the machines of the mod, capsules, the faction core), none of the tag
 *   {@code projectrebirth:android_protected} (shelter doors and hatches), nothing unbreakable, nothing on land its owner may not
 *   use, nothing another mod's protection forbids (a break event of a stand-in player with the owner's id).</li>
 *   <li>Miner: layer by layer from the top; along the wall a spiral stair is left (one block a layer) to climb out; water and lava
 *   are plugged with a filler from the gear or skipped. In "ores only" everything is mined but only ores are kept.</li>
 *   <li>Lumberjack: a tree is a log on no log with natural leaves near it (buildings are left); it is felled whole from the bottom,
 *   its leaves cut (option), a sapling planted where it stood (option).</li>
 *   <li>Farmer: only what is ripe, replanted from what it got; empty farmland is sown; tilling with a hoe is an option.</li>
 * </ul>
 */
final class AndroidWork {
    static final TagKey<Block> PROTECTED = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(ProjectRebirth.MOD_ID, "android_protected"));
    /** Cells looked at in one tick while it looks for the next job. */
    private static final int SCAN_PER_TICK = 512;
    /** Ticks it tries to reach a job before it gives it up for this pass. */
    private static final int GIVE_UP_TICKS = 200;
    /** A job whose place this many path searches in a row did not reach is skipped for this pass (1.7.3, block F). */
    private static final int UNREACHABLE_SEARCHES = 2;
    private static final double REACH = 4.5;
    private static final int TREE_LOGS = 320;
    private static final int TREE_LEAVES = 600;

    private enum Kind {
        BREAK, BERRIES, PLANT, TILL
    }

    /** One thing to do at one block; tall jobs (trees, cane) are reached from beside, at any height. */
    private record Job(Kind kind, BlockPos pos, boolean tall) {
    }

    private final AndroidEntity android;
    private MachineTask task = MachineTask.STAND_DOWN;
    private int cursor;
    private boolean workedThisPass;
    private final Set<Long> plugged = new HashSet<>();
    private final Set<Long> skipped = new HashSet<>();
    private final List<BlockPos> toPlant = new ArrayList<>();
    private final List<BlockPos> treeBases = new ArrayList<>();
    private final Deque<Job> queue = new ArrayDeque<>();
    private final Set<UUID> ignoredItems = new HashSet<>();
    @Nullable
    private Job job;
    @Nullable
    private ItemEntity pickup;
    private float progress;
    private int stage = -1;
    private int approach;
    /** Path searches in a row that did not reach the current job's place (1.7.3, block F). */
    private int failedPaths;
    private boolean unloading;
    private int rest;
    private int flee;
    private int pickupWait;
    /** Ticks between two blocks broken, as a player has (block J: the vanilla delay of five ticks). */
    private int pause;
    private final EnumSet<ConsoleAlarm> raised = EnumSet.noneOf(ConsoleAlarm.class);

    AndroidWork(AndroidEntity android) {
        this.android = android;
    }

    // ------------------------------------------------------------------ the order

    private boolean hasOrder() {
        MachineTask now = android.machineCore().task();
        return now.type() == MachineTask.Type.WORK && now.from().isPresent() && now.to().isPresent() && now.chest().isPresent()
                && MachineTask.WORKS.contains(now.work());
    }

    /** Whether it works now: a work android with a work order, in working order. */
    boolean active() {
        return android.isWorker() && android.working() && hasOrder();
    }

    /** A new order starts the work over; an order of another kind ends the work's reports. */
    void checkOrder() {
        MachineTask now = android.machineCore().task();
        if (!now.equals(task)) {
            task = now;
            reset();
            cursor = 0;
            plugged.clear();
            toPlant.clear();
        }
        if (!hasOrder()) {
            for (ConsoleAlarm alarm : List.copyOf(raised)) {
                report(alarm, false);
            }
        }
    }

    private void reset() {
        abortBreak();
        job = null;
        pickup = null;
        queue.clear();
        treeBases.clear();
        skipped.clear();
        ignoredItems.clear();
        unloading = false;
        rest = 0;
        workedThisPass = false;
    }

    private String work() {
        return task.work();
    }

    private boolean mining() {
        return work().startsWith("mine");
    }

    private boolean enabled() {
        return switch (work()) {
            case "mine", "mine_ore" -> RebirthConfig.WORK_MINE.get();
            case "lumber" -> RebirthConfig.WORK_LUMBER.get();
            default -> RebirthConfig.WORK_FARM.get();
        };
    }

    private void report(ConsoleAlarm alarm, boolean on) {
        if (on ? raised.add(alarm) : raised.remove(alarm)) {
            ConsoleLink.report(android, android.machineCore(), alarm, on);
        }
    }

    /** Public for tests (through the android): the reports it raised. */
    boolean raised(ConsoleAlarm alarm) {
        return raised.contains(alarm);
    }

    // ------------------------------------------------------------------ the area

    private int minX() {
        return Math.min(task.from().get().getX(), task.to().get().getX());
    }

    private int maxX() {
        return Math.max(task.from().get().getX(), task.to().get().getX());
    }

    private int minZ() {
        return Math.min(task.from().get().getZ(), task.to().get().getZ());
    }

    private int maxZ() {
        return Math.max(task.from().get().getZ(), task.to().get().getZ());
    }

    /** The miner works between the corners; the lumberjack and the farmer a little below and above them (hills, trees). */
    private int minY() {
        int y = Math.min(task.from().get().getY(), task.to().get().getY());
        return mining() ? y : y - 3;
    }

    private int maxY() {
        int y = Math.max(task.from().get().getY(), task.to().get().getY());
        return mining() ? y : y + (work().equals("lumber") ? 8 : 4);
    }

    private int sizeX() {
        return maxX() - minX() + 1;
    }

    private int sizeZ() {
        return maxZ() - minZ() + 1;
    }

    private int cells() {
        return sizeX() * sizeZ() * (maxY() - minY() + 1);
    }

    /** The cell of a scan index: the miner layer by layer from the top, back and forth; the others column by column, from below. */
    private BlockPos cell(int index) {
        int sx = sizeX(), sz = sizeZ();
        if (mining()) {
            int layer = index / (sx * sz);
            int rest = index % (sx * sz);
            int row = rest / sx;
            int col = rest % sx;
            if ((row & 1) == 1) {
                col = sx - 1 - col;
            }
            return new BlockPos(minX() + col, maxY() - layer, minZ() + row);
        }
        int height = maxY() - minY() + 1;
        int column = index / height;
        return new BlockPos(minX() + column % sx, minY() + index % height, minZ() + column / sx);
    }

    /** The spiral stair of a mine: one block a layer along the wall, each a step lower than the one before. */
    boolean isStep(BlockPos pos) {
        int sx = sizeX(), sz = sizeZ();
        if (!mining() || sx < 3 || sz < 3) {
            return false;
        }
        int x = pos.getX() - minX(), z = pos.getZ() - minZ();
        int index;
        if (z == 0) {
            index = x;
        } else if (x == sx - 1) {
            index = sx - 1 + z;
        } else if (z == sz - 1) {
            index = sx - 1 + sz - 1 + (sx - 1 - x);
        } else if (x == 0) {
            index = 2 * (sx - 1) + sz - 1 + (sz - 1 - z);
        } else {
            return false;
        }
        int perimeter = 2 * (sx + sz) - 4;
        return index == Math.floorMod(maxY() - pos.getY(), perimeter);
    }

    // ------------------------------------------------------------------ one tick

    void tick() {
        ServerLevel level = (ServerLevel) android.level();
        if (flee > 0) {
            flee--;
            BlockPos chest = task.chest().get();
            if (flee % 20 == 0 && android.distanceToSqr(Vec3.atCenterOf(chest)) > 9.0) {
                android.getNavigation().moveTo(chest.getX() + 0.5, chest.getY(), chest.getZ() + 0.5, 1.2);
            }
            return;
        }
        if (pause > 0) {
            pause--;
            return;
        }
        if (rest > 0) {
            rest--;
            android.getNavigation().stop();
            return;
        }
        if (!enabled()) {
            android.getNavigation().stop();
            return;
        }
        if (needsTool() && !unloading) {
            unloading = true;
        }
        if (unloading) {
            unload(level);
            return;
        }
        if (job != null) {
            doJob(level);
            return;
        }
        if (pickup != null) {
            doPickup();
            return;
        }
        if (!queue.isEmpty()) {
            job = queue.poll();
            approach = 0;
            return;
        }
        if (!treeBases.isEmpty()) {
            if (RebirthConfig.WORK_REPLANT_SAPLINGS.get()) {
                toPlant.addAll(treeBases);
                while (toPlant.size() > 64) {
                    toPlant.remove(0);
                }
            }
            treeBases.clear();
        }
        if (!toPlant.isEmpty() && plantSapling(level)) {
            return;
        }
        if (!mining() && --pickupWait <= 0) {
            pickupWait = 20;
            pickup = nearestItem(level);
            if (pickup != null) {
                approach = 0;
                return;
            }
        }
        scan(level);
    }

    /** Looks at the next cells for a job; at the end of a pass with nothing done the area is done for now. */
    private void scan(ServerLevel level) {
        int total = cells();
        for (int i = 0; i < SCAN_PER_TICK; i++) {
            if (cursor >= total) {
                cursor = 0;
                skipped.clear();
                ignoredItems.clear();
                if (workedThisPass) {
                    workedThisPass = false;
                    return;
                }
                if (hasLoad()) {
                    unloading = true;
                    return;
                }
                report(ConsoleAlarm.WORK_DONE, true);
                rest = RebirthConfig.WORK_REST_SECONDS.get() * 20;
                return;
            }
            BlockPos pos = cell(cursor++);
            if (skipped.contains(pos.asLong()) || !level.isLoaded(pos)) {
                continue;
            }
            Job found = switch (work()) {
                case "lumber" -> lumberJob(level, pos);
                case "farm" -> farmJob(level, pos);
                default -> mineJob(level, pos);
            };
            if (found != null) {
                job = found;
                approach = 0;
                return;
            }
        }
    }

    // ------------------------------------------------------------------ what it may break

    private boolean mayBreak(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.hasBlockEntity() || state.is(PROTECTED) || state.getDestroySpeed(level, pos) < 0.0f) {
            return false;
        }
        return mayWorkAt(level, pos) && !CommonHooks.fireBlockBreak(level, GameType.SURVIVAL, standIn(level), pos, state).isCanceled();
    }

    /** Off faction land, or on the land of its owner's faction where the owner may use containers. */
    private boolean mayWorkAt(ServerLevel level, BlockPos pos) {
        Optional<Faction> land = FactionService.territoryAt(level.getServer(), level, pos);
        if (land.isEmpty()) {
            return true;
        }
        UUID owner = android.machineCore().owner();
        return owner != null && land.get().isMember(owner) && land.get().role(owner).canUseContainers();
    }

    /** A player that stands in for the owner when other mods are asked whether a block may be broken. */
    private FakePlayer standIn(ServerLevel level) {
        UUID owner = android.machineCore().owner();
        return FakePlayerFactory.get(level, new GameProfile(owner != null ? owner : android.getUUID(), "[Android]"));
    }

    // ------------------------------------------------------------------ the miner

    @Nullable
    private Job mineJob(ServerLevel level, BlockPos pos) {
        if (plugged.contains(pos.asLong()) || isStep(pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return null;
        }
        if (state.getBlock() instanceof LiquidBlock) {
            // water and lava: plugged with a filler from the gear, or left
            int filler = fillerSlot();
            if (filler < 0) {
                skipped.add(pos.asLong());
                return null;
            }
            return new Job(Kind.PLANT, pos, false);
        }
        if (!mayBreak(level, pos, state)) {
            skipped.add(pos.asLong());
            return null;
        }
        return new Job(Kind.BREAK, pos, false);
    }

    private boolean keepsAll() {
        return !work().equals("mine_ore");
    }

    private static boolean isOre(BlockState state) {
        if (state.is(Tags.Blocks.ORES)) {
            return true;
        }
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return RebirthConfig.WORK_ORE_BLOCKS.get().contains(id);
    }

    private static boolean isFiller(ItemStack stack) {
        return !stack.isEmpty() && RebirthConfig.WORK_FILLER_BLOCKS.get().contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                && stack.getItem() instanceof BlockItem;
    }

    private int fillerSlot() {
        SimpleContainer gear = android.gear();
        for (int i = 1; i < 36; i++) {
            if (isFiller(gear.getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ the lumberjack

    @Nullable
    private Job lumberJob(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(BlockTags.LOGS) || level.getBlockState(pos.below()).is(BlockTags.LOGS)) {
            return null;
        }
        List<BlockPos> logs = tree(level, pos);
        if (logs.isEmpty()) {
            skipped.add(pos.asLong());
            return null;
        }
        int bottom = logs.stream().mapToInt(BlockPos::getY).min().orElse(pos.getY());
        for (BlockPos log : logs) {
            if (log.getY() == bottom && !level.getBlockState(log.below()).is(BlockTags.LOGS)) {
                treeBases.add(log);
            }
        }
        logs.sort(Comparator.comparingInt((BlockPos p) -> p.getY()).thenComparingDouble(p -> p.distSqr(pos)));
        for (BlockPos log : logs) {
            queue.add(new Job(Kind.BREAK, log, true));
        }
        if (RebirthConfig.WORK_CUT_LEAVES.get()) {
            for (BlockPos leaf : leaves(level, logs)) {
                queue.add(new Job(Kind.BREAK, leaf, true));
            }
        }
        return queue.poll();
    }

    /** The logs of the tree standing on this log, or none when it has no natural leaves (a building) or may not be felled. */
    private List<BlockPos> tree(ServerLevel level, BlockPos base) {
        List<BlockPos> logs = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Deque<BlockPos> open = new ArrayDeque<>();
        open.add(base);
        seen.add(base.asLong());
        boolean natural = false;
        while (!open.isEmpty() && logs.size() < TREE_LOGS) {
            BlockPos log = open.poll();
            BlockState state = level.getBlockState(log);
            if (!mayBreak(level, log, state)) {
                return List.of();
            }
            logs.add(log);
            for (Direction side : Direction.values()) {
                if (naturalLeaves(level.getBlockState(log.relative(side)))) {
                    natural = true;
                }
            }
            for (BlockPos near : BlockPos.betweenClosed(log.offset(-1, 0, -1), log.offset(1, 1, 1))) {
                if (near.getY() < base.getY() || near.getX() < minX() - 6 || near.getX() > maxX() + 6 || near.getZ() < minZ() - 6
                        || near.getZ() > maxZ() + 6 || !seen.add(near.asLong()) || !level.isLoaded(near)) {
                    continue;
                }
                if (level.getBlockState(near).is(BlockTags.LOGS)) {
                    open.add(near.immutable());
                }
            }
        }
        return natural ? logs : List.of();
    }

    private static boolean naturalLeaves(BlockState state) {
        return state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT);
    }

    /** The natural leaves close to the felled logs (not those of the trees around). */
    private List<BlockPos> leaves(ServerLevel level, List<BlockPos> logs) {
        List<BlockPos> found = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (BlockPos log : logs) {
            for (BlockPos near : BlockPos.betweenClosed(log.offset(-3, -1, -3), log.offset(3, 3, 3))) {
                if (found.size() >= TREE_LEAVES) {
                    break;
                }
                if (seen.add(near.asLong()) && level.isLoaded(near) && naturalLeaves(level.getBlockState(near))
                        && mayWorkAt(level, near)) {
                    found.add(near.immutable());
                }
            }
        }
        found.sort(Comparator.comparingInt((BlockPos p) -> p.getY()).reversed());
        return found;
    }

    /** The sapling of the kind of the felled tree when it has one, else any. */
    private boolean plantSapling(ServerLevel level) {
        BlockPos pos = toPlant.get(0);
        if (!level.isLoaded(pos) || !level.getBlockState(pos).canBeReplaced() || !level.getBlockState(pos).getFluidState().isEmpty()
                || !mayWorkAt(level, pos)) {
            toPlant.remove(0);
            return true;
        }
        int slot = -1;
        for (int i = 1; i < 36 && slot < 0; i++) {
            ItemStack stack = android.gear().getItem(i);
            if (stack.is(ItemTags.SAPLINGS) && stack.getItem() instanceof BlockItem item && item.getBlock().defaultBlockState().canSurvive(level, pos)) {
                slot = i;
            }
        }
        if (slot < 0) {
            return false;
        }
        if (!reach(pos, false)) {
            if (++approach > GIVE_UP_TICKS) {
                toPlant.remove(0);
                approach = 0;
            }
            return true;
        }
        approach = 0;
        ItemStack sapling = android.gear().getItem(slot);
        level.setBlockAndUpdate(pos, ((BlockItem) sapling.getItem()).getBlock().defaultBlockState());
        level.playSound(null, pos, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 1.0f, 0.8f);
        sapling.shrink(1);
        android.gear().setChanged();
        android.workSwing();
        toPlant.remove(0);
        return true;
    }

    // ------------------------------------------------------------------ the farmer

    @Nullable
    private Job farmJob(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        boolean ripe = block instanceof CropBlock crop && crop.isMaxAge(state)
                || block instanceof NetherWartBlock && state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE
                || block instanceof CocoaBlock && state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE
                || (state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN)) && attachedFruit(level, pos);
        if (ripe) {
            return mayBreak(level, pos, state) ? new Job(Kind.BREAK, pos, false) : skip(pos);
        }
        if (block instanceof SweetBerryBushBlock && state.getValue(SweetBerryBushBlock.AGE) >= 2) {
            return mayWorkAt(level, pos) ? new Job(Kind.BERRIES, pos, false) : skip(pos);
        }
        if ((block instanceof SugarCaneBlock || block instanceof BambooStalkBlock) && level.getBlockState(pos.below()).is(block)
                && !level.getBlockState(pos.below(2)).is(block)) {
            // the cane above the lowest block, from the top down
            List<BlockPos> column = new ArrayList<>();
            for (BlockPos up = pos; level.getBlockState(up).is(block) && column.size() < 32; up = up.above()) {
                column.add(up);
            }
            for (int i = column.size() - 1; i >= 0; i--) {
                if (!mayBreak(level, column.get(i), level.getBlockState(column.get(i)))) {
                    queue.clear();
                    return skip(pos);
                }
                queue.add(new Job(Kind.BREAK, column.get(i), true));
            }
            return queue.poll();
        }
        if (state.isAir() && (level.getBlockState(pos.below()).is(Blocks.FARMLAND) || level.getBlockState(pos.below()).is(Blocks.SOUL_SAND))
                && seedSlot(level, pos) >= 0 && mayWorkAt(level, pos)) {
            return new Job(Kind.PLANT, pos, false);
        }
        if (RebirthConfig.WORK_TILL.get() && android.gear().getItem(0).is(ItemTags.HOES) && tillable(state)
                && level.getBlockState(pos.above()).isAir() && mayWorkAt(level, pos)) {
            return new Job(Kind.TILL, pos, false);
        }
        return null;
    }

    @Nullable
    private Job skip(BlockPos pos) {
        skipped.add(pos.asLong());
        return null;
    }

    private static boolean tillable(BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT_PATH);
    }

    /** A melon or a pumpkin that grew from a stem (not one set as a block). */
    private static boolean attachedFruit(ServerLevel level, BlockPos pos) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockState near = level.getBlockState(pos.relative(side));
            if (near.getBlock() instanceof AttachedStemBlock && near.getValue(AttachedStemBlock.FACING) == side.getOpposite()) {
                return true;
            }
        }
        return false;
    }

    /** A slot of its gear with a seed that grows here (on farmland or soul sand), or -1. */
    private int seedSlot(ServerLevel level, BlockPos pos) {
        for (int i = 1; i < 36; i++) {
            ItemStack stack = android.gear().getItem(i);
            if (stack.getItem() instanceof BlockItem item && (item.getBlock() instanceof CropBlock || item.getBlock() instanceof NetherWartBlock)
                    && item.getBlock().defaultBlockState().canSurvive(level, pos)) {
                return i;
            }
        }
        return -1;
    }

    /** What it plants again where a crop was: the crop's own seed from its gear, if it has one. */
    private void replant(ServerLevel level, BlockPos pos, BlockState was) {
        Block block = was.getBlock();
        if (!(block instanceof CropBlock || block instanceof NetherWartBlock || block instanceof CocoaBlock)) {
            return;
        }
        Item seed = block.asItem();
        for (int i = 1; i < 36; i++) {
            ItemStack stack = android.gear().getItem(i);
            if (stack.is(seed)) {
                BlockState planted = block instanceof CropBlock crop ? crop.getStateForAge(0)
                        : block instanceof CocoaBlock ? was.setValue(CocoaBlock.AGE, 0) : block.defaultBlockState();
                if (planted.canSurvive(level, pos)) {
                    level.setBlockAndUpdate(pos, planted);
                    level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0f, 1.0f);
                    stack.shrink(1);
                    android.gear().setChanged();
                }
                return;
            }
        }
    }

    // ------------------------------------------------------------------ doing a job

    /** Whether it reaches the block from where it stands; if not, it walks there. */
    private boolean reach(BlockPos pos, boolean tall) {
        Vec3 center = Vec3.atCenterOf(pos);
        boolean near = tall
                ? Math.pow(android.getX() - center.x, 2) + Math.pow(android.getZ() - center.z, 2) <= REACH * REACH && Math.abs(center.y - android.getY()) < 40
                : android.getEyePosition().distanceToSqr(center) <= REACH * REACH;
        if (near) {
            android.getNavigation().stop();
            android.getLookControl().setLookAt(center.x, center.y, center.z);
            return true;
        }
        if (approach % 20 == 0) {
            android.getNavigation().moveTo(pos.getX() + 0.5, tall ? android.getY() : pos.getY() + 1, pos.getZ() + 0.5, 1.0);
            // 1.7.3, block F: a search that cannot reach the place goes through the whole range of a machine's path (up to
            // thousands of nodes, several ms); count them, so that doJob skips such a place after two instead of ten
            net.minecraft.world.level.pathfinder.Path path = android.getNavigation().getPath();
            failedPaths = path == null || !path.canReach() ? failedPaths + 1 : 0;
        }
        return false;
    }

    private void doJob(ServerLevel level) {
        Job now = job;
        BlockState state = level.getBlockState(now.pos());
        if (!level.isLoaded(now.pos())) {
            finishJob(false);
            return;
        }
        if (approach == 0) {
            failedPaths = 0;
        }
        if (!reach(now.pos(), now.tall())) {
            if (++approach > GIVE_UP_TICKS || failedPaths >= UNREACHABLE_SEARCHES) {
                skipped.add(now.pos().asLong());
                queue.clear();
                finishJob(false);
            }
            return;
        }
        switch (now.kind()) {
            case BREAK -> {
                if (state.isAir() || !mayBreakCheap(level, now.pos(), state)) {
                    finishJob(false);
                    return;
                }
                progress += breakSpeed(level, now.pos(), state);
                if (level.getGameTime() % 5 == 0) {
                    android.workSwing();
                }
                int next = (int) (progress * 10.0f);
                if (next != stage && progress < 1.0f) {
                    stage = next;
                    level.destroyBlockProgress(android.getId(), now.pos(), stage);
                }
                if (progress >= 1.0f) {
                    breakBlock(level, now.pos(), state);
                    finishJob(true);
                    pause = 5;
                }
            }
            case BERRIES -> {
                if (!(state.getBlock() instanceof SweetBerryBushBlock) || state.getValue(SweetBerryBushBlock.AGE) < 2) {
                    finishJob(false);
                    return;
                }
                int age = state.getValue(SweetBerryBushBlock.AGE);
                store(new ItemStack(net.minecraft.world.item.Items.SWEET_BERRIES, 1 + level.random.nextInt(2) + (age == 3 ? 1 : 0)));
                level.setBlockAndUpdate(now.pos(), state.setValue(SweetBerryBushBlock.AGE, 1));
                level.playSound(null, now.pos(), SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0f, 0.8f + level.random.nextFloat() * 0.4f);
                android.workSwing();
                used(level);
                finishJob(true);
            }
            case PLANT -> {
                if (mining()) {
                    int slot = fillerSlot();
                    if (slot >= 0 && state.getBlock() instanceof LiquidBlock) {
                        ItemStack filler = android.gear().getItem(slot);
                        level.setBlockAndUpdate(now.pos(), ((BlockItem) filler.getItem()).getBlock().defaultBlockState());
                        level.playSound(null, now.pos(), SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
                        filler.shrink(1);
                        android.gear().setChanged();
                        plugged.add(now.pos().asLong());
                        android.workSwing();
                    }
                    finishJob(slot >= 0);
                    return;
                }
                int slot = seedSlot(level, now.pos());
                if (slot >= 0 && state.isAir()) {
                    ItemStack seed = android.gear().getItem(slot);
                    level.setBlockAndUpdate(now.pos(), ((BlockItem) seed.getItem()).getBlock().defaultBlockState());
                    level.playSound(null, now.pos(), SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0f, 1.0f);
                    seed.shrink(1);
                    android.gear().setChanged();
                    android.workSwing();
                }
                finishJob(slot >= 0);
            }
            case TILL -> {
                ItemStack hoe = android.gear().getItem(0);
                if (!tillable(state) || !hoe.is(ItemTags.HOES) || !level.getBlockState(now.pos().above()).isAir()) {
                    finishJob(false);
                    return;
                }
                level.setBlockAndUpdate(now.pos(), Blocks.FARMLAND.defaultBlockState());
                level.playSound(null, now.pos(), SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0f, 1.0f);
                hoe.hurtAndBreak(1, level, android, item -> {
                });
                android.gear().setChanged();
                android.workSwing();
                finishJob(true);
            }
        }
    }

    /** The checks that may change while it breaks (the rest were made when the job was found). */
    private static boolean mayBreakCheap(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.hasBlockEntity() && !state.is(PROTECTED) && state.getDestroySpeed(level, pos) >= 0.0f;
    }

    private void finishJob(boolean done) {
        abortBreak();
        job = null;
        approach = 0;
        if (done) {
            workedThisPass = true;
            report(ConsoleAlarm.WORK_DONE, false);
        }
    }

    private void abortBreak() {
        if (job != null && stage >= 0) {
            android.level().destroyBlockProgress(android.getId(), job.pos(), -1);
        }
        progress = 0.0f;
        stage = -1;
    }

    /** What a player with the tool in hand breaks in a tick, times the work speed of its body class. */
    private float breakSpeed(ServerLevel level, BlockPos pos, BlockState state) {
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness <= 0.0f) {
            return 1.0f;
        }
        ItemStack tool = android.gear().getItem(0);
        if (!AndroidEntity.usable(tool)) {
            tool = ItemStack.EMPTY;
        }
        boolean correct = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
        float speed = Math.max(1.0f, tool.getDestroySpeed(state));
        return speed / hardness / (correct ? 30.0f : 100.0f) * RebirthConfig.androidStats(android.getBodyClass()).workSpeed().get().floatValue();
    }

    private void breakBlock(ServerLevel level, BlockPos pos, BlockState state) {
        ItemStack tool = android.gear().getItem(0);
        if (!AndroidEntity.usable(tool)) {
            tool = ItemStack.EMPTY;
        }
        boolean keep = !mining() || keepsAll() || isOre(state);
        boolean correct = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
        if (keep && correct) {
            BlockEntity entity = level.getBlockEntity(pos);
            for (ItemStack drop : Block.getDrops(state, level, pos, entity, android, tool)) {
                store(drop);
            }
        }
        level.destroyBlock(pos, false, android);
        if (!tool.isEmpty() && state.getDestroySpeed(level, pos) != 0.0f && tool.isDamageableItem()) {
            tool.hurtAndBreak(1, level, android, item -> {
            });
            android.gear().setChanged();
        }
        replant(level, pos, state);
        used(level);
    }

    private void used(ServerLevel level) {
        if (BodyPower.enabled()) {
            android.setCharge(android.charge() - (float) (RebirthConfig.WORK_ENERGY_PER_BLOCK.get() * BodyPower.useMultiplier(android.getBodyClass())));
        }
    }

    // ------------------------------------------------------------------ its gear

    /** Puts what it got into its gear (never the hand, the armor or the off hand); what does not fit lies where it is, and it goes to unload. */
    private void store(ItemStack stack) {
        ItemStack left = insert(android.gear(), stack.copy());
        if (!left.isEmpty()) {
            android.spawnAtLocation(left);
            unloading = true;
        } else if (freeSlots() == 0) {
            unloading = true;
        }
    }

    static ItemStack insert(SimpleContainer gear, ItemStack stack) {
        for (int i = 1; i < 36 && !stack.isEmpty(); i++) {
            ItemStack there = gear.getItem(i);
            if (!there.isEmpty() && ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < there.getMaxStackSize()) {
                int moved = Math.min(stack.getCount(), there.getMaxStackSize() - there.getCount());
                there.grow(moved);
                stack.shrink(moved);
            }
        }
        for (int i = 1; i < 36 && !stack.isEmpty(); i++) {
            if (gear.getItem(i).isEmpty()) {
                gear.setItem(i, stack.split(stack.getMaxStackSize()));
            }
        }
        gear.setChanged();
        return stack;
    }

    private int freeSlots() {
        int free = 0;
        for (int i = 1; i < 36; i++) {
            if (android.gear().getItem(i).isEmpty()) {
                free++;
            }
        }
        return free;
    }

    private TagKey<Item> toolTag() {
        return switch (work()) {
            case "lumber" -> ItemTags.AXES;
            case "farm" -> ItemTags.HOES;
            default -> ItemTags.PICKAXES;
        };
    }

    /** The miner cannot work without a pickaxe; the lumberjack fells by hand and the farmer needs a hoe only to till. */
    private boolean needsTool() {
        return mining() && !android.gear().getItem(0).is(ItemTags.PICKAXES);
    }

    /** How many of this it keeps when it hands in: its tools, and a few seeds, saplings or fillers of a kind. */
    private int keep(ItemStack stack) {
        if (stack.is(toolTag())) {
            return Integer.MAX_VALUE;
        }
        boolean wanted = switch (work()) {
            case "lumber" -> stack.is(ItemTags.SAPLINGS);
            case "farm" -> stack.getItem() instanceof BlockItem item && (item.getBlock() instanceof CropBlock || item.getBlock() instanceof NetherWartBlock
                    || item.getBlock() instanceof CocoaBlock);
            default -> isFiller(stack);
        };
        return wanted ? RebirthConfig.WORK_KEEP_SEEDS.get() : 0;
    }

    /** Whether it carries anything it would hand in. */
    private boolean hasLoad() {
        Map<Item, Integer> kept = new HashMap<>();
        for (int i = 1; i < 36; i++) {
            ItemStack stack = android.gear().getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            int already = kept.getOrDefault(stack.getItem(), 0);
            if (already + stack.getCount() > keep(stack)) {
                return true;
            }
            kept.put(stack.getItem(), already + stack.getCount());
        }
        return false;
    }

    /** To the chest: hands in its load, takes a tool if it needs one; a full or lost chest, or no tool, is reported. */
    private void unload(ServerLevel level) {
        BlockPos chest = task.chest().get();
        if (!level.isLoaded(chest)) {
            return;
        }
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, chest, null);
        if (handler == null) {
            report(ConsoleAlarm.CHEST_FULL, true);
            unloading = false;
            rest = RebirthConfig.WORK_REST_SECONDS.get() * 20;
            return;
        }
        if (android.distanceToSqr(Vec3.atCenterOf(chest)) > 6.25) {
            if (approach++ % 20 == 0) {
                android.getNavigation().moveTo(chest.getX() + 0.5, chest.getY(), chest.getZ() + 0.5, 1.0);
            }
            if (approach > GIVE_UP_TICKS * 2) {
                // it cannot get there: as good as a chest it cannot use
                approach = 0;
                report(ConsoleAlarm.CHEST_FULL, true);
                unloading = false;
                rest = RebirthConfig.WORK_REST_SECONDS.get() * 20;
            }
            return;
        }
        approach = 0;
        android.getNavigation().stop();
        android.getLookControl().setLookAt(Vec3.atCenterOf(chest));
        boolean full = handIn(handler);
        boolean tool = takeTool(handler);
        android.workSwing();
        level.playSound(null, chest, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5f, 1.0f);
        report(ConsoleAlarm.CHEST_FULL, full);
        report(ConsoleAlarm.NO_TOOL, mining() && !tool);
        unloading = false;
        if (full || mining() && !tool) {
            rest = RebirthConfig.WORK_REST_SECONDS.get() * 20;
        }
    }

    /** Hands in all but what it keeps; true when the chest had no room for all of it. */
    private boolean handIn(IItemHandler handler) {
        Map<Item, Integer> kept = new HashMap<>();
        boolean full = false;
        SimpleContainer gear = android.gear();
        for (int i = 1; i < 36; i++) {
            ItemStack stack = gear.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            int already = kept.getOrDefault(stack.getItem(), 0);
            int stays = Math.max(0, Math.min(stack.getCount(), keep(stack) - already));
            kept.put(stack.getItem(), already + stays);
            if (stays >= stack.getCount()) {
                continue;
            }
            ItemStack out = stack.copyWithCount(stack.getCount() - stays);
            ItemStack left = ItemHandlerHelper.insertItem(handler, out, false);
            full |= !left.isEmpty();
            stack.setCount(stays + left.getCount());
        }
        gear.setChanged();
        return full;
    }

    /** Takes the tool of its work from the chest when its hand has none; true when it has one now. */
    private boolean takeTool(IItemHandler handler) {
        SimpleContainer gear = android.gear();
        if (gear.getItem(0).is(toolTag())) {
            return true;
        }
        for (int i = 1; i < 36; i++) {
            if (gear.getItem(i).is(toolTag())) {
                ItemStack hand = gear.getItem(0);
                gear.setItem(0, gear.getItem(i));
                gear.setItem(i, hand);
                return true;
            }
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (handler.getStackInSlot(slot).is(toolTag())) {
                ItemStack taken = handler.extractItem(slot, 1, false);
                if (taken.isEmpty()) {
                    continue;
                }
                ItemStack hand = gear.getItem(0);
                gear.setItem(0, taken);
                if (!hand.isEmpty()) {
                    ItemStack left = insert(gear, hand);
                    if (!left.isEmpty()) {
                        left = ItemHandlerHelper.insertItem(handler, left, false);
                    }
                    if (!left.isEmpty()) {
                        android.spawnAtLocation(left);
                    }
                }
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ picking up

    /** The nearest item lying in its area that it can pick up, or null. */
    @Nullable
    private ItemEntity nearestItem(ServerLevel level) {
        AABB area = new AABB(minX() - 4, minY() - 4, minZ() - 4, maxX() + 5, maxY() + 12, maxZ() + 5);
        ItemEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area, item -> item.isAlive() && !item.hasPickUpDelay()
                && !ignoredItems.contains(item.getUUID()))) {
            double distance = android.distanceToSqr(item);
            if (distance < bestDistance) {
                best = item;
                bestDistance = distance;
            }
        }
        return best;
    }

    private void doPickup() {
        ItemEntity item = pickup;
        if (item == null || !item.isAlive()) {
            pickup = null;
            return;
        }
        if (android.distanceToSqr(item) > 2.25) {
            if (approach++ % 20 == 0) {
                android.getNavigation().moveTo(item, 1.0);
            }
            if (approach > GIVE_UP_TICKS) {
                ignoredItems.add(item.getUUID());
                pickup = null;
            }
            return;
        }
        ItemStack left = insert(android.gear(), item.getItem().copy());
        android.take(item, item.getItem().getCount() - left.getCount());
        if (left.isEmpty()) {
            item.discard();
        } else {
            item.setItem(left);
            ignoredItems.add(item.getUUID());
            unloading = true;
        }
        pickup = null;
        approach = 0;
    }

    // ------------------------------------------------------------------ attacked

    /** Hit by someone: it drops the work and runs to its chest for a while; the console hears of the fight. */
    void attacked() {
        if (!active()) {
            return;
        }
        reset();
        queue.clear();
        flee = RebirthConfig.WORK_FLEE_SECONDS.get() * 20;
    }

    void stop() {
        abortBreak();
        if (job != null) {
            // the job is found again on the next look
            job = null;
        }
        android.getNavigation().stop();
    }

    // ------------------------------------------------------------------ saving

    void save(CompoundTag tag) {
        CompoundTag work = new CompoundTag();
        MachineTask.CODEC.encodeStart(NbtOps.INSTANCE, task).result().ifPresent(found -> work.put("Task", found));
        work.putInt("Cursor", cursor);
        work.putLongArray("Plugged", plugged.stream().mapToLong(Long::longValue).toArray());
        work.putLongArray("Plant", toPlant.stream().mapToLong(BlockPos::asLong).toArray());
        int alarms = 0;
        for (ConsoleAlarm alarm : raised) {
            alarms |= 1 << alarm.ordinal();
        }
        work.putInt("Raised", alarms);
        tag.put("Work", work);
    }

    void load(CompoundTag tag) {
        CompoundTag work = tag.getCompound("Work");
        task = work.contains("Task") ? MachineTask.CODEC.parse(NbtOps.INSTANCE, work.get("Task")).result().orElse(MachineTask.STAND_DOWN)
                : MachineTask.STAND_DOWN;
        cursor = work.getInt("Cursor");
        plugged.clear();
        for (long pos : work.getLongArray("Plugged")) {
            plugged.add(pos);
        }
        toPlant.clear();
        for (long pos : work.getLongArray("Plant")) {
            toPlant.add(BlockPos.of(pos));
        }
        raised.clear();
        int alarms = work.getInt("Raised");
        for (ConsoleAlarm alarm : ConsoleAlarm.values()) {
            if ((alarms & (1 << alarm.ordinal())) != 0) {
                raised.add(alarm);
            }
        }
    }

    /** The goal of a work android: its work, while it has a work order and nothing more urgent (charging) holds it. */
    final class WorkGoal extends Goal {
        WorkGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            checkOrder();
            return active();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            AndroidWork.this.tick();
        }

        @Override
        public void stop() {
            AndroidWork.this.stop();
        }
    }
}
