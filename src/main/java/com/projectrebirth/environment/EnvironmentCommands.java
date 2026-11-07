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

/** Operator commands: /rebirth environment status [player], event &lt;type&gt; [intensity], warn &lt;type&gt; [intensity], clear, body &lt;player&gt; &lt;dose&gt; &lt;toxin&gt;. They act on the dimension of whoever calls them. */
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
                        .then(Commands.literal("warn")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Environment.EVENTS, builder))
                                        .executes(ctx -> warnEvent(ctx, StringArgumentType.getString(ctx, "type"), -1.0f))
                                        .then(Commands.argument("intensity", FloatArgumentType.floatArg(MIN_INTENSITY, MAX_INTENSITY))
                                                .executes(ctx -> warnEvent(ctx, StringArgumentType.getString(ctx, "type"),
                                                        FloatArgumentType.getFloat(ctx, "intensity"))))))
                        .then(Commands.literal("clear").executes(EnvironmentCommands::clear))
                        .then(Commands.literal("room")
                                .then(Commands.argument("unit", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                        .executes(EnvironmentCommands::room)))
                        .then(Commands.literal("body")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("dose", FloatArgumentType.floatArg(0.0f))
                                                .then(Commands.argument("toxin", FloatArgumentType.floatArg(0.0f))
                                                        .executes(EnvironmentCommands::setBody)))))));
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
        com.projectrebirth.shelter.Room room = com.projectrebirth.shelter.Shelters.sealedRoomAt(level, pos);
        Exposure exposure = Environment.exposureAt(level, pos);
        Component phase = Component.translatable("environment.projectrebirth.phase." + state.phase().id());
        Component event = state.event() == null ? Component.literal("-") : eventName(state.event());
        source.sendSuccess(() -> Component.translatable("message.projectrebirth.environment.status_world", level.dimension().location().toString(),
                phase, event, number(state.intensity()), state.secondsLeft()), false);
        source.sendSuccess(() -> Component.translatable("message.projectrebirth.environment.status_place", player.getDisplayName(), biome, profile,
                Component.translatable(room != null ? "environment.projectrebirth.shelter.sealed"
                        : shelter.underground() ? "environment.projectrebirth.shelter.underground"
                        : shelter.underRoof() ? "environment.projectrebirth.shelter.roof" : "environment.projectrebirth.shelter.open")), false);
        source.sendSuccess(() -> Component.translatable("message.projectrebirth.environment.status_exposure", number(exposure.get(Hazard.POLLUTION)),
                number(exposure.get(Hazard.RADIATION)), number(exposure.get(Hazard.ACID))), false);
        Biology biology = HumanBody.biology(player);
        source.sendSuccess(() -> Component.translatable("message.projectrebirth.environment.status_body", number(biology.dose()), HumanBody.doseStage(biology.dose()),
                number(biology.toxin()), HumanBody.toxinStage(biology.toxin())), false);
        source.sendSuccess(() -> Component.translatable("message.projectrebirth.environment.status_contamination",
                number(com.projectrebirth.shelter.Contamination.of(player.getVehicle() instanceof com.projectrebirth.powerarmor.PowerArmorEntity armor ? armor : player))), false);
        return 1;
    }

    /** Scans the room of an air filtration unit now and tells it, with the time the scan took. */
    private static int room(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos unit = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(ctx, "unit");
        if (!(level.getBlockState(unit).getBlock() instanceof com.projectrebirth.shelter.AirFiltrationUnitBlock)) {
            ctx.getSource().sendFailure(Component.translatable("message.projectrebirth.shelter.no_unit"));
            return 0;
        }
        com.projectrebirth.shelter.Shelters shelters = com.projectrebirth.shelter.Shelters.of(level);
        shelters.scanNow(unit);
        ctx.getSource().sendSuccess(() -> com.projectrebirth.shelter.AirFiltrationUnitBlock.status(level, unit), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("message.projectrebirth.shelter.scan_time", shelters.lastScanBlocks(),
                String.format(Locale.ROOT, "%.2f", shelters.lastScanNanos() / 1_000_000.0)), false);
        return 1;
    }

    /** Sets the numbers of a person, for checking the symptoms. */
    private static int setBody(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        HumanBody.setBiology(player, new Biology(FloatArgumentType.getFloat(ctx, "dose"), FloatArgumentType.getFloat(ctx, "toxin")));
        ctx.getSource().sendSuccess(() -> Component.translatable("message.projectrebirth.environment.body_set", player.getDisplayName()), true);
        return 1;
    }

    private static int warnEvent(CommandContext<CommandSourceStack> ctx, String event, float givenIntensity) {
        return begin(ctx, event, givenIntensity, true);
    }

    private static int startEvent(CommandContext<CommandSourceStack> ctx, String event, float givenIntensity) {
        return begin(ctx, event, givenIntensity, false);
    }

    private static int begin(CommandContext<CommandSourceStack> ctx, String event, float givenIntensity, boolean warn) {
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
        EnvironmentState state = EnvironmentState.get(level);
        String was = state.event();
        if (was != null && !was.equals(event) && state.phase() != EventPhase.WARNING) {
            // The one going on gives way: its rain stops.
            Catastrophes.ended(level, was);
        }
        if (warn) {
            state.startWarning(event, intensity, seconds, RebirthConfig.ENVIRONMENT_WARNING_SECONDS.get());
        } else {
            state.startActive(event, intensity, seconds);
        }
        Catastrophes.onSecond(level, state, warn ? EnvironmentState.Change.WARNING : EnvironmentState.Change.ACTIVE, event);
        source.sendSuccess(() -> Component.translatable(warn ? "message.projectrebirth.environment.warned" : "message.projectrebirth.environment.started", eventName(event), number(intensity), seconds), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        EnvironmentState state = EnvironmentState.get(level);
        String was = state.event();
        state.clear();
        Catastrophes.cleared(level, was);
        ctx.getSource().sendSuccess(() -> Component.translatable("message.projectrebirth.environment.cleared"), true);
        return 1;
    }
}
