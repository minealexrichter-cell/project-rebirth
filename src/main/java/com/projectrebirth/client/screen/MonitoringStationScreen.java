package com.projectrebirth.client.screen;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.projectrebirth.client.ContaminationMapView;
import com.projectrebirth.client.GuiAtlas;
import com.projectrebirth.client.MonitoringClient;
import com.projectrebirth.monitoring.MonitoringPayload;
import com.projectrebirth.monitoring.MonitoringStationBlockEntity;
import com.projectrebirth.monitoring.MonitoringStationMenu;
import com.projectrebirth.technology.energy.EnergyUtil;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import static com.projectrebirth.client.screen.GuiShapes.panel;
import static com.projectrebirth.client.screen.GuiShapes.playerInventory;
import static com.projectrebirth.client.screen.GuiShapes.slot;

/** Monitoring station window: the map of the chunks around, the legend, energy, printing, and the graph of the last days. */
public class MonitoringStationScreen extends AbstractContainerScreen<MonitoringStationMenu> {
    private static final int MAP_X = 8;
    private static final int MAP_Y = 16;
    private static final int MAP_SIZE = 102;
    private static final int RIGHT = 118;
    private static final int ENERGY_Y = 46;
    private static final int GRAPH_Y = 90;
    private static final int HEADER = 0x404040;
    private static final int DIM = 0x707070;

    @Nullable
    private Component hover;

    public MonitoringStationScreen(MonitoringStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = MonitoringStationMenu.WIDTH;
        imageHeight = MonitoringStationMenu.INVENTORY_Y + 82;
        inventoryLabelX = MonitoringStationMenu.INVENTORY_X;
        inventoryLabelY = MonitoringStationMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("screen.projectrebirth.monitoring_station.print"),
                        button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MonitoringStationBlockEntity.BUTTON_PRINT))
                .bounds(leftPos + MonitoringStationMenu.PAPER_X + 19, topPos + MonitoringStationMenu.SLOT_Y - 1,
                        MonitoringStationMenu.MAP_X - MonitoringStationMenu.PAPER_X - 21, 18)
                .tooltip(Tooltip.create(Component.translatable("screen.projectrebirth.monitoring_station.print_tip")))
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (hover != null) {
            graphics.renderTooltip(font, hover, mouseX, mouseY);
        } else if (isHovering(RIGHT, ENERGY_Y, imageWidth - RIGHT - 8, 6, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("screen.projectrebirth.energy", EnergyUtil.format(menu.energy()),
                    EnergyUtil.format(menu.energyCapacity())), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        panel(graphics, x, y, imageWidth, imageHeight);
        slot(graphics, x + MonitoringStationMenu.PAPER_X, y + MonitoringStationMenu.SLOT_Y);
        slot(graphics, x + MonitoringStationMenu.MAP_X, y + MonitoringStationMenu.SLOT_Y);
        playerInventory(graphics, x + MonitoringStationMenu.INVENTORY_X, y + MonitoringStationMenu.INVENTORY_Y);
        GuiAtlas.energyHorizontal(graphics, x + RIGHT, y + ENERGY_Y, imageWidth - RIGHT - 8, 6, menu.energy(), menu.energyCapacity());
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        MonitoringPayload scan = MonitoringClient.of(menu.pos());
        hover = null;
        boolean working = menu.state() == MonitoringStationBlockEntity.STATE_WORKING && scan != null;
        if (working) {
            int cell = Math.max(3, MAP_SIZE / scan.snapshot().side());
            hover = ContaminationMapView.draw(graphics, scan.snapshot(), MAP_X, MAP_Y, cell, mouseX - leftPos, mouseY - topPos);
        } else {
            graphics.fill(MAP_X - 1, MAP_Y - 1, MAP_X + MAP_SIZE + 1, MAP_Y + MAP_SIZE + 1, 0xFF101316);
            String key = switch (menu.state()) {
                case MonitoringStationBlockEntity.STATE_NO_ENERGY -> "no_energy";
                case MonitoringStationBlockEntity.STATE_OFF -> "off";
                default -> "scanning";
            };
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.translatable("screen.projectrebirth.monitoring_station." + key), MAP_SIZE - 8);
            for (int i = 0; i < lines.size(); i++) {
                graphics.drawString(font, lines.get(i), MAP_X + MAP_SIZE / 2 - font.width(lines.get(i)) / 2, MAP_Y + 44 + i * 10, 0xFFCCCCCC, false);
            }
        }
        ContaminationMapView.legend(graphics, font, RIGHT, 16, 56, HEADER);
        graphics.drawString(font, Component.translatable("screen.projectrebirth.monitoring_station.graph"), RIGHT, GRAPH_Y - 10, HEADER, false);
        ContaminationMapView.graph(graphics, font, scan == null ? List.of() : scan.history(), RIGHT, GRAPH_Y, imageWidth - RIGHT - 8, 20, DIM);
    }
}
