package com.projectrebirth.client.ui;

import java.util.function.Supplier;

import com.projectrebirth.config.RebirthClientConfig;
import com.projectrebirth.core.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

/** The interface sounds (assets_src/sounds/ui/make_ui_sounds.sh); all can be turned off in the client config [ui]. */
public enum UiSounds {
    CLICK(ModSounds.UI_CLICK::get, 1.0f, 0.8f),
    HOVER(ModSounds.UI_HOVER::get, 1.0f, 0.4f),
    TOGGLE(ModSounds.UI_TOGGLE::get, 1.0f, 0.8f),
    ERROR(ModSounds.UI_ERROR::get, 1.0f, 0.7f),
    WORK_START(ModSounds.UI_WORK_START::get, 1.0f, 0.5f),
    WORK_END(ModSounds.UI_WORK_END::get, 1.0f, 0.5f),
    ALARM(ModSounds.UI_ALARM::get, 1.0f, 0.6f);

    private final Supplier<SoundEvent> sound;
    private final float pitch;
    private final float volume;

    UiSounds(Supplier<SoundEvent> sound, float pitch, float volume) {
        this.sound = sound;
        this.pitch = pitch;
        this.volume = volume;
    }

    public void play() {
        if (!RebirthClientConfig.UI_SOUNDS.get() || this == HOVER && !RebirthClientConfig.UI_HOVER_SOUND.get()) {
            return;
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound.get(), pitch, volume));
    }

    /** Whether the small animations of windows play (client config [ui] animations). */
    public static boolean animations() {
        return RebirthClientConfig.UI_ANIMATIONS.get();
    }

    /** Whether blinking and shaking are off (client config [ui] reducedMotion). */
    public static boolean reducedMotion() {
        return RebirthClientConfig.UI_REDUCED_MOTION.get();
    }
}
