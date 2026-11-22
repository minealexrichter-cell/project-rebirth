package com.projectrebirth.gametest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyItems;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.factions.FactionBlocks;
import com.projectrebirth.factions.FactionService;
import com.projectrebirth.garrison.AiCoreItem;
import com.projectrebirth.garrison.AndroidEntity;
import com.projectrebirth.garrison.AndroidWork;
import com.projectrebirth.garrison.Androids;
import com.projectrebirth.garrison.ConsoleAlarm;
import com.projectrebirth.garrison.ConsoleBlockEntity;
import com.projectrebirth.garrison.ConsoleView;
import com.projectrebirth.garrison.GarrisonRegistry;
import com.projectrebirth.garrison.MachineTask;
import com.projectrebirth.garrison.MineShaft;
import com.projectrebirth.garrison.WorkSites;
import com.projectrebirth.logistics.LogisticsRegistry;
import com.projectrebirth.logistics.StorageControllerBlockEntity;
import com.projectrebirth.multiblock.MultiblockRegistry;
import com.projectrebirth.multiblock.MultiblockSchema;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.8.0, block J: work androids that find their work themselves, and the storage. The floor (floor_24x24) is stone six rows deep
 * (test y 1-6); machines stand at y 7. Tests of a mine set the depth and the tunnels small for the plot, and set them back.
 */
@GameTestHolder(PlanTests.INTERFACE)
@PrefixGameTestTemplate(false)
public class OwnWorkGameTests {
    private static final String TEMPLATE = "floor_24x24";
    private static final int GROUND = 6;
    private static final int STAND = 7;

