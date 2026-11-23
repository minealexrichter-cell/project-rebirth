package com.projectrebirth.client;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.projectrebirth.monitoring.DayPeak;
import com.projectrebirth.monitoring.Snapshot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * Draws a contamination snapshot as a map of chunks (1.6.0, block E): radiation yellow, toxins green, the stronger the brighter;
 * the station, the shelters and the players marked; and the graph of the peaks by day. Shared by the monitoring station's window
 * and the printed map. Client only.
 */
public final class ContaminationMapView {
    public static final int RADIATION = 0xFFE6D23A;
    public static final int TOXIN = 0xFF5FBF3A;
    public static final int STATION = 0xFFFFFFFF;
    public static final int SHELTER = 0xFF37C6E8;
    public static final int PLAYER = 0xFFFF5555;
    public static final int YOU = 0xFFFFAA00;
    public static final int SOURCE = 0xFFB04CFF;
    public static final int RUIN = 0xFFB8875A;
    /** 1.9.0, block F: a node of the machine mind. */
    public static final int MIND = 0xFFFF2A1F;

    public static Component mindLabel(Snapshot.Marker marker) {
        int stage = marker.name().matches("[1-5]") ? Integer.parseInt(marker.name()) : 1;
        return Component.translatable("screen.projectrebirth.contamination.legend.mind", com.projectrebirth.machinemind.MindComputerBlockEntity.roman(stage),
                com.projectrebirth.machinemind.MindComputerBlockEntity.stageName(stage));
    }
    private static final int GROUND = 0xFF20262B;
    private static final int GAP = 0xFF101316;
    /** At this much the colour is full. */
    private static final float FULL = 6.0f;

    private ContaminationMapView() {
    }

    public static int cellColor(float radiation, float toxin) {
        float most = Math.max(radiation, toxin);
        if (most <= 0.0f) {
            return GROUND;
        }
        float share = radiation / (radiation + toxin);
        int mixed = FastColor.ARGB32.lerp(share, TOXIN, RADIATION);
        return FastColor.ARGB32.lerp(0.2f + 0.8f * Mth.clamp(most / FULL, 0.0f, 1.0f), GROUND, mixed);
    }

