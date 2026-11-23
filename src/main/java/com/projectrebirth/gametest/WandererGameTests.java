package com.projectrebirth.gametest;

import java.util.List;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.WastelandConfig;
import com.projectrebirth.core.RebirthGameRules;
import com.projectrebirth.garrison.GarrisonRegistry;
import com.projectrebirth.garrison.TargetRules;
import com.projectrebirth.garrison.TestMachineEntity;
import com.projectrebirth.hostile.HostileRegistry;
import com.projectrebirth.hostile.RemainsEntity;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.wanderers.WandererData;
import com.projectrebirth.wanderers.WandererEntity;
import com.projectrebirth.wanderers.WandererGroups;
import com.projectrebirth.wanderers.WandererRegistry;
import com.projectrebirth.wanderers.WandererTradeMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.9.0, block E: wanderers. Groups come from data with one head; they are neutral until a player hurts one of them, then the
 * whole group (and only it) turns on that player; they fight monsters and mutants, the garrison leaves them be; the head trades;
 * the fallen leave wrecks; a group goes away with no player near; the game rule switches them off.
 */
@GameTestHolder(PlanTests.WASTELAND)
@PrefixGameTestTemplate(false)
public class WandererGameTests {
    private static final String TEMPLATE = "floor_9x9";

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static WandererData.GroupSpec group(GameTestHelper helper, String name) {
        return helper.getLevel().registryAccess().registryOrThrow(WandererData.GROUPS)
                .getOrThrow(ResourceKey.create(WandererData.GROUPS, ProjectRebirth.id(name)));
    }

    private static WandererEntity spawn(GameTestHelper helper, String group, BlockPos at) {
        return WandererGroups.spawnGroup(helper.getLevel(), group(helper, group), helper.absolutePos(at), helper.getLevel().random);
    }

    private static WandererEntity one(GameTestHelper helper, EntityType<WandererEntity> type, String variant, BlockPos at) {
        WandererEntity wanderer = helper.spawnWithNoFreeWill(type, at);
        wanderer.setVariant(variant);
        return wanderer;
    }

