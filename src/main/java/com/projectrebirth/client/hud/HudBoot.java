package com.projectrebirth.client.hud;

import java.util.List;

import com.projectrebirth.config.RebirthClientConfig;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The "systems start" of the visor and the helmet (1.8.0 block C): for a second and a half after entering a body or an
 * armor, lines of checks appear one by one ("CONSCIOUSNESS LINK… OK"), then the HUD elements come up. Off in [ui]
 * bootAnimation. One instance for the body, one for the armor.
 */
public final class HudBoot {
    /** Ticks of the whole start; the elements show from {@link #ELEMENTS_FROM}. */
    private static final int LENGTH = 32;
    private static final int ELEMENTS_FROM = 22;

    private final List<String> lines;
    private int ticks = LENGTH;
    private boolean was;

    /** lines: translation keys of the check lines. */
    public HudBoot(List<String> lines) {
        this.lines = lines;
    }

    /** Called every client tick with whether the player is now in the body (or armor). */
    public void tick(boolean in) {
        if (in && !was) {
            ticks = RebirthClientConfig.UI_BOOT_ANIMATION.get() ? 0 : LENGTH;
        }
        was = in;
        if (ticks < LENGTH) {
            ticks++;
        }
    }

    public boolean running() {
        return ticks < LENGTH;
    }

    /** Whether the HUD elements are up yet. */
    public boolean elementsUp() {
        return ticks >= ELEMENTS_FROM;
    }

    /** How far the elements have come up, 0..1 (for a short fade). */
    public float elementsAlpha() {
        return ticks >= LENGTH ? 1.0f : Math.max(0.0f, (ticks - ELEMENTS_FROM) / (float) (LENGTH - ELEMENTS_FROM));
    }

    /** Draws the check lines in the screen's middle-left, in the given colour. */
    public void draw(GuiGraphics graphics, int color, int dimColor) {
        if (!running()) {
            return;
        }
        int x = graphics.guiWidth() / 2 - 90;
        int y = graphics.guiHeight() / 2 - 40;
        int shown = Math.min(lines.size(), ticks * lines.size() / ELEMENTS_FROM + 1);
        for (int i = 0; i < shown; i++) {
            boolean done = i < shown - 1 || ticks >= ELEMENTS_FROM;
            Component line = Component.literal("> ").append(Component.translatable(lines.get(i)))
                    .append(done ? Component.translatable("hud.projectrebirth.boot.ok") : Component.literal(ticks / 3 % 2 == 0 ? " _" : ""));
            graphics.drawString(HudDraw.font(), line, x, y + i * 10, done ? color : dimColor, true);
        }
    }
}
