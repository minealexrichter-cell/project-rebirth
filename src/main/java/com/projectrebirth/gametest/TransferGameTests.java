package com.projectrebirth.gametest;

import java.util.List;
import java.util.UUID;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.BodyBlocks;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyEntities;
import com.projectrebirth.bodies.HumanShellEntity;
import com.projectrebirth.bodies.ShellRegistry;
import com.projectrebirth.bodies.TransferCapsuleBlock;
import com.projectrebirth.bodies.TransferService;
import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Server-side checks for consciousness transfer. Run with {@code .\gradlew runGameTestServer}.
 * Each test: a capsule at (0, 2, 0), the player's improved body at (2, 2, 2); the player carries 5 diamonds
 * (human inventory) and the body carries 1 stick (body inventory).
 */
@GameTestHolder(ProjectRebirth.MOD_ID)
@PrefixGameTestTemplate(false)
public class TransferGameTests {
    private static final String TEMPLATE = "floor_3x3";
    // Run apart from other tests: the nearest-body search must not find unowned bodies of neighbouring test areas.
    private static final String BATCH = "transfer";
    private static final BlockPos CAPSULE = new BlockPos(0, 2, 0);
    private static final BlockPos BODY_POS = new BlockPos(2, 2, 2);
    private static final BodyClass BODY_CLASS = BodyClass.IMPROVED_ROBOT;

    private record Setup(ServerPlayer player, ArtificialBodyEntity body, BlockPos capsule) {
    }

    private static Setup setUp(GameTestHelper helper) {
        BlockState lower = BodyBlocks.TRANSFER_CAPSULE.get().defaultBlockState()
                .setValue(TransferCapsuleBlock.FACING, Direction.NORTH).setValue(TransferCapsuleBlock.HALF, DoubleBlockHalf.LOWER);
        helper.setBlock(CAPSULE, lower);
        helper.setBlock(CAPSULE.above(), lower.setValue(TransferCapsuleBlock.HALF, DoubleBlockHalf.UPPER));

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos standAt = helper.absolutePos(new BlockPos(1, 2, 0));
        player.moveTo(standAt.getX() + 0.5, standAt.getY(), standAt.getZ() + 0.5);
        player.getInventory().add(new ItemStack(Items.DIAMOND, 5));

        ArtificialBodyEntity body = helper.spawn(BodyEntities.type(BODY_CLASS), BODY_POS);
        body.setOwner(player.getUUID());
        body.setStoredInventory(inventoryWith(helper, new ItemStack(Items.STICK)));
        return new Setup(player, body, helper.absolutePos(CAPSULE));
    }

    private static ListTag inventoryWith(GameTestHelper helper, ItemStack stack) {
        ListTag list = new ListTag();
        CompoundTag tag = (CompoundTag) stack.save(helper.getLevel().registryAccess(), new CompoundTag());
        tag.putByte("Slot", (byte) 0);
        list.add(tag);
        return list;
    }

    private static TransferService.TransferResult transfer(Setup setup) {
        return TransferService.transferFromCapsule(setup.player(), setup.capsule(), Direction.NORTH);
    }

    private static List<HumanShellEntity> shells(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(HumanShellEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(6));
    }

