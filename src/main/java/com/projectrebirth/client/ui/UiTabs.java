package com.projectrebirth.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Tabs on top of a panel. A tab may carry a colour mark (the groups of the garrison) — always with its number or name,
 * never the colour alone. Tabs share the width by their text; if the text does not fit it is shrunk or cut (UiText).
 */
public class UiTabs extends AbstractWidget {
    /** color 0 means no mark. */
    public record Tab(Component label, int color) {
        public static Tab of(Component label) {
            return new Tab(label, 0);
        }
    }

    private final List<Tab> tabs;
    private final IntSupplier selected;
    private final IntConsumer select;
    private final List<int[]> bounds = new ArrayList<>();

    public UiTabs(int x, int y, int width, List<Tab> tabs, IntSupplier selected, IntConsumer select) {
        super(x, y, width, 14, Component.empty());
        this.tabs = tabs;
        this.selected = selected;
        this.select = select;
        layout();
    }

    private void layout() {
        bounds.clear();
        var font = Minecraft.getInstance().font;
        int natural = 0;
        for (Tab tab : tabs) {
            natural += font.width(tab.label) + 12 + (tab.color != 0 ? 8 : 0);
        }
        float squeeze = Math.min(1.0f, (float) (width - (tabs.size() - 1) * 2) / Math.max(1, natural));
        int left = getX();
        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            int w = Math.round((font.width(tab.label) + 12 + (tab.color != 0 ? 8 : 0)) * squeeze);
            if (i == tabs.size() - 1 && squeeze < 1.0f) {
                w = getX() + width - left;
            }
            bounds.add(new int[] {left, w});
            left += w + 2;
        }
    }

    private int tabAt(double mouseX, double mouseY) {
        if (mouseY < getY() || mouseY >= getY() + height) {
            return -1;
        }
        for (int i = 0; i < bounds.size(); i++) {
            if (mouseX >= bounds.get(i)[0] && mouseX < bounds.get(i)[0] + bounds.get(i)[1]) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int hovered = isHovered() ? tabAt(mouseX, mouseY) : -1;
        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            int x = bounds.get(i)[0];
            int w = bounds.get(i)[1];
            boolean on = i == selected.getAsInt();
            UiSprites.draw(graphics, on ? UiSprites.TAB_ON : i == hovered ? UiSprites.TAB_HOVER : UiSprites.TAB_OFF, x, getY(), w, height);
            int textX = x + 6;
            if (tab.color != 0) {
                graphics.fill(textX, getY() + 5, textX + 5, getY() + 10, 0xFF000000 | tab.color);
                textX += 8;
            }
            UiText.line(graphics, tab.label, textX, getY() + 4, x + w - 4 - textX, on ? UiColors.AMBER_LIGHT : UiColors.TEXT_SECONDARY);
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        int tab = tabAt(mouseX, mouseY);
        if (tab >= 0 && tab != selected.getAsInt()) {
            UiSounds.CLICK.play();
            select.accept(tab);
        }
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager manager) {
        // the click sounds in onClick, only when the tab changes
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        int index = selected.getAsInt();
        if (index >= 0 && index < tabs.size()) {
            output.add(NarratedElementType.TITLE, tabs.get(index).label);
        }
    }
}
