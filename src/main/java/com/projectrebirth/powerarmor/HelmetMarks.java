package com.projectrebirth.powerarmor;

import java.util.ArrayList;
import java.util.List;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.BodyRegistry;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Marks of the helmet's compass that only the server knows (1.8.0 block C): the pilot's own artificial bodies standing in
 * the same dimension. Sent every two seconds to each pilot whose client has the mod's channel; only for drawing.
 */
public final class HelmetMarks {
    private static final int PERIOD = 40;
    private static final int MAX_MARKS = 16;

    /** A mark: its kind (0 = own body) and its block x and z. */
    public record Mark(int kind, int x, int z) {
        public static final StreamCodec<ByteBuf, Mark> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Mark::kind, ByteBufCodecs.VAR_INT, Mark::x, ByteBufCodecs.VAR_INT, Mark::z, Mark::new);
    }

    public record MarksPayload(List<Mark> marks) implements CustomPacketPayload {
        public static final Type<MarksPayload> TYPE = new Type<>(ProjectRebirth.id("helmet_marks"));
        public static final StreamCodec<ByteBuf, MarksPayload> STREAM_CODEC = StreamCodec.composite(
                Mark.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_MARKS)), MarksPayload::marks, MarksPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The last marks the client got; never used on a server. */
    public static volatile List<Mark> received = List.of();

    private HelmetMarks() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(HelmetMarks::registerPayloads);
        NeoForge.EVENT_BUS.addListener(HelmetMarks::onServerTick);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(MarksPayload.TYPE, MarksPayload.STREAM_CODEC, (payload, context) -> received = payload.marks());
    }

    /** The marks for this pilot now. Public for tests. */
    public static List<Mark> marksFor(ServerPlayer player) {
        List<Mark> marks = new ArrayList<>();
        for (BodyRegistry.BodyRecord record : BodyRegistry.get(player.server).bodiesOf(player.getUUID())) {
            if (record.state() == BodyRegistry.State.STANDING && record.dimension().equals(player.level().dimension()) && marks.size() < MAX_MARKS) {
                marks.add(new Mark(0, (int) Math.floor(record.position().x), (int) Math.floor(record.position().z)));
            }
        }
        return marks;
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % PERIOD != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.getVehicle() instanceof PowerArmorEntity && player.connection.hasChannel(MarksPayload.TYPE)) {
                PacketDistributor.sendToPlayer(player, new MarksPayload(marksFor(player)));
            }
        }
    }
}
