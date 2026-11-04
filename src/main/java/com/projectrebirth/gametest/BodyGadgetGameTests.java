package com.projectrebirth.gametest;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.BodyAttachments;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyEntities;
import com.projectrebirth.bodies.Jetpack;
import com.projectrebirth.bodies.PossessionState;
import com.projectrebirth.bodies.TransferService;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.upgrades.InstalledUpgrades;
import com.projectrebirth.upgrades.UpgradeRegistry;
import com.projectrebirth.upgrades.Upgrades;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** What the gadgets of a body do. Run with {@code .\gradlew runGameTestServer}. */
@GameTestHolder(ProjectRebirth.MOD_ID)
@PrefixGameTestTemplate(false)
public class BodyGadgetGameTests {
    private static final String TEMPLATE = "floor_5x5";
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    private static final double EPSILON = 0.001;

    private static InstalledUpgrades upgrades(Item... items) {
        InstalledUpgrades upgrades = InstalledUpgrades.EMPTY;
        for (Item item : items) {
            upgrades = upgrades.with(new InstalledUpgrades.Installed(Optional.empty(), new ItemStack(item)));
        }
        return upgrades;
    }

    private static ServerPlayer playerWith(GameTestHelper helper, Item... items) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setData(BodyAttachments.POSSESSION, PossessionState.of(BodyClass.PREMIUM_SYNTHETIC));
        player.setData(UpgradeRegistry.CONTROLLED_BODY, upgrades(items));
        return player;
    }

    @GameTest(template = TEMPLATE)
    public static void platingAddsArmorPerPieceOnAStandingBody(GameTestHelper helper) {
        ArtificialBodyEntity body = helper.spawn(BodyEntities.type(BodyClass.PREMIUM_SYNTHETIC), POS);
        double base = RebirthConfig.bodyStats(BodyClass.PREMIUM_SYNTHETIC).armor().get();
        helper.assertTrue(Math.abs(body.getAttributeValue(Attributes.ARMOR) - base) < EPSILON, "No plates: the armor of the class");

        var access = helper.getLevel().registryAccess();
        Upgrades.install(access, body, new ItemStack(UpgradeRegistry.COPPER_PLATING.get()), null);
        Upgrades.install(access, body, new ItemStack(UpgradeRegistry.COPPER_PLATING.get()), null);
        double copper = RebirthConfig.plating("copper").armor().get();
        helper.assertTrue(Math.abs(body.getAttributeValue(Attributes.ARMOR) - (base + 2 * copper)) < EPSILON, "Two pieces count twice");

        Upgrades.remove(body, 0);
        Upgrades.remove(body, 0);
        helper.assertTrue(Math.abs(body.getAttributeValue(Attributes.ARMOR) - base) < EPSILON, "Without plates the armor goes back");
        Upgrades.install(access, body, new ItemStack(UpgradeRegistry.TITANIUM_PLATING.get()), null);
        helper.assertTrue(body.getAttributeValue(Attributes.ARMOR_TOUGHNESS) > 0.0, "Titanium also adds toughness");
        helper.assertTrue(RebirthConfig.plating("iron").armor().get() > copper
                && RebirthConfig.plating("titanium").armor().get() > RebirthConfig.plating("iron").armor().get(), "Better material, more armor");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void platingCountsOnAControlledBodyAndGoesAwayForAHuman(GameTestHelper helper) {
        ServerPlayer player = playerWith(helper, UpgradeRegistry.IRON_PLATING.get());
        TransferService.applyBodyModifiers(player, BodyClass.PREMIUM_SYNTHETIC);
        double expected = RebirthConfig.bodyStats(BodyClass.PREMIUM_SYNTHETIC).armor().get() + RebirthConfig.plating("iron").armor().get();
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.ARMOR) - expected) < EPSILON,
                "Class and plates, got " + player.getAttributeValue(Attributes.ARMOR));
        TransferService.removeBodyModifiers(player);
        helper.assertTrue(player.getAttributeValue(Attributes.ARMOR) < EPSILON, "A human has none of it");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void sealingKeepsTheAirUnderWaterOnlyWithTheModule(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 2, 0), new BlockPos(4, 4, 4))) {
            helper.setBlock(pos, Blocks.WATER);
        }
        ArtificialBodyEntity sealed = helper.spawn(BodyEntities.type(BodyClass.BASIC_ROBOT), new BlockPos(1, 2, 1));
        sealed.setUpgrades(upgrades(UpgradeRegistry.SEALING_MODULE.get()));
        ArtificialBodyEntity plain = helper.spawn(BodyEntities.type(BodyClass.BASIC_ROBOT), new BlockPos(3, 2, 3));
        int full = plain.getMaxAirSupply();
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(plain.getAirSupply() < full, "Without the module the air must run out");
            helper.assertTrue(sealed.getAirSupply() == full, "With the module it must not, got " + sealed.getAirSupply());
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- jetpack

    @GameTest(template = TEMPLATE)
    public static void heightIsMeasuredFromTheSurfaceUnderTheBody(GameTestHelper helper) {
        BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 2));
        Vec3 above = Vec3.atBottomCenterOf(floor.above(4));
        helper.assertTrue(Math.abs(Jetpack.heightAboveSurface(helper.getLevel(), above, 8) - 3.0) < EPSILON, "Three blocks above the stone floor");
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.WATER);
        helper.assertTrue(Math.abs(Jetpack.heightAboveSurface(helper.getLevel(), above, 8) - 2.0) < EPSILON, "Water counts as a surface");
        Vec3 high = Vec3.atBottomCenterOf(floor.above(30));
        helper.assertTrue(Jetpack.heightAboveSurface(helper.getLevel(), high, 8) > 8.0, "Too high above anything counts as above the limit");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void reserveIsSpentInFlightAndRechargesOnlyOnTheGround(GameTestHelper helper) {
        ServerPlayer player = playerWith(helper, UpgradeRegistry.JETPACK.get());
        BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 2));
        player.moveTo(Vec3.atBottomCenterOf(floor.above(4)));
        player.setOnGround(false);
        helper.assertTrue(Jetpack.equipped(player), "The player must have the jetpack");
        helper.assertTrue(Jetpack.reserve(player) == 1.0f, "It starts full");

        player.fallDistance = 5.0f;
        Jetpack.serverTick(player, true);
        float afterOne = Jetpack.reserve(player);
        helper.assertTrue(afterOne < 1.0f, "Flying must use the reserve");
        helper.assertTrue(player.fallDistance == 0.0f, "A flight is not a fall");

        Jetpack.serverTick(player, false);
        helper.assertTrue(Jetpack.reserve(player) == afterOne, "No flight in the air: no use and no recharge");

        int capacity = (int) Math.round(RebirthConfig.JETPACK_THRUST_SECONDS.get() * 20);
        for (int i = 0; i < capacity + 5; i++) {
            Jetpack.serverTick(player, true);
        }
        helper.assertTrue(Jetpack.reserve(player) == 0.0f, "It must run out");
        player.fallDistance = 5.0f;
        Jetpack.serverTick(player, true);
        helper.assertTrue(player.fallDistance == 5.0f, "Empty: an ordinary fall, with its damage");

        player.setOnGround(true);
        Jetpack.serverTick(player, false);
        helper.assertTrue(Jetpack.reserve(player) > 0.0f, "On the ground it must recharge");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aFallAfterAFlightCountsOnceEvenIfTheGameForgetsIt(GameTestHelper helper) {
        ServerPlayer player = playerWith(helper, UpgradeRegistry.JETPACK.get());
        BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 2));
        player.moveTo(Vec3.atBottomCenterOf(floor.above(6)));
        player.setOnGround(false);
        Jetpack.serverTick(player, true);
        helper.assertTrue(Jetpack.watchingFall(player), "After a flight the fall is watched");
        Jetpack.serverTick(player, false);
        helper.assertTrue(Jetpack.watchingFall(player), "Still in the air");
        player.moveTo(Vec3.atBottomCenterOf(floor.above(1)));
        player.setOnGround(true);
        player.fallDistance = 4.0f;
        Jetpack.serverTick(player, false);
        helper.assertFalse(Jetpack.watchingFall(player), "On the ground it is over");
        helper.assertTrue(player.fallDistance == 0.0f, "The game must not count the same fall again");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void noFlightAboveTheLimit(GameTestHelper helper) {
        ServerPlayer player = playerWith(helper, UpgradeRegistry.JETPACK.get());
        BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 2));
        player.moveTo(Vec3.atBottomCenterOf(floor.above(RebirthConfig.JETPACK_MAX_HEIGHT.get() + 10)));
        player.setOnGround(false);
        player.fallDistance = 5.0f;
        Jetpack.serverTick(player, true);
        helper.assertTrue(Jetpack.reserve(player) == 1.0f, "High above the limit the server does not count it as a flight");
        helper.assertTrue(player.fallDistance == 5.0f, "Falling stays falling");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void noJetpackNoReserveUse(GameTestHelper helper) {
        ServerPlayer player = playerWith(helper, UpgradeRegistry.SEALING_MODULE.get());
        helper.assertFalse(Jetpack.equipped(player), "A sealing module is no jetpack");
        helper.succeed();
    }
}
