package com.projectrebirth.client.garrison;

import java.util.List;

import javax.annotation.Nullable;

import com.projectrebirth.client.GuiAtlas;
import com.projectrebirth.garrison.ConsoleNetwork;
import com.projectrebirth.garrison.ConsoleView;
import com.projectrebirth.garrison.TabletData;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The tactical tablet's screen (1.7.0, block D): the short list of its console's machines and the quick orders, with the marks the
 * tablet holds (the point to guard, the route, the work area and its chest). Client only.
 */
public class TabletScreen extends Screen {
    private static final int W = 248, H = 200, ROW = 11, ROWS = 7, LIST_Y = 34;
    @Nullable
    private final BlockPos console;
    private final TabletData data;
    @Nullable
    private ConsoleView view;
    private final ConsoleOrders.Target target = new ConsoleOrders.Target();
    private int scroll;

    public TabletScreen(TabletData data) {
        super(Component.translatable("item.projectrebirth.tactical_tablet"));
        this.data = data;
        this.console = data.console().map(where -> where.pos()).orElse(null);
    }

    @Nullable
    public BlockPos console() {
        return console;
    }

    public void setView(ConsoleView view) {
        this.view = view;
        rebuildWidgets();
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    @Override
    protected void init() {
        if (console == null) {
            return;
        }
        if (view == null) {
            ConsoleOrders.send(console, ConsoleNetwork.REQUEST_VIEW, null, -1, 0, 0, true);
        }
        addRenderableWidget(ConsoleOrders.targetButton(left() + 8, top() + 116, W - 16, target, view, this::rebuildWidgets));
        ConsoleOrders.orderButtons(left() + 8, top() + 134, 56, 4, console, target, true, this::addRenderableWidget, this::rebuildWidgets);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (view != null) {
            int mx = (int) mouseX - left(), my = (int) mouseY - top();
            if (mx >= 8 && mx < W - 8 && my >= LIST_Y && my < LIST_Y + ROW * ROWS) {
                int index = scroll + (my - LIST_Y) / ROW;
                if (index < view.rows().size()) {
                    target.machine = view.rows().get(index).id();
                    target.group = -1;
                    rebuildWidgets();
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = left(), y = top();
        graphics.drawString(font, title, x + 8, y + 6, 0x404040, false);
        if (console == null) {
            graphics.drawWordWrap(font, Component.translatable("screen.projectrebirth.tablet.no_console"), x + 8, y + 24, W - 16, 0x404040);
            return;
        }
        Component marks = Component.translatable("screen.projectrebirth.tablet.marks", Component.translatable("tablet_mode.projectrebirth." + data.mode()),
                data.guard().isPresent() ? "✔" : "—", data.route().size(), data.areaFrom().isPresent() && data.areaTo().isPresent() ? "✔" : "—",
                data.chest().isPresent() ? "✔" : "—");
        graphics.drawString(font, marks, x + 8, y + 18, 0x404040, false);
        GuiAtlas.recess(graphics, x + 7, y + LIST_Y - 1, W - 14, ROW * ROWS + 2);
        if (view == null) {
            graphics.drawString(font, Component.translatable("screen.projectrebirth.console.waiting"), x + 10, y + LIST_Y + 2, 0xFFDDDDDD, false);
            return;
        }
        List<ConsoleView.Row> rows = view.rows();
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            ConsoleView.Row row = rows.get(scroll + i);
            int ry = y + LIST_Y + i * ROW;
            if (row.id().equals(target.machine) && target.group < 0) {
                graphics.fill(x + 8, ry, x + W - 8, ry + ROW, 0x6037C6E8);
            }
            graphics.fill(x + 10, ry + 3, x + 15, ry + 8, ConsoleOrders.stateColor(row.state()));
            graphics.drawString(font, row.group() < 0 ? " " : String.valueOf(row.group() + 1), x + 18, ry + 2, 0xFFE0B33A, false);
            graphics.drawString(font, font.plainSubstrByWidth(row.name(), 90), x + 26, ry + 2, 0xFFEEEEEE, false);
            graphics.drawString(font, ConsoleOrders.taskName(row.task()), x + 122, ry + 2, 0xFFAAAAAA, false);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        GuiAtlas.panel(graphics, left(), top(), W, H);
    }
}
