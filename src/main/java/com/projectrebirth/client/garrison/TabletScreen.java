package com.projectrebirth.client.garrison;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.projectrebirth.client.ui.UiButton;
import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiPanels;
import com.projectrebirth.client.ui.UiSprites;
import com.projectrebirth.client.ui.UiText;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.garrison.ConsoleBlockEntity;
import com.projectrebirth.garrison.ConsoleNetwork;
import com.projectrebirth.garrison.ConsoleView;
import com.projectrebirth.garrison.MachineTask;
import com.projectrebirth.garrison.TabletData;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The tactical tablet's screen (1.7.1; style A since 1.8.0): its console's machines on a sunken screen (a click chooses one), whom
 * the orders are for (the chosen machine, a group or every machine), the orders and the work. An order that needs points closes
 * the screen: the player marks them in the world. Client only.
 */
public class TabletScreen extends Screen {
    private static final int W = 300, H = 232;
    private static final int ROW = 12, ROWS = 8, LIST_Y = 38, LIST_H = ROW * ROWS + 4;
    private static final int TARGET_Y = LIST_Y + LIST_H + 4, WHOM_Y = TARGET_Y + 13, ORDERS_Y = WHOM_Y + 18, WORK_Y = ORDERS_Y + 36;
    private static final int LABEL_W = 46;
    @Nullable
    private final BlockPos console;
    @Nullable
    private ConsoleView view;
    private final ConsoleOrders.Target target = new ConsoleOrders.Target();
    private int scroll;
    private final List<UiButton> orderButtons = new ArrayList<>();

    public TabletScreen(TabletData data) {
        super(Component.translatable("item.projectrebirth.tactical_tablet"));
        this.console = data.console().map(where -> where.pos()).orElse(null);
    }

    @Nullable
    public BlockPos console() {
        return console;
    }

    public void setView(ConsoleView view) {
        this.view = view;
        scroll = Mth.clamp(scroll, 0, Math.max(0, view.rows().size() - ROWS));
        rebuildWidgets();
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.projectrebirth.tablet." + key, args);
    }

    @Override
    protected void init() {
        int x = left(), y = top();
        addRenderableWidget(UiButton.help(x + W - 37, y + 4, this, "tactical_tablet"));
        addRenderableWidget(UiButton.icon(x + W - 21, y + 4, 13, UiSprites.ICON_CLOSE, 7, Component.translatable("screen.projectrebirth.ui.close"), this::onClose));
        orderButtons.clear();
        if (console == null) {
            return;
        }
        if (view == null) {
            ConsoleOrders.send(console, ConsoleNetwork.REQUEST_VIEW, null, -1, 0, 0, true);
        }
        // whom: every machine or a group (a machine is chosen in the list)
        int cx = x + 7 + LABEL_W;
        UiButton all = new UiButton(cx, y + WHOM_Y, 36, 13, Component.translatable("screen.projectrebirth.console.all"), null, 0, UiButton.Look.CHIP,
                () -> choose(ConsoleBlockEntity.ALL)).selected(() -> target.group == ConsoleBlockEntity.ALL);
        all.setTooltip(Tooltip.create(Component.translatable("screen.projectrebirth.console.all_tip")));
        addRenderableWidget(all);
        int chipW = (W - 7 - (cx + 38 - x) - 2 * (ConsoleBlockEntity.GROUPS - 1)) / ConsoleBlockEntity.GROUPS;
        for (int g = 0; g < ConsoleBlockEntity.GROUPS; g++) {
            int group = g;
            UiButton chip = new UiButton(cx + 38 + g * (chipW + 2), y + WHOM_Y, chipW, 13, Component.literal(String.valueOf(g + 1)), null, 0,
                    UiButton.Look.CHIP, () -> choose(group)).selected(() -> target.group == group);
            chip.setTooltip(Tooltip.create(Component.translatable("screen.projectrebirth.console.group_tip", g + 1)));
            addRenderableWidget(chip);
        }
        // the orders without a work kind, two rows of four
        int ow = (W - 14 - 3 * 3) / 4;
        int i = 0;
        for (MachineTask.Type type : ConsoleOrders.ORDERS) {
            UiButton button = new UiButton(x + 7 + (i % 4) * (ow + 3), y + ORDERS_Y + (i / 4) * 17, ow, 15, Component.translatable(type.translationKey()),
                    null, 0, UiButton.Look.METAL, () -> order(type, 0));
            button.setTooltip(Tooltip.create(Component.translatable("order_tip.projectrebirth." + type.id(), RebirthConfig.TABLET_ROUTE_POINTS.getDefault())));
            orderButtons.add(addRenderableWidget(button));
            i++;
        }
        // the work, with its kind
        int ww = (W - 7 - (7 + LABEL_W) - 3 * (MachineTask.WORKS.size() - 1)) / MachineTask.WORKS.size();
        for (int w = 0; w < MachineTask.WORKS.size(); w++) {
            int work = w;
            String kind = MachineTask.WORKS.get(w);
            UiButton button = new UiButton(cx + w * (ww + 3), y + WORK_Y, ww, 15, Component.translatable("work_short.projectrebirth." + kind),
                    null, 0, UiButton.Look.METAL, () -> order(MachineTask.Type.WORK, work));
            button.setTooltip(Tooltip.create(Component.translatable("order_tip.projectrebirth.work", Component.translatable("work_kind.projectrebirth." + kind))));
            orderButtons.add(addRenderableWidget(button));
        }
        refreshOrders();
    }

