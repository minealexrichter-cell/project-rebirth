package com.projectrebirth.wanderers;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.projectrebirth.ProjectRebirth;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator commands: /rebirth wanderers spawn &lt;group&gt; (a whole group a few blocks ahead), /rebirth wanderers count. */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class WandererCommands {
    private WandererCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("rebirth")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("wanderers")
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("group", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(context.getSource().getLevel().registryAccess()
                                                .registryOrThrow(WandererData.GROUPS).keySet().stream().map(id -> id.getPath()), builder))
                                        .executes(WandererCommands::spawn)))
                        .then(Commands.literal("count").executes(WandererCommands::count))));
    }

    private static int spawn(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        String name = StringArgumentType.getString(context, "group");
        var spec = level.registryAccess().registryOrThrow(WandererData.GROUPS)
                .getOptional(ResourceKey.create(WandererData.GROUPS, ProjectRebirth.id(name)));
        if (spec.isEmpty()) {
            source.sendFailure(Component.translatable("commands.projectrebirth.wanderers.unknown", name));
            return 0;
        }
        Vec3 ahead = source.getPosition().add(source.getRotation().y == 0 && source.getEntity() == null ? Vec3.ZERO
                : Vec3.directionFromRotation(0.0f, source.getRotation().y).scale(6.0));
        WandererEntity head = WandererGroups.spawnGroup(level, spec.get(), BlockPos.containing(ahead), level.random);
        if (head == null) {
            source.sendFailure(Component.translatable("commands.projectrebirth.wanderers.failed", name));
            return 0;
        }
        int size = head.group().size();
        source.sendSuccess(() -> Component.translatable("commands.projectrebirth.wanderers.spawned", name, size), true);
        return size;
    }

    private static int count(CommandContext<CommandSourceStack> context) {
        int groups = WandererGroups.groupsInWorld(context.getSource().getLevel());
        context.getSource().sendSuccess(() -> Component.translatable("commands.projectrebirth.wanderers.count", groups), false);
        return groups;
    }
}
