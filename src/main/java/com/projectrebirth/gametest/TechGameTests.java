package com.projectrebirth.gametest;

import java.util.List;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.technology.FabricationRecipe;
import com.projectrebirth.technology.Metal;
import com.projectrebirth.technology.Station;
import com.projectrebirth.technology.StationBlockEntity;
import com.projectrebirth.technology.TechBlocks;
import com.projectrebirth.technology.TechItems;
import com.projectrebirth.technology.TechRecipes;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server-side checks for resources and production. */
@GameTestHolder(ProjectRebirth.MOD_ID)
@PrefixGameTestTemplate(false)
public class TechGameTests {
    private static final String TEMPLATE = "floor_3x3";
    private static final BlockPos STATION_POS = new BlockPos(1, 2, 1);

    /** Every ore drops its raw material to a fitting pickaxe and nothing to bare hands. */
    @GameTest(template = TEMPLATE)
    public static void oresDropRawMaterial(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        for (Metal metal : Metal.values()) {
            for (Block ore : List.of(TechBlocks.ORES.get(metal).get(), TechBlocks.DEEPSLATE_ORES.get(metal).get())) {
                BlockState state = ore.defaultBlockState();
                List<ItemStack> drops = Block.getDrops(state, level, pos, null, null, pickaxe);
                helper.assertTrue(drops.size() == 1 && drops.get(0).is(TechItems.RAW.get(metal).get()),
                        ore + " should drop raw " + metal.id() + ", dropped " + drops);
                helper.assertTrue(pickaxe.isCorrectToolForDrops(state), "Iron pickaxe must mine " + ore);
                helper.assertFalse(new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(state),
                        "Wooden pickaxe must not mine " + ore);
            }
        }
        helper.succeed();
    }

