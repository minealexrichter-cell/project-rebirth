package com.projectrebirth.gametest;

import java.util.UUID;

import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyItems;
import com.projectrebirth.garrison.AiCoreItem;
import com.projectrebirth.garrison.AndroidEntity;
import com.projectrebirth.garrison.Androids;
import com.projectrebirth.garrison.ConsoleAlarm;
import com.projectrebirth.garrison.GarrisonRegistry;
import com.projectrebirth.garrison.MachineTask;
import com.projectrebirth.shelter.ShelterRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The work android (1.7.0, block F): the miner takes its area layer by layer and leaves a stair, keeps only ores when told,
 * plugs water, leaves containers and shelter blocks, fetches a pickaxe from its chest or reports it has none; the lumberjack fells
 * a whole tree, plants a sapling and leaves the logs of a building; the farmer takes only what is ripe and replants; all hand in
 * at the chest; attacked, a worker does not fight; the order and its state are saved.
 */
@GameTestHolder(PlanTests.GARRISON)
@PrefixGameTestTemplate(false)
public class AndroidWorkGameTests {
    private static final String TEMPLATE = "floor_9x9";

    private static ServerPlayer player(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(id, "work_test_" + id.toString().substring(0, 6)));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.setShiftKeyDown(false);
        return player;
    }

    /** A premium work android of the owner (the fastest worker), standing at that place. */
    private static AndroidEntity worker(GameTestHelper helper, BlockPos at) {
        ServerPlayer owner = player(helper);
        ItemStack core = new ItemStack(GarrisonRegistry.AI_CORE.get());
        AiCoreItem.bind(core, owner);
        ItemStack stack = new ItemStack(BodyItems.item(BodyClass.PREMIUM_SYNTHETIC));
        stack.set(GarrisonRegistry.ANDROID_PARTS.get(), new GarrisonRegistry.AndroidParts(core, new ItemStack(GarrisonRegistry.WORK_MODULE.get())));
        AndroidEntity android = Androids.place(helper.getLevel(), owner, stack, BodyClass.PREMIUM_SYNTHETIC, helper.absolutePos(at), 0.0f);
        helper.assertTrue(android != null && android.isWorker(), "A work android stands");
        return android;
    }

    private static void order(GameTestHelper helper, AndroidEntity android, String work, BlockPos from, BlockPos to, BlockPos chest) {
        android.machineCore().setTask(MachineTask.work(work, helper.absolutePos(from), helper.absolutePos(to), helper.absolutePos(chest)));
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

    /** Stone from (2, 2, 2) to (5, 3, 5) with an iron ore, a barrel and a shelter hatch in it. */
    private static void quarry(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(2, 2, 2), new BlockPos(5, 3, 5))) {
            helper.setBlock(pos, Blocks.STONE);
        }
        helper.setBlock(new BlockPos(4, 3, 4), Blocks.IRON_ORE);
        helper.setBlock(new BlockPos(5, 3, 3), Blocks.BARREL);
        helper.setBlock(new BlockPos(5, 2, 3), ShelterRegistry.SEALED_HATCH.get());
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1600)
    public static void theMinerTakesItsAreaLayerByLayerAndLeavesAStair(GameTestHelper helper) {
        quarry(helper);
        Container chest = chest(helper, new BlockPos(7, 2, 7));
        AndroidEntity android = worker(helper, new BlockPos(1, 2, 1));
        android.gear().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        order(helper, android, "mine", new BlockPos(2, 2, 2), new BlockPos(5, 3, 5), new BlockPos(7, 2, 7));
        // the stair of a 4 by 4 mine: the first cell of the wall on the top layer, the second one a layer lower
        helper.assertTrue(android.isMineStep(helper.absolutePos(new BlockPos(2, 3, 2))) && android.isMineStep(helper.absolutePos(new BlockPos(3, 2, 2)))
                && !android.isMineStep(helper.absolutePos(new BlockPos(3, 3, 2))) && !android.isMineStep(helper.absolutePos(new BlockPos(3, 3, 3))),
                "A spiral stair along the wall");
        helper.succeedWhen(() -> {
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(2, 2, 2), new BlockPos(5, 3, 5))) {
                BlockState state = helper.getBlockState(pos);
                boolean step = pos.equals(new BlockPos(2, 3, 2)) || pos.equals(new BlockPos(3, 2, 2));
                boolean kept = pos.equals(new BlockPos(5, 3, 3)) || pos.equals(new BlockPos(5, 2, 3));
                helper.assertTrue(step || kept ? !state.isAir() : state.isAir(), "Mined, but the stair, the barrel and the hatch stay: " + pos + " " + state);
            }
            helper.assertBlockPresent(Blocks.BARREL, new BlockPos(5, 3, 3));
            helper.assertBlockPresent(ShelterRegistry.SEALED_HATCH.get(), new BlockPos(5, 2, 3));
            helper.assertTrue(count(chest, Items.RAW_IRON) >= 1 && count(chest, Items.COBBLESTONE) > 0,
                    "The ore and the stone are in the chest: " + count(chest, Items.RAW_IRON) + " / " + count(chest, Items.COBBLESTONE));
            helper.assertTrue(android.workReported(ConsoleAlarm.WORK_DONE), "It reports the area done");
            helper.assertTrue(android.gear().getItem(0).is(Items.DIAMOND_PICKAXE) && android.gear().getItem(0).getDamageValue() > 0, "Its pickaxe wears");
            android.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1600)
    public static void oresOnlyMinesEverythingButKeepsOnlyOres(GameTestHelper helper) {
        quarry(helper);
        Container chest = chest(helper, new BlockPos(7, 2, 7));
        AndroidEntity android = worker(helper, new BlockPos(1, 2, 1));
        android.gear().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        order(helper, android, "mine_ore", new BlockPos(2, 2, 2), new BlockPos(5, 3, 5), new BlockPos(7, 2, 7));
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockState(new BlockPos(4, 3, 4)).isAir() && helper.getBlockState(new BlockPos(3, 3, 3)).isAir(),
                    "The whole area is mined");
            helper.assertTrue(count(chest, Items.RAW_IRON) >= 1, "The ore is in the chest");
            int stone = count(chest, Items.COBBLESTONE);
            for (int i = 0; i < AndroidEntity.GEAR; i++) {
                stone += android.gear().getItem(i).is(Items.COBBLESTONE) ? 1 : 0;
            }
            helper.assertTrue(stone == 0, "No stone kept");
            android.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void theMinerPlugsWaterWithStoneFromItsGear(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 0, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.WATER);
        chest(helper, new BlockPos(7, 2, 7));
        AndroidEntity android = worker(helper, new BlockPos(1, 2, 3));
        android.gear().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        android.gear().setItem(3, new ItemStack(Items.COBBLESTONE, 4));
        order(helper, android, "mine", new BlockPos(3, 1, 3), new BlockPos(3, 1, 3), new BlockPos(7, 2, 7));
        helper.succeedWhen(() -> {
            // 1.8.0: the water filled with its cobblestone, then dug out with the rest of the area: dry
            helper.assertTrue(helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(3, 1, 3))).isEmpty()
                    && !helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(3, 1, 3))).is(Blocks.WATER), "The water is gone");
            helper.assertTrue(android.workReported(ConsoleAlarm.WORK_DONE), "The area is done");
            android.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void aMinerWithoutAPickaxeTakesOneFromTheChest(GameTestHelper helper) {
        quarry(helper);
        Container chest = chest(helper, new BlockPos(1, 2, 7));
        chest.setItem(4, new ItemStack(Items.IRON_PICKAXE));
        AndroidEntity android = worker(helper, new BlockPos(1, 2, 5));
        order(helper, android, "mine", new BlockPos(2, 2, 2), new BlockPos(5, 3, 5), new BlockPos(1, 2, 7));
        helper.succeedWhen(() -> {
            helper.assertTrue(android.gear().getItem(0).is(Items.IRON_PICKAXE) && count(chest, Items.IRON_PICKAXE) == 0, "It took the pickaxe");
            helper.assertFalse(android.workReported(ConsoleAlarm.NO_TOOL), "And has a tool");
            android.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void aMinerWithNoPickaxeAnywhereReportsIt(GameTestHelper helper) {
        quarry(helper);
        chest(helper, new BlockPos(1, 2, 7));
        AndroidEntity android = worker(helper, new BlockPos(1, 2, 5));
        order(helper, android, "mine", new BlockPos(2, 2, 2), new BlockPos(5, 3, 5), new BlockPos(1, 2, 7));
        helper.succeedWhen(() -> {
            helper.assertTrue(android.workReported(ConsoleAlarm.NO_TOOL), "No tool is reported");
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(4, 3, 3));
            android.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1600)
    public static void theLumberjackFellsATreeAndPlantsASaplingButLeavesABuilding(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.DIRT);
        for (int y = 2; y <= 5; y++) {
            helper.setBlock(new BlockPos(3, y, 3), Blocks.OAK_LOG);
        }
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(2, 4, 2), new BlockPos(4, 6, 4))) {
            if (helper.getBlockState(pos).isAir()) {
                helper.setBlock(pos, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1));
            }
        }
        // a building: logs with leaves set by hand (persistent)
        helper.setBlock(new BlockPos(7, 2, 5), Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(7, 3, 5), Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(7, 4, 5), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        Container chest = chest(helper, new BlockPos(7, 2, 1));
        AndroidEntity android = worker(helper, new BlockPos(1, 2, 1));
        android.gear().setItem(0, new ItemStack(Items.IRON_AXE));
        android.gear().setItem(5, new ItemStack(Items.OAK_SAPLING));
        order(helper, android, "lumber", new BlockPos(1, 2, 1), new BlockPos(7, 2, 7), new BlockPos(7, 2, 1));
        helper.succeedWhen(() -> {
            for (int y = 2; y <= 5; y++) {
                helper.assertFalse(helper.getBlockState(new BlockPos(3, y, 3)).is(Blocks.OAK_LOG), "The trunk is felled");
            }
            helper.assertBlockPresent(Blocks.OAK_SAPLING, new BlockPos(3, 2, 3));
            helper.assertTrue(helper.getBlockState(new BlockPos(3, 6, 3)).isAir(), "Its leaves are cut");
            helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(7, 2, 5));
            helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(7, 3, 5));
            helper.assertTrue(count(chest, Items.OAK_LOG) == 4, "The four logs are in the chest: " + count(chest, Items.OAK_LOG));
            android.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public static void theFarmerTakesOnlyWhatIsRipeAndReplants(GameTestHelper helper) {
        for (int x = 2; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, 1, 2), Blocks.FARMLAND);
        }
        helper.setBlock(new BlockPos(2, 2, 2), ((CropBlock) Blocks.WHEAT).getStateForAge(7));
        helper.setBlock(new BlockPos(3, 2, 2), ((CropBlock) Blocks.WHEAT).getStateForAge(3));
        helper.setBlock(new BlockPos(4, 2, 2), ((CropBlock) Blocks.CARROTS).getStateForAge(7));
        helper.setBlock(new BlockPos(6, 1, 2), Blocks.GRASS_BLOCK);
        helper.setBlock(new BlockPos(6, 2, 2), Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3));
        // a pumpkin set by hand, not grown from a stem
        helper.setBlock(new BlockPos(6, 2, 6), Blocks.PUMPKIN);
        Container chest = chest(helper, new BlockPos(7, 2, 7));
        AndroidEntity android = worker(helper, new BlockPos(1, 2, 4));
        order(helper, android, "farm", new BlockPos(1, 2, 1), new BlockPos(7, 2, 7), new BlockPos(7, 2, 7));
        helper.succeedWhen(() -> {
            BlockState wheat = helper.getBlockState(new BlockPos(2, 2, 2));
            helper.assertTrue(wheat.is(Blocks.WHEAT) && wheat.getValue(CropBlock.AGE) == 0, "Ripe wheat harvested and replanted: " + wheat);
            BlockState young = helper.getBlockState(new BlockPos(3, 2, 2));
            helper.assertTrue(young.is(Blocks.WHEAT) && young.getValue(CropBlock.AGE) >= 3, "Young wheat left");
            BlockState carrots = helper.getBlockState(new BlockPos(4, 2, 2));
            helper.assertTrue(carrots.is(Blocks.CARROTS) && carrots.getValue(CropBlock.AGE) == 0, "Carrots harvested and replanted");
            helper.assertTrue(helper.getBlockState(new BlockPos(6, 2, 2)).getValue(SweetBerryBushBlock.AGE) == 1, "Berries picked");
            helper.assertBlockPresent(Blocks.PUMPKIN, new BlockPos(6, 2, 6));
            for (int x = 2; x <= 4; x++) {
                helper.assertBlockPresent(Blocks.FARMLAND, new BlockPos(x, 1, 2));
            }
            helper.assertTrue(count(chest, Items.WHEAT) == 1 && count(chest, Items.SWEET_BERRIES) > 0,
                    "The harvest is in the chest: " + count(chest, Items.WHEAT) + " / " + count(chest, Items.SWEET_BERRIES));
            android.discard();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void anAttackedWorkerDoesNotFightAndItsOrderIsSaved(GameTestHelper helper) {
        AndroidEntity android = worker(helper, new BlockPos(2, 2, 2));
        android.gear().setItem(0, new ItemStack(Items.IRON_SWORD));
        chest(helper, new BlockPos(7, 2, 7));
        order(helper, android, "lumber", new BlockPos(1, 2, 1), new BlockPos(7, 2, 7), new BlockPos(7, 2, 7));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 2));
        android.hurt(helper.getLevel().damageSources().mobAttack(zombie), 2.0f);
        helper.assertTrue(android.getTarget() == null && android.machineCore().fighting(), "It does not fight back; the console hears of the fight");
        helper.assertTrue(android.getName().getString().contains("Work") || android.getName().getString().contains("рабоч"),
                "Named a work android: " + android.getName().getString());

        CompoundTag tag = new CompoundTag();
        android.saveWithoutId(tag);
        AndroidEntity again = Androids.type(BodyClass.PREMIUM_SYNTHETIC).create(helper.getLevel());
        again.load(tag);
        helper.assertTrue(again.isWorker() && again.machineCore().task().equals(android.machineCore().task())
                        && tag.getCompound("Work").contains("Task"), "The work module and the order are saved");
        again.discard();
        zombie.discard();
        android.discard();
        helper.succeed();
    }
}
