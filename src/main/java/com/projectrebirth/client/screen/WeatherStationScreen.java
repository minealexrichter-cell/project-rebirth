package com.projectrebirth.client.screen;

import com.projectrebirth.client.ui.UiBars;
import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiEnergy;
import com.projectrebirth.client.ui.UiMachineScreen;
import com.projectrebirth.client.ui.UiPanels;
import com.projectrebirth.client.ui.UiSlots;
import com.projectrebirth.client.ui.UiSprites;
import com.projectrebirth.client.ui.UiTable;
import com.projectrebirth.client.ui.UiText;
import com.projectrebirth.client.ui.UiTooltip;
import com.projectrebirth.environment.Environment;
import com.projectrebirth.environment.EventPhase;
import com.projectrebirth.weather.WeatherStationBlockEntity;
import com.projectrebirth.weather.WeatherStationMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Weather station in the style "Industrial terminal" (1.8.0 block D): the forecast screen (the coming catastrophe, the time
 * until it in display digits, its strength and length, what to do), the sky now, the long range module, energy, and the
 * last catastrophes as a table.
 */
public class WeatherStationScreen extends UiMachineScreen<WeatherStationMenu> {
    private static final int W = WeatherStationMenu.WIDTH;
    private static final int H = 243;
    private static final int SCREEN_W = 226;
    private static final int RIGHT_X = 239;

    private final UiEnergy energy = new UiEnergy();

