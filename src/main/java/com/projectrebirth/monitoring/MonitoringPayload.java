package com.projectrebirth.monitoring;

import java.util.List;
import java.util.function.Consumer;

import com.projectrebirth.ProjectRebirth;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Server to a player with a monitoring station's window open (1.6.0, block E): its latest scan and the peaks of the last days.
 * Sent when the window opens and after every scan.
 */
public record MonitoringPayload(BlockPos pos, Snapshot snapshot, List<DayPeak> history) implements CustomPacketPayload {
    public static final Type<MonitoringPayload> TYPE = new Type<>(ProjectRebirth.id("monitoring"));
    public static final StreamCodec<ByteBuf, MonitoringPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MonitoringPayload::pos,
            Snapshot.STREAM_CODEC, MonitoringPayload::snapshot,
            DayPeak.STREAM_CODEC.apply(ByteBufCodecs.list()), MonitoringPayload::history,
            MonitoringPayload::new);

    /** Set by the client side; on a server nothing arrives here. */
    public static Consumer<MonitoringPayload> clientHandler = payload -> {
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(MonitoringPayload::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> clientHandler.accept(payload));
    }
}
