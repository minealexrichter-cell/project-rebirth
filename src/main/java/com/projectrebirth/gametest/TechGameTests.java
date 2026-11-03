package com.projectrebirth.gametest;

import java.util.List;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyComponents;
import com.projectrebirth.bodies.BodyItems;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.powerarmor.PowerArmorRegistry;
import com.projectrebirth.technology.FabricationRecipe;
import com.projectrebirth.technology.Maintenance;
import com.projectrebirth.technology.MaintenanceBlockEntity;
import com.projectrebirth.technology.Metal;
import com.projectrebirth.technology.Station;
import com.projectrebirth.technology.StationBlockEntity;
import com.projectrebirth.technology.TechBlocks;
import com.projectrebirth.technology.TechItems;
import com.projectrebirth.technology.TechRecipes;
import com.projectrebirth.technology.TechSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
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
        StationBlockEntity placed = helper.getBlockEntity(STATION_POS);
        // Charged: these tests are about recipes and timing; energy has its own tests.
        placed.energy().generate(Integer.MAX_VALUE);
        return placed;
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
        helper.runAfterDelay(time / 2, () -> helper.assertTrue(station.getBlockState().getValue(TechSounds.WORKING),
                "A producing station is marked working (its loop sound plays)"));
        helper.runAfterDelay(time / 2, () -> helper.assertTrue(
                station.items().getStackInSlot(StationBlockEntity.OUTPUT_SLOT).isEmpty(), "Nothing may be made before the time is up"));
        helper.runAfterDelay(time + 5, () -> {
            ItemStack output = station.items().getStackInSlot(StationBlockEntity.OUTPUT_SLOT);
            helper.assertTrue(output.is(TechItems.CIRCUIT_BOARD.get()) && output.getCount() == 2, "Expected 2 circuit boards, got " + output);
            helper.assertTrue(station.items().getStackInSlot(0).getCount() == 1, "One copper ingot must remain");
            helper.assertTrue(station.items().getStackInSlot(1).isEmpty() && station.items().getStackInSlot(4).isEmpty(),
                    "Silver and redstone must be used up");
            helper.assertFalse(station.getBlockState().getValue(TechSounds.WORKING), "Out of materials it stops working");
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

    private static MaintenanceBlockEntity placeMaintenance(GameTestHelper helper) {
        helper.setBlock(STATION_POS, TechBlocks.MAINTENANCE_STATION.get());
        MaintenanceBlockEntity placed = helper.getBlockEntity(STATION_POS);
        placed.energy().generate(Integer.MAX_VALUE);
        return placed;
    }

    /** Plates go to critical integrity first, then iron to structural; a fully repaired armor item is "like new". */
    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void maintenanceRepairsArmorCriticalAndStructural(GameTestHelper helper) {
        MaintenanceBlockEntity station = placeMaintenance(helper);
        float perIngot = RebirthConfig.ARMOR_REPAIR_STRUCTURAL_PER_INGOT.get().floatValue();
        float perPlate = RebirthConfig.ARMOR_REPAIR_CRITICAL_PER_PLATE.get().floatValue();
        ItemStack armor = new ItemStack(PowerArmorRegistry.POWER_ARMOR_ITEM.get());
        float maxStructural = Maintenance.maxStructural(armor);
        float maxCritical = Maintenance.maxCritical(armor);
        armor.set(PowerArmorRegistry.STORED_STRUCTURAL.get(), maxStructural - 3 * perIngot);
        armor.set(PowerArmorRegistry.STORED_CRITICAL.get(), maxCritical - 2 * perPlate);
        station.items().setStackInSlot(MaintenanceBlockEntity.SERVICE_SLOT, armor);
        station.items().setStackInSlot(MaintenanceBlockEntity.FIRST_MATERIAL_SLOT, new ItemStack(Items.IRON_INGOT, 10));
        station.items().setStackInSlot(MaintenanceBlockEntity.FIRST_MATERIAL_SLOT + 1, new ItemStack(PowerArmorRegistry.ARMOR_PLATE.get(), 5));
        int step = MaintenanceBlockEntity.stepTicks();

        helper.runAfterDelay(2 * step + 5, () -> {
            ItemStack partly = station.items().getStackInSlot(MaintenanceBlockEntity.SERVICE_SLOT);
            helper.assertTrue(Maintenance.armorCritical(partly) >= maxCritical
                    && station.items().getStackInSlot(MaintenanceBlockEntity.FIRST_MATERIAL_SLOT).getCount() == 10,
                    "Critical integrity must be repaired first, with plates, got " + Maintenance.armorCritical(partly));
        });
        helper.runAfterDelay(6 * step + 10, () -> {
            ItemStack repaired = station.items().getStackInSlot(MaintenanceBlockEntity.SERVICE_SLOT);
            helper.assertFalse(Maintenance.needsRepair(repaired), "The armor must be fully repaired");
            helper.assertFalse(repaired.has(PowerArmorRegistry.STORED_STRUCTURAL.get()) || repaired.has(PowerArmorRegistry.STORED_CRITICAL.get()),
                    "A fully repaired armor item must lose its damage data");
            helper.assertTrue(station.items().getStackInSlot(MaintenanceBlockEntity.FIRST_MATERIAL_SLOT).getCount() == 7,
                    "Three iron ingots must be used");
            helper.assertTrue(station.items().getStackInSlot(MaintenanceBlockEntity.FIRST_MATERIAL_SLOT + 1).getCount() == 3,
                    "Two plates must be used");
            helper.succeed();
        });
    }

    /** Robot bodies take iron, synthetic bodies take synthetic fiber and ignore iron. */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void maintenanceRepairsBodiesWithTheirMaterial(GameTestHelper helper) {
        MaintenanceBlockEntity robotStation = placeMaintenance(helper);
        ItemStack robot = new ItemStack(BodyItems.item(BodyClass.BASIC_ROBOT));
        robot.set(BodyComponents.STORED_INTEGRITY.get(), 5.0f);
        robotStation.items().setStackInSlot(MaintenanceBlockEntity.SERVICE_SLOT, robot);
        robotStation.items().setStackInSlot(MaintenanceBlockEntity.FIRST_MATERIAL_SLOT, new ItemStack(Items.IRON_INGOT, 10));

        BlockPos syntheticPos = new BlockPos(0, 2, 0);
        helper.setBlock(syntheticPos, TechBlocks.MAINTENANCE_STATION.get());
        MaintenanceBlockEntity syntheticStation = helper.getBlockEntity(syntheticPos);
        syntheticStation.energy().generate(Integer.MAX_VALUE);
        ItemStack synthetic = new ItemStack(BodyItems.item(BodyClass.ADVANCED_SYNTHETIC));
        synthetic.set(BodyComponents.STORED_INTEGRITY.get(), 5.0f);
        syntheticStation.items().setStackInSlot(MaintenanceBlockEntity.SERVICE_SLOT, synthetic);
        syntheticStation.items().setStackInSlot(MaintenanceBlockEntity.FIRST_MATERIAL_SLOT, new ItemStack(Items.IRON_INGOT, 10));

        float perItem = RebirthConfig.MAINTENANCE_BODY_REPAIR_PER_ITEM.get().floatValue();
        int robotSteps = Mth.ceil((Maintenance.maxBodyIntegrity(robot) - 5.0f) / perItem);
        helper.runAfterDelay((robotSteps + 1) * MaintenanceBlockEntity.stepTicks() + 5, () -> {
            ItemStack repaired = robotStation.items().getStackInSlot(MaintenanceBlockEntity.SERVICE_SLOT);
            helper.assertFalse(Maintenance.needsRepair(repaired) || repaired.has(BodyComponents.STORED_INTEGRITY.get()),
                    "The robot body must be fully repaired");
            helper.assertTrue(robotStation.items().getStackInSlot(MaintenanceBlockEntity.FIRST_MATERIAL_SLOT).getCount() == 10 - robotSteps,
                    "Expected " + robotSteps + " iron ingots to be used");
            ItemStack untouched = syntheticStation.items().getStackInSlot(MaintenanceBlockEntity.SERVICE_SLOT);
            helper.assertTrue(Maintenance.bodyIntegrity(untouched) == 5.0f
                    && syntheticStation.items().getStackInSlot(MaintenanceBlockEntity.FIRST_MATERIAL_SLOT).getCount() == 10,
                    "A synthetic body must not be repaired with iron");
            syntheticStation.items().setStackInSlot(MaintenanceBlockEntity.FIRST_MATERIAL_SLOT + 1, new ItemStack(TechItems.SYNTHETIC_FIBER.get(), 1));
            helper.runAfterDelay(MaintenanceBlockEntity.stepTicks() + 5, () -> {
                helper.assertTrue(Maintenance.bodyIntegrity(syntheticStation.items().getStackInSlot(MaintenanceBlockEntity.SERVICE_SLOT))
                        == Math.min(Maintenance.maxBodyIntegrity(untouched), 5.0f + perItem), "Synthetic fiber must repair the synthetic body");
                helper.succeed();
            });
        });
    }

    /** A hopper on the side feeds the station's inputs; a hopper below takes only the product, never the materials. */
    @GameTest(template = "floor_5x5", timeoutTicks = 400)
    public static void hoppersFeedAndEmptyStations(GameTestHelper helper) {
        BlockPos stationPos = new BlockPos(2, 3, 2);
        helper.setBlock(stationPos, TechBlocks.STATIONS.get(Station.ELECTRONICS_FABRICATOR).get());
        StationBlockEntity station = helper.getBlockEntity(stationPos);
        station.energy().generate(Integer.MAX_VALUE);
        helper.setBlock(new BlockPos(1, 3, 2), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.CHEST);
        HopperBlockEntity feeder = helper.getBlockEntity(new BlockPos(1, 3, 2));
        feeder.setItem(0, new ItemStack(Items.COPPER_INGOT, 2));
        feeder.setItem(1, new ItemStack(TechItems.INGOTS.get(Metal.SILVER).get(), 1));
        feeder.setItem(2, new ItemStack(Items.REDSTONE, 2));
        station.select(recipeIndex(helper, Station.ELECTRONICS_FABRICATOR, "circuit_board"));
        helper.succeedWhen(() -> {
            ChestBlockEntity chest = helper.getBlockEntity(new BlockPos(3, 2, 2));
            int boards = 0;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (chest.getItem(i).is(TechItems.CIRCUIT_BOARD.get())) {
                    boards += chest.getItem(i).getCount();
                }
                helper.assertFalse(chest.getItem(i).is(Items.COPPER_INGOT) || chest.getItem(i).is(Items.REDSTONE),
                        "Materials must not be pulled out of the station");
            }
            helper.assertTrue(boards == 2, "Expected 2 circuit boards in the chest, got " + boards);
        });
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
