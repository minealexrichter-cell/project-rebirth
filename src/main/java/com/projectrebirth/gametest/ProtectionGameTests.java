package com.projectrebirth.gametest;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.Biology;
import com.projectrebirth.environment.EnvironmentRegistry;
import com.projectrebirth.environment.Exposure;
import com.projectrebirth.environment.Hazard;
import com.projectrebirth.environment.HumanBody;
import com.projectrebirth.environment.Protection;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Respirator, filters, suits. Run with {@code .\gradlew runGameTestServer}. */
@GameTestHolder(ProjectRebirth.MOD_ID)
@PrefixGameTestTemplate(false)
public class ProtectionGameTests {
    private static final String TEMPLATE = "floor_5x5";
    private static final float EPSILON = 0.0001f;

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static ItemStack withFilter(Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(EnvironmentRegistry.FILTER_LIFE.get(), RebirthConfig.PROTECTION_FILTER_LIFE.get().floatValue());
        return stack;
    }

    private static void wear(ServerPlayer player, Item head, Item chest, Item legs, Item feet) {
        player.setItemSlot(EquipmentSlot.HEAD, head == null ? ItemStack.EMPTY : withFilter(head));
        player.setItemSlot(EquipmentSlot.CHEST, chest == null ? ItemStack.EMPTY : new ItemStack(chest));
        player.setItemSlot(EquipmentSlot.LEGS, legs == null ? ItemStack.EMPTY : new ItemStack(legs));
        player.setItemSlot(EquipmentSlot.FEET, feet == null ? ItemStack.EMPTY : new ItemStack(feet));
    }

