package com.projectrebirth.gametest;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.BodyAttachments;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyEntities;
import com.projectrebirth.bodies.BodyItems;
import com.projectrebirth.bodies.BodyRules;
import com.projectrebirth.bodies.PossessionState;
import com.projectrebirth.bodies.TransferService;
import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Class rules of artificial bodies: eating and drinking, status effects, healing, self-repair and the repair kit.
 * Run with {@code .\gradlew runGameTestServer}. Expectations follow the default config.
 */
@GameTestHolder(ProjectRebirth.MOD_ID)
@PrefixGameTestTemplate(false)
public class BodyRulesGameTests {
    private static final String TEMPLATE = "floor_3x3";
    private static final BlockPos BODY_POS = new BlockPos(1, 2, 1);

    private static ServerPlayer playerIn(GameTestHelper helper, BodyClass bodyClass) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setData(BodyAttachments.POSSESSION, PossessionState.of(bodyClass));
        TransferService.applyBodyModifiers(player, bodyClass);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    /** Puts the stack in the main hand and tries to use it; true if the player started using it. */
    private static boolean tryUse(GameTestHelper helper, ServerPlayer player, ItemStack stack) {
        player.stopUsingItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        return player.isUsingItem();
    }

    private static ItemStack potion() {
        return PotionContents.createItemStack(Items.POTION, Potions.STRENGTH);
    }

