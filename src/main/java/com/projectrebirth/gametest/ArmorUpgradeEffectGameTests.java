package com.projectrebirth.gametest;

import java.util.Optional;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.powerarmor.ArmorUpgradeEffects;
import com.projectrebirth.powerarmor.ArmorVariant;
import com.projectrebirth.powerarmor.Floodlight;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.powerarmor.PowerArmorRegistry;
import com.projectrebirth.technology.Maintenance;
import com.projectrebirth.upgrades.ArmorSlot;
import com.projectrebirth.upgrades.InstalledUpgrades;
import com.projectrebirth.upgrades.UpgradeRegistry;
import com.projectrebirth.upgrades.Upgrades;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** What the upgrades of a power armor do. Run with {@code .\gradlew runGameTestServer}. */
@GameTestHolder(ProjectRebirth.MOD_ID)
@PrefixGameTestTemplate(false)
public class ArmorUpgradeEffectGameTests {
    private static final String TEMPLATE = "floor_5x5";
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    private static final double EPSILON = 0.001;

    private static PowerArmorEntity armor(GameTestHelper helper, ArmorVariant variant) {
        PowerArmorEntity armor = helper.spawn(PowerArmorRegistry.POWER_ARMOR.get(), POS);
        armor.setVariant(variant);
        return armor;
    }

    private static void install(GameTestHelper helper, PowerArmorEntity armor, Item item, ArmorSlot slot) {
        Upgrades.Problem problem = Upgrades.install(helper.getLevel().registryAccess(), armor, new ItemStack(item), slot);
        helper.assertTrue(problem == null, item + " on " + slot + ": " + problem);
    }

    private static float baseStructural(PowerArmorEntity armor) {
        return armor.stats().maxStructural().get().floatValue();
    }

