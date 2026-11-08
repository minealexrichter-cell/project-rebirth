package com.projectrebirth.gametest;

import java.util.List;
import java.util.Optional;

import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.BodyAttachments;
import com.projectrebirth.bodies.BodyBlocks;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyEntities;
import com.projectrebirth.bodies.Jetpack;
import com.projectrebirth.bodies.PossessionState;
import com.projectrebirth.bodies.TransferCapsuleBlock;
import com.projectrebirth.bodies.TransferService;
import com.projectrebirth.bodies.power.BodyHeat;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.powerarmor.PowerArmorRegistry;
import com.projectrebirth.upgrades.InstalledUpgrades;
import com.projectrebirth.upgrades.UpgradeRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Version 1.5.0, block B: heat of artificial bodies. {@code .\gradlew runGameTestServer -PgameTests=projectrebirth_power}. */
@GameTestHolder(PlanTests.POWER)
@PrefixGameTestTemplate(false)
public class BodyHeatGameTests {
    private static final String TEMPLATE = "floor_5x5";
    private static final BlockPos POS = new BlockPos(2, 2, 2);

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
        BodyHeat.tickSecond(player); // remembers where it stands
        return player;
    }

    @GameTest(template = TEMPLATE)
    public static void runningHeatsMoreThanResting(GameTestHelper helper) {
        ServerPlayer runner = playerIn(helper, BodyClass.BASIC_ROBOT);
        ServerPlayer rester = playerIn(helper, BodyClass.BASIC_ROBOT);
        BodyHeat.setHeat(runner, 50.0f);
        BodyHeat.setHeat(rester, 50.0f);
        runner.setSprinting(true);
        runner.moveTo(runner.getX() + 4.0, runner.getY(), runner.getZ());
        BodyHeat.tickSecond(runner);
        BodyHeat.tickSecond(rester);
        helper.assertTrue(BodyHeat.heat(runner) > BodyHeat.heat(rester), "Running: " + BodyHeat.heat(runner) + ", resting: " + BodyHeat.heat(rester));
        helper.assertTrue(BodyHeat.heat(rester) < 50.0f, "Rest cools");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theJetpackAndBlowsHeat(GameTestHelper helper) {
        ServerPlayer player = playerIn(helper, BodyClass.BASIC_ROBOT, UpgradeRegistry.JETPACK.get());
        player.moveTo(player.getX(), player.getY() + 2.0, player.getZ());
        player.setOnGround(false);
        for (int i = 0; i < 20; i++) {
            Jetpack.serverTick(player, true);
        }
        float afterFlight = BodyHeat.heat(player);
        helper.assertTrue(Math.abs(afterFlight - RebirthConfig.HEAT_JETPACK.get()) < 0.05, "A second of thrust: " + afterFlight);
        BodyHeat.add(player, RebirthConfig.HEAT_ATTACK.get());
        helper.assertTrue(BodyHeat.heat(player) > afterFlight, "A blow heats");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void fireAroundHeats(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.LAVA);
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.MAGMA_BLOCK);
        double heat = BodyHeat.fireHeat(helper.getLevel(), helper.absolutePos(POS));
        helper.assertTrue(heat >= 2 * RebirthConfig.HEAT_NEAR_FIRE.get() - 1.0e-6, "Lava and magma around: " + heat);
        helper.assertTrue(BodyHeat.fireHeat(helper.getLevel(), helper.absolutePos(POS).above(10)) == 0.0, "Nothing far away");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aHotBiomeHeats(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(POS);
        double before = BodyHeat.climateHeat(helper.getLevel(), at);
        helper.getLevel().getServer().getCommands().performPrefixedCommand(helper.getLevel().getServer().createCommandSourceStack().withSuppressedOutput(),
                // the game blends neighbouring biome cells when it reads a biome, so fill a few cells all round
                "fillbiome " + (at.getX() - 8) + " " + Math.max(at.getY() - 2, helper.getLevel().getMinBuildHeight()) + " " + (at.getZ() - 8) + " " + (at.getX() + 8) + " " + (at.getY() + 8) + " " + (at.getZ() + 8) + " minecraft:desert");
        double desert = BodyHeat.climateHeat(helper.getLevel(), at);
        helper.assertTrue(desert > before && desert > 0.0, "A desert heats: " + before + " -> " + desert);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void waterCoolsAStandingBodyFast(GameTestHelper helper) {
        ArtificialBodyEntity dry = helper.spawn(BodyEntities.type(BodyClass.BASIC_ROBOT), new BlockPos(1, 2, 1));
        // glass all round, so that the water stays with the wet body
        for (int y = 2; y <= 3; y++) {
            for (BlockPos side : List.of(new BlockPos(2, y, 3), new BlockPos(4, y, 3), new BlockPos(3, y, 2), new BlockPos(3, y, 4))) {
                helper.setBlock(side, Blocks.GLASS);
            }
            helper.setBlock(new BlockPos(3, y, 3), Blocks.WATER);
        }
        ArtificialBodyEntity wet = helper.spawn(BodyEntities.type(BodyClass.BASIC_ROBOT), new BlockPos(3, 2, 3));
        dry.setHeat(80.0f);
        wet.setHeat(80.0f);
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(dry.heat() < 80.0f, "A standing body cools: " + dry.heat());
            helper.assertTrue(wet.heat() < dry.heat() - 5.0f, "Water cools much faster: " + wet.heat() + " vs " + dry.heat() + "; wet in water " + wet.isInWater() + ", dry in rain " + dry.isInWaterOrRain() + ", raining " + helper.getLevel().isRaining() + ", sky " + helper.getLevel().canSeeSky(dry.blockPosition().above()) + ", ticks " + dry.tickCount);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void aHotBodyThrottles(GameTestHelper helper) {
        ServerPlayer player = playerIn(helper, BodyClass.BASIC_ROBOT, UpgradeRegistry.JETPACK.get());
        double speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double attack = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        BodyHeat.setHeat(player, 75.0f);
        BodyHeat.applyLimits(player);
        helper.assertTrue(BodyHeat.state(player) == BodyHeat.State.WARM, "75 is a warning only");
        helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) == speed, "A warning does not slow down");

        BodyHeat.setHeat(player, 90.0f);
        BodyHeat.applyLimits(player);
        helper.assertTrue(BodyHeat.isThrottled(player), "90 throttles");
        helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) < speed, "Throttled: slower");
        helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_DAMAGE) < attack, "Throttled: weaker blows");
        player.moveTo(player.getX(), player.getY() + 2.0, player.getZ());
        player.setOnGround(false);
        Jetpack.serverTick(player, true);
        helper.assertTrue(player.getData(UpgradeRegistry.JETPACK_USED) == 0.0f, "Throttled: no jetpack");
        helper.assertTrue(TransferService.state(player).body().isPresent(), "The mind stays in the body");

        BodyHeat.setHeat(player, 50.0f);
        BodyHeat.applyLimits(player);
        helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) == speed, "Cool again: full speed");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void anOverheatedBodyStaysWeakUntilItCoolsBelowTheThrottleLevel(GameTestHelper helper) {
        ServerPlayer player = playerIn(helper, BodyClass.BASIC_ROBOT);
        float health = player.getHealth();
        BodyHeat.setHeat(player, 100.0f);
        BodyHeat.tickSecond(player);
        helper.assertTrue(BodyHeat.isOverheated(player), "100 overheats");
        helper.assertTrue(player.getHealth() == health && player.isAlive(), "Overheating does not destroy the body");
        BodyHeat.setHeat(player, 90.0f);
        BodyHeat.tickSecond(player);
        helper.assertTrue(BodyHeat.isOverheated(player), "Still overheated above the throttle level");
        BodyHeat.setHeat(player, (float) (RebirthConfig.HEAT_THROTTLE.get() - 1.0));
        BodyHeat.tickSecond(player);
        helper.assertFalse(BodyHeat.isOverheated(player), "Below the throttle level it is over");
        helper.assertFalse(BodyHeat.isThrottled(player), "And no longer throttled");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theCoolingModuleAndBetterClassesCoolFaster(GameTestHelper helper) {
        ServerPlayer basic = playerIn(helper, BodyClass.BASIC_ROBOT);
        ServerPlayer cooled = playerIn(helper, BodyClass.BASIC_ROBOT, UpgradeRegistry.COOLING_MODULE.get());
        ServerPlayer premium = playerIn(helper, BodyClass.PREMIUM_SYNTHETIC);
        for (ServerPlayer player : List.of(basic, cooled, premium)) {
            BodyHeat.setHeat(player, 50.0f);
            BodyHeat.tickSecond(player);
        }
        helper.assertTrue(BodyHeat.heat(cooled) < BodyHeat.heat(basic), "The module cools: " + BodyHeat.heat(cooled) + " vs " + BodyHeat.heat(basic));
        helper.assertTrue(BodyHeat.heat(premium) < BodyHeat.heat(basic), "The premium body cools faster: " + BodyHeat.heat(premium) + " vs " + BodyHeat.heat(basic));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aBodyInPowerArmorDoesNotHeatUp(GameTestHelper helper) {
        ServerPlayer player = playerIn(helper, BodyClass.BASIC_ROBOT);
        PowerArmorEntity armor = helper.spawn(PowerArmorRegistry.POWER_ARMOR.get(), POS);
        player.startRiding(armor, true);
        BodyHeat.add(player, 30.0);
        helper.assertTrue(BodyHeat.heat(player) == 0.0f, "No heat from work in a power armor");
        helper.succeed();
    }

    @GameTest(template = "floor_3x3", batch = "power_transfer")
    public static void theHeatGoesIntoAndOutOfTheBody(GameTestHelper helper) {
        BlockPos capsule = new BlockPos(0, 2, 0);
        BlockState lower = BodyBlocks.TRANSFER_CAPSULE.get().defaultBlockState()
                .setValue(TransferCapsuleBlock.FACING, Direction.NORTH).setValue(TransferCapsuleBlock.HALF, DoubleBlockHalf.LOWER);
        helper.setBlock(capsule, lower);
        helper.setBlock(capsule.above(), lower.setValue(TransferCapsuleBlock.HALF, DoubleBlockHalf.UPPER));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos standAt = helper.absolutePos(new BlockPos(1, 2, 0));
        player.moveTo(standAt.getX() + 0.5, standAt.getY(), standAt.getZ() + 0.5);
        ArtificialBodyEntity body = helper.spawn(BodyEntities.type(BodyClass.IMPROVED_ROBOT), new BlockPos(2, 2, 2));
        body.setOwner(player.getUUID());
        body.setHeat(40.0f);
        helper.assertTrue(TransferService.transferFromCapsule(player, helper.absolutePos(capsule), Direction.NORTH)
                == TransferService.TransferResult.SUCCESS, "Transfer must succeed");
        helper.assertTrue(BodyHeat.heat(player) == 40.0f, "Heat after entering: " + BodyHeat.heat(player));
        BodyHeat.setHeat(player, 60.0f);
        helper.assertTrue(TransferService.returnToHumanBody(player) == TransferService.ReturnResult.SUCCESS, "Return must succeed");
        List<ArtificialBodyEntity> standing = helper.getLevel().getEntitiesOfClass(ArtificialBodyEntity.class,
                new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(6), ArtificialBodyEntity::isAlive);
        helper.assertTrue(standing.size() == 1 && standing.get(0).heat() == 60.0f, "The body left behind keeps its heat");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "heat_off")
    public static void withTheSystemOffNothingHeatsOrThrottles(GameTestHelper helper) {
        ServerPlayer player = playerIn(helper, BodyClass.BASIC_ROBOT);
        BodyHeat.setHeat(player, 95.0f);
        RebirthConfig.BODY_HEAT_ENABLED.set(false);
        BodyHeat.applyLimits(player);
        boolean throttled = BodyHeat.isThrottled(player);
        BodyHeat.setHeat(player, 0.0f);
        BodyHeat.add(player, 50.0);
        float heat = BodyHeat.heat(player);
        RebirthConfig.BODY_HEAT_ENABLED.set(true);
        helper.assertFalse(throttled, "Off: never throttled");
        helper.assertTrue(heat == 0.0f, "Off: nothing heats");
        helper.succeed();
    }
}