    /**
     * 1.7.3: the body's modifiers are put on again at every login; the health above a human's 20 must stay (it was cut to 20,
     * found loading old worlds).
     */
    @GameTest(template = TEMPLATE)
    public static void aBodyKeepsItsHealthWhenItsModifiersArePutOnAgain(GameTestHelper helper) {
        ServerPlayer player = playerIn(helper, BodyClass.IMPROVED_ROBOT);
        float full = player.getMaxHealth();
        helper.assertTrue(full > 20.0f, "An improved body has more than 20: " + full);
        TransferService.applyBodyModifiers(player, BodyClass.IMPROVED_ROBOT);
        helper.assertTrue(player.getHealth() == full, "Full health is kept: " + player.getHealth() + " of " + full);
        player.setHealth(23.0f);
        TransferService.applyBodyModifiers(player, BodyClass.IMPROVED_ROBOT);
        helper.assertTrue(player.getHealth() == 23.0f, "A wounded body keeps its health: " + player.getHealth());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void robotAndSyntheticBodiesCannotEatOrDrinkAnything(GameTestHelper helper) {
        for (BodyClass bodyClass : new BodyClass[] {BodyClass.BASIC_ROBOT, BodyClass.IMPROVED_ROBOT,
                BodyClass.ADVANCED_SYNTHETIC, BodyClass.HIGHTECH_SYNTHETIC}) {
            ServerPlayer player = playerIn(helper, bodyClass);
            for (ItemStack stack : new ItemStack[] {new ItemStack(Items.BREAD, 3), new ItemStack(Items.GOLDEN_APPLE, 3),
                    potion(), new ItemStack(Items.MILK_BUCKET)}) {
                int count = stack.getCount();
                helper.assertFalse(tryUse(helper, player, stack), bodyClass + " must not start using " + stack);
                helper.assertTrue(player.getMainHandItem().getCount() == count && player.getMainHandItem().is(stack.getItem()),
                        bodyClass + ": a refused " + stack + " must not be used up");
            }
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void premiumBodyUsesGoldenApplesAndPotionsButNoFood(GameTestHelper helper) {
        ServerPlayer player = playerIn(helper, BodyClass.PREMIUM_SYNTHETIC);
        helper.assertFalse(tryUse(helper, player, new ItemStack(Items.BREAD)), "The premium body must not eat bread");

        ItemStack apple = new ItemStack(Items.GOLDEN_APPLE, 2);
        helper.assertTrue(tryUse(helper, player, apple), "The premium body must eat golden apples");
        apple.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(apple.getCount() == 1, "The golden apple must be eaten");
        helper.assertTrue(player.hasEffect(MobEffects.REGENERATION) && player.hasEffect(MobEffects.ABSORPTION),
                "The golden apple must give its effects");

        ItemStack potion = potion();
        helper.assertTrue(tryUse(helper, player, potion), "The premium body must drink potions");
        potion.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(player.hasEffect(MobEffects.DAMAGE_BOOST), "The potion must work");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void onlyThePremiumBodyIsAffectedByEffects(GameTestHelper helper) {
        for (BodyClass bodyClass : BodyClass.values()) {
            ServerPlayer player = playerIn(helper, bodyClass);
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
            boolean expected = bodyClass == BodyClass.PREMIUM_SYNTHETIC;
            helper.assertTrue(player.hasEffect(MobEffects.POISON) == expected,
                    bodyClass + (expected ? " must be poisoned" : " must be immune to effects"));
            helper.assertTrue(player.isAffectedByPotions() == expected, bodyClass + ": instant potions follow the same rule");
        }
        ArtificialBodyEntity standing = helper.spawn(BodyEntities.type(BodyClass.BASIC_ROBOT), BODY_POS);
        standing.addEffect(new MobEffectInstance(MobEffects.WITHER, 200));
        helper.assertFalse(standing.hasEffect(MobEffects.WITHER), "A standing robot body must be immune to effects too");

        ServerPlayer robot = playerIn(helper, BodyClass.BASIC_ROBOT);
        BodyRules.addOwnEffect(robot, new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60));
        helper.assertTrue(robot.hasEffect(MobEffects.DAMAGE_RESISTANCE), "The mod's own protection effect must still apply");
        helper.succeed();
    }

    /** 1.7.1: no machine of the mod feels potions, lasting or instant. */
    @GameTest(template = TEMPLATE)
    public static void machinesAreImmuneToPotions(GameTestHelper helper) {
        java.util.List<net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.LivingEntity>> types = java.util.List.of(
                com.projectrebirth.garrison.Androids.type(BodyClass.BASIC_ROBOT), com.projectrebirth.garrison.GarrisonRegistry.GUARD_MECH.get(),
                com.projectrebirth.garrison.GarrisonRegistry.MACHINE_GUN_TURRET.get(), com.projectrebirth.hostile.HostileRegistry.PATROL_ROBOT.get(),
                com.projectrebirth.hostile.HostileRegistry.MECH.get(), com.projectrebirth.hostile.HostileRegistry.GUARD_TURRET.get(),
                com.projectrebirth.powerarmor.PowerArmorRegistry.POWER_ARMOR.get());
        for (var type : types) {
            net.minecraft.world.entity.LivingEntity machine = helper.spawn(type, BODY_POS);
            machine.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
            machine.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200));
            helper.assertTrue(machine.getActiveEffects().isEmpty(), type.getDescriptionId() + " must take no effects");
            helper.assertFalse(machine.isAffectedByPotions(), type.getDescriptionId() + " must be untouched by instant potions");
            machine.discard();
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void robotBodiesHealOnlyThroughTheMod(GameTestHelper helper) {
        ServerPlayer robot = playerIn(helper, BodyClass.BASIC_ROBOT);
        robot.setHealth(10.0f);
        robot.heal(5.0f);
        helper.assertTrue(robot.getHealth() == 10.0f, "Ordinary healing (such as peaceful regeneration) must not repair a robot");
        BodyRules.repair(robot, 5.0f);
        helper.assertTrue(robot.getHealth() == 15.0f, "The mod's repair must work, health " + robot.getHealth());

        ServerPlayer premium = playerIn(helper, BodyClass.PREMIUM_SYNTHETIC);
        premium.setHealth(10.0f);
        premium.heal(5.0f);
        helper.assertTrue(premium.getHealth() == 15.0f, "The premium body heals normally");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void syntheticBodiesSelfRepairAfterADelayRobotsDoNot(GameTestHelper helper) {
        ServerPlayer robot = playerIn(helper, BodyClass.IMPROVED_ROBOT);
        ServerPlayer synthetic = playerIn(helper, BodyClass.ADVANCED_SYNTHETIC);
        ArtificialBodyEntity standing = helper.spawn(BodyEntities.type(BodyClass.HIGHTECH_SYNTHETIC), BODY_POS);
        int delay = RebirthConfig.bodyStats(BodyClass.ADVANCED_SYNTHETIC).selfRepairDelaySeconds().get() * 20;
        helper.runAfterDelay(5, () -> {
            for (var entity : new net.minecraft.world.entity.LivingEntity[] {robot, synthetic, standing}) {
                entity.setHealth(10.0f);
                BodyRules.onDamaged(entity);
            }
            BodyRules.tickSelfRepair(robot, BodyClass.IMPROVED_ROBOT);
            BodyRules.tickSelfRepair(synthetic, BodyClass.ADVANCED_SYNTHETIC);
            helper.assertTrue(synthetic.getHealth() == 10.0f, "Self-repair must wait after damage");
            helper.runAfterDelay(delay / 2, () -> {
                BodyRules.tickSelfRepair(synthetic, BodyClass.ADVANCED_SYNTHETIC);
                helper.assertTrue(synthetic.getHealth() == 10.0f, "Self-repair must still wait halfway through the delay");
                helper.assertTrue(standing.getHealth() == 10.0f, "A standing body must also wait");
            });
            helper.runAfterDelay(delay + 45, () -> {
                BodyRules.tickSelfRepair(robot, BodyClass.IMPROVED_ROBOT);
                BodyRules.tickSelfRepair(synthetic, BodyClass.ADVANCED_SYNTHETIC);
                helper.assertTrue(robot.getHealth() == 10.0f, "A robot body must not repair itself");
                helper.assertTrue(synthetic.getHealth() > 10.0f, "A synthetic body must repair itself after the delay");
                helper.assertTrue(standing.getHealth() > 10.0f, "A standing synthetic body repairs itself every second, health " + standing.getHealth());
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE)
    public static void repairKitRepairsAndDamageInterruptsIt(GameTestHelper helper) {
        ServerPlayer player = playerIn(helper, BodyClass.BASIC_ROBOT);
        player.setHealth(5.0f);
        ItemStack kits = new ItemStack(BodyItems.REPAIR_KIT.get(), 3);

        helper.assertTrue(tryUse(helper, player, kits), "The kit must start in an artificial body");
        player.hurt(player.damageSources().fellOutOfWorld(), 1.0f);
        helper.assertFalse(player.isUsingItem(), "Damage must interrupt the repair");
        helper.assertTrue(kits.getCount() == 3, "An interrupted kit is not used up");

        helper.assertTrue(tryUse(helper, player, kits), "The kit can be started again");
        float before = player.getHealth();
        kits.finishUsingItem(helper.getLevel(), player);
        float expected = before + player.getMaxHealth() * RebirthConfig.REPAIR_KIT_FRACTION.get().floatValue();
        helper.assertTrue(Math.abs(player.getHealth() - expected) < 0.01f, "Expected " + expected + " after the kit, got " + player.getHealth());
        helper.assertTrue(kits.getCount() == 2, "A finished kit is used up");

        ServerPlayer human = helper.makeMockServerPlayerInLevel();
        human.setGameMode(GameType.SURVIVAL);
        human.setHealth(5.0f);
        helper.assertFalse(tryUse(helper, human, new ItemStack(BodyItems.REPAIR_KIT.get())), "The kit does not work on a human");
        helper.succeed();
    }
}
