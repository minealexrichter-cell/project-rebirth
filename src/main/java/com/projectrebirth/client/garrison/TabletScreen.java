package com.projectrebirth.client.garrison;

import java.util.List;

import javax.annotation.Nullable;

import com.projectrebirth.client.GuiAtlas;
import com.projectrebirth.garrison.ConsoleNetwork;
import com.projectrebirth.garrison.ConsoleView;
import com.projectrebirth.garrison.TabletData;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The tactical tablet's screen (1.7.1): its console's machines (a click chooses one), whom the orders are for (the chosen machine,
 * a group or every machine), the orders and the work. An order that needs points closes the screen: the player marks them in the
 * world. Client only.
 */
public class TabletScreen extends Screen {
    private static final int W = 256, H = 214, ROW = 11, ROWS = 8, LIST_Y = 30, PANEL_Y = 136;
    @Nullable
    private final BlockPos console;
    @Nullable
    private ConsoleView view;
    private final ConsoleOrders.Target target = new ConsoleOrders.Target();
    private int scroll;

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
        ConsoleOrders.panel(left() + 8, top() + PANEL_Y, console, target, true, this::addRenderableWidget, this::rebuildWidgets, this::onClose);
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
        GuiAtlas.recess(graphics, x + 7, y + LIST_Y - 1, W - 14, ROW * ROWS + 2);
        if (view == null) {
            graphics.drawString(font, Component.translatable("screen.projectrebirth.console.waiting"), x + 10, y + LIST_Y + 2, 0xFFDDDDDD, false);
            return;
        }
        Component status = view.powered()
                ? Component.translatable("screen.projectrebirth.tablet.status", console.getX(), console.getY(), console.getZ(), view.rows().size())
                : Component.translatable("screen.projectrebirth.console.no_energy").withStyle(ChatFormatting.DARK_RED);
        graphics.drawString(font, font.substrByWidth(status, W - 16).getString(), x + 8, y + 18, view.powered() ? 0x404040 : 0x8A1A1A, false);
        List<ConsoleView.Row> rows = view.rows();
        if (rows.isEmpty()) {
            graphics.drawWordWrap(font, Component.translatable("screen.projectrebirth.console.no_machines"), x + 10, y + LIST_Y + 2, W - 20, 0xFFDDDDDD);
        }
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            ConsoleView.Row row = rows.get(scroll + i);
            int ry = y + LIST_Y + i * ROW;
            boolean chosen = row.id().equals(target.machine) && target.group == -1
                    || target.group >= 0 && row.group() == target.group || target.group == com.projectrebirth.garrison.ConsoleBlockEntity.ALL;
            if (chosen) {
                graphics.fill(x + 8, ry, x + W - 8, ry + ROW, 0x6037C6E8);
            }
            graphics.fill(x + 10, ry + 3, x + 15, ry + 8, ConsoleOrders.stateColor(row.state()));
            graphics.drawString(font, row.group() < 0 ? " " : String.valueOf(row.group() + 1), x + 18, ry + 2, 0xFFE0B33A, false);
            graphics.drawString(font, font.plainSubstrByWidth(row.name(), 104), x + 26, ry + 2, 0xFFEEEEEE, false);
            Component task = ConsoleOrders.taskName(row.task());
            graphics.drawString(font, font.substrByWidth(task, 72).getString(), x + 134, ry + 2, 0xFFAAAAAA, false);
            // a machine the console has not heard for a while is out of its reach: it gets its orders when it is back
            boolean silent = row.heardSeconds() < 0 || row.heardSeconds() > 3;
            graphics.drawString(font, silent ? "⚠" : "•", x + W - 18, ry + 2, silent ? 0xFFE0A030 : 0xFF5FBF3A, false);
        }
        Component whom = ConsoleOrders.targetLabel(target, view);
        graphics.drawString(font, font.substrByWidth(whom, W - 16).getString(), x + 8, y + PANEL_Y - 11, 0x202020, false);
        ConsoleOrders.labels(graphics, font, x + 8, y + PANEL_Y, 0x404040);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        GuiAtlas.panel(graphics, left(), top(), W, H);
    }
}