    private static int droppedCount(GameTestHelper helper, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(6),
                        entity -> entity.getItem().is(item))
                .stream().mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static void assertCount(GameTestHelper helper, String what, int expected, int actual) {
        helper.assertTrue(expected == actual, what + ": expected " + expected + ", got " + actual);
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void transferAndVoluntaryReturnKeepEachInventorySeparate(GameTestHelper helper) {
        Setup setup = setUp(helper);
        ServerPlayer player = setup.player();

        assertCount(helper, "transfer result", 0, transfer(setup).ordinal());
        helper.assertTrue(setup.body().isRemoved(), "The body entity must be removed while controlled");
        helper.assertTrue(TransferService.state(player).body().orElse(null) == BODY_CLASS, "The player must control the body");
        assertCount(helper, "sticks in body inventory", 1, player.getInventory().countItem(Items.STICK));
        assertCount(helper, "diamonds while in body", 0, player.getInventory().countItem(Items.DIAMOND));
        helper.assertTrue(player.getMaxHealth() == RebirthConfig.bodyStats(BODY_CLASS).maxHealth().get().floatValue(),
                "Max health must come from the body class, got " + player.getMaxHealth());
        assertCount(helper, "shells in capsule", 1, shells(helper).size());
        helper.assertTrue(ShellRegistry.get(player.server).get(player.getUUID()).isPresent(), "The human body must be recorded");

        helper.assertTrue(TransferService.returnToHumanBody(player) == TransferService.ReturnResult.SUCCESS, "Return should succeed");
        // A second request must change nothing.
        helper.assertTrue(TransferService.returnToHumanBody(player) == TransferService.ReturnResult.NOT_IN_BODY, "Second return must be refused");

        helper.assertTrue(TransferService.state(player).isHuman(), "The player must be human again");
        assertCount(helper, "diamonds after return", 5, player.getInventory().countItem(Items.DIAMOND));
        assertCount(helper, "sticks after return", 0, player.getInventory().countItem(Items.STICK));
        helper.assertTrue(player.getMaxHealth() == 20.0f, "Human max health must be 20, got " + player.getMaxHealth());
        List<ArtificialBodyEntity> bodies = helper.getLevel().getEntitiesOfClass(ArtificialBodyEntity.class,
                new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(6), ArtificialBodyEntity::isAlive);
        assertCount(helper, "bodies left in the world", 1, bodies.size());
        assertCount(helper, "items stored in the left body", 1, bodies.getFirst().getStoredInventory().size());
        helper.assertTrue(ShellRegistry.get(player.server).get(player.getUUID()).isEmpty(), "The human body record must be removed");
        helper.assertTrue(shells(helper).stream().allMatch(HumanShellEntity::isRemoved), "The shell entity must be removed");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void destroyedBodyReturnsToHumanAndDropsBodyItems(GameTestHelper helper) {
        Setup setup = setUp(helper);
        ServerPlayer player = setup.player();
        transfer(setup);

        player.kill();

        helper.assertTrue(player.isAlive(), "The player must survive the destruction of the body");
        helper.assertTrue(TransferService.state(player).isHuman(), "The player must be back in the human body");
        assertCount(helper, "diamonds after emergency return", 5, player.getInventory().countItem(Items.DIAMOND));
        assertCount(helper, "sticks dropped by the destroyed body", 1, droppedCount(helper, Items.STICK));
        assertCount(helper, "diamonds dropped", 0, droppedCount(helper, Items.DIAMOND));
        helper.assertTrue(ShellRegistry.get(player.server).get(player.getUUID()).isEmpty(), "The human body record must be removed");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void humanBodyDeathDropsItsItemsAndThenBodyDeathIsRealDeath(GameTestHelper helper) {
        Setup setup = setUp(helper);
        ServerPlayer player = setup.player();
        transfer(setup);

        HumanShellEntity shell = shells(helper).getFirst();
        shell.kill();
        assertCount(helper, "diamonds dropped by the dead human body", 5, droppedCount(helper, Items.DIAMOND));
        helper.assertTrue(ShellRegistry.get(player.server).get(player.getUUID()).isEmpty(), "The record must be removed with the human body");
        helper.assertTrue(TransferService.returnToHumanBody(player) == TransferService.ReturnResult.HUMAN_BODY_LOST,
                "Voluntary return must be refused without a human body");

        player.kill();
        helper.assertTrue(player.isDeadOrDying(), "Without any body left the player must die normally");
        helper.assertTrue(TransferService.state(player).isHuman(), "The dead player must not stay in the artificial body");
        assertCount(helper, "sticks dropped by the destroyed body", 1, droppedCount(helper, Items.STICK));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void destroyedBodyMovesToBackupBodyWhenHumanBodyIsDead(GameTestHelper helper) {
        Setup setup = setUp(helper);
        ServerPlayer player = setup.player();
        ArtificialBodyEntity second = helper.spawn(BodyEntities.type(BodyClass.BASIC_ROBOT), new BlockPos(2, 2, 0));
        second.setOwner(player.getUUID());
        transfer(setup);
        // The capsule takes the nearest of the two bodies; the other one becomes the backup.
        BodyClass first = TransferService.state(player).body().orElseThrow();
        BodyClass expectedBackup = first == BodyClass.BASIC_ROBOT ? BODY_CLASS : BodyClass.BASIC_ROBOT;
        shells(helper).getFirst().kill();

        player.kill();

        helper.assertTrue(player.isAlive(), "The player must survive by moving into the backup body");
        BodyClass now = TransferService.state(player).body().orElse(null);
        helper.assertTrue(now == expectedBackup, "The player must control the backup body " + expectedBackup + ", got " + now);
        assertCount(helper, "unoccupied bodies left", 0, helper.getLevel().getEntitiesOfClass(ArtificialBodyEntity.class,
                new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(6), ArtificialBodyEntity::isAlive).size());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void foreignBodyCannotBeUsed(GameTestHelper helper) {
        Setup setup = setUp(helper);
        setup.body().setOwner(UUID.randomUUID());

        helper.assertTrue(transfer(setup) == TransferService.TransferResult.NO_BODY, "Another player's body must not be used");
        helper.assertTrue(TransferService.state(setup.player()).isHuman(), "The player must stay human");
        assertCount(helper, "diamonds kept", 5, setup.player().getInventory().countItem(Items.DIAMOND));
        assertCount(helper, "shells", 0, shells(helper).size());
        helper.succeed();
    }
}
