package com.projectrebirth.gametest;

import java.util.List;

import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.BodyAttachments;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyEntities;
import com.projectrebirth.bodies.BodyItems;
import com.projectrebirth.bodies.BodyRules;
import com.projectrebirth.bodies.PossessionState;
import com.projectrebirth.bodies.TransferService;
import com.projectrebirth.bodies.power.BodyHeat;
import com.projectrebirth.bodies.power.BodyPower;
import com.projectrebirth.bodies.power.BodyPowerRegistry;
import com.projectrebirth.bodies.power.BodyWear;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.technology.Maintenance;
import com.projectrebirth.technology.TechItems;
import com.projectrebirth.upgrades.InstalledUpgrades;
import com.projectrebirth.upgrades.UpgradeRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Version 1.5.0, block B2: wear of artificial bodies. {@code .\gradlew runGameTestServer -PgameTests=projectrebirth_power}. */
@GameTestHolder(PlanTests.POWER)
@PrefixGameTestTemplate(false)
public class BodyWearGameTests {
    private static final String TEMPLATE = "floor_5x5";
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    private static final float EPSILON = 0.001f;

    private static ServerPlayer playerIn(GameTestHelper helper, BodyClass bodyClass) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setData(BodyAttachments.POSSESSION, PossessionState.of(bodyClass));
        player.setData(UpgradeRegistry.CONTROLLED_BODY, InstalledUpgrades.EMPTY);
        TransferService.applyBodyModifiers(player, bodyClass);
        BlockPos at = helper.absolutePos(POS);
        player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return player;
    }

    @GameTest(template = TEMPLATE)
    public static void workWearsAndBetterBodiesWearLess(GameTestHelper helper) {
        ServerPlayer basic = playerIn(helper, BodyClass.BASIC_ROBOT);
        ServerPlayer premium = playerIn(helper, BodyClass.PREMIUM_SYNTHETIC);
        BodyWear.tickSecond(basic);
        BodyWear.tickSecond(premium);
        helper.assertTrue(Math.abs(BodyWear.wear(basic) - RebirthConfig.WEAR_WORK.get()) < 1.0e-5, "A second of work: " + BodyWear.wear(basic));
        helper.assertTrue(BodyWear.wear(premium) < BodyWear.wear(basic), "The premium body wears less");
        double perDay = RebirthConfig.WEAR_WORK.get() * 1200.0;
        helper.assertTrue(perDay > 9.0 && perDay < 11.0, "About 10% a game day for the basic body: " + perDay);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void anOverheatedBodyWearsFast(GameTestHelper helper) {
        ServerPlayer player = playerIn(helper, BodyClass.BASIC_ROBOT);
        BodyHeat.setHeat(player, 100.0f);
        BodyHeat.tickSecond(player);
        BodyWear.tickSecond(player);
        float worn = BodyWear.wear(player);
        helper.assertTrue(worn >= RebirthConfig.WEAR_OVERHEAT.get().floatValue(), "Overheated: fast wear, got " + worn);
        helper.assertTrue(player.getHealth() == player.getMaxHealth(), "Wear is not integrity");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theStagesOfWear(GameTestHelper helper) {
        ServerPlayer player = playerIn(helper, BodyClass.ADVANCED_SYNTHETIC);
        double speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double attack = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float maxHealth = player.getMaxHealth();

        BodyWear.setWear(player, 25.0f);
        BodyWear.applyLimits(player);
        float before = BodyPower.charge(player);
        BodyPower.spend(player, 100.0);
        float spent = before - BodyPower.charge(player);
        float expected = (float) (100.0 * BodyPower.useMultiplier(BodyClass.ADVANCED_SYNTHETIC) * (1.0 + RebirthConfig.WEAR_ENERGY_AT_25.get()));
        helper.assertTrue(Math.abs(spent - expected) < 0.01f, "25%: more charge, spent " + spent + ", expected " + expected);
        helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) == speed, "25%: same speed");

        BodyWear.setWear(player, 50.0f);
        BodyWear.applyLimits(player);
        helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) < speed, "50%: slower");
        helper.assertTrue(BodyWear.selfRepairMultiplier(player) == RebirthConfig.WEAR_SELF_REPAIR_AT_50.get(), "50%: half the self-repair");
        helper.assertTrue(BodyWear.heatMultiplier(player) > 1.0, "50%: faster heating");
        helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_DAMAGE) == attack, "50%: same blows");

        BodyWear.setWear(player, 75.0f);
        BodyWear.applyLimits(player);
        helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_DAMAGE) < attack, "75%: weaker blows");

        BodyWear.setWear(player, 100.0f);
        BodyWear.applyLimits(player);
        helper.assertTrue(BodyWear.isCritical(player), "100% is critical");
        helper.assertTrue(Math.abs(player.getMaxHealth() - maxHealth * (1.0 - RebirthConfig.WEAR_MAX_HEALTH_AT_100.get())) < 0.01,
                "100%: half the maximum integrity, got " + player.getMaxHealth());
        helper.assertTrue(player.getHealth() <= player.getMaxHealth(), "Integrity is cut down to the new maximum");
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed * (1.0 - RebirthConfig.WEAR_SLOWDOWN_AT_100.get())) < 1.0e-6,
                "100%: much slower");
        player.setHealth(1.0f);
        player.setData(BodyAttachments.LAST_DAMAGE, helper.getLevel().getGameTime() - 100_000L);
        BodyRules.tickSelfRepair(player, BodyClass.ADVANCED_SYNTHETIC);
        helper.assertTrue(player.getHealth() == 1.0f, "100%: no self-repair");
        helper.assertTrue(TransferService.state(player).body().isPresent() && player.isAlive(), "Control stays, nothing is destroyed");

        BodyWear.setWear(player, 0.0f);
        BodyWear.applyLimits(player);
        helper.assertTrue(player.getMaxHealth() == maxHealth && player.getAttributeValue(Attributes.MOVEMENT_SPEED) == speed, "Serviced: all back");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theRepairKitDoesNotTakeWearOff(GameTestHelper helper) {
        ServerPlayer player = playerIn(helper, BodyClass.BASIC_ROBOT);
        BodyWear.setWear(player, 40.0f);
        player.setHealth(5.0f);
        new ItemStack(BodyItems.REPAIR_KIT.get()).finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(player.getHealth() > 5.0f, "The kit mends integrity");
        helper.assertTrue(BodyWear.wear(player) == 40.0f, "But not wear");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theMaintenanceStationTakesWearOff(GameTestHelper helper) {
        ItemStack robot = new ItemStack(BodyItems.item(BodyClass.BASIC_ROBOT));
        robot.set(BodyPowerRegistry.STORED_WEAR.get(), 25.0f);
        helper.assertTrue(Maintenance.needsRepair(robot), "A worn body needs maintenance");
        helper.assertTrue(Maintenance.materialFor(robot, item -> item == Items.IRON_INGOT) == null, "Iron does not take wear off");
        helper.assertTrue(Maintenance.materialFor(robot, item -> item == TechItems.ACTUATOR.get()) == TechItems.ACTUATOR.get(), "Actuators do, for robots");
        Maintenance.apply(robot, TechItems.ACTUATOR.get());
        float step = RebirthConfig.MAINTENANCE_BODY_WEAR_PER_ITEM.get().floatValue();
        helper.assertTrue(Math.abs(Maintenance.bodyWear(robot) - (25.0f - step)) < EPSILON, "One step: " + Maintenance.bodyWear(robot));
        Maintenance.apply(robot, TechItems.ACTUATOR.get());
        Maintenance.apply(robot, TechItems.ACTUATOR.get());
        helper.assertFalse(robot.has(BodyPowerRegistry.STORED_WEAR.get()), "No wear left");
        helper.assertFalse(Maintenance.needsRepair(robot), "Done");

        ItemStack synthetic = new ItemStack(BodyItems.item(BodyClass.ADVANCED_SYNTHETIC));
        synthetic.set(BodyPowerRegistry.STORED_WEAR.get(), 30.0f);
        helper.assertTrue(Maintenance.materialFor(synthetic, item -> item == TechItems.CIRCUIT_BOARD.get()) == TechItems.CIRCUIT_BOARD.get(),
                "Circuit boards for synthetic bodies");
        synthetic.set(com.projectrebirth.bodies.BodyComponents.STORED_INTEGRITY.get(), 10.0f);
        helper.assertTrue(Maintenance.materialFor(synthetic, item -> true) == TechItems.SYNTHETIC_FIBER.get(), "Integrity first");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void wearTravelsWithThePackedBody(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        ArtificialBodyEntity body = helper.spawn(BodyEntities.type(BodyClass.BASIC_ROBOT), POS);
        body.setOwner(player.getUUID());
        body.setWear(37.0f);
        player.setShiftKeyDown(true);
        body.interact(player, InteractionHand.MAIN_HAND);
        player.setShiftKeyDown(false);
        ItemStack stack = player.getInventory().items.stream().filter(item -> item.is(BodyItems.item(BodyClass.BASIC_ROBOT))).findFirst().orElse(ItemStack.EMPTY);
        helper.assertTrue(stack.getOrDefault(BodyPowerRegistry.STORED_WEAR.get(), 0.0f) == 37.0f, "The item carries the wear");
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos floor = helper.absolutePos(POS.below());
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)));
        List<ArtificialBodyEntity> placed = helper.getLevel().getEntitiesOfClass(ArtificialBodyEntity.class,
                new AABB(helper.absolutePos(POS)).inflate(3), ArtificialBodyEntity::isAlive);
        helper.assertTrue(placed.size() == 1 && placed.get(0).wear() == 37.0f, "The placed body keeps its wear");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aCriticallyWornStandingBodyHasHalfItsIntegrity(GameTestHelper helper) {
        ArtificialBodyEntity body = helper.spawn(BodyEntities.type(BodyClass.IMPROVED_ROBOT), POS);
        float full = body.getMaxHealth();
        body.setWear(100.0f);
        helper.assertTrue(Math.abs(body.getMaxHealth() - full * (1.0 - RebirthConfig.WEAR_MAX_HEALTH_AT_100.get())) < 0.01, "Half: " + body.getMaxHealth());
        helper.assertTrue(body.getHealth() <= body.getMaxHealth(), "Cut down");
        body.setWear(0.0f);
        helper.assertTrue(body.getMaxHealth() == full, "Serviced: full again");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "wear_off")
    public static void withTheSystemOffNothingWears(GameTestHelper helper) {
        ServerPlayer player = playerIn(helper, BodyClass.BASIC_ROBOT);
        BodyWear.setWear(player, 100.0f);
        RebirthConfig.BODY_WEAR_ENABLED.set(false);
        BodyWear.applyLimits(player);
        boolean critical = BodyWear.isCritical(player);
        float max = player.getMaxHealth();
        BodyWear.setWear(player, 0.0f);
        BodyWear.tickSecond(player);
        float wear = BodyWear.wear(player);
        RebirthConfig.BODY_WEAR_ENABLED.set(true);
        BodyWear.applyLimits(player);
        helper.assertFalse(critical, "Off: never critical");
        helper.assertTrue(max == (float) RebirthConfig.bodyStats(BodyClass.BASIC_ROBOT).maxHealth().get().doubleValue(), "Off: full integrity");
        helper.assertTrue(wear == 0.0f, "Off: nothing wears");
        helper.succeed();
    }
}
