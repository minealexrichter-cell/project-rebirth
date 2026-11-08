package com.projectrebirth.client;

import java.util.ArrayList;
import java.util.List;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.BodyGadgets;
import com.projectrebirth.bodies.Jetpack;
import com.projectrebirth.bodies.TransferService;
import com.projectrebirth.bodies.power.BodyHeat;
import com.projectrebirth.bodies.power.BodyPower;
import com.projectrebirth.bodies.power.BodyWear;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * HUD of a player in an artificial body. In place of the hearts: the integrity bar, above it the thin bars of wear, charge and
 * heat (each with its small mark), and the jetpack's thrust; right of the integrity bar the alarms (no charge, overheating,
 * critical wear, interference). In the status panel a line with the numbers and the names of the alarms. Only the vanilla
 * health and food layers are replaced; other mods' layers stay. With the HUD switched off in the settings the hearts come
 * back; the hunger bar stays hidden, since a body has no hunger.
 */
public final class BodyHud {
    private static final int WIDTH = HudLayout.BAR_WIDTH;
    private static final int HEIGHT = HudLayout.BAR_HEIGHT;
    private static final int THIN = HudLayout.BODY_THIN_BAR;
    private static final int THRUST_HEIGHT = 5;
    private static final int THRUST_COLOR = 0xFF7FE8FF;
    private static final int ALARM_STEP = 10;

    private BodyHud() {
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(BodyHud::registerGuiLayers);
        NeoForge.EVENT_BUS.addListener(BodyHud::hideVanillaBars);
    }

    private static boolean inBody(Player player) {
        return player != null && TransferService.isInArtificialBody(player);
    }

