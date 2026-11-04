package com.projectrebirth.gametest;

import java.util.List;
import java.util.Optional;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.BodyBlocks;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyEntities;
import com.projectrebirth.bodies.BodyItems;
import com.projectrebirth.bodies.BodyRegistry;
import com.projectrebirth.bodies.TransferCapsuleBlock;
import com.projectrebirth.bodies.TransferService;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.powerarmor.ArmorVariant;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.powerarmor.PowerArmorRegistry;
import com.projectrebirth.upgrades.ArmorSlot;
import com.projectrebirth.upgrades.InstalledUpgrades;
import com.projectrebirth.upgrades.UpgradeDefinition;
import com.projectrebirth.upgrades.UpgradeHost;
import com.projectrebirth.upgrades.UpgradeLayout;
import com.projectrebirth.upgrades.UpgradeRegistry;
import com.projectrebirth.upgrades.Upgrades;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The shared upgrade system on bodies and armors. Run with {@code .\gradlew runGameTestServer}. */
@GameTestHolder(ProjectRebirth.MOD_ID)
@PrefixGameTestTemplate(false)
public class UpgradeGameTests {
    private static final String TEMPLATE = "floor_5x5";
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    private static RegistryAccess access(GameTestHelper helper) {
        return helper.getLevel().registryAccess();
    }

    private static ArtificialBodyEntity body(GameTestHelper helper, BodyClass bodyClass) {
        return helper.spawn(BodyEntities.type(bodyClass), POS);
    }

    private static PowerArmorEntity armor(GameTestHelper helper) {
        return helper.spawn(PowerArmorRegistry.POWER_ARMOR.get(), POS);
    }

    private static ItemStack stack(Item item) {
        return new ItemStack(item);
    }

    private static void assertFits(GameTestHelper helper, UpgradeHost host, ItemStack stack, ArmorSlot slot) {
        Upgrades.Problem problem = Upgrades.install(access(helper), host, stack, slot);
        helper.assertTrue(problem == null, "Must fit: " + stack + " " + slot + ", refused: " + problem);
    }

    private static void assertRefused(GameTestHelper helper, UpgradeHost host, ItemStack stack, ArmorSlot slot, Upgrades.Problem expected) {
        int before = stack.getCount();
        InstalledUpgrades installed = host.upgrades();
        Upgrades.Problem problem = Upgrades.install(access(helper), host, stack, slot);
        helper.assertTrue(problem == expected, "Expected " + expected + " for " + stack + ", got " + problem);
        helper.assertTrue(stack.getCount() == before && host.upgrades().equals(installed), "A refused upgrade must change nothing");
    }