    /** The orders are grey until someone is chosen (the line above the chips says so). */
    private void refreshOrders() {
        for (UiButton button : orderButtons) {
            button.active = target.chosen();
        }
    }

    private void choose(int group) {
        target.group = group;
        refreshOrders();
    }

    private void order(MachineTask.Type type, int work) {
        if (console == null || !target.chosen()) {
            return;
        }
        ConsoleOrders.send(console, ConsoleNetwork.ORDER, target.group < 0 && target.group != ConsoleBlockEntity.ALL ? target.machine : null,
                target.group, type.ordinal(), work, true);
        // an order with points closes the screen so that the player can mark them in the world
        if (type == MachineTask.Type.WORK || ConsoleNetwork.needsPoints(type)) {
            onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (view != null) {
            int mx = (int) mouseX - left(), my = (int) mouseY - top();
            if (mx >= 9 && mx < W - 9 && my >= LIST_Y + 2 && my < LIST_Y + 2 + ROW * ROWS) {
                int index = scroll + (my - LIST_Y - 2) / ROW;
                if (index < view.rows().size()) {
                    target.machine = view.rows().get(index).id();
                    target.group = -1;
                    refreshOrders();
                    com.projectrebirth.client.ui.UiSounds.CLICK.play();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (view != null) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, Math.max(0, view.rows().size() - ROWS));
        }
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        int x = left(), y = top();
        UiPanels.window(graphics, x, y, W, H);
        UiPanels.title(graphics, x, y, W, title, null, x + W - 40);
        if (console != null) {
            UiPanels.screen(graphics, x + 7, y + LIST_Y, W - 14, LIST_H);
            UiPanels.divider(graphics, x + 7, y + TARGET_Y - 3, W - 14);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = left(), y = top();
        if (console == null) {
            UiPanels.plate(graphics, UiSprites.Tone.WARNING, tr("no_console"), x + 7, y + 26, W - 14);
            return;
        }
        if (view == null) {
            UiText.line(graphics, Component.translatable("screen.projectrebirth.console.waiting"), x + 11, y + LIST_Y + 4, W - 22, UiColors.SCREEN_TEXT);
            return;
        }
        if (view.powered()) {
            UiText.line(graphics, UiText.upper(tr("status", console.getX(), console.getY(), console.getZ(), view.rows().size())),
                    x + 8, y + 25, W - 16, UiColors.AMBER);
        } else {
            UiPanels.plate(graphics, UiSprites.Tone.ERROR, Component.translatable("screen.projectrebirth.console.no_energy"), x + 7, y + 22, W - 14);
        }
        renderRows(graphics, x, y, mouseX, mouseY);
        Component whom = ConsoleOrders.targetLabel(target, view);
        UiText.line(graphics, whom, x + 8, y + TARGET_Y, W - 16, target.chosen() ? UiColors.AMBER_LIGHT : UiColors.TEXT_DIM);
        UiPanels.label(graphics, tr("whom"), x + 8, y + WHOM_Y + 3, LABEL_W - 4);
        UiPanels.label(graphics, Component.translatable("screen.projectrebirth.console.work"), x + 8, y + WORK_Y + 4, LABEL_W - 4);
        // the colour of each group under its chip, as on the console's tabs
        int cx = x + 7 + LABEL_W + 38;
        int chipW = (W - 7 - (LABEL_W + 7 + 38) - 2 * (ConsoleBlockEntity.GROUPS - 1)) / ConsoleBlockEntity.GROUPS;
        for (int g = 0; g < ConsoleBlockEntity.GROUPS; g++) {
            int gx = cx + g * (chipW + 2);
            graphics.fill(gx + 2, y + WHOM_Y + 11, gx + chipW - 2, y + WHOM_Y + 12, 0xFF000000 | ConsoleScreen.GROUP_COLORS[g]);
        }
    }

    private void renderRows(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        List<ConsoleView.Row> rows = view.rows();
        int lx = x + 9, ly = y + LIST_Y + 2, lw = W - 18;
        if (rows.isEmpty()) {
            UiText.wrapped(graphics, Component.translatable("screen.projectrebirth.console.no_machines"), lx + 3, ly + 3, lw - 6, LIST_H - 8, UiColors.SCREEN_TEXT);
            return;
        }
        boolean scrolls = rows.size() > ROWS;
        int rowW = scrolls ? lw - 5 : lw;
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            ConsoleView.Row row = rows.get(scroll + i);
            int ry = ly + i * ROW;
            boolean chosen = row.id().equals(target.machine) && target.group == -1
                    || target.group >= 0 && row.group() == target.group || target.group == ConsoleBlockEntity.ALL;
            boolean hovered = mouseX >= lx && mouseX < lx + rowW && mouseY >= ry && mouseY < ry + ROW;
            if (chosen) {
                graphics.fill(lx, ry, lx + rowW, ry + ROW, UiColors.AMBER_BACK);
                graphics.fill(lx, ry, lx + 1, ry + ROW, UiColors.AMBER);
            } else if (hovered) {
                graphics.fill(lx, ry, lx + rowW, ry + ROW, 0x18FFFFFF);
            }
            graphics.fill(lx + 4, ry + 4, lx + 8, ry + 8, ConsoleOrders.stateColor(row.state()));
            if (row.group() >= 0) {
                int color = 0xFF000000 | ConsoleScreen.GROUP_COLORS[row.group()];
                graphics.fill(lx + 11, ry + 2, lx + 13, ry + 10, color);
                graphics.drawString(font, String.valueOf(row.group() + 1), lx + 15, ry + 2, color, false);
            }
            UiText.line(graphics, ConsoleBlockEntity.display(row.name()), lx + 24, ry + 2, 140, chosen ? UiColors.AMBER_LIGHT : UiColors.TEXT);
            UiText.line(graphics, ConsoleOrders.taskName(row.task()), lx + 170, ry + 2, rowW - 186, UiColors.TEXT_SECONDARY);
            // a machine the console has not heard for a while is out of its reach: it gets its orders when it is back
            boolean silent = row.heardSeconds() < 0 || row.heardSeconds() > 3;
            graphics.drawString(font, silent ? "⚠" : "•", lx + rowW - 9, ry + 2, silent ? UiColors.WARNING : UiColors.OK, false);
        }
        if (scrolls) {
            int trackH = ROW * ROWS;
            int thumbH = Math.max(8, trackH * ROWS / rows.size());
            int thumbY = ly + (trackH - thumbH) * scroll / Math.max(1, rows.size() - ROWS);
            graphics.fill(lx + lw - 3, ly, lx + lw - 1, ly + trackH, 0xFF1F2B23);
            graphics.fill(lx + lw - 3, thumbY, lx + lw - 1, thumbY + thumbH, UiColors.AMBER_DIM);
        }
    }
}
