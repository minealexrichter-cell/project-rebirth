package com.projectrebirth.gametest;

import java.util.Optional;

import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.BodyAttachments;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyEntities;
import com.projectrebirth.bodies.BodyGadgets;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.Hazard;
import com.projectrebirth.environment.HumanBody;
import com.projectrebirth.environment.MachineBody;
import com.projectrebirth.powerarmor.ArmorVariant;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.powerarmor.PowerArmorRegistry;
import com.projectrebirth.upgrades.ArmorSlot;
import com.projectrebirth.upgrades.InstalledUpgrades;
import com.projectrebirth.upgrades.UpgradeRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Artificial bodies and power armor in the environment (block D of plan 1.3). */
@GameTestHolder(PlanTests.ATMOSPHERE)
@PrefixGameTestTemplate(false)
public class MachineEnvironmentGameTests {
    private static final String TEMPLATE = "floor_5x5";
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    /** A second on which the acid wear of a body comes. */
    private static final long WEAR_SECOND = 0L;

    private static InstalledUpgrades upgrades(Item... items) {
        InstalledUpgrades upgrades = InstalledUpgrades.EMPTY;
        for (Item item : items) {
            upgrades = upgrades.with(new InstalledUpgrades.Installed(Optional.empty(), new ItemStack(item)));
        }
        return upgrades;
    }

    private static ArtificialBodyEntity body(GameTestHelper helper, BodyClass bodyClass, Item... items) {
        ArtificialBodyEntity body = helper.spawn(BodyEntities.type(bodyClass), POS);
        body.setUpgrades(upgrades(items));
        return body;
    }

