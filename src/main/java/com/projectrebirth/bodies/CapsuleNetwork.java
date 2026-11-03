package com.projectrebirth.bodies;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The capsule window: the server sends the player's bodies with what can be done with each, the client asks to
 * transfer into one (or to forget a destroyed one), and the server checks everything again. A body in an unloaded
 * chunk is loaded for a few seconds and the transfer is retried as soon as it appears.
 */
public final class CapsuleNetwork {
    /** How long a chosen body's chunk is kept loaded, and how long the transfer waits for it. */
    private static final int LOAD_TICKS = 100;
    private static final TicketType<ChunkPos> LOAD_TICKET =
            TicketType.create(ProjectRebirth.MOD_ID + "_body_load", Comparator.comparingLong(ChunkPos::toLong), LOAD_TICKS);
    /** A player may use the capsule window from this far (squared distance to the capsule centre). */
    private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private record Pending(BlockPos capsule, UUID body, long until) {
    }

    private CapsuleNetwork() {
    }

    // ---------------------------------------------------------------- payloads

    /** What the window may offer for a body. */
    public enum Availability {
        AVAILABLE, TOO_FAR, OTHER_DIMENSION, OCCUPIED, DESTROYED;

        public static final StreamCodec<ByteBuf, Availability> STREAM_CODEC =
                ByteBufCodecs.VAR_INT.map(id -> values()[Mth.clamp(id, 0, values().length - 1)], Availability::ordinal);
    }

