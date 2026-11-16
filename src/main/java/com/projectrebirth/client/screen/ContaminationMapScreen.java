package com.projectrebirth.client.screen;

import com.projectrebirth.client.ContaminationMapView;
import com.projectrebirth.monitoring.Snapshot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import static com.projectrebirth.client.screen.GuiShapes.panel;

/** A printed contamination map, opened from the hand (1.6.0, block E): the scan of the day it was printed. */
public class ContaminationMapScreen extends Screen {
    private static final int CELL = 8;
    private final Snapshot snapshot;

    public ContaminationMapScreen(Snapshot snapshot) {
        super(Component.translatable("item.projectrebirth.contamination_map"));
        this.snapshot = snapshot;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int size = snapshot.side() * CELL;
        int width = size + 90, height = size + 40;
        int x = (this.width - width) / 2, y = (this.height - height) / 2;
        panel(graphics, x, y, width, height);
        graphics.drawString(font, Component.translatable("screen.projectrebirth.contamination_map.title", snapshot.day()), x + 8, y + 7, 0x404040, false);
        Component hover = ContaminationMapView.draw(graphics, snapshot, x + 8, y + 20, CELL, mouseX, mouseY);
        ContaminationMapView.legend(graphics, font, x + size + 14, y + 20, 0x404040);
        if (hover != null) {
            graphics.drawString(font, hover, x + 8, y + size + 26, 0x2F4F6F, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
