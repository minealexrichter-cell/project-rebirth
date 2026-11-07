package com.projectrebirth.environment;

import org.jetbrains.annotations.Nullable;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.core.ModSounds;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What the players of a dimension learn of its catastrophe: messages, the siren, and the state for their clients. Acid
 * rain also brings real rain, so that it looks and wets like rain and does not fall where the biome has none.
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class Catastrophes {
    /** Between two changes the clients get the state this often, in seconds, so that their clocks do not drift. */
    private static final int RESEND_SECONDS = 10;
    private static final int TICKS_PER_SECOND = 20;

    private Catastrophes() {
    }

    /** After the second of a dimension: tell what changed, keep the rain going. {@code before} is the catastrophe before this second. */
    public static void onSecond(ServerLevel level, EnvironmentState state, EnvironmentState.Change change, @Nullable String before) {
        String event = state.event();
        switch (change) {
            case WARNING -> {
                int minutes = Mth.ceil(state.secondsLeft() / 60.0f);
                Component message = state.secondsLeft() < 60
                        ? Component.translatable("message.projectrebirth.environment.warning_soon", name(event))
                        : Component.translatable("message.projectrebirth.environment.warning", name(event), minutes);
                for (ServerPlayer player : level.players()) {
                    player.sendSystemMessage(message);
                    player.playNotifySound(ModSounds.ENVIRONMENT_SIREN.get(), SoundSource.WEATHER, 1.0f, 1.0f);
                }
            }
            case ACTIVE -> {
                tellAll(level, Component.translatable("message.projectrebirth.environment.began", name(event)));
                if (Environment.ACID_RAIN.equals(event)) {
                    rain(level, state.secondsLeft() + RebirthConfig.ENVIRONMENT_AFTERMATH_SECONDS.get());
                }
            }
            case AFTERMATH -> tellAll(level, Component.translatable("message.projectrebirth.environment.fading", name(event)));
            case ENDED -> ended(level, before);
            default -> {
            }
        }
        if (change != EnvironmentState.Change.NONE || level.getGameTime() / TICKS_PER_SECOND % RESEND_SECONDS == 0) {
            sendAll(level, state);
        }
        if (change == EnvironmentState.Change.NONE && state.phase() == EventPhase.ACTIVE && Environment.ACID_RAIN.equals(event)
                && level.getGameTime() / TICKS_PER_SECOND % RESEND_SECONDS == 0 && !level.getLevelData().isRaining()) {
            // Sleeping through the night stops the rain; acid rain goes on.
            rain(level, state.secondsLeft() + RebirthConfig.ENVIRONMENT_AFTERMATH_SECONDS.get());
        }
    }

    /** The end of a catastrophe by itself; the state has already forgotten which it was. */
    public static void ended(ServerLevel level, @Nullable String event) {
        if (event != null) {
            tellAll(level, Component.translatable("message.projectrebirth.environment.ended", name(event)));
            if (Environment.ACID_RAIN.equals(event)) {
                clearRain(level);
            }
        }
    }

    /** An operator ended it. */
    public static void cleared(ServerLevel level, @Nullable String event) {
        ended(level, event);
        sendAll(level, EnvironmentState.get(level));
    }

    private static void rain(ServerLevel level, int seconds) {
        if (RebirthConfig.ENVIRONMENT_ACID_RAIN_WEATHER.get()) {
            level.setWeatherParameters(0, Math.max(1, seconds) * TICKS_PER_SECOND, true, level.isThundering());
        }
    }

    private static void clearRain(ServerLevel level) {
        if (RebirthConfig.ENVIRONMENT_ACID_RAIN_WEATHER.get() && level.getLevelData().isRaining()) {
            level.setWeatherParameters(level.getRandom().nextInt(12000, 36000), 0, false, false);
        }
    }

    private static Component name(@Nullable String event) {
        return Component.translatable("environment.projectrebirth.event." + (event == null ? "none" : event));
    }

    private static void tellAll(ServerLevel level, Component message) {
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(message);
        }
    }

    private static void sendAll(ServerLevel level, EnvironmentState state) {
        CatastrophePayload payload = Environment.isEnabled() ? CatastrophePayload.of(state) : CatastrophePayload.CALM;
        for (ServerPlayer player : level.players()) {
            send(player, payload);
        }
    }

    private static void send(ServerPlayer player, CatastrophePayload payload) {
        if (player.connection.hasChannel(CatastrophePayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private static void sendTo(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            send(serverPlayer, Environment.isEnabled() ? CatastrophePayload.of(EnvironmentState.get(serverPlayer.serverLevel())) : CatastrophePayload.CALM);
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        sendTo(event.getEntity());
    }

    @SubscribeEvent
    static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        sendTo(event.getEntity());
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        sendTo(event.getEntity());
    }
}
