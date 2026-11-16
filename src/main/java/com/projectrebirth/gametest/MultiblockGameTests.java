package com.projectrebirth.gametest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.factions.FactionBlocks;
import com.projectrebirth.factions.FactionService;
import com.projectrebirth.multiblock.AssemblyRecipe;
import com.projectrebirth.multiblock.MultiblockControllerBlockEntity;
import com.projectrebirth.multiblock.MultiblockRegistry;
import com.projectrebirth.multiblock.MultiblockSchema;
import com.projectrebirth.upgrades.InstalledUpgrades;
import com.projectrebirth.upgrades.UpgradeRegistry;
import com.projectrebirth.upgrades.Upgrades;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The multiblock engine (1.7.0, block A), on the plan of the tests ({@code test_frame}: a 3 by 3 frame of iron blocks two layers
 * high, an empty zone in the middle, the controller in the front with an energy port on its left and a cargo port on its right):
 * whole, incomplete, turned; the list the hologram shows; the ports; the stages with a pause without energy and with a broken
 * block; saving; a broken controller giving everything back; the land of another faction.
 */
@GameTestHolder(PlanTests.GARRISON)
@PrefixGameTestTemplate(false)
public class MultiblockGameTests {
    private static final String TEMPLATE = "floor_5x5";
    private static final ResourceLocation TEST = ProjectRebirth.id("test_frame");

    private static MultiblockSchema schema(GameTestHelper helper) {
        MultiblockSchema schema = MultiblockRegistry.schema(helper.getLevel().registryAccess(), TEST);
        helper.assertTrue(schema != null, "The plan of the tests is loaded");
        return schema;
    }

