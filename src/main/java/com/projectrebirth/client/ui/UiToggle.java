package com.projectrebirth.client.ui;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A switch 15 x 8 with its meaning in words next to it ("Monsters — attack"), so the state is never told by colour alone.
 * A disabled switch is dimmed and says why in its tooltip.
 */
public class UiToggle extends AbstractButton {
    private final BooleanSupplier state;
    private final Consumer<Boolean> change;
    private final Function<Boolean, Component> meaning;

    /** meaning gives the words for the current state, e.g. on → "Monsters — attack", off → "Monsters — ignore". */
    public UiToggle(int x, int y, int width, BooleanSupplier state, Consumer<Boolean> change, Function<Boolean, Component> meaning) {
        super(x, y, width, 10, meaning.apply(state.getAsBoolean()));
        this.state = state;
        this.change = change;
        this.meaning = meaning;
    }

    public void disable(@Nullable Component reason) {
        active = reason == null;
        setTooltip(reason == null ? null : Tooltip.create(reason));
    }

    @Override
    public void onPress() {
        change.accept(!state.getAsBoolean());
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager manager) {
        UiSounds.TOGGLE.play();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean on = state.getAsBoolean();
        setMessage(meaning.apply(on));
        graphics.setColor(1.0f, 1.0f, 1.0f, active ? 1.0f : 0.45f);
        UiSprites.draw(graphics, on ? UiSprites.TOGGLE_ON : UiSprites.TOGGLE_OFF, getX(), getY() + 1, 15, 8);
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        int color = !active ? UiColors.TEXT_DIM : isHoveredOrFocused() ? UiColors.AMBER_LIGHT : UiColors.TEXT;
        UiText.line(graphics, getMessage(), getX() + 19, getY() + 1, width - 19, color);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