    /**
     * Draws the map with its top left corner there, each chunk this many pixels.
     *
     * @return what is under the mouse, or null
     */
    @Nullable
    public static Component draw(GuiGraphics graphics, Snapshot snapshot, int x, int y, int cell, int mouseX, int mouseY) {
        int side = snapshot.side(), r = snapshot.radius();
        graphics.fill(x - 1, y - 1, x + side * cell + 1, y + side * cell + 1, GAP);
        Component hover = null;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                int px = x + (dx + r) * cell, py = y + (dz + r) * cell;
                float rad = snapshot.radiationAt(dx, dz), tox = snapshot.toxinAt(dx, dz);
                graphics.fill(px, py, px + cell - 1, py + cell - 1, cellColor(rad, tox));
                if (mouseX >= px && mouseX < px + cell && mouseY >= py && mouseY < py + cell) {
                    hover = Component.translatable("screen.projectrebirth.contamination.cell", (snapshot.centerX() + dx) * 16 + 8,
                            (snapshot.centerZ() + dz) * 16 + 8, String.format(java.util.Locale.ROOT, "%.1f", rad), String.format(java.util.Locale.ROOT, "%.1f", tox));
                }
            }
        }
        String self = Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getGameProfile().getName();
        int originX = (snapshot.centerX() - r) * 16, originZ = (snapshot.centerZ() - r) * 16;
        for (Snapshot.Marker marker : snapshot.markers()) {
            int mx = x + Mth.floor((marker.x() - originX) / 16.0f * cell), my = y + Mth.floor((marker.z() - originZ) / 16.0f * cell);
            switch (marker.kind()) {
                case Snapshot.STATION -> {
                    graphics.fill(mx - 2, my - 2, mx + 3, my + 3, 0xFF000000);
                    graphics.fill(mx - 1, my - 1, mx + 2, my + 2, STATION);
                }
                case Snapshot.SOURCE -> {
                    graphics.fill(mx - 1, my - 1, mx + 2, my + 2, 0xFF000000);
                    graphics.fill(mx, my - 1, mx + 1, my + 2, SOURCE);
                    graphics.fill(mx - 1, my, mx + 2, my + 1, SOURCE);
                }
                case Snapshot.STRUCTURE -> {
                    graphics.fill(mx - 2, my - 2, mx + 3, my + 3, 0xFF000000);
                    graphics.fill(mx - 1, my - 1, mx + 2, my + 2, RUIN);
                }
                case Snapshot.MIND -> {
                    graphics.fill(mx - 3, my - 3, mx + 4, my + 4, 0xFF000000);
                    graphics.fill(mx - 2, my - 2, mx + 3, my + 3, MIND);
                    graphics.fill(mx, my, mx + 1, my + 1, 0xFFFFFFFF);
                    if (Math.abs(mouseX - mx) <= 3 && Math.abs(mouseY - my) <= 3) {
                        hover = mindLabel(marker);
                    }
                }
                case Snapshot.SHELTER -> {
                    graphics.fill(mx - 2, my - 2, mx + 2, my + 2, 0xFF000000);
                    graphics.fill(mx - 1, my - 1, mx + 1, my + 1, SHELTER);
                }
                default -> {
                    boolean you = marker.name().equals(self);
                    graphics.fill(mx - 1, my - 1, mx + 2, my + 2, you ? YOU : PLAYER);
                    if (!you && Math.abs(mouseX - mx) <= 2 && Math.abs(mouseY - my) <= 2) {
                        hover = Component.literal(marker.name());
                    }
                }
            }
        }
        return hover;
    }

    /** The legend in one column: a coloured mark and its name per line. */
    public static void legend(GuiGraphics graphics, Font font, int x, int y, int textColor) {
        legend(graphics, font, x, y, 0, textColor);
    }

    /** The legend; with a column width above 0 in two columns, three marks and two. */
    public static void legend(GuiGraphics graphics, Font font, int x, int y, int column, int textColor) {
        int[] colors = {RADIATION, TOXIN, STATION, SHELTER, PLAYER};
        String[] keys = {"radiation", "toxin", "station", "shelter", "player"};
        for (int i = 0; i < colors.length; i++) {
            int lx = column > 0 && i >= 3 ? x + column : x;
            int ly = y + (column > 0 && i >= 3 ? i - 3 : i) * 9;
            graphics.fill(lx, ly + 1, lx + 5, ly + 6, 0xFF000000);
            graphics.fill(lx + 1, ly + 2, lx + 4, ly + 5, colors[i]);
            graphics.drawString(font, Component.translatable("screen.projectrebirth.contamination.legend." + keys[i]), lx + 8, ly, textColor, false);
        }
    }

    /** The graph of the peaks by day: a yellow and a green bar a day, the newest on the right. */
    public static void graph(GuiGraphics graphics, Font font, List<DayPeak> history, int x, int y, int width, int height, int textColor) {
        graphics.fill(x, y + height, x + width, y + height + 1, 0xFF505050);
        if (history.isEmpty()) {
            return;
        }
        float top = FULL;
        for (DayPeak peak : history) {
            top = Math.max(top, Math.max(peak.radiation(), peak.toxin()));
        }
        int slot = Math.min(22, width / history.size());
        for (int i = 0; i < history.size(); i++) {
            DayPeak peak = history.get(i);
            int sx = x + width - (history.size() - i) * slot;
            int bar = Math.max(2, (slot - 4) / 2);
            int rh = Mth.ceil(height * peak.radiation() / top), th = Mth.ceil(height * peak.toxin() / top);
            graphics.fill(sx + 1, y + height - rh, sx + 1 + bar, y + height, RADIATION);
            graphics.fill(sx + 1 + bar, y + height - th, sx + 1 + 2 * bar, y + height, TOXIN);
            if (i == 0 || i == history.size() - 1) {
                String day = String.valueOf(peak.day());
                graphics.drawString(font, day, sx + slot / 2 - font.width(day) / 2, y + height + 2, textColor, false);
            }
        }
    }
}
