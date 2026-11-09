package com.projectrebirth.gametest;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.powerarmor.ArmorVariant;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.powerarmor.PowerArmorRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Version 1.5.2: the empty armored hands strike as often as a sword, so clicks cannot be spammed. Run alone with
 * {@code .\gradlew runGameTestServer -PgameTests=projectrebirth_armor}.
 */
@GameTestHolder(PlanTests.ARMOR)
@PrefixGameTestTemplate(false)
public class ArmorStrikeGameTests {
    private static final String TEMPLATE = "floor_5x5";

    @SuppressWarnings("removal")
    private static ServerPlayer pilotIn(GameTestHelper helper, ArmorVariant variant) {
        PowerArmorEntity armor = helper.spawn(PowerArmorRegistry.POWER_ARMOR.get(), new BlockPos(2, 2, 2));
        armor.setVariant(variant);
        ServerPlayer pilot = helper.makeMockServerPlayerInLevel();
        Vec3 at = armor.position().add(0.0, 0.0, -1.5);
        pilot.moveTo(at.x, at.y, at.z);
        // a new player is given the Codex: the hands must be empty
        pilot.getInventory().clearContent();
        helper.assertTrue(pilot.startRiding(armor, true), "The pilot gets in");
        return pilot;
    }

    /** In every armor the empty hands get a sword's strike speed; an item in the hand keeps its own. */
    @GameTest(template = TEMPLATE)
    public static void theEmptyArmoredHandsStrikeLikeASword(GameTestHelper helper) {
        for (ArmorVariant variant : ArmorVariant.values()) {
            ServerPlayer pilot = pilotIn(helper, variant);
            PowerArmorEntity armor = (PowerArmorEntity) pilot.getVehicle();
            double speed = pilot.getAttributeValue(Attributes.ATTACK_SPEED);
            helper.assertTrue(Math.abs(speed - RebirthConfig.ARMOR_PILOT_ATTACK_SPEED.get()) < 1.0e-6,
                    variant.id() + ": empty hands strike " + RebirthConfig.ARMOR_PILOT_ATTACK_SPEED.get() + " times a second, got " + speed + ", active " + armor.isActive());
            pilot.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            armor.updateStrikeSpeed(pilot);
            helper.assertTrue(Math.abs(pilot.getAttributeValue(Attributes.ATTACK_SPEED) - pilot.getAttributeBaseValue(Attributes.ATTACK_SPEED)) < 1.0e-6,
                    variant.id() + ": with an item in the hand the change is taken off");
            pilot.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            armor.updateStrikeSpeed(pilot);
            pilot.stopRiding();
            helper.assertTrue(Math.abs(pilot.getAttributeValue(Attributes.ATTACK_SPEED) - pilot.getAttributeBaseValue(Attributes.ATTACK_SPEED)) < 1.0e-6,
                    variant.id() + ": out of the armor the strike speed is the player's own again");
        }
        helper.succeed();
    }

    /** A strike right after another does not land; once the hands are ready again it does. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void aStrikeBeforeTheHandsAreReadyDoesNotLand(GameTestHelper helper) {
        ServerPlayer pilot = pilotIn(helper, ArmorVariant.COMBAT);
        Zombie target = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 4));
        target.setNoAi(true);
        pilot.resetAttackStrengthTicker();
        float before = target.getHealth();
        helper.assertFalse(PowerArmorEntity.strikeReady(pilot), "Right after a strike the hands are not ready: strength " + pilot.getAttackStrengthScale(0.5f) + ", delay " + pilot.getCurrentItemAttackStrengthDelay() + ", active " + ((PowerArmorEntity) pilot.getVehicle()).isActive() + ", hand " + pilot.getMainHandItem());
        pilot.attack(target);
        helper.assertTrue(target.getHealth() == before, "A strike before the hands are ready does no harm");
        // the cooldown of a sword: 20 / 1.6 = 12.5 ticks
        int ready = (int) Math.ceil(20.0 / RebirthConfig.ARMOR_PILOT_ATTACK_SPEED.get()) + 2;
        helper.runAtTickTime(ready + 1, () -> {
            for (int i = 0; i < ready && !PowerArmorEntity.strikeReady(pilot); i++) {
                // a mock player is not ticked by a connection: count the time for it as its own tick would
                pilot.doTick();
            }
            helper.assertTrue(PowerArmorEntity.strikeReady(pilot), "After a sword's cooldown the hands are ready");
            pilot.attack(target);
            helper.assertTrue(target.getHealth() < before, "and the strike lands");
            helper.succeed();
        });
    }
}
