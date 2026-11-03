package com.projectrebirth.bodies;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import com.projectrebirth.ProjectRebirth;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.payload.SyncAttachmentsPayload;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class BodyAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ProjectRebirth.MOD_ID);

    /** Not copied on death: a player always respawns in a human body. */
    public static final Supplier<AttachmentType<PossessionState>> POSSESSION = ATTACHMENTS.register("possession",
            () -> AttachmentType.builder(() -> PossessionState.HUMAN)
                    .serialize(PossessionState.CODEC, state -> !state.isHuman())
                    // Only to connections that understand the sync payload (skips fake players without a client).
                    .sync((holder, to) -> to.connection.hasChannel(SyncAttachmentsPayload.TYPE), PossessionState.STREAM_CODEC)
                    .build());

    /** Game time of the last damage to an artificial body (controlled or standing); self-repair waits after it. */
    public static final Supplier<AttachmentType<Long>> LAST_DAMAGE = ATTACHMENTS.register("last_damage",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG, time -> time != 0L).build());

    private BodyAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
}
