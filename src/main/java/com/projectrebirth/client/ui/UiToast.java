package com.projectrebirth.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;

/**
 * A pop-up notice in the corner of the screen in the style of the mod: the window metal with an amber stripe, an icon of
 * its tone, a title and one line of text; it goes away after five seconds. An alarm also sounds.
 */
public class UiToast implements Toast {
    private static final long SHOW_MS = 5000L;

    private final UiSprites.Tone tone;
    private final Component title;
    private final Component text;

    public UiToast(UiSprites.Tone tone, Component title, Component text) {
        this.tone = tone;
        this.title = title;
        this.text = text;
    }

    /** Shows a notice; an error notice also plays the alarm sound. */
    public static void show(UiSprites.Tone tone, Component title, Component text) {
        Minecraft.getInstance().getToasts().addToast(new UiToast(tone, title, text));
        if (tone == UiSprites.Tone.ERROR) {
            UiSounds.ALARM.play();
        }
    }

    @Override
    public int width() {
        return 180;
    }

    @Override
    public int height() {
        return 32;
    }

    @Override
    public Visibility render(GuiGraphics graphics, ToastComponent toasts, long timeSinceVisible) {
        UiSprites.draw(graphics, UiSprites.TOAST, 0, 0, width(), height());
        if (tone.icon != null) {
            UiSprites.draw(graphics, tone.icon, 9, 7, 7, 7);
        }
        UiText.line(graphics, title, 20, 6, width() - 26, tone == UiSprites.Tone.INFO ? UiColors.AMBER : tone.text);
        UiText.line(graphics, text, 9, 18, width() - 15, UiColors.TEXT);
        return timeSinceVisible >= SHOW_MS * toasts.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }
}