    /** One row of the window. {@code distance} is in blocks from the capsule, -1 in another dimension. */
    public record Entry(UUID id, BodyClass bodyClass, String dimension, BlockPos pos, float health, float maxHealth,
                        int distance, Availability availability) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.of(
                (buf, entry) -> {
                    UUIDUtil.STREAM_CODEC.encode(buf, entry.id());
                    BodyClass.STREAM_CODEC.encode(buf, entry.bodyClass());
                    ByteBufCodecs.STRING_UTF8.encode(buf, entry.dimension());
                    BlockPos.STREAM_CODEC.encode(buf, entry.pos());
                    buf.writeFloat(entry.health());
                    buf.writeFloat(entry.maxHealth());
                    ByteBufCodecs.VAR_INT.encode(buf, entry.distance());
                    Availability.STREAM_CODEC.encode(buf, entry.availability());
                },
                buf -> new Entry(UUIDUtil.STREAM_CODEC.decode(buf), BodyClass.STREAM_CODEC.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf), BlockPos.STREAM_CODEC.decode(buf), buf.readFloat(), buf.readFloat(),
                        ByteBufCodecs.VAR_INT.decode(buf), Availability.STREAM_CODEC.decode(buf)));
    }

    /** Server to client: open (or refresh) the window of this capsule. */
    public record OpenPayload(BlockPos capsule, int radius, List<Entry> bodies) implements CustomPacketPayload {
        public static final Type<OpenPayload> TYPE = new Type<>(ProjectRebirth.id("capsule_open"));
        public static final StreamCodec<ByteBuf, OpenPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OpenPayload::capsule,
                ByteBufCodecs.VAR_INT, OpenPayload::radius,
                Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenPayload::bodies,
                OpenPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: transfer into this body, or ({@code forget}) remove a destroyed body from the list. */
    public record ActionPayload(BlockPos capsule, UUID body, boolean forget) implements CustomPacketPayload {
        public static final Type<ActionPayload> TYPE = new Type<>(ProjectRebirth.id("capsule_action"));
        public static final StreamCodec<ByteBuf, ActionPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, ActionPayload::capsule,
                UUIDUtil.STREAM_CODEC, ActionPayload::body,
                ByteBufCodecs.BOOL, ActionPayload::forget,
                ActionPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The last window data received on the client; the client side opens the screen from it. Never used on a server. */
    @Nullable
    public static volatile OpenPayload received;

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(CapsuleNetwork::registerPayloads);
        NeoForge.EVENT_BUS.addListener(CapsuleNetwork::onServerTick);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(OpenPayload.TYPE, OpenPayload.STREAM_CODEC, (payload, context) -> received = payload);
        registrar.playToServer(ActionPayload.TYPE, ActionPayload.STREAM_CODEC, CapsuleNetwork::onAction);
    }

    /** Whether this player's client can show the window (a client without the mod's channel cannot). */
    public static boolean canShowWindow(ServerPlayer player) {
        return player.connection != null && player.connection.hasChannel(OpenPayload.TYPE);
    }

    // ---------------------------------------------------------------- server

    /** The rows of the window for a player at this capsule. Public for tests. */
    public static List<Entry> entriesFor(ServerPlayer player, ServerLevel level, BlockPos capsuleLower) {
        Vec3 center = Vec3.atBottomCenterOf(capsuleLower);
        List<Entry> entries = new ArrayList<>();
        for (BodyRegistry.BodyRecord record : BodyRegistry.get(player.server).bodiesOf(player.getUUID())) {
            boolean sameDimension = record.dimension().equals(level.dimension());
            int distance = sameDimension ? (int) Math.round(record.position().distanceTo(center)) : -1;
            Availability availability = switch (record.state()) {
                case DESTROYED -> Availability.DESTROYED;
                case CONTROLLED -> Availability.OCCUPIED;
                case STANDING -> !sameDimension ? Availability.OTHER_DIMENSION
                        : TransferService.withinRadius(record.position(), center) ? Availability.AVAILABLE : Availability.TOO_FAR;
            };
            entries.add(new Entry(record.id(), record.bodyClass(), record.dimension().location().toString(),
                    BlockPos.containing(record.position()), record.health(), record.maxHealth(), distance, availability));
        }
        entries.sort(Comparator.comparingInt((Entry entry) -> entry.availability().ordinal())
                .thenComparingInt(entry -> entry.distance() < 0 ? Integer.MAX_VALUE : entry.distance()));
        return entries;
    }

    public static void openWindow(ServerPlayer player, BlockPos capsuleLower) {
        PacketDistributor.sendToPlayer(player, new OpenPayload(capsuleLower, RebirthConfig.TRANSFER_RADIUS.get(),
                entriesFor(player, player.serverLevel(), capsuleLower)));
    }

    private static void onAction(ActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos capsule = payload.capsule();
        Direction facing = capsuleFacing(player.serverLevel(), capsule);
        if (facing == null || player.distanceToSqr(Vec3.atBottomCenterOf(capsule)) > MAX_DISTANCE_SQR) {
            TransferAnimationPayload.sendFailed(player);
            return;
        }
        if (payload.forget()) {
            BodyRegistry registry = BodyRegistry.get(player.server);
            registry.find(player.getUUID(), payload.body())
                    .filter(record -> record.state() == BodyRegistry.State.DESTROYED)
                    .ifPresent(record -> registry.remove(player.getUUID(), record.id()));
            openWindow(player, capsule);
            return;
        }
        transfer(player, capsule, facing, payload.body());
    }

    /** Runs a transfer the window asked for; on failure the window is refreshed so it shows the new state. */
    public static TransferService.TransferResult transfer(ServerPlayer player, BlockPos capsule, Direction facing, UUID body) {
        TransferService.TransferResult result = TransferService.transferFromCapsule(player, capsule, facing, body);
        if (result == TransferService.TransferResult.LOADING) {
            BodyRegistry.get(player.server).find(player.getUUID(), body).ifPresent(record -> {
                ChunkPos chunk = new ChunkPos(BlockPos.containing(record.position()));
                player.serverLevel().getChunkSource().addRegionTicket(LOAD_TICKET, chunk, 2, chunk);
                PENDING.put(player.getUUID(), new Pending(capsule, body, player.serverLevel().getGameTime() + LOAD_TICKS));
            });
        } else if (result != TransferService.TransferResult.SUCCESS && canShowWindow(player)) {
            openWindow(player, capsule);
        }
        player.displayClientMessage(result.message(), true);
        return result;
    }

    @Nullable
    private static Direction capsuleFacing(ServerLevel level, BlockPos lower) {
        BlockState state = level.getBlockState(lower);
        if (state.getBlock() instanceof TransferCapsuleBlock && state.getValue(TransferCapsuleBlock.HALF) == DoubleBlockHalf.LOWER) {
            return state.getValue(TransferCapsuleBlock.FACING);
        }
        return null;
    }

    /** Retries waiting transfers once the body's chunk has loaded. The map is empty almost always. */
    private static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        // A copy: a retried transfer may wait again and put its player back into the map.
        for (Map.Entry<UUID, Pending> entry : new ArrayList<>(PENDING.entrySet())) {
            Pending pending = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                PENDING.remove(entry.getKey());
                continue;
            }
            ServerLevel level = player.serverLevel();
            BlockPos at = BodyRegistry.get(server).find(player.getUUID(), pending.body())
                    .map(record -> BlockPos.containing(record.position())).orElse(pending.capsule());
            boolean ready = level.getEntity(pending.body()) != null || level.areEntitiesLoaded(new ChunkPos(at).toLong());
            boolean expired = level.getGameTime() > pending.until();
            if (!ready && !expired) {
                continue;
            }
            PENDING.remove(entry.getKey());
            Direction facing = capsuleFacing(level, pending.capsule());
            if (facing == null) {
                continue;
            }
            if (ready) {
                transfer(player, pending.capsule(), facing, pending.body());
            } else {
                player.displayClientMessage(TransferService.TransferResult.BODY_UNAVAILABLE.message(), true);
                openWindow(player, pending.capsule());
            }
        }
    }
}
