package com.projectrebirth.hostile;

import java.util.function.Consumer;

import com.projectrebirth.ProjectRebirth;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Server to everyone who sees a hostile mechanism: it fired, from here to there, for the flash, the tracer and the sparks.
 * It changes nothing; the server has already decided what was hit.
 *
 * @param ending 0 nothing, 1 a block, 2 a body
 */
public record TurretShotPayload(int shooter, float sx, float sy, float sz, float ex, float ey, float ez, int ending) implements CustomPacketPayload {
    public static final Type<TurretShotPayload> TYPE = new Type<>(ProjectRebirth.id("turret_shot"));
    public static final StreamCodec<ByteBuf, TurretShotPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TurretShotPayload decode(ByteBuf buf) {
            return new TurretShotPayload(ByteBufCodecs.VAR_INT.decode(buf), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readByte());
        }

        @Override
        public void encode(ByteBuf buf, TurretShotPayload p) {
            ByteBufCodecs.VAR_INT.encode(buf, p.shooter());
            buf.writeFloat(p.sx()).writeFloat(p.sy()).writeFloat(p.sz()).writeFloat(p.ex()).writeFloat(p.ey()).writeFloat(p.ez());
            buf.writeByte(p.ending());
        }
    };

    /** Set by the client side; on a server nothing ever arrives here. */
    public static Consumer<TurretShotPayload> clientHandler = payload -> {
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(TurretShotPayload::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> clientHandler.accept(payload));
    }
}
