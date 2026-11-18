package com.projectrebirth.client.ui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A list on a screen with a scroll bar: rows of one height, each drawn by the window (a recipe, a body card, a machine),
 * the selected one framed amber. Scrolls with the wheel or by dragging the thumb.
 */
public class UiList<T> extends AbstractWidget {
    @FunctionalInterface
    public interface Row<T> {
        void draw(GuiGraphics graphics, T item, int x, int y, int width, int height, boolean selected, boolean hovered);
    }

    private final Supplier<List<T>> items;
    private final int rowHeight;
    private final Row<T> row;
    private final Supplier<T> selected;
    private final Consumer<T> select;
    private int start;
    private boolean dragging;
    private boolean background = true;

    public UiList(int x, int y, int width, int height, int rowHeight, Supplier<List<T>> items, Row<T> row,
                  Supplier<T> selected, Consumer<T> select) {
        super(x, y, width, height, Component.empty());
        this.items = items;
        this.rowHeight = rowHeight;
        this.row = row;
        this.selected = selected;
        this.select = select;
    }

    /** Without the sunken screen behind it (when the window draws its own). */
    public UiList<T> noBackground() {
        background = false;
        return this;
    }

    private int visibleRows() {
        return Math.max(1, (height - 4) / rowHeight);
    }

    private int maxStart() {
        return Math.max(0, items.get().size() - visibleRows());
    }

    /** Scrolls so that the item is in view. */
    public void reveal(T item) {
        int index = items.get().indexOf(item);
        if (index >= 0 && (index < start || index >= start + visibleRows())) {
            start = Mth.clamp(index - visibleRows() / 2, 0, maxStart());
        }
    }

    @Nullable
    private T itemAt(double mouseX, double mouseY) {
        if (mouseX < getX() + 2 || mouseX >= getX() + width - 5 || mouseY < getY() + 2) {
            return null;
        }
        int index = start + (int) (mouseY - getY() - 2) / rowHeight;
        List<T> list = items.get();
        return (int) (mouseY - getY() - 2) / rowHeight < visibleRows() && index < list.size() ? list.get(index) : null;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (background) {
            UiPanels.screen(graphics, getX(), getY(), width, height);
        }
        List<T> list = items.get();
        start = Mth.clamp(start, 0, maxStart());
        T hovered = isHovered() ? itemAt(mouseX, mouseY) : null;
        T chosen = selected.get();
        for (int i = 0; i < visibleRows() && start + i < list.size(); i++) {
            T item = list.get(start + i);
            row.draw(graphics, item, getX() + 2, getY() + 2 + i * rowHeight, width - 7, rowHeight, item.equals(chosen), item.equals(hovered));
        }
        if (maxStart() > 0) {
            int trackX = getX() + width - 4;
            int trackH = visibleRows() * rowHeight;
            graphics.fill(trackX, getY() + 2, trackX + 2, getY() + 2 + trackH, 0xFF1F2623);
            int thumbH = Math.max(8, trackH * visibleRows() / list.size());
            int thumbY = (trackH - thumbH) * start / maxStart();
            graphics.fill(trackX, getY() + 2 + thumbY, trackX + 2, getY() + 2 + thumbY + thumbH, UiColors.AMBER);
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (mouseX >= getX() + width - 6 && maxStart() > 0) {
            dragging = true;
            scrollTo(mouseY);
            return;
        }
        T item = itemAt(mouseX, mouseY);
        if (item != null) {
            UiSounds.CLICK.play();
            select.accept(item);
        }
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager manager) {
        // the click sounds in onClick, only on a row
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        if (dragging) {
            scrollTo(mouseY);
        }
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        dragging = false;
    }

    private void scrollTo(double mouseY) {
        float fraction = (float) ((mouseY - getY() - 2) / Math.max(1, visibleRows() * rowHeight));
        start = Mth.clamp(Math.round(fraction * maxStart()), 0, maxStart());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY) || maxStart() == 0) {
            return false;
        }
        start = Mth.clamp(start - (int) Math.signum(scrollY), 0, maxStart());
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
