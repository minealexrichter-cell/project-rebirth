package com.projectrebirth.powerarmor;

import com.projectrebirth.ProjectRebirth;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: the pilot pressed attack. Hitting air sends nothing to the server in vanilla,
 * so without this the armor's punch would only play when something was hit.
 */
public record ArmorSwingPayload() implements CustomPacketPayload {
    public static final ArmorSwingPayload INSTANCE = new ArmorSwingPayload();
    public static final Type<ArmorSwingPayload> TYPE = new Type<>(ProjectRebirth.id("armor_swing"));
    public static final StreamCodec<ByteBuf, ArmorSwingPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ArmorSwingPayload::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, ArmorSwingPayload::handle);
    }

    private static void handle(ArmorSwingPayload payload, IPayloadContext context) {
        if (context.player().getVehicle() instanceof PowerArmorEntity armor && armor.isActive()) {
            armor.playAttackAnimation();
        }
    }
}
