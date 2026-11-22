package com.projectrebirth.client.logistics;

import java.util.List;

import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiDropdown;
import com.projectrebirth.client.ui.UiMachineScreen;
import com.projectrebirth.client.ui.UiPanels;
import com.projectrebirth.client.ui.UiSearchField;
import com.projectrebirth.client.ui.UiSlots;
import com.projectrebirth.client.ui.UiSprites;
import com.projectrebirth.logistics.LogisticsRegistry;
import com.projectrebirth.logistics.StorageMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 1.8.0, block J: the window of the storage in the style "Industrial terminal": a search field and a sort order over the store
 * (9 × 5 cells, the wheel scrolls), how full it is, whether the construction is whole, the player's inventory.
 */
public class StorageScreen extends UiMachineScreen<StorageMenu> {
    private static final int W = 196;
    private static final int H = 246;
    private UiSearchField search;
    private int sort = StorageMenu.SORT_NAME;
    private int row;
    private String sent = "";
    private int sentSort = -1;
    private int sentRow = -1;

    public StorageScreen(StorageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H, "storage");
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.projectrebirth.storage." + key, args);
    }

    @Override
    protected void init() {
        super.init();
        search = addRenderableWidget(new UiSearchField(leftPos + 7, topPos + 23, 104, 13, tr("search")));
        search.setResponder(text -> {
            row = 0;
            ask();
        });
        addRenderableWidget(new UiDropdown<>(leftPos + 115, topPos + 23, W - 7 - 115, List.of(StorageMenu.SORT_NAME, StorageMenu.SORT_COUNT, StorageMenu.SORT_MOD),
                value -> tr("sort." + value), () -> sort, value -> {
                    sort = value;
                    row = 0;
                    ask();
                }));
        ask();
    }

    /** Tells the server what to show (only when it changed). */
    private void ask() {
        String query = search == null ? "" : search.getValue();
        if (query.equals(sent) && sort == sentSort && row == sentRow) {
            return;
        }
        sent = query;
        sentSort = sort;
        sentRow = row;
        PacketDistributor.sendToServer(new LogisticsRegistry.ViewPayload(menu.containerId, query, sort, row));
    }

    private int maxRow() {
        int shown = menu.get(StorageMenu.DATA_RESULTS) + (search != null && search.getValue().isEmpty()
                ? menu.get(StorageMenu.DATA_SLOTS) - menu.get(StorageMenu.DATA_USED) : 1);
        return Math.max(0, (shown + StorageMenu.COLUMNS - 1) / StorageMenu.COLUMNS - StorageMenu.ROWS);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int gx = leftPos + StorageMenu.VIEW_X, gy = topPos + StorageMenu.VIEW_Y;
        if (mouseX >= gx - 1 && mouseX < gx + StorageMenu.COLUMNS * 18 + 12 && mouseY >= gy - 1 && mouseY < gy + StorageMenu.ROWS * 18) {
            row = Mth.clamp(row - (int) Math.signum(scrollY), 0, maxRow());
            ask();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // typing in the search field: the keys of the inventory do not close the window
        if (search != null && search.isFocused() && keyCode != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            return search.keyPressed(keyCode, scanCode, modifiers) || search.canConsumeInput() || super.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void drawBody(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int gx = x + StorageMenu.VIEW_X, gy = y + StorageMenu.VIEW_Y;
        for (int i = 0; i < StorageMenu.VIEW; i++) {
            UiSlots.frame(graphics, UiSlots.Kind.INPUT, gx + (i % StorageMenu.COLUMNS) * 18, gy + (i / StorageMenu.COLUMNS) * 18);
        }
        // the scroll bar
        int bx = gx + StorageMenu.COLUMNS * 18 + 2, bh = StorageMenu.ROWS * 18 - 2;
        UiPanels.screen(graphics, bx, gy - 1, 8, bh + 2);
        int max = maxRow();
        int knob = Math.max(10, bh / (max + 1));
        int ky = max <= 0 ? 0 : (bh - knob) * Math.min(row, max) / max;
        graphics.fill(bx + 2, gy + ky, bx + 6, gy + ky + knob, max <= 0 ? UiColors.TEXT_DIM | 0xFF000000 : UiColors.AMBER);
        // how full it is, and whether it is whole
        int slots = menu.get(StorageMenu.DATA_SLOTS), used = menu.get(StorageMenu.DATA_USED);
        int sy = gy + StorageMenu.ROWS * 18 + 3;
        if (menu.get(StorageMenu.DATA_WHOLE) == 0) {
            plate(graphics, UiSprites.Tone.ERROR, tr("not_whole"), x + 7, sy, W - 14);
        } else {
            float share = slots <= 0 ? 0.0f : used / (float) slots;
            plate(graphics, share >= 0.95f ? UiSprites.Tone.WARNING : UiSprites.Tone.OK, tr("used", used, slots), x + 7, sy, W - 14);
        }
        inventory(graphics, x + StorageMenu.INVENTORY_X, y + StorageMenu.INVENTORY_Y);
    }
}
