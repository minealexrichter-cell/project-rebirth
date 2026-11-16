package com.projectrebirth.gametest;

import java.util.List;
import java.util.Optional;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.multiblock.AssemblyRecipe;
import com.projectrebirth.multiblock.MultiblockControllerBlockEntity;
import com.projectrebirth.multiblock.MultiblockRegistry;
import com.projectrebirth.multiblock.MultiblockSchema;
import com.projectrebirth.technology.FabricationRecipe;
import com.projectrebirth.technology.Station;
import com.projectrebirth.technology.TechRecipes;
import com.projectrebirth.upgrades.InstalledUpgrades;
import com.projectrebirth.upgrades.UpgradeRegistry;
import com.projectrebirth.upgrades.Upgrades;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The real multiblocks of 1.7.0, block B: the body workshop and the heavy dock are whole when built by their plans and turn their
 * manipulators to the zone; the cheap robot of the workshop; a body over a body with its upgrades; power armor in the dock; the
 * old station recipes only with multiblocks off.
 */
@GameTestHolder(PlanTests.GARRISON)
@PrefixGameTestTemplate(false)
public class GarrisonGameTests {
    private static final String TEMPLATE = "floor_9x9";

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ProjectRebirth.id(id));
    }

    /**
     * Builds a whole construction facing south with its lowest layer at y 1 and its corner at x 0, z 0 of the test area; returns
     * its controller, the plan already set.
     */
    private static MultiblockControllerBlockEntity build(GameTestHelper helper, ResourceLocation id, Block controllerBlock) {
        ServerLevel level = helper.getLevel();
        MultiblockSchema schema = MultiblockRegistry.schema(level.registryAccess(), id);
        helper.assertTrue(schema != null, "The plan " + id + " is loaded");
        Direction facing = Direction.SOUTH;
        // where the cells land with the controller at the origin, to shift the whole into the area
        int[] controllerCell = null;
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (int y = 0; y < schema.height(); y++) {
            for (int z = 0; z < schema.depth(); z++) {
                for (int x = 0; x < schema.width(); x++) {
                    MultiblockSchema.Cell cell = schema.cell(y, z, x);
                    if (cell != null && cell.controller()) {
                        controllerCell = new int[]{x, y, z};
                    }
                    BlockPos at = schema.worldPos(BlockPos.ZERO, facing, x, y, z);
                    minX = Math.min(minX, at.getX());
                    minZ = Math.min(minZ, at.getZ());
                }
            }
        }
        helper.assertTrue(controllerCell != null, "The plan has a controller");
        BlockPos lowest = schema.worldPos(BlockPos.ZERO, facing, controllerCell[0], 0, controllerCell[2]);
        BlockPos controller = helper.absolutePos(new BlockPos(-minX, 1 + controllerCell[1] - lowest.getY(), -minZ));
        for (int y = 0; y < schema.height(); y++) {
            for (int z = 0; z < schema.depth(); z++) {
                for (int x = 0; x < schema.width(); x++) {
                    MultiblockSchema.Cell cell = schema.cell(y, z, x);
                    if (cell == null || cell.zone()) {
                        continue;
                    }
                    BlockState state = cell.controller()
                            ? controllerBlock.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing)
                            : cell.display();
                    level.setBlock(schema.worldPos(controller, facing, x, y, z), state, 3);
                }
            }
        }
        MultiblockControllerBlockEntity entity = (MultiblockControllerBlockEntity) level.getBlockEntity(controller);
        entity.setSchema(id);
        return entity;
    }

    private static void wholeWithManipulators(GameTestHelper helper, MultiblockControllerBlockEntity entity, int zone, int energy, int cargo,
                                              int manipulators) {
        MultiblockSchema.Check check = entity.revalidate();
        helper.assertTrue(check.complete(), "Whole: " + check.problems());
        helper.assertTrue(check.zone().size() == zone && check.energyPorts().size() == energy && check.cargoPorts().size() == cargo,
                "Zone " + check.zone().size() + ", energy ports " + check.energyPorts().size() + ", cargo ports " + check.cargoPorts().size());
        Vec3 target = entity.schema().displayPoint(entity.getBlockPos(), entity.facing());
        int found = 0;
        for (BlockPos pos : BlockPos.betweenClosed(check.bounds().minX(), check.bounds().minY(), check.bounds().minZ(),
                check.bounds().maxX(), check.bounds().maxY(), check.bounds().maxZ())) {
            BlockState state = helper.getLevel().getBlockState(pos);
            if (state.getBlock() instanceof com.projectrebirth.multiblock.ManipulatorBlock) {
                found++;
                Vec3 here = Vec3.atCenterOf(pos), ahead = Vec3.atCenterOf(pos.relative(state.getValue(HorizontalDirectionalBlock.FACING)));
                helper.assertTrue(ahead.distanceToSqr(target.x, here.y, target.z) < here.distanceToSqr(target.x, here.y, target.z),
                        "The manipulator at " + pos + " faces the machine under assembly");
            }
        }
        helper.assertTrue(found == manipulators, "Manipulators: " + found);
    }

    @GameTest(template = TEMPLATE)
    public static void theBodyWorkshopIsWholeWhenBuiltByItsPlan(GameTestHelper helper) {
        MultiblockControllerBlockEntity entity = build(helper, MultiblockRegistry.BODY_WORKSHOP, MultiblockRegistry.WORKSHOP_CONTROLLER.get());
        wholeWithManipulators(helper, entity, 4, 1, 1, 2);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theHeavyDockIsWholeWhenBuiltByItsPlan(GameTestHelper helper) {
        MultiblockControllerBlockEntity entity = build(helper, MultiblockRegistry.HEAVY_DOCK, MultiblockRegistry.DOCK_CONTROLLER.get());
        wholeWithManipulators(helper, entity, 150, 2, 2, 4);
        helper.succeed();
    }

    /** Starts a recipe with full energy and the materials of all its stages in the slots. */
    private static void start(MultiblockControllerBlockEntity entity, String recipeId, ItemStack base) {
        entity.revalidate();
        AssemblyRecipe recipe = MultiblockRegistry.recipesFor(entity.getLevel(), entity.schemaId()).stream()
                .filter(holder -> holder.id().equals(ProjectRebirth.id(recipeId))).findFirst().orElseThrow().value();
        entity.select(ProjectRebirth.id(recipeId));
        entity.items().setStackInSlot(MultiblockControllerBlockEntity.BASE_SLOT, base);
        int slot = MultiblockControllerBlockEntity.FIRST_MATERIAL;
        for (AssemblyRecipe.Stage stage : recipe.stages()) {
            for (var material : stage.materials()) {
                entity.items().setStackInSlot(slot++, material.ingredient().getItems()[0].copyWithCount(material.count()));
            }
        }
        entity.energy().load(entity.energy().getMaxEnergyStored());
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1600)
    public static void theWorkshopMakesACheaperRobotAndTheStationKeepsItsOwn(GameTestHelper helper) {
        AssemblyRecipe workshop = MultiblockRegistry.recipesFor(helper.getLevel(), MultiblockRegistry.BODY_WORKSHOP).stream()
                .filter(holder -> holder.id().equals(ProjectRebirth.id("workshop_basic_robot_body"))).findFirst().orElseThrow().value();
        List<FabricationRecipe> station = TechRecipes.recipesFor(helper.getLevel(), Station.ASSEMBLY_STATION).stream().map(holder -> holder.value()).toList();
        FabricationRecipe old = station.stream().filter(r -> r.result().is(item("basic_robot_body"))).findFirst().orElse(null);
        helper.assertTrue(old != null, "The assembly station still makes the basic robot body");
        int workshopIron = workshop.stages().stream().flatMap(s -> s.materials().stream())
                .filter(m -> m.ingredient().test(new ItemStack(Items.IRON_INGOT))).mapToInt(m -> m.count()).sum();
        int stationIron = old.ingredients().stream().filter(m -> m.ingredient().test(new ItemStack(Items.IRON_INGOT))).mapToInt(m -> m.count()).sum();
        helper.assertTrue(workshopIron < stationIron, "Less iron in the workshop: " + workshopIron + " against " + stationIron);
        helper.assertTrue(station.stream().noneMatch(r -> r.result().is(item("advanced_synthetic_body"))),
                "Synthetic bodies are no longer made at the station");

        MultiblockControllerBlockEntity entity = build(helper, MultiblockRegistry.BODY_WORKSHOP, MultiblockRegistry.WORKSHOP_CONTROLLER.get());
        start(entity, "workshop_basic_robot_body", ItemStack.EMPTY);
        helper.runAfterDelay(workshop.totalTime() + 40, () -> {
            ItemStack out = entity.items().getStackInSlot(MultiblockControllerBlockEntity.OUTPUT_SLOT);
            helper.assertTrue(out.is(item("basic_robot_body")), "A robot body from the workshop: " + out);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 2000)
    public static void aBodyOverABodyKeepsItsUpgrades(GameTestHelper helper) {
        MultiblockControllerBlockEntity entity = build(helper, MultiblockRegistry.BODY_WORKSHOP, MultiblockRegistry.WORKSHOP_CONTROLLER.get());
        ItemStack body = new ItemStack(item("basic_robot_body"));
        Upgrades.putOnStack(body, InstalledUpgrades.EMPTY.with(new InstalledUpgrades.Installed(Optional.empty(), new ItemStack(UpgradeRegistry.COPPER_PLATING.get()))));
        start(entity, "workshop_improved_robot_body", body);
        int time = MultiblockRegistry.recipesFor(helper.getLevel(), MultiblockRegistry.BODY_WORKSHOP).stream()
                .filter(holder -> holder.id().equals(ProjectRebirth.id("workshop_improved_robot_body"))).findFirst().orElseThrow().value().totalTime();
        helper.runAfterDelay(time + 40, () -> {
            ItemStack out = entity.items().getStackInSlot(MultiblockControllerBlockEntity.OUTPUT_SLOT);
            helper.assertTrue(out.is(item("improved_robot_body")), "The improved body: " + out);
            helper.assertTrue(Upgrades.ofStack(out).entries().stream().anyMatch(e -> e.stack().is(UpgradeRegistry.COPPER_PLATING.get())),
                    "The copper plates went over to it");
            helper.succeed();
        });
    }

    /** The dock builds power armor and it rolls out through the gate on its own, not into the output slot. */
    @GameTest(template = TEMPLATE, timeoutTicks = 4600)
    public static void theHeavyDockAssemblesPowerArmorThatRollsOut(GameTestHelper helper) {
        MultiblockControllerBlockEntity entity = build(helper, MultiblockRegistry.HEAVY_DOCK, MultiblockRegistry.DOCK_CONTROLLER.get());
        start(entity, "dock_power_armor", ItemStack.EMPTY);
        // a long assembly takes more energy than the dock holds: the generators keep feeding it
        helper.onEachTick(() -> entity.energy().load(entity.energy().getMaxEnergyStored()));
        int time = MultiblockRegistry.recipesFor(helper.getLevel(), MultiblockRegistry.HEAVY_DOCK).stream()
                .filter(holder -> holder.id().equals(ProjectRebirth.id("dock_power_armor"))).findFirst().orElseThrow().value().totalTime();
        Vec3 exit = entity.schema().exitPoint(entity.getBlockPos(), entity.facing());
        Vec3 inside = entity.schema().displayPoint(entity.getBlockPos(), entity.facing());
        helper.assertTrue(exit != null, "The dock has an exit");
        helper.runAfterDelay(20, () -> helper.assertTrue(entity.state() == MultiblockControllerBlockEntity.STATE_WORKING,
                "Working: " + entity.state()));
        helper.runAfterDelay(time + 10, () -> {
            var found = helper.getLevel().getEntitiesOfClass(com.projectrebirth.powerarmor.PowerArmorEntity.class,
                    new net.minecraft.world.phys.AABB(inside, exit).inflate(2.0));
            helper.assertTrue(found.size() == 1 && found.get(0).isRollingOut(), "The armor stands in the dock and starts to roll: " + found.size());
            helper.assertTrue(entity.items().getStackInSlot(MultiblockControllerBlockEntity.OUTPUT_SLOT).isEmpty(), "Nothing in the output slot");
        });
        helper.runAfterDelay(time + 250, () -> {
            var found = helper.getLevel().getEntitiesOfClass(com.projectrebirth.powerarmor.PowerArmorEntity.class,
                    new net.minecraft.world.phys.AABB(inside, exit).inflate(2.0));
            helper.assertTrue(found.size() == 1, "One armor");
            var armor = found.get(0);
            helper.assertTrue(!armor.isRollingOut() && armor.position().distanceTo(new Vec3(exit.x, armor.getY(), exit.z)) < 0.2,
                    "It rolled out to the exit: " + armor.position() + " / " + exit);
            helper.assertTrue(armor.getVariant() == com.projectrebirth.powerarmor.ArmorVariant.COMBAT && armor.getStructural() == armor.getMaxStructural(),
                    "Combat armor, whole");
            armor.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void theOldStationRecipesComeBackOnlyWithMultiblocksOff(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean before = RebirthConfig.MULTIBLOCKS_ENABLED.get();
        try {
            RebirthConfig.MULTIBLOCKS_ENABLED.set(true);
            helper.assertTrue(TechRecipes.recipesFor(level, Station.ARMOR_WORKSTATION).stream().noneMatch(h -> h.value().result().is(item("power_armor"))),
                    "With multiblocks on, the armor workstation does not make power armor");
            RebirthConfig.MULTIBLOCKS_ENABLED.set(false);
            helper.assertTrue(TechRecipes.recipesFor(level, Station.ARMOR_WORKSTATION).stream().anyMatch(h -> h.value().result().is(item("power_armor"))),
                    "With multiblocks off, it does again");
            helper.assertTrue(TechRecipes.recipesFor(level, Station.ASSEMBLY_STATION).stream().anyMatch(h -> h.value().result().is(item("premium_synthetic_body"))),
                    "And the assembly station makes synthetic bodies again");
        } finally {
            RebirthConfig.MULTIBLOCKS_ENABLED.set(before);
        }
        helper.succeed();
    }
}
