package com.projectrebirth.gametest;

import java.util.List;
import java.util.Optional;

import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.BodyAttachments;
import com.projectrebirth.bodies.BodyBlocks;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyEntities;
import com.projectrebirth.bodies.BodyEvents;
import com.projectrebirth.bodies.BodyRules;
import com.projectrebirth.bodies.Jetpack;
import com.projectrebirth.bodies.PossessionState;
import com.projectrebirth.bodies.TransferCapsuleBlock;
import com.projectrebirth.bodies.TransferService;
import com.projectrebirth.bodies.power.BodyBatteryItem;
import com.projectrebirth.bodies.power.BodyPower;
import com.projectrebirth.bodies.power.BodyPowerRegistry;
import com.projectrebirth.bodies.power.ChargingPadBlockEntity;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.upgrades.InstalledUpgrades;
import com.projectrebirth.upgrades.UpgradeRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Version 1.5.0, block A: the charge of artificial bodies. Run alone with
 * {@code .\gradlew runGameTestServer -PgameTests=projectrebirth_power}.
 */
@GameTestHolder(PlanTests.POWER)
@PrefixGameTestTemplate(false)
public class BodyPowerGameTests {
    private static final String TEMPLATE = "floor_5x5";
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    private static final float EPSILON = 0.01f;

