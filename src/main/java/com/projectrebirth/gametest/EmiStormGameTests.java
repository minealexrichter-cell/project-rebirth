package com.projectrebirth.gametest;

import java.util.Map;
import java.util.Optional;

import com.projectrebirth.bodies.BodyAttachments;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyRules;
import com.projectrebirth.bodies.Jetpack;
import com.projectrebirth.bodies.PossessionState;
import com.projectrebirth.bodies.TransferService;
import com.projectrebirth.bodies.power.BodyPower;
import com.projectrebirth.bodies.power.BodyWear;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.Emi;
import com.projectrebirth.environment.Environment;
import com.projectrebirth.environment.EnvironmentProfile;
import com.projectrebirth.environment.EnvironmentState;
import com.projectrebirth.environment.Exposure;
import com.projectrebirth.environment.Hazard;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.powerarmor.PowerArmorRegistry;
import com.projectrebirth.shelter.Shelters;
import com.projectrebirth.technology.Station;
import com.projectrebirth.technology.StationBlockEntity;
import com.projectrebirth.technology.TechBlocks;
import com.projectrebirth.technology.TechRecipes;
import com.projectrebirth.upgrades.ArmorSlot;
import com.projectrebirth.upgrades.InstalledUpgrades;
import com.projectrebirth.upgrades.UpgradeRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Version 1.5.0, block E: the electromagnetic storm. {@code .\gradlew runGameTestServer -PgameTests=projectrebirth_power}. */
@GameTestHolder(PlanTests.POWER)
@PrefixGameTestTemplate(false)
public class EmiStormGameTests {
    private static final String TEMPLATE = "floor_5x5";
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    private static Exposure emi(float amount) {
        return Exposure.builder().add(Hazard.EMI, amount).build();
    }

