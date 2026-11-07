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
 * Server to the players of a dimension: the catastrophe as everyone may see it, for the sky, the fog, the sounds and the
 * warning in the HUD. Sent when the phase changes, when a player comes into the dimension and now and then in between.
 *
 * @param event        the catastrophe, or "" in the calm
 * @param phase        the phase
 * @param intensity    its intensity
 * @param secondsLeft  seconds left in this phase
 * @param phaseSeconds the whole length of this phase
 */
public record CatastrophePayload(String event, EventPhase phase, float intensity, int secondsLeft, int phaseSeconds) implements CustomPacketPayload {
    public static final CatastrophePayload CALM = new CatastrophePayload("", EventPhase.CALM, 0.0f, 0, 0);
    public static final Type<CatastrophePayload> TYPE = new Type<>(ProjectRebirth.id("catastrophe"));
    private static final StreamCodec<ByteBuf, EventPhase> PHASE_CODEC = ByteBufCodecs.VAR_INT.map(id -> EventPhase.values()[Math.floorMod(id, EventPhase.values().length)], EventPhase::ordinal);
    public static final StreamCodec<ByteBuf, CatastrophePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CatastrophePayload::event,
            PHASE_CODEC, CatastrophePayload::phase,
            ByteBufCodecs.FLOAT, CatastrophePayload::intensity,
            ByteBufCodecs.VAR_INT, CatastrophePayload::secondsLeft,
            ByteBufCodecs.VAR_INT, CatastrophePayload::phaseSeconds,
            CatastrophePayload::new);

    /** Set by the client side; on a server nothing arrives here. */
    public static Consumer<CatastrophePayload> clientHandler = payload -> {
    };

    public static CatastrophePayload of(EnvironmentState state) {
        if (state.phase() == EventPhase.CALM || state.event() == null) {
            return CALM;
        }
        return new CatastrophePayload(state.event(), state.phase(), state.intensity(), state.secondsLeft(), state.phaseSeconds());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(CatastrophePayload::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> clientHandler.accept(payload));
    }
}
