package com.projectrebirth.client.hud;

import javax.annotation.Nullable;

import com.projectrebirth.client.screen.HudSettingsScreen;
import com.projectrebirth.client.ui.UiButton;
import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiPanels;
import com.projectrebirth.client.ui.UiSounds;
import com.projectrebirth.client.ui.UiText;
import com.projectrebirth.client.ui.UiTooltip;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The HUD editor (1.8.0 block C, key H): every element of the mod's HUD is shown as a frame where it stands; drag a frame
 * to move it, turn the wheel over it to size it, right-click it to turn it off or on. "Reset" puts everything back,
 * "Settings" opens the other options (HUD on/off, the machines' bars, the transfer animation). The live HUD stays visible
 * under the frames when the player is in a body or an armor. Saved on closing.
 */
public class HudEditorScreen extends Screen {
    private static final int FRAME = 0xCCFFB648;
    private static final int FRAME_OFF = 0x80A3AAB0;

    @Nullable
    private HudElement dragging;
    private double grabX;
    private double grabY;

    public HudEditorScreen() {
        super(Component.translatable("screen.projectrebirth.hud_editor"));
    }

    @Override
    protected void init() {
        int x = width / 2 - 120;
        int y = height / 2 + 6;
        addRenderableWidget(new UiButton(x, y, 76, 16, Component.translatable("screen.projectrebirth.hud_editor.reset"), null, 0, UiButton.Look.METAL,
                () -> {
                    HudElement.resetAll();
                    UiSounds.TOGGLE.play();
                }));
        addRenderableWidget(new UiButton(x + 82, y, 76, 16, Component.translatable("screen.projectrebirth.hud_editor.settings"), null, 0, UiButton.Look.METAL,
                () -> minecraft.setScreen(new HudSettingsScreen(this))));
        addRenderableWidget(new UiButton(x + 164, y, 76, 16, Component.translatable("gui.done"), null, 0, UiButton.Look.METAL, this::onClose));
    }

    private static Component name(HudElement element) {
        return Component.translatable("screen.projectrebirth.hud_editor.element." + element.id());
    }

    private int[] box(HudElement element) {
        int[] at = element.position(width, height);
        return new int[] {at[0], at[1], Math.round(element.width * element.scale()), Math.round(element.height * element.scale())};
    }

    @Nullable
    private HudElement at(double mouseX, double mouseY) {
        HudElement found = null;
        for (HudElement element : HudElement.values()) {
            int[] b = box(element);
            if (mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3]) {
                found = element;
            }
        }
        return found;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // no blur: the live HUD under the frames is what is being placed
        graphics.fill(0, 0, width, height, 0x30000000);
        graphics.fill(width / 2 - 124, height / 2 - 32, width / 2 + 124, height / 2 + 26, 0xCC0E1311);
        UiPanels.label(graphics, title, width / 2 - 120, height / 2 - 28, 240);
        UiText.wrapped(graphics, Component.translatable("screen.projectrebirth.hud_editor.help"), width / 2 - 120, height / 2 - 17, 240, 18, UiColors.TEXT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        HudElement hovered = dragging != null ? dragging : at(mouseX, mouseY);
        for (HudElement element : HudElement.values()) {
            int[] b = box(element);
            boolean on = element.placement().visible();
            int color = on ? FRAME : FRAME_OFF;
            if (element == hovered) {
                graphics.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], 0x30FFB648);
            }
            graphics.fill(b[0], b[1], b[0] + b[2], b[1] + 1, color);
            graphics.fill(b[0], b[1] + b[3] - 1, b[0] + b[2], b[1] + b[3], color);
            graphics.fill(b[0], b[1], b[0] + 1, b[1] + b[3], color);
            graphics.fill(b[0] + b[2] - 1, b[1], b[0] + b[2], b[1] + b[3], color);
            // the name on a tab above the frame (inside it if there is no room), so it does not hide the element itself
            Component label = (on ? name(element) : Component.translatable("screen.projectrebirth.hud_editor.off", name(element)))
                    .copy().append("  " + Math.round(element.placement().scale() * 100) + "%");
            int labelW = Math.min(Math.max(40, b[2]), font.width(label) + 6);
            int labelY = b[1] >= 11 ? b[1] - 11 : b[1] + 1;
            graphics.fill(b[0], labelY, b[0] + labelW, labelY + 11, 0xCC0E1311);
            UiText.line(graphics, label, b[0] + 3, labelY + 2, labelW - 5, on ? UiColors.AMBER_LIGHT : UiColors.TEXT_SECONDARY);
        }
        if (hovered != null && dragging == null) {
            UiTooltip.of(name(hovered))
                    .text(Component.translatable("screen.projectrebirth.hud_editor.tip"), UiColors.TEXT_SECONDARY)
                    .render(graphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        HudElement element = at(mouseX, mouseY);
        if (element == null) {
            return false;
        }
        if (button == 1) {
            HudElement.Placement p = element.placement();
            element.set(new HudElement.Placement(p.dx(), p.dy(), p.scale(), !p.visible()));
            UiSounds.TOGGLE.play();
            return true;
        }
        if (button == 0) {
            int[] b = box(element);
            dragging = element;
            grabX = mouseX - b[0];
            grabY = mouseY - b[1];
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging == null) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        int[] b = box(dragging);
        int x = Mth.clamp((int) Math.round(mouseX - grabX), 0, Math.max(0, width - b[2]));
        int y = Mth.clamp((int) Math.round(mouseY - grabY), 0, Math.max(0, height - b[3]));
        HudElement.Placement p = dragging.placement();
        if (dragging == HudElement.STATUS_PANEL) {
            dragging.set(new HudElement.Placement(x, y, p.scale(), p.visible()));
        } else {
            int[] anchor = dragging.anchor(width, height);
            dragging.set(new HudElement.Placement(x - anchor[0], y - anchor[1], p.scale(), p.visible()));
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        HudElement element = at(mouseX, mouseY);
        if (element == null) {
            return false;
        }
        HudElement.Placement p = element.placement();
        float scale = Mth.clamp(Math.round((p.scale() + (scrollY > 0 ? 0.1f : -0.1f)) * 10) / 10.0f, 0.5f, 2.5f);
        element.set(new HudElement.Placement(p.dx(), p.dy(), scale, p.visible()));
        return true;
    }

    @Override
    public void onClose() {
        com.projectrebirth.config.RebirthClientConfig.CLIENT_SPEC.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
