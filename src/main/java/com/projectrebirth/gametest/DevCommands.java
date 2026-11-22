package com.projectrebirth.gametest;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.factions.Faction;
import com.projectrebirth.factions.FactionBlocks;
import com.projectrebirth.factions.FactionData;
import com.projectrebirth.factions.FactionService;
import com.projectrebirth.garrison.ConsoleBlockEntity;
import com.projectrebirth.garrison.MachineEntity;
import com.projectrebirth.garrison.PackedMachineItem;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Operator commands for checks in the development environment only (1.7.3, block E): what players do in the faction window and
 * by hand, run through the same server code. The package {@code gametest} is left out of the released jar (build.gradle), so
 * players never get this command.
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class DevCommands {
    private DevCommands() {
    }

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("rbdev").requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS));
        root.then(Commands.literal("faction")
                .then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> reply(ctx, FactionService.create(player(ctx), StringArgumentType.getString(ctx, "name"), ChatFormatting.GOLD)))))
                .then(Commands.literal("core").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(DevCommands::core)))
                .then(withOther("ally", FactionService::proposeAlliance))
                .then(withOther("allyaccept", FactionService::acceptAlliance))
                .then(withOther("unally", FactionService::breakAlliance))
                .then(withOther("war", FactionService::declareEnemy))
                .then(withOther("peace", FactionService::offerPeace))
                .then(Commands.literal("warnow").then(Commands.argument("other", StringArgumentType.word())
                        .executes(DevCommands::warNow))));
        root.then(Commands.literal("machine")
                .then(Commands.argument("item", ResourceLocationArgument.id())
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("yaw", FloatArgumentType.floatArg(-180.0f, 180.0f))
                                        .executes(DevCommands::machine)))));
        // 1.8.0, block H: the paths of the machines near the player as particles
        root.then(Commands.literal("path")
                .executes(ctx -> path(ctx, 60))
                .then(Commands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 3600))
                        .executes(ctx -> path(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "seconds")))));
        root.then(Commands.literal("intruders")
                .then(Commands.argument("console", BlockPosArgument.blockPos())
                        .then(Commands.argument("on", BoolArgumentType.bool())
                                .executes(DevCommands::intruders))));
        root.then(Commands.literal("order")
                .then(Commands.argument("console", BlockPosArgument.blockPos())
                        .then(Commands.argument("type", StringArgumentType.word())
                                .executes(ctx -> order(ctx, null))
                                .then(Commands.argument("point", BlockPosArgument.blockPos())
                                        .executes(ctx -> order(ctx, BlockPosArgument.getLoadedBlockPos(ctx, "point")))))));
        root.then(Commands.literal("android")
                .then(Commands.argument("body", StringArgumentType.word())
                        .then(Commands.argument("module", StringArgumentType.word())
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(DevCommands::android)))));
        // 1.8.0, block J: a work order without an area (the android finds its work and storage itself)
        root.then(Commands.literal("ownwork")
                .then(Commands.argument("machine", net.minecraft.commands.arguments.EntityArgument.entity())
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .executes(ctx -> {
                                    net.minecraft.world.entity.Entity machine = net.minecraft.commands.arguments.EntityArgument.getEntity(ctx, "machine");
                                    if (!(machine instanceof com.projectrebirth.garrison.GarrisonMachine garrison)) {
                                        ctx.getSource().sendFailure(Component.literal("not a machine"));
                                        return 0;
                                    }
                                    garrison.machineCore().setTask(com.projectrebirth.garrison.MachineTask.workOwn(StringArgumentType.getString(ctx, "kind")));
                                    ctx.getSource().sendSuccess(() -> Component.literal("own work " + StringArgumentType.getString(ctx, "kind") + " to "
                                            + machine.getName().getString()), false);
                                    return 1;
                                }))));
        root.then(Commands.literal("work")
                .then(Commands.argument("console", BlockPosArgument.blockPos())
                        .then(Commands.argument("machine", net.minecraft.commands.arguments.EntityArgument.entity())
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                        .then(Commands.argument("chest", BlockPosArgument.blockPos())
                                                                .executes(DevCommands::work))))))));
        root.then(Commands.literal("workshop")
                .then(Commands.argument("corner", BlockPosArgument.blockPos())
                        .then(Commands.argument("recipe", StringArgumentType.word())
                                .executes(ctx -> workshop(ctx, false)))));
        // 1.8.0, block J: a whole storage at that corner (its controller facing south), owned by the player
        root.then(Commands.literal("storage")
                .then(Commands.argument("corner", BlockPosArgument.blockPos())
                        .executes(DevCommands::storage)));
        root.then(Commands.literal("dock")
                .then(Commands.argument("corner", BlockPosArgument.blockPos())
                        .then(Commands.argument("recipe", StringArgumentType.word())
                                .executes(ctx -> workshop(ctx, true)))));
        root.then(Commands.literal("ui").then(Commands.literal("bench")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(DevCommands::uiBench))));
        root.then(Commands.literal("ui").then(Commands.literal("gear").executes(DevCommands::uiGear)));
        root.then(Commands.literal("ui").then(Commands.literal("garrison")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(DevCommands::uiGarrison))));
        root.then(Commands.literal("use")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(DevCommands::use)));
        event.getDispatcher().register(root);
    }

    /**
     * 1.8.0: puts every block of the mod that has a window in a row along +x (two blocks apart) for /rbdev ui check, which
     * then opens them one by one; the list and the places are in DevUiBench, shared with the client.
     */
    private static int uiBench(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos origin = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        ServerLevel level = ctx.getSource().getLevel();
        int placed = 0;
        for (int i = 0; i < DevUiBench.BLOCKS.size(); i++) {
            BlockPos pos = DevUiBench.place(origin, i);
            var block = BuiltInRegistries.BLOCK.getOptional(ProjectRebirth.id(DevUiBench.BLOCKS.get(i)));
            if (block.isEmpty()) {
                continue;
            }
            for (int up = 0; up < 3; up++) {
                level.setBlock(pos.above(up), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
            level.setBlock(pos.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
            // a floor where the check puts the player (in front of the block), so a bench put up in the air works too
            for (int back = 1; back <= 3; back++) {
                level.setBlock(pos.below().north(back), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
            }
            level.setBlock(pos, block.get().defaultBlockState(), 3);
            placed++;
        }
        int count = placed;
        ctx.getSource().sendSuccess(() -> Component.literal("ui bench: " + count + " blocks"), false);
        return count;
    }

    /**
     * 1.8.0: right-clicks a block with an empty hand as the player who runs the command (`execute as <player> run rbdev use x y z`),
     * so a window opens without aiming at the block: for screenshots of windows and the layout check.
     */
    private static int use(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        ServerLevel level = player.serverLevel();
        net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
        net.minecraft.world.InteractionResult result = level.getBlockState(pos).useWithoutItem(level, player, hit);
        ctx.getSource().sendSuccess(() -> Component.literal("use " + pos.toShortString() + ": " + result), false);
        return result.consumesAction() ? 1 : 0;
    }

    /** Sets an android up from a packed body with an AI core and a module (combat or work), as the item in the hand does. */
    private static int android(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = player(ctx);
        com.projectrebirth.bodies.BodyClass bodyClass = com.projectrebirth.bodies.BodyClass.byId(StringArgumentType.getString(ctx, "body"));
        Item bodyItem = BuiltInRegistries.ITEM.get(ProjectRebirth.id(bodyClass.id()));
        Item module = "work".equals(StringArgumentType.getString(ctx, "module"))
                ? com.projectrebirth.garrison.GarrisonRegistry.WORK_MODULE.get() : com.projectrebirth.garrison.GarrisonRegistry.COMBAT_MODULE.get();
        ItemStack stack = new ItemStack(bodyItem);
        stack.set(com.projectrebirth.garrison.GarrisonRegistry.ANDROID_PARTS.get(), new com.projectrebirth.garrison.GarrisonRegistry.AndroidParts(
                new ItemStack(com.projectrebirth.garrison.GarrisonRegistry.AI_CORE.get()), new ItemStack(module)));
        var android = com.projectrebirth.garrison.Androids.place(player.serverLevel(), player, stack, bodyClass,
                BlockPosArgument.getLoadedBlockPos(ctx, "pos"), 0.0f);
        ctx.getSource().sendSuccess(() -> Component.literal(android == null ? "not placed" : "placed " + android.getUUID()), false);
        return android == null ? 0 : 1;
    }

    /** Gives one machine of the console the order "work" in an area with a chest, as the tablet does. */
    private static int work(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!(ctx.getSource().getLevel().getBlockEntity(BlockPosArgument.getLoadedBlockPos(ctx, "console")) instanceof ConsoleBlockEntity console)) {
            ctx.getSource().sendFailure(Component.literal("not a console"));
            return 0;
        }
        net.minecraft.world.entity.Entity machine = net.minecraft.commands.arguments.EntityArgument.getEntity(ctx, "machine");
        com.projectrebirth.garrison.MachineTask task = com.projectrebirth.garrison.MachineTask.work(StringArgumentType.getString(ctx, "kind"),
                BlockPosArgument.getLoadedBlockPos(ctx, "from"), BlockPosArgument.getLoadedBlockPos(ctx, "to"),
                BlockPosArgument.getLoadedBlockPos(ctx, "chest"));
        console.order(machine.getUUID(), task);
        ctx.getSource().sendSuccess(() -> Component.literal("work " + task.work() + " to " + machine.getName().getString()
                + (console.knows(machine.getUUID()) ? "" : " (not linked to this console)")), false);
        return 1;
    }

    /**
     * Builds a whole body workshop facing south with its lowest layer at the corner, and starts a recipe with the materials of
     * all its stages and full energy (as the game tests do).
     */
    private static int workshop(CommandContext<CommandSourceStack> ctx, boolean dock) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos corner = BlockPosArgument.getLoadedBlockPos(ctx, "corner");
        net.minecraft.resources.ResourceLocation id = dock ? com.projectrebirth.multiblock.MultiblockRegistry.HEAVY_DOCK
                : com.projectrebirth.multiblock.MultiblockRegistry.BODY_WORKSHOP;
        net.minecraft.world.level.block.Block controllerBlock = dock ? com.projectrebirth.multiblock.MultiblockRegistry.DOCK_CONTROLLER.get()
                : com.projectrebirth.multiblock.MultiblockRegistry.WORKSHOP_CONTROLLER.get();
        com.projectrebirth.multiblock.MultiblockSchema schema = com.projectrebirth.multiblock.MultiblockRegistry.schema(level.registryAccess(), id);
        net.minecraft.core.Direction facing = net.minecraft.core.Direction.SOUTH;
        int[] controllerCell = null;
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (int y = 0; y < schema.height(); y++) {
            for (int z = 0; z < schema.depth(); z++) {
                for (int x = 0; x < schema.width(); x++) {
                    com.projectrebirth.multiblock.MultiblockSchema.Cell cell = schema.cell(y, z, x);
                    if (cell != null && cell.controller()) {
                        controllerCell = new int[]{x, y, z};
                    }
                    BlockPos at = schema.worldPos(BlockPos.ZERO, facing, x, y, z);
                    minX = Math.min(minX, at.getX());
                    minZ = Math.min(minZ, at.getZ());
                }
            }
        }
        BlockPos lowest = schema.worldPos(BlockPos.ZERO, facing, controllerCell[0], 0, controllerCell[2]);
        BlockPos controller = corner.offset(-minX, controllerCell[1] - lowest.getY(), -minZ);
        for (int y = 0; y < schema.height(); y++) {
            for (int z = 0; z < schema.depth(); z++) {
                for (int x = 0; x < schema.width(); x++) {
                    com.projectrebirth.multiblock.MultiblockSchema.Cell cell = schema.cell(y, z, x);
                    if (cell == null || cell.zone()) {
                        continue;
                    }
                    net.minecraft.world.level.block.state.BlockState state = cell.controller()
                            ? controllerBlock.defaultBlockState()
                                    .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, facing)
                            : cell.display();
                    level.setBlock(schema.worldPos(controller, facing, x, y, z), state, 3);
                }
            }
        }
        com.projectrebirth.multiblock.MultiblockControllerBlockEntity entity =
                (com.projectrebirth.multiblock.MultiblockControllerBlockEntity) level.getBlockEntity(controller);
        entity.setSchema(id);
        var check = entity.revalidate();
        net.minecraft.resources.ResourceLocation recipeId = ProjectRebirth.id(StringArgumentType.getString(ctx, "recipe"));
        com.projectrebirth.multiblock.AssemblyRecipe recipe = com.projectrebirth.multiblock.MultiblockRegistry.recipesFor(level, id).stream()
                .filter(holder -> holder.id().equals(recipeId)).findFirst().orElseThrow().value();
        entity.select(recipeId);
        int slot = com.projectrebirth.multiblock.MultiblockControllerBlockEntity.FIRST_MATERIAL;
        for (com.projectrebirth.multiblock.AssemblyRecipe.Stage stage : recipe.stages()) {
            for (var material : stage.materials()) {
                entity.items().setStackInSlot(slot++, material.ingredient().getItems()[0].copyWithCount(material.count()));
            }
        }
        entity.energy().load(entity.energy().getMaxEnergyStored());
        ctx.getSource().sendSuccess(() -> Component.literal((dock ? "dock at " : "workshop at ") + controller.toShortString() + ", whole " + check.complete()), false);
        return 1;
    }

    /** Gives every machine of the console one order, as the console's buttons and the tablet do. */
    private static int order(CommandContext<CommandSourceStack> ctx, BlockPos point) throws CommandSyntaxException {
        if (!(ctx.getSource().getLevel().getBlockEntity(BlockPosArgument.getLoadedBlockPos(ctx, "console")) instanceof ConsoleBlockEntity console)) {
            ctx.getSource().sendFailure(Component.literal("not a console"));
            return 0;
        }
        com.projectrebirth.garrison.MachineTask.Type type = com.projectrebirth.garrison.MachineTask.Type.valueOf(
                StringArgumentType.getString(ctx, "type").toUpperCase(java.util.Locale.ROOT));
        com.projectrebirth.garrison.MachineTask task = point == null ? com.projectrebirth.garrison.MachineTask.simple(type)
                : com.projectrebirth.garrison.MachineTask.at(type, point);
        int count = console.orderGroup(ConsoleBlockEntity.ALL, task);
        ctx.getSource().sendSuccess(() -> Component.literal(type + " to " + count + " machines"), false);
        return count;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> withOther(String name,
                                                                         BiFunction<ServerPlayer, UUID, FactionService.Result> action) {
        return Commands.literal(name).then(Commands.argument("other", StringArgumentType.word())
                .executes(ctx -> {
                    Optional<Faction> other = FactionData.get(ctx.getSource().getServer()).byName(StringArgumentType.getString(ctx, "other"));
                    if (other.isEmpty()) {
                        ctx.getSource().sendFailure(Component.literal("no such faction"));
                        return 0;
                    }
                    return reply(ctx, action.apply(player(ctx), other.get().id()));
                }));
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    private static int reply(CommandContext<CommandSourceStack> ctx, FactionService.Result result) {
        ctx.getSource().sendSuccess(() -> Component.literal(String.valueOf(result)), false);
        return 1;
    }

    /** Sets a faction core where the player stands it, by the same rules as placing the block. */
    private static int core(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = player(ctx);
        ServerLevel level = player.serverLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        FactionService.Result may = FactionService.canPlaceCore(player, level, pos);
        if (may.success()) {
            level.setBlock(pos, FactionBlocks.FACTION_CORE.get().defaultBlockState(), 3);
            FactionService.addCore(player, level, pos);
        }
        return reply(ctx, may);
    }

    /** Ends the preparation of a declared war at once: moves its start back by the preparation time. */
    private static int warNow(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        FactionData data = FactionData.get(ctx.getSource().getServer());
        Optional<Faction> own = data.of(player(ctx).getUUID());
        Optional<Faction> other = data.byName(StringArgumentType.getString(ctx, "other"));
        if (own.isEmpty() || other.isEmpty() || !own.get().warStarts().containsKey(other.get().id())) {
            ctx.getSource().sendFailure(Component.literal("no declared war"));
            return 0;
        }
        long start = System.currentTimeMillis() - FactionService.preparationMillis() - 1000L;
        own.get().warStarts().put(other.get().id(), start);
        other.get().warStarts().put(own.get().id(), start);
        data.setDirty();
        ctx.getSource().sendSuccess(() -> Component.literal("war active"), false);
        return 1;
    }

    /** Sets a machine up from its item as the player would, at that place. */
    private static int machine(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = player(ctx);
        Item item = BuiltInRegistries.ITEM.get(ResourceLocationArgument.getId(ctx, "item"));
        if (!(item instanceof PackedMachineItem packed)) {
            ctx.getSource().sendFailure(Component.literal("not a machine item"));
            return 0;
        }
        MachineEntity machine = packed.place(player.serverLevel(), player, new ItemStack(item), BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                FloatArgumentType.getFloat(ctx, "yaw"));
        ctx.getSource().sendSuccess(() -> Component.literal(machine == null ? "not placed" : "placed " + machine.getUUID()), false);
        return machine == null ? 0 : 1;
    }

    /**
     * 1.8.0, block E: a garrison to look at in the console's window: a console with full energy owned by the player at the corner,
     * a service post with its plates and chest, a turret mount with a turret and rounds, a mech, a small robot, a combat and a work
     * android, in two groups, with a guard point, a patrol route and a work area, and the alarm of a shut-down machine.
     */
    /** 1.8.0, block E: opens the gear window of the nearest android (as a click with an empty hand does). */
    private static int uiGear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = player(ctx);
        var found = player.serverLevel().getEntitiesOfClass(com.projectrebirth.garrison.AndroidEntity.class, player.getBoundingBox().inflate(12),
                android -> android.isAlive());
        if (found.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("no android near"));
            return 0;
        }
        found.sort(java.util.Comparator.comparingDouble(player::distanceToSqr));
        var android = found.get(0);
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, who) -> new com.projectrebirth.garrison.AndroidGearMenu(id, inventory, android),
                android.getDisplayName()), buf -> buf.writeVarInt(android.getId()));
        return 1;
    }

    private static int uiGarrison(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = player(ctx);
        ServerLevel level = player.serverLevel();
        BlockPos at = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        for (int dx = -2; dx <= 24; dx++) {
            for (int dz = -2; dz <= 24; dz++) {
                level.setBlock(at.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                for (int up = 0; up < 4; up++) {
                    level.setBlock(at.offset(dx, up, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        level.setBlock(at, com.projectrebirth.garrison.GarrisonRegistry.CONSOLE.get().defaultBlockState(), 3);
        ConsoleBlockEntity console = (ConsoleBlockEntity) level.getBlockEntity(at);
        console.setOwner(player.getUUID());
        console.energy().load(console.energy().getMaxEnergyStored());
        console.serverTick();
        // the service post: its console, four plates and a chest with iron and plates
        BlockPos post = at.offset(6, 0, 0);
        level.setBlock(post, com.projectrebirth.garrison.GarrisonRegistry.SERVICE_POST.get().defaultBlockState(), 3);
        for (int dx = 1; dx <= 2; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                level.setBlock(post.offset(dx, 0, dz), com.projectrebirth.garrison.GarrisonRegistry.SERVICE_POST_PLATE.get().defaultBlockState(), 3);
            }
        }
        level.setBlock(post.north(), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
        if (level.getBlockEntity(post.north()) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 23));
            chest.setItem(1, new ItemStack(com.projectrebirth.powerarmor.PowerArmorRegistry.ARMOR_PLATE.get(), 4));
            chest.setItem(2, new ItemStack(com.projectrebirth.weapons.WeaponRegistry.ASSAULT_RIFLE_AMMO.get(), 96));
        }
        if (level.getBlockEntity(post) instanceof com.projectrebirth.garrison.ServicePostBlockEntity postEntity) {
            postEntity.energy().load(postEntity.energy().getMaxEnergyStored() / 2);
        }
        // the turret on its mount
        BlockPos mount = at.offset(3, 0, 12);
        level.setBlock(mount, com.projectrebirth.garrison.GarrisonRegistry.TURRET_MOUNT.get().defaultBlockState(), 3);
        if (level.getBlockEntity(mount) instanceof com.projectrebirth.garrison.TurretMountBlockEntity mountEntity) {
            mountEntity.energy().load(mountEntity.energy().getMaxEnergyStored());
            mountEntity.ammo().setStackInSlot(0, new ItemStack(com.projectrebirth.weapons.WeaponRegistry.ASSAULT_RIFLE_AMMO.get(), 64));
            mountEntity.ammo().setStackInSlot(1, new ItemStack(com.projectrebirth.weapons.WeaponRegistry.ASSAULT_RIFLE_AMMO.get(), 40));
        }
        java.util.List<net.minecraft.world.entity.Entity> placed = new java.util.ArrayList<>();
        var turretItem = com.projectrebirth.garrison.GarrisonRegistry.MACHINE_GUN_TURRET_ITEM.get();
        placed.add(turretItem.place(level, player, new ItemStack(turretItem), mount.above(), 0.0f));
        var mechItem = com.projectrebirth.garrison.GarrisonRegistry.GUARD_MECH_ITEM.get();
        placed.add(mechItem.place(level, player, new ItemStack(mechItem), at.offset(12, 0, 8), 0.0f));
        var robotItem = com.projectrebirth.garrison.GarrisonRegistry.SMALL_ROBOT_ITEM.get();
        placed.add(robotItem.place(level, player, new ItemStack(robotItem), at.offset(16, 0, 10), 0.0f));
        for (String module : new String[] {"combat", "work"}) {
            ItemStack body = new ItemStack(BuiltInRegistries.ITEM.get(ProjectRebirth.id(com.projectrebirth.bodies.BodyClass.values()[module.equals("work") ? 0 : 2].id())));
            body.set(com.projectrebirth.garrison.GarrisonRegistry.ANDROID_PARTS.get(), new com.projectrebirth.garrison.GarrisonRegistry.AndroidParts(
                    new ItemStack(com.projectrebirth.garrison.GarrisonRegistry.AI_CORE.get()), new ItemStack(module.equals("work")
                    ? com.projectrebirth.garrison.GarrisonRegistry.WORK_MODULE.get() : com.projectrebirth.garrison.GarrisonRegistry.COMBAT_MODULE.get())));
            placed.add(com.projectrebirth.garrison.Androids.place(level, player, body,
                    com.projectrebirth.bodies.BodyClass.values()[module.equals("work") ? 0 : 2], at.offset(module.equals("work") ? 18 : 8, 0, module.equals("work") ? 18 : 4), 0.0f));
        }
        int linked = 0;
        int index = 0;
        for (net.minecraft.world.entity.Entity machine : placed) {
            if (machine == null || !console.knows(machine.getUUID())) {
                index++;
                continue;
            }
            linked++;
            java.util.UUID id = machine.getUUID();
            switch (index) {
                case 0 -> console.setGroup(id, 0);
                case 1 -> {
                    console.setGroup(id, 0);
                    console.order(id, com.projectrebirth.garrison.MachineTask.at(com.projectrebirth.garrison.MachineTask.Type.GUARD, at.offset(14, 0, 4)));
                }
                case 2 -> {
                    console.setGroup(id, 0);
                    console.order(id, com.projectrebirth.garrison.MachineTask.patrol(java.util.List.of(at.offset(14, 0, 14), at.offset(22, 0, 14),
                            at.offset(22, 0, 22), at.offset(14, 0, 22))));
                }
                case 3 -> console.setGroup(id, 2);
                default -> {
                    console.setGroup(id, 1);
                    level.setBlock(at.offset(20, 0, 22), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
                    console.order(id, com.projectrebirth.garrison.MachineTask.work("lumber", at.offset(16, 0, 16), at.offset(23, 10, 23), at.offset(20, 0, 22)));
                }
            }
            index++;
        }
        console.serverTick();
        int count = linked;
        ctx.getSource().sendSuccess(() -> Component.literal("ui garrison: console at " + at.toShortString() + ", " + count + " machines linked"), false);
        return count;
    }

    private static int intruders(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!(ctx.getSource().getLevel().getBlockEntity(BlockPosArgument.getLoadedBlockPos(ctx, "console")) instanceof ConsoleBlockEntity console)) {
            ctx.getSource().sendFailure(Component.literal("not a console"));
            return 0;
        }
        console.setIntruderMode(BoolArgumentType.getBool(ctx, "on"));
        ctx.getSource().sendSuccess(() -> Component.literal("intruder mode " + console.intruderMode()), false);
        return 1;
    }

    private static int path(CommandContext<CommandSourceStack> ctx, int seconds) throws CommandSyntaxException {
        DevPathView.watch(ctx.getSource().getPlayerOrException(), seconds);
        ctx.getSource().sendSuccess(() -> Component.literal(seconds > 0 ? "paths of machines shown for " + seconds + " s" : "paths hidden"), false);
        return 1;
    }

    private static int storage(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos corner = BlockPosArgument.getLoadedBlockPos(ctx, "corner");
        com.projectrebirth.multiblock.MultiblockSchema schema = com.projectrebirth.multiblock.MultiblockRegistry.schema(level.registryAccess(),
                com.projectrebirth.logistics.LogisticsRegistry.SCHEMA);
        net.minecraft.core.Direction facing = net.minecraft.core.Direction.SOUTH;
        int[] c = schema.controllerCell();
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (int y = 0; y < schema.height(); y++) {
            for (int r = 0; r < schema.depth(); r++) {
                for (int x = 0; x < schema.width(); x++) {
                    BlockPos at = schema.worldPos(BlockPos.ZERO, facing, x, y, r);
                    minX = Math.min(minX, at.getX());
                    minZ = Math.min(minZ, at.getZ());
                }
            }
        }
        BlockPos controller = corner.offset(-minX, c[1], -minZ);
        for (int y = 0; y < schema.height(); y++) {
            for (int r = 0; r < schema.depth(); r++) {
                for (int x = 0; x < schema.width(); x++) {
                    com.projectrebirth.multiblock.MultiblockSchema.Cell cell = schema.cell(y, r, x);
                    if (cell == null || cell.zone()) {
                        continue;
                    }
                    net.minecraft.world.level.block.state.BlockState state = cell.display();
                    if (cell.controller()) {
                        state = state.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, facing);
                    }
                    level.setBlock(schema.worldPos(controller, facing, x, y, r), state, 3);
                }
            }
        }
        var storage = (com.projectrebirth.logistics.StorageControllerBlockEntity) level.getBlockEntity(controller);
        storage.setOwner(player(ctx).getUUID());
        // a few kinds in every rack's share, to see them on the shelves
        net.minecraft.world.item.Item[] samples = {net.minecraft.world.item.Items.IRON_INGOT, net.minecraft.world.item.Items.COBBLESTONE,
                net.minecraft.world.item.Items.OAK_LOG, net.minecraft.world.item.Items.WHEAT, net.minecraft.world.item.Items.DIAMOND,
                net.minecraft.world.item.Items.REDSTONE, net.minecraft.world.item.Items.COPPER_INGOT, net.minecraft.world.item.Items.GOLD_INGOT,
                net.minecraft.world.item.Items.TORCH, net.minecraft.world.item.Items.IRON_PICKAXE, net.minecraft.world.item.Items.CARROT,
                net.minecraft.world.item.Items.BRICKS};
        int per = storage.slots() / Math.max(1, com.projectrebirth.logistics.StorageControllerBlockEntity.racks(schema));
        for (int k = 0; k * per < storage.slots(); k++) {
            for (int j = 0; j < 4; j++) {
                storage.store().setStackInSlot(k * per + j, new ItemStack(samples[(k * 5 + j * 3) % samples.length], 16));
            }
        }
        var check = storage.revalidate();
        ctx.getSource().sendSuccess(() -> Component.literal("storage at " + controller.toShortString() + ", whole " + (check != null && check.complete())), false);
        return 1;
    }
}
