package com.projectrebirth.gametest;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.powerarmor.ArmorMountingMenu;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.powerarmor.PowerArmorRegistry;
import com.projectrebirth.technology.Station;
import com.projectrebirth.technology.StationBlockEntity;
import com.projectrebirth.technology.TechBlocks;
import com.projectrebirth.technology.TechRecipes;
import com.projectrebirth.upgrades.ArmorSlot;
import com.projectrebirth.upgrades.UpgradeRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The mounting tool's window on a power armor. Run with {@code .\gradlew runGameTestServer}. */
@GameTestHolder(ProjectRebirth.MOD_ID)
@PrefixGameTestTemplate(false)
public class ArmorMountingGameTests {
    private static final String TEMPLATE = "floor_5x5";
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    /** The first hotbar slot in the window: seven parts, then 27 slots of the inventory. */
    private static final int HOTBAR_SLOT = ArmorMountingMenu.SLOT_COUNT + 27;

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return player;
    }

    private static PowerArmorEntity armorOf(GameTestHelper helper, ServerPlayer owner) {
        PowerArmorEntity armor = helper.spawn(PowerArmorRegistry.POWER_ARMOR.get(), POS);
        armor.setOwner(owner.getUUID());
        return armor;
    }

    /** Puts what is in the first hotbar slot into a part of the armor, the way a player's clicks do. */
    private static void place(ArmorMountingMenu menu, ServerPlayer player, ArmorSlot part) {
        menu.clicked(HOTBAR_SLOT, 0, ClickType.PICKUP, player);
        menu.clicked(part.ordinal(), 1, ClickType.PICKUP, player);
        menu.clicked(HOTBAR_SLOT, 0, ClickType.PICKUP, player);
    }

    private static ItemStack stack(Item item) {
        return new ItemStack(item);
    }

    @GameTest(template = TEMPLATE)
    public static void theToolWithoutShiftDoesNothingToTheArmor(GameTestHelper helper) {
        // Opening the window itself needs a real client: a test player cannot receive it.
        ServerPlayer owner = player(helper);
        PowerArmorEntity armor = armorOf(helper, owner);
        owner.setItemInHand(InteractionHand.MAIN_HAND, stack(PowerArmorRegistry.UPGRADE_MOUNTING_TOOL.get()));
        armor.interact(owner, InteractionHand.MAIN_HAND);
        helper.assertFalse(owner.containerMenu instanceof ArmorMountingMenu, "Without shift no window");
        helper.assertFalse(owner.isPassenger(), "The tool must not put the player into the armor");
        helper.assertFalse(armor.isRemoved(), "The tool does not pack the armor");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void onlyTheOwnerAtAnEmptyWholeArmorNearby(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        ServerPlayer stranger = player(helper);
        PowerArmorEntity armor = armorOf(helper, owner);
        helper.assertTrue(ArmorMountingMenu.mayMount(owner, armor), "The owner may");
        helper.assertFalse(ArmorMountingMenu.mayMount(stranger, armor), "A stranger may not");

        stranger.setItemInHand(InteractionHand.MAIN_HAND, stack(PowerArmorRegistry.UPGRADE_MOUNTING_TOOL.get()));
        stranger.setShiftKeyDown(true);
        armor.interact(stranger, InteractionHand.MAIN_HAND);
        helper.assertFalse(stranger.containerMenu instanceof ArmorMountingMenu, "A stranger gets no window");

        ServerPlayer far = player(helper);
        armor.setOwner(far.getUUID());
        far.moveTo(far.getX() + 40, far.getY(), far.getZ());
        helper.assertFalse(ArmorMountingMenu.mayMount(far, armor), "Too far away");

        armor.setOwner(owner.getUUID());
        armor.setIntegrity(0.0f, armor.getMaxCritical());
        helper.assertTrue(armor.isDisabled() && ArmorMountingMenu.mayMount(owner, armor), "A shut-down armor can be worked on");
        armor.setIntegrity(0.0f, 0.0f);
        helper.assertFalse(ArmorMountingMenu.mayMount(owner, armor), "A wreck cannot");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aPilotClosesTheWindow(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        PowerArmorEntity armor = armorOf(helper, owner);
        ArmorMountingMenu menu = new ArmorMountingMenu(1, owner.getInventory(), armor);
        helper.assertTrue(menu.stillValid(owner), "Empty armor: open");
        ServerPlayer pilot = player(helper);
        pilot.startRiding(armor, true);
        helper.assertFalse(menu.stillValid(owner), "With a pilot in it the server refuses every click");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void clicksInstallAndRemoveWithoutCopies(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        PowerArmorEntity armor = armorOf(helper, owner);
        ArmorMountingMenu menu = new ArmorMountingMenu(1, owner.getInventory(), armor);
        owner.getInventory().items.set(0, new ItemStack(UpgradeRegistry.ARMOR_IRON_PLATING.get(), 3));
        // Right click puts one piece in.
        place(menu, owner, ArmorSlot.RIGHT_ARM);
        helper.assertTrue(armor.upgrades().entries().size() == 1, "One piece on the armor, got " + armor.upgrades());
        helper.assertTrue(owner.getInventory().countItem(UpgradeRegistry.ARMOR_IRON_PLATING.get()) == 2, "Two left");
        helper.assertTrue(armor.upgrades().indexOf(ArmorSlot.RIGHT_ARM) >= 0, "On the right part");

        menu.clicked(ArmorSlot.RIGHT_ARM.ordinal(), 0, ClickType.QUICK_MOVE, owner);
        helper.assertTrue(armor.upgrades().isEmpty(), "Shift-click takes it off");
        helper.assertTrue(owner.getInventory().countItem(UpgradeRegistry.ARMOR_IRON_PLATING.get()) == 3 && menu.getCarried().isEmpty(), "All three back");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void slotsRefuseWhatDoesNotFit(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        PowerArmorEntity armor = armorOf(helper, owner);
        ArmorMountingMenu menu = new ArmorMountingMenu(1, owner.getInventory(), armor);
        helper.assertFalse(menu.getSlot(ArmorSlot.HEAD.ordinal()).mayPlace(stack(UpgradeRegistry.ARMOR_IRON_PLATING.get())), "No plates on the head");
        helper.assertFalse(menu.getSlot(ArmorSlot.CHEST_BACK.ordinal()).mayPlace(stack(UpgradeRegistry.JETPACK.get())), "Body gadgets are not for armor");
        helper.assertFalse(menu.getSlot(ArmorSlot.HEAD.ordinal()).mayPlace(stack(Items.STONE)), "Stone is no upgrade");
        helper.assertTrue(menu.getSlot(ArmorSlot.HEAD.ordinal()).mayPlace(stack(UpgradeRegistry.FLOODLIGHT.get())), "A floodlight on the head");

        owner.getInventory().items.set(0, stack(Items.STONE));
        menu.clicked(HOTBAR_SLOT, 0, ClickType.QUICK_MOVE, owner);
        helper.assertTrue(armor.upgrades().isEmpty(), "Shift-click with stone must install nothing");

        owner.getInventory().items.set(0, new ItemStack(UpgradeRegistry.COMPRESSED_AIR_MODULE.get()));
        menu.clicked(HOTBAR_SLOT, 0, ClickType.QUICK_MOVE, owner);
        helper.assertTrue(armor.upgrades().indexOf(ArmorSlot.CHEST_BACK) >= 0, "Shift-click uses the first part that fits");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aBannerKeepsItsDataThroughTheWindow(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        PowerArmorEntity armor = armorOf(helper, owner);
        ArmorMountingMenu menu = new ArmorMountingMenu(1, owner.getInventory(), armor);
        ItemStack banner = stack(Items.RED_BANNER);
        banner.set(DataComponents.CUSTOM_NAME, Component.literal("Flag of the test"));
        owner.getInventory().items.set(0, banner.copy());
        menu.clicked(HOTBAR_SLOT, 0, ClickType.QUICK_MOVE, owner);
        helper.assertTrue(armor.upgrades().indexOf(ArmorSlot.CHEST_BACK) >= 0, "A banner goes on the back");
        menu.clicked(ArmorSlot.CHEST_BACK.ordinal(), 0, ClickType.QUICK_MOVE, owner);
        ItemStack back = owner.getInventory().items.stream().filter(stack -> stack.is(Items.RED_BANNER)).findFirst().orElse(ItemStack.EMPTY);
        helper.assertTrue(ItemStack.matches(back, banner), "It must come back with its data");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void upgradeFabricatorMakesArmorUpgrades(GameTestHelper helper) {
        helper.setBlock(POS, TechBlocks.STATIONS.get(Station.ARMOR_UPGRADE_FABRICATOR).get());
        StationBlockEntity station = helper.getBlockEntity(POS);
        station.energy().receiveEnergy(station.energy().getMaxEnergyStored(), false);
        station.items().setStackInSlot(0, new ItemStack(Items.COPPER_INGOT, 8));
        station.items().setStackInSlot(1, new ItemStack(com.projectrebirth.technology.TechItems.BRONZE_INGOT.get(), 2));
        var recipes = TechRecipes.recipesFor(helper.getLevel(), Station.ARMOR_UPGRADE_FABRICATOR);
        helper.assertTrue(recipes.size() == 5, "Five recipes, got " + recipes.size());
        int index = -1;
        for (int i = 0; i < recipes.size(); i++) {
            if (recipes.get(i).value().result().is(UpgradeRegistry.ARMOR_COPPER_PLATING.get())) {
                index = i;
            }
        }
        station.select(index);
        helper.runAfterDelay(StationBlockEntity.productionTime(recipes.get(index).value()) + 5, () -> {
            helper.assertTrue(station.items().getStackInSlot(StationBlockEntity.OUTPUT_SLOT).is(UpgradeRegistry.ARMOR_COPPER_PLATING.get()), "The plating must be made");
            helper.succeed();
        });
    }
}
