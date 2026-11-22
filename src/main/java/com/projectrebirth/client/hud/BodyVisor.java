package com.projectrebirth.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyGadgets;
import com.projectrebirth.bodies.Jetpack;
import com.projectrebirth.bodies.TransferService;
import com.projectrebirth.bodies.power.BodyHeat;
import com.projectrebirth.bodies.power.BodyPower;
import com.projectrebirth.bodies.power.BodyWear;
import com.projectrebirth.client.Interference;
import com.projectrebirth.client.ui.UiBars;
import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiSounds;
import com.projectrebirth.client.ui.UiText;
import com.projectrebirth.config.RebirthClientConfig;
import com.projectrebirth.upgrades.InstalledUpgrades;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The visor of an artificial body (1.8.0 block C): what the player sees while in a body, drawn by the body's class.
 * <ul>
 * <li>Robots (T1–T2): a rough monochrome green display with text bars "[####....]", scan lines and, at the edges, a dark
 * frame; T1 is darker and noisier than T2.</li>
 * <li>Synthetic bodies T3–T4: the style "Industrial terminal": a translucent panel with bars and amber corner marks.</li>
 * <li>Premium (T5): an almost invisible interface: thin white lines, no frame, corner ticks only.</li>
 * </ul>
 * Warnings in words go in the middle ("Low charge — 3 min"); at low integrity static and cracks creep in from the edges,
 * and a hit makes a short glitch ([ui] damageEffects; no glitch with "less motion"). Entering a body starts the systems
 * ([ui] bootAnimation). The readings replace the old bars over the hearts; the hunger bar stays hidden.
 */
public final class BodyVisor {
    private enum Style { ROBOT, TERMINAL, CLEAN }

    private static final RandomSource RANDOM = RandomSource.create();
    private static final HudBoot BOOT = new HudBoot(List.of("hud.projectrebirth.boot.body.link", "hud.projectrebirth.boot.body.motors",
            "hud.projectrebirth.boot.body.power", "hud.projectrebirth.boot.body.sensors"));
    /** Charge (FE) of the last 30 seconds, one a second, to tell how long it lasts. */
    private static final float[] CHARGE = new float[30];
    private static int seconds;
    private static int ticks;
    private static int glitch;
    private static int lastHurt;