    /** How many of this item lie on the ground around the test. */
    private static int dropped(GameTestHelper helper, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8),
                        entity -> entity.getItem().is(item))
                .stream().mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static ItemStack onlyPacked(GameTestHelper helper, Player player, Item item) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                return stack;
            }
        }
        helper.fail("The packed item must be in the inventory");
        return ItemStack.EMPTY;
    }

    private static void place(GameTestHelper helper, Player player, ItemStack packed) {
        player.setItemInHand(InteractionHand.MAIN_HAND, packed);
        BlockPos floor = helper.absolutePos(new BlockPos(1, 1, 1));
        packed.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)));
    }

    // ---------------------------------------------------------------- rules

    @GameTest(template = TEMPLATE)
    public static void slotsPerBodyClassComeFromTheConfigDefaults(GameTestHelper helper) {
        for (BodyClass bodyClass : BodyClass.values()) {
            helper.assertTrue(RebirthConfig.bodyStats(bodyClass).upgradeSlots().get() == bodyClass.ordinal() + 1,
                    "Default slots of " + bodyClass.id());
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void installAndRemoveMoveTheItem(GameTestHelper helper) {
        ArtificialBodyEntity body = body(helper, BodyClass.ADVANCED_SYNTHETIC);
        ItemStack source = new ItemStack(UpgradeRegistry.IRON_PLATING.get(), 3);
        assertFits(helper, body, source, null);
        helper.assertTrue(source.getCount() == 2, "One piece must leave the source");
        helper.assertTrue(body.upgrades().entries().size() == 1 && body.upgrades().entries().getFirst().stack().getCount() == 1,
                "One piece must be installed");
        ItemStack back = Upgrades.remove(body, 0);
        helper.assertTrue(back.is(UpgradeRegistry.IRON_PLATING.get()) && back.getCount() == 1, "The piece must come back");
        helper.assertTrue(body.upgrades().isEmpty(), "Nothing may stay installed");
        helper.assertTrue(Upgrades.remove(body, 0).isEmpty(), "Removing twice must give nothing");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void bodyRulesRefuseWhatDoesNotFit(GameTestHelper helper) {
        ArtificialBodyEntity basic = body(helper, BodyClass.BASIC_ROBOT);
        assertRefused(helper, basic, stack(Items.STICK), null, Upgrades.Problem.NOT_AN_UPGRADE);
        assertRefused(helper, basic, stack(UpgradeRegistry.FLOODLIGHT.get()), null, Upgrades.Problem.WRONG_HOST);
        assertFits(helper, basic, stack(UpgradeRegistry.SEALING_MODULE.get()), null);
        assertRefused(helper, basic, stack(UpgradeRegistry.JETPACK.get()), null, Upgrades.Problem.NO_FREE_SLOTS);
        assertRefused(helper, basic, stack(UpgradeRegistry.SEALING_MODULE.get()), null, Upgrades.Problem.TOO_MANY);

        ArtificialBodyEntity premium = body(helper, BodyClass.PREMIUM_SYNTHETIC);
        assertFits(helper, premium, stack(UpgradeRegistry.COPPER_PLATING.get()), null);
        assertFits(helper, premium, stack(UpgradeRegistry.COPPER_PLATING.get()), null);
        assertRefused(helper, premium, stack(UpgradeRegistry.IRON_PLATING.get()), null, Upgrades.Problem.OTHER_MATERIAL);
        assertFits(helper, premium, stack(UpgradeRegistry.JETPACK.get()), null);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void armorSlotsAreNamedAndComeFromTheData(GameTestHelper helper) {
        PowerArmorEntity armor = armor(helper);
        assertRefused(helper, armor, stack(UpgradeRegistry.JETPACK.get()), ArmorSlot.CHEST_BACK, Upgrades.Problem.WRONG_HOST);
        assertRefused(helper, armor, stack(UpgradeRegistry.FLOODLIGHT.get()), null, Upgrades.Problem.NO_SLOT_CHOSEN);
        assertRefused(helper, armor, stack(UpgradeRegistry.FLOODLIGHT.get()), ArmorSlot.RIGHT_LEG, Upgrades.Problem.WRONG_SLOT);
        // Different materials on different parts are allowed on an armor.
        assertFits(helper, armor, stack(UpgradeRegistry.COPPER_PLATING.get()), ArmorSlot.RIGHT_LEG);
        assertFits(helper, armor, stack(UpgradeRegistry.TITANIUM_PLATING.get()), ArmorSlot.LEFT_LEG);
        assertRefused(helper, armor, stack(UpgradeRegistry.COMPRESSED_AIR_MODULE.get()), ArmorSlot.LEFT_LEG, Upgrades.Problem.SLOT_TAKEN);
        assertFits(helper, armor, stack(UpgradeRegistry.COMPRESSED_AIR_MODULE.get()), ArmorSlot.CHEST_BACK);
        assertRefused(helper, armor, stack(Items.RED_BANNER), ArmorSlot.CHEST_BACK, Upgrades.Problem.SLOT_TAKEN);
        helper.assertTrue(armor.upgrades().indexOf(ArmorSlot.LEFT_LEG) >= 0 && armor.upgrades().indexOf(ArmorSlot.HEAD) < 0, "Find by slot");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void incompatibleKindsAreRefusedBothWays(GameTestHelper helper) {
        UpgradeDefinition jetpack = new UpgradeDefinition(HolderSet.direct(UpgradeRegistry.JETPACK.getDelegate()), "jetpack", Optional.empty(),
                Optional.of(new UpgradeDefinition.BodyFit(1, 1, false)), List.of(), List.of());
        UpgradeDefinition sealing = new UpgradeDefinition(HolderSet.direct(UpgradeRegistry.SEALING_MODULE.getDelegate()), "sealing", Optional.empty(),
                Optional.of(new UpgradeDefinition.BodyFit(1, 1, false)), List.of(), List.of("jetpack"));
        UpgradeLayout layout = new UpgradeLayout.Counted(5);
        InstalledUpgrades withJetpack = InstalledUpgrades.EMPTY.with(new InstalledUpgrades.Installed(Optional.empty(), stack(UpgradeRegistry.JETPACK.get())));
        InstalledUpgrades withSealing = InstalledUpgrades.EMPTY.with(new InstalledUpgrades.Installed(Optional.empty(), stack(UpgradeRegistry.SEALING_MODULE.get())));
        Optional<UpgradeDefinition> none = Optional.empty();
        helper.assertTrue(Upgrades.problem(s -> s.is(UpgradeRegistry.JETPACK.get()) ? Optional.of(jetpack) : none, sealing,
                stack(UpgradeRegistry.SEALING_MODULE.get()), withJetpack, layout, null) == Upgrades.Problem.INCOMPATIBLE, "Named by the new one");
        helper.assertTrue(Upgrades.problem(s -> s.is(UpgradeRegistry.SEALING_MODULE.get()) ? Optional.of(sealing) : none, jetpack,
                stack(UpgradeRegistry.JETPACK.get()), withSealing, layout, null) == Upgrades.Problem.INCOMPATIBLE, "Named by the installed one");
        helper.succeed();
    }

    // ---------------------------------------------------------------- the same pieces, wherever the body or armor goes

    @GameTest(template = TEMPLATE)
    public static void bodyUpgradesTravelInTheItemWithoutCopies(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ArtificialBodyEntity body = body(helper, BodyClass.HIGHTECH_SYNTHETIC);
        body.setOwner(player.getUUID());
        assertFits(helper, body, stack(UpgradeRegistry.TITANIUM_PLATING.get()), null);
        assertFits(helper, body, stack(UpgradeRegistry.JETPACK.get()), null);
        InstalledUpgrades installed = body.upgrades();

        player.setShiftKeyDown(true);
        body.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(body.isRemoved(), "Packing must remove the body");
        helper.assertTrue(dropped(helper, UpgradeRegistry.JETPACK.get()) == 0, "Packing must not drop upgrades");
        ItemStack packed = onlyPacked(helper, player, BodyItems.item(BodyClass.HIGHTECH_SYNTHETIC));
        helper.assertTrue(Upgrades.ofStack(packed).equals(installed), "The item must carry the upgrades");

        place(helper, player, packed);
        helper.assertTrue(packed.isEmpty(), "Placing must use up the item");
        List<ArtificialBodyEntity> placed = helper.getLevel().getEntitiesOfClass(ArtificialBodyEntity.class,
                new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8), ArtificialBodyEntity::isAlive);
        helper.assertTrue(placed.size() == 1 && placed.getFirst().upgrades().equals(installed), "The placed body must have them back");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void armorUpgradesTravelInTheItemWithoutCopies(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PowerArmorEntity armor = armor(helper);
        armor.setVariant(ArmorVariant.MOBILE);
        armor.setOwner(player.getUUID());
        assertFits(helper, armor, stack(UpgradeRegistry.IRON_PLATING.get()), ArmorSlot.LEFT_ARM);
        InstalledUpgrades installed = armor.upgrades();

        player.setItemInHand(InteractionHand.MAIN_HAND, stack(PowerArmorRegistry.REPAIR_WRENCH.get()));
        player.setShiftKeyDown(true);
        armor.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(armor.isRemoved(), "Packing must remove the armor");
        ItemStack packed = onlyPacked(helper, player, PowerArmorRegistry.item(ArmorVariant.MOBILE));
        helper.assertTrue(Upgrades.ofStack(packed).equals(installed), "The item must carry the upgrades");

        place(helper, player, packed);
        helper.assertTrue(packed.isEmpty(), "Placing must use up the item");
        List<PowerArmorEntity> placed = helper.getLevel().getEntitiesOfClass(PowerArmorEntity.class,
                new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8), PowerArmorEntity::isAlive);
        helper.assertTrue(placed.size() == 1 && placed.getFirst().upgrades().equals(installed), "The placed armor must have them back");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void packedHostChangesTheStack(GameTestHelper helper) {
        ItemStack packed = stack(BodyItems.item(BodyClass.BASIC_ROBOT));
        UpgradeHost host = UpgradeHost.ofPacked(packed);
        helper.assertTrue(UpgradeHost.ofPacked(stack(Items.STICK)) == null, "Other items hold no upgrades");
        assertFits(helper, host, stack(UpgradeRegistry.SEALING_MODULE.get()), null);
        helper.assertTrue(packed.has(UpgradeRegistry.INSTALLED.get()), "The stack must hold it");
        helper.assertTrue(Upgrades.remove(host, 0).is(UpgradeRegistry.SEALING_MODULE.get()), "It must come out");
        helper.assertFalse(packed.has(UpgradeRegistry.INSTALLED.get()), "An empty list must not stay on the item, so that items still stack and compare");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void upgradesSurviveSavingAndKeepTheirData(GameTestHelper helper) {
        ArtificialBodyEntity body = body(helper, BodyClass.PREMIUM_SYNTHETIC);
        assertFits(helper, body, stack(UpgradeRegistry.COPPER_PLATING.get()), null);
        CompoundTag saved = body.saveWithoutId(new CompoundTag());
        ArtificialBodyEntity loaded = BodyEntities.type(BodyClass.PREMIUM_SYNTHETIC).create(helper.getLevel());
        loaded.load(saved);
        helper.assertTrue(loaded.upgrades().equals(body.upgrades()) && !loaded.upgrades().isEmpty(), "A body must keep its upgrades");

        PowerArmorEntity armor = armor(helper);
        ItemStack banner = stack(Items.RED_BANNER);
        banner.set(DataComponents.CUSTOM_NAME, Component.literal("Flag of the test"));
        assertFits(helper, armor, banner.copy(), ArmorSlot.CHEST_BACK);
        PowerArmorEntity loadedArmor = PowerArmorRegistry.POWER_ARMOR.get().create(helper.getLevel());
        loadedArmor.load(armor.saveWithoutId(new CompoundTag()));
        helper.assertTrue(loadedArmor.upgrades().equals(armor.upgrades()), "An armor must keep its upgrades");
        ItemStack back = Upgrades.remove(loadedArmor, loadedArmor.upgrades().indexOf(ArmorSlot.CHEST_BACK));
        helper.assertTrue(ItemStack.matches(back, banner), "A banner must come back with all its data");
        helper.succeed();
    }

    // ---------------------------------------------------------------- destruction

    @GameTest(template = TEMPLATE)
    public static void destroyedBodyDropsItsUpgradesOnce(GameTestHelper helper) {
        ArtificialBodyEntity body = body(helper, BodyClass.ADVANCED_SYNTHETIC);
        assertFits(helper, body, stack(UpgradeRegistry.SEALING_MODULE.get()), null);
        body.hurt(new DamageSource(helper.getLevel().damageSources().generic().typeHolder()), 1000.0f);
        helper.assertTrue(body.isDeadOrDying(), "The body must be destroyed");
        helper.assertTrue(dropped(helper, UpgradeRegistry.SEALING_MODULE.get()) == 1, "One module must drop");
        helper.assertTrue(body.upgrades().isEmpty(), "Nothing may stay on the body");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void wreckKeepsUpgradesUntilDismantled(GameTestHelper helper) {
        PowerArmorEntity armor = armor(helper);
        assertFits(helper, armor, stack(UpgradeRegistry.COPPER_PLATING.get()), ArmorSlot.RIGHT_ARM);
        armor.setIntegrity(0.0f, 0.0f);
        helper.assertTrue(armor.isWrecked(), "The armor must become a wreck");
        helper.assertTrue(dropped(helper, UpgradeRegistry.COPPER_PLATING.get()) == 0, "A wreck keeps its upgrades");

        Player mechanic = helper.makeMockPlayer(GameType.SURVIVAL);
        mechanic.setItemInHand(InteractionHand.MAIN_HAND, stack(PowerArmorRegistry.REPAIR_WRENCH.get()));
        mechanic.setShiftKeyDown(true);
        armor.interact(mechanic, InteractionHand.MAIN_HAND);
        helper.assertTrue(armor.isRemoved(), "Dismantling must remove the wreck");
        helper.assertTrue(dropped(helper, UpgradeRegistry.COPPER_PLATING.get()) == 1, "Dismantling must drop the plating once");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void killedArmorDropsItsUpgrades(GameTestHelper helper) {
        PowerArmorEntity armor = armor(helper);
        assertFits(helper, armor, stack(UpgradeRegistry.FLOODLIGHT.get()), ArmorSlot.HEAD);
        armor.kill();
        helper.assertTrue(armor.isRemoved(), "/kill must remove the armor");
        helper.assertTrue(dropped(helper, UpgradeRegistry.FLOODLIGHT.get()) == 1, "The floodlight must drop once");
        helper.succeed();
    }

    // ---------------------------------------------------------------- a controlled body: the player holds its upgrades

    private static ServerPlayer playerNextToCapsule(GameTestHelper helper) {
        BlockState lower = BodyBlocks.TRANSFER_CAPSULE.get().defaultBlockState()
                .setValue(TransferCapsuleBlock.FACING, Direction.NORTH).setValue(TransferCapsuleBlock.HALF, DoubleBlockHalf.LOWER);
        helper.setBlock(BlockPos.ZERO.above(2), lower);
        helper.setBlock(BlockPos.ZERO.above(3), lower.setValue(TransferCapsuleBlock.HALF, DoubleBlockHalf.UPPER));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos standAt = helper.absolutePos(new BlockPos(1, 2, 0));
        player.moveTo(standAt.getX() + 0.5, standAt.getY(), standAt.getZ() + 0.5);
        return player;
    }

    private static void transfer(GameTestHelper helper, ServerPlayer player, ArtificialBodyEntity body) {
        TransferService.TransferResult result = TransferService.transferFromCapsule(player, helper.absolutePos(BlockPos.ZERO.above(2)), Direction.NORTH, body.getUUID());
        helper.assertTrue(result == TransferService.TransferResult.SUCCESS, "Transfer must work, got " + result);
    }

    @GameTest(template = TEMPLATE)
    public static void transferMovesUpgradesThroughThePlayer(GameTestHelper helper) {
        ServerPlayer player = playerNextToCapsule(helper);
        ArtificialBodyEntity body = body(helper, BodyClass.IMPROVED_ROBOT);
        body.setOwner(player.getUUID());
        BodyRegistry.get(player.server).updateStanding(body);
        assertFits(helper, body, stack(UpgradeRegistry.JETPACK.get()), null);
        InstalledUpgrades installed = body.upgrades();

        transfer(helper, player, body);
        helper.assertTrue(body.isRemoved() && body.upgrades().isEmpty(), "The old entity must have given them away");
        helper.assertTrue(player.getData(UpgradeRegistry.CONTROLLED_BODY).equals(installed), "The player must hold them");

        helper.assertTrue(TransferService.returnToHumanBody(player) == TransferService.ReturnResult.SUCCESS, "Return must work");
        helper.assertTrue(player.getData(UpgradeRegistry.CONTROLLED_BODY).isEmpty(), "The human must hold none");
        List<ArtificialBodyEntity> standing = helper.getLevel().getEntitiesOfClass(ArtificialBodyEntity.class,
                new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8), ArtificialBodyEntity::isAlive);
        helper.assertTrue(standing.size() == 1 && standing.getFirst().upgrades().equals(installed), "The body must have them again");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void destroyedControlledBodyDropsItsUpgradesOnce(GameTestHelper helper) {
        ServerPlayer player = playerNextToCapsule(helper);
        ArtificialBodyEntity body = body(helper, BodyClass.IMPROVED_ROBOT);
        body.setOwner(player.getUUID());
        BodyRegistry.get(player.server).updateStanding(body);
        assertFits(helper, body, stack(UpgradeRegistry.SEALING_MODULE.get()), null);
        transfer(helper, player, body);

        player.kill();
        helper.assertTrue(TransferService.state(player).isHuman(), "The player must be back in the human body");
        helper.assertTrue(player.getData(UpgradeRegistry.CONTROLLED_BODY).isEmpty(), "The human must hold none");
        helper.assertTrue(dropped(helper, UpgradeRegistry.SEALING_MODULE.get()) == 1, "The module must drop once");
        helper.succeed();
    }
}
