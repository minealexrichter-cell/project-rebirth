package com.projectrebirth.bodies;

import com.projectrebirth.ProjectRebirth;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the player pressed the "return to human body" key. Carries no data; the server decides everything. */
public record ReturnToHumanPayload() implements CustomPacketPayload {
    public static final ReturnToHumanPayload INSTANCE = new ReturnToHumanPayload();
    public static final Type<ReturnToHumanPayload> TYPE = new Type<>(ProjectRebirth.id("return_to_human"));
    public static final StreamCodec<ByteBuf, ReturnToHumanPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ReturnToHumanPayload::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, ReturnToHumanPayload::handle);
    }

    private static void handle(ReturnToHumanPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            TransferService.ReturnResult result = TransferService.returnToHumanBody(player);
            player.displayClientMessage(result.message(), true);
            if (result != TransferService.ReturnResult.SUCCESS) {
                TransferAnimationPayload.sendFailed(player);
            }
        }
    }
}
