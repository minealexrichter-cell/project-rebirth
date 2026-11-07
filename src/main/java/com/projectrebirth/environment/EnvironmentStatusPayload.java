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
 * @param dose      their dose
 * @param toxin     their toxic load
 */
public record EnvironmentStatusPayload(float radiation, float dose, float toxin) implements CustomPacketPayload {
    public static final Type<EnvironmentStatusPayload> TYPE = new Type<>(ProjectRebirth.id("environment_status"));
    public static final StreamCodec<ByteBuf, EnvironmentStatusPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, EnvironmentStatusPayload::radiation,
            ByteBufCodecs.FLOAT, EnvironmentStatusPayload::dose,
            ByteBufCodecs.FLOAT, EnvironmentStatusPayload::toxin,
            EnvironmentStatusPayload::new);

    /** Set by the client side; on a server nothing arrives here. */
    public static Consumer<EnvironmentStatusPayload> clientHandler = payload -> {
    };

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
