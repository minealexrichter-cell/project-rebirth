package com.projectrebirth.client.ui;

import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A button of the style: metal with a bevel (or a flat "chip" for filters and tabs), with an icon, a text or both. The
 * text always fits (see UiText). A disabled button is dimmed and says why in its tooltip.
 */
public class UiButton extends AbstractButton {
    public enum Look {
        /** A raised metal button. */
        METAL,
        /** A flat chip that is lit amber while it is selected (filters, tabs). */
        CHIP
    }

    private final Look look;
    @Nullable
    private final ResourceLocation icon;
    private final int iconSize;
    private final Runnable action;
    private BooleanSupplier selected = () -> false;
    private int textColor = UiColors.TEXT;

    public UiButton(int x, int y, int width, int height, Component text, @Nullable ResourceLocation icon, int iconSize, Look look, Runnable action) {
        super(x, y, width, height, text);
        this.look = look;
        this.icon = icon;
        this.iconSize = iconSize;
        this.action = action;
    }

    /** A square metal button with only an icon; the text is its tooltip and narration. */
    public static UiButton icon(int x, int y, int size, ResourceLocation icon, int iconSize, Component tooltip, Runnable action) {
        UiButton button = new UiButton(x, y, size, size, tooltip, icon, iconSize, Look.METAL, action);
        button.setTooltip(Tooltip.create(tooltip));
        return button;
    }

    public UiButton selected(BooleanSupplier selected) {
        this.selected = selected;
        return this;
    }

    public UiButton color(int color) {
        this.textColor = color;
        return this;
    }

    /** Disables the button and tells why in its tooltip; enables it again with a null reason. */
    public void disable(@Nullable Component reason) {
        active = reason == null;
        if (reason != null) {
            setTooltip(Tooltip.create(reason));
        }
    }

    /**
     * The "?" button of a window: opens the Codex on the entry about this window; closing the Codex returns to it.
     */
    public static UiButton help(int x, int y, net.minecraft.client.gui.screens.Screen window, String codexEntry) {
        return icon(x, y, 13, UiSprites.ICON_HELP, 7, Component.translatable("screen.projectrebirth.ui.help"),
                () -> net.minecraft.client.Minecraft.getInstance().setScreen(com.projectrebirth.client.codex.CodexScreen.openFrom(window, codexEntry)));
    }

    private boolean wasHovered;

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager manager) {
        UiSounds.CLICK.play();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean lit = selected.getAsBoolean();
        if (isHovered() && !wasHovered && active) {
            UiSounds.HOVER.play();
        }
        wasHovered = isHovered();
        ResourceLocation sprite = switch (look) {
            case METAL -> !active ? UiSprites.BUTTON_DISABLED : isHoveredOrFocused() ? UiSprites.BUTTON_HOVER : UiSprites.BUTTON;
            case CHIP -> lit ? UiSprites.CHIP_ON : isHoveredOrFocused() ? UiSprites.CHIP_OFF_HOVER : UiSprites.CHIP_OFF;
        };
        UiSprites.draw(graphics, sprite, getX(), getY(), width, height);
        int alpha = active ? 255 : 115;
        boolean hasText = icon == null || look == Look.CHIP;
        int contentWidth = (icon != null ? iconSize : 0) + (hasText ? minecraftFont().width(getMessage()) + (icon != null ? 3 : 0) : 0);
        int left = getX() + Math.max(3, (width - contentWidth) / 2);
        if (icon != null) {
            graphics.setColor(1.0f, 1.0f, 1.0f, alpha / 255.0f);
            UiSprites.draw(graphics, icon, hasText ? left : getX() + (width - iconSize) / 2, getY() + (height - iconSize) / 2, iconSize, iconSize);
            graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            left += iconSize + 3;
        }
        if (hasText) {
            int color = look == Look.CHIP ? (lit ? UiColors.AMBER_LIGHT : UiColors.TEXT_SECONDARY) : textColor;
            UiText.line(graphics, getMessage(), left, getY() + (height - 8) / 2, getX() + width - 3 - left, UiColors.withAlpha(color, alpha));
        }
    }

    private static net.minecraft.client.gui.Font minecraftFont() {
        return net.minecraft.client.Minecraft.getInstance().font;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
