package com.projectrebirth.powerarmor;

import com.projectrebirth.ProjectRebirth;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: the pilot started or stopped holding the emergency hatch key.
 * The server counts the holding time itself, so the client cannot skip the wait.
 */
public record HatchReleasePayload(boolean holding) implements CustomPacketPayload {
    public static final Type<HatchReleasePayload> TYPE = new Type<>(ProjectRebirth.id("hatch_release"));
    public static final StreamCodec<ByteBuf, HatchReleasePayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(HatchReleasePayload::new, HatchReleasePayload::holding);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(HatchReleasePayload::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, HatchReleasePayload::handle);
    }

    private static void handle(HatchReleasePayload payload, IPayloadContext context) {
        if (context.player().getVehicle() instanceof PowerArmorEntity armor) {
            armor.setHatchHeld(context.player(), payload.holding());
        }
    }
}
