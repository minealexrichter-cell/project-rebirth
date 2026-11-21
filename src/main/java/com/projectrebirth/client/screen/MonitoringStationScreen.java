package com.projectrebirth.client.screen;

import java.util.List;

import com.projectrebirth.client.ContaminationMapView;
import com.projectrebirth.client.MonitoringClient;
import com.projectrebirth.client.ui.UiButton;
import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiEnergy;
import com.projectrebirth.client.ui.UiMachineScreen;
import com.projectrebirth.client.ui.UiMap;
import com.projectrebirth.client.ui.UiPanels;
import com.projectrebirth.client.ui.UiSlots;
import com.projectrebirth.client.ui.UiSprites;
import com.projectrebirth.client.ui.UiText;
import com.projectrebirth.client.ui.UiTooltip;
import com.projectrebirth.monitoring.MonitoringPayload;
import com.projectrebirth.monitoring.MonitoringStationBlockEntity;
import com.projectrebirth.monitoring.MonitoringStationMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Environmental monitoring station in the style "Industrial terminal" (1.8.0 block D): the scan on the shared map widget
 * (drawn once into a texture, zoom with the wheel, drag, layers of radiation, toxins and marks, the values of a chunk under
 * the mouse), the legend, the peaks by day, the print row (paper, button, printed map), the state, energy.
 */
public class MonitoringStationScreen extends UiMachineScreen<MonitoringStationMenu> {
    private static final int W = MonitoringStationMenu.WIDTH;
    private static final int H = 250;
    private static final int RIGHT_X = 213;
    private static int layers = SnapshotMapSource.RADIATION | SnapshotMapSource.TOXIN | SnapshotMapSource.MARKS;

    private final SnapshotMapSource source = new SnapshotMapSource();
    private final UiEnergy energy = new UiEnergy();
    private UiMap map;

