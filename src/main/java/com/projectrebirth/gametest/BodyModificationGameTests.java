package com.projectrebirth.gametest;

import java.util.List;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyItems;
import com.projectrebirth.technology.BodyModificationBlockEntity;
import com.projectrebirth.technology.Station;
import com.projectrebirth.technology.StationBlockEntity;
import com.projectrebirth.technology.TechBlocks;
import com.projectrebirth.technology.TechItems;
import com.projectrebirth.technology.TechRecipes;
import com.projectrebirth.upgrades.InstalledUpgrades;
import com.projectrebirth.upgrades.UpgradeHost;
import com.projectrebirth.upgrades.UpgradeRegistry;
import com.projectrebirth.upgrades.Upgrades;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The implant fabricator and the body modification station. Run with {@code .\gradlew runGameTestServer}. */
@GameTestHolder(ProjectRebirth.MOD_ID)
@PrefixGameTestTemplate(false)
public class BodyModificationGameTests {
    private static final String TEMPLATE = "floor_5x5";
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    private static BodyModificationBlockEntity station(GameTestHelper helper, BodyClass bodyClass, ItemStack gadgets) {
        helper.setBlock(POS, TechBlocks.BODY_MODIFICATION_STATION.get());
        BodyModificationBlockEntity station = helper.getBlockEntity(POS);
        station.energy().receiveEnergy(station.energy().getMaxEnergyStored(), false);
        station.items().setStackInSlot(BodyModificationBlockEntity.BODY_SLOT, new ItemStack(BodyItems.item(bodyClass)));
        station.items().setStackInSlot(BodyModificationBlockEntity.FIRST_GADGET_SLOT, gadgets);
        return station;
    }

    private static InstalledUpgrades installed(BodyModificationBlockEntity station) {
        return Upgrades.ofStack(station.items().getStackInSlot(BodyModificationBlockEntity.BODY_SLOT));
    }

