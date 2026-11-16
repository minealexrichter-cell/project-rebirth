package com.projectrebirth.weather;

import java.util.function.Consumer;

import com.projectrebirth.ProjectRebirth;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Server to a player: a weather station's forecast for their HUD (1.6.0, block D), sent every few seconds while it holds. A short
 * forecast carries only the kind and the span of minutes: the exact time stays on the server.
 *
 * @param event         the catastrophe coming
 * @param exact         a long range forecast
 * @param secondsUntil  seconds until it begins (long range only, else -1)
 * @param roughLow      the span of minutes of a short forecast, from...
 * @param roughHigh     ...to
 * @param intensity     its intensity (long range only)
 * @param activeSeconds its length (long range only)
 */
public record ForecastPayload(String event, boolean exact, int secondsUntil, int roughLow, int roughHigh, float intensity, int activeSeconds)
        implements CustomPacketPayload {
    public static final Type<ForecastPayload> TYPE = new Type<>(ProjectRebirth.id("forecast"));
    public static final StreamCodec<ByteBuf, ForecastPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.event());
                buf.writeBoolean(payload.exact());
                ByteBufCodecs.VAR_INT.encode(buf, payload.secondsUntil());
                ByteBufCodecs.VAR_INT.encode(buf, payload.roughLow());
                ByteBufCodecs.VAR_INT.encode(buf, payload.roughHigh());
                buf.writeFloat(payload.intensity());
                ByteBufCodecs.VAR_INT.encode(buf, payload.activeSeconds());
            },
            buf -> new ForecastPayload(ByteBufCodecs.STRING_UTF8.decode(buf), buf.readBoolean(), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), buf.readFloat(), ByteBufCodecs.VAR_INT.decode(buf)));

    /** Set by the client side; on a server nothing arrives here. */
    public static Consumer<ForecastPayload> clientHandler = payload -> {
    };

    public static ForecastPayload of(Forecast forecast) {
        return forecast.exact()
                ? new ForecastPayload(forecast.event(), true, forecast.secondsUntil(), 0, 0, forecast.intensity(), forecast.activeSeconds())
                : new ForecastPayload(forecast.event(), false, -1, forecast.roughLow(), forecast.roughHigh(), 0.0f, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(ForecastPayload::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> clientHandler.accept(payload));
    }
}
