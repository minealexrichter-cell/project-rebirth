package com.projectrebirth.client.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A table on a screen: a header row in capitals, then rows of cells; a cell is text (fitted to its column) or an icon
 * with text (a check mark for "allowed", a cross for "not allowed", always with a word or a column title next to it).
 */
public final class UiTable {
    public record Cell(Component text, int color, ResourceLocation icon) {
        public static Cell text(Component text) {
            return new Cell(text, UiColors.SCREEN_TEXT, null);
        }

        public static Cell yes(Component text) {
            return new Cell(text, UiColors.OK, UiSprites.ICON_CHECK);
        }

        public static Cell no(Component text) {
            return new Cell(text, UiColors.ERROR, UiSprites.ICON_CROSS);
        }
    }

    private final List<Component> headers = new ArrayList<>();
    private final List<Integer> widths = new ArrayList<>();
    private final List<List<Cell>> rows = new ArrayList<>();

    /** Adds a column with its title and width in pixels. */
    public UiTable column(Component header, int width) {
        headers.add(header);
        widths.add(width);
        return this;
    }

    public UiTable row(Cell... cells) {
        rows.add(List.of(cells));
        return this;
    }

    public int height(int rowHeight) {
        return (rows.size() + 1) * rowHeight + 2;
    }

    /** Draws the table at (x, y); cut texts go into hints. Rows alternate with a faint stripe. */
    public void render(GuiGraphics graphics, int x, int y, int rowHeight, UiText.Hints hints) {
        int columnX = x;
        for (int c = 0; c < headers.size(); c++) {
            hints.line(graphics, UiText.upper(headers.get(c)), columnX + 2, y + 1, widths.get(c) - 4, UiColors.TEXT_SECONDARY);
            columnX += widths.get(c);
        }
        int total = columnX - x;
        graphics.fill(x, y + rowHeight - 1, x + total, y + rowHeight, 0xFF2A332E);
        for (int r = 0; r < rows.size(); r++) {
            int rowY = y + (r + 1) * rowHeight + 1;
            if (r % 2 == 1) {
                graphics.fill(x, rowY - 1, x + total, rowY + rowHeight - 1, 0x0CFFFFFF);
            }
            columnX = x;
            for (int c = 0; c < Math.min(headers.size(), rows.get(r).size()); c++) {
                Cell cell = rows.get(r).get(c);
                int textX = columnX + 2;
                if (cell.icon != null) {
                    UiSprites.draw(graphics, cell.icon, textX, rowY + (rowHeight - 9) / 2 + 1, 7, 7);
                    textX += 9;
                }
                hints.line(graphics, cell.text, textX, rowY + (rowHeight - 9) / 2 + 1, columnX + widths.get(c) - 2 - textX, cell.color);
                columnX += widths.get(c);
            }
        }
    }
}
