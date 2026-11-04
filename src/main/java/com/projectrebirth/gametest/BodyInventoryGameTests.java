package com.projectrebirth.gametest;

import java.util.List;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.BodyAttachments;
import com.projectrebirth.bodies.BodyBlocks;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyEntities;
import com.projectrebirth.bodies.BodyInventory;
import com.projectrebirth.bodies.BodyRegistry;
import com.projectrebirth.bodies.PossessionState;
import com.projectrebirth.bodies.TransferCapsuleBlock;
import com.projectrebirth.bodies.TransferService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Artificial bodies wear no ordinary armor, whichever way it gets there. Run with {@code .\gradlew runGameTestServer}. */
@GameTestHolder(ProjectRebirth.MOD_ID)
@PrefixGameTestTemplate(false)
public class BodyInventoryGameTests {
    private static final String TEMPLATE = "floor_5x5";
    private static final List<Item> WEARABLE = List.of(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
            Items.ELYTRA, Items.CARVED_PUMPKIN, Items.ZOMBIE_HEAD);

    private static ServerPlayer player(GameTestHelper helper, boolean inBody) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos standAt = helper.absolutePos(new BlockPos(1, 2, 1));
        player.moveTo(standAt.getX() + 0.5, standAt.getY(), standAt.getZ() + 0.5);
        BodyInventory.lockArmorSlots(player);
        if (inBody) {
            player.setData(BodyAttachments.POSSESSION, PossessionState.of(BodyClass.BASIC_ROBOT));
        }
        return player;
    }

    private static boolean noArmor(ServerPlayer player) {
        return player.getInventory().armor.stream().allMatch(ItemStack::isEmpty);
    }

    @GameTest(template = TEMPLATE)
    public static void rightClickIsRefusedInABodyAndWorksForAHuman(GameTestHelper helper) {
        ServerPlayer player = player(helper, true);
        for (Item item : WEARABLE) {
            ItemStack stack = new ItemStack(item);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            InteractionResult result = player.gameMode.useItem(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND);
            helper.assertTrue(result == InteractionResult.FAIL, item + " must be refused, got " + result);
            helper.assertTrue(noArmor(player) && player.getMainHandItem().is(item), item + " must stay in the hand");
        }
        ServerPlayer human = player(helper, false);
        ItemStack helmet = new ItemStack(Items.IRON_HELMET);
        human.setItemInHand(InteractionHand.MAIN_HAND, helmet);
        human.gameMode.useItem(human, helper.getLevel(), helmet, InteractionHand.MAIN_HAND);
        helper.assertTrue(human.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "A human still wears armor");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void armorSlotsCloseOnlyInABody(GameTestHelper helper) {
        ServerPlayer player = player(helper, true);
        ItemStack helmet = new ItemStack(Items.IRON_HELMET);
        for (int i = InventoryMenu.ARMOR_SLOT_START; i < InventoryMenu.ARMOR_SLOT_END; i++) {
            helper.assertFalse(player.inventoryMenu.getSlot(i).isActive(), "Slot " + i + " must be closed in a body");
            helper.assertFalse(player.inventoryMenu.getSlot(i).mayPlace(helmet), "Slot " + i + " must take nothing in a body");
        }
        // Shift-click from the hotbar: must go to the main inventory, not to the helmet slot.
        int hotbar = InventoryMenu.USE_ROW_SLOT_START;
        player.inventoryMenu.getSlot(hotbar).set(helmet);
        player.inventoryMenu.quickMoveStack(player, hotbar);
        helper.assertTrue(noArmor(player), "Shift-click must not put armor on a body");
        helper.assertTrue(player.getInventory().countItem(Items.IRON_HELMET) == 1, "The helmet must stay in the inventory");

        ServerPlayer human = player(helper, false);
        helper.assertTrue(human.inventoryMenu.getSlot(InventoryMenu.ARMOR_SLOT_START).isActive(), "A human keeps the slots");
        helper.assertTrue(human.inventoryMenu.getSlot(InventoryMenu.ARMOR_SLOT_START).mayPlace(helmet), "A human keeps the helmet slot");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void armorThatGetsThereAnywayGoesBackAndIsNotLost(GameTestHelper helper) {
        ServerPlayer player = player(helper, true);
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
        // What a dispenser does.
        ItemStack boots = new ItemStack(Items.IRON_BOOTS);
        player.setItemSlot(EquipmentSlot.FEET, boots.copy());
        BodyInventory.clearArmor(player);
        helper.assertTrue(noArmor(player), "Nothing may stay on the body");
        helper.assertTrue(player.getInventory().countItem(Items.ELYTRA) == 1 && player.getInventory().countItem(Items.IRON_BOOTS) == 1,
                "Both must be in the inventory");

        for (int i = 0; i < player.getInventory().items.size(); i++) {
            player.getInventory().items.set(i, new ItemStack(Items.STONE));
        }
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        BodyInventory.clearArmor(player);
        helper.assertTrue(noArmor(player), "A full inventory changes nothing here");
        int onGround = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8),
                entity -> entity.getItem().is(Items.DIAMOND_HELMET)).size();
        helper.assertTrue(onGround == 1, "With no room it must drop next to the body");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aBodyFromAnOldWorldTakesItsArmorOffOnTransfer(GameTestHelper helper) {
        BlockState lower = BodyBlocks.TRANSFER_CAPSULE.get().defaultBlockState()
                .setValue(TransferCapsuleBlock.FACING, Direction.NORTH).setValue(TransferCapsuleBlock.HALF, DoubleBlockHalf.LOWER);
        helper.setBlock(new BlockPos(0, 2, 0), lower);
        helper.setBlock(new BlockPos(0, 3, 0), lower.setValue(TransferCapsuleBlock.HALF, DoubleBlockHalf.UPPER));
        ServerPlayer player = player(helper, false);

        ArtificialBodyEntity body = helper.spawn(BodyEntities.type(BodyClass.BASIC_ROBOT), new BlockPos(3, 2, 3));
        body.setOwner(player.getUUID());
        BodyRegistry.get(player.server).updateStanding(body);
        ListTag inventory = new ListTag();
        CompoundTag worn = (CompoundTag) new ItemStack(Items.GOLDEN_CHESTPLATE).save(helper.getLevel().registryAccess(), new CompoundTag());
        worn.putByte("Slot", (byte) 102);
        inventory.add(worn);
        body.setStoredInventory(inventory);

        TransferService.TransferResult result = TransferService.transferFromCapsule(player, helper.absolutePos(new BlockPos(0, 2, 0)), Direction.NORTH, body.getUUID());
        helper.assertTrue(result == TransferService.TransferResult.SUCCESS, "Transfer must work, got " + result);
        helper.assertTrue(noArmor(player), "The body must not wear it");
        helper.assertTrue(player.getInventory().countItem(Items.GOLDEN_CHESTPLATE) == 1, "It must be in the inventory");
        helper.succeed();
    }
}
