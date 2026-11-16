package com.projectrebirth.client;

import org.jetbrains.annotations.Nullable;

import com.projectrebirth.weather.ForecastPayload;
import com.projectrebirth.weather.WeatherStationBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The forecast of a weather station as this player gets it (1.6.0, block D): kept a little longer than the station sends it, the
 * long range one before a short one. Shown by the catastrophe line of the HUD. Client only.
 */
public final class ForecastClient {
    /** Forgotten when no station has sent it for this long: a few periods of sending. */
    private static final long KEEP_TICKS = 20L * (WeatherStationBlockEntity.SEND_SECONDS * 2 + 2);

    @Nullable
    private static ForecastPayload last;
    private static long receivedAt;

    private ForecastClient() {
    }

    static void register() {
        ForecastPayload.clientHandler = payload -> Minecraft.getInstance().execute(() -> accept(payload));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> last = null);
    }

    private static long now() {
        return Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
    }

    private static void accept(ForecastPayload payload) {
        // a long range forecast is not replaced by a short one from another station
        if (current() == null || payload.exact() || !last.exact()) {
            last = payload;
            receivedAt = now();
        }
    }

    @Nullable
    public static ForecastPayload current() {
        long age = now() - receivedAt;
        return last != null && age >= 0 && age <= KEEP_TICKS ? last : null;
    }

    /** Seconds until the catastrophe of a long range forecast, counted on since it came. */
    public static int secondsUntil(ForecastPayload forecast) {
        return Math.max(0, forecast.secondsUntil() - (int) ((now() - receivedAt) / 20L));
    }

    /** The forecast line of the HUD, or null without a forecast. */
    @Nullable
    public static Component line() {
        ForecastPayload forecast = current();
        if (forecast == null) {
            return null;
        }
        Component name = Component.translatable("environment.projectrebirth.event." + forecast.event());
        if (forecast.exact()) {
            int seconds = secondsUntil(forecast);
            String time = String.format("%d:%02d", seconds / 60, seconds % 60);
            return Component.translatable("hud.projectrebirth.forecast.exact", name, time, Math.round(forecast.intensity() * 100.0f),
                    Math.max(1, (forecast.activeSeconds() + 59) / 60));
        }
        return forecast.roughLow() <= 0
                ? Component.translatable("hud.projectrebirth.forecast.rough_soon", name, forecast.roughHigh())
                : Component.translatable("hud.projectrebirth.forecast.rough", name, forecast.roughLow(), forecast.roughHigh());
    }
}
