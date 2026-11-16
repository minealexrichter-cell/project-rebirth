package com.projectrebirth.scouting;

import java.util.function.Consumer;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.monitoring.Snapshot;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Server to a player every few seconds while an analyzer or a sensor works for them (1.6.0, block F).
 *
 * @param tools          which tools read it: {@link Scouting#ANALYZER}, {@link Scouting#SENSOR_TOOL} bits
 * @param snapshot       the contamination of the chunks around, with the sensor's marks
 * @param sourceRadiation radiation of the sources (blocks, ore, creatures) at the player: what makes the analyzer click
 * @param sourceToxin    toxins of the sources at the player
 */
public record ScoutPayload(int tools, Snapshot snapshot, float sourceRadiation, float sourceToxin) implements CustomPacketPayload {
    public static final Type<ScoutPayload> TYPE = new Type<>(ProjectRebirth.id("scout"));
    public static final StreamCodec<ByteBuf, ScoutPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ScoutPayload::tools,
            Snapshot.STREAM_CODEC, ScoutPayload::snapshot,
            ByteBufCodecs.FLOAT, ScoutPayload::sourceRadiation,
            ByteBufCodecs.FLOAT, ScoutPayload::sourceToxin,
            ScoutPayload::new);

    /** Set by the client side; on a server nothing arrives here. */
    public static Consumer<ScoutPayload> clientHandler = payload -> {
    };

    public boolean analyzer() {
        return (tools & Scouting.ANALYZER) != 0;
    }

    public boolean sensor() {
        return (tools & Scouting.SENSOR_TOOL) != 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(ScoutPayload::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> clientHandler.accept(payload));
    }
}
