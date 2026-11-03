package com.projectrebirth.gametest;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.powerarmor.ArmorAbility;
import com.projectrebirth.powerarmor.ArmorVariant;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.powerarmor.PowerArmorRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The ability slot (hotbar slot 9) of a power armor pilot. Run with {@code .\gradlew runGameTestServer}. */
@GameTestHolder(ProjectRebirth.MOD_ID)
@PrefixGameTestTemplate(false)
public class ArmorAbilityGameTests {
    private static final String TEMPLATE = "floor_5x5";

    private static PowerArmorEntity armor(GameTestHelper helper, ArmorVariant variant, BlockPos pos) {
        PowerArmorEntity armor = PowerArmorRegistry.POWER_ARMOR.get().create(helper.getLevel());
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(pos));
        armor.setVariant(variant);
        armor.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
        helper.getLevel().addFreshEntity(armor);
        return armor;
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static int abilityItems(Inventory inventory) {
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (ArmorAbility.isAbility(inventory.getItem(i))) {
                count++;
            }
        }
        return count;
    }

    @GameTest(template = TEMPLATE)
    public static void slotAppearsInTheArmorAndItsItemMovesAside(GameTestHelper helper) {
        PowerArmorEntity armor = armor(helper, ArmorVariant.COMBAT, new BlockPos(2, 2, 2));
        ServerPlayer player = player(helper);
        Inventory inventory = player.getInventory();
        inventory.setItem(ArmorAbility.SLOT, new ItemStack(Items.DIAMOND, 5));

        player.startRiding(armor, true);
        ArmorAbility.sync(player);
        helper.assertTrue(inventory.getItem(ArmorAbility.SLOT).is(PowerArmorRegistry.abilityItem(ArmorVariant.COMBAT)),
                "Slot 9 must hold the combat ability");
        helper.assertTrue(inventory.countItem(Items.DIAMOND) == 5, "The diamonds from slot 9 move to another slot");

        // Moved anywhere else, the icon disappears; slot 9 gets it back.
        inventory.setItem(3, inventory.getItem(ArmorAbility.SLOT).copy());
        inventory.setItem(ArmorAbility.SLOT, new ItemStack(Items.STICK));
        ArmorAbility.sync(player);
        helper.assertTrue(abilityItems(inventory) == 1 && ArmorAbility.isAbility(inventory.getItem(ArmorAbility.SLOT)),
                "Exactly one icon, in slot 9");
        helper.assertTrue(inventory.countItem(Items.STICK) == 1, "An item put into slot 9 is moved, not lost");

        player.stopRiding();
        ArmorAbility.sync(player);
        helper.assertTrue(abilityItems(inventory) == 0, "No icon outside the armor");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void fullInventoryKeepsTheSlotItemAsideAndGivesItBack(GameTestHelper helper) {
        PowerArmorEntity armor = armor(helper, ArmorVariant.MOBILE, new BlockPos(2, 2, 2));
        ServerPlayer player = player(helper);
        Inventory inventory = player.getInventory();
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            inventory.setItem(i, new ItemStack(Items.DIRT, 64));
        }
        player.startRiding(armor, true);
        ArmorAbility.sync(player);
        helper.assertTrue(inventory.countItem(Items.DIRT) == 35 * 64, "The dirt of slot 9 is kept aside");
        helper.assertTrue(player.getData(PowerArmorRegistry.SET_ASIDE).getCount() == 64, "Kept aside, not lost");

        player.stopRiding();
        ArmorAbility.sync(player);
        helper.assertTrue(inventory.getItem(ArmorAbility.SLOT).is(Items.DIRT) && inventory.countItem(Items.DIRT) == 36 * 64,
                "After getting out the dirt is back in slot 9");
        helper.assertTrue(player.getData(PowerArmorRegistry.SET_ASIDE).isEmpty(), "Nothing stays aside");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void iconNeverLiesInTheWorld(GameTestHelper helper) {
        Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
        ItemEntity dropped = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(PowerArmorRegistry.abilityItem(ArmorVariant.WORKER)));
        helper.assertFalse(helper.getLevel().addFreshEntity(dropped), "An icon must not appear as an item in the world");
        ServerPlayer player = player(helper);
        player.getInventory().setItem(0, new ItemStack(PowerArmorRegistry.abilityItem(ArmorVariant.WORKER)));
        ArmorAbility.sync(player);
        helper.assertTrue(abilityItems(player.getInventory()) == 0, "An icon outside an armor is removed");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void combatSlotHitsHarder(GameTestHelper helper) {
        PowerArmorEntity armor = armor(helper, ArmorVariant.COMBAT, new BlockPos(1, 2, 2));
        ServerPlayer pilot = player(helper);
        pilot.startRiding(armor, true);
        ArmorAbility.sync(pilot);
        IronGolem target = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new BlockPos(3, 2, 2));
        float[] damage = new float[2];
        for (int round = 0; round < 2; round++) {
            pilot.getInventory().selected = round == 0 ? 0 : ArmorAbility.SLOT;
            target.setHealth(target.getMaxHealth());
            target.invulnerableTime = 0;
            pilot.attack(target);
            damage[round] = target.getMaxHealth() - target.getHealth();
        }
        float bonus = RebirthConfig.ARMOR_ABILITY_ATTACK_BONUS.get().floatValue();
        helper.assertTrue(Math.abs(damage[1] - damage[0] - bonus) < 0.01f,
                "The ability slot must add " + bonus + " damage: empty hand " + damage[0] + ", ability slot " + damage[1]);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void mobileSlotRunsFasterAndMobileArmorFallsFarther(GameTestHelper helper) {
        PowerArmorEntity mobile = armor(helper, ArmorVariant.MOBILE, new BlockPos(1, 2, 1));
        ServerPlayer pilot = player(helper);
        pilot.startRiding(mobile, true);
        ArmorAbility.sync(pilot);
        pilot.getInventory().selected = 0;
        float normal = mobile.pilotSpeed(pilot);
        pilot.getInventory().selected = ArmorAbility.SLOT;
        float boosted = mobile.pilotSpeed(pilot);
        float bonus = RebirthConfig.ARMOR_ABILITY_SPEED_BONUS.get().floatValue();
        helper.assertTrue(Math.abs(boosted - normal * (1 + bonus)) < 0.0001f, "Speed " + normal + " must grow by " + bonus + ", got " + boosted);

        helper.assertTrue(mobile.getAttributeValue(Attributes.SAFE_FALL_DISTANCE) == 15.0, "The mobile armor falls 15 blocks safely");
        // A new player is invulnerable for a few seconds after joining; the pilot's share of the fall must arrive.
        ObfuscationReflectionHelper.setPrivateValue(ServerPlayer.class, pilot, 0, "spawnInvulnerableTime");
        PowerArmorEntity combat = armor(helper, ArmorVariant.COMBAT, new BlockPos(3, 2, 3));
        mobile.causeFallDamage(14.5f, 1.0f, mobile.damageSources().fall());
        combat.causeFallDamage(14.5f, 1.0f, combat.damageSources().fall());
        // With its pilot inside: vanilla also hands the fall to the pilot, which must not end up in the armor.
        helper.assertTrue(mobile.getStructural() == mobile.getMaxStructural(), "A 14 block fall must not hurt the mobile armor or its pilot");
        helper.assertTrue(combat.getStructural() < combat.getMaxStructural(), "It still hurts the combat armor");
        helper.succeed();
    }
}
