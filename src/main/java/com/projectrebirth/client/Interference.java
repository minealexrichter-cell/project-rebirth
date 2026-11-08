package com.projectrebirth.client;

import com.projectrebirth.environment.Emi;

import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;

/**
 * What electromagnetic interference does to the screens of this player's devices (block E of 1.5.0): numbers jump and bars
 * flicker now and then, but stay readable. Only for the player's own HUD; nothing here decides anything.
 */
public final class Interference {
    private static final RandomSource RANDOM = RandomSource.create();

    private Interference() {
    }

    /** Whether the player's devices are disturbed now. */
    public static boolean active() {
        return Minecraft.getInstance().player != null && Emi.disturbed(Minecraft.getInstance().player);
    }

    /** Some digits replaced by random ones, now and then. */
    public static String scramble(String text) {
        if (!active() || RANDOM.nextFloat() > 0.3f) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            out.append(Character.isDigit(c) && RANDOM.nextFloat() < 0.5f ? (char) ('0' + RANDOM.nextInt(10)) : c);
        }
        return out.toString();
    }

    /** A bar shown a little off: its fraction jitters by up to a fifth. */
    public static float jitter(float fraction) {
        if (!active() || RANDOM.nextFloat() > 0.3f) {
            return fraction;
        }
        return Math.max(0.0f, Math.min(1.0f, fraction + (RANDOM.nextFloat() - 0.5f) * 0.4f));
    }
}