    /** Builds the construction around a controller facing that way; returns the controller's place in the world. */
    private static BlockPos build(GameTestHelper helper, BlockPos controllerAt, Direction facing, boolean controllerOnly) {
        ServerLevel level = helper.getLevel();
        MultiblockSchema schema = schema(helper);
        BlockPos controller = helper.absolutePos(controllerAt);
        for (int y = 0; y < schema.height(); y++) {
            for (int z = 0; z < schema.depth(); z++) {
                for (int x = 0; x < schema.width(); x++) {
                    MultiblockSchema.Cell cell = schema.cell(y, z, x);
                    if (cell == null || cell.zone() || controllerOnly && !cell.controller()) {
                        continue;
                    }
                    BlockState state = cell.controller()
                            ? MultiblockRegistry.CONTROLLER.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing)
                            : cell.display();
                    level.setBlock(schema.worldPos(controller, facing, x, y, z), state, 3);
                }
            }
        }
        controller(helper, controller).setSchema(TEST);
        return controller;
    }

    private static MultiblockControllerBlockEntity controller(GameTestHelper helper, BlockPos absolute) {
        return (MultiblockControllerBlockEntity) helper.getLevel().getBlockEntity(absolute);
    }

    /** A place of the frame: the iron block at the back left of the lower layer. */
    private static BlockPos backLeft(GameTestHelper helper, BlockPos controller) {
        return schema(helper).worldPos(controller, Direction.SOUTH, 0, 0, 0);
    }

    @GameTest(template = TEMPLATE)
    public static void aWholeConstructionIsCompleteAndAWrongOneIsNot(GameTestHelper helper) {
        BlockPos controller = build(helper, new BlockPos(2, 2, 3), Direction.SOUTH, false);
        MultiblockControllerBlockEntity entity = controller(helper, controller);
        MultiblockSchema.Check check = entity.revalidate();
        helper.assertTrue(check.complete(), "Whole: " + check.problems());
        helper.assertTrue(check.energyPorts().size() == 1 && check.cargoPorts().size() == 1 && check.zone().size() == 2, "Its ports and its zone");
        BlockPos corner = backLeft(helper, controller);
        helper.getLevel().setBlock(corner, Blocks.AIR.defaultBlockState(), 3);
        check = entity.revalidate();
        helper.assertTrue(!check.complete() && check.problems().size() == 1 && check.problems().get(0).pos().equals(corner)
                && check.problems().get(0).kind() == MultiblockSchema.ProblemKind.MISSING, "A missing block: " + check.problems());
        helper.getLevel().setBlock(corner, Blocks.DIRT.defaultBlockState(), 3);
        check = entity.revalidate();
        helper.assertTrue(!check.complete() && check.problems().get(0).kind() == MultiblockSchema.ProblemKind.WRONG, "A wrong block");
        helper.getLevel().setBlock(corner, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        BlockPos zone = check.zone().get(0);
        helper.getLevel().setBlock(zone, Blocks.STONE.defaultBlockState(), 3);
        check = entity.revalidate();
        helper.assertTrue(!check.complete() && check.problems().get(0).kind() == MultiblockSchema.ProblemKind.BLOCKED, "The zone must stay empty");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theConstructionTurnsWithTheController(GameTestHelper helper) {
        BlockPos controller = build(helper, new BlockPos(3, 2, 2), Direction.EAST, false);
        MultiblockControllerBlockEntity entity = controller(helper, controller);
        helper.assertTrue(entity.revalidate().complete(), "Built facing east, it is whole facing east");
        MultiblockSchema.Check asSouth = schema(helper).check(helper.getLevel(), controller, Direction.SOUTH, null);
        helper.assertFalse(asSouth.complete(), "The same blocks do not make it facing south");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theHologramListsEveryMissingBlock(GameTestHelper helper) {
        BlockPos controller = build(helper, new BlockPos(2, 2, 3), Direction.SOUTH, true);
        MultiblockSchema schema = schema(helper);
        MultiblockSchema.Check check = schema.check(helper.getLevel(), controller, Direction.SOUTH, null);
        int expected = 0;
        for (int y = 0; y < schema.height(); y++) {
            for (int z = 0; z < schema.depth(); z++) {
                for (int x = 0; x < schema.width(); x++) {
                    MultiblockSchema.Cell cell = schema.cell(y, z, x);
                    if (cell != null && !cell.zone() && !cell.controller()) {
                        expected++;
                        BlockPos pos = schema.worldPos(controller, Direction.SOUTH, x, y, z);
                        helper.assertTrue(check.problems().stream().anyMatch(p -> p.pos().equals(pos) && p.kind() == MultiblockSchema.ProblemKind.MISSING
                                && p.expected().getBlock() == cell.display().getBlock()), "Missing at " + pos + ": " + cell.display());
                    }
                }
            }
        }
        helper.assertTrue(check.problems().size() == expected && expected == 15, "Fifteen blocks to build, got " + check.problems().size());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void thePortsTakeEnergyAndCarryMaterials(GameTestHelper helper) {
        BlockPos controller = build(helper, new BlockPos(2, 2, 3), Direction.SOUTH, false);
        MultiblockControllerBlockEntity entity = controller(helper, controller);
        MultiblockSchema.Check check = entity.revalidate();
        ServerLevel level = helper.getLevel();
        IEnergyStorage energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, check.energyPorts().get(0), null);
        helper.assertTrue(energy != null && energy.receiveEnergy(500, false) == 500 && entity.energy().getEnergyStored() == 500, "Energy through the port");
        IItemHandler cargo = level.getCapability(Capabilities.ItemHandler.BLOCK, check.cargoPorts().get(0), null);
        helper.assertTrue(cargo != null, "The cargo port has a handler");
        ItemStack left = ItemStack.EMPTY;
        for (int i = 0; i < cargo.getSlots(); i++) {
            left = cargo.insertItem(i, new ItemStack(Items.IRON_INGOT, 3), false);
            if (left.isEmpty()) {
                break;
            }
        }
        int held = 0;
        for (int i = MultiblockControllerBlockEntity.FIRST_MATERIAL; i < MultiblockControllerBlockEntity.OUTPUT_SLOT; i++) {
            held += entity.items().getStackInSlot(i).is(Items.IRON_INGOT) ? entity.items().getStackInSlot(i).getCount() : 0;
        }
        helper.assertTrue(left.isEmpty() && held == 3, "Materials through the port into the material slots: " + held);
        entity.items().setStackInSlot(MultiblockControllerBlockEntity.OUTPUT_SLOT, new ItemStack(Items.IRON_BLOCK));
        ItemStack out = ItemStack.EMPTY;
        for (int i = 0; i < cargo.getSlots() && out.isEmpty(); i++) {
            out = cargo.extractItem(i, 1, false);
        }
        helper.assertTrue(out.is(Items.IRON_BLOCK), "The finished item out through the port: " + out);
        level.setBlock(backLeft(helper, controller), Blocks.AIR.defaultBlockState(), 3);
        entity.revalidate();
        helper.assertTrue(level.getCapability(Capabilities.EnergyStorage.BLOCK, check.energyPorts().get(0), null) == null,
                "A port of an incomplete construction serves nothing");
        helper.succeed();
    }

    private static ItemStack bodyWithPlating() {
        ItemStack body = new ItemStack(BuiltInRegistries.ITEM.get(ProjectRebirth.id("basic_robot_body")));
        Upgrades.putOnStack(body, InstalledUpgrades.EMPTY.with(new InstalledUpgrades.Installed(Optional.empty(), new ItemStack(UpgradeRegistry.COPPER_PLATING.get()))));
        return body;
    }

    private static MultiblockControllerBlockEntity ready(GameTestHelper helper, BlockPos controller) {
        MultiblockControllerBlockEntity entity = controller(helper, controller);
        entity.revalidate();
        entity.select(ProjectRebirth.id("test_assembly"));
        entity.items().setStackInSlot(MultiblockControllerBlockEntity.BASE_SLOT, bodyWithPlating());
        entity.items().setStackInSlot(MultiblockControllerBlockEntity.FIRST_MATERIAL, new ItemStack(Items.IRON_INGOT, 2));
        entity.items().setStackInSlot(MultiblockControllerBlockEntity.FIRST_MATERIAL + 1, new ItemStack(Items.REDSTONE, 1));
        return entity;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void theAssemblyGoesByStagesAndPausesWithoutEnergyOrAWholeConstruction(GameTestHelper helper) {
        BlockPos controller = build(helper, new BlockPos(2, 2, 3), Direction.SOUTH, false);
        MultiblockControllerBlockEntity entity = ready(helper, controller);
        BlockPos corner = backLeft(helper, controller);
        int[] frozen = new int[1];
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(entity.stage() == 0 && entity.ticks() == 0 && entity.state() == MultiblockControllerBlockEntity.STATE_NO_ENERGY,
                    "Started, then paused without energy: stage " + entity.stage() + ", state " + entity.state());
            helper.assertTrue(entity.items().getStackInSlot(MultiblockControllerBlockEntity.BASE_SLOT).isEmpty()
                    && entity.consumedView().stream().mapToInt(ItemStack::getCount).sum() == 2, "The body and the frame's iron are taken");
            entity.energy().load(entity.energy().getMaxEnergyStored());
        });
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(entity.ticks() > 0, "With energy it works");
            helper.getLevel().setBlock(corner, Blocks.AIR.defaultBlockState(), 3);
        });
        helper.runAfterDelay(17, () -> {
            helper.assertTrue(entity.state() == MultiblockControllerBlockEntity.STATE_INCOMPLETE, "A broken block pauses it: " + entity.state());
            frozen[0] = entity.ticks();
        });
        helper.runAfterDelay(27, () -> {
            helper.assertTrue(entity.ticks() == frozen[0] && entity.stage() == 0, "Nothing is lost and nothing moves while it is incomplete");
            helper.getLevel().setBlock(corner, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        });
        helper.runAfterDelay(100, () -> {
            ItemStack out = entity.items().getStackInSlot(MultiblockControllerBlockEntity.OUTPUT_SLOT);
            helper.assertTrue(out.is(BuiltInRegistries.ITEM.get(ProjectRebirth.id("improved_robot_body"))), "The result: " + out);
            InstalledUpgrades kept = Upgrades.ofStack(out);
            helper.assertTrue(kept.entries().stream().anyMatch(e -> e.stack().is(UpgradeRegistry.COPPER_PLATING.get())), "The plates moved onto it");
            helper.assertTrue(entity.stage() == -1 && entity.items().getStackInSlot(MultiblockControllerBlockEntity.FIRST_MATERIAL + 1).isEmpty(),
                    "Done, the second stage's redstone used");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void theWorkIsSavedAndABrokenControllerGivesEverythingBack(GameTestHelper helper) {
        BlockPos controller = build(helper, new BlockPos(2, 2, 3), Direction.SOUTH, false);
        MultiblockControllerBlockEntity entity = ready(helper, controller);
        entity.energy().load(5);
        helper.runAfterDelay(5, () -> {
            ServerLevel level = helper.getLevel();
            CompoundTag saved = entity.saveWithFullMetadata(level.registryAccess());
            BlockEntity loaded = BlockEntity.loadStatic(controller, entity.getBlockState(), saved, level.registryAccess());
            helper.assertTrue(loaded instanceof MultiblockControllerBlockEntity copy && copy.stage() == entity.stage() && copy.ticks() == entity.ticks()
                    && copy.consumedView().size() == entity.consumedView().size() && !copy.baseView().isEmpty(), "The stage, its time, the taken materials saved");
            level.destroyBlock(controller, false);
            List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(controller).inflate(3));
            int iron = drops.stream().filter(d -> d.getItem().is(Items.IRON_INGOT)).mapToInt(d -> d.getItem().getCount()).sum();
            boolean body = drops.stream().anyMatch(d -> d.getItem().is(BuiltInRegistries.ITEM.get(ProjectRebirth.id("basic_robot_body"))));
            boolean redstone = drops.stream().anyMatch(d -> d.getItem().is(Items.REDSTONE));
            helper.assertTrue(iron == 2 && body && redstone, "The iron of the stage begun, the body and the redstone fall out: iron " + iron);
            drops.forEach(ItemEntity::discard);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "multiblock_territory")
    public static void aConstructionOnTheLandOfAnotherFactionDoesNotCount(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FactionService.adminDisband(server, "Builders Mu");
        ServerPlayer member = helper.makeMockServerPlayerInLevel();
        member.setGameMode(GameType.SURVIVAL);
        FactionService.create(member, "Builders Mu", ChatFormatting.GOLD);
        BlockPos core = helper.absolutePos(new BlockPos(0, 2, 0));
        helper.getLevel().setBlockAndUpdate(core, FactionBlocks.FACTION_CORE.get().defaultBlockState());
        FactionService.addCore(member, helper.getLevel(), core);
        try {
            BlockPos controller = build(helper, new BlockPos(2, 2, 3), Direction.SOUTH, false);
            MultiblockControllerBlockEntity entity = controller(helper, controller);
            entity.setOwner(UUID.randomUUID());
            MultiblockSchema.Check check = entity.revalidate();
            helper.assertTrue(!check.complete() && check.problems().stream().allMatch(p -> p.kind() == MultiblockSchema.ProblemKind.FOREIGN),
                    "A stranger's construction on the faction's land: " + check.problems().size());
            entity.setOwner(member.getUUID());
            helper.assertTrue(entity.revalidate().complete(), "A member's is whole");
        } finally {
            FactionService.adminDisband(server, "Builders Mu");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void thePlansAndRecipesOfTheTestsExistOnlyInTests(GameTestHelper helper) {
        MultiblockSchema schema = schema(helper);
        helper.assertTrue(schema.testOnly() && schema.available(), "The plan of the tests is marked and works while tests run");
        List<AssemblyRecipe> recipes = MultiblockRegistry.recipesFor(helper.getLevel(), TEST).stream().map(holder -> holder.value()).toList();
        helper.assertTrue(recipes.size() == 2 && recipes.stream().allMatch(AssemblyRecipe::testOnly), "Its two recipes, both of the tests");
        helper.succeed();
    }
}
