package com.projectrebirth.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * The common frame of the mod's machine windows in the style "Industrial terminal" (1.8.0): the metal body with the title,
 * the "?" (Codex) and "✕" buttons, the player's inventory, cut texts with their full text under the mouse, and tooltip
 * areas. A window only draws its own part in {@link #drawBody} and names its areas with {@link #tooltip}.
 */
public abstract class UiMachineScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    protected final UiText.Hints hints = new UiText.Hints();
    private final List<Area> areas = new ArrayList<>();
    @Nullable
    private final String codexEntry;
    private final ResourceLocation icon;

    private record Area(int x, int y, int width, int height, Supplier<UiTooltip> tooltip) {
    }

    protected UiMachineScreen(M menu, Inventory inventory, Component title, int width, int height, @Nullable String codexEntry) {
        this(menu, inventory, title, width, height, codexEntry, UiSprites.ICON_STATION);
    }

    protected UiMachineScreen(M menu, Inventory inventory, Component title, int width, int height, @Nullable String codexEntry, ResourceLocation icon) {
        super(menu, inventory, title);
        this.imageWidth = width;
        this.imageHeight = height;
        this.codexEntry = codexEntry;
        this.icon = icon;
    }

    @Override
    protected void init() {
        super.init();
        if (codexEntry != null) {
            // the entry is asked when the button is pressed: some windows know it only after the server's data came
            addRenderableWidget(UiButton.icon(leftPos + imageWidth - 37, topPos + 4, 13, UiSprites.ICON_HELP, 7,
                    Component.translatable("screen.projectrebirth.ui.help"),
                    () -> net.minecraft.client.Minecraft.getInstance().setScreen(com.projectrebirth.client.codex.CodexScreen.openFrom(this, codexEntry()))));
        }
        addRenderableWidget(UiButton.icon(leftPos + imageWidth - 21, topPos + 4, 13, UiSprites.ICON_CLOSE, 7,
                Component.translatable("screen.projectrebirth.ui.close"), this::onClose));
    }

    /** The Codex entry of the "?" button. */
    protected String codexEntry() {
        return codexEntry;
    }

    /** Draws everything of the window inside its body (the frame and title are already there). */
    protected abstract void drawBody(GuiGraphics graphics, int x, int y, int mouseX, int mouseY);

    /** Draws over the slots and items (ghosts, counts); empty by default. */
    protected void drawOver(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hints.clear();
        areas.clear();
        super.render(graphics, mouseX, mouseY, partialTick);
        drawOver(graphics, leftPos, topPos, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
        if (hoveredSlot != null && hoveredSlot.hasItem() || !menu.getCarried().isEmpty()) {
            return;
        }
        for (Area area : areas) {
            if (mouseX >= area.x && mouseX < area.x + area.width && mouseY >= area.y && mouseY < area.y + area.height) {
                UiTooltip tooltip = area.tooltip.get();
                if (tooltip != null) {
                    tooltip.render(graphics, mouseX, mouseY);
                    return;
                }
            }
        }
        Component hint = hints.at(mouseX, mouseY);
        if (hint != null) {
            graphics.renderTooltip(font, hint, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        UiPanels.window(graphics, leftPos, topPos, imageWidth, imageHeight);
        if (UiPanels.title(graphics, leftPos, topPos, imageWidth, title, icon, leftPos + imageWidth - (codexEntry != null ? 42 : 26))) {
            hints.add(leftPos + 23, topPos + 5, imageWidth - 70, 11, title);
        }
        drawBody(graphics, leftPos, topPos, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // every label is drawn in drawBody, in the style's own way
    }

    /** A tooltip over an area of the window (absolute coordinates); asked only while the mouse is there. */
    protected void tooltip(int x, int y, int width, int height, Supplier<UiTooltip> tooltip) {
        areas.add(new Area(x, y, width, height, tooltip));
    }

    /** The player's inventory with its label; (itemX, itemY) is the top-left item of the three rows, as in the menu. */
    protected void inventory(GuiGraphics graphics, int itemX, int itemY) {
        UiPanels.label(graphics, playerInventoryTitle, itemX - 1, itemY - 11, 160);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                UiSlots.frame(graphics, UiSlots.Kind.INPUT, itemX + col * 18, itemY + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            UiSlots.frame(graphics, UiSlots.Kind.INPUT, itemX + col * 18, itemY + 58);
        }
    }

    /** A fitted line that offers its whole text under the mouse if it had to be cut. */
    protected void text(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
        hints.line(graphics, text, x, y, maxWidth, color);
    }

    /** A status plate; a cut text is offered under the mouse. */
    protected void plate(GuiGraphics graphics, UiSprites.Tone tone, Component text, int x, int y, int width) {
        if (UiPanels.plate(graphics, tone, text, x, y, width)) {
            hints.add(x, y, width, UiPanels.PLATE_HEIGHT, text);
        }
    }

    /** A status badge; a cut word is offered under the mouse. */
    protected void badge(GuiGraphics graphics, UiSprites.Tone tone, Component text, int x, int y, int width) {
        if (UiPanels.badge(graphics, tone, text, x, y, width)) {
            hints.add(x, y, width, UiPanels.PLATE_HEIGHT, text);
        }
    }
}
