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

    /** A small mine for the plot: down to test y 3 (four steps), a main tunnel of 3, no side tunnels, no torches. */
    private static Runnable smallMine(GameTestHelper helper) {
        int depth = RebirthConfig.MINE_DEPTH.get(), corridor = RebirthConfig.MINE_CORRIDOR.get(), branch = RebirthConfig.MINE_BRANCH.get(),
                torch = RebirthConfig.MINE_TORCH_EVERY.get();
        RebirthConfig.MINE_DEPTH.set(helper.absolutePos(new BlockPos(0, 3, 0)).getY());
        RebirthConfig.MINE_CORRIDOR.set(3);
        RebirthConfig.MINE_BRANCH.set(0);
        RebirthConfig.MINE_TORCH_EVERY.set(0);
        return () -> {
            RebirthConfig.MINE_DEPTH.set(depth);
            RebirthConfig.MINE_CORRIDOR.set(corridor);
            RebirthConfig.MINE_BRANCH.set(branch);
            RebirthConfig.MINE_TORCH_EVERY.set(torch);
        };
    }

    /** Its mine starts at that column (the test chooses the place: the real search looks 56 blocks and more out). */
    private static BlockPos mineAt(GameTestHelper helper, AndroidEntity android, int x, int z, int progress) {
        BlockPos origin = helper.absolutePos(new BlockPos(x, STAND, z));
        WorkSites.get(helper.getLevel().getServer()).set(android.getUUID(),
                new WorkSites.Site("mine", helper.getLevel().dimension(), origin, Direction.EAST, progress));
        return origin;
    }

    // ------------------------------------------------------------------ the place of a mine

    @GameTest(template = TEMPLATE, batch = "own_work")
    public static void aMineGoesOnlyFarFromTheBaseAndOffAnyFactionsLand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos middle = helper.absolutePos(new BlockPos(8, GROUND, 8));
        helper.assertTrue(MineShaft.siteAt(level, middle.getX(), middle.getZ(), List.of()) != null, "Open dry ground: a mine fits");
        helper.assertTrue(MineShaft.siteAt(level, middle.getX(), middle.getZ(), List.of(middle.offset(10, 0, 0))) == null,
                "Too near a storage, shelter or core: no mine");
        // water on the square of the stair: no mine
        helper.setBlock(new BlockPos(11, GROUND, 11), Blocks.WATER);
        helper.assertTrue(MineShaft.siteAt(level, middle.getX(), middle.getZ(), List.of()) == null, "Wet ground: no mine: "
                + MineShaft.siteAt(level, middle.getX(), middle.getZ(), List.of()) + " water at " + helper.absolutePos(new BlockPos(11, GROUND, 11)));
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

    // ------------------------------------------------------------------ the mine

    @GameTest(template = TEMPLATE, timeoutTicks = 2400, batch = "own_work")
    public static void aMinerDigsItsStairDownToTheDepthAndItsTunnel(GameTestHelper helper) {
        Runnable restore = smallMine(helper);
        ServerPlayer owner = player(helper);
        chest(helper, new BlockPos(2, STAND, 2));
        AndroidEntity android = worker(helper, owner, 3, 8);
        android.gear().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        android.gear().setItem(1, new ItemStack(Items.COBBLESTONE, 32));
        BlockPos origin = mineAt(helper, android, 4, 8, 0);
        android.machineCore().setTask(ownWork(helper, "mine", new BlockPos(2, STAND, 2)));
        int depth = RebirthConfig.MINE_DEPTH.get();
        helper.succeedWhen(() -> {
            // the stair: four steps east, each a block lower; then the tunnel three blocks on
            for (int i = 1; i <= 4; i++) {
                BlockPos feet = origin.offset(i, -i, 0);
                helper.assertTrue(helper.getLevel().getBlockState(feet).isAir() && helper.getLevel().getBlockState(feet.above()).isAir(),
                        "Step " + i + " dug at " + feet);
            }
            for (int j = 1; j <= 3; j++) {
                BlockPos feet = origin.offset(4 + j, -4, 0);
                helper.assertTrue(helper.getLevel().getBlockState(feet).isAir(), "Tunnel " + j + " dug at " + feet);
            }
            helper.assertTrue(origin.getY() - 4 == depth, "Down to the depth");
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
        AndroidEntity android = worker(helper, owner, 3, 8);
        android.gear().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        android.gear().setItem(1, new ItemStack(Items.SAND, 64));
        android.gear().setItem(2, new ItemStack(Items.GRAVEL, 64));
        BlockPos origin = mineAt(helper, android, 4, 8, 0);
        // a hollow under the second step: its floor must be filled
        helper.setBlock(new BlockPos(6, 4, 8), Blocks.AIR);
        helper.setBlock(new BlockPos(6, 3, 8), Blocks.AIR);
        android.machineCore().setTask(ownWork(helper, "mine", new BlockPos(2, STAND, 2)));
        helper.onEachTick(() -> {
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(0, -6, -1), origin.offset(8, 2, 1))) {
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

    @GameTest(template = TEMPLATE, timeoutTicks = 1600, batch = "own_work")
    public static void aMinerUnderGravelNeitherDiesNorGetsStuck(GameTestHelper helper) {
        Runnable restore = smallMine(helper);
        ServerPlayer owner = player(helper);
        chest(helper, new BlockPos(2, STAND, 2));
        // gravel over the first steps of the stair, in the ground and above it
        for (int x = 4; x <= 8; x++) {
            for (int y = 4; y <= GROUND + 3; y++) {
                if (y > GROUND && x < 5) {
                    continue;
                }
                helper.setBlock(new BlockPos(x, y, 8), Blocks.GRAVEL);
            }
        }
        AndroidEntity android = worker(helper, owner, 3, 8);
        android.gear().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        android.gear().setItem(1, new ItemStack(Items.COBBLESTONE, 64));
        BlockPos origin = mineAt(helper, android, 4, 8, 0);
        android.machineCore().setTask(ownWork(helper, "mine", new BlockPos(2, STAND, 2)));
        float health = android.getHealth();
        helper.onEachTick(() -> helper.assertTrue(android.isAlive() && android.getLastDamageSource() == null
                || android.getLastDamageSource() != null && !android.getLastDamageSource().is(net.minecraft.world.damagesource.DamageTypes.IN_WALL),
                "It is never buried"));
        helper.succeedWhen(() -> {
            BlockPos fourth = origin.offset(4, -4, 0);
            helper.assertTrue(helper.getLevel().getBlockState(fourth).isAir() && helper.getLevel().getBlockState(fourth.above()).isAir(),
                    "It got through the gravel down to the fourth step");
            helper.assertTrue(android.getHealth() >= health, "Unhurt");
            restore.run();
            WorkSites.get(helper.getLevel().getServer()).set(android.getUUID(), null);
            android.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1600, batch = "own_work")
    public static void aMinerGoesOnWithItsMineWhereItLeftOff(GameTestHelper helper) {
        Runnable restore = smallMine(helper);
        ServerPlayer owner = player(helper);
        chest(helper, new BlockPos(2, STAND, 2));
        AndroidEntity android = worker(helper, owner, 3, 8);
        android.gear().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        android.gear().setItem(1, new ItemStack(Items.COBBLESTONE, 32));
        // the first two steps were dug before the restart, the mine remembers two done
        BlockPos origin = mineAt(helper, android, 4, 8, 3);
        for (int i = 0; i <= 2; i++) {
            BlockPos feet = origin.offset(i, -i, 0);
            for (int h = 0; h < 3; h++) {
                helper.getLevel().setBlock(feet.above(h), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        // what the world keeps of it comes back the same
        WorkSites sites = WorkSites.get(helper.getLevel().getServer());
        helper.assertTrue(sites.of(android.getUUID()).progress() == 3, "Saved where it was");
        android.machineCore().setTask(ownWork(helper, "mine", new BlockPos(2, STAND, 2)));
        int[] lowest = {Integer.MAX_VALUE};
        helper.onEachTick(() -> {
            WorkSites.Site site = sites.of(android.getUUID());
            if (site != null) {
                lowest[0] = Math.min(lowest[0], site.progress());
            }
        });
        helper.succeedWhen(() -> {
            BlockPos third = origin.offset(3, -3, 0);
            helper.assertTrue(helper.getLevel().getBlockState(third).isAir(), "The third step dug");
            helper.assertTrue(lowest[0] >= 3, "It never started over: " + lowest[0]);
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

    @GameTest(template = TEMPLATE, timeoutTicks = 1600, batch = "own_work")
    public static void aLumberjackFindsATreeItself(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        Container chest = chest(helper, new BlockPos(4, STAND, 4));
        helper.setBlock(new BlockPos(16, GROUND, 16), Blocks.GRASS_BLOCK);
        tree(helper, 16, 16);
        AndroidEntity android = worker(helper, owner, 5, 5);
        android.gear().setItem(0, new ItemStack(Items.DIAMOND_AXE));
        android.machineCore().setTask(ownWork(helper, "lumber", new BlockPos(4, STAND, 4)));
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(16, STAND + 1, 16))).isAir(), "The tree is felled");
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
