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
        root.then(Commands.literal("workshop")
                .then(Commands.argument("corner", BlockPosArgument.blockPos())
                        .then(Commands.argument("recipe", StringArgumentType.word())
                                .executes(DevCommands::workshop))));
        event.getDispatcher().register(root);
    }

    /**
     * Builds a whole body workshop facing south with its lowest layer at the corner, and starts a recipe with the materials of
     * all its stages and full energy (as the game tests do).
     */
    private static int workshop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos corner = BlockPosArgument.getLoadedBlockPos(ctx, "corner");
        net.minecraft.resources.ResourceLocation id = com.projectrebirth.multiblock.MultiblockRegistry.BODY_WORKSHOP;
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
                            ? com.projectrebirth.multiblock.MultiblockRegistry.WORKSHOP_CONTROLLER.get().defaultBlockState()
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
        ctx.getSource().sendSuccess(() -> Component.literal("workshop at " + controller.toShortString() + ", whole " + check.complete()), false);
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

    private static int intruders(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!(ctx.getSource().getLevel().getBlockEntity(BlockPosArgument.getLoadedBlockPos(ctx, "console")) instanceof ConsoleBlockEntity console)) {
            ctx.getSource().sendFailure(Component.literal("not a console"));
            return 0;
        }
        console.setIntruderMode(BoolArgumentType.getBool(ctx, "on"));
        ctx.getSource().sendSuccess(() -> Component.literal("intruder mode " + console.intruderMode()), false);
        return 1;
    }
}
