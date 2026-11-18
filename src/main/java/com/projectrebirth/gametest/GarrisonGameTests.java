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

    /** The body workshop's hall: whole, its frames hide under the drawn walls; a broken block or controller shows them again. */
    @GameTest(template = TEMPLATE)
    public static void theWorkshopHidesItsFramesUnderTheHallWhileWhole(GameTestHelper helper) {
        MultiblockControllerBlockEntity entity = build(helper, MultiblockRegistry.BODY_WORKSHOP, MultiblockRegistry.WORKSHOP_CONTROLLER.get());
        MultiblockSchema.Check check = entity.revalidate();
        helper.assertTrue(check.complete() && entity.formed(), "Whole and formed");
        ServerLevel level = helper.getLevel();
        java.util.List<BlockPos> frames = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(check.bounds().minX(), check.bounds().minY(), check.bounds().minZ(),
                check.bounds().maxX(), check.bounds().maxY(), check.bounds().maxZ())) {
            if (level.getBlockState(pos).is(MultiblockRegistry.ASSEMBLY_FRAME.get())) {
                frames.add(pos.immutable());
            }
        }
        helper.assertTrue(frames.size() == 18 && frames.stream().allMatch(pos -> level.getBlockState(pos).getValue(com.projectrebirth.multiblock.FrameBlock.FORMED)),
                "All 18 frames hidden: " + frames.size());
        level.setBlock(frames.get(0), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        entity.revalidate();
        helper.assertTrue(!entity.formed() && frames.stream().skip(1).noneMatch(pos -> level.getBlockState(pos).getValue(com.projectrebirth.multiblock.FrameBlock.FORMED)),
                "A broken frame: the hall goes, the others show");
        level.setBlock(frames.get(0), MultiblockRegistry.ASSEMBLY_FRAME.get().defaultBlockState(), 3);
        entity.revalidate();
        helper.assertTrue(entity.formed(), "Mended, it stands again");
        level.destroyBlock(entity.getBlockPos(), false);
        helper.assertTrue(frames.stream().noneMatch(pos -> level.getBlockState(pos).getValue(com.projectrebirth.multiblock.FrameBlock.FORMED)),
                "A broken controller shows every frame");
        level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(entity.getBlockPos()).inflate(3)).forEach(e -> e.discard());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theHeavyDockIsWholeWhenBuiltByItsPlan(GameTestHelper helper) {
        MultiblockControllerBlockEntity entity = build(helper, MultiblockRegistry.HEAVY_DOCK, MultiblockRegistry.DOCK_CONTROLLER.get());
        wholeWithManipulators(helper, entity, 150, 2, 2, 4);
        // its hall: the heavy frames and the crane beams hide under the walls
        helper.assertTrue(entity.formed(), "The dock's hall stands");
        MultiblockSchema.Check check = entity.revalidate();
        long hidden = 0, shown = 0;
        for (BlockPos pos : BlockPos.betweenClosed(check.bounds().minX(), check.bounds().minY(), check.bounds().minZ(),
                check.bounds().maxX(), check.bounds().maxY(), check.bounds().maxZ())) {
            BlockState state = helper.getLevel().getBlockState(pos);
            if (state.getBlock() instanceof com.projectrebirth.multiblock.FrameBlock) {
                if (state.getValue(com.projectrebirth.multiblock.FrameBlock.FORMED)) hidden++; else shown++;
            }
        }
        helper.assertTrue(hidden > 100 && shown == 0, "Every frame and beam hidden: " + hidden + " hidden, " + shown + " shown");
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

    /** The workshop puts an AI core and a combat module into a packed body, and the reverse recipe takes them out whole (1.7.0). */
    @GameTest(template = TEMPLATE, timeoutTicks = 1600)
    public static void theWorkshopPutsACoreIntoABodyAndTakesItOut(GameTestHelper helper) {
        MultiblockControllerBlockEntity entity = build(helper, MultiblockRegistry.BODY_WORKSHOP, MultiblockRegistry.WORKSHOP_CONTROLLER.get());
        ItemStack body = new ItemStack(item("advanced_synthetic_body"));
        Upgrades.putOnStack(body, InstalledUpgrades.EMPTY.with(new InstalledUpgrades.Installed(Optional.empty(), new ItemStack(UpgradeRegistry.COPPER_PLATING.get()))));
        start(entity, "workshop_android_advanced_synthetic_body", body);
        java.util.function.ToIntFunction<String> time = id -> MultiblockRegistry.recipesFor(helper.getLevel(), MultiblockRegistry.BODY_WORKSHOP).stream()
                .filter(holder -> holder.id().equals(ProjectRebirth.id(id))).findFirst().orElseThrow().value().totalTime();
        int attach = time.applyAsInt("workshop_android_advanced_synthetic_body");
        int strip = time.applyAsInt("workshop_android_core_out_advanced_synthetic_body");
        helper.runAfterDelay(attach + 40, () -> {
            ItemStack out = entity.items().getStackInSlot(MultiblockControllerBlockEntity.OUTPUT_SLOT);
            var parts = out.get(com.projectrebirth.garrison.GarrisonRegistry.ANDROID_PARTS.get());
            helper.assertTrue(out.is(item("advanced_synthetic_body")) && parts != null, "An android out of the workshop: " + out);
            helper.assertTrue(parts.core().is(com.projectrebirth.garrison.GarrisonRegistry.AI_CORE.get())
                    && parts.module().is(com.projectrebirth.garrison.GarrisonRegistry.COMBAT_MODULE.get()), "With the core and the module");
            helper.assertTrue(Upgrades.ofStack(out).entries().stream().anyMatch(e -> e.stack().is(UpgradeRegistry.COPPER_PLATING.get())), "Its plates kept");
            entity.items().setStackInSlot(MultiblockControllerBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);
            start(entity, "workshop_android_core_out_advanced_synthetic_body", out);
        });
        helper.runAfterDelay(attach + 40 + strip + 40, () -> {
            ItemStack out = entity.items().getStackInSlot(MultiblockControllerBlockEntity.OUTPUT_SLOT);
            helper.assertTrue(out.is(item("advanced_synthetic_body")) && !out.has(com.projectrebirth.garrison.GarrisonRegistry.ANDROID_PARTS.get()),
                    "A plain body again: " + out);
            boolean core = false, module = false;
            for (int i = 0; i < entity.items().getSlots(); i++) {
                ItemStack stack = entity.items().getStackInSlot(i);
                core |= stack.is(com.projectrebirth.garrison.GarrisonRegistry.AI_CORE.get());
                module |= stack.is(com.projectrebirth.garrison.GarrisonRegistry.COMBAT_MODULE.get());
            }
            helper.assertTrue(core && module, "The core and the module came back");
            helper.succeed();
        });
    }

    /** 1.7.1: what the dock builds of the garrison rolls out too: the mech as it is, a turret in its crate. */
    private static void dockRollsOut(GameTestHelper helper, String recipe, Class<? extends net.minecraft.world.entity.Entity> kind,
                                     java.util.function.BiConsumer<GameTestHelper, net.minecraft.world.entity.Entity> outside) {
        MultiblockControllerBlockEntity entity = build(helper, MultiblockRegistry.HEAVY_DOCK, MultiblockRegistry.DOCK_CONTROLLER.get());
        start(entity, recipe, ItemStack.EMPTY);
        helper.onEachTick(() -> entity.energy().load(entity.energy().getMaxEnergyStored()));
        int time = MultiblockRegistry.recipesFor(helper.getLevel(), MultiblockRegistry.HEAVY_DOCK).stream()
                .filter(holder -> holder.id().equals(ProjectRebirth.id(recipe))).findFirst().orElseThrow().value().totalTime();
        Vec3 exit = entity.schema().exitPoint(entity.getBlockPos(), entity.facing());
        Vec3 inside = entity.schema().displayPoint(entity.getBlockPos(), entity.facing());
        net.minecraft.world.phys.AABB around = new net.minecraft.world.phys.AABB(inside, exit).inflate(3.0);
        // 1.7.2: a machine without ammunition walks off to a post once it is out, so its arrival at the exit is noted as it happens
        boolean[] arrived = new boolean[1];
        helper.onEachTick(() -> helper.getLevel().getEntitiesOfClass(kind, around).forEach(thing -> {
            if (thing.position().distanceTo(new Vec3(exit.x, thing.getY(), exit.z)) < 0.8) {
                arrived[0] = true;
            }
        }));
        net.minecraft.world.entity.Entity[] built = new net.minecraft.world.entity.Entity[1];
        helper.runAfterDelay(time + 10, () -> {
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(kind, around).size() == 1, recipe + ": it stands in the dock");
            built[0] = helper.getLevel().getEntitiesOfClass(kind, around).get(0);
            helper.assertTrue(entity.items().getStackInSlot(MultiblockControllerBlockEntity.OUTPUT_SLOT).isEmpty(), "Nothing in the output slot");
        });
        helper.runAfterDelay(time + 200, () -> {
            // 1.7.2: the one that was built (a machine may walk off to a post once it is out)
            var thing = built[0];
            helper.assertTrue(thing != null && thing.isAlive(), "It is still there");
            helper.assertTrue(arrived[0], recipe + ": it rolled out to the exit: " + thing.position() + " / " + exit);
            outside.accept(helper, thing);
            thing.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 4200)
    public static void theHeavyDockBuildsAMechThatRollsOut(GameTestHelper helper) {
        dockRollsOut(helper, "dock_guard_mech_gun_rocket", com.projectrebirth.garrison.GuardMechEntity.class, (h, thing) -> {
            var mech = (com.projectrebirth.garrison.GuardMechEntity) thing;
            h.assertTrue(!mech.isRollingOut() && mech.working() && mech.energy() > mech.capacity() * 0.9f,
                    "Out, charged, working: " + mech.isRollingOut() + " " + mech.working() + " " + mech.energy());
            h.assertTrue(mech.node(com.projectrebirth.garrison.GuardMechEntity.LEFT) == com.projectrebirth.garrison.GuardMechEntity.Node.ROCKET,
                    "The nodes of its recipe");
        });
    }

    /** 1.7.1, block E: the small robot comes out of the dock on its own legs, charged, with its gun. */
    @GameTest(template = TEMPLATE, timeoutTicks = 2000)
    public static void theHeavyDockBuildsASmallRobot(GameTestHelper helper) {
        dockRollsOut(helper, "dock_small_robot", com.projectrebirth.garrison.SmallRobotEntity.class, (h, thing) -> {
            var robot = (com.projectrebirth.garrison.SmallRobotEntity) thing;
            h.assertTrue(!robot.isRollingOut() && robot.working() && robot.energy() > robot.capacity() * 0.9f, "Out, charged, working");
            h.assertTrue(robot.node(com.projectrebirth.garrison.GuardMechEntity.RIGHT) == com.projectrebirth.garrison.GuardMechEntity.Node.GUN, "With its gun");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 3000)
    public static void theHeavyDockRollsATurretOutInACrate(GameTestHelper helper) {
        dockRollsOut(helper, "dock_machine_gun_turret", com.projectrebirth.garrison.TurretCrateEntity.class, (h, thing) -> {
            var crate = (com.projectrebirth.garrison.TurretCrateEntity) thing;
            h.assertTrue(!crate.isRollingOut() && crate.getItem().is(com.projectrebirth.garrison.GarrisonRegistry.MACHINE_GUN_TURRET_ITEM.get()),
                    "Out, with the turret inside: " + crate.getItem());
            net.minecraft.server.level.ServerPlayer player = h.makeMockServerPlayerInLevel();
            crate.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND);
            h.assertTrue(crate.isRemoved() && player.getInventory().countItem(com.projectrebirth.garrison.GarrisonRegistry.MACHINE_GUN_TURRET_ITEM.get()) == 1,
                    "A right-click takes the turret out of the crate");
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

    /**
     * 1.7.3: a station of a world from before 1.7.0 with a synthetic body chosen (a classic recipe, now made in the body workshop)
     * says that the recipe moved instead of "select a recipe"; a recipe of today does not.
     */
    @GameTest(template = TEMPLATE, batch = "garrison_moved_recipe")
    public static void anOldStationTellsThatItsRecipeMovedToAMultiblock(GameTestHelper helper) {
        boolean before = RebirthConfig.MULTIBLOCKS_ENABLED.get();
        try {
            RebirthConfig.MULTIBLOCKS_ENABLED.set(true);
            BlockPos at = new BlockPos(4, 1, 4);
            helper.setBlock(at, net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ProjectRebirth.id("assembly_station")));
            com.projectrebirth.technology.StationBlockEntity station = helper.getBlockEntity(at);
            net.minecraft.nbt.CompoundTag old = new net.minecraft.nbt.CompoundTag();
            old.putString("selected", "projectrebirth:advanced_synthetic_body");
            station.loadCustomOnly(old, helper.getLevel().registryAccess());
            helper.assertTrue(station.movedRecipe(), "The synthetic body moved to the workshop");
            net.minecraft.nbt.CompoundTag today = new net.minecraft.nbt.CompoundTag();
            today.putString("selected", "projectrebirth:ai_core");
            station.loadCustomOnly(today, helper.getLevel().registryAccess());
            helper.assertFalse(station.movedRecipe(), "The AI core is still made here");
        } finally {
            RebirthConfig.MULTIBLOCKS_ENABLED.set(before);
        }
        helper.succeed();
    }
}
