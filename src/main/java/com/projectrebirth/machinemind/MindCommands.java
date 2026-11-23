package com.projectrebirth.machinemind;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.projectrebirth.ProjectRebirth;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator commands: /rebirth mind list, spawn (a node where the caller stands), stage &lt;node&gt; &lt;stage&gt;, kill &lt;node&gt;. */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class MindCommands {
    private MindCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("rebirth")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("mind")
                        .then(Commands.literal("list").executes(MindCommands::list))
                        .then(Commands.literal("spawn").executes(MindCommands::spawn))
                        .then(Commands.literal("stage").then(Commands.argument("node", IntegerArgumentType.integer(1))
                                .then(Commands.argument("stage", IntegerArgumentType.integer(1, 5)).executes(MindCommands::stage))))
                        .then(Commands.literal("kill").then(Commands.argument("node", IntegerArgumentType.integer(1)).executes(MindCommands::kill)))));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        var nodes = MindNodes.get(context.getSource().getServer()).all();
        context.getSource().sendSuccess(() -> Component.translatable("commands.projectrebirth.mind.list", nodes.size()), false);
        for (MindNodes.Node node : nodes) {
            context.getSource().sendSuccess(() -> Component.translatable(node.alive() ? "commands.projectrebirth.mind.node" : "commands.projectrebirth.mind.dead", node.number(),
                    node.pos().getX(), node.pos().getY(), node.pos().getZ(), node.dimension().location().toString(), MindComputerBlockEntity.roman(node.stage())), false);
        }
        return nodes.size();
    }

    private static int spawn(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPos.containing(context.getSource().getPosition()).relative(Direction.fromYRot(context.getSource().getRotation().y), 4);
        if (!level.isEmptyBlock(pos)) {
            context.getSource().sendFailure(Component.translatable("commands.projectrebirth.mind.no_room"));
            return 0;
        }
        level.setBlockAndUpdate(pos, MindRegistry.COMPUTER.get().defaultBlockState());
        if (level.getBlockEntity(pos) instanceof MindComputerBlockEntity computer) {
            computer.forceAwake();
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.projectrebirth.mind.spawned", pos.getX(), pos.getY(), pos.getZ()), true);
        return 1;
    }

    /** The computer of a node, its chunk loaded for it; null (and a message) if there is none alive. */
    private static MindComputerBlockEntity computer(CommandContext<CommandSourceStack> context) {
        int number = IntegerArgumentType.getInteger(context, "node");
        MindNodes.Node node = MindNodes.get(context.getSource().getServer()).byNumber(number);
        if (node != null && node.alive()) {
            ServerLevel level = context.getSource().getServer().getLevel(node.dimension());
            if (level != null && level.getChunkAt(node.pos()).getBlockEntity(node.pos()) instanceof MindComputerBlockEntity computer) {
                return computer;
            }
        }
        context.getSource().sendFailure(Component.translatable("commands.projectrebirth.mind.none", number));
        return null;
    }

    private static int stage(CommandContext<CommandSourceStack> context) {
        MindComputerBlockEntity computer = computer(context);
        if (computer == null) {
            return 0;
        }
        int stage = IntegerArgumentType.getInteger(context, "stage");
        computer.setStage((ServerLevel) computer.getLevel(), stage);
        context.getSource().sendSuccess(() -> Component.translatable("commands.projectrebirth.mind.stage", IntegerArgumentType.getInteger(context, "node"),
                MindComputerBlockEntity.roman(stage), MindComputerBlockEntity.stageName(stage)), true);
        return stage;
    }

    private static int kill(CommandContext<CommandSourceStack> context) {
        MindComputerBlockEntity computer = computer(context);
        if (computer == null) {
            return 0;
        }
        computer.destroy((ServerLevel) computer.getLevel());
        context.getSource().sendSuccess(() -> Component.translatable("commands.projectrebirth.mind.killed", IntegerArgumentType.getInteger(context, "node")), true);
        return 1;
    }
}
