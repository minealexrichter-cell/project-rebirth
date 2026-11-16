package com.projectrebirth.client;

import org.jetbrains.annotations.Nullable;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.core.ModSounds;
import com.projectrebirth.monitoring.Snapshot;
import com.projectrebirth.scouting.ScoutPayload;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The field analyzer and the sensors in the HUD (1.6.0, block F): a small map of the contamination around under the status panel,
 * the analyzer's numbers above it, and the analyzer's clicks near radiation sources. Shows what the server last sent. Client only.
 */
public final class ScoutingClient {
    private static final RandomSource RANDOM = RandomSource.create();
    /** The map is about this wide, at the HUD's scale. */
    private static final int MAP_SIZE = 72;
    private static final float CLICKS_PER_RADIATION = 6.0f;
    private static final float MAX_CLICKS_PER_SECOND = 14.0f;

    @Nullable
    private static ScoutPayload last;
    private static long receivedAt;

    private ScoutingClient() {
    }

    static void register(IEventBus modEventBus) {
        ScoutPayload.clientHandler = payload -> Minecraft.getInstance().execute(() -> {
            last = payload;
            receivedAt = now();
        });
        modEventBus.addListener((RegisterGuiLayersEvent event) ->
                event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, ProjectRebirth.id("scouting_map"), ScoutingClient::render));
        NeoForge.EVENT_BUS.addListener(ScoutingClient::onClientTick);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> last = null);
    }

    private static long now() {
        return Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
    }

    /** The last reading while it holds: a few periods of sending, then it is gone (the tool was put away). */
    @Nullable
    static ScoutPayload current() {
        int seconds = RebirthConfig.SERVER_SPEC.isLoaded() ? RebirthConfig.SCOUTING_UPDATE_SECONDS.get() : 2;
        long age = now() - receivedAt;
        return last != null && age >= 0 && age <= 20L * seconds + 15L ? last : null;
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ScoutPayload reading = current();
        if (reading == null || !reading.analyzer() || minecraft.player == null || minecraft.level == null || minecraft.isPaused()) {
            return;
        }
        float from = RebirthConfig.SERVER_SPEC.isLoaded() ? RebirthConfig.ANALYZER_CLICK_FROM.get().floatValue() : 0.05f;
        if (reading.sourceRadiation() < from) {
            return;
        }
        float perTick = Math.min(MAX_CLICKS_PER_SECOND, reading.sourceRadiation() * CLICKS_PER_RADIATION + 1.0f) / 20.0f;
        int clicks = (int) perTick + (RANDOM.nextFloat() < perTick - (int) perTick ? 1 : 0);
        for (int i = 0; i < clicks; i++) {
            minecraft.level.playLocalSound(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ(), ModSounds.DOSIMETER_CLICK.get(),
                    SoundSource.PLAYERS, 0.5f, 1.4f + 0.2f * RANDOM.nextFloat(), false);
        }
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        ScoutPayload reading = current();
        if (reading == null || minecraft.options.hideGui || !HudLayout.enabled()) {
            return;
        }
        Snapshot snapshot = reading.snapshot();
        int cell = Mth.clamp(MAP_SIZE / snapshot.side(), 4, 10);
        int size = cell * snapshot.side();
        float scale = HudLayout.scale();
        graphics.pose().pushPose();
        graphics.pose().translate(HudLayout.panelX(graphics.guiWidth()),
                HudLayout.panelY(graphics.guiHeight()) + Math.round(HudLayout.PANEL_HEIGHT * scale) + 4, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        int y = 0;
        int width = Math.max(size + 4, 0);
        Component[] lines = new Component[0];
        if (reading.analyzer()) {
            lines = new Component[] {
                    Component.translatable("hud.projectrebirth.analyzer.ground", fmt(snapshot.radiationAt(0, 0)), fmt(snapshot.toxinAt(0, 0))),
                    Component.translatable("hud.projectrebirth.analyzer.sources", fmt(reading.sourceRadiation()), fmt(reading.sourceToxin()))};
            for (Component line : lines) {
                width = Math.max(width, minecraft.font.width(line) + 4);
            }
        }
        Component title = Component.translatable(reading.sensor() ? "hud.projectrebirth.sensor.title" : "hud.projectrebirth.analyzer.title");
        width = Math.max(width, minecraft.font.width(title) + 4);
        int height = 11 + lines.length * 10 + size + 4;
        graphics.fill(-2, -2, width, height, 0x90000000);
        graphics.drawString(minecraft.font, title, 0, y, 0xFF8FD3F5);
        y += 11;
        for (Component line : lines) {
            graphics.drawString(minecraft.font, line, 0, y, 0xFFE0E0E0);
            y += 10;
        }
        ContaminationMapView.draw(graphics, snapshot, 1, y + 1, cell, Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2);
        graphics.pose().popPose();
    }

    private static String fmt(float value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
