package com.projectrebirth.weapons;

import java.util.List;
import java.util.function.Consumer;

import com.projectrebirth.ProjectRebirth;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Server to the shooter and everyone who sees them: a shot was fired, for its flash, tracers, sparks and recoil. It
 * changes nothing in the game; the server has already decided what was hit.
 *
 * @param shooter the entity id of the pilot
 * @param pellets where each bullet or pellet ended
 */
public record WeaponShotPayload(int shooter, List<Pellet> pellets) implements CustomPacketPayload {
    public static final Type<WeaponShotPayload> TYPE = new Type<>(ProjectRebirth.id("weapon_shot"));
    public static final StreamCodec<ByteBuf, WeaponShotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WeaponShotPayload::shooter,
            Pellet.STREAM_CODEC.apply(ByteBufCodecs.list(64)), WeaponShotPayload::pellets,
            WeaponShotPayload::new);

    /** Set by the client side; on a server nothing ever arrives here. */
    public static Consumer<WeaponShotPayload> clientHandler = payload -> {
    };

    public enum Ending {
        NOTHING, BLOCK, ENTITY
    }

    public record Pellet(float x, float y, float z, Ending ending) {
        static final StreamCodec<ByteBuf, Pellet> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Pellet::x,
                ByteBufCodecs.FLOAT, Pellet::y,
                ByteBufCodecs.FLOAT, Pellet::z,
                ByteBufCodecs.idMapper(i -> Ending.values()[i], Ending::ordinal), Pellet::ending,
                Pellet::new);

        public Vec3 end() {
            return new Vec3(x, y, z);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(WeaponShotPayload::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> clientHandler.accept(payload));
    }
}
