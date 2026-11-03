package com.projectrebirth.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Personal client settings (config/projectrebirth-client.toml), changed in the HUD settings screen or in the file.
 */
public final class RebirthClientConfig {
    public static final ModConfigSpec CLIENT_SPEC;

    public static final ModConfigSpec.BooleanValue HUD_ENABLED;
    public static final ModConfigSpec.DoubleValue HUD_SCALE;
    public static final ModConfigSpec.IntValue HUD_PANEL_X;
    public static final ModConfigSpec.IntValue HUD_PANEL_Y;
    public static final ModConfigSpec.IntValue HUD_BAR_OFFSET_X;
    public static final ModConfigSpec.IntValue HUD_BAR_OFFSET_Y;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("hud");
        HUD_ENABLED = builder.comment("Show the mod's HUD. When off, a body shows the vanilla hearts and an armor shows no status.")
                .define("enabled", true);
        HUD_SCALE = builder.comment("Size of the status panel and the body's integrity bar.")
                .defineInRange("scale", 1.0, 0.5, 3.0);
        HUD_PANEL_X = builder.comment("Left edge of the status panel (body and armor status), in GUI units.")
                .defineInRange("panelX", 4, 0, 4000);
        HUD_PANEL_Y = builder.comment("Top edge of the status panel, in GUI units.")
                .defineInRange("panelY", 4, 0, 4000);
        HUD_BAR_OFFSET_X = builder.comment("Shift of the body's integrity bar from the place of the hearts, in GUI units.")
                .defineInRange("barOffsetX", 0, -4000, 4000);
        HUD_BAR_OFFSET_Y = builder.comment("Vertical shift of the body's integrity bar; positive is down.")
                .defineInRange("barOffsetY", 0, -4000, 4000);
        builder.pop();
        CLIENT_SPEC = builder.build();
    }

    private RebirthClientConfig() {
    }
}