    private static ServerPlayer player(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(id, "own_work_" + id.toString().substring(0, 6)));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        return player;
    }

    private static AndroidEntity worker(GameTestHelper helper, ServerPlayer owner, int x, int z) {
        ItemStack core = new ItemStack(GarrisonRegistry.AI_CORE.get());
        AiCoreItem.bind(core, owner);
        ItemStack stack = new ItemStack(BodyItems.item(BodyClass.PREMIUM_SYNTHETIC));
        stack.set(GarrisonRegistry.ANDROID_PARTS.get(), new GarrisonRegistry.AndroidParts(core, new ItemStack(GarrisonRegistry.WORK_MODULE.get())));
        AndroidEntity android = Androids.place(helper.getLevel(), owner, stack, BodyClass.PREMIUM_SYNTHETIC, helper.absolutePos(new BlockPos(x, STAND, z)), 0.0f);
        helper.assertTrue(android != null && android.isWorker(), "A work android stands");
        return android;
    }

    /** An order of its own (no area) with a chest to hand in at (no storage in the test). */
    private static MachineTask ownWork(GameTestHelper helper, String work, BlockPos chest) {
        return new MachineTask(MachineTask.Type.WORK, Optional.empty(), List.of(), Optional.empty(), Optional.empty(),
                Optional.of(helper.absolutePos(chest)), Optional.empty(), work);
    }

    private static Container chest(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at, Blocks.CHEST);
        return (Container) helper.getBlockEntity(at);
    }

    private static int count(Container container, Item item) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).is(item)) {
                total += container.getItem(i).getCount();
            }
        }
        return total;
    }

    /** The config as it was before the batch of quarries (the tests of a batch run at once and share it). */
    private static int[] mineConfig;

    /**
     * A small quarry for the plot: 5 × 5, down to test y 3 (four layers: 6, 5, 4, 3), a torch every second layer (only with torches
     * in its gear). Set for the whole batch and put back after it (a test putting it back would change the quarries of the others).
     */
    private static Runnable smallMine(GameTestHelper helper) {
        if (mineConfig == null) {
            mineConfig = new int[] {RebirthConfig.MINE_DEPTH.get(), RebirthConfig.MINE_SIZE.get(), RebirthConfig.MINE_TORCH_EVERY.get()};
        }
        RebirthConfig.MINE_DEPTH.set(helper.absolutePos(new BlockPos(0, 3, 0)).getY());
        RebirthConfig.MINE_SIZE.set(5);
        RebirthConfig.MINE_TORCH_EVERY.set(2);
        return () -> {
        };
    }

    private static int searchRadius = -1;

    /** The lumberjacks look round their own plot only (left-overs of other tests lie in the plots next to theirs). */
    @net.minecraft.gametest.framework.BeforeBatch(batch = "own_work_trees")
    public static void nearTrees(ServerLevel level) {
        nearOnly();
    }

    @net.minecraft.gametest.framework.BeforeBatch(batch = "own_work_lumber")
    public static void nearLumber(ServerLevel level) {
        nearOnly();
    }

    private static void nearOnly() {
        searchRadius = RebirthConfig.WORK_SEARCH_RADIUS.get();
        RebirthConfig.WORK_SEARCH_RADIUS.set(14);
    }

    @net.minecraft.gametest.framework.AfterBatch(batch = "own_work_trees")
    public static void farTrees(ServerLevel level) {
        farAgain();
    }

    @net.minecraft.gametest.framework.AfterBatch(batch = "own_work_lumber")
    public static void farLumber(ServerLevel level) {
        farAgain();
    }

    private static void farAgain() {
        if (searchRadius > 0) {
            RebirthConfig.WORK_SEARCH_RADIUS.set(searchRadius);
            searchRadius = -1;
        }
    }

    @net.minecraft.gametest.framework.AfterBatch(batch = "own_work")
    public static void putTheMineConfigBack(ServerLevel level) {
        if (mineConfig != null) {
            RebirthConfig.MINE_DEPTH.set(mineConfig[0]);
            RebirthConfig.MINE_SIZE.set(mineConfig[1]);
            RebirthConfig.MINE_TORCH_EVERY.set(mineConfig[2]);
            mineConfig = null;
        }
    }

    /** Its quarry has its corner at that column, its top layer the ground (the real search looks 56 blocks and more out). */
    private static BlockPos mineAt(GameTestHelper helper, AndroidEntity android, int x, int z, int progress) {
        BlockPos corner = helper.absolutePos(new BlockPos(x, GROUND, z));
        WorkSites.get(helper.getLevel().getServer()).set(android.getUUID(),
                new WorkSites.Site("mine", helper.getLevel().dimension(), corner, Direction.EAST, progress));
        return corner;
    }

    /** The stair block of a layer of the 5 × 5 quarry at that corner: along its wall, one block on each layer down. */
    private static BlockPos step(BlockPos corner, int layer) {
        int[][] ring = {{0, 0}, {1, 0}, {2, 0}, {3, 0}, {4, 0}, {4, 1}, {4, 2}, {4, 3}, {4, 4}, {3, 4}, {2, 4}, {1, 4}, {0, 4}, {0, 3}, {0, 2}, {0, 1}};
        int[] at = ring[layer % ring.length];
        return corner.offset(at[0], -layer, at[1]);
    }

    /** What is wrong with a layer of the quarry: a block other than its stair left, or the stair dug away; null when it is right. */
    private static String layerProblem(GameTestHelper helper, BlockPos corner, int layer) {
        BlockPos stair = step(corner, layer);
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                BlockPos pos = corner.offset(x, -layer, z);
                BlockState state = helper.getLevel().getBlockState(pos);
                boolean open = state.isAir() || state.is(Blocks.TORCH) || !state.getFluidState().isEmpty();
                if (pos.equals(stair) ? open : !open) {
                    return (pos.equals(stair) ? "the stair dug away at " : state + " left at ") + pos;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ the place of a mine

    @GameTest(template = TEMPLATE, batch = "own_work")
    public static void aMineGoesOnlyFarFromTheBaseAndOffAnyFactionsLand(GameTestHelper helper) {
        Runnable restore = smallMine(helper);
        ServerLevel level = helper.getLevel();
        BlockPos middle = helper.absolutePos(new BlockPos(8, GROUND, 8));
        BlockPos site = MineShaft.siteAt(level, middle.getX(), middle.getZ(), List.of());
        helper.assertTrue(site != null && site.getY() == middle.getY(), "Open dry ground: a quarry fits, its top the ground: " + site);
        helper.assertTrue(MineShaft.siteAt(level, middle.getX(), middle.getZ(), List.of(middle.offset(10, 0, 0))) == null,
                "Too near a storage, shelter or core: no mine");
        // water on the square of the quarry: no mine
        helper.setBlock(new BlockPos(10, GROUND, 10), Blocks.WATER);
        helper.assertTrue(MineShaft.siteAt(level, middle.getX(), middle.getZ(), List.of()) == null, "Wet ground: no mine");
        restore.run();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "own_work_faction")
    public static void aMineNeverGoesOnFactionLand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FactionService.adminDisband(level.getServer(), "Mine Land");
        ServerPlayer leader = player(helper);
        FactionService.create(leader, "Mine Land", ChatFormatting.DARK_GREEN);
        BlockPos core = helper.absolutePos(new BlockPos(2, STAND, 2));
        helper.setBlock(new BlockPos(2, STAND, 2), FactionBlocks.FACTION_CORE.get());
        FactionService.addCore(leader, level, core);
        BlockPos middle = helper.absolutePos(new BlockPos(8, GROUND, 8));
        helper.assertTrue(MineShaft.siteAt(level, middle.getX(), middle.getZ(), List.of()) == null, "On a faction's land: no mine");
        FactionService.adminDisband(level.getServer(), "Mine Land");
        helper.succeed();
    }

    // ------------------------------------------------------------------ the quarry

    @GameTest(template = TEMPLATE, timeoutTicks = 3600, batch = "own_work")
    public static void aMinerDigsOutItsQuarryLayerByLayerWithAStairAlongTheWall(GameTestHelper helper) {
        Runnable restore = smallMine(helper);
        ServerPlayer owner = player(helper);
        chest(helper, new BlockPos(2, STAND, 2));
        AndroidEntity android = worker(helper, owner, 2, 8);
        android.gear().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        android.gear().setItem(1, new ItemStack(Items.TORCH, 8));
        BlockPos corner = mineAt(helper, android, 4, 4, 0);
        android.machineCore().setTask(ownWork(helper, "mine", new BlockPos(2, STAND, 2)));
        helper.succeedWhen(() -> {
            for (int layer = 0; layer < 4; layer++) {
                String problem = layerProblem(helper, corner, layer);
                helper.assertTrue(problem == null, "Layer " + layer + ": " + problem);
            }
            // a torch on the stair every second layer
            helper.assertTrue(helper.getLevel().getBlockState(step(corner, 1).above()).is(Blocks.TORCH), "A torch over the second step: " + helper.getLevel().getBlockState(step(corner, 1).above()) + " at " + step(corner, 1).above() + ", torches " + android.gear().getItem(1));
            helper.assertTrue(helper.getLevel().getBlockState(step(corner, 3).above()).is(Blocks.TORCH), "A torch over the fourth step");
            helper.assertTrue(helper.getLevel().getBlockState(corner.offset(2, -4, 2)).isSolid(), "Not deeper than mineDepth");
            restore.run();
            WorkSites.get(helper.getLevel().getServer()).set(android.getUUID(), null);
            android.discard();
        });
    }

    /** 1.8.0 (owner): water at the wall and in the quarry: the wall is plugged, the quarry is dug out dry. */
    @GameTest(template = TEMPLATE, timeoutTicks = 3600, batch = "own_work")
    public static void aMinerPlugsWaterAndDigsItsQuarryDry(GameTestHelper helper) {
        Runnable restore = smallMine(helper);
        ServerPlayer owner = player(helper);
        chest(helper, new BlockPos(2, STAND, 2));
        AndroidEntity android = worker(helper, owner, 2, 8);
        android.gear().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        android.gear().setItem(1, new ItemStack(Items.COBBLESTONE, 32));
        BlockPos corner = mineAt(helper, android, 4, 4, 0);
        // a spring in the wall next to the second layer, a pool on the ground by the quarry, a spring inside it
        // (from the corner of the quarry, in the world's axes: the quarry is laid out so, whichever way the plot is turned)
        BlockPos wall = corner.offset(-1, -1, 2), pool = corner.offset(2, 1, 5), inside = corner.offset(2, -2, 2);
        for (BlockPos water : new BlockPos[] {wall, pool, inside}) {
            helper.getLevel().setBlock(water, Blocks.WATER.defaultBlockState(), 3);
        }
        android.machineCore().setTask(ownWork(helper, "mine", new BlockPos(2, STAND, 2)));
        helper.succeedWhen(() -> {
            for (int layer = 0; layer < 4; layer++) {
                for (int x = 0; x < 5; x++) {
                    for (int z = 0; z < 5; z++) {
                        BlockPos pos = corner.offset(x, -layer, z);
                        BlockState state = helper.getLevel().getBlockState(pos);
                        helper.assertTrue(pos.equals(step(corner, layer)) || state.isAir() || state.is(Blocks.TORCH),
                                "Dug out and dry at corner + " + pos.subtract(corner) + ": " + state + ", it is at corner + " + android.blockPosition().subtract(corner) + ", holding " + android.gear().getItem(1) + ", reports " + java.util.Arrays.stream(ConsoleAlarm.values()).filter(android::workReported).toList() + ", site " + WorkSites.get(helper.getLevel().getServer()).of(android.getUUID()));
                    }
                }
            }
            helper.assertTrue(helper.getLevel().getFluidState(wall).isEmpty(), "The spring in the wall plugged");
            restore.run();
            WorkSites.get(helper.getLevel().getServer()).set(android.getUUID(), null);
            android.discard();
        });
    }

    /** 1.8.0 (owner): a miner with a pickaxe and a shovel digs dirt with the shovel and stone with the pickaxe. */
    @GameTest(template = TEMPLATE, timeoutTicks = 3600, batch = "own_work")
    public static void aMinerDigsDirtWithItsShovelAndStoneWithItsPickaxe(GameTestHelper helper) {
        Runnable restore = smallMine(helper);
        ServerPlayer owner = player(helper);
        chest(helper, new BlockPos(2, STAND, 2));
        AndroidEntity android = worker(helper, owner, 2, 8);
        android.gear().setItem(0, new ItemStack(Items.IRON_PICKAXE));
        android.gear().setItem(1, new ItemStack(Items.IRON_SHOVEL));
        BlockPos corner = mineAt(helper, android, 4, 4, 0);
        // the top layer dirt, the rest stone
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.getLevel().setBlock(corner.offset(x, 0, z), Blocks.DIRT.defaultBlockState(), 3);
            }
        }
        android.machineCore().setTask(ownWork(helper, "mine", new BlockPos(2, STAND, 2)));
        boolean[] shovelOnDirt = new boolean[1];
        helper.onEachTick(() -> {
            ItemStack hand = android.gear().getItem(0);
            shovelOnDirt[0] |= hand.is(Items.IRON_SHOVEL) && hand.getDamageValue() > 0;
        });
        helper.succeedWhen(() -> {
            for (int layer = 0; layer < 4; layer++) {
                String problem = layerProblem(helper, corner, layer);
                helper.assertTrue(problem == null, "Layer " + layer + ": " + problem);
            }
            helper.assertTrue(shovelOnDirt[0], "It dug the dirt with its shovel");
            int pickaxeWear = 0;
            for (int i = 0; i < 36; i++) {
                if (android.gear().getItem(i).is(Items.IRON_PICKAXE)) {
                    pickaxeWear = android.gear().getItem(i).getDamageValue();
                }
            }
            helper.assertTrue(pickaxeWear >= 40, "And the stone with its pickaxe: worn " + pickaxeWear);
            restore.run();
            WorkSites.get(helper.getLevel().getServer()).set(android.getUUID(), null);
            android.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 600, batch = "own_work")
    public static void aMinerNeverBuildsWithSandOrGravel(GameTestHelper helper) {
        // whatever the list says, a block that falls is never a filler
        List<? extends String> fillers = RebirthConfig.WORK_FILLER_BLOCKS.get();
        RebirthConfig.WORK_FILLER_BLOCKS.set(List.of("minecraft:sand", "minecraft:gravel", "minecraft:red_concrete_powder", "minecraft:cobblestone"));
        helper.assertFalse(AndroidWork.isFiller(new ItemStack(Items.SAND)), "Sand is no filler");
        helper.assertFalse(AndroidWork.isFiller(new ItemStack(Items.GRAVEL)), "Gravel is no filler");
        helper.assertFalse(AndroidWork.isFiller(new ItemStack(Items.RED_CONCRETE_POWDER)), "Concrete powder is no filler");
        helper.assertTrue(AndroidWork.isFiller(new ItemStack(Items.COBBLESTONE)), "Cobblestone is");
        Runnable restore = smallMine(helper);
        ServerPlayer owner = player(helper);
        chest(helper, new BlockPos(2, STAND, 2));
        AndroidEntity android = worker(helper, owner, 2, 8);
        android.gear().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        android.gear().setItem(1, new ItemStack(Items.SAND, 64));
        android.gear().setItem(2, new ItemStack(Items.GRAVEL, 64));
        BlockPos corner = mineAt(helper, android, 4, 4, 0);
        // water in the quarry: to be plugged, but never with sand or gravel
        helper.setBlock(new BlockPos(6, 4, 6), Blocks.WATER);
        android.machineCore().setTask(ownWork(helper, "mine", new BlockPos(2, STAND, 2)));
        helper.onEachTick(() -> {
            for (BlockPos pos : BlockPos.betweenClosed(corner.offset(-1, -5, -1), corner.offset(5, 2, 5))) {
                BlockState state = helper.getLevel().getBlockState(pos);
                helper.assertFalse(state.is(Blocks.SAND) || state.is(Blocks.GRAVEL), "No sand or gravel placed at " + pos);
            }
        });
        helper.runAfterDelay(500, () -> {
            helper.assertTrue(android.gear().getItem(1).is(Items.SAND) && android.gear().getItem(1).getCount() == 64
                    && android.gear().getItem(2).getCount() == 64, "Its sand and gravel are all still there");
            RebirthConfig.WORK_FILLER_BLOCKS.set(fillers);
            restore.run();
            WorkSites.get(helper.getLevel().getServer()).set(android.getUUID(), null);
            android.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 3600, batch = "own_work")
    public static void aMinerUnderGravelNeitherDiesNorGetsStuck(GameTestHelper helper) {
        Runnable restore = smallMine(helper);
        ServerPlayer owner = player(helper);
        chest(helper, new BlockPos(2, STAND, 2));
        // a column of gravel in the quarry, from its bottom to three blocks over the ground
        for (int y = 3; y <= GROUND + 3; y++) {
            helper.setBlock(new BlockPos(6, y, 6), Blocks.GRAVEL);
        }
        AndroidEntity android = worker(helper, owner, 2, 8);
        android.gear().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        BlockPos corner = mineAt(helper, android, 4, 4, 0);
        android.machineCore().setTask(ownWork(helper, "mine", new BlockPos(2, STAND, 2)));
        float health = android.getHealth();
        helper.onEachTick(() -> helper.assertTrue(android.isAlive() && android.getLastDamageSource() == null
                || android.getLastDamageSource() != null && !android.getLastDamageSource().is(net.minecraft.world.damagesource.DamageTypes.IN_WALL),
                "It is never buried"));
        helper.succeedWhen(() -> {
            for (int layer = 0; layer < 4; layer++) {
                String problem = layerProblem(helper, corner, layer);
                helper.assertTrue(problem == null, "Layer " + layer + ": " + problem);
            }
            helper.assertTrue(android.getHealth() >= health, "Unhurt");
            restore.run();
            WorkSites.get(helper.getLevel().getServer()).set(android.getUUID(), null);
            android.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 2400, batch = "own_work")
    public static void aMinerGoesOnWithItsMineWhereItLeftOff(GameTestHelper helper) {
        Runnable restore = smallMine(helper);
        ServerPlayer owner = player(helper);
        chest(helper, new BlockPos(2, STAND, 2));
        AndroidEntity android = worker(helper, owner, 2, 8);
        android.gear().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        // the first two layers were dug before the restart (all but their stair), the world remembers two done
        BlockPos corner = mineAt(helper, android, 4, 4, 2);
        for (int layer = 0; layer < 2; layer++) {
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    BlockPos pos = corner.offset(x, -layer, z);
                    if (!pos.equals(step(corner, layer))) {
                        helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        // a block it would dig first if it started over
        BlockPos marker = corner.offset(2, 0, 2);
        helper.getLevel().setBlock(marker, Blocks.STONE.defaultBlockState(), 3);
        WorkSites sites = WorkSites.get(helper.getLevel().getServer());
        helper.assertTrue(sites.of(android.getUUID()).progress() == 2, "Saved where it was");
        android.machineCore().setTask(ownWork(helper, "mine", new BlockPos(2, STAND, 2)));
        helper.succeedWhen(() -> {
            for (int layer = 2; layer < 4; layer++) {
                String problem = layerProblem(helper, corner, layer);
                helper.assertTrue(problem == null, "Layer " + layer + ": " + problem);
            }
            helper.assertTrue(helper.getLevel().getBlockState(marker).is(Blocks.STONE), "It went on from the third layer, not from the top");
            helper.assertTrue(sites.of(android.getUUID()) != null && sites.of(android.getUUID()).progress() >= 3,
                    "The world keeps how far it got: " + sites.of(android.getUUID()));
            restore.run();
            sites.set(android.getUUID(), null);
            android.discard();
        });
    }

    // ------------------------------------------------------------------ the lumberjack and the farmer

    private static void tree(GameTestHelper helper, int x, int z) {
        for (int y = STAND; y < STAND + 4; y++) {
            helper.setBlock(new BlockPos(x, y, z), Blocks.OAK_LOG);
        }
        for (BlockPos leaf : BlockPos.betweenClosed(new BlockPos(x - 1, STAND + 3, z - 1), new BlockPos(x + 1, STAND + 4, z + 1))) {
            if (helper.getLevel().getBlockState(helper.absolutePos(leaf)).isAir()) {
                helper.setBlock(leaf, Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, false));
            }
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1600, batch = "own_work_trees")
    public static void aLumberjackFindsATreeItself(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        Container chest = chest(helper, new BlockPos(4, STAND, 4));
        helper.setBlock(new BlockPos(16, GROUND, 16), Blocks.GRASS_BLOCK);
        tree(helper, 16, 16);
        AndroidEntity android = worker(helper, owner, 5, 5);
        android.gear().setItem(0, new ItemStack(Items.DIAMOND_AXE));
        android.machineCore().setTask(ownWork(helper, "lumber", new BlockPos(4, STAND, 4)));
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(16, STAND + 1, 16))).isAir(), "The tree is felled; it is at " + helper.relativePos(android.blockPosition()) + " with " + android.gear().getItem(0) + ", " + android.gear().getItem(1) + ", work " + android.machineCore().task().work() + ", going to " + (android.getNavigation().getTargetPos() == null ? null : helper.relativePos(android.getNavigation().getTargetPos())) + ", there " + (android.getNavigation().getTargetPos() == null ? null : helper.getLevel().getBlockState(android.getNavigation().getTargetPos()) + "/" + helper.getLevel().getBlockState(android.getNavigation().getTargetPos().below())) + ", raised " + java.util.Arrays.stream(ConsoleAlarm.values()).filter(android::workReported).toList());
            android.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1600, batch = "own_work_lumber")
    public static void aLumberjackTakesAnAxeAndSaplingsFromItsChestAndPlantsAgain(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        Container chest = chest(helper, new BlockPos(4, STAND, 4));
        chest.setItem(0, new ItemStack(Items.IRON_AXE));
        chest.setItem(1, new ItemStack(Items.OAK_SAPLING, 4));
        helper.setBlock(new BlockPos(16, GROUND, 16), Blocks.GRASS_BLOCK);
        tree(helper, 16, 16);
        AndroidEntity android = worker(helper, owner, 5, 5);
        android.machineCore().setTask(ownWork(helper, "lumber", new BlockPos(4, STAND, 4)));
        helper.succeedWhen(() -> {
            helper.assertTrue(android.gear().getItem(0).is(Items.IRON_AXE), "It took the axe from its chest");
            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(16, STAND + 1, 16))).isAir()
                    && helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(16, STAND + 3, 16))).isAir(), "The whole tree is felled");
            for (BlockPos leaf : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(15, STAND + 3, 15)), helper.absolutePos(new BlockPos(17, STAND + 4, 17)))) {
                helper.assertFalse(helper.getLevel().getBlockState(leaf).is(Blocks.OAK_LEAVES), "Its leaves cut too");
            }
            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(16, STAND, 16))).is(Blocks.OAK_SAPLING),
                    "A sapling where it stood");
            // nothing of it left for the lumberjacks of the next batches (their plots come next to this one)
            for (BlockPos left : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(14, STAND + 1, 14)), helper.absolutePos(new BlockPos(18, STAND + 6, 18)))) {
                helper.getLevel().setBlock(left, Blocks.AIR.defaultBlockState(), 2);
            }
            android.discard();
        });
    }

    private static void ripeWheat(GameTestHelper helper, int x, int z) {
        helper.setBlock(new BlockPos(x, GROUND, z), Blocks.FARMLAND);
        helper.setBlock(new BlockPos(x, STAND, z), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
    }

    private static boolean ripe(GameTestHelper helper, int x, int z) {
        BlockState state = helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x, STAND, z)));
        return state.is(Blocks.WHEAT) && state.getValue(CropBlock.AGE) == 7;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1600, batch = "own_work")
    public static void aFarmerFindsItsFieldItselfAndLeavesAZoneAlone(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        // its console with a zone «do not touch» over one of the two fields
        BlockPos consolePos = helper.absolutePos(new BlockPos(1, STAND, 20));
        helper.getLevel().setBlockAndUpdate(consolePos, GarrisonRegistry.CONSOLE.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        ConsoleBlockEntity console = (ConsoleBlockEntity) helper.getLevel().getBlockEntity(consolePos);
        console.setOwner(owner.getUUID());
        console.energy().load(console.energy().getMaxEnergyStored());
        console.serverTick();
        BlockPos zoneCorner = helper.absolutePos(new BlockPos(14, 0, 14));
        helper.assertTrue(console.addZone(new ConsoleView.Zone(zoneCorner.getX(), zoneCorner.getZ(), zoneCorner.getX() + 3, zoneCorner.getZ() + 3)), "A zone");
        chest(helper, new BlockPos(4, STAND, 4));
        ripeWheat(helper, 8, 8);
        ripeWheat(helper, 15, 15);
        AndroidEntity android = worker(helper, owner, 5, 5);
        helper.assertTrue(console.knows(android.getUUID()), "Linked to its console");
        console.order(android.getUUID(), ownWork(helper, "farm", new BlockPos(4, STAND, 4)));
        helper.onEachTick(() -> helper.assertTrue(ripe(helper, 15, 15), "The field in the zone is left alone"));
        helper.succeedWhen(() -> {
            helper.assertFalse(ripe(helper, 8, 8), "Its own field is harvested");
            android.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1200, batch = "own_work_faction")
    public static void aFarmerLeavesTheFieldsOfAnotherFactionAlone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FactionService.adminDisband(level.getServer(), "Field Land");
        // a small territory, only round the far field (the default 24 blocks would cover the whole plot)
        int radius = RebirthConfig.FACTION_CORE_RADIUS.get();
        RebirthConfig.FACTION_CORE_RADIUS.set(5);
        ServerPlayer stranger = player(helper);
        FactionService.create(stranger, "Field Land", ChatFormatting.YELLOW);
        helper.setBlock(new BlockPos(20, STAND, 20), FactionBlocks.FACTION_CORE.get());
        FactionService.addCore(stranger, level, helper.absolutePos(new BlockPos(20, STAND, 20)));
        ServerPlayer owner = player(helper);
        chest(helper, new BlockPos(4, STAND, 4));
        ripeWheat(helper, 18, 18);
        ripeWheat(helper, 8, 8);
        AndroidEntity android = worker(helper, owner, 5, 5);
        android.machineCore().setTask(ownWork(helper, "farm", new BlockPos(4, STAND, 4)));
        helper.onEachTick(() -> helper.assertTrue(ripe(helper, 18, 18), "The field on their land is left alone"));
        helper.runAfterDelay(600, () -> {
            helper.assertFalse(ripe(helper, 8, 8), "Its own field it harvested");
            RebirthConfig.FACTION_CORE_RADIUS.set(radius);
            FactionService.adminDisband(level.getServer(), "Field Land");
            android.discard();
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ the storage

    /** Builds the storage with its controller at (1, 7, 1), facing south; returns it checked. */
    static StorageControllerBlockEntity storage(GameTestHelper helper, ServerPlayer owner) {
        ServerLevel level = helper.getLevel();
        MultiblockSchema schema = MultiblockRegistry.schema(level.registryAccess(), LogisticsRegistry.SCHEMA);
        helper.assertTrue(schema != null, "The plan of the storage is loaded");
        Direction facing = Direction.SOUTH;
        int[] c = schema.controllerCell();
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (int y = 0; y < schema.height(); y++) {
            for (int r = 0; r < schema.depth(); r++) {
                for (int x = 0; x < schema.width(); x++) {
                    BlockPos at = schema.worldPos(BlockPos.ZERO, facing, x, y, r);
                    minX = Math.min(minX, at.getX());
                    minZ = Math.min(minZ, at.getZ());
                }
            }
        }
        BlockPos controller = helper.absolutePos(new BlockPos(1 - minX, STAND + c[1], 1 - minZ));
        for (int y = 0; y < schema.height(); y++) {
            for (int r = 0; r < schema.depth(); r++) {
                for (int x = 0; x < schema.width(); x++) {
                    MultiblockSchema.Cell cell = schema.cell(y, r, x);
                    if (cell == null || cell.zone()) {
                        continue;
                    }
                    BlockState state = cell.controller()
                            ? LogisticsRegistry.STORAGE_CONTROLLER.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing)
                            : cell.display();
                    level.setBlock(schema.worldPos(controller, facing, x, y, r), state, 3);
                }
            }
        }
        StorageControllerBlockEntity storage = (StorageControllerBlockEntity) level.getBlockEntity(controller);
        storage.setOwner(owner.getUUID());
        MultiblockSchema.Check check = storage.revalidate();
        helper.assertTrue(check != null && check.complete(), "The storage is whole: " + (check == null ? "no plan" : check.problems()));
        return storage;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1200, batch = "own_work_storage")
    public static void aMinerHandsInAtTheStorageAndTakesAPickaxeFromIt(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        StorageControllerBlockEntity storage = storage(helper, owner);
        storage.store().insertItem(0, new ItemStack(Items.IRON_PICKAXE), false);
        helper.assertTrue(storage.portItems() != null && storage.whole(), "Its store is open");
        AndroidEntity android = worker(helper, owner, 10, 10);
        // no pickaxe, a load of raw iron to hand in
        android.gear().setItem(3, new ItemStack(Items.RAW_IRON, 64));
        android.machineCore().setTask(MachineTask.workOwn("mine"));
        helper.succeedWhen(() -> {
            helper.assertTrue(android.gear().getItem(0).is(Items.IRON_PICKAXE), "It took the pickaxe from the storage");
            int dirt = 0;
            for (int i = 0; i < storage.store().getSlots(); i++) {
                if (storage.store().getStackInSlot(i).is(Items.RAW_IRON)) {
                    dirt += storage.store().getStackInSlot(i).getCount();
                }
            }
            helper.assertTrue(dirt == 64, "It handed in its load: " + dirt);
            helper.assertFalse(android.workReported(ConsoleAlarm.NO_STORAGE), "It found the storage");
            WorkSites.get(helper.getLevel().getServer()).set(android.getUUID(), null);
            android.discard();
        });
    }

    @GameTest(template = TEMPLATE, batch = "own_work_storage")
    public static void aStorageWithAPieceMissingKeepsItsStoreShut(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        StorageControllerBlockEntity storage = storage(helper, owner);
        helper.assertTrue(storage.slots() >= 256, "256 slots: " + storage.slots());
        BlockPos rack = null;
        for (BlockPos pos : BlockPos.betweenClosed(storage.getBlockPos().offset(-4, 0, -4), storage.getBlockPos().offset(4, 2, 4))) {
            if (helper.getLevel().getBlockState(pos).is(LogisticsRegistry.STORAGE_RACK.get())) {
                rack = pos.immutable();
                break;
            }
        }
        helper.assertTrue(rack != null, "A rack");
        helper.getLevel().setBlockAndUpdate(rack, Blocks.AIR.defaultBlockState());
        storage.revalidate();
        helper.assertFalse(storage.whole() || storage.portItems() != null, "A rack gone: the store is shut");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "own_work")
    public static void withoutAStorageOrChestTheWorkerReportsIt(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        AndroidEntity android = worker(helper, owner, 10, 10);
        android.gear().setItem(0, new ItemStack(Items.DIAMOND_AXE));
        android.machineCore().setTask(MachineTask.workOwn("lumber"));
        helper.succeedWhen(() -> {
            helper.assertTrue(android.workReported(ConsoleAlarm.NO_STORAGE), "«No storage»");
            android.discard();
        });
    }
}
