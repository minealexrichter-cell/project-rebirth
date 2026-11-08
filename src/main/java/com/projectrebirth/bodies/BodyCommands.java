package com.projectrebirth.bodies;

import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Operator commands for testing and administration:
 * /rebirth status [player], /rebirth return [player], /rebirth transfer [player] [capsule position],
 * /rebirth charge [player] [percent], /rebirth heat [player] [0-100],
 * /rebirth wear [player] [0-100] (1.5.0).
 */
public final class BodyCommands {
    private BodyCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rebirth")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> status(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("return")
                        .executes(ctx -> returnToHuman(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> returnToHuman(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("transfer")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("capsule", BlockPosArgument.blockPos())
                                        .executes(ctx -> transfer(ctx, EntityArgument.getPlayer(ctx, "player"),
                                                BlockPosArgument.getLoadedBlockPos(ctx, "capsule"))))))
                .then(Commands.literal("charge")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("percent", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 100))
                                        .executes(ctx -> charge(ctx, EntityArgument.getPlayer(ctx, "player"),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "percent"))))))
                .then(Commands.literal("heat")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("heat", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 100))
                                        .executes(ctx -> heat(ctx, EntityArgument.getPlayer(ctx, "player"),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "heat"))))))
                .then(Commands.literal("wear")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("wear", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 100))
                                        .executes(ctx -> wear(ctx, EntityArgument.getPlayer(ctx, "player"),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "wear")))))));
    }

    private static int status(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        Component body = TransferService.state(player).body()
                .<Component>map(bodyClass -> Component.translatable(bodyClass.translationKey()))
                .orElse(Component.translatable("message.projectrebirth.command.human"));
        Component shell = ShellRegistry.get(player.server).get(player.getUUID())
                .<Component>map(record -> Component.translatable("message.projectrebirth.command.shell_at",
                        record.dimension().location().toString(),
                        (int) Math.floor(record.position().x), (int) Math.floor(record.position().y), (int) Math.floor(record.position().z),
                        String.format(Locale.ROOT, "%.1f", record.health())))
                .orElse(Component.translatable("message.projectrebirth.command.no_shell"));
        ctx.getSource().sendSuccess(() -> Component.translatable("message.projectrebirth.command.status",
                player.getDisplayName(), body, String.format(Locale.ROOT, "%.1f", player.getHealth()), String.format(Locale.ROOT, "%.1f", player.getMaxHealth()), shell), false);
        return 1;
    }

    /** Sets the charge of the body a player controls (1.5.0), for tests and administration. */
    private static int charge(CommandContext<CommandSourceStack> ctx, ServerPlayer player, int percent) {
        if (!TransferService.isInArtificialBody(player)) {
            ctx.getSource().sendFailure(Component.translatable("message.projectrebirth.transfer.not_in_body"));
            return 0;
        }
        com.projectrebirth.bodies.power.BodyPower.setCharge(player, com.projectrebirth.bodies.power.BodyPower.capacity(player) * percent / 100.0f);
        com.projectrebirth.bodies.power.BodyPower.applyLimits(player);
        ctx.getSource().sendSuccess(() -> Component.translatable("message.projectrebirth.command.charge", player.getDisplayName(), percent), true);
        return 1;
    }

    /** Sets the heat (0-100) of the body a player controls (1.5.0), for tests and administration. */
    private static int heat(CommandContext<CommandSourceStack> ctx, ServerPlayer player, int heat) {
        if (!TransferService.isInArtificialBody(player)) {
            ctx.getSource().sendFailure(Component.translatable("message.projectrebirth.transfer.not_in_body"));
            return 0;
        }
        com.projectrebirth.bodies.power.BodyHeat.setHeat(player, heat);
        com.projectrebirth.bodies.power.BodyHeat.tickSecond(player);
        ctx.getSource().sendSuccess(() -> Component.translatable("message.projectrebirth.command.heat", player.getDisplayName(), heat), true);
        return 1;
    }

    /** Sets the wear (0-100%) of the body a player controls (1.5.0), for tests and administration. */
    private static int wear(CommandContext<CommandSourceStack> ctx, ServerPlayer player, int wear) {
        if (!TransferService.isInArtificialBody(player)) {
            ctx.getSource().sendFailure(Component.translatable("message.projectrebirth.transfer.not_in_body"));
            return 0;
        }
        com.projectrebirth.bodies.power.BodyWear.setWear(player, wear);
        com.projectrebirth.bodies.power.BodyWear.applyLimits(player);
        ctx.getSource().sendSuccess(() -> Component.translatable("message.projectrebirth.command.wear", player.getDisplayName(), wear), true);
        return 1;
    }

    private static int returnToHuman(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        TransferService.ReturnResult result = TransferService.returnToHumanBody(player);
        ctx.getSource().sendSuccess(result::message, true);
        return result == TransferService.ReturnResult.SUCCESS ? 1 : 0;
    }

    private static int transfer(CommandContext<CommandSourceStack> ctx, ServerPlayer player, BlockPos pos) throws CommandSyntaxException {
        BlockState state = player.serverLevel().getBlockState(pos);
        if (!state.is(BodyBlocks.TRANSFER_CAPSULE.get())) {
            ctx.getSource().sendFailure(Component.translatable("message.projectrebirth.command.not_capsule"));
            return 0;
        }
        BlockPos lower = state.getValue(TransferCapsuleBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        TransferService.TransferResult result = TransferService.transferFromCapsule(player, lower, state.getValue(TransferCapsuleBlock.FACING));
        ctx.getSource().sendSuccess(result::message, true);
        return result == TransferService.TransferResult.SUCCESS ? 1 : 0;
    }
}