    private static void clear(GameTestHelper helper) {
        AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16);
        helper.getLevel().getEntitiesOfClass(WandererEntity.class, box).forEach(WandererEntity::discard);
    }

    @GameTest(template = TEMPLATE)
    public static void groupsAndOffersComeFromData(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        int groups = access.registryOrThrow(WandererData.GROUPS).size();
        int trades = access.registryOrThrow(WandererData.TRADES).size();
        helper.assertTrue(groups >= 5, "Five kinds of groups in data: " + groups);
        helper.assertTrue(trades >= 20, "Twenty offers in data: " + trades);
        helper.assertTrue(group(helper, "mercenaries").minStage() >= 2, "Mercenaries from stage II");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aGroupHasOneHeadWithOffers(GameTestHelper helper) {
        WandererEntity head = spawn(helper, "caravan", new BlockPos(4, 2, 4));
        helper.assertTrue(head != null && head.isLeader(), "The group has a head");
        List<WandererEntity> members = head.group();
        helper.assertTrue(members.size() >= 3, "Armor, survivors and a robot: " + members.size());
        helper.assertTrue(members.stream().filter(WandererEntity::isLeader).count() == 1, "Exactly one head");
        helper.assertTrue(members.stream().allMatch(m -> head.groupId().equals(m.groupId())), "One group");
        helper.assertTrue(head.kind() == WandererEntity.Kind.ARMORED && head.hasGun(), "The caravan is led by an armed armor");
        int offers = head.offers().size();
        helper.assertTrue(offers >= WastelandConfig.WANDERERS_OFFERS_MIN.get() && offers <= WastelandConfig.WANDERERS_OFFERS_MAX.get(),
                "Offers within the config: " + offers);
        helper.assertTrue(head.offers().stream().distinct().count() == offers, "No offer twice");
        clear(helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aHitTurnsTheWholeGroupButNotOthers(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        WandererEntity head = spawn(helper, "scouts", new BlockPos(2, 2, 2));
        WandererEntity others = spawn(helper, "caravan", new BlockPos(6, 2, 6));
        helper.assertFalse(head.hostileTo(player) || others.hostileTo(player), "Neutral at first");
        WandererEntity hit = head.group().get(head.group().size() - 1);
        hit.hurt(player.damageSources().playerAttack(player), 1.0f);
        helper.assertTrue(head.group().stream().allMatch(m -> m.hostileTo(player)), "The whole group turned on the player");
        helper.assertTrue(head.group().stream().allMatch(m -> m.getTarget() == player), "And goes for the player");
        helper.assertFalse(others.group().stream().anyMatch(m -> m.hostileTo(player)), "The other group does not care");
        clear(helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void wanderersFightMonstersAndMutantsOnly(GameTestHelper helper) {
        var zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        var crawler = helper.spawnWithNoFreeWill(HostileRegistry.CRAWLER.get(), new BlockPos(2, 2, 1));
        var villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(3, 2, 1));
        WandererEntity other = one(helper, WandererRegistry.ROBOT.get(), "", new BlockPos(4, 2, 1));
        helper.assertTrue(WandererEntity.isFoe(zombie) && WandererEntity.isFoe(crawler), "Monsters and mutants are foes");
        helper.assertFalse(WandererEntity.isFoe(villager) || WandererEntity.isFoe(other), "Villagers and wanderers are not");
        helper.assertTrue(HostileRegistry.CRAWLER.get().builtInRegistryHolder() != null
                        && WandererRegistry.SURVIVOR.get().is(net.minecraft.tags.TagKey.create(Registries.ENTITY_TYPE, ProjectRebirth.id("mutant_prey"))),
                "Mutants hunt wanderers");
        clear(helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theGarrisonLeavesWanderersBe(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        TestMachineEntity machine = GarrisonRegistry.TEST_MACHINE.get().create(helper.getLevel());
        Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 4)));
        machine.moveTo(pos.x, pos.y, pos.z, 0.0f, 0.0f);
        machine.machineCore().setOwner(owner.getUUID(), "owner");
        helper.getLevel().addFreshEntity(machine);
        WandererEntity wanderer = one(helper, WandererRegistry.ANDROID.get(), "advanced_synthetic_body", new BlockPos(6, 2, 6));
        helper.assertFalse(TargetRules.mayAttack(machine, wanderer), "A wanderer is not a target");
        machine.machineCore().remember(wanderer, helper.getLevel().getGameTime());
        helper.assertTrue(TargetRules.mayAttack(machine, wanderer), "Unless it attacked the machine");
        machine.discard();
        clear(helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void tradingTakesTheCostAndGivesTheGoods(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        WandererData.Trade offer = new WandererData.Trade(List.of(new ItemStack(net.minecraft.world.item.Items.EMERALD, 3)),
                new ItemStack(net.minecraft.world.item.Items.BREAD, 4), 1, 0);
        player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.EMERALD, 2));
        helper.assertFalse(WandererTradeMenu.canAfford(player, offer) || WandererTradeMenu.trade(player, offer), "Two emeralds are not enough");
        player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.EMERALD, 2));
        helper.assertTrue(WandererTradeMenu.trade(player, offer), "Four are");
        helper.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.EMERALD) == 1, "Three taken");
        helper.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.BREAD) == 4, "The goods given");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theHeadKeepsItsOffersOverASave(GameTestHelper helper) {
        WandererEntity head = spawn(helper, "loner_with_robot", new BlockPos(4, 2, 4));
        CompoundTag tag = new CompoundTag();
        head.saveWithoutId(tag);
        WandererEntity copy = WandererRegistry.type(head.kind()).create(helper.getLevel());
        copy.load(tag);
        helper.assertTrue(copy.isLeader() && head.groupId().equals(copy.groupId()), "Head and group kept");
        helper.assertTrue(copy.offers().size() == head.offers().size() && !copy.offers().isEmpty(), "Offers kept: " + copy.offers().size());
        helper.assertTrue(ItemStack.matches(copy.offers().get(0).result(), head.offers().get(0).result()), "The same goods");
        helper.assertTrue(copy.variant().equals(head.variant()), "The same look");
        clear(helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theFallenLeaveWrecks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WandererEntity armor = one(helper, WandererRegistry.ARMORED.get(), "mobile", new BlockPos(2, 2, 2));
        WandererEntity android = one(helper, WandererRegistry.ANDROID.get(), "hightech_synthetic_body", new BlockPos(6, 2, 2));
        WandererEntity robot = one(helper, WandererRegistry.ROBOT.get(), "", new BlockPos(4, 2, 6));
        for (WandererEntity w : List.of(armor, android, robot)) {
            w.hurt(level.damageSources().generic(), 1000.0f);
        }
        AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(12);
        helper.succeedWhen(() -> {
            var wrecks = level.getEntitiesOfClass(PowerArmorEntity.class, box);
            helper.assertTrue(wrecks.size() == 1 && wrecks.get(0).isWrecked()
                    && wrecks.get(0).getVariant() == com.projectrebirth.powerarmor.ArmorVariant.MOBILE, "A wrecked mobile armor");
            var remains = level.getEntitiesOfClass(RemainsEntity.class, box).stream().map(RemainsEntity::kind).toList();
            helper.assertTrue(remains.contains(RemainsEntity.Kind.forBody(com.projectrebirth.bodies.BodyClass.HIGHTECH_SYNTHETIC)),
                    "The android's body remains: " + remains);
            helper.assertTrue(remains.contains(RemainsEntity.Kind.WANDERER_ROBOT), "The robot's wreck");
            wrecks.forEach(PowerArmorEntity::discard);
            level.getEntitiesOfClass(RemainsEntity.class, box).forEach(RemainsEntity::discard);
        });
    }

    @GameTest(template = TEMPLATE)
    public static void aHeadThatFallsHandsOverTheGroup(GameTestHelper helper) {
        WandererEntity head = spawn(helper, "android_patrol", new BlockPos(4, 2, 4));
        List<WandererEntity> rest = head.group().stream().filter(m -> m != head).toList();
        helper.assertTrue(!rest.isEmpty(), "A patrol of several");
        head.hurt(helper.getLevel().damageSources().generic(), 1000.0f);
        helper.assertTrue(rest.stream().filter(WandererEntity::isLeader).count() == 1, "One of the rest took over");
        helper.assertTrue(rest.stream().filter(WandererEntity::isLeader).allMatch(m -> !m.offers().isEmpty()), "With its own offers");
        clear(helper);
        AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16);
        helper.getLevel().getEntitiesOfClass(RemainsEntity.class, box).forEach(RemainsEntity::discard);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aGroupGoesAwayAsAWhole(GameTestHelper helper) {
        WandererEntity head = spawn(helper, "caravan", new BlockPos(4, 2, 4));
        List<WandererEntity> members = head.group();
        head.goAway();
        helper.assertTrue(members.stream().allMatch(WandererEntity::isRemoved), "The whole group left");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(RemainsEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16)).isEmpty(),
                "Leaving leaves nothing behind");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theGameRuleSwitchesWanderersOff(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var rule = server.getGameRules().getRule(RebirthGameRules.WANDERERS);
        boolean before = rule.get();
        try {
            rule.set(true, server);
            helper.assertTrue(WandererGroups.enabled(helper.getLevel()) == WastelandConfig.WANDERERS_ENABLED.get(), "On by the rule");
            rule.set(false, server);
            helper.assertFalse(WandererGroups.enabled(helper.getLevel()), "Off by the rule");
        } finally {
            rule.set(before, server);
        }
        helper.succeed();
    }
}