    @GameTest(template = TEMPLATE)
    public static void platingRaisesTheMaximumPerPartAndRemovingLimitsTheCurrentValue(GameTestHelper helper) {
        PowerArmorEntity armor = armor(helper, ArmorVariant.COMBAT);
        float base = baseStructural(armor);
        helper.assertTrue(Math.abs(armor.getStructural() - base) < EPSILON, "Starts full");
        install(helper, armor, UpgradeRegistry.ARMOR_COPPER_PLATING.get(), ArmorSlot.RIGHT_ARM);
        install(helper, armor, UpgradeRegistry.ARMOR_TITANIUM_PLATING.get(), ArmorSlot.LEFT_LEG);
        float bonus = RebirthConfig.armorPlating("copper").structural().get().floatValue()
                + RebirthConfig.armorPlating("titanium").structural().get().floatValue();
        helper.assertTrue(Math.abs(armor.getMaxStructural() - (base + bonus)) < EPSILON, "Each part counts, got " + armor.getMaxStructural());
        helper.assertTrue(Math.abs(armor.getStructural() - base) < EPSILON, "Installing does not restore integrity");
        helper.assertTrue(armor.getMaxCritical() > armor.stats().maxCritical().get().floatValue(), "Titanium also adds critical integrity");

        armor.setIntegrity(armor.getMaxStructural(), armor.getMaxCritical());
        helper.assertTrue(Upgrades.remove(armor, armor.upgrades().indexOf(ArmorSlot.LEFT_LEG)).is(UpgradeRegistry.ARMOR_TITANIUM_PLATING.get()), "It comes off");
        helper.assertTrue(Math.abs(armor.getStructural() - armor.getMaxStructural()) < EPSILON, "The current value is limited to the new maximum");
        helper.assertTrue(armor.getCritical() <= armor.stats().maxCritical().get().floatValue() + EPSILON, "Critical too");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aPackedArmorKeepsItsMaximumForTheMaintenanceStation(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        PowerArmorEntity armor = armor(helper, ArmorVariant.COMBAT);
        armor.setOwner(owner.getUUID());
        install(helper, armor, UpgradeRegistry.ARMOR_IRON_PLATING.get(), ArmorSlot.CHEST_FRONT);
        armor.setIntegrity(10.0f, armor.getMaxCritical());
        float max = armor.getMaxStructural();

        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PowerArmorRegistry.REPAIR_WRENCH.get()));
        owner.setShiftKeyDown(true);
        armor.interact(owner, InteractionHand.MAIN_HAND);
        ItemStack packed = owner.getInventory().items.stream().filter(stack -> stack.is(PowerArmorRegistry.item(ArmorVariant.COMBAT))).findFirst().orElse(ItemStack.EMPTY);
        helper.assertTrue(Math.abs(Maintenance.maxStructural(packed) - max) < EPSILON, "The station repairs up to the maximum with plates, got " + Maintenance.maxStructural(packed));
        helper.assertTrue(Maintenance.armorStructural(packed) == 10.0f, "The damage stays");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void airModulesAddToTheJumpUpToTheLimit(GameTestHelper helper) {
        PowerArmorEntity combat = armor(helper, ArmorVariant.COMBAT);
        double base = combat.stats().jumpStrength().get();
        double per = RebirthConfig.ARMOR_JUMP_PER_AIR_MODULE.get();
        helper.assertTrue(Math.abs(ArmorUpgradeEffects.jumpStrength(combat) - base) < EPSILON, "No module: the variant's jump");
        install(helper, combat, UpgradeRegistry.COMPRESSED_AIR_MODULE.get(), ArmorSlot.CHEST_BACK);
        helper.assertTrue(Math.abs(ArmorUpgradeEffects.jumpStrength(combat) - (base + per)) < EPSILON, "One module");
        helper.assertTrue(combat.getAttributeValue(Attributes.SAFE_FALL_DISTANCE) > combat.stats().safeFallDistance().get(), "A higher jump lands safely");

        PowerArmorEntity mobile = armor(helper, ArmorVariant.MOBILE);
        install(helper, mobile, UpgradeRegistry.COMPRESSED_AIR_MODULE.get(), ArmorSlot.CHEST_BACK);
        install(helper, mobile, UpgradeRegistry.COMPRESSED_AIR_MODULE.get(), ArmorSlot.RIGHT_LEG);
        install(helper, mobile, UpgradeRegistry.COMPRESSED_AIR_MODULE.get(), ArmorSlot.LEFT_LEG);
        helper.assertTrue(ArmorUpgradeEffects.jumpStrength(mobile) <= RebirthConfig.ARMOR_MAX_JUMP_STRENGTH.get() + EPSILON, "Three modules stay under the limit");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void upgradesReachTheClientsWithTheArmor(GameTestHelper helper) {
        PowerArmorEntity armor = armor(helper, ArmorVariant.COMBAT);
        InstalledUpgrades upgrades = InstalledUpgrades.EMPTY.with(new InstalledUpgrades.Installed(Optional.of(ArmorSlot.HEAD), new ItemStack(UpgradeRegistry.FLOODLIGHT.get())));
        armor.setUpgrades(upgrades);
        helper.assertTrue(armor.upgrades().equals(upgrades) && armor.getEntityData().isDirty(), "A change must be marked for sending");
        helper.succeed();
    }

    // ---------------------------------------------------------------- floodlight

    private static ServerPlayer pilotIn(GameTestHelper helper, PowerArmorEntity armor) {
        ServerPlayer pilot = helper.makeMockServerPlayerInLevel();
        pilot.setGameMode(GameType.SURVIVAL);
        pilot.startRiding(armor, true);
        pilot.setXRot(0.0f);
        pilot.setYRot(0.0f);
        return pilot;
    }

    private static boolean anyLight(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(-20, 1, -20)), helper.absolutePos(new BlockPos(24, 12, 24)))) {
            if (helper.getLevel().getBlockState(pos).is(PowerArmorRegistry.FLOODLIGHT_LIGHT.get())) {
                return true;
            }
        }
        return false;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void lightFollowsTheSwitchAndGoesAwayWithTheArmor(GameTestHelper helper) {
        PowerArmorEntity armor = armor(helper, ArmorVariant.COMBAT);
        ServerPlayer pilot = pilotIn(helper, armor);
        Floodlight.toggle(armor, pilot);
        helper.assertFalse(armor.isFloodlightOn(), "Without a floodlight nothing turns on");
        install(helper, armor, UpgradeRegistry.FLOODLIGHT.get(), ArmorSlot.HEAD);
        Floodlight.toggle(armor, pilot);
        helper.assertTrue(armor.isFloodlightOn(), "The key turns it on");
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(anyLight(helper), "A light must stand where the pilot looks");
            Floodlight.toggle(armor, pilot);
            helper.runAfterDelay(10, () -> {
                helper.assertFalse(anyLight(helper), "Switched off: no light");
                Floodlight.toggle(armor, pilot);
                helper.runAfterDelay(10, () -> {
                    helper.assertTrue(anyLight(helper), "On again");
                    pilot.stopRiding();
                    helper.runAfterDelay(10, () -> {
                        helper.assertFalse(anyLight(helper), "No pilot: no light");
                        pilot.startRiding(armor, true);
                        helper.runAfterDelay(10, () -> {
                            helper.assertTrue(anyLight(helper), "Pilot back: light again");
                            armor.discard();
                            helper.runAfterDelay(3, () -> {
                                helper.assertFalse(anyLight(helper), "Armor gone: no light");
                                helper.succeed();
                            });
                        });
                    });
                });
            });
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void aShutDownArmorHasNoLight(GameTestHelper helper) {
        PowerArmorEntity armor = armor(helper, ArmorVariant.COMBAT);
        ServerPlayer pilot = pilotIn(helper, armor);
        install(helper, armor, UpgradeRegistry.FLOODLIGHT.get(), ArmorSlot.HEAD);
        Floodlight.toggle(armor, pilot);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(anyLight(helper), "Light on");
            armor.setIntegrity(0.0f, armor.getMaxCritical());
            helper.runAfterDelay(10, () -> {
                helper.assertFalse(anyLight(helper), "Shut down: light off");
                helper.succeed();
            });
        });
    }

    /** What a restart or a crash leaves behind: the block is there, and nothing remembers it. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void aForgottenLightRemovesItself(GameTestHelper helper) {
        helper.setBlock(POS, PowerArmorRegistry.FLOODLIGHT_LIGHT.get());
        helper.assertBlockPresent(PowerArmorRegistry.FLOODLIGHT_LIGHT.get(), POS);
        helper.runAfterDelay(60, () -> {
            helper.assertBlockPresent(Blocks.AIR, POS);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void theLightDoesNotReplaceBlocks(GameTestHelper helper) {
        PowerArmorEntity armor = armor(helper, ArmorVariant.COMBAT);
        ServerPlayer pilot = pilotIn(helper, armor);
        install(helper, armor, UpgradeRegistry.FLOODLIGHT.get(), ArmorSlot.HEAD);
        Floodlight.toggle(armor, pilot);
        BlockPos target = Floodlight.target(armor);
        helper.assertTrue(target != null, "There is air in front");
        helper.assertTrue(helper.getLevel().getBlockState(target).isAir(), "It stands only in air");
        helper.succeed();
    }
}
