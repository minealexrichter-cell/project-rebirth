package com.projectrebirth.powerarmor;

import com.projectrebirth.ProjectRebirth;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the pilot of a worker armor pressed the mining mode key (3x3 or one block). */
public record MiningModePayload() implements CustomPacketPayload {
    public static final MiningModePayload INSTANCE = new MiningModePayload();
    public static final Type<MiningModePayload> TYPE = new Type<>(ProjectRebirth.id("mining_mode"));
    public static final StreamCodec<ByteBuf, MiningModePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(MiningModePayload::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, MiningModePayload::handle);
    }

    private static void handle(MiningModePayload payload, IPayloadContext context) {
        if (context.player().getVehicle() instanceof PowerArmorEntity armor && armor.getVariant() == ArmorVariant.WORKER
                && armor.getPilot() == context.player()) {
            armor.setWideMining(!armor.isWideMining());
            context.player().displayClientMessage(Component.translatable(armor.isWideMining()
                    ? "message.projectrebirth.armor.mining_wide" : "message.projectrebirth.armor.mining_single"), true);
        }
    }
}