    @GameTest(template = TEMPLATE)
    public static void ordinaryArmorProtectsFromNothing(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        for (Hazard hazard : Hazard.values()) {
            helper.assertTrue(Protection.ofGear(player, hazard) == 0.0f, "Netherite against " + hazard);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aRespiratorNeedsAFilterAndOnlyHelpsTheBreath(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(EnvironmentRegistry.RESPIRATOR.get()));
        helper.assertTrue(Protection.ofGear(player, Hazard.POLLUTION) == 0.0f, "No filter, no protection");
        player.setItemSlot(EquipmentSlot.HEAD, withFilter(EnvironmentRegistry.RESPIRATOR.get()));
        float expected = RebirthConfig.protectionKit("respirator").pollution().get().floatValue();
        helper.assertTrue(Math.abs(Protection.ofGear(player, Hazard.POLLUTION) - expected) < EPSILON, "With a filter");
        helper.assertTrue(Protection.ofGear(player, Hazard.ACID) == 0.0f && Protection.ofGear(player, Hazard.RADIATION) == 0.0f, "Only the breath");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aFullSuitProtectsFullyAndPiecesByTheirShare(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        wear(player, EnvironmentRegistry.HEAVY_SUIT_HELMET.get(), EnvironmentRegistry.HEAVY_SUIT.get(), EnvironmentRegistry.HEAVY_SUIT_TROUSERS.get(), EnvironmentRegistry.HEAVY_SUIT_BOOTS.get());
        RebirthConfig.ProtectionStats heavy = RebirthConfig.protectionKit("heavy_suit");
        helper.assertTrue(Math.abs(Protection.ofGear(player, Hazard.RADIATION) - heavy.radiation().get().floatValue()) < EPSILON, "Full radiation protection of the kit");
        helper.assertTrue(Math.abs(Protection.ofGear(player, Hazard.ACID) - 1.0f) < EPSILON, "Full acid protection");
        wear(player, null, EnvironmentRegistry.HAZMAT_SUIT.get(), null, null);
        float chest = RebirthConfig.PROTECTION_CHEST_SHARE.get().floatValue();
        helper.assertTrue(Math.abs(Protection.ofGear(player, Hazard.ACID) - chest) < EPSILON, "Only the chest: its share, got " + Protection.ofGear(player, Hazard.ACID));
        helper.assertTrue(Protection.ofGear(player, Hazard.POLLUTION) == 0.0f, "No head piece, no breath protection");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theFilterIsUsedOnlyInPollutedAirAndFasterInWorse(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.setItemSlot(EquipmentSlot.HEAD, withFilter(EnvironmentRegistry.RESPIRATOR.get()));
        float full = Protection.filterLeft(player.getItemBySlot(EquipmentSlot.HEAD));
        float safe = RebirthConfig.PROTECTION_FILTER_SAFE_POLLUTION.get().floatValue();
        Protection.useFilter(player, 0.0f);
        Protection.useFilter(player, safe);
        helper.assertTrue(Protection.filterLeft(player.getItemBySlot(EquipmentSlot.HEAD)) == full, "Clean air and the ordinary background use nothing");
        Protection.useFilter(player, 2.0f + safe);
        float afterOne = full - Protection.filterLeft(player.getItemBySlot(EquipmentSlot.HEAD));
        Protection.useFilter(player, 20.0f + safe * 10.0f);
        float afterTen = full - afterOne - Protection.filterLeft(player.getItemBySlot(EquipmentSlot.HEAD));
        helper.assertTrue(afterOne > 0.0f && Math.abs(afterTen - 10.0f * afterOne) < 0.01f, "Ten times the pollution, ten times the use");
        player.getItemBySlot(EquipmentSlot.HEAD).set(EnvironmentRegistry.FILTER_LIFE.get(), 0.5f);
        Protection.useFilter(player, 5.0f);
        helper.assertTrue(Protection.filterLeft(player.getItemBySlot(EquipmentSlot.HEAD)) == 0.0f, "It runs out");
        helper.assertTrue(Protection.ofGear(player, Hazard.POLLUTION) == 0.0f, "A spent filter protects nothing");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aFilterGoesInByHandAndTheRestOfTheOldComesBack(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ItemStack respirator = new ItemStack(EnvironmentRegistry.RESPIRATOR.get());
        respirator.set(EnvironmentRegistry.FILTER_LIFE.get(), 300.0f);
        player.setItemSlot(EquipmentSlot.HEAD, respirator);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EnvironmentRegistry.FILTER.get(), 3));
        EnvironmentRegistry.FILTER.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getMainHandItem().getCount() == 2, "One filter is used");
        helper.assertTrue(Protection.filterLeft(player.getItemBySlot(EquipmentSlot.HEAD)) == RebirthConfig.PROTECTION_FILTER_LIFE.get().floatValue(), "A fresh filter is in");
        helper.assertTrue(player.getInventory().items.stream().anyMatch(stack -> stack.is(EnvironmentRegistry.FILTER.get()) && Protection.filterLeft(stack) == 300.0f),
                "The old one comes back with what it had left");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theBodyTakesLessInASuitAndAcidWearsIt(GameTestHelper helper) {
        ServerPlayer bare = player(helper);
        ServerPlayer suited = player(helper);
        wear(suited, EnvironmentRegistry.HAZMAT_HELMET.get(), EnvironmentRegistry.HAZMAT_SUIT.get(), EnvironmentRegistry.HAZMAT_TROUSERS.get(), EnvironmentRegistry.HAZMAT_BOOTS.get());
        Exposure rain = Exposure.builder().add(Hazard.POLLUTION, 3.0f).add(Hazard.ACID, 1.5f).build();
        for (int i = 0; i < 10; i++) {
            HumanBody.onSecond(bare, rain);
            HumanBody.onSecond(suited, rain);
        }
        Biology bareBody = HumanBody.biology(bare);
        Biology suitedBody = HumanBody.biology(suited);
        helper.assertTrue(suitedBody.toxin() < bareBody.toxin() / 5.0f, "A hazmat keeps most toxins off: " + suitedBody + " vs " + bareBody);
        ItemStack suit = suited.getItemBySlot(EquipmentSlot.CHEST);
        int before = suit.getDamageValue();
        com.projectrebirth.environment.Protection.wearByAcid(suited);
        helper.assertTrue(suit.getDamageValue() > before, "Acid wears the suit");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theFirstGearIsMadeOnACraftingTable(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING);
        for (Item item : new Item[] {EnvironmentRegistry.RESPIRATOR.get(), EnvironmentRegistry.FILTER.get(), EnvironmentRegistry.HAZMAT_HELMET.get(),
                EnvironmentRegistry.HAZMAT_SUIT.get(), EnvironmentRegistry.HAZMAT_TROUSERS.get(), EnvironmentRegistry.HAZMAT_BOOTS.get()}) {
            helper.assertTrue(recipes.stream().anyMatch(recipe -> recipe.value().getResultItem(helper.getLevel().registryAccess()).is(item)), "No crafting table recipe for " + item);
        }
        helper.succeed();
    }
}
