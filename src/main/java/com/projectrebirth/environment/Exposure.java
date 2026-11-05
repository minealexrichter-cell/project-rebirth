package com.projectrebirth.environment;

import java.util.Arrays;

/** How strong each hazard is at one place at one moment. Protection and what it does to a body come after this. */
public final class Exposure {
    public static final Exposure NONE = new Exposure(new float[Hazard.values().length]);

    private final float[] values;

    private Exposure(float[] values) {
        this.values = values;
    }

    public float get(Hazard hazard) {
        return values[hazard.ordinal()];
    }

    public boolean isNone() {
        for (float value : values) {
            if (value > 0.0f) {
                return false;
            }
        }
        return true;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return Arrays.toString(values);
    }

    public static final class Builder {
        private final float[] values = new float[Hazard.values().length];

        public Builder add(Hazard hazard, float amount) {
            values[hazard.ordinal()] += amount;
            return this;
        }

        public Builder multiply(Hazard hazard, float factor) {
            values[hazard.ordinal()] *= factor;
            return this;
        }

        public Exposure build() {
            return new Exposure(values.clone());
        }
    }
}