    private static int onGround(GameTestHelper helper, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8),
                        entity -> entity.getItem().is(item))
                .stream().mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void installingTakesTimeAndEnergyAndMovesOneGadget(GameTestHelper helper) {
        BodyModificationBlockEntity station = station(helper, BodyClass.PREMIUM_SYNTHETIC, new ItemStack(UpgradeRegistry.COPPER_PLATING.get(), 3));
        int energyBefore = station.energy().getEnergyStored();
        helper.assertTrue(station.startInstall(0), "Installing must start");
        helper.assertFalse(station.startInstall(0), "Only one installation at a time");
        int time = BodyModificationBlockEntity.installTicks();
        helper.runAfterDelay(time / 2, () -> helper.assertTrue(installed(station).isEmpty(), "Nothing may be installed before the time is up"));
        helper.runAfterDelay(time + 5, () -> {
            helper.assertTrue(installed(station).entries().size() == 1, "One piece must be installed");
            helper.assertTrue(station.items().getStackInSlot(BodyModificationBlockEntity.FIRST_GADGET_SLOT).getCount() == 2, "One piece must leave the slot");
            helper.assertTrue(station.energy().getEnergyStored() < energyBefore, "It must use energy");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void withoutEnergyInstallingWaitsAndContinues(GameTestHelper helper) {
        BodyModificationBlockEntity station = station(helper, BodyClass.BASIC_ROBOT, new ItemStack(UpgradeRegistry.SEALING_MODULE.get()));
        station.energy().load(0);
        helper.assertTrue(station.startInstall(0), "Installing must start");
        int time = BodyModificationBlockEntity.installTicks();
        helper.runAfterDelay(time + 5, () -> {
            helper.assertTrue(installed(station).isEmpty(), "Without energy nothing happens");
            station.energy().receiveEnergy(station.energy().getMaxEnergyStored(), false);
            helper.runAfterDelay(time + 5, () -> {
                helper.assertTrue(installed(station).entries().size() == 1, "With energy it must finish");
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE)
    public static void whatDoesNotFitDoesNotStart(GameTestHelper helper) {
        BodyModificationBlockEntity station = station(helper, BodyClass.BASIC_ROBOT, new ItemStack(UpgradeRegistry.JETPACK.get()));
        UpgradeHost host = UpgradeHost.ofPacked(station.items().getStackInSlot(BodyModificationBlockEntity.BODY_SLOT));
        Upgrades.install(helper.getLevel().registryAccess(), host, new ItemStack(UpgradeRegistry.SEALING_MODULE.get()), null);
        helper.assertFalse(station.startInstall(0), "A basic body has one slot");
        helper.assertFalse(station.startInstall(3), "An empty gadget slot installs nothing");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void removingGivesTheGadgetToThePlayerOnce(GameTestHelper helper) {
        BodyModificationBlockEntity station = station(helper, BodyClass.IMPROVED_ROBOT, ItemStack.EMPTY);
        UpgradeHost host = UpgradeHost.ofPacked(station.items().getStackInSlot(BodyModificationBlockEntity.BODY_SLOT));
        Upgrades.install(helper.getLevel().registryAccess(), host, new ItemStack(UpgradeRegistry.JETPACK.get()), null);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(station.remove(player, 0), "It must come out");
        helper.assertFalse(station.remove(player, 0), "It must not come out twice");
        helper.assertTrue(player.getInventory().countItem(UpgradeRegistry.JETPACK.get()) == 1, "The player must have it");
        helper.assertTrue(installed(station).isEmpty(), "The body must not have it");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void automationRules(GameTestHelper helper) {
        helper.setBlock(POS, TechBlocks.BODY_MODIFICATION_STATION.get());
        BodyModificationBlockEntity station = helper.getBlockEntity(POS);
        ItemStack stone = new ItemStack(Items.STONE);
        helper.assertTrue(station.automation().insertItem(1, stone, false).getCount() == 1, "A gadget slot takes only gadgets");
        helper.assertTrue(station.automation().insertItem(0, stone, false).getCount() == 1, "The body slot takes only bodies");
        helper.assertTrue(station.automation().insertItem(1, new ItemStack(UpgradeRegistry.COPPER_PLATING.get()), false).isEmpty(), "A gadget goes in");
        helper.assertTrue(station.automation().insertItem(1, new ItemStack(UpgradeRegistry.FLOODLIGHT.get()), false).getCount() == 1, "Armor upgrades are not gadgets");
        helper.assertTrue(station.automation().insertItem(0, new ItemStack(BodyItems.item(BodyClass.BASIC_ROBOT)), false).isEmpty(), "A body goes in");
        helper.assertTrue(station.automation().extractItem(1, 1, false).isEmpty(), "A gadget does not come out");

        helper.assertTrue(station.startInstall(0), "Installing must start");
        helper.assertTrue(station.automation().extractItem(0, 1, false).isEmpty(), "No body comes out during an installation");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void brokenStationDropsTheBodyWithItsGadgets(GameTestHelper helper) {
        BodyModificationBlockEntity station = station(helper, BodyClass.ADVANCED_SYNTHETIC, new ItemStack(UpgradeRegistry.COPPER_PLATING.get()));
        UpgradeHost host = UpgradeHost.ofPacked(station.items().getStackInSlot(BodyModificationBlockEntity.BODY_SLOT));
        Upgrades.install(helper.getLevel().registryAccess(), host, new ItemStack(UpgradeRegistry.JETPACK.get()), null);
        helper.getLevel().destroyBlock(helper.absolutePos(POS), false);
        List<ItemEntity> bodies = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8),
                entity -> entity.getItem().is(BodyItems.item(BodyClass.ADVANCED_SYNTHETIC)));
        helper.assertTrue(bodies.size() == 1 && !Upgrades.ofStack(bodies.getFirst().getItem()).isEmpty(), "The body must drop with its gadget");
        helper.assertTrue(onGround(helper, UpgradeRegistry.COPPER_PLATING.get()) == 1, "The gadget in the slot must drop");
        helper.assertTrue(onGround(helper, UpgradeRegistry.JETPACK.get()) == 0, "The installed gadget stays in the body");
        helper.succeed();
    }

    // ---------------------------------------------------------------- moving to a better body

    @GameTest(template = TEMPLATE)
    public static void movingUpgradesReturnsWhatDoesNotFit(GameTestHelper helper) {
        ItemStack premium = new ItemStack(BodyItems.item(BodyClass.PREMIUM_SYNTHETIC));
        UpgradeHost from = UpgradeHost.ofPacked(premium);
        var access = helper.getLevel().registryAccess();
        Upgrades.install(access, from, new ItemStack(UpgradeRegistry.SEALING_MODULE.get()), null);
        Upgrades.install(access, from, new ItemStack(UpgradeRegistry.JETPACK.get()), null);
        Upgrades.install(access, from, new ItemStack(UpgradeRegistry.IRON_PLATING.get()), null);

        ItemStack basic = new ItemStack(BodyItems.item(BodyClass.BASIC_ROBOT));
        List<ItemStack> left = Upgrades.moveAll(access, Upgrades.ofStack(premium), basic);
        helper.assertTrue(Upgrades.ofStack(basic).entries().size() == 1, "A basic body takes one");
        helper.assertTrue(left.size() == 2, "The other two come back, got " + left.size());

        List<ItemStack> all = Upgrades.moveAll(access, Upgrades.ofStack(premium), new ItemStack(Items.STICK));
        helper.assertTrue(all.size() == 3, "Into an item that holds no upgrades nothing is lost");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public static void betterBodyAtTheAssemblyStationKeepsTheGadgets(GameTestHelper helper) {
        helper.setBlock(POS, TechBlocks.STATIONS.get(Station.ASSEMBLY_STATION).get());
        StationBlockEntity station = helper.getBlockEntity(POS);
        station.energy().receiveEnergy(station.energy().getMaxEnergyStored(), false);
        ItemStack old = new ItemStack(BodyItems.item(BodyClass.BASIC_ROBOT));
        Upgrades.install(helper.getLevel().registryAccess(), UpgradeHost.ofPacked(old), new ItemStack(UpgradeRegistry.SEALING_MODULE.get()), null);
        station.items().setStackInSlot(0, old);
        station.items().setStackInSlot(1, new ItemStack(TechItems.INGOTS.get(com.projectrebirth.technology.Metal.ALUMINUM).get(), 6));
        station.items().setStackInSlot(2, new ItemStack(TechItems.ACTUATOR.get(), 2));
        station.items().setStackInSlot(3, new ItemStack(TechItems.CIRCUIT_BOARD.get(), 2));
        int index = -1;
        var recipes = TechRecipes.recipesFor(helper.getLevel(), Station.ASSEMBLY_STATION);
        for (int i = 0; i < recipes.size(); i++) {
            if (recipes.get(i).id().getPath().equals("improved_robot_body")) {
                index = i;
            }
        }
        station.select(index);
        int time = StationBlockEntity.productionTime(recipes.get(index).value());
        helper.runAfterDelay(time + 5, () -> {
            ItemStack made = station.items().getStackInSlot(StationBlockEntity.OUTPUT_SLOT);
            helper.assertTrue(made.is(BodyItems.item(BodyClass.IMPROVED_ROBOT)), "The better body must be made");
            helper.assertTrue(Upgrades.ofStack(made).entries().size() == 1, "It must have the gadget of the old one");
            helper.assertTrue(station.items().getStackInSlot(0).isEmpty(), "The old body must be used up");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void implantFabricatorMakesGadgets(GameTestHelper helper) {
        helper.setBlock(POS, TechBlocks.STATIONS.get(Station.IMPLANT_FABRICATOR).get());
        StationBlockEntity station = helper.getBlockEntity(POS);
        station.energy().receiveEnergy(station.energy().getMaxEnergyStored(), false);
        station.items().setStackInSlot(0, new ItemStack(Items.COPPER_INGOT, 6));
        station.items().setStackInSlot(1, new ItemStack(TechItems.BRONZE_INGOT.get(), 1));
        var recipes = TechRecipes.recipesFor(helper.getLevel(), Station.IMPLANT_FABRICATOR);
        helper.assertTrue(recipes.size() == 10, "Ten gadget recipes (extra battery, cooling module and electronics shielding since 1.5.0, the sensor module since 1.6.0, chitin plating since 1.9.0), got " + recipes.size());
        int index = -1;
        for (int i = 0; i < recipes.size(); i++) {
            if (recipes.get(i).value().result().is(UpgradeRegistry.COPPER_PLATING.get())) {
                index = i;
            }
        }
        station.select(index);
        helper.runAfterDelay(StationBlockEntity.productionTime(recipes.get(index).value()) + 5, () -> {
            helper.assertTrue(station.items().getStackInSlot(StationBlockEntity.OUTPUT_SLOT).is(UpgradeRegistry.COPPER_PLATING.get()), "The plating must be made");
            helper.succeed();
        });
    }
}
