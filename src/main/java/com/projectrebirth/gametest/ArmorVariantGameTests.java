package com.projectrebirth.gametest;

import java.util.HashSet;
import java.util.List;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.factions.Faction;
import com.projectrebirth.factions.FactionBlocks;
import com.projectrebirth.factions.FactionData;
import com.projectrebirth.factions.FactionService;
import com.projectrebirth.powerarmor.ArmorAbility;
import com.projectrebirth.powerarmor.ArmorVariant;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.powerarmor.PowerArmorRegistry;
import com.projectrebirth.powerarmor.WorkerMining;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Power armor variants and the worker armor's 3x3 mining. Run with {@code .\gradlew runGameTestServer}. */
@GameTestHolder(ProjectRebirth.MOD_ID)
@PrefixGameTestTemplate(false)
public class ArmorVariantGameTests {
    private static final String TEMPLATE = "floor_5x5";
    private static final BlockPos ARMOR_POS = new BlockPos(1, 2, 2);
    /** Centre of the 3x3 wall east of the armor. */
    private static final BlockPos WALL_CENTER = new BlockPos(4, 3, 2);

    private static PowerArmorEntity armor(GameTestHelper helper, ArmorVariant variant) {
        PowerArmorEntity armor = PowerArmorRegistry.POWER_ARMOR.get().create(helper.getLevel());
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(ARMOR_POS));
        armor.setVariant(variant);
        armor.moveTo(at.x, at.y, at.z, -90.0f, 0.0f);
        helper.getLevel().addFreshEntity(armor);
        return armor;
    }

    /** A pilot in the armor, looking east at the wall with the ability slot selected. */
    private static ServerPlayer pilot(GameTestHelper helper, PowerArmorEntity armor) {
        ServerPlayer pilot = helper.makeMockServerPlayerInLevel();
        pilot.setGameMode(GameType.SURVIVAL);
        helper.assertTrue(pilot.startRiding(armor, true), "The pilot must get in");
        ArmorAbility.sync(pilot);
        pilot.getInventory().selected = ArmorAbility.SLOT;
        pilot.setYRot(-90.0f);
        pilot.setXRot(0.0f);
        return pilot;
    }

    private static List<BlockPos> wall(GameTestHelper helper) {
        return WorkerMining.area(helper.absolutePos(WALL_CENTER), new Vec3(1, 0, 0));
    }

    private static int dropped(GameTestHelper helper, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8),
                entity -> entity.getItem().is(item)).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    @GameTest(template = TEMPLATE)
    public static void eachVariantHasItsOwnStats(GameTestHelper helper) {
        for (ArmorVariant variant : ArmorVariant.values()) {
            PowerArmorEntity armor = armor(helper, variant);
            RebirthConfig.ArmorStats stats = RebirthConfig.armorStats(variant);
            helper.assertTrue(armor.getMaxStructural() == stats.maxStructural().get().floatValue()
                    && armor.getStructural() == armor.getMaxStructural(), variant + ": structural integrity from its config");
            helper.assertTrue(armor.getMaxCritical() == stats.maxCritical().get().floatValue(), variant + ": critical integrity");
            helper.assertTrue(armor.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) == stats.movementSpeed().get(), variant + ": speed");
            ServerPlayer pilot = pilot(helper, armor);
            helper.assertTrue(Math.abs(pilot.getAttributeValue(Attributes.ATTACK_DAMAGE) - 1.0 - stats.pilotAttackDamageBonus().get()) < 0.001,
                    variant + ": punch of the pilot");
            pilot.stopRiding();
            armor.discard();
        }
        helper.assertTrue(RebirthConfig.armorStats(ArmorVariant.MOBILE).movementSpeed().get() > RebirthConfig.armorStats(ArmorVariant.COMBAT).movementSpeed().get()
                && RebirthConfig.armorStats(ArmorVariant.WORKER).maxStructural().get() < RebirthConfig.armorStats(ArmorVariant.COMBAT).maxStructural().get(),
                "Defaults follow the plan: mobile is faster, worker is weaker than combat");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void armorFromBeforeVariantsIsCombatAndPackingKeepsTheVariant(GameTestHelper helper) {
        PowerArmorEntity old = armor(helper, ArmorVariant.WORKER);
        CompoundTag tag = new CompoundTag();
        old.saveWithoutId(tag);
        tag.remove("Variant");
        PowerArmorEntity loaded = PowerArmorRegistry.POWER_ARMOR.get().create(helper.getLevel());
        loaded.load(tag);
        helper.assertTrue(loaded.getVariant() == ArmorVariant.COMBAT, "An armor saved without a variant (0.1.0) is a combat armor");

        PowerArmorEntity mobile = armor(helper, ArmorVariant.MOBILE);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PowerArmorRegistry.REPAIR_WRENCH.get()));
        owner.setShiftKeyDown(true);
        mobile.interact(owner, InteractionHand.MAIN_HAND);
        owner.getCooldowns().removeCooldown(PowerArmorRegistry.REPAIR_WRENCH.get());
        mobile.interact(owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(owner.getInventory().countItem(PowerArmorRegistry.item(ArmorVariant.MOBILE)) == 1,
                "A packed mobile armor must be a mobile armor item");
        old.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void areaIsAcrossTheLookDirection(GameTestHelper helper) {
        BlockPos center = new BlockPos(10, 64, 10);
        for (Vec3 look : new Vec3[] {new Vec3(1, 0, 0), new Vec3(0, -1, 0), new Vec3(0.2, 0.1, -1)}) {
            List<BlockPos> area = WorkerMining.area(center, look);
            helper.assertTrue(area.size() == 9 && new HashSet<>(area).size() == 9 && area.getFirst().equals(center), "Nine different blocks, centre first");
            boolean flat = look.y != 0 && Math.abs(look.y) > 0.5
                    ? area.stream().allMatch(pos -> pos.getY() == center.getY())
                    : Math.abs(look.x) > Math.abs(look.z) ? area.stream().allMatch(pos -> pos.getX() == center.getX())
                    : area.stream().allMatch(pos -> pos.getZ() == center.getZ());
            helper.assertTrue(flat, "The square must face the look direction " + look);
        }
        helper.succeed();
    }

    /**
     * The centre is obsidian; around it: stone, bedrock and a faction core of another faction. Stone breaks and drops
     * cobblestone, bedrock is unbreakable, and the core is protected from an outsider like any block broken alone.
     */
    @GameTest(template = TEMPLATE)
    public static void workerArmorMinesThreeByThreeButNotWhatIsProtected(GameTestHelper helper) {
        FactionService.adminDisband(helper.getLevel().getServer(), "Mine Lambda");
        List<BlockPos> wall = wall(helper);
        for (BlockPos pos : wall) {
            helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        }
        helper.getLevel().setBlockAndUpdate(wall.get(0), Blocks.OBSIDIAN.defaultBlockState());
        BlockPos bedrock = wall.get(1);
        BlockPos core = wall.get(2);
        helper.getLevel().setBlockAndUpdate(bedrock, Blocks.BEDROCK.defaultBlockState());
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        FactionService.create(owner, "Mine Lambda", ChatFormatting.GREEN);
        helper.getLevel().setBlockAndUpdate(core, FactionBlocks.FACTION_CORE.get().defaultBlockState());
        FactionService.addCore(owner, helper.getLevel(), core);

        PowerArmorEntity armor = armor(helper, ArmorVariant.WORKER);
        ServerPlayer pilot = pilot(helper, armor);
        helper.assertTrue(WorkerMining.miningArmor(pilot) == armor, "The ability slot of a worker armor mines");
        pilot.gameMode.destroyBlock(wall.get(0));

        for (BlockPos pos : wall) {
            boolean shouldStay = pos.equals(bedrock) || pos.equals(core);
            helper.assertTrue(helper.getLevel().getBlockState(pos).isAir() != shouldStay,
                    pos + (shouldStay ? " must stay" : " must be broken") + ", is " + helper.getLevel().getBlockState(pos));
        }
        helper.assertTrue(dropped(helper, Items.OBSIDIAN) == 1, "The obsidian drops as with a diamond pickaxe");
        helper.assertTrue(dropped(helper, Items.COBBLESTONE) == 6, "Six stone blocks drop cobblestone, got " + dropped(helper, Items.COBBLESTONE));
        Faction faction = FactionData.get(helper.getLevel().getServer()).all().stream()
                .filter(f -> f.name().equals("Mine Lambda")).findFirst().orElseThrow();
        helper.assertFalse(faction.cores().isEmpty(), "The faction keeps its core");
        FactionService.adminDisband(helper.getLevel().getServer(), "Mine Lambda");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void singleModeToolsAndOtherArmorsMineOneBlock(GameTestHelper helper) {
        List<BlockPos> wall = wall(helper);
        PowerArmorEntity worker = armor(helper, ArmorVariant.WORKER);
        ServerPlayer pilot = pilot(helper, worker);
        for (int round = 0; round < 3; round++) {
            for (BlockPos pos : wall) {
                helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            }
            if (round == 0) {
                worker.setWideMining(false);
            } else if (round == 1) {
                worker.setWideMining(true);
                pilot.getInventory().selected = 0;
                pilot.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            } else {
                pilot.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                pilot.getInventory().selected = ArmorAbility.SLOT;
                pilot.stopRiding();
                worker.discard();
                PowerArmorEntity combat = armor(helper, ArmorVariant.COMBAT);
                helper.assertTrue(pilot.startRiding(combat, true), "The pilot must get into the combat armor");
                pilot.setYRot(-90.0f);
                ArmorAbility.sync(pilot);
            }
            pilot.gameMode.destroyBlock(wall.get(0));
            long broken = wall.stream().filter(pos -> helper.getLevel().getBlockState(pos).isAir()).count();
            helper.assertTrue(broken == 1, "Round " + round + ": only the target may break, broke " + broken);
        }
        helper.succeed();
    }
}
