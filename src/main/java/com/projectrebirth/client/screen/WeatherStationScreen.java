package com.projectrebirth.client.screen;

import com.projectrebirth.client.GuiAtlas;
import com.projectrebirth.environment.Environment;
import com.projectrebirth.environment.EventPhase;
import com.projectrebirth.technology.energy.EnergyUtil;
import com.projectrebirth.weather.WeatherStationBlockEntity;
import com.projectrebirth.weather.WeatherStationMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import static com.projectrebirth.client.screen.GuiShapes.panel;
import static com.projectrebirth.client.screen.GuiShapes.playerInventory;
import static com.projectrebirth.client.screen.GuiShapes.slot;

/** Weather station window: energy, the forecast, the sky now and the last catastrophes, with the slot of the long range module. */
public class WeatherStationScreen extends AbstractContainerScreen<WeatherStationMenu> {
    private static final int HEADER = 0x404040;
    private static final int TEXT = 0x2F4F6F;
    private static final int DIM = 0x707070;
    private static final int HISTORY_LINES = 4;

    public WeatherStationScreen(WeatherStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, GuiShapes.fit(title, WeatherStationMenu.WIDTH - 16));
        imageWidth = WeatherStationMenu.WIDTH;
        imageHeight = WeatherStationMenu.INVENTORY_Y + 82;
        inventoryLabelY = WeatherStationMenu.INVENTORY_Y - 11;
        inventoryLabelX = WeatherStationMenu.INVENTORY_X;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        panel(graphics, x, y, imageWidth, imageHeight);
        slot(graphics, x + WeatherStationMenu.MODULE_X, y + WeatherStationMenu.MODULE_Y);
        playerInventory(graphics, x + WeatherStationMenu.INVENTORY_X, y + WeatherStationMenu.INVENTORY_Y);
        GuiAtlas.energyHorizontal(graphics, x + 8, y + 19, imageWidth - 40, 6, menu.energy(), menu.energyCapacity());
    }

    private static Component event(int index) {
        String id = index >= 0 && index < Environment.EVENTS.size() ? Environment.EVENTS.get(index) : "none";
        return Component.translatable("environment.projectrebirth.event." + id);
    }

    private static Component key(String key, Object... args) {
        return Component.translatable("screen.projectrebirth.weather_station." + key, args);
    }

    private void line(GuiGraphics graphics, Component text, int y, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(text.getString(), imageWidth - 16), 8, y, color, false);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        graphics.drawString(font, Component.translatable("screen.projectrebirth.energy", EnergyUtil.format(menu.energy()),
                EnergyUtil.format(menu.energyCapacity())), 8, 28, DIM, false);
        // the forecast
        line(graphics, key("forecast"), 39, HEADER);
        int state = menu.get(WeatherStationBlockEntity.DATA_STATE);
        Component first, second = null;
        switch (state) {
            case WeatherStationBlockEntity.STATE_FORECAST -> {
                Component name = event(menu.get(WeatherStationBlockEntity.DATA_EVENT));
                int until = menu.get(WeatherStationBlockEntity.DATA_UNTIL);
                if (until >= 0) {
                    first = key("exact", name, String.format("%d:%02d", until / 60, until % 60));
                    second = key("details", menu.get(WeatherStationBlockEntity.DATA_INTENSITY),
                            Math.max(1, (menu.get(WeatherStationBlockEntity.DATA_LENGTH) + 59) / 60));
                } else {
                    int low = menu.get(WeatherStationBlockEntity.DATA_ROUGH_LOW), high = menu.get(WeatherStationBlockEntity.DATA_ROUGH_HIGH);
                    first = low <= 0 ? key("rough_soon", name, high) : key("rough", name, low, high);
                    second = key("module_hint");
                }
            }
            case WeatherStationBlockEntity.STATE_CLEAR -> first = key("clear", menu.get(WeatherStationBlockEntity.DATA_HORIZON));
            case WeatherStationBlockEntity.STATE_ACTIVE -> first = key("going_on");
            case WeatherStationBlockEntity.STATE_NO_SKY -> first = key("no_sky");
            case WeatherStationBlockEntity.STATE_NO_ENERGY -> first = key("no_energy");
            default -> first = key("none_here");
        }
        line(graphics, first, 49, state == WeatherStationBlockEntity.STATE_FORECAST ? TEXT : DIM);
        if (second != null) {
            line(graphics, second, 58, state == WeatherStationBlockEntity.STATE_FORECAST && menu.get(WeatherStationBlockEntity.DATA_UNTIL) >= 0 ? TEXT : DIM);
        }
        // the sky now
        line(graphics, key("now"), 70, HEADER);
        int phase = menu.get(WeatherStationBlockEntity.DATA_NOW_PHASE);
        Component now;
        if (phase < 0 || phase >= EventPhase.values().length) {
            now = Component.literal("—");
        } else {
            Component name = event(menu.get(WeatherStationBlockEntity.DATA_NOW_EVENT));
            int left = menu.get(WeatherStationBlockEntity.DATA_NOW_LEFT);
            now = switch (EventPhase.values()[phase]) {
                case WARNING -> key("now_warning", name);
                case ACTIVE -> key("now_active", name, Math.max(1, (left + 59) / 60));
                case AFTERMATH -> key("now_aftermath", name);
                default -> key("now_calm");
            };
        }
        line(graphics, now, 80, TEXT);
        // the last catastrophes
        line(graphics, key("history"), 92, HEADER);
        int count = Math.min(HISTORY_LINES, menu.get(WeatherStationBlockEntity.DATA_HISTORY));
        if (count <= 0) {
            line(graphics, key("history_none"), 102, DIM);
        }
        for (int i = 0; i < count; i++) {
            int at = WeatherStationBlockEntity.DATA_PAST + 4 * i;
            line(graphics, key("history_entry", menu.get(at + 3), event(menu.get(at)), menu.get(at + 1), menu.get(at + 2)), 102 + 10 * i, TEXT);
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int x, int y) {
        super.renderTooltip(graphics, x, y);
        if (menu.getCarried().isEmpty() && hoveredSlot != null && hoveredSlot.index == WeatherStationBlockEntity.MODULE_SLOT && !hoveredSlot.hasItem()) {
            graphics.renderTooltip(font, key("module_slot"), x, y);
        }
    }
}
