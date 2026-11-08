package com.projectrebirth.environment;

import java.util.function.Consumer;

import com.projectrebirth.ProjectRebirth;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Server to one player, once per second: what their own HUD may show. It decides nothing on the client.
 *
 * @param radiation the radiation reaching them now, after protection
 * @param pollution the polluted air reaching them now, after protection
 * @param acid      the acid reaching them now, after protection
 * @param dose      their dose
 * @param toxin     their toxic load
 * @param danger    which hazards are dangerous for them now: bit {@code 1 << hazard.ordinal()}
 * @param shelter   where they are: {@link #SHELTER_NONE}, {@link #SHELTER_CLEAN}, {@link #SHELTER_POLLUTED}, {@link #SHELTER_LEAK}
 * @param contamination their external contamination
 * @param temperature   the temperature where they are, in degrees (NaN with temperature switched off)
 * @param thermal       their heat (above 0) or cold (below 0) stress
 */
public record EnvironmentStatusPayload(float radiation, float pollution, float acid, float dose, float toxin, int danger, int shelter,
        float contamination, float temperature, float thermal) implements CustomPacketPayload {
    public static final int SHELTER_NONE = 0;
    public static final int SHELTER_CLEAN = 1;
    public static final int SHELTER_POLLUTED = 2;
    public static final int SHELTER_LEAK = 3;

    public static final Type<EnvironmentStatusPayload> TYPE = new Type<>(ProjectRebirth.id("environment_status"));
    public static final StreamCodec<ByteBuf, EnvironmentStatusPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeFloat(payload.radiation());
                buf.writeFloat(payload.pollution());
                buf.writeFloat(payload.acid());
                buf.writeFloat(payload.dose());
                buf.writeFloat(payload.toxin());
                ByteBufCodecs.VAR_INT.encode(buf, payload.danger());
                ByteBufCodecs.VAR_INT.encode(buf, payload.shelter());
                buf.writeFloat(payload.contamination());
                buf.writeFloat(payload.temperature());
                buf.writeFloat(payload.thermal());
            },
            buf -> new EnvironmentStatusPayload(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), buf.readFloat(), buf.readFloat(), buf.readFloat()));

    /** Set by the client side; on a server nothing arrives here. */
    public static Consumer<EnvironmentStatusPayload> clientHandler = payload -> {
    };

    public boolean dangerous(Hazard hazard) {
        return (danger & (1 << hazard.ordinal())) != 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(EnvironmentStatusPayload::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> clientHandler.accept(payload));
    }
}
