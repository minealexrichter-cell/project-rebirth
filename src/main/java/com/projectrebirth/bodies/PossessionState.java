package com.projectrebirth.bodies;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Which body a player's consciousness currently controls. Empty means the player's own human body.
 * Stored as a player data attachment, saved with the player and synced to clients for rendering.
 */
public record PossessionState(Optional<BodyClass> body) {
    public static final PossessionState HUMAN = new PossessionState(Optional.empty());

    public static final Codec<PossessionState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BodyClass.CODEC.optionalFieldOf("body").forGetter(PossessionState::body)
    ).apply(instance, PossessionState::new));

    public static final StreamCodec<ByteBuf, PossessionState> STREAM_CODEC =
            ByteBufCodecs.optional(BodyClass.STREAM_CODEC).map(PossessionState::new, PossessionState::body);

    public static PossessionState of(BodyClass bodyClass) {
        return new PossessionState(Optional.of(bodyClass));
    }

    public boolean isHuman() {
        return body.isEmpty();
    }
}
