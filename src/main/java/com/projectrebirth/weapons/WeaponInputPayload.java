package com.projectrebirth.weapons;

import com.projectrebirth.ProjectRebirth;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: only which key went down or up. The server decides everything else (see {@link Weapons}). */
public record WeaponInputPayload(Input input) implements CustomPacketPayload {
    public static final Type<WeaponInputPayload> TYPE = new Type<>(ProjectRebirth.id("weapon_input"));
    public static final StreamCodec<ByteBuf, WeaponInputPayload> STREAM_CODEC = ByteBufCodecs.idMapper(i -> Input.values()[i], Input::ordinal)
            .map(WeaponInputPayload::new, WeaponInputPayload::input);

    public enum Input {
        TRIGGER_DOWN, TRIGGER_UP, AIM_DOWN, AIM_UP, RELOAD
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(WeaponInputPayload::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, WeaponInputPayload::handle);
    }

    private static void handle(WeaponInputPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        switch (payload.input()) {
            case TRIGGER_DOWN -> Weapons.pressTrigger(player);
            case TRIGGER_UP -> Weapons.releaseTrigger(player);
            case AIM_DOWN -> Weapons.setAiming(player, true);
            case AIM_UP -> Weapons.setAiming(player, false);
            case RELOAD -> Weapons.startReload(player);
        }
    }
}
