package com.projectrebirth.multiblock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.projectrebirth.ProjectRebirth;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Server to a player with a controller's window open (1.7.0, block A): what is wrong with the construction, for the list of
 * errors. Sent when the window opens and whenever the controller looks at its construction again.
 */
public record MultiblockStatusPayload(BlockPos pos, boolean planned, List<Entry> problems) implements CustomPacketPayload {
    /** Shown in the window at most this many. */
    public static final int MAX = 24;

    /** One wrong place: where, what is wrong (the ordinal of the problem kind), the block it needs. */
    public record Entry(BlockPos pos, int kind, ResourceLocation block) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Entry::pos,
                ByteBufCodecs.VAR_INT, Entry::kind,
                ResourceLocation.STREAM_CODEC, Entry::block,
                Entry::new);
    }

    public static final Type<MultiblockStatusPayload> TYPE = new Type<>(ProjectRebirth.id("multiblock_status"));
    public static final StreamCodec<ByteBuf, MultiblockStatusPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MultiblockStatusPayload::pos,
            ByteBufCodecs.BOOL, MultiblockStatusPayload::planned,
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), MultiblockStatusPayload::problems,
            MultiblockStatusPayload::new);

    /** Set by the client side; on a server nothing arrives here. */
    public static Consumer<MultiblockStatusPayload> clientHandler = payload -> {
    };

    static MultiblockStatusPayload of(BlockPos pos, @Nullable MultiblockSchema.Check check) {
        List<Entry> entries = new ArrayList<>();
        if (check != null) {
            for (MultiblockSchema.Problem problem : check.problems()) {
                if (entries.size() >= MAX) {
                    break;
                }
                entries.add(new Entry(problem.pos(), problem.kind().ordinal(), BuiltInRegistries.BLOCK.getKey(problem.expected().getBlock())));
            }
        }
        return new MultiblockStatusPayload(pos, check != null, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(MultiblockStatusPayload::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> clientHandler.accept(payload));
    }
}