    private static ServerPlayer playerIn(GameTestHelper helper, BodyClass bodyClass) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setData(BodyAttachments.POSSESSION, PossessionState.of(bodyClass));
        player.setData(UpgradeRegistry.CONTROLLED_BODY, InstalledUpgrades.EMPTY);
        TransferService.applyBodyModifiers(player, bodyClass);
        return player;
    }

    private static void near(GameTestHelper helper, String what, float expected, float actual) {
        helper.assertTrue(Math.abs(expected - actual) < EPSILON, what + ": expected " + expected + ", got " + actual);
    }

    private static int capacity(BodyClass bodyClass) {
        return RebirthConfig.bodyStats(bodyClass).energyCapacity().get();
    }

    // ---------------------------------------------------------------- where the charge lives

    @GameTest(template = TEMPLATE)
    public static void aNewOrOldBodyIsFull(GameTestHelper helper) {
        ArtificialBodyEntity body = helper.spawn(BodyEntities.type(BodyClass.BASIC_ROBOT), POS);
        near(helper, "a body without a saved charge (new, or from 1.4.1)", capacity(BodyClass.BASIC_ROBOT), body.charge());
        ServerPlayer player = playerIn(helper, BodyClass.ADVANCED_SYNTHETIC);
        near(helper, "a player who was in a body in 1.4.1", capacity(BodyClass.ADVANCED_SYNTHETIC), BodyPower.charge(player));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theChargeIsSavedWithTheBody(GameTestHelper helper) {
        ArtificialBodyEntity body = helper.spawn(BodyEntities.type(BodyClass.IMPROVED_ROBOT), POS);
        body.setCharge(1234.0f);
        CompoundTag tag = new CompoundTag();
        body.addAdditionalSaveData(tag);
        ArtificialBodyEntity loaded = helper.spawn(BodyEntities.type(BodyClass.IMPROVED_ROBOT), POS.east());
        loaded.readAdditionalSaveData(tag);
        near(helper, "charge after saving and loading", 1234.0f, loaded.charge());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aPackedBodyKeepsItsCharge(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        ArtificialBodyEntity body = helper.spawn(BodyEntities.type(BodyClass.BASIC_ROBOT), POS);
        body.setOwner(player.getUUID());
        body.setCharge(7000.0f);
        player.setShiftKeyDown(true);
        body.interact(player, InteractionHand.MAIN_HAND);
        player.setShiftKeyDown(false);
        helper.assertTrue(body.isRemoved(), "The body must be packed");
        ItemStack stack = player.getInventory().items.stream().filter(item -> item.is(com.projectrebirth.bodies.BodyItems.item(BodyClass.BASIC_ROBOT))).findFirst().orElse(ItemStack.EMPTY);
        helper.assertFalse(stack.isEmpty(), "The packed body must be in the inventory: " + player.getInventory().items.stream().filter(item -> !item.isEmpty()).toList());
        Float stored = stack.get(BodyPowerRegistry.STORED_CHARGE.get());
        helper.assertTrue(stored != null, "The item must carry the charge");
        near(helper, "charge on the item", 7000.0f, stored);

        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos floor = helper.absolutePos(POS.below());
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)));
        List<ArtificialBodyEntity> placed = helper.getLevel().getEntitiesOfClass(ArtificialBodyEntity.class,
                new AABB(helper.absolutePos(POS)).inflate(3), ArtificialBodyEntity::isAlive);
        helper.assertTrue(placed.size() == 1, "One body must stand again, found " + placed.size());
        near(helper, "charge of the placed body", 7000.0f, placed.get(0).charge());
        helper.succeed();
    }

    @GameTest(template = "floor_3x3", batch = "power_transfer")
    public static void theChargeGoesIntoAndOutOfTheBody(GameTestHelper helper) {
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
        body.setCharge(12345.0f);

        helper.assertTrue(TransferService.transferFromCapsule(player, helper.absolutePos(capsule), Direction.NORTH)
                == TransferService.TransferResult.SUCCESS, "Transfer must succeed");
        near(helper, "charge after entering", 12345.0f, BodyPower.charge(player));
        BodyPower.setCharge(player, 5000.0f);
        helper.assertTrue(TransferService.returnToHumanBody(player) == TransferService.ReturnResult.SUCCESS, "Return must succeed");
        helper.assertTrue(player.getData(BodyPowerRegistry.CONTROLLED_CHARGE) < 0.0f, "A human keeps no charge");
        List<ArtificialBodyEntity> standing = helper.getLevel().getEntitiesOfClass(ArtificialBodyEntity.class,
                new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(6), ArtificialBodyEntity::isAlive);
        helper.assertTrue(standing.size() == 1, "The body must stand again, found " + standing.size());
        near(helper, "charge of the body left behind", 5000.0f, standing.get(0).charge());
        helper.succeed();
    }

    // ---------------------------------------------------------------- use

    @GameTest(template = TEMPLATE)
    public static void workSpendsChargeAndBetterBodiesSpendLess(GameTestHelper helper) {
        ServerPlayer basic = playerIn(helper, BodyClass.BASIC_ROBOT);
        ServerPlayer premium = playerIn(helper, BodyClass.PREMIUM_SYNTHETIC);
        BodyPower.tickSecond(basic);
        BodyPower.tickSecond(premium);
        float idleBasic = capacity(BodyClass.BASIC_ROBOT) - BodyPower.charge(basic);
        float idlePremium = capacity(BodyClass.PREMIUM_SYNTHETIC) - BodyPower.charge(premium);
        near(helper, "a second of the basic body", RebirthConfig.BODY_ENERGY_IDLE.get().floatValue(), idleBasic);
        helper.assertTrue(idlePremium < idleBasic, "The premium body must spend less: " + idlePremium + " vs " + idleBasic);

        float before = BodyPower.charge(basic);
        basic.moveTo(basic.getX() + 3.0, basic.getY(), basic.getZ());
        BodyPower.tickSecond(basic);
        float walked = before - BodyPower.charge(basic);
        near(helper, "a second of walking", (float) (RebirthConfig.BODY_ENERGY_IDLE.get() + RebirthConfig.BODY_ENERGY_WALK.get()), walked);

        before = BodyPower.charge(basic);
        BodyPower.spend(basic, RebirthConfig.BODY_ENERGY_ATTACK.get());
        near(helper, "a blow", RebirthConfig.BODY_ENERGY_ATTACK.get().floatValue(), before - BodyPower.charge(basic));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void lowChargeSlowsDown(GameTestHelper helper) {
        ServerPlayer player = playerIn(helper, BodyClass.BASIC_ROBOT);
        double normal = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        BodyPower.setCharge(player, capacity(BodyClass.BASIC_ROBOT) * 0.1f);
        BodyPower.applyLimits(player);
        helper.assertTrue(BodyPower.state(player) == BodyPower.State.LOW, "10% is low");
        double slow = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(Math.abs(slow - normal * (1.0 - RebirthConfig.BODY_ENERGY_LOW_SLOWDOWN.get())) < 1.0e-6,
                "Low charge slows down: " + normal + " -> " + slow);
        helper.assertTrue(player.getAttributeValue(Attributes.JUMP_STRENGTH) > 0.0, "A low body still jumps");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void emergencyModeLimitsTheBodyButKeepsControl(GameTestHelper helper) {
        ServerPlayer player = playerIn(helper, BodyClass.ADVANCED_SYNTHETIC);
        player.setData(UpgradeRegistry.CONTROLLED_BODY, InstalledUpgrades.EMPTY.with(
                new InstalledUpgrades.Installed(Optional.empty(), new ItemStack(UpgradeRegistry.JETPACK.get()))));
        double normal = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        BodyPower.setCharge(player, 0.0f);
        BodyPower.applyLimits(player);
        BodyEvents.tickControlledBody(player);

        helper.assertTrue(BodyPower.isEmpty(player), "No charge is the emergency mode");
        helper.assertTrue(TransferService.state(player).body().isPresent(), "The mind stays in the body");
        double slow = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(slow > 0.0 && slow < normal, "Slow walking, not standing still: " + normal + " -> " + slow);
        helper.assertTrue(player.getAttributeValue(Attributes.JUMP_STRENGTH) <= 0.0, "No jumping");
        helper.assertTrue(player.getFoodData().getFoodLevel() <= 6, "The food level that stops running");

        player.moveTo(player.getX(), player.getY() + 2.0, player.getZ());
        player.setOnGround(false);
        float used = player.getData(UpgradeRegistry.JETPACK_USED);
        Jetpack.serverTick(player, true);
        helper.assertTrue(player.getData(UpgradeRegistry.JETPACK_USED) == used, "No jetpack thrust without charge");

        player.setHealth(player.getMaxHealth() - 5.0f);
        player.setData(BodyAttachments.LAST_DAMAGE, helper.getLevel().getGameTime() - 100_000L);
        float health = player.getHealth();
        BodyRules.tickSelfRepair(player, BodyClass.ADVANCED_SYNTHETIC);
        helper.assertTrue(player.getHealth() == health, "No self-repair without charge");

        BodyPower.setCharge(player, 1000.0f);
        BodyPower.applyLimits(player);
        BodyEvents.tickControlledBody(player);
        helper.assertTrue(player.getAttributeValue(Attributes.JUMP_STRENGTH) > 0.0, "With charge it jumps again");
        helper.assertTrue(player.getFoodData().getFoodLevel() > 6, "With charge it may run again");
        BodyRules.tickSelfRepair(player, BodyClass.ADVANCED_SYNTHETIC);
        helper.assertTrue(player.getHealth() > health, "With charge it mends itself");
        helper.assertTrue(BodyPower.charge(player) < 1000.0f, "Self-repair costs charge");
        helper.succeed();
    }

    // ---------------------------------------------------------------- refilling

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void thePadChargesBodiesAndTheBatteryInIt(GameTestHelper helper) {
        BlockPos padPos = new BlockPos(2, 1, 2);
        helper.setBlock(padPos, BodyPowerRegistry.CHARGING_PAD.get().defaultBlockState());
        ChargingPadBlockEntity pad = (ChargingPadBlockEntity) helper.getBlockEntity(padPos);
        pad.energy().generate(pad.energy().getMaxEnergyStored());
        ItemStack battery = new ItemStack(BodyPowerRegistry.BODY_BATTERY.get());
        pad.setBattery(battery);
        ArtificialBodyEntity body = helper.spawn(BodyEntities.type(BodyClass.BASIC_ROBOT), padPos.above());
        body.setCharge(0.0f);
        ServerPlayer player = playerIn(helper, BodyClass.BASIC_ROBOT);
        BlockPos above = helper.absolutePos(padPos.above());
        player.moveTo(above.getX() + 0.8, above.getY() - 0.8, above.getZ() + 0.8);
        BodyPower.setCharge(player, 0.0f);
        int energyBefore = pad.energy().getEnergyStored();
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(body.charge() > 0.0f, "The standing body must be charged");
            helper.assertTrue(BodyPower.charge(player) > 0.0f, "The controlled body must be charged");
            helper.assertTrue(BodyBatteryItem.stored(pad.battery()) > 0, "The battery in the pad must be charged");
            helper.assertTrue(pad.energy().getEnergyStored() < energyBefore, "The pad must spend its energy");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void aPadWithoutEnergyChargesNothing(GameTestHelper helper) {
        BlockPos padPos = new BlockPos(2, 1, 2);
        helper.setBlock(padPos, BodyPowerRegistry.CHARGING_PAD.get().defaultBlockState());
        ArtificialBodyEntity body = helper.spawn(BodyEntities.type(BodyClass.BASIC_ROBOT), padPos.above());
        body.setCharge(0.0f);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(body.charge() == 0.0f, "No energy, no charge");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void theBodyBatteryHandsOverItsCharge(GameTestHelper helper) {
        ServerPlayer player = playerIn(helper, BodyClass.BASIC_ROBOT);
        BodyPower.setCharge(player, 1000.0f);
        ItemStack battery = new ItemStack(BodyPowerRegistry.BODY_BATTERY.get());
        BodyBatteryItem.setStored(battery, 30_000);
        battery.finishUsingItem(helper.getLevel(), player);
        near(helper, "charge after the battery", 31_000.0f, BodyPower.charge(player));
        helper.assertTrue(BodyBatteryItem.stored(battery) == 0, "The battery gave all it had");

        BodyPower.setCharge(player, capacity(BodyClass.BASIC_ROBOT) - 500.0f);
        BodyBatteryItem.setStored(battery, 30_000);
        battery.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(BodyBatteryItem.stored(battery) == 29_500, "Only what the body was missing, left " + BodyBatteryItem.stored(battery));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theExtraBatteryRaisesTheCapacity(GameTestHelper helper) {
        InstalledUpgrades withBattery = InstalledUpgrades.EMPTY.with(
                new InstalledUpgrades.Installed(Optional.empty(), new ItemStack(UpgradeRegistry.EXTRA_BATTERY.get())));
        float plain = BodyPower.capacity(helper.getLevel().registryAccess(), BodyClass.HIGHTECH_SYNTHETIC, InstalledUpgrades.EMPTY);
        float more = BodyPower.capacity(helper.getLevel().registryAccess(), BodyClass.HIGHTECH_SYNTHETIC, withBattery);
        near(helper, "the extra battery", RebirthConfig.EXTRA_BATTERY_CAPACITY.get().floatValue(), more - plain);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "power_off")
    public static void withTheSystemOffNothingIsSpentOrLimited(GameTestHelper helper) {
        ServerPlayer player = playerIn(helper, BodyClass.BASIC_ROBOT);
        BodyPower.setCharge(player, 0.0f);
        RebirthConfig.BODY_ENERGY_ENABLED.set(false);
        BodyPower.applyLimits(player);
        boolean empty = BodyPower.isEmpty(player);
        double jump = player.getAttributeValue(Attributes.JUMP_STRENGTH);
        BodyPower.setCharge(player, 100.0f);
        BodyPower.tickSecond(player);
        float charge = BodyPower.charge(player);
        RebirthConfig.BODY_ENERGY_ENABLED.set(true);
        helper.assertFalse(empty, "Off: never the emergency mode");
        helper.assertTrue(jump > 0.0, "Off: it jumps");
        near(helper, "Off: nothing is spent", 100.0f, charge);
        helper.succeed();
    }
}
