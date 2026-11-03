package com.projectrebirth.factions;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Administrator commands (permission level 2). Players manage their faction through the faction window (key G);
 * these commands let an administrator inspect and fix factions, also from the server console.
 */
final class FactionCommands {
    private FactionCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("faction")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list").executes(FactionCommands::list))
                .then(Commands.literal("disband")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> reply(ctx, FactionService.adminDisband(ctx.getSource().getServer(),
                                        StringArgumentType.getString(ctx, "name"))))))
                .then(Commands.literal("join")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> reply(ctx, FactionService.adminSetMember(ctx.getSource().getServer(),
                                                EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "name")))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> reply(ctx, FactionService.adminRemoveMember(ctx.getSource().getServer(),
                                        EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("friendlyfire")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> reply(ctx, FactionService.adminSetFriendlyFire(ctx.getSource().getServer(),
                                                StringArgumentType.getString(ctx, "name"), BoolArgumentType.getBool(ctx, "enabled"))))))));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        FactionData data = FactionData.get(ctx.getSource().getServer());
        if (data.all().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("message.projectrebirth.faction.none"), false);
            return 0;
        }
        for (Faction faction : data.all()) {
            MutableComponent line = Component.literal(faction.name()).withStyle(faction.color())
                    .append(Component.literal(" (" + (FactionService.friendlyFire(faction) ? "FF on" : "FF off") + "): ")
                            .withStyle(ChatFormatting.GRAY));
            faction.members().forEach((id, name) -> line.append(Component.literal(
                    (id.equals(faction.leader()) ? "*" : "") + name + " ").withStyle(ChatFormatting.WHITE)));
            ctx.getSource().sendSuccess(() -> line, false);
        }
        return data.all().size();
    }

    private static int reply(CommandContext<CommandSourceStack> ctx, FactionService.Result result) throws CommandSyntaxException {
        if (result.success()) {
            ctx.getSource().sendSuccess(result::message, true);
            return 1;
        }
        ctx.getSource().sendFailure(result.message());
        return 0;
    }
}
