package com.projectrebirth.gametest;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.powerarmor.ArmorVariant;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.powerarmor.PowerArmorRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Version 1.5.2: the combat armor opens before its pilot gets in and out. Get in: right click, the shell opens, the player
 * steps in, it closes. Get out: sneak, it opens, the pilot steps out in front, it closes. While it is not shut the armor
 * does not walk. The other variants still let their pilot in and out at once.
 */
@GameTestHolder(ProjectRebirth.MOD_ID)
@PrefixGameTestTemplate(false)
public class ArmorShellGameTests {
    private static final String TEMPLATE = "floor_5x5";
    private static final BlockPos ARMOR_POS = new BlockPos(2, 2, 2);
    private static final int OPEN = PowerArmorEntity.SHELL_OPEN_TICKS;
    private static final int OPEN_AND_HOLD = PowerArmorEntity.SHELL_OPEN_TICKS + PowerArmorEntity.SHELL_HOLD_TICKS;
    private static final int WHOLE = OPEN_AND_HOLD + PowerArmorEntity.SHELL_CLOSE_TICKS;

    private static PowerArmorEntity armor(GameTestHelper helper, ArmorVariant variant) {
        PowerArmorEntity armor = helper.spawn(PowerArmorRegistry.POWER_ARMOR.get(), ARMOR_POS);
        armor.setVariant(variant);
        return armor;
    }

    @SuppressWarnings("removal")
    private static ServerPlayer playerBeside(GameTestHelper helper, PowerArmorEntity armor) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 at = armor.position().add(0.0, 0.0, -1.5);
        player.moveTo(at.x, at.y, at.z);
        return player;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void theArmorOpensLetsThePlayerInAndCloses(GameTestHelper helper) {
        PowerArmorEntity armor = armor(helper, ArmorVariant.COMBAT);
        ServerPlayer player = playerBeside(helper, armor);
        armor.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(armor.shellState() == PowerArmorEntity.SHELL_OPENING, "Right click opens the shell");
        helper.assertFalse(player.isPassenger(), "The player waits until it is open");
        helper.runAtTickTime(OPEN - 4, () -> helper.assertFalse(player.isPassenger(), "Still opening: not in yet"));
        helper.runAtTickTime(OPEN_AND_HOLD + 3, () -> {
            helper.assertTrue(player.getVehicle() == armor, "Open: the player has stepped in");
            helper.assertTrue(armor.shellState() == PowerArmorEntity.SHELL_CLOSING, "With the pilot in, it closes");
        });
        helper.runAtTickTime(WHOLE + 5, () -> {
            helper.assertTrue(armor.isShellClosed(), "Shut again");
            helper.assertTrue(player.getVehicle() == armor, "with the pilot inside");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void thePilotSneaksAndTheArmorOpensToLetThemOut(GameTestHelper helper) {
        PowerArmorEntity armor = armor(helper, ArmorVariant.COMBAT);
        ServerPlayer pilot = playerBeside(helper, armor);
        helper.assertTrue(pilot.startRiding(armor, true), "The pilot gets in");
        pilot.setShiftKeyDown(true);
        pilot.stopRiding();
        helper.assertTrue(pilot.getVehicle() == armor, "Sneaking does not drop the pilot out at once");
        helper.assertTrue(armor.shellState() == PowerArmorEntity.SHELL_OPENING, "It opens first");
        helper.runAtTickTime(OPEN_AND_HOLD + 3, () -> {
            helper.assertFalse(pilot.isPassenger(), "Open: the pilot has stepped out");
            helper.assertTrue(armor.shellState() == PowerArmorEntity.SHELL_CLOSING, "Empty, it closes");
        });
        helper.runAtTickTime(WHOLE + 5, () -> {
            helper.assertTrue(armor.isShellClosed(), "Shut again, empty");
            helper.assertFalse(armor.isVehicle(), "Nobody inside");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void aPlayerWhoWalksAwayIsNotPulledIn(GameTestHelper helper) {
        PowerArmorEntity armor = armor(helper, ArmorVariant.COMBAT);
        ServerPlayer player = playerBeside(helper, armor);
        armor.interact(player, InteractionHand.MAIN_HAND);
        ServerPlayer other = playerBeside(helper, armor);
        armor.interact(other, InteractionHand.MAIN_HAND);
        helper.assertFalse(other.isPassenger(), "Another player waits while it opens for the first");
        helper.runAtTickTime(4, () -> player.moveTo(player.getX() + 30.0, player.getY(), player.getZ()));
        helper.runAtTickTime(WHOLE + 5, () -> {
            helper.assertFalse(player.isPassenger(), "Too far: not pulled in");
            helper.assertTrue(armor.isShellClosed(), "It closes again empty");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void otherArmorsStillLetThePilotInAtOnce(GameTestHelper helper) {
        PowerArmorEntity armor = armor(helper, ArmorVariant.MOBILE);
        ServerPlayer player = playerBeside(helper, armor);
        armor.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getVehicle() == armor, "A mobile armor (no opening model yet) takes the pilot at once");
        helper.assertTrue(armor.isShellClosed(), "and never opens");
        helper.succeed();
    }
}
