package com.projectrebirth.environment;

import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator commands: /rebirth environment status [player], event &lt;type&gt; [intensity], clear. They act on the dimension of whoever calls them. */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class EnvironmentCommands {
    private static final float MIN_INTENSITY = 0.1f;
    private static final float MAX_INTENSITY = 10.0f;

    private EnvironmentCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rebirth")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("environment")
                        .then(Commands.literal("status")
                                .executes(ctx -> status(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> status(ctx, EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("event")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Environment.EVENTS, builder))
                                        .executes(ctx -> startEvent(ctx, StringArgumentType.getString(ctx, "type"), -1.0f))
                                        .then(Commands.argument("intensity", FloatArgumentType.floatArg(MIN_INTENSITY, MAX_INTENSITY))
                                                .executes(ctx -> startEvent(ctx, StringArgumentType.getString(ctx, "type"),
                                                        FloatArgumentType.getFloat(ctx, "intensity"))))))
                        .then(Commands.literal("clear").executes(EnvironmentCommands::clear))));
    }

    private static String number(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static Component eventName(String event) {
        return Component.translatable("environment.projectrebirth.event." + event);
    }

    private static int status(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        CommandSourceStack source = ctx.getSource();
        if (!Environment.isEnabled()) {
            source.sendSuccess(() -> Component.translatable("message.projectrebirth.environment.disabled"), false);
            return 1;
        }
        ServerLevel level = player.serverLevel();
        EnvironmentState state = EnvironmentState.get(level);
        BlockPos pos = BlockPos.containing(player.getEyePosition());
        String biome = level.getBiome(pos).unwrapKey().map(key -> key.location().toString()).orElse("?");
        String profile = Environment.profileFor(level.registryAccess(), level.getBiome(pos))
                .flatMap(found -> level.registryAccess().registryOrThrow(EnvironmentRegistry.PROFILES).getResourceKey(found))
                .map(key -> key.location().getPath()).orElse("-");
        Environment.Shelter shelter = Environment.shelterAt(level, pos);
        Exposure exposure = Environment.exposureAt(level, pos);
        Component phase = Component.translatable("environment.projectrebirth.phase." + state.phase().id());
        Component event = state.event() == null ? Component.literal("-") : eventName(state.event());
        source.sendSuccess(() -> Component.translatable("message.projectrebirth.environment.status_world", level.dimension().location().toString(),
                phase, event, number(state.intensity()), state.secondsLeft()), false);
        source.sendSuccess(() -> Component.translatable("message.projectrebirth.environment.status_place", player.getDisplayName(), biome, profile,
                Component.translatable(shelter.underground() ? "environment.projectrebirth.shelter.underground"
                        : shelter.underRoof() ? "environment.projectrebirth.shelter.roof" : "environment.projectrebirth.shelter.open")), false);
        source.sendSuccess(() -> Component.translatable("message.projectrebirth.environment.status_exposure", number(exposure.get(Hazard.POLLUTION)),
                number(exposure.get(Hazard.RADIATION)), number(exposure.get(Hazard.ACID))), false);
        return 1;
    }

    private static int startEvent(CommandContext<CommandSourceStack> ctx, String event, float givenIntensity) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        RebirthConfig.EnvironmentEventStats stats = RebirthConfig.environmentEvent(event);
        String refusal = !Environment.isEnabled() ? "disabled" : stats == null ? "unknown_event"
                : !stats.enabled().get() ? "event_disabled"
                : !Environment.hasCatastrophes(level.registryAccess(), level.dimension()) ? "no_catastrophes" : null;
        if (refusal != null) {
            source.sendFailure(Component.translatable("message.projectrebirth.environment." + refusal, event));
            return 0;
        }
        RandomSource random = level.getRandom();
        float min = stats.minIntensity().get().floatValue();
        float max = stats.maxIntensity().get().floatValue();
        float intensity = givenIntensity > 0.0f ? givenIntensity : (min + max) / 2.0f;
        int seconds = stats.minActiveSeconds().get() + random.nextInt(Math.max(1, stats.maxActiveSeconds().get() - stats.minActiveSeconds().get() + 1));
        EnvironmentState.get(level).startActive(event, intensity, seconds);
        source.sendSuccess(() -> Component.translatable("message.projectrebirth.environment.started", eventName(event), number(intensity), seconds), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        EnvironmentState.get(ctx.getSource().getLevel()).clear();
        ctx.getSource().sendSuccess(() -> Component.translatable("message.projectrebirth.environment.cleared"), true);
        return 1;
    }
}