    private static ServerPlayer playerIn(GameTestHelper helper, BodyClass bodyClass, Item... gadgets) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setData(BodyAttachments.POSSESSION, PossessionState.of(bodyClass));
        InstalledUpgrades upgrades = InstalledUpgrades.EMPTY;
        for (Item gadget : gadgets) {
            upgrades = upgrades.with(new InstalledUpgrades.Installed(Optional.empty(), new ItemStack(gadget)));
        }
        player.setData(UpgradeRegistry.CONTROLLED_BODY, upgrades);
        TransferService.applyBodyModifiers(player, bodyClass);
        BlockPos at = helper.absolutePos(POS);
        player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return player;
    }

    @GameTest(template = TEMPLATE)
    public static void theStormIsInTheCycleAndDepthWeakensIt(GameTestHelper helper) {
        helper.assertTrue(Environment.EVENTS.contains(Environment.EMI_STORM), "One of the catastrophes");
        helper.assertTrue(Environment.EMI_STORM.equals(EnvironmentState.chooseEvent(helper.getLevel().getRandom(), Environment.EMI_STORM::equals)),
                "It can be chosen by its weight");
        EnvironmentProfile profile = new EnvironmentProfile(HolderSet.direct(), 0, Map.of(), Map.of(), 0.0f);
        EnvironmentState state = new EnvironmentState();
        state.startActive(Environment.EMI_STORM, 1.0f, 100);
        float open = Environment.compute(profile, state, Environment.Shelter.NONE).get(Hazard.EMI);
        float roofed = Environment.compute(profile, state, new Environment.Shelter(true, false)).get(Hazard.EMI);
        float deep = Environment.compute(profile, state, new Environment.Shelter(true, true)).get(Hazard.EMI);
        helper.assertTrue(open > 0.0f && roofed == open, "A roof does not help: " + open + " / " + roofed);
        helper.assertTrue(Math.abs(deep - open * RebirthConfig.EMI_UNDERGROUND.get()) < 0.001, "Deep ground weakens it: " + deep);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void betterBodiesAndShieldingFeelLess(GameTestHelper helper) {
        ServerPlayer basic = playerIn(helper, BodyClass.BASIC_ROBOT);
        ServerPlayer premium = playerIn(helper, BodyClass.PREMIUM_SYNTHETIC);
        ServerPlayer shielded = playerIn(helper, BodyClass.BASIC_ROBOT, UpgradeRegistry.EMI_SHIELDING.get());
        float b = Emi.felt(basic, 10.0f);
        float p = Emi.felt(premium, 10.0f);
        float s = Emi.felt(shielded, 10.0f);
        helper.assertTrue(p < b, "The premium body resists more: " + p + " vs " + b);
        helper.assertTrue(Math.abs(s - b * RebirthConfig.EMI_SHIELD.get()) < 0.001, "Shielding keeps most of it off: " + s);
        helper.assertFalse(Emi.disturbs(s), "A shielded body is not disturbed by an ordinary storm");

        ServerPlayer pilot = helper.makeMockServerPlayerInLevel();
        PowerArmorEntity armor = helper.spawn(PowerArmorRegistry.POWER_ARMOR.get(), POS);
        pilot.startRiding(armor, true);
        float plain = Emi.felt(pilot, 10.0f);
        armor.setUpgrades(InstalledUpgrades.EMPTY.with(new InstalledUpgrades.Installed(Optional.of(ArmorSlot.CHEST_FRONT),
                new ItemStack(UpgradeRegistry.EMI_SCREEN.get()))));
        float screened = Emi.felt(pilot, 10.0f);
        helper.assertTrue(plain == 10.0f && Math.abs(screened - 10.0f * RebirthConfig.EMI_SHIELD.get()) < 0.001,
                "The EMI screen of an armor: " + plain + " -> " + screened);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aDisturbedBodyKeepsControlButSuffers(GameTestHelper helper) {
        ServerPlayer player = playerIn(helper, BodyClass.ADVANCED_SYNTHETIC, UpgradeRegistry.JETPACK.get());
        float before = BodyPower.charge(player);
        Emi.second(player, emi(10.0f));
        helper.assertTrue(Emi.disturbed(player), "Disturbed");
        helper.assertTrue(BodyPower.charge(player) < before, "More charge used");

        player.setHealth(player.getMaxHealth() - 5.0f);
        player.setData(BodyAttachments.LAST_DAMAGE, helper.getLevel().getGameTime() - 100_000L);
        float health = player.getHealth();
        BodyRules.tickSelfRepair(player, BodyClass.ADVANCED_SYNTHETIC);
        helper.assertTrue(player.getHealth() == health, "No self-repair in the storm");

        double dropout = RebirthConfig.EMI_JETPACK_DROPOUT.get();
        RebirthConfig.EMI_JETPACK_DROPOUT.set(1.0);
        player.moveTo(player.getX(), player.getY() + 2.0, player.getZ());
        player.setOnGround(false);
        Jetpack.serverTick(player, true);
        float used = player.getData(UpgradeRegistry.JETPACK_USED);
        RebirthConfig.EMI_JETPACK_DROPOUT.set(dropout);
        helper.assertTrue(used == 0.0f, "The jetpack cuts out");

        float wear = BodyWear.wear(player);
        Emi.fault(player);
        helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "A fault slows the body down");
        helper.assertTrue(BodyWear.wear(player) > wear, "And wears it a little");
        helper.assertTrue(TransferService.state(player).body().isPresent() && player.isAlive(), "Control stays");

        Emi.second(player, Exposure.NONE);
        helper.assertFalse(Emi.disturbed(player), "After the storm, nothing");
        helper.succeed();
    }

    private static StationBlockEntity station(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, TechBlocks.STATIONS.get(Station.ELECTRONICS_FABRICATOR).get());
        StationBlockEntity station = helper.getBlockEntity(pos);
        station.energy().receiveEnergy(station.energy().getMaxEnergyStored(), false);
        // circuit boards: copper, silver, redstone
        station.items().setStackInSlot(0, new ItemStack(Items.COPPER_INGOT, 16));
        station.items().setStackInSlot(1, new ItemStack(com.projectrebirth.technology.TechItems.INGOTS.get(com.projectrebirth.technology.Metal.SILVER).get(), 16));
        station.items().setStackInSlot(2, new ItemStack(Items.REDSTONE, 16));
        var recipes = TechRecipes.recipesFor(helper.getLevel(), Station.ELECTRONICS_FABRICATOR);
        for (int i = 0; i < recipes.size(); i++) {
            if (recipes.get(i).value().result().is(com.projectrebirth.technology.TechItems.CIRCUIT_BOARD.get())) {
                station.select(i);
            }
        }
        return station;
    }

    @GameTest(template = TEMPLATE, batch = "emi_world", timeoutTicks = 200)
    public static void aStationOutsideAShelterStandsStillAndGoesOnAfterwards(GameTestHelper helper) {
        StationBlockEntity station = station(helper, POS);
        EnvironmentState state = EnvironmentState.get(helper.getLevel());
        state.startActive(Environment.EMI_STORM, 1.0f, 600);
        helper.runAfterDelay(45, () -> {
            int progress = station.progress();
            int energy = station.energy().getEnergyStored();
            boolean paused = Emi.stationFactor(helper.getLevel(), helper.absolutePos(POS)) == 0.0f;
            state.clear();
            helper.assertTrue(paused, "The storm stops the station");
            helper.assertTrue(progress == 0 && energy == station.energy().getMaxEnergyStored(), "No progress and no energy used: " + progress + ", " + energy);
            helper.runAfterDelay(45, () -> {
                helper.assertTrue(station.progress() > 0, "After the storm it goes on: " + station.progress());
                helper.succeed();
            });
        });
    }

    @GameTest(template = "floor_9x9", batch = "emi_world_room", skyAccess = true, timeoutTicks = 100)
    public static void aStationInASealedRoomWorksOnAndALeakStopsIt(GameTestHelper helper) {
        ShelterGameTests.box(helper, Blocks.STONE);
        BlockPos inside = new BlockPos(3, 2, 3);
        helper.runAfterDelay(2, () -> {
            Shelters.of(helper.getLevel()).scanNow(helper.absolutePos(ShelterGameTests.UNIT));
            helper.setBlock(inside, TechBlocks.STATIONS.get(Station.ELECTRONICS_FABRICATOR).get());
            EnvironmentState state = EnvironmentState.get(helper.getLevel());
            state.startActive(Environment.EMI_STORM, 1.0f, 600);
            float sealed = Emi.stationFactor(helper.getLevel(), helper.absolutePos(inside));
            helper.setBlock(ShelterGameTests.WALL, Blocks.AIR);
            Shelters.of(helper.getLevel()).scanNow(helper.absolutePos(ShelterGameTests.UNIT));
            float leaking = Emi.stationFactor(helper.getLevel(), helper.absolutePos(inside));
            state.clear();
            helper.assertTrue(sealed == 1.0f, "In a sealed room the station works on: " + sealed);
            helper.assertTrue(leaking == 0.0f, "A leaking room does not shield it: " + leaking);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void aPersonFeelsItButIsNotHurt(GameTestHelper helper) {
        ServerPlayer human = helper.makeMockServerPlayerInLevel();
        human.setGameMode(GameType.SURVIVAL);
        float health = human.getHealth();
        Emi.second(human, emi(10.0f));
        helper.assertTrue(Emi.level(human) == 10.0f, "The devices of a person feel it (dosimeter, HUD)");
        helper.assertTrue(human.getHealth() == health && !human.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "The person is not hurt");
        helper.succeed();
    }
}
