package com.projectrebirth.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** A search field of the style: a dark box with a magnifier and a grey hint while it is empty. */
public class UiSearchField extends EditBox {
    private final int frameX;
    private final int frameY;
    private final int frameWidth;
    private final int frameHeight;

    /** The frame takes the given rectangle; the text sits right of the magnifier. */
    public UiSearchField(int x, int y, int width, int height, Component hint) {
        super(Minecraft.getInstance().font, x + 13, y + (height - 8) / 2, width - 16, 9, hint);
        this.frameX = x;
        this.frameY = y;
        this.frameWidth = width;
        this.frameHeight = height;
        setBordered(false);
        setMaxLength(40);
        setTextColor(UiColors.TEXT);
        setHint(hint.copy().withColor(UiColors.TEXT_DIM));
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiSprites.draw(graphics, isFocused() ? UiSprites.FIELD_FOCUS : UiSprites.FIELD, frameX, frameY, frameWidth, frameHeight);
        UiSprites.draw(graphics, UiSprites.ICON_SEARCH, frameX + 3, frameY + (frameHeight - 7) / 2, 7, 7);
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return visible && mouseX >= frameX && mouseX < frameX + frameWidth && mouseY >= frameY && mouseY < frameY + frameHeight;
    }
}
