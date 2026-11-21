package com.projectrebirth.client.screen;

import java.util.Locale;

import com.projectrebirth.client.Interference;
import com.projectrebirth.client.ui.UiBars;
import com.projectrebirth.client.ui.UiButton;
import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiMachineScreen;
import com.projectrebirth.client.ui.UiPanels;
import com.projectrebirth.client.ui.UiSprites;
import com.projectrebirth.client.ui.UiText;
import com.projectrebirth.shelter.ShelterControlPanelBlockEntity;
import com.projectrebirth.shelter.ShelterPanelMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Shelter control panel in the style "Industrial terminal" (1.8.0 block D): the danger level as a scale with its word,
 * the backup battery, the rooms as cards (sealed or which leak, volume, units, the air inside, the cartridge or what is
 * missing) and the "Find the leak" button, which lights the leak up in the world as before.
 */
public class ShelterPanelScreen extends UiMachineScreen<ShelterPanelMenu> {
    private static final int W = 300;
    private static final int H = 214;
    private static final int CARD_H = 30;

    public ShelterPanelScreen(ShelterPanelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H, "shelter_panel");
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(new UiButton(leftPos + 7, topPos + H - 23, W - 14, 16, tr("find_leak"), UiSprites.ICON_WARNING_AMBER, 7,
                UiButton.Look.METAL, () -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ShelterPanelMenu.BUTTON_FIND_LEAK);
                    }
                }));
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.projectrebirth.panel." + key, args);
    }

    private static String hundredths(int value) {
        return String.format(Locale.ROOT, "%.2f", value / 100.0f);
    }

    /** The danger levels the panel knows (the redstone strength): 0, 4, 6, 8, 12, 15. */
    private static UiSprites.Tone tone(int danger) {
        return danger == 0 ? UiSprites.Tone.OK : danger >= 12 ? UiSprites.Tone.ERROR : UiSprites.Tone.WARNING;
    }

    @Override
    protected void drawBody(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int danger = menu.get(ShelterControlPanelBlockEntity.DATA_DANGER);
        UiSprites.Tone tone = tone(danger);
        // danger and backup
        UiPanels.label(graphics, tr("label.danger"), x + 8, y + 26, 120);
        int color = danger == 0 ? UiColors.OK : danger >= 12 ? UiColors.ERROR : UiColors.WARNING;
        UiBars.meter(graphics, tone.icon, x + 8, y + 37, 140, danger / 15.0f, color, Component.literal(danger + "/15"));
        text(graphics, Component.translatable("screen.projectrebirth.panel.danger." + danger), x + 8, y + 49, 140, tone.text);
        UiPanels.label(graphics, tr("label.backup"), x + 156, y + 26, W - 163);
        int backup = menu.get(ShelterControlPanelBlockEntity.DATA_BACKUP);
        if (backup >= 0) {
            boolean reserve = menu.get(ShelterControlPanelBlockEntity.DATA_RESERVE) != 0;
            UiBars.meter(graphics, null, x + 156, y + 37, W - 163, backup / 1000.0f, reserve ? UiColors.WARNING : UiColors.ENERGY,
                    Component.literal(backup / 10 + "%"));
            text(graphics, tr(reserve ? "reserve_on" : "reserve_waiting"), x + 156, y + 49, W - 163, reserve ? UiColors.WARNING : UiColors.TEXT_SECONDARY);
        } else {
            UiText.wrapped(graphics, tr("no_backup"), x + 156, y + 37, W - 163, 20, UiColors.TEXT_DIM);
        }
        if (Interference.active()) {
            text(graphics, tr("interference"), x + W - 90, y + 26, 50, 0xFFCE93D8);
        }
        // rooms
        UiPanels.label(graphics, tr("label.rooms"), x + 8, y + 62, 200);
        int rooms = menu.get(ShelterControlPanelBlockEntity.DATA_ROOMS);
        int listY = y + 73;
        UiPanels.screen(graphics, x + 7, listY, W - 14, 4 * CARD_H + 4);
        if (rooms == 0) {
            UiText.wrapped(graphics, tr("no_rooms"), x + 14, listY + 8, W - 28, 40, UiColors.AMBER_DIM);
            return;
        }
        int shown = Math.min(rooms, ShelterControlPanelBlockEntity.ROOMS_SHOWN);
        for (int i = 0; i < shown; i++) {
            drawRoom(graphics, i, x + 9, listY + 2 + i * CARD_H, W - 18);
        }
        if (rooms > shown) {
            text(graphics, tr("more", rooms - shown), x + W - 80, y + 62, 72, UiColors.TEXT_SECONDARY);
        }
    }

    private void drawRoom(GuiGraphics graphics, int i, int x, int y, int width) {
        int state = menu.room(i, ShelterControlPanelBlockEntity.ROOM_STATE);
        int flags = menu.room(i, ShelterControlPanelBlockEntity.ROOM_FLAGS);
        boolean sealed = state == 1;
        UiSprites.Tone tone = !sealed ? UiSprites.Tone.ERROR : flags != 0 ? UiSprites.Tone.WARNING : UiSprites.Tone.OK;
        UiPanels.card(graphics, x, y, width, CARD_H - 2, false);
        UiSprites.draw(graphics, tone.dot, x + 5, y + 5, 5, 5);
        text(graphics, tr("room_title", i + 1, Component.translatable("screen.projectrebirth.panel.room_state." + state)), x + 13, y + 3, 150, tone.text);
        text(graphics, tr("room_size", menu.room(i, ShelterControlPanelBlockEntity.ROOM_VOLUME), menu.room(i, ShelterControlPanelBlockEntity.ROOM_UNITS)),
                x + 13, y + 15, 150, UiColors.TEXT_SECONDARY);
        int rx = x + 170;
        int rw = width - 174;
        text(graphics, tr("air", hundredths(menu.room(i, ShelterControlPanelBlockEntity.ROOM_POLLUTION))), rx, y + 3, rw, UiColors.SCREEN_TEXT);
        Component supply;
        int supplyColor;
        if ((flags & ShelterControlPanelBlockEntity.FLAG_NO_CARTRIDGE) != 0) {
            supply = tr("no_cartridge");
            supplyColor = UiColors.ERROR;
        } else if ((flags & ShelterControlPanelBlockEntity.FLAG_NO_ENERGY) != 0) {
            supply = tr("no_energy");
            supplyColor = UiColors.ERROR;
        } else {
            supply = tr("cartridge", menu.room(i, ShelterControlPanelBlockEntity.ROOM_CARTRIDGE) / 10);
            supplyColor = UiColors.TEXT_SECONDARY;
        }
        text(graphics, supply, rx, y + 15, rw, supplyColor);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