    @GameTest(template = TEMPLATE)
    public static void acidWearsABodyWithoutASealingModuleOnly(GameTestHelper helper) {
        ArtificialBodyEntity open = body(helper, BodyClass.BASIC_ROBOT);
        ArtificialBodyEntity sealed = body(helper, BodyClass.BASIC_ROBOT, UpgradeRegistry.SEALING_MODULE.get());
        float full = open.getHealth();
        float done = MachineBody.acidSecond(open, BodyClass.BASIC_ROBOT, open.upgrades(), 1.0f, WEAR_SECOND);
        MachineBody.acidSecond(sealed, BodyClass.BASIC_ROBOT, sealed.upgrades(), 1.0f, WEAR_SECOND);
        helper.assertTrue(done > 0.0f && open.getHealth() < full, "The open body wears: " + open.getHealth());
        helper.assertTrue(sealed.getHealth() == sealed.getMaxHealth(), "The sealed body does not: " + sealed.getHealth());
        float expected = MachineBody.acidWear(BodyClass.BASIC_ROBOT) * RebirthConfig.MACHINE_ACID_WEAR_INTERVAL.get();
        helper.assertTrue(Math.abs(done - expected) < 0.0001f, "The wear of the seconds between comes at once: " + done + ", expected " + expected);
        helper.assertTrue(MachineBody.acidSecond(open, BodyClass.BASIC_ROBOT, open.upgrades(), 1.0f, 20L) == 0.0f, "Not every second");
        helper.assertTrue(MachineBody.acidSecond(open, BodyClass.BASIC_ROBOT, open.upgrades(), 0.0f, WEAR_SECOND) == 0.0f, "No acid, no wear");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void betterBodiesWearMoreSlowly(GameTestHelper helper) {
        BodyClass[] classes = BodyClass.values();
        for (int i = 1; i < classes.length; i++) {
            helper.assertTrue(MachineBody.acidWear(classes[i]) < MachineBody.acidWear(classes[i - 1]),
                    classes[i] + " must wear more slowly than " + classes[i - 1]);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void acidHoldsBackSelfRepair(GameTestHelper helper) {
        ArtificialBodyEntity body = body(helper, BodyClass.PREMIUM_SYNTHETIC);
        body.setData(BodyAttachments.LAST_DAMAGE, -10_000L);
        MachineBody.acidSecond(body, BodyClass.PREMIUM_SYNTHETIC, body.upgrades(), 1.5f, WEAR_SECOND);
        helper.assertTrue(body.getData(BodyAttachments.LAST_DAMAGE) == helper.getLevel().getGameTime(), "The wear counts as damage for self-repair");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theSealingModuleKeepsOutWaterAcidAndDust(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        InstalledUpgrades sealing = upgrades(UpgradeRegistry.SEALING_MODULE.get());
        InstalledUpgrades jetpack = upgrades(UpgradeRegistry.JETPACK.get());
        for (String hazard : new String[] {BodyGadgets.DROWNING, BodyGadgets.ACID, BodyGadgets.DUST}) {
            helper.assertTrue(BodyGadgets.protects(access, sealing, hazard), "The sealing module against " + hazard);
            helper.assertFalse(BodyGadgets.protects(access, jetpack, hazard), "A jetpack against " + hazard);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120)
    public static void aControlledBodyWearsToo(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        // A new player cannot be hurt for its first 3 seconds.
        helper.runAfterDelay(70, () -> {
            float full = player.getHealth();
            MachineBody.acidSecond(player, BodyClass.IMPROVED_ROBOT, InstalledUpgrades.EMPTY, 1.0f, WEAR_SECOND);
            helper.assertTrue(player.getHealth() < full, "A player in a body without the module wears");
            float sealedHealth = player.getHealth();
            player.invulnerableTime = 0;
            MachineBody.acidSecond(player, BodyClass.IMPROVED_ROBOT, upgrades(UpgradeRegistry.SEALING_MODULE.get()), 1.0f, WEAR_SECOND);
            helper.assertTrue(player.getHealth() == sealedHealth, "With it, not");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void platingAddsRadiationProtectionForThePilot(GameTestHelper helper) {
        ServerPlayer pilot = helper.makeMockServerPlayerInLevel();
        pilot.setGameMode(GameType.SURVIVAL);
        PowerArmorEntity armor = helper.spawn(PowerArmorRegistry.POWER_ARMOR.get(), POS);
        armor.setVariant(ArmorVariant.COMBAT);
        pilot.startRiding(armor, true);
        float bare = HumanBody.protection(pilot, Hazard.RADIATION);
        helper.assertTrue(Math.abs(bare - RebirthConfig.HUMAN_PILOT_RADIATION_PROTECTION.get().floatValue()) < 0.0001f, "Without plating: " + bare);
        armor.setUpgrades(armor.upgrades()
                .with(new InstalledUpgrades.Installed(Optional.of(ArmorSlot.CHEST_FRONT), new ItemStack(UpgradeRegistry.ARMOR_IRON_PLATING.get())))
                .with(new InstalledUpgrades.Installed(Optional.of(ArmorSlot.LEFT_LEG), new ItemStack(UpgradeRegistry.ARMOR_TITANIUM_PLATING.get()))));
        float plated = HumanBody.protection(pilot, Hazard.RADIATION);
        float expected = Math.min(1.0f, bare + 2 * RebirthConfig.MACHINE_PILOT_RADIATION_PER_PLATE.get().floatValue());
        helper.assertTrue(Math.abs(plated - expected) < 0.0001f, "Two platings: " + plated + ", expected " + expected);
        helper.assertTrue(HumanBody.protection(pilot, Hazard.ACID) == 1.0f && HumanBody.protection(pilot, Hazard.POLLUTION) == 1.0f, "The cockpit is sealed");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void armorIsNotWornByAcidByDefaultButCanCorrode(GameTestHelper helper) {
        helper.assertFalse(RebirthConfig.MACHINE_ARMOR_ACID_WEAR.get(), "Off by default");
        PowerArmorEntity armor = helper.spawn(PowerArmorRegistry.POWER_ARMOR.get(), POS);
        armor.setVariant(ArmorVariant.COMBAT);
        float structural = armor.getStructural();
        float critical = armor.getCritical();
        armor.corrode(2.0f);
        helper.assertTrue(armor.getStructural() == structural - 2.0f && armor.getCritical() == critical, "Only the outer shell");
        helper.succeed();
    }
}