    public MonitoringStationScreen(MonitoringStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H, "monitoring_station");
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.projectrebirth.monitoring_station." + key, args);
    }

    @Override
    protected void init() {
        super.init();
        if (map != null) {
            map.close();
        }
        map = addRenderableWidget(new UiMap(leftPos + 7, topPos + 25, 200, 110, source, () -> layers, () -> source.marks(layers)));
        if (source.snapshot() != null) {
            map.centreOn(source.snapshot().centerX() * 16 + 8, source.snapshot().centerZ() * 16 + 8);
        }
        int chipY = topPos + 139;
        addRenderableWidget(new UiButton(leftPos + 7, chipY, 64, 11, Component.translatable("screen.projectrebirth.contamination.legend.radiation"),
                null, 0, UiButton.Look.CHIP, () -> layers ^= SnapshotMapSource.RADIATION).selected(() -> (layers & SnapshotMapSource.RADIATION) != 0));
        addRenderableWidget(new UiButton(leftPos + 75, chipY, 64, 11, Component.translatable("screen.projectrebirth.contamination.legend.toxin"),
                null, 0, UiButton.Look.CHIP, () -> layers ^= SnapshotMapSource.TOXIN).selected(() -> (layers & SnapshotMapSource.TOXIN) != 0));
        addRenderableWidget(new UiButton(leftPos + 143, chipY, 64, 11, tr("layer.marks"),
                null, 0, UiButton.Look.CHIP, () -> layers ^= SnapshotMapSource.MARKS).selected(() -> (layers & SnapshotMapSource.MARKS) != 0));
        addRenderableWidget(new UiButton(leftPos + MonitoringStationMenu.PAPER_X + 21, topPos + MonitoringStationMenu.SLOT_Y - 1,
                MonitoringStationMenu.MAP_X - MonitoringStationMenu.PAPER_X - 25, 18, tr("print"), null, 0, UiButton.Look.METAL,
                () -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MonitoringStationBlockEntity.BUTTON_PRINT)));
    }

    @Override
    public void removed() {
        if (map != null) {
            map.close();
        }
        super.removed();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        energy.tick(menu.energy(), 0);
        MonitoringPayload scan = MonitoringClient.of(menu.pos());
        boolean first = source.snapshot() == null;
        if (source.set(scan == null ? null : scan.snapshot()) && map != null) {
            map.invalidate();
            if (first && source.snapshot() != null) {
                map.centreOn(source.snapshot().centerX() * 16 + 8, source.snapshot().centerZ() * 16 + 8);
            }
        }
    }

    @Override
    protected void drawBody(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int state = menu.state();
        MonitoringPayload scan = MonitoringClient.of(menu.pos());
        // the right column: legend, graph, print row, state
        int rx = x + RIGHT_X;
        int rw = W - 7 - RIGHT_X;
        UiPanels.label(graphics, tr("label.legend"), rx, y + 26, rw);
        ContaminationMapView.legend(graphics, font, rx, y + 37, 70, UiColors.SCREEN_TEXT);
        UiPanels.label(graphics, tr("graph"), rx, y + 68, rw);
        UiPanels.screen(graphics, rx, y + 78, rw, 34);
        ContaminationMapView.graph(graphics, font, scan == null ? List.of() : scan.history(), rx + 4, y + 82, rw - 8, 20, UiColors.TEXT_SECONDARY);
        UiSlots.frame(graphics, UiSlots.Kind.INPUT, x + MonitoringStationMenu.PAPER_X, y + MonitoringStationMenu.SLOT_Y);
        UiSlots.frame(graphics, UiSlots.Kind.INPUT, x + MonitoringStationMenu.MAP_X, y + MonitoringStationMenu.SLOT_Y);
        UiSprites.Tone tone = state == MonitoringStationBlockEntity.STATE_WORKING ? UiSprites.Tone.OK
                : state == MonitoringStationBlockEntity.STATE_NO_ENERGY ? UiSprites.Tone.WARNING : UiSprites.Tone.INFO;
        String key = switch (state) {
            case MonitoringStationBlockEntity.STATE_WORKING -> scan == null ? "scanning" : "working";
            case MonitoringStationBlockEntity.STATE_NO_ENERGY -> "no_energy";
            default -> "off";
        };
        plate(graphics, tone, tr(key), rx, y + 139, rw);
        if (scan == null || state != MonitoringStationBlockEntity.STATE_WORKING) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0f, 0.0f, 50.0f);
            UiText.centred(graphics, tr(key), x + 107, y + 76, 180, UiColors.AMBER_DIM);
            graphics.pose().popPose();
        }
        if (map != null) {
            Component hovered = map.hovered(mouseX, mouseY);
            if (hovered != null) {
                tooltip(mouseX, mouseY, 1, 1, () -> UiTooltip.of(hovered).text(tr("map_hint"), UiColors.TEXT_DIM));
            }
        }
        // inventory and help with the energy
        inventory(graphics, x + MonitoringStationMenu.INVENTORY_X, y + MonitoringStationMenu.INVENTORY_Y);
        int ix = x + 176;
        int iy = y + MonitoringStationMenu.INVENTORY_Y - 11;
        int iw = W - 7 - 176;
        UiPanels.screen(graphics, ix, iy, iw, H - 7 - (MonitoringStationMenu.INVENTORY_Y - 11));
        energy.draw(this, graphics, ix + 5, iy + 5, iw - 10, 26, menu.energy(), menu.energyCapacity());
        UiText.wrapped(graphics, tr("help"), ix + 5, iy + 36, iw - 10, 44, UiColors.TEXT_SECONDARY);
    }

    @Override
    protected void drawOver(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        if (menu.getSlot(0).getItem().isEmpty()) {
            UiSlots.ghost(graphics, new ItemStack(Items.PAPER), x + MonitoringStationMenu.PAPER_X, y + MonitoringStationMenu.SLOT_Y);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