    /** Raw material and ore smelt into the ingot. */
    @GameTest(template = TEMPLATE)
    public static void rawMaterialsSmeltIntoIngots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (Metal metal : Metal.values()) {
            for (ItemStack input : List.of(new ItemStack(TechItems.RAW.get(metal).get()), new ItemStack(TechItems.ORES.get(metal).get()))) {
                ItemStack result = level.getRecipeManager()
                        .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(input), level)
                        .map(recipe -> recipe.value().assemble(new SingleRecipeInput(input), level.registryAccess()))
                        .orElse(ItemStack.EMPTY);
                helper.assertTrue(result.is(TechItems.INGOTS.get(metal).get()), input + " should smelt into the " + metal.id() + " ingot, got " + result);
            }
        }
        helper.succeed();
    }

    private static StationBlockEntity placeStation(GameTestHelper helper, Station station) {
        helper.setBlock(STATION_POS, TechBlocks.STATIONS.get(station).get());
        return helper.getBlockEntity(STATION_POS);
    }

    private static int recipeIndex(GameTestHelper helper, Station station, String name) {
        List<RecipeHolder<FabricationRecipe>> recipes = TechRecipes.recipesFor(helper.getLevel(), station);
        for (int i = 0; i < recipes.size(); i++) {
            if (recipes.get(i).id().equals(ProjectRebirth.id(name))) {
                return i;
            }
        }
        throw new GameTestAssertException("No recipe " + name + " at " + station.id());
    }

    private static int recipeTime(GameTestHelper helper, Station station, String name) {
        return StationBlockEntity.productionTime(TechRecipes.recipesFor(helper.getLevel(), station)
                .get(recipeIndex(helper, station, name)).value());
    }

    /** The fabricator turns copper, silver and redstone into two circuit boards and keeps the leftovers. */
    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void fabricatorMakesCircuitBoards(GameTestHelper helper) {
        StationBlockEntity station = placeStation(helper, Station.ELECTRONICS_FABRICATOR);
        station.items().setStackInSlot(0, new ItemStack(Items.COPPER_INGOT, 3));
        station.items().setStackInSlot(1, new ItemStack(TechItems.INGOTS.get(Metal.SILVER).get(), 1));
        station.items().setStackInSlot(4, new ItemStack(Items.REDSTONE, 2));
        station.select(recipeIndex(helper, Station.ELECTRONICS_FABRICATOR, "circuit_board"));
        int time = recipeTime(helper, Station.ELECTRONICS_FABRICATOR, "circuit_board");
        helper.runAfterDelay(time / 2, () -> helper.assertTrue(
                station.items().getStackInSlot(StationBlockEntity.OUTPUT_SLOT).isEmpty(), "Nothing may be made before the time is up"));
        helper.runAfterDelay(time + 5, () -> {
            ItemStack output = station.items().getStackInSlot(StationBlockEntity.OUTPUT_SLOT);
            helper.assertTrue(output.is(TechItems.CIRCUIT_BOARD.get()) && output.getCount() == 2, "Expected 2 circuit boards, got " + output);
            helper.assertTrue(station.items().getStackInSlot(0).getCount() == 1, "One copper ingot must remain");
            helper.assertTrue(station.items().getStackInSlot(1).isEmpty() && station.items().getStackInSlot(4).isEmpty(),
                    "Silver and redstone must be used up");
            helper.succeed();
        });
    }

    /** Without all materials the station does nothing and takes nothing. */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void stationWaitsForMaterials(GameTestHelper helper) {
        StationBlockEntity station = placeStation(helper, Station.ASSEMBLY_STATION);
        station.items().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 6));
        station.items().setStackInSlot(1, new ItemStack(TechItems.ACTUATOR.get(), 2));
        station.select(recipeIndex(helper, Station.ASSEMBLY_STATION, "basic_robot_body"));
        helper.runAfterDelay(recipeTime(helper, Station.ASSEMBLY_STATION, "basic_robot_body") + 5, () -> {
            helper.assertTrue(station.items().getStackInSlot(StationBlockEntity.OUTPUT_SLOT).isEmpty(), "No body without a circuit board");
            helper.assertTrue(station.items().getStackInSlot(0).getCount() == 6 && station.items().getStackInSlot(1).getCount() == 2,
                    "Nothing may be consumed");
            helper.succeed();
        });
    }

    /** Contents, the chosen recipe and the progress survive saving and loading. */
    @GameTest(template = TEMPLATE)
    public static void stationStateIsSaved(GameTestHelper helper) {
        StationBlockEntity station = placeStation(helper, Station.ARMOR_WORKSTATION);
        station.items().setStackInSlot(2, new ItemStack(Items.IRON_INGOT, 5));
        station.select(recipeIndex(helper, Station.ARMOR_WORKSTATION, "armor_plate"));
        CompoundTag saved = station.saveWithFullMetadata(helper.getLevel().registryAccess());
        BlockEntity loaded = BlockEntity.loadStatic(station.getBlockPos(), station.getBlockState(), saved, helper.getLevel().registryAccess());
        helper.assertTrue(loaded instanceof StationBlockEntity copy && copy.items().getStackInSlot(2).getCount() == 5
                && saved.getString("selected").equals(ProjectRebirth.id("armor_plate").toString()), "Station state was not saved: " + saved);
        helper.succeed();
    }

    /** Breaking a station drops its contents. */
    @GameTest(template = TEMPLATE)
    public static void brokenStationDropsContents(GameTestHelper helper) {
        StationBlockEntity station = placeStation(helper, Station.ELECTRONICS_FABRICATOR);
        station.items().setStackInSlot(3, new ItemStack(Items.QUARTZ, 7));
        station.items().setStackInSlot(StationBlockEntity.OUTPUT_SLOT, new ItemStack(TechItems.PROCESSOR.get(), 1));
        helper.destroyBlock(STATION_POS);
        helper.assertItemEntityCountIs(Items.QUARTZ, STATION_POS, 2.0, 7);
        helper.assertItemEntityCountIs(TechItems.PROCESSOR.get(), STATION_POS, 2.0, 1);
        helper.succeed();
    }

    /** Bodies and the power armor are no longer made on the crafting table: only at the stations. */
    @GameTest(template = TEMPLATE)
    public static void bodiesAndArmorComeFromStations(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> stationMade = List.of("basic_robot_body", "improved_robot_body", "advanced_synthetic_body",
                "hightech_synthetic_body", "premium_synthetic_body", "transfer_capsule", "power_armor", "armor_plate", "circuit_board");
        for (String name : stationMade) {
            helper.assertTrue(level.getRecipeManager().byKey(ProjectRebirth.id(name))
                    .map(holder -> holder.value() instanceof FabricationRecipe).orElse(false), name + " must be a station recipe");
        }
        for (RecipeHolder<FabricationRecipe> holder : level.getRecipeManager().getAllRecipesFor(TechRecipes.FABRICATION.get())) {
            for (var ingredient : holder.value().ingredients()) {
                helper.assertTrue(ingredient.getItems().length > 0, holder.id() + " has an ingredient with no items");
            }
        }
        helper.succeed();
    }

    /** Three copper and one tin make four bronze on a normal crafting table. */
    @GameTest(template = TEMPLATE)
    public static void bronzeFromCopperAndTin(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CraftingInput input = CraftingInput.of(2, 2, List.of(new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.COPPER_INGOT),
                new ItemStack(Items.COPPER_INGOT), new ItemStack(TechItems.INGOTS.get(Metal.TIN).get())));
        ItemStack result = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(recipe -> recipe.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);
        helper.assertTrue(result.is(TechItems.BRONZE_INGOT.get()) && result.getCount() == 4, "Expected 4 bronze, got " + result);
        helper.succeed();
    }
}
