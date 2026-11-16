package com.projectrebirth.weather;

import org.jetbrains.annotations.Nullable;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.EnvironmentState;

/**
 * What a weather station foresees (1.6.0, block D): the next catastrophe of its dimension within its horizon. Without the long
 * range module only the kind and a rough span of minutes; with it, the exact time, the intensity and the length.
 *
 * @param event         the catastrophe coming
 * @param secondsUntil  seconds until it begins (the truth; shown only by a long range forecast)
 * @param exact         whether the forecast is a long range one
 * @param intensity     its intensity
 * @param activeSeconds how long it will last
 */
public record Forecast(String event, int secondsUntil, boolean exact, float intensity, int activeSeconds) {
    /** What a station sees now; null when no catastrophe is coming within its horizon (or one is going on). */
    @Nullable
    public static Forecast of(EnvironmentState state, boolean longRange) {
        String event = state.comingEvent();
        if (event == null) {
            return null;
        }
        int until = state.secondsUntilComing();
        if (until > horizonSeconds(longRange)) {
            return null;
        }
        return new Forecast(event, until, longRange, state.comingIntensity(), state.comingActiveSeconds());
    }

    public static int horizonSeconds(boolean longRange) {
        return 60 * (longRange ? RebirthConfig.WEATHER_STATION_LONG_FORECAST_MINUTES.get() : RebirthConfig.WEATHER_STATION_FORECAST_MINUTES.get());
    }

    /** The rough span of a short forecast, in minutes: from this... */
    public int roughLow() {
        int step = RebirthConfig.WEATHER_STATION_ROUND_MINUTES.get();
        return secondsUntil / 60 / step * step;
    }

    /** ...to this. */
    public int roughHigh() {
        return roughLow() + RebirthConfig.WEATHER_STATION_ROUND_MINUTES.get();
    }
}