    private BodyVisor() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(BodyVisor::registerLayers);
        NeoForge.EVENT_BUS.addListener(BodyVisor::tick);
    }

    private static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, ProjectRebirth.id("body_visor_frame"), BodyVisor::renderFrame);
        event.registerAbove(VanillaGuiLayers.PLAYER_HEALTH, ProjectRebirth.id("body_visor"), BodyVisor::renderReadings);
    }

    static boolean inBody(Player player) {
        return player != null && TransferService.isInArtificialBody(player);
    }

    private static BodyClass bodyClass(Player player) {
        return TransferService.state(player).body().orElse(BodyClass.BASIC_ROBOT);
    }

    private static Style style(Player player) {
        return switch (bodyClass(player)) {
            case BASIC_ROBOT, IMPROVED_ROBOT -> Style.ROBOT;
            case ADVANCED_SYNTHETIC, HIGHTECH_SYNTHETIC -> Style.TERMINAL;
            case PREMIUM_SYNTHETIC -> Style.CLEAN;
        };
    }

    private static void tick(ClientTickEvent.Post event) {
        Player player = Minecraft.getInstance().player;
        boolean in = inBody(player);
        // the systems start once the camera of the transfer has come down
        BOOT.tick(in && !com.projectrebirth.client.TransferAnimation.isActive());
        if (!in) {
            seconds = 0;
            return;
        }
        if (player.hurtTime > lastHurt && RebirthClientConfig.UI_DAMAGE_EFFECTS.get() && !UiSounds.reducedMotion()) {
            glitch = 6;
        }
        lastHurt = player.hurtTime;
        if (glitch > 0) {
            glitch--;
        }
        if (++ticks % 20 == 0 && BodyPower.enabled()) {
            CHARGE[seconds % CHARGE.length] = BodyPower.charge(player);
            seconds++;
        }
    }

    /** Minutes until the charge is gone at the use of the last half minute, or -1 if it does not go down. */
    private static int minutesLeft(Player player) {
        int span = Math.min(seconds, CHARGE.length) - 1;
        if (span < 5) {
            return -1;
        }
        float now = CHARGE[(seconds - 1) % CHARGE.length];
        float before = CHARGE[(seconds - 1 - span) % CHARGE.length];
        float perSecond = (before - now) / span;
        if (perSecond <= 0.0f) {
            return -1;
        }
        return Math.max(0, Math.round(now / perSecond / 60.0f));
    }

    // ---------------------------------------------------------------- warnings

    /** The warnings of the body now, most urgent first: a sentence each. */
    static List<Component> warnings(Player player) {
        List<Component> list = new ArrayList<>();
        if (BodyPower.enabled()) {
            BodyPower.State charge = BodyPower.state(player);
            if (charge == BodyPower.State.EMPTY) {
                list.add(tr("warning.no_charge"));
            } else if (charge == BodyPower.State.LOW) {
                int minutes = minutesLeft(player);
                list.add(minutes > 0 ? tr("warning.low_charge_min", minutes) : minutes == 0 ? tr("warning.low_charge_soon") : tr("warning.low_charge"));
            }
        }
        if (BodyHeat.enabled()) {
            BodyHeat.State heat = BodyHeat.state(player);
            if (heat == BodyHeat.State.OVERHEATED) {
                list.add(tr("warning.overheated"));
            } else if (heat == BodyHeat.State.THROTTLED) {
                list.add(tr("warning.throttled"));
            }
        }
        if (BodyWear.enabled()) {
            int stage = BodyWear.stage(player);
            if (stage >= 2) {
                list.add(tr("warning.wear_" + Math.min(4, stage), Math.round(BodyWear.wear(player))));
            }
        }
        if (Interference.active()) {
            list.add(tr("warning.interference"));
        }
        return list;
    }

    private static net.minecraft.network.chat.MutableComponent tr(String key, Object... args) {
        return Component.translatable("hud.projectrebirth.visor." + key, args);
    }

    // ---------------------------------------------------------------- frame and damage

    private static void renderFrame(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (!inBody(player) || minecraft.options.hideGui || !com.projectrebirth.client.HudLayout.enabled()) {
            return;
        }
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        Style style = style(player);
        if (RebirthClientConfig.UI_VISOR_FRAME.get()) {
            switch (style) {
                case ROBOT -> robotFrame(graphics, w, h, bodyClass(player) == BodyClass.BASIC_ROBOT);
                case TERMINAL -> corners(graphics, w, h, 18, UiColors.withAlpha(UiColors.AMBER, 150), 2);
                case CLEAN -> corners(graphics, w, h, 12, 0x59FFFFFF, 1);
            }
        }
        if (RebirthClientConfig.UI_DAMAGE_EFFECTS.get()) {
            float max = player.getMaxHealth();
            float fraction = max > 0 ? player.getHealth() / max : 1.0f;
            float intensity = Mth.clamp((0.35f - fraction) / 0.35f, 0.0f, 1.0f) * (style == Style.CLEAN ? 0.5f : 1.0f);
            if (intensity > 0.0f) {
                cracks(graphics, w, h, intensity, style == Style.ROBOT ? 0xC9FFD2 : 0xFFFFFF);
                if (!UiSounds.reducedMotion()) {
                    staticNoise(graphics, w, h, intensity, style == Style.ROBOT ? HudDraw.ROBOT_GREEN : 0xFFFFFFFF);
                }
            }
            if (glitch > 0) {
                for (int i = 0; i < 3; i++) {
                    int y = RANDOM.nextInt(Math.max(1, h));
                    graphics.fill(0, y, w, y + 2 + RANDOM.nextInt(6), style == Style.ROBOT ? 0x405CFF7A : 0x40FFFFFF);
                }
            }
        }
        BOOT.draw(graphics, style == Style.ROBOT ? HudDraw.ROBOT_GREEN : style == Style.TERMINAL ? UiColors.AMBER : 0xFFFFFFFF,
                style == Style.ROBOT ? HudDraw.ROBOT_DIM : UiColors.AMBER_DIM);
    }

    /** The robots' frame: dark chamfered edges, a thin green line, faint scan lines (T1 darker than T2). */
    private static void robotFrame(GuiGraphics graphics, int w, int h, boolean rough) {
        int dark = rough ? 0xC0040A06 : 0x90040A06;
        int edge = 5;
        graphics.fill(0, 0, w, edge, dark);
        graphics.fill(0, h - edge, w, h, dark);
        graphics.fill(0, edge, edge, h - edge, dark);
        graphics.fill(w - edge, edge, w, h - edge, dark);
        // chamfers in the corners
        for (int i = 0; i < 14; i++) {
            int len = 14 - i;
            graphics.fill(edge, edge + i, edge + len, edge + i + 1, dark);
            graphics.fill(w - edge - len, edge + i, w - edge, edge + i + 1, dark);
            graphics.fill(edge, h - edge - i - 1, edge + len, h - edge - i, dark);
            graphics.fill(w - edge - len, h - edge - i - 1, w - edge, h - edge - i, dark);
        }
        int line = UiColors.withAlpha(HudDraw.ROBOT_GREEN, rough ? 120 : 90);
        graphics.fill(edge + 14, edge, w - edge - 14, edge + 1, line);
        graphics.fill(edge + 14, h - edge - 1, w - edge - 14, h - edge, line);
        graphics.fill(edge, edge + 14, edge + 1, h - edge - 14, line);
        graphics.fill(w - edge - 1, edge + 14, w - edge, h - edge - 14, line);
        int scan = rough ? 0x16000000 : 0x0E000000;
        try (com.projectrebirth.client.ui.UiQuads quads = com.projectrebirth.client.ui.UiQuads.begin(graphics)) {
            for (int y = 0; y < h; y += 3) {
                quads.fill(0, y, w, y + 1, scan);
            }
        }
        if (rough && !UiSounds.reducedMotion()) {
            // a slow brighter band running down, like an old tube
            int band = (int) (HudDraw.time() / 30 % (h + 40)) - 20;
            graphics.fill(0, band, w, band + 6, 0x0C5CFF7A);
        }
    }

    /** Thin L marks in the four corners. */
    static void corners(GuiGraphics graphics, int w, int h, int size, int color, int thick) {
        int m = 6;
        graphics.fill(m, m, m + size, m + thick, color);
        graphics.fill(m, m, m + thick, m + size, color);
        graphics.fill(w - m - size, m, w - m, m + thick, color);
        graphics.fill(w - m - thick, m, w - m, m + size, color);
        graphics.fill(m, h - m - thick, m + size, h - m, color);
        graphics.fill(m, h - m - size, m + thick, h - m, color);
        graphics.fill(w - m - size, h - m - thick, w - m, h - m, color);
        graphics.fill(w - m - thick, h - m - size, w - m, h - m, color);
    }

    /** Cracks of the display from two corners, the stronger the lower the integrity. */
    private static void cracks(GuiGraphics graphics, int w, int h, float intensity, int rgb) {
        int color = UiColors.withAlpha(rgb, Math.round(60 + 140 * intensity));
        int[][] right = {{w - 6, 40}, {w - 30, 58}, {w - 22, 80}, {w - 46, 102}};
        int[][] left = {{8, h - 50}, {34, h - 68}, {30, h - 86}};
        polyline(graphics, right, color);
        polyline(graphics, left, color);
        if (intensity > 0.5f) {
            polyline(graphics, new int[][] {{w - 22, 80}, {w - 8, 92}}, color);
            polyline(graphics, new int[][] {{34, h - 68}, {58, h - 62}}, color);
        }
    }

    private static void polyline(GuiGraphics graphics, int[][] points, int color) {
        try (com.projectrebirth.client.ui.UiQuads quads = com.projectrebirth.client.ui.UiQuads.begin(graphics)) {
            polyline(quads, points, color);
        }
    }

    private static void polyline(com.projectrebirth.client.ui.UiQuads graphics, int[][] points, int color) {
        for (int i = 1; i < points.length; i++) {
            int x0 = points[i - 1][0], y0 = points[i - 1][1], x1 = points[i][0], y1 = points[i][1];
            int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
            for (int s = 0; s <= steps; s++) {
                int x = x0 + (x1 - x0) * s / Math.max(1, steps);
                int y = y0 + (y1 - y0) * s / Math.max(1, steps);
                graphics.fill(x, y, x + 1, y + 1, color);
            }
        }
    }

    /** Short bright specks at the edges of the screen. */
    private static void staticNoise(GuiGraphics graphics, int w, int h, float intensity, int rgb) {
        int count = Math.round(40 * intensity);
        int depth = Math.round(10 + 40 * intensity);
        try (com.projectrebirth.client.ui.UiQuads quads = com.projectrebirth.client.ui.UiQuads.begin(graphics)) {
            specks(quads, w, h, count, depth, rgb);
        }
    }

    private static void specks(com.projectrebirth.client.ui.UiQuads graphics, int w, int h, int count, int depth, int rgb) {
        for (int i = 0; i < count; i++) {
            boolean vertical = RANDOM.nextBoolean();
            int x = vertical ? (RANDOM.nextBoolean() ? RANDOM.nextInt(depth) : w - 1 - RANDOM.nextInt(depth)) : RANDOM.nextInt(w);
            int y = vertical ? RANDOM.nextInt(h) : (RANDOM.nextBoolean() ? RANDOM.nextInt(depth) : h - 1 - RANDOM.nextInt(depth));
            graphics.fill(x, y, x + 1 + RANDOM.nextInt(3), y + 1, UiColors.withAlpha(rgb, 60 + RANDOM.nextInt(120)));
        }
    }

    // ---------------------------------------------------------------- the readings

    private static void renderReadings(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (!inBody(player) || minecraft.options.hideGui || minecraft.gameMode == null || !minecraft.gameMode.canHurtPlayer()) {
            return;
        }
        Style style = style(player);
        HudElement element = HudElement.BODY_VISOR;
        int height = height(player, style);
        if (element.visible() && element.placement().dx() == 0 && element.placement().dy() == 0) {
            // standing where the hearts were, it takes their room, so the armor icons and air bubbles stay above it
            minecraft.gui.leftHeight += Math.round(height * element.scale()) + 1;
        }
        if (BOOT.elementsUp()) {
            if (element.visible()) {
                int[] at = element.position(graphics.guiWidth(), graphics.guiHeight());
                graphics.pose().pushPose();
                graphics.pose().translate(at[0], at[1] + Math.round((element.height - height) * element.scale()), 0.0f);
                graphics.pose().scale(element.scale(), element.scale(), 1.0f);
                switch (style) {
                    case ROBOT -> robot(graphics, player);
                    case TERMINAL -> terminal(graphics, player);
                    case CLEAN -> clean(graphics, player);
                }
                graphics.pose().popPose();
            }
            Warnings.draw(graphics, warnings(player), style == Style.ROBOT ? Warnings.Look.ROBOT : style == Style.TERMINAL ? Warnings.Look.TERMINAL : Warnings.Look.CLEAN);
        }
    }

    /** The height the readings take, at scale 1. */
    private static int height(Player player, Style style) {
        int rows = 1 + (BodyPower.enabled() ? 1 : 0) + (BodyHeat.enabled() ? 1 : 0) + (BodyWear.enabled() ? 1 : 0) + (Jetpack.equipped(player) ? 1 : 0);
        boolean gadgets = !BodyGadgets.of(player).entries().isEmpty();
        return switch (style) {
            case ROBOT -> (rows + 1 + (gadgets ? 1 : 0)) * 9 + 4;
            case TERMINAL -> 14 + rows * 11 + (gadgets ? 10 : 0) + 4;
            case CLEAN -> 30 + (gadgets ? 12 : 0);
        };
    }

    private static float integrity(Player player) {
        float max = player.getMaxHealth();
        return max > 0 ? Mth.clamp(player.getHealth() / max, 0.0f, 1.0f) : 0.0f;
    }

    private static String healthText(Player player) {
        String text = Mth.ceil(player.getHealth()) + "/" + Mth.ceil(player.getMaxHealth());
        if (player.getAbsorptionAmount() > 0) {
            text += " +" + Mth.ceil(player.getAbsorptionAmount());
        }
        return Interference.scramble(text);
    }

    private static String gadgetNames(Player player, boolean upper) {
        StringBuilder text = new StringBuilder();
        for (InstalledUpgrades.Installed entry : BodyGadgets.of(player).entries()) {
            if (!text.isEmpty()) {
                text.append(upper ? " " : " · ");
            }
            String name = entry.stack().getHoverName().getString();
            text.append(upper ? name.toUpperCase(Locale.ROOT) + "[" + Component.translatable("hud.projectrebirth.visor.ok").getString() + "]" : name);
        }
        return text.toString();
    }

    /** T1–T2: lines of green text on a dark plate. */
    private static void robot(GuiGraphics graphics, Player player) {
        Font font = HudDraw.font();
        int width = 160;
        int height = height(player, Style.ROBOT);
        graphics.fill(0, 0, width, height, HudDraw.ROBOT_BACK);
        BodyClass bodyClass = bodyClass(player);
        int y = 2;
        line(graphics, font, Component.literal("T" + (bodyClass.ordinal() + 1) + " · ").append(UiText.upper(Component.translatable(bodyClass.translationKey()))),
                y, HudDraw.ROBOT_DIM, width);
        y += 9;
        line(graphics, font, readout("integrity", integrity(player), healthText(player)), y, integrity(player) <= 0.25f && HudDraw.blinkOn(400)
                ? 0xFFC9FFD2 : HudDraw.ROBOT_GREEN, width);
        y += 9;
        if (BodyPower.enabled()) {
            float charge = BodyPower.state(player) == BodyPower.State.EMPTY ? 0.0f : BodyPower.fraction(player);
            line(graphics, font, readout("charge", Interference.jitter(charge), Interference.scramble(Math.round(charge * 100) + "%")), y, HudDraw.ROBOT_GREEN, width);
            y += 9;
        }
        if (BodyHeat.enabled()) {
            float heat = BodyHeat.heat(player);
            line(graphics, font, readout("heat", Interference.jitter(heat / BodyHeat.MAX), Interference.scramble(String.valueOf(Math.round(heat)))), y, HudDraw.ROBOT_GREEN, width);
            y += 9;
        }
        if (BodyWear.enabled()) {
            float wear = BodyWear.wear(player);
            line(graphics, font, readout("wear", wear / BodyWear.MAX, Interference.scramble(Math.round(wear) + "%")), y, HudDraw.ROBOT_GREEN, width);
            y += 9;
        }
        if (Jetpack.equipped(player)) {
            line(graphics, font, readout("jetpack", Jetpack.reserve(player), Math.round(Jetpack.reserve(player) * 100) + "%"), y, HudDraw.ROBOT_GREEN, width);
            y += 9;
        }
        String gadgets = gadgetNames(player, true);
        if (!gadgets.isEmpty()) {
            line(graphics, font, tr("robot.modules").append(gadgets), y, HudDraw.ROBOT_DIM, width);
        }
    }

    private static Component readout(String key, float fraction, String value) {
        return Component.literal(String.format(Locale.ROOT, "%-6s", tr("robot." + key).getString()) + " " + HudDraw.textBar(fraction, 10) + " " + value);
    }

    private static void line(GuiGraphics graphics, Font font, Component text, int y, int color, int width) {
        UiText.line(graphics, text, 3, y, width - 6, color);
    }

    /** T3–T4: the style "Industrial terminal": a translucent panel, bars with notches and numbers. */
    private static void terminal(GuiGraphics graphics, Player player) {
        int width = 150;
        int height = height(player, Style.TERMINAL);
        HudDraw.panel(graphics, 0, 0, width, height);
        BodyClass bodyClass = bodyClass(player);
        UiText.line(graphics, UiText.upper(Component.translatable(bodyClass.translationKey())), 4, 3, width - 8, UiColors.AMBER);
        int y = 14;
        float integrity = integrity(player);
        meter(graphics, y, tr("short.integrity"), Interference.jitter(integrity), UiColors.integrity(integrity), healthText(player), width);
        y += 11;
        if (BodyPower.enabled()) {
            float charge = BodyPower.state(player) == BodyPower.State.EMPTY ? 0.0f : BodyPower.fraction(player);
            meter(graphics, y, tr("short.charge"), Interference.jitter(charge), BodyPower.state(player) == BodyPower.State.NORMAL ? UiColors.ENERGY : UiColors.ERROR,
                    Interference.scramble(Math.round(charge * 100) + "%"), width);
            y += 11;
        }
        if (BodyHeat.enabled()) {
            float heat = BodyHeat.heat(player);
            meter(graphics, y, tr("short.heat"), Interference.jitter(heat / BodyHeat.MAX), BodyHeat.state(player) == BodyHeat.State.NORMAL ? UiColors.HEAT : UiColors.ERROR,
                    Interference.scramble(String.valueOf(Math.round(heat))), width);
            y += 11;
        }
        if (BodyWear.enabled()) {
            float wear = BodyWear.wear(player);
            meter(graphics, y, tr("short.wear"), wear / BodyWear.MAX, BodyWear.stage(wear) >= 3 ? UiColors.ERROR : UiColors.WEAR, Math.round(wear) + "%", width);
            y += 11;
        }
        if (Jetpack.equipped(player)) {
            meter(graphics, y, tr("short.jetpack"), Jetpack.reserve(player), 0xFF7FE8FF, Math.round(Jetpack.reserve(player) * 100) + "%", width);
            y += 11;
        }
        String gadgets = gadgetNames(player, false);
        if (!gadgets.isEmpty()) {
            UiText.line(graphics, Component.literal(gadgets), 4, y + 1, width - 8, UiColors.TEXT_SECONDARY);
        }
    }

    private static void meter(GuiGraphics graphics, int y, Component name, float fraction, int color, String value, int width) {
        UiText.line(graphics, name, 4, y + 1, 40, UiColors.TEXT_SECONDARY);
        UiBars.horizontal(graphics, 46, y, width - 46 - 44, 9, fraction, color);
        UiText.line(graphics, Component.literal(value), width - 42, y + 1, 39, color);
    }

    /** T5: thin white lines with soft shadows, nothing more. */
    private static void clean(GuiGraphics graphics, Player player) {
        Font font = HudDraw.font();
        int width = 150;
        float integrity = integrity(player);
        graphics.drawString(font, tr("clean.integrity"), 0, 0, HudDraw.CLEAN, true);
        String health = healthText(player);
        graphics.drawString(font, health, width - font.width(health), 0, HudDraw.CLEAN, true);
        HudDraw.thinBar(graphics, 0, 10, width, Interference.jitter(integrity), integrity <= 0.25f ? UiColors.ERROR : 0xFFE9F6FF);
        int columns = (BodyPower.enabled() ? 1 : 0) + (BodyHeat.enabled() ? 1 : 0) + (BodyWear.enabled() ? 1 : 0) + (Jetpack.equipped(player) ? 1 : 0);
        int columnW = columns == 0 ? width : (width - (columns - 1) * 6) / columns;
        int x = 0;
        if (BodyPower.enabled()) {
            float charge = BodyPower.fraction(player);
            x = column(graphics, font, x, columnW, tr("clean.charge", Math.round(charge * 100)), charge, 0xFF7FD3FF);
        }
        if (BodyHeat.enabled()) {
            float heat = BodyHeat.heat(player);
            x = column(graphics, font, x, columnW, tr("clean.heat", Math.round(heat)), heat / BodyHeat.MAX, UiColors.HEAT);
        }
        if (BodyWear.enabled()) {
            float wear = BodyWear.wear(player);
            x = column(graphics, font, x, columnW, tr("clean.wear", Math.round(wear)), wear / BodyWear.MAX, UiColors.WEAR);
        }
        if (Jetpack.equipped(player)) {
            column(graphics, font, x, columnW, tr("clean.jetpack", Math.round(Jetpack.reserve(player) * 100)), Jetpack.reserve(player), 0xFF7FE8FF);
        }
        String gadgets = gadgetNames(player, false);
        if (!gadgets.isEmpty()) {
            UiText.line(graphics, Component.literal(gadgets), 0, 31, width, 0xB0FFFFFF);
        }
    }

    private static int column(GuiGraphics graphics, Font font, int x, int width, Component text, float fraction, int color) {
        // small letters, as in the mock-up: three columns share the width
        UiText.scaled(graphics, text, x, 16, 0.75f, 0xDDFFFFFF);
        HudDraw.thinBar(graphics, x, 25, width, fraction, color);
        return x + width + 6;
    }

    /** For the HUD editor: a sample of the readings in the body's style (T3 look). */
    public static void preview(GuiGraphics graphics, int width, int height) {
        HudDraw.panel(graphics, 0, 0, width, height);
        UiText.line(graphics, tr("preview"), 4, 3, width - 8, UiColors.AMBER);
        UiBars.horizontal(graphics, 4, 16, width - 8, 9, 0.8f, UiColors.OK);
        UiBars.horizontal(graphics, 4, 28, width - 8, 9, 0.6f, UiColors.ENERGY);
        UiBars.horizontal(graphics, 4, 40, width - 8, 9, 0.3f, UiColors.HEAT);
    }
}
