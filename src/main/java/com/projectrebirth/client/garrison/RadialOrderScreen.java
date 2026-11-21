package com.projectrebirth.client.garrison;

import java.util.List;

import javax.annotation.Nullable;

import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiPanels;
import com.projectrebirth.client.ui.UiSounds;
import com.projectrebirth.client.ui.UiSprites;
import com.projectrebirth.client.ui.UiText;
import com.projectrebirth.garrison.ConsoleView;
import com.projectrebirth.garrison.MachineKind;
import com.projectrebirth.garrison.MachineTask;
import com.projectrebirth.garrison.OrderRules;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The round menu of the tactical tablet (1.8.0, block F): while the menu key (Y) is held, the console's orders stand in a circle;
 * the mouse points at one and letting the key go gives it to the machines chosen in the world (a click does too). The orders none of
 * them can carry out are grey with the reason. The wheel switches the group (all machines of it are chosen). Orders with points wait
 * for the clicks in the world, as before. Client only.
 */
public class RadialOrderScreen extends Screen {
    private static final List<MachineTask.Type> ORDERS = List.of(MachineTask.Type.GUARD, MachineTask.Type.PATROL, MachineTask.Type.AREA_PATROL,
            MachineTask.Type.ESCORT, MachineTask.Type.HOLD, MachineTask.Type.WORK, MachineTask.Type.TO_POST, MachineTask.Type.SHELTER,
            MachineTask.Type.STAND_DOWN);
    /** The items stand on an ellipse, wider than high, so that the words do not meet. */
    private static final int RADIUS_X = 124;
    private static final int RADIUS = 84;
    private static final int ITEM_W = 74;
    private static final int ITEM_H = 16;
    private static final int DEAD_ZONE = 22;

    public RadialOrderScreen() {
        super(Component.translatable("screen.projectrebirth.tablet_menu.title"));
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.projectrebirth.tablet_menu." + key, args);
    }

    /** The order under the mouse (by its direction from the centre), or -1 in the middle. */
    private int hovered(double mouseX, double mouseY) {
        double dx = mouseX - width / 2.0;
        double dy = mouseY - height / 2.0;
        if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) {
            return -1;
        }
        double angle = Math.atan2(dy * RADIUS_X / (double) RADIUS, dx) + Math.PI / 2;
        double step = Math.PI * 2 / ORDERS.size();
        return (int) Math.floorMod(Math.round(angle / step), ORDERS.size());
    }

    /** Why no chosen machine can carry out this order, or null. */
    @Nullable
    private static Component unavailable(MachineTask.Type type) {
        if (TabletClient.selection().isEmpty()) {
            return tr("choose_first");
        }
        String problem = null;
        for (java.util.UUID id : TabletClient.selection()) {
            ConsoleView.Row row = TabletClient.row(id);
            if (row == null) {
                continue;
            }
            String why = OrderRules.problem(MachineKind.byId(row.kind()), row.facts().worker(), type);
            if (why == null) {
                return null;
            }
            problem = why;
        }
        return problem == null ? tr("choose_first") : Component.translatable(problem);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x50000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int cx = width / 2;
        int cy = height / 2;
        int hover = hovered(mouseX, mouseY);
        double step = Math.PI * 2 / ORDERS.size();
        // the ring
        for (int i = 0; i < 180; i += 2) {
            double angle = Math.PI * 2 * i / 180;
            int px = (int) Math.round(cx + Math.cos(angle) * (RADIUS_X - 44));
            int py = (int) Math.round(cy + Math.sin(angle) * (RADIUS - 30));
            graphics.fill(px, py, px + 1, py + 1, 0x80FFB648);
        }
        Component why = null;
        for (int i = 0; i < ORDERS.size(); i++) {
            MachineTask.Type type = ORDERS.get(i);
            double angle = i * step - Math.PI / 2;
            int x = (int) Math.round(cx + Math.cos(angle) * RADIUS_X) - ITEM_W / 2;
            int y = (int) Math.round(cy + Math.sin(angle) * RADIUS) - ITEM_H / 2;
            Component problem = unavailable(type);
            boolean on = i == hover;
            UiSprites.draw(graphics, problem != null ? UiSprites.BUTTON_DISABLED : on ? UiSprites.BUTTON_HOVER : UiSprites.BUTTON, x, y, ITEM_W, ITEM_H);
            if (on) {
                graphics.renderOutline(x - 1, y - 1, ITEM_W + 2, ITEM_H + 2, problem != null ? UiColors.TEXT_DIM : UiColors.AMBER);
                why = problem;
            }
            UiText.centred(graphics, Component.translatable(type.translationKey()), x + ITEM_W / 2, y + 4, ITEM_W - 6,
                    problem != null ? UiColors.TEXT_DIM : on ? UiColors.AMBER_LIGHT : UiColors.TEXT);
        }
        // the middle: whom the order is for
        int chosen = TabletClient.selection().size();
        Component who = TabletClient.group() >= 0 ? tr("group", TabletClient.group() + 1, chosen)
                : chosen == 0 ? tr("nobody") : TabletClient.group() == -1 ? tr("all", chosen) : tr("chosen", chosen);
        int bw = 120;
        UiPanels.screen(graphics, cx - bw / 2, cy - 14, bw, 28);
        UiText.centred(graphics, who, cx, cy - 10, bw - 8, chosen == 0 ? UiColors.AMBER_DIM : UiColors.SCREEN_TEXT);
        UiText.centred(graphics, hover < 0 ? tr("cancel") : Component.translatable(ORDERS.get(hover).translationKey()), cx, cy + 2, bw - 8,
                hover < 0 ? UiColors.TEXT_DIM : UiColors.AMBER);
        // under the ring: the reason, or what the keys do
        Component line = why != null ? tr("unavailable", why) : tr("help", TabletClient.MENU_KEY.getTranslatedKeyMessage());
        int lw = Math.min(width - 20, Math.max(200, font.width(line) + 16));
        UiPanels.plate(graphics, why != null ? UiSprites.Tone.ERROR : UiSprites.Tone.INFO, line, cx - lw / 2, cy + RADIUS + 16, lw);
    }

    private void choose(int index) {
        if (index < 0 || index >= ORDERS.size()) {
            onClose();
            return;
        }
        MachineTask.Type type = ORDERS.get(index);
        if (unavailable(type) != null) {
            UiSounds.ERROR.play();
            onClose();
            return;
        }
        UiSounds.CLICK.play();
        TabletClient.order(type, ConsoleScreen.workKind());
        onClose();
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (TabletClient.MENU_KEY.matches(keyCode, scanCode)) {
            double mouseX = minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
            double mouseY = minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
            choose(hovered(mouseX, mouseY));
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        choose(hovered(mouseX, mouseY));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        TabletClient.switchGroup(scrollY > 0 ? -1 : 1);
        return true;
    }

    /** For the layout check of development: the menu with the mouse at an angle. */
    public static int orders() {
        return ORDERS.size();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

}
