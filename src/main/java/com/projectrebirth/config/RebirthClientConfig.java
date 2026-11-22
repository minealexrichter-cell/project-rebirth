package com.projectrebirth.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Personal client settings (config/projectrebirth-client.toml), changed in the settings screen or in the file.
 */
public final class RebirthClientConfig {
    public static final ModConfigSpec CLIENT_SPEC;

    public static final ModConfigSpec.BooleanValue HUD_ENABLED;
    public static final ModConfigSpec.BooleanValue RADIATION_GRAIN;
    public static final ModConfigSpec.DoubleValue RADIATION_GRAIN_FROM;
    public static final ModConfigSpec.DoubleValue RADIATION_GRAIN_FULL;
    public static final ModConfigSpec.DoubleValue WEAPON_SCOPE_FOV;
    public static final ModConfigSpec.DoubleValue HUD_SCALE;
    public static final ModConfigSpec.IntValue HUD_PANEL_X;
    public static final ModConfigSpec.IntValue HUD_PANEL_Y;
    public static final ModConfigSpec.IntValue HUD_BAR_OFFSET_X;
    public static final ModConfigSpec.IntValue HUD_BAR_OFFSET_Y;
    public static final ModConfigSpec.BooleanValue TRANSFER_ANIMATION_ENABLED;
    public static final ModConfigSpec.BooleanValue HUD_MACHINE_BARS;
    public static final ModConfigSpec.IntValue HUD_MACHINE_BAR_DISTANCE;
    public static final ModConfigSpec.BooleanValue HUD_CONSOLE_ALARM;
    public static final ModConfigSpec.BooleanValue HUD_TABLET_LINES;
    public static final ModConfigSpec.DoubleValue TRANSFER_ANIMATION_SECONDS;
    public static final ModConfigSpec.DoubleValue TRANSFER_ANIMATION_HEIGHT;
    public static final ModConfigSpec.BooleanValue ZONES_VISUALS;
    public static final ModConfigSpec.DoubleValue ZONES_PARTICLES;
    public static final ModConfigSpec.DoubleValue ZONES_TINT;
    public static final ModConfigSpec.BooleanValue UI_SOUNDS;
    public static final ModConfigSpec.BooleanValue UI_HOVER_SOUND;
    public static final ModConfigSpec.BooleanValue UI_ANIMATIONS;
    public static final ModConfigSpec.BooleanValue UI_REDUCED_MOTION;
    public static final ModConfigSpec.BooleanValue UI_VISOR_FRAME;
    public static final ModConfigSpec.BooleanValue UI_HELMET_FRAME;
    public static final ModConfigSpec.BooleanValue UI_BOOT_ANIMATION;
    public static final ModConfigSpec.BooleanValue UI_DAMAGE_EFFECTS;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> HUD_ELEMENTS;
    public static final ModConfigSpec.BooleanValue UI_CONSOLE_TUTORIAL;

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
        // 1.7.0, block I: the player's machines
        HUD_MACHINE_BARS = builder.comment("Show the two integrity bars over your own machines nearby.").define("machineBars", true);
        HUD_MACHINE_BAR_DISTANCE = builder.comment("Blocks within which the bars of a machine show.").defineInRange("machineBarDistance", 16, 2, 64);
        HUD_CONSOLE_ALARM = builder.comment("Show the mark of a console alarm going on (your consoles and your faction's).").define("consoleAlarm", true);
        HUD_TABLET_LINES = builder.comment("Show the lines of the tactical tablet in hand: its mode, console and marks.").define("tabletLines", true);
        builder.pop();
        builder.push("weapons");
        WEAPON_SCOPE_FOV = builder.comment("The field of view is multiplied by this while aiming through a scope; less is closer.")
                .defineInRange("scopeFovMultiplier", 0.3, 0.1, 1.0);
        builder.pop();
        builder.push("transfer_animation");
        TRANSFER_ANIMATION_ENABLED = builder.comment("Play the camera animation when consciousness moves to another body.")
                .define("enabled", true);
        TRANSFER_ANIMATION_SECONDS = builder.comment("Length of the animation; waiting for the server and for chunks adds to it.")
                .defineInRange("seconds", 3.0, 1.0, 10.0);
        TRANSFER_ANIMATION_HEIGHT = builder.comment("How many blocks the camera rises.")
                .defineInRange("height", 40.0, 10.0, 120.0);
        builder.pop();
        builder.comment("Contaminated zones (1.6.0): how a spot of residual contamination looks around you.").push("zones");
        ZONES_VISUALS = builder.comment("Rare particles and a light tint of the air inside a contaminated spot.").define("visuals", true);
        ZONES_PARTICLES = builder.comment("Particles per second of a spot at full strength.").defineInRange("particlesPerSecond", 6.0, 0.0, 200.0);
        ZONES_TINT = builder.comment("Strength of the tint of the air inside a spot, 0 to 1.").defineInRange("tint", 0.2, 0.0, 1.0);
        builder.pop();
        builder.comment("Radiation grain (1.6.0): the film grain and brownish filter of a radiation burst also come whenever radiation reaches you",
                "(a contaminated spot, a source, uranium you carry), growing with the level.").push("radiation_grain");
        RADIATION_GRAIN = builder.comment("Show the grain from the radiation reaching you (a burst shows its own grain either way).").define("enabled", true);
        RADIATION_GRAIN_FROM = builder.comment("Radiation at which the grain starts (the HUD calls radiation dangerous above 1.5).")
                .defineInRange("from", 1.5, 0.0, 10000.0);
        RADIATION_GRAIN_FULL = builder.comment("Radiation at which the grain is as strong as in the strongest burst.")
                .defineInRange("full", 12.0, 0.1, 10000.0);
        builder.pop();
        builder.comment("Interface (1.8.0): the windows, HUD and tooltips in the mod's own style.").push("ui");
        UI_SOUNDS = builder.comment("Short mechanical sounds of the interface: clicks, switches, errors, start and end of work, alarms.")
                .define("sounds", true);
        UI_HOVER_SOUND = builder.comment("A very quiet tick when the mouse comes onto a button.").define("hoverSound", false);
        UI_ANIMATIONS = builder.comment("Smooth bars and the small animations of windows (the picture of a station's work).")
                .define("animations", true);
        UI_REDUCED_MOTION = builder.comment("Less motion: no blinking of alarms and no shaking.").define("reducedMotion", false);
        // 1.8.0, block C: the body's visor and the armor's helmet
        UI_VISOR_FRAME = builder.comment("The thin frame of the body's visor around the screen (rough for robots, almost none for the premium body).")
                .define("visorFrame", true);
        UI_HELMET_FRAME = builder.comment("The thin frame of the power armor's helmet at the edges of the screen.").define("helmetFrame", true);
        UI_BOOT_ANIMATION = builder.comment("The 'systems start' lines when you enter a body or an armor.").define("bootAnimation", true);
        UI_DAMAGE_EFFECTS = builder.comment("Static and cracks of the visor at low integrity, and a short glitch on a hit.").define("damageEffects", true);
        HUD_ELEMENTS = builder.comment("Places of the HUD elements set in the HUD editor (key H): \"element dx dy scale visible\" each.",
                        "The status panel and the body's visor also use panelX/panelY and barOffsetX/barOffsetY of [hud].")
                .defineListAllowEmpty("hudElements", java.util.List.of(), () -> "", entry -> entry instanceof String);
        // 1.8.0, block E
        UI_CONSOLE_TUTORIAL = builder.comment("Show the short lesson over the console's window when it is opened (it turns itself off once seen; '?' shows it again).")
                .define("consoleTutorial", true);
        builder.pop();
        CLIENT_SPEC = builder.build();
    }

    private RebirthClientConfig() {
    }
}
