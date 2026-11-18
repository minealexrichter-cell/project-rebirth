package com.projectrebirth.client.ui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A drop-down list: a box with the current value and an arrow; a click opens the list below it over everything else,
 * a click on a line picks it, a click elsewhere closes it. Add it to the screen last so it gets the clicks first.
 */
public class UiDropdown<T> extends AbstractWidget {
    private static final int LINE = 11;

    private final List<T> options;
    private final Function<T, Component> name;
    private final Supplier<T> current;
    private final Consumer<T> pick;
    private boolean open;

    public UiDropdown(int x, int y, int width, List<T> options, Function<T, Component> name, Supplier<T> current, Consumer<T> pick) {
        super(x, y, width, 12, Component.empty());
        this.options = options;
        this.name = name;
        this.current = current;
        this.pick = pick;
    }

    public boolean isOpen() {
        return open;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return visible && mouseX >= getX() && mouseX < getX() + width && mouseY >= getY() && mouseY < getY() + height + (open ? listHeight() : 0);
    }

    private int listHeight() {
        return options.size() * LINE + 2;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiSprites.draw(graphics, open || isHoveredOrFocused() ? UiSprites.FIELD_FOCUS : UiSprites.FIELD, getX(), getY(), width, height);
        UiText.line(graphics, name.apply(current.get()), getX() + 4, getY() + 2, width - 16, UiColors.TEXT);
        UiSprites.draw(graphics, open ? UiSprites.ICON_ARROW_UP : UiSprites.ICON_ARROW_DOWN, getX() + width - 10, getY() + 4, 7, 4);
        if (!open) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, 300.0f);
        int top = getY() + height;
        UiSprites.draw(graphics, UiSprites.TOOLTIP, getX(), top, width, listHeight());
        for (int i = 0; i < options.size(); i++) {
            T option = options.get(i);
            int y = top + 1 + i * LINE;
            boolean hovered = mouseX >= getX() && mouseX < getX() + width && mouseY >= y && mouseY < y + LINE;
            boolean chosen = option.equals(current.get());
            if (hovered) {
                graphics.fill(getX() + 2, y, getX() + width - 2, y + LINE, UiColors.AMBER_BACK);
            }
            UiText.line(graphics, name.apply(option), getX() + 5, y + 2, width - 10, chosen ? UiColors.AMBER_LIGHT : UiColors.TEXT);
        }
        graphics.pose().popPose();
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (open && mouseY >= getY() + height) {
            int index = (int) (mouseY - getY() - height - 1) / LINE;
            if (index >= 0 && index < options.size()) {
                pick.accept(options.get(index));
            }
            open = false;
            return;
        }
        open = !open;
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager manager) {
        UiSounds.CLICK.play();
    }

    /** A click anywhere else closes the list (the screen calls this before passing the click on). */
    public void closeIfOutside(double mouseX, double mouseY) {
        if (open && !isMouseOver(mouseX, mouseY)) {
            open = false;
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, name.apply(current.get()));
    }
}
