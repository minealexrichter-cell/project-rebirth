package com.projectrebirth.bodies;

import javax.annotation.Nullable;

import com.projectrebirth.ProjectRebirth;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Server to client: tells the client how a transfer ended, so that it can finish its transfer animation. It changes
 * nothing in the game; the transfer itself is always done by the server, whether the client plays the animation or not.
 *
 * @param arrived       true: the player has moved to the new body; false: the requested transfer did not happen
 * @param sameDimension the player was in the same dimension before the transfer
 */
public record TransferAnimationPayload(boolean arrived, boolean sameDimension, double fromX, double fromY, double fromZ)
        implements CustomPacketPayload {
    public static final Type<TransferAnimationPayload> TYPE = new Type<>(ProjectRebirth.id("transfer_animation"));
    public static final StreamCodec<ByteBuf, TransferAnimationPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, TransferAnimationPayload::arrived,
            ByteBufCodecs.BOOL, TransferAnimationPayload::sameDimension,
            ByteBufCodecs.DOUBLE, TransferAnimationPayload::fromX,
            ByteBufCodecs.DOUBLE, TransferAnimationPayload::fromY,
            ByteBufCodecs.DOUBLE, TransferAnimationPayload::fromZ,
            TransferAnimationPayload::new);

    /** The last message received on the client; the client side reads it every tick. Never used on a server. */
    @Nullable
    public static volatile TransferAnimationPayload received;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(TransferAnimationPayload::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> received = payload);
    }

    static void sendArrived(ServerPlayer player, boolean sameDimension, Vec3 from) {
        send(player, new TransferAnimationPayload(true, sameDimension, from.x, from.y, from.z));
    }

    static void sendFailed(ServerPlayer player) {
        send(player, new TransferAnimationPayload(false, true, player.getX(), player.getY(), player.getZ()));
    }

    /** Test players have no real client behind them. */
    private static void send(ServerPlayer player, TransferAnimationPayload payload) {
        if (player.connection != null && player.connection.hasChannel(TYPE)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
