package com.projectrebirth.environment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projectrebirth.config.RebirthConfig;

/**
 * What the environment has done to a human body: the radiation dose and the toxic load. They belong to the person, so they
 * wait unchanged while the person is in an artificial body, apart from what the body clears by itself.
 *
 * @param residual 1.9.0: the accumulated dose, the share of every dose that stays in the bones. The body does not clear it;
 *                 it lowers the maximum health until the radiation purge station or antiradin takes it away. Saved worlds of
 *                 1.8 have none.
 */
public record Biology(float dose, float toxin, float residual) {
    public static final Biology CLEAN = new Biology(0.0f, 0.0f, 0.0f);
    public static final Codec<Biology> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("dose").forGetter(Biology::dose),
            Codec.FLOAT.fieldOf("toxin").forGetter(Biology::toxin),
            Codec.FLOAT.optionalFieldOf("residual", 0.0f).forGetter(Biology::residual)
    ).apply(instance, Biology::new));

    /** Without an accumulated dose (tests and the older code paths that set both numbers). */
    public Biology(float dose, float toxin) {
        this(dose, toxin, 0.0f);
    }

    public Biology withDose(float newDose) {
        return new Biology(newDose, toxin, residual);
    }

    public Biology withToxin(float newToxin) {
        return new Biology(dose, newToxin, residual);
    }

    public Biology withResidual(float newResidual) {
        return new Biology(dose, toxin, newResidual);
    }

    public Biology clamped() {
        float maxDose = RebirthConfig.HUMAN_MAX_DOSE.get().floatValue();
        return new Biology(Math.max(0.0f, Math.min(dose, maxDose)),
                Math.max(0.0f, Math.min(toxin, RebirthConfig.HUMAN_MAX_TOXIN.get().floatValue())),
                Math.max(0.0f, Math.min(residual, maxDose)));
    }
}
