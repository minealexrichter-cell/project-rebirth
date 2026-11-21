package com.projectrebirth.client.screen;

import com.projectrebirth.client.ContaminationMapView;
import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiMap;
import com.projectrebirth.client.ui.UiPanels;
import com.projectrebirth.client.ui.UiText;
import com.projectrebirth.client.ui.UiTooltip;
import com.projectrebirth.monitoring.Snapshot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A printed contamination map, opened from the hand (1.6.0 block E; 1.8.0 block D in the style "Industrial terminal"):
 * the scan of the day it was printed on the shared map widget (zoom with the wheel, drag) and the legend.
 */
public class ContaminationMapScreen extends Screen {
    private static final int W = 320;
    private static final int H = 200;
    private static final int MAP_W = 220;
    private static final int LAYERS = SnapshotMapSource.RADIATION | SnapshotMapSource.TOXIN | SnapshotMapSource.MARKS;

    private final Snapshot snapshot;
    private final SnapshotMapSource source = new SnapshotMapSource();
    private UiMap map;
    private int left;
    private int top;

    public ContaminationMapScreen(Snapshot snapshot) {
        super(Component.translatable("screen.projectrebirth.contamination_map.title", snapshot.day()));
        this.snapshot = snapshot;
        source.set(snapshot);
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        if (map != null) {
            map.close();
        }
        map = addRenderableWidget(new UiMap(left + 7, top + 25, MAP_W, H - 32, source, () -> LAYERS, () -> source.marks(LAYERS)));
        map.centreOn(snapshot.centerX() * 16 + 8, snapshot.centerZ() * 16 + 8);
    }

    @Override
    public void removed() {
        if (map != null) {
            map.close();
        }
        super.removed();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        UiPanels.window(graphics, left, top, W, H);
        UiPanels.title(graphics, left, top, W, title, null, left + W - 8);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int rx = left + MAP_W + 14;
        int rw = W - 7 - (MAP_W + 14);
        UiPanels.label(graphics, Component.translatable("screen.projectrebirth.monitoring_station.label.legend"), rx, top + 26, rw);
        ContaminationMapView.legend(graphics, font, rx, top + 37, UiColors.SCREEN_TEXT);
        UiText.wrapped(graphics, Component.translatable("screen.projectrebirth.monitoring_station.map_hint"), rx, top + H - 34, rw, 26, UiColors.TEXT_DIM);
        Component hovered = map == null ? null : map.hovered(mouseX, mouseY);
        if (hovered != null) {
            UiTooltip.of(hovered).render(graphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