    private static void hideVanillaBars(RenderGuiLayerEvent.Pre event) {
        boolean hidden = event.getName().equals(VanillaGuiLayers.FOOD_LEVEL)
                || event.getName().equals(VanillaGuiLayers.PLAYER_HEALTH) && HudLayout.enabled();
        if (hidden && inBody(Minecraft.getInstance().player)) {
            event.setCanceled(true);
        }
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.PLAYER_HEALTH, ProjectRebirth.id("body_integrity"), BodyHud::renderIntegrity);
        event.registerAbove(VanillaGuiLayers.HOTBAR, ProjectRebirth.id("body_panel"), BodyHud::renderPanel);
    }

    /** The alarms of the body now, in the order they are shown. */
    static List<GuiAtlas.Alarm> alarms(Player player) {
        List<GuiAtlas.Alarm> alarms = new ArrayList<>();
        if (BodyPower.enabled() && BodyPower.state(player) == BodyPower.State.EMPTY) {
            alarms.add(GuiAtlas.Alarm.NO_CHARGE);
        }
        BodyHeat.State heat = BodyHeat.state(player);
        if (heat == BodyHeat.State.THROTTLED || heat == BodyHeat.State.OVERHEATED) {
            alarms.add(GuiAtlas.Alarm.OVERHEAT);
        }
        if (BodyWear.enabled() && BodyWear.isCritical(player)) {
            alarms.add(GuiAtlas.Alarm.CRITICAL_WEAR);
        }
        if (Interference.active()) {
            alarms.add(GuiAtlas.Alarm.INTERFERENCE);
        }
        return alarms;
    }

    private static boolean blinkOff(Player player) {
        return player.tickCount / 10 % 2 == 1;
    }

    private static int chargeColor(Player player) {
        return switch (BodyPower.state(player)) {
            case NORMAL -> 0xFFFFD54F;
            case LOW -> 0xFFFF8F00;
            case EMPTY -> blinkOff(player) ? 0xFF7F1D1D : 0xFFE53935;
        };
    }

    private static int heatColor(Player player) {
        return switch (BodyHeat.state(player)) {
            case NORMAL -> 0xFF90A4AE;
            case WARM -> 0xFFFF9800;
            default -> blinkOff(player) ? 0xFF8D1C00 : 0xFFFF3D00;
        };
    }

    private static int wearColor(float wear) {
        int stage = BodyWear.stage(wear);
        return stage >= 4 ? 0xFFE53935 : stage == 3 ? 0xFFFF7043 : stage == 2 ? 0xFFFFB74D : 0xFFA1887F;
    }

    /** Drawn where the hearts would be, and takes their space so the armor icons and air bubbles stay above it. */
    private static void renderIntegrity(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (!HudLayout.enabled() || !inBody(player) || minecraft.options.hideGui || minecraft.gameMode == null || !minecraft.gameMode.canHurtPlayer()) {
            return;
        }
        float scale = HudLayout.scale();
        int bottom = graphics.guiHeight() - minecraft.gui.leftHeight + HEIGHT;
        boolean jetpack = Jetpack.equipped(player);
        boolean wear = BodyWear.enabled();
        boolean charge = BodyPower.enabled();
        boolean heat = BodyHeat.enabled();
        int stack = HEIGHT + 1 + (wear ? THIN + 1 : 0) + (charge ? THIN + 1 : 0) + (heat ? THIN + 1 : 0) + (jetpack ? THRUST_HEIGHT + 1 : 0);
        minecraft.gui.leftHeight += Math.round(stack * scale);
        graphics.pose().pushPose();
        graphics.pose().translate(HudLayout.barX(graphics.guiWidth() / 2 - 91, graphics.guiWidth()),
                HudLayout.barBottom(bottom, graphics.guiHeight()), 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        int x = 0;
        int y = -HEIGHT;

        float max = player.getMaxHealth();
        float fraction = max > 0 ? Mth.clamp(player.getHealth() / max, 0.0f, 1.0f) : 0.0f;
        int color = fraction > 0.5f ? 0xFF4FC3F7 : fraction > 0.25f ? 0xFFFFC107 : 0xFFE53935;
        GuiAtlas.bar(graphics, x, y, WIDTH, HEIGHT, Interference.jitter(fraction), color);

        int above = y;
        if (wear) {
            float worn = BodyWear.wear(player);
            above -= THIN + 1;
            thin(graphics, x, above, Interference.jitter(worn / BodyWear.MAX), wearColor(worn), GuiAtlas.Small.WEAR);
        }
        if (charge) {
            boolean empty = BodyPower.state(player) == BodyPower.State.EMPTY;
            above -= THIN + 1;
            thin(graphics, x, above, empty ? 1.0f : Interference.jitter(BodyPower.fraction(player)), chargeColor(player), GuiAtlas.Small.CHARGE);
        }
        if (heat) {
            above -= THIN + 1;
            thin(graphics, x, above, Interference.jitter(BodyHeat.heat(player) / BodyHeat.MAX), heatColor(player), GuiAtlas.Small.HEAT);
        }
        if (jetpack) {
            GuiAtlas.bar(graphics, x, above - THRUST_HEIGHT - 1, WIDTH, THRUST_HEIGHT, Jetpack.reserve(player), THRUST_COLOR);
        }

        // Right of the integrity bar: the seal, then the alarms.
        int iconX = x + WIDTH + 1;
        if (BodyGadgets.has(player.level().registryAccess(), BodyGadgets.of(player), BodyGadgets.SEALING)) {
            GuiAtlas.sealIcon(graphics, iconX, y);
            iconX += ALARM_STEP;
        }
        for (GuiAtlas.Alarm alarm : alarms(player)) {
            // the alarms that stop the body blink; interference and wear stay lit
            boolean blinks = alarm == GuiAtlas.Alarm.NO_CHARGE || alarm == GuiAtlas.Alarm.OVERHEAT;
            if (!blinks || !blinkOff(player)) {
                GuiAtlas.alarm(graphics, iconX, y, alarm);
            }
            iconX += ALARM_STEP;
        }

        Font font = minecraft.font;
        String text = Mth.ceil(player.getHealth()) + " / " + Mth.ceil(max);
        float absorption = player.getAbsorptionAmount();
        if (absorption > 0) {
            text += " +" + Mth.ceil(absorption);
        }
        text = Interference.scramble(text);
        graphics.drawString(font, text, x + (WIDTH - font.width(text)) / 2, y + 1, 0xFFFFFF, true);
        graphics.pose().popPose();
    }

    /** A thin bar with its small mark on the right. */
    private static void thin(GuiGraphics graphics, int x, int y, float fraction, int color, GuiAtlas.Small mark) {
        GuiAtlas.bar(graphics, x, y, WIDTH, THIN, fraction, color);
        GuiAtlas.small(graphics, x + WIDTH + 2, y, mark);
    }

    /**
     * Under the body line of the status panel: the numbers of charge, heat and wear, and the names of the alarms, so that
     * the bars and marks can be read.
     */
    private static void renderPanel(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (!HudLayout.enabled() || !inBody(player) || minecraft.options.hideGui) {
            return;
        }
        Font font = minecraft.font;
        HudLayout.pushPanel(graphics);
        int x = 0;
        int y = HudLayout.ROW;
        if (BodyPower.enabled()) {
            x = reading(graphics, font, x, y, GuiAtlas.Small.CHARGE, Math.round(BodyPower.fraction(player) * 100.0f) + "%", chargeColor(player));
        }
        if (BodyHeat.enabled()) {
            x = reading(graphics, font, x, y, GuiAtlas.Small.HEAT, Integer.toString(Math.round(BodyHeat.heat(player))), heatColor(player));
        }
        if (BodyWear.enabled()) {
            float worn = BodyWear.wear(player);
            reading(graphics, font, x, y, GuiAtlas.Small.WEAR, Math.round(worn) + "%", wearColor(worn));
        }
        List<GuiAtlas.Alarm> alarms = alarms(player);
        x = 0;
        y += HudLayout.ROW;
        for (GuiAtlas.Alarm alarm : alarms) {
            GuiAtlas.alarm(graphics, x, y - 1, alarm);
            x += 11;
            Component name = Component.translatable("hud.projectrebirth.body.alarm." + alarm.name().toLowerCase(java.util.Locale.ROOT));
            graphics.drawString(font, name, x, y, alarmColor(alarm));
            x += font.width(name) + 6;
        }
        graphics.pose().popPose();
    }

    private static int reading(GuiGraphics graphics, Font font, int x, int y, GuiAtlas.Small mark, String value, int color) {
        GuiAtlas.small(graphics, x, y + 2, mark);
        String text = Interference.scramble(value);
        graphics.drawString(font, text, x + 7, y, color);
        return x + 7 + font.width(text) + 8;
    }

    private static int alarmColor(GuiAtlas.Alarm alarm) {
        return switch (alarm) {
            case NO_CHARGE, CRITICAL_WEAR -> 0xFFFF5555;
            case OVERHEAT -> 0xFFFF7043;
            case INTERFERENCE -> 0xFFCE93D8;
            default -> 0xFFFFFFFF;
        };
    }
}
