package com.projectrebirth.environment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projectrebirth.config.RebirthConfig;

/**
 * What the environment has done to a human body: the radiation dose and the toxic load. They belong to the person, so they
 * wait unchanged while the person is in an artificial body, apart from what the body clears by itself.
 */
public record Biology(float dose, float toxin) {
    public static final Biology CLEAN = new Biology(0.0f, 0.0f);
    public static final Codec<Biology> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("dose").forGetter(Biology::dose),
            Codec.FLOAT.fieldOf("toxin").forGetter(Biology::toxin)
    ).apply(instance, Biology::new));

    public Biology clamped() {
        return new Biology(Math.max(0.0f, Math.min(dose, RebirthConfig.HUMAN_MAX_DOSE.get().floatValue())),
                Math.max(0.0f, Math.min(toxin, RebirthConfig.HUMAN_MAX_TOXIN.get().floatValue())));
    }
}
