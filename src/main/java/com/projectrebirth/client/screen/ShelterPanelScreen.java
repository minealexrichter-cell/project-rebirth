package com.projectrebirth.client.screen;

import java.util.Locale;

import com.projectrebirth.shelter.ShelterControlPanelBlockEntity;
import com.projectrebirth.shelter.ShelterPanelMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import static com.projectrebirth.client.screen.GuiShapes.panel;

/** Window of the shelter control panel: the danger, the backup power and each watched room; a button that shows the leak. */
public class ShelterPanelScreen extends AbstractContainerScreen<ShelterPanelMenu> {
    private static final int TEXT = 0x404040;
    private static final int GREEN = 0x3F8F3F;
    private static final int ORANGE = 0xB06A10;
    private static final int RED = 0xB03030;
    private static final int ROW = 10;

    public ShelterPanelScreen(ShelterPanelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 196;
        imageHeight = 176;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("screen.projectrebirth.panel.find_leak"), button -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ShelterPanelMenu.BUTTON_FIND_LEAK);
            }
        }).bounds(leftPos + 8, topPos + imageHeight - 26, imageWidth - 16, 18).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        panel(graphics, leftPos, topPos, imageWidth, imageHeight);
    }

    /** A line of text, made smaller if it is wider than the window. */
    private void line(GuiGraphics graphics, Component text, int x, int y, int color) {
        int width = font.width(text);
        int room = imageWidth - x - 8;
        if (width <= room) {
            graphics.drawString(font, text, x, y, color, false);
            return;
        }
        float scale = room / (float) width;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y + (1.0f - scale) * font.lineHeight / 2.0f, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static String hundredths(int value) {
        return String.format(Locale.ROOT, "%.2f", value / 100.0f);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        int danger = menu.get(ShelterControlPanelBlockEntity.DATA_DANGER);
        int y = 18;
        line(graphics, Component.translatable("screen.projectrebirth.panel.danger", danger,
                Component.translatable("screen.projectrebirth.panel.danger." + danger)), 8, y, danger == 0 ? GREEN : danger >= 12 ? RED : ORANGE);
        y += ROW + 2;
        int backup = menu.get(ShelterControlPanelBlockEntity.DATA_BACKUP);
        if (backup >= 0) {
            boolean reserve = menu.get(ShelterControlPanelBlockEntity.DATA_RESERVE) != 0;
            line(graphics, Component.translatable(reserve ? "screen.projectrebirth.panel.backup_on" : "screen.projectrebirth.panel.backup", backup / 10),
                    8, y, reserve ? ORANGE : TEXT);
        } else {
            line(graphics, Component.translatable("screen.projectrebirth.panel.no_backup"), 8, y, TEXT);
        }
        y += ROW + 4;
        int rooms = menu.get(ShelterControlPanelBlockEntity.DATA_ROOMS);
        if (rooms == 0) {
            line(graphics, Component.translatable("screen.projectrebirth.panel.no_rooms"), 8, y, TEXT);
            return;
        }
        for (int i = 0; i < Math.min(rooms, ShelterControlPanelBlockEntity.ROOMS_SHOWN); i++) {
            int state = menu.room(i, ShelterControlPanelBlockEntity.ROOM_STATE);
            int flags = menu.room(i, ShelterControlPanelBlockEntity.ROOM_FLAGS);
            boolean sealed = state == 1;
            line(graphics, Component.translatable("screen.projectrebirth.panel.room", i + 1,
                    Component.translatable("screen.projectrebirth.panel.room_state." + state), menu.room(i, ShelterControlPanelBlockEntity.ROOM_VOLUME),
                    menu.room(i, ShelterControlPanelBlockEntity.ROOM_UNITS)), 8, y, sealed ? GREEN : RED);
            y += ROW;
            Component supply = (flags & ShelterControlPanelBlockEntity.FLAG_NO_CARTRIDGE) != 0
                    ? Component.translatable("screen.projectrebirth.panel.no_cartridge")
                    : (flags & ShelterControlPanelBlockEntity.FLAG_NO_ENERGY) != 0
                    ? Component.translatable("screen.projectrebirth.panel.no_energy")
                    : Component.translatable("screen.projectrebirth.panel.cartridge", menu.room(i, ShelterControlPanelBlockEntity.ROOM_CARTRIDGE) / 10);
            line(graphics, Component.translatable("screen.projectrebirth.panel.room_air",
                    hundredths(menu.room(i, ShelterControlPanelBlockEntity.ROOM_POLLUTION)), supply), 14, y, flags != 0 ? RED : TEXT);
            y += ROW + 3;
        }
        if (rooms > ShelterControlPanelBlockEntity.ROOMS_SHOWN) {
            line(graphics, Component.translatable("screen.projectrebirth.panel.more", rooms - ShelterControlPanelBlockEntity.ROOMS_SHOWN), 8, y, TEXT);
        }
    }
}