    public WeatherStationScreen(WeatherStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H, "weather_station");
    }

    private static Component key(String key, Object... args) {
        return Component.translatable("screen.projectrebirth.weather_station." + key, args);
    }

    private static String eventId(int index) {
        return index >= 0 && index < Environment.EVENTS.size() ? Environment.EVENTS.get(index) : "none";
    }

    private static Component event(int index) {
        return Component.translatable("environment.projectrebirth.event." + eventId(index));
    }

    private static String clock(int seconds) {
        return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        energy.tick(menu.energy(), 0);
    }

    @Override
    protected void drawBody(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int state = menu.get(WeatherStationBlockEntity.DATA_STATE);
        int sx = x + 7;
        int sy = y + 25;
        UiPanels.screen(graphics, sx, sy, SCREEN_W, 96);
        text(graphics, Component.literal("> ").append(UiText.upper(key("forecast"))), sx + 5, sy + 5, SCREEN_W - 10, UiColors.AMBER);
        UiSprites.Tone tone;
        Component plate;
        switch (state) {
            case WeatherStationBlockEntity.STATE_FORECAST -> {
                int eventIndex = menu.get(WeatherStationBlockEntity.DATA_EVENT);
                text(graphics, UiText.upper(event(eventIndex)), sx + 5, sy + 18, SCREEN_W - 10, UiColors.AMBER_LIGHT);
                int until = menu.get(WeatherStationBlockEntity.DATA_UNTIL);
                if (until >= 0) {
                    UiText.scaled(graphics, Component.literal(clock(until)), sx + 5, sy + 30, 2.0f, UiColors.AMBER);
                    text(graphics, key("until"), sx + 70, sy + 37, SCREEN_W - 75, UiColors.AMBER_DIM);
                    int intensity = menu.get(WeatherStationBlockEntity.DATA_INTENSITY);
                    UiBars.meter(graphics, null, sx + 5, sy + 52, SCREEN_W - 10, intensity / 100.0f, UiColors.WARNING,
                            Component.literal(intensity + "%"));
                    text(graphics, key("lasts", Math.max(1, (menu.get(WeatherStationBlockEntity.DATA_LENGTH) + 59) / 60)), sx + 5, sy + 64,
                            SCREEN_W - 10, UiColors.SCREEN_TEXT);
                } else {
                    int low = menu.get(WeatherStationBlockEntity.DATA_ROUGH_LOW);
                    int high = menu.get(WeatherStationBlockEntity.DATA_ROUGH_HIGH);
                    UiText.scaled(graphics, Component.literal(low <= 0 ? "< " + high : low + "–" + high), sx + 5, sy + 30, 2.0f, UiColors.AMBER);
                    text(graphics, key("minutes"), sx + 90, sy + 37, SCREEN_W - 95, UiColors.AMBER_DIM);
                    text(graphics, key("module_hint"), sx + 5, sy + 52, SCREEN_W - 10, UiColors.TEXT_SECONDARY);
                }
                tone = UiSprites.Tone.WARNING;
                plate = Component.translatable("hud.projectrebirth.catastrophe.advice." + eventId(eventIndex));
            }
            case WeatherStationBlockEntity.STATE_CLEAR -> {
                UiText.wrapped(graphics, key("clear", menu.get(WeatherStationBlockEntity.DATA_HORIZON)), sx + 5, sy + 20, SCREEN_W - 10, 30, UiColors.OK);
                tone = UiSprites.Tone.OK;
                plate = key("todo.clear");
            }
            case WeatherStationBlockEntity.STATE_ACTIVE -> {
                text(graphics, key("going_on"), sx + 5, sy + 20, SCREEN_W - 10, UiColors.ERROR);
                tone = UiSprites.Tone.ERROR;
                plate = key("todo.active");
            }
            case WeatherStationBlockEntity.STATE_NO_SKY -> {
                text(graphics, key("no_sky"), sx + 5, sy + 20, SCREEN_W - 10, UiColors.ERROR);
                tone = UiSprites.Tone.ERROR;
                plate = key("todo.no_sky");
            }
            case WeatherStationBlockEntity.STATE_NO_ENERGY -> {
                text(graphics, key("no_energy"), sx + 5, sy + 20, SCREEN_W - 10, UiColors.WARNING);
                tone = UiSprites.Tone.WARNING;
                plate = Component.translatable("screen.projectrebirth.station.plate.no_energy");
            }
            default -> {
                text(graphics, key("none_here"), sx + 5, sy + 20, SCREEN_W - 10, UiColors.TEXT_SECONDARY);
                tone = UiSprites.Tone.INFO;
                plate = key("none_here");
            }
        }
        // the sky now, at the bottom of the screen
        graphics.fill(sx + 4, sy + 77, sx + SCREEN_W - 4, sy + 78, 0xFF2A332E);
        text(graphics, Component.literal(UiText.upper(key("now")).getString() + ": ").append(now()), sx + 5, sy + 82, SCREEN_W - 10, UiColors.SCREEN_TEXT);

        // module, energy, state
        int rx = x + RIGHT_X;
        int rw = W - 7 - RIGHT_X;
        UiPanels.label(graphics, key("label.module"), rx, y + 26, rw);
        UiSlots.frame(graphics, UiSlots.Kind.UPGRADE, x + WeatherStationMenu.MODULE_X, y + WeatherStationMenu.MODULE_Y);
        text(graphics, key(menu.getSlot(0).hasItem() ? "module_in" : "module_out"), x + WeatherStationMenu.MODULE_X + 23, y + 46,
                x + W - 7 - (x + WeatherStationMenu.MODULE_X + 23), UiColors.TEXT_SECONDARY);
        energy.draw(this, graphics, rx, y + 66, rw, 26, menu.energy(), menu.energyCapacity());
        badge(graphics, tone, key("badge." + state), rx, y + 104, rw);
        plate(graphics, tone == UiSprites.Tone.OK ? UiSprites.Tone.INFO : tone, plate, x + 7, y + 126, W - 14);

        // inventory and history
        inventory(graphics, x + WeatherStationMenu.INVENTORY_X, y + WeatherStationMenu.INVENTORY_Y);
        int ix = x + 176;
        int iy = y + WeatherStationMenu.INVENTORY_Y - 11;
        int iw = W - 7 - 176;
        UiPanels.screen(graphics, ix, iy, iw, H - 7 - (WeatherStationMenu.INVENTORY_Y - 11));
        text(graphics, UiText.upper(key("history")), ix + 5, iy + 5, iw - 10, UiColors.AMBER);
        int count = Math.min(5, menu.get(WeatherStationBlockEntity.DATA_HISTORY));
        if (count <= 0) {
            text(graphics, key("history_none"), ix + 5, iy + 18, iw - 10, UiColors.TEXT_DIM);
            return;
        }
        UiTable table = new UiTable().column(key("col.day"), 18).column(key("col.event"), iw - 10 - 18 - 28 - 20)
                .column(key("col.strength"), 28).column(key("col.minutes"), 20);
        for (int i = 0; i < count; i++) {
            int at = WeatherStationBlockEntity.DATA_PAST + 4 * i;
            table.row(UiTable.Cell.text(Component.literal(String.valueOf(menu.get(at + 3)))), UiTable.Cell.text(event(menu.get(at))),
                    UiTable.Cell.text(Component.literal(menu.get(at + 1) + "%")), UiTable.Cell.text(Component.literal(String.valueOf(menu.get(at + 2)))));
        }
        table.render(graphics, ix + 5, iy + 16, 11, hints);
    }

    private Component now() {
        int phase = menu.get(WeatherStationBlockEntity.DATA_NOW_PHASE);
        if (phase < 0 || phase >= EventPhase.values().length) {
            return Component.literal("—");
        }
        Component name = event(menu.get(WeatherStationBlockEntity.DATA_NOW_EVENT));
        int left = menu.get(WeatherStationBlockEntity.DATA_NOW_LEFT);
        return switch (EventPhase.values()[phase]) {
            case WARNING -> key("now_warning", name);
            case ACTIVE -> key("now_active", name, Math.max(1, (left + 59) / 60));
            case AFTERMATH -> key("now_aftermath", name);
            default -> key("now_calm");
        };
    }

    @Override
    protected void drawOver(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        if (!menu.getSlot(0).hasItem()) {
            tooltip(x + WeatherStationMenu.MODULE_X, y + WeatherStationMenu.MODULE_Y, 16, 16,
                    () -> UiTooltip.of(key("module_slot")).text(key("module_hint"), UiColors.TEXT_SECONDARY));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
