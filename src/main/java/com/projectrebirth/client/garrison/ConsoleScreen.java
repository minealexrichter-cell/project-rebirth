package com.projectrebirth.client.garrison;

import java.util.List;

import com.projectrebirth.client.ContaminationMapView;
import com.projectrebirth.client.GuiAtlas;
import com.projectrebirth.garrison.ConsoleAlarm;
import com.projectrebirth.garrison.ConsoleBlockEntity;
import com.projectrebirth.garrison.ConsoleMenu;
import com.projectrebirth.garrison.ConsoleNetwork;
import com.projectrebirth.garrison.ConsoleView;
import com.projectrebirth.monitoring.Snapshot;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * The console's window (1.7.0, block D), four pages: the machines (list, the chosen one, its group, orders), the map (the machines
 * around the console, over the contamination of a monitoring station near), the rules (the intruder mode of the console and of each
 * group, the weather link) and the alarm log. Every button goes to the server, which answers with a fresh view. Client only.
 */
public class ConsoleScreen extends AbstractContainerScreen<ConsoleMenu> {
    private static final int MACHINES = 0, MAP = 1, RULES = 2, LOG = 3;
    private static final int LIST_X = 8, LIST_Y = 40, ROW = 11, ROWS = 9, LIST_W = 140, PANEL_Y = 156;
    private static int page = MACHINES;
    private final ConsoleOrders.Target target = new ConsoleOrders.Target();
    private int scroll;

    public ConsoleScreen(ConsoleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 256;
        imageHeight = 234;
    }

    /** A fresh view came: buttons whose words depend on it are made again. */
    public void viewChanged() {
        rebuildWidgets();
    }

    @Override
    protected void init() {
        super.init();
        String[] names = {"machines", "map", "rules", "log"};
        for (int i = 0; i < names.length; i++) {
            int tab = i;
            Button button = Button.builder(Component.translatable("screen.projectrebirth.console.page." + names[i]), b -> {
                page = tab;
                rebuildWidgets();
            }).bounds(leftPos + 6 + i * 61, topPos + 16, 59, 16).build();
            button.active = page != i;
            addRenderableWidget(button);
        }
        ConsoleView view = menu.view();
        switch (page) {
            case MACHINES -> machineButtons(view);
            case RULES -> ruleButtons(view);
            default -> {
            }
        }
    }

    private void machineButtons(ConsoleView view) {
        int x = leftPos + 154;
        Button group = Button.builder(groupLabel(view), b -> {
            ConsoleView.Row row = chosen(view);
            if (row != null) {
                int next = row.group() + 1 >= ConsoleBlockEntity.GROUPS ? -1 : row.group() + 1;
                ConsoleOrders.send(menu.pos(), ConsoleNetwork.SET_GROUP, row.id(), -1, next, 0, false);
            }
        }).bounds(x, topPos + 102, 94, 16).build();
        group.active = chosen(view) != null;
        addRenderableWidget(group);
        Button unlink = Button.builder(Component.translatable("screen.projectrebirth.console.unlink"), b -> {
            ConsoleView.Row row = chosen(view);
            if (row != null) {
                ConsoleOrders.send(menu.pos(), ConsoleNetwork.UNLINK, row.id(), -1, 0, 0, false);
                target.machine = null;
            }
        }).bounds(x, topPos + 120, 94, 16).build();
        unlink.active = chosen(view) != null;
        addRenderableWidget(unlink);
        ConsoleOrders.panel(leftPos + 8, topPos + PANEL_Y, menu.pos(), target, false, this::addRenderableWidget, this::rebuildWidgets, this::pointOrder);
    }

    /**
     * An order that needs points went to the player's tablet (1.7.1): with a tablet linked to this console the window closes, so
     * that the points can be clicked in the world; without one the server says that a tablet is needed.
     */
    private void pointOrder() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        for (net.minecraft.world.item.ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof com.projectrebirth.garrison.TabletItem && com.projectrebirth.garrison.TabletItem.data(stack).console()
                    .filter(where -> where.pos().equals(menu.pos())).isPresent()) {
                onClose();
                return;
            }
        }
        if (player.getOffhandItem().getItem() instanceof com.projectrebirth.garrison.TabletItem && com.projectrebirth.garrison.TabletItem
                .data(player.getOffhandItem()).console().filter(where -> where.pos().equals(menu.pos())).isPresent()) {
            onClose();
        }
    }

    private Component groupLabel(ConsoleView view) {
        ConsoleView.Row row = chosen(view);
        int group = row == null ? -1 : row.group();
        return group < 0 ? Component.translatable("screen.projectrebirth.console.no_group")
                : Component.translatable("screen.projectrebirth.console.group", group + 1);
    }

    private void ruleButtons(ConsoleView view) {
        boolean intruders = view != null && view.intruderMode();
        Button console = Button.builder(Component.translatable("screen.projectrebirth.console.intruders", onOff(intruders)),
                b -> ConsoleOrders.send(menu.pos(), ConsoleNetwork.CONSOLE_INTRUDERS, null, -1, intruders ? 0 : 1, 0, false))
                .bounds(leftPos + 8, topPos + 40, 240, 16).build();
        console.active = view != null && (view.intruderAllowed() || intruders);
        addRenderableWidget(console);
        boolean weather = view != null && view.weatherLink();
        addRenderableWidget(Button.builder(Component.translatable("screen.projectrebirth.console.weather", onOff(weather)),
                b -> ConsoleOrders.send(menu.pos(), ConsoleNetwork.WEATHER, null, -1, weather ? 0 : 1, 0, false))
                .bounds(leftPos + 8, topPos + 58, 240, 16).build());
        for (int g = 0; g < ConsoleBlockEntity.GROUPS; g++) {
            int group = g;
            ConsoleView.Group data = view == null ? new ConsoleView.Group(0, false) : view.groups().get(g);
            Button button = Button.builder(Component.translatable("screen.projectrebirth.console.group_intruders", g + 1, data.members(), onOff(data.intruders())),
                    b -> ConsoleOrders.send(menu.pos(), ConsoleNetwork.GROUP_INTRUDERS, null, group, data.intruders() ? 0 : 1, 0, false))
                    .bounds(leftPos + 8 + (g % 2) * 122, topPos + 96 + (g / 2) * 18, 118, 16).build();
            button.active = view != null && (view.intruderAllowed() || data.intruders());
            addRenderableWidget(button);
        }
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "screen.projectrebirth.console.on" : "screen.projectrebirth.console.off");
    }

    private ConsoleView.Row chosen(ConsoleView view) {
        if (view == null || target.machine == null) {
            return null;
        }
        for (ConsoleView.Row row : view.rows()) {
            if (row.id().equals(target.machine)) {
                return row;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ConsoleView view = menu.view();
        if (page == MACHINES && view != null) {
            int mx = (int) mouseX - leftPos, my = (int) mouseY - topPos;
            if (mx >= LIST_X && mx < LIST_X + LIST_W && my >= LIST_Y && my < LIST_Y + ROW * ROWS) {
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
        ConsoleView view = menu.view();
        if (page == MACHINES && view != null) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, Math.max(0, view.rows().size() - ROWS));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        GuiAtlas.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 5, 0x404040, false);
        ConsoleView view = menu.view();
        if (view == null) {
            graphics.drawString(font, Component.translatable("screen.projectrebirth.console.waiting"), 8, 40, 0x404040, false);
            return;
        }
        Component status = view.powered() ? Component.translatable("screen.projectrebirth.console.powered", view.rows().size())
                : Component.translatable("screen.projectrebirth.console.no_energy").withStyle(ChatFormatting.DARK_RED);
        graphics.drawString(font, status, imageWidth - 8 - font.width(status), 5, 0x404040, false);
        GuiAtlas.energyHorizontal(graphics, 152, 34, 96, 4, view.energy(), view.capacity());
        switch (page) {
            case MACHINES -> machines(graphics, view);
            case MAP -> map(graphics, view, mouseX - leftPos, mouseY - topPos);
            case RULES -> rules(graphics, view);
            case LOG -> log(graphics, view);
            default -> {
            }
        }
    }

    private void machines(GuiGraphics graphics, ConsoleView view) {
        GuiAtlas.recess(graphics, LIST_X - 1, LIST_Y - 1, LIST_W + 2, ROW * ROWS + 2);
        List<ConsoleView.Row> rows = view.rows();
        if (rows.isEmpty()) {
            graphics.drawWordWrap(font, Component.translatable("screen.projectrebirth.console.no_machines"), LIST_X + 2, LIST_Y + 2, LIST_W - 4, 0xFFDDDDDD);
        }
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            ConsoleView.Row row = rows.get(scroll + i);
            int y = LIST_Y + i * ROW;
            if (row.id().equals(target.machine) && target.group == -1 || target.group >= 0 && row.group() == target.group
                    || target.group == ConsoleBlockEntity.ALL) {
                graphics.fill(LIST_X, y, LIST_X + LIST_W, y + ROW, 0x6037C6E8);
            }
            graphics.fill(LIST_X + 2, y + 3, LIST_X + 7, y + 8, ConsoleOrders.stateColor(row.state()));
            String group = row.group() < 0 ? " " : String.valueOf(row.group() + 1);
            graphics.drawString(font, group, LIST_X + 10, y + 2, 0xFFE0B33A, false);
            graphics.drawString(font, font.substrByWidth(com.projectrebirth.garrison.ConsoleBlockEntity.display(row.name()), 70).getString(), LIST_X + 18, y + 2, 0xFFEEEEEE, false);
            Component task = ConsoleOrders.taskName(row.task());
            graphics.drawString(font, font.substrByWidth(task, 50).getString(), LIST_X + LIST_W - 52, y + 2, 0xFFAAAAAA, false);
        }
        Component whom = ConsoleOrders.targetLabel(target, view);
        graphics.drawString(font, font.substrByWidth(whom, 240).getString(), 8, PANEL_Y - 11, 0x202020, false);
        ConsoleOrders.labels(graphics, font, 8, PANEL_Y, 0x404040);
        ConsoleView.Row row = chosen(view);
        int x = 154;
        if (row == null) {
            graphics.drawWordWrap(font, Component.translatable("screen.projectrebirth.console.choose"), x, 42, 94, 0x404040);
            return;
        }
        graphics.drawString(font, font.substrByWidth(com.projectrebirth.garrison.ConsoleBlockEntity.display(row.name()), 94).getString(), x, 42, 0x202020, false);
        graphics.drawString(font, ConsoleOrders.stateName(row.state()), x, 52, ConsoleOrders.stateColor(row.state()), false);
        graphics.drawString(font, Mth.ceil(row.structural()) + "/" + Mth.ceil(row.maxStructural()) + "  " + Mth.ceil(row.critical()) + "/"
                + Mth.ceil(row.maxCritical()), x, 62, 0x404040, false);
        graphics.drawString(font, row.x() + " " + row.y() + " " + row.z(), x, 72, 0x404040, false);
        graphics.drawString(font, ConsoleOrders.heard(row.heardSeconds()), x, 82, 0x404040, false);
        graphics.drawString(font, ConsoleOrders.taskName(row.task()), x, 92, 0x404040, false);
    }

    private void map(GuiGraphics graphics, ConsoleView view, int mouseX, int mouseY) {
        int size = 132, x0 = 8, y0 = 40;
        int cx = menu.pos().getX(), cz = menu.pos().getZ();
        double blocksPerPixel;
        int originX, originZ;
        if (view.map().isPresent()) {
            Snapshot snapshot = view.map().get();
            int side = 2 * snapshot.radius() + 1;
            int cell = Math.max(1, size / side);
            ContaminationMapView.draw(graphics, snapshot, x0, y0, cell, mouseX, mouseY);
            blocksPerPixel = 16.0 / cell;
            originX = (snapshot.centerX() - snapshot.radius()) * 16;
            originZ = (snapshot.centerZ() - snapshot.radius()) * 16;
            size = cell * side;
        } else {
            graphics.fill(x0, y0, x0 + size, y0 + size, 0xFF20262C);
            // zoomed to the farthest machine (at least 32 blocks each way, at most the console's range), a line every 16 blocks
            int span = 32;
            for (ConsoleView.Row row : view.rows()) {
                span = Math.max(span, Math.max(Math.abs(row.x() - cx), Math.abs(row.z() - cz)) + 8);
            }
            span = Math.min(span, view.range());
            blocksPerPixel = 2.0 * span / size;
            originX = cx - span;
            originZ = cz - span;
            for (int line = -span + Math.floorMod(-(cx - span), 16); line <= span; line += 16) {
                int px = x0 + (int) ((line + span) / blocksPerPixel);
                graphics.fill(px, y0, px + 1, y0 + size, 0xFF2C343C);
            }
            for (int line = -span + Math.floorMod(-(cz - span), 16); line <= span; line += 16) {
                int pz = y0 + (int) ((line + span) / blocksPerPixel);
                graphics.fill(x0, pz, x0 + size, pz + 1, 0xFF2C343C);
            }
            graphics.drawString(font, Component.translatable("screen.projectrebirth.console.map_span", 2 * span), x0 + 2, y0 + size - 9, 0xFF8A949E, false);
        }
        dot(graphics, x0, y0, size, (cx - originX) / blocksPerPixel, (cz - originZ) / blocksPerPixel, 0xFFFFFFFF, 3);
        for (ConsoleView.Row row : view.rows()) {
            int color = ConsoleOrders.stateColor(row.state());
            if (row.id().equals(target.machine)) {
                dot(graphics, x0, y0, size, (row.x() - originX) / blocksPerPixel, (row.z() - originZ) / blocksPerPixel, 0xFF37C6E8, 4);
            }
            dot(graphics, x0, y0, size, (row.x() - originX) / blocksPerPixel, (row.z() - originZ) / blocksPerPixel, color, 2);
        }
        int lx = 148;
        graphics.drawString(font, Component.translatable("screen.projectrebirth.console.map_console"), lx + 8, 42, 0x404040, false);
        graphics.fill(lx, 43, lx + 4, 47, 0xFFFFFFFF);
        for (int state = 0; state < 3; state++) {
            graphics.fill(lx, 55 + state * 10, lx + 4, 59 + state * 10, ConsoleOrders.stateColor(state));
            graphics.drawString(font, ConsoleOrders.stateName(state), lx + 8, 54 + state * 10, 0x404040, false);
        }
        Component layer = view.map().isPresent() ? Component.translatable("screen.projectrebirth.console.map_layer")
                : Component.translatable("screen.projectrebirth.console.map_no_layer");
        graphics.drawWordWrap(font, layer, lx, 92, 100, 0x404040);
    }

    private static void dot(GuiGraphics graphics, int x0, int y0, int size, double px, double pz, int color, int radius) {
        int x = x0 + (int) px, y = y0 + (int) pz;
        if (x < x0 || y < y0 || x >= x0 + size || y >= y0 + size) {
            return;
        }
        graphics.fill(x - radius / 2, y - radius / 2, x - radius / 2 + radius, y - radius / 2 + radius, color);
    }

    private void rules(GuiGraphics graphics, ConsoleView view) {
        if (!view.intruderAllowed()) {
            graphics.drawString(font, Component.translatable("screen.projectrebirth.console.intruders_forbidden"), 8, 78, 0xFF8A1A1A, false);
        } else if (view.sheltering()) {
            graphics.drawString(font, Component.translatable("screen.projectrebirth.console.sheltering"), 8, 78, 0xFF1A4A8A, false);
        } else {
            graphics.drawString(font, Component.translatable("screen.projectrebirth.console.group_rules"), 8, 82, 0x404040, false);
        }
    }

    private void log(GuiGraphics graphics, ConsoleView view) {
        GuiAtlas.recess(graphics, 7, 39, 242, 180);
        if (view.log().isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.projectrebirth.console.log_empty"), 10, 42, 0xFFDDDDDD, false);
        }
        int y = 42;
        for (ConsoleView.Line line : view.log()) {
            if (y > 210) {
                break;
            }
            ConsoleAlarm alarm = ConsoleAlarm.byId(line.alarm());
            Component text = Component.translatable("screen.projectrebirth.console.log_line", ConsoleOrders.ago(line.secondsAgo()),
                    Component.translatable("console_alarm.projectrebirth." + alarm.id() + (line.ended() ? "_ended" : "")), com.projectrebirth.garrison.ConsoleBlockEntity.display(line.name()),
                    line.x() + " " + line.y() + " " + line.z());
            graphics.drawString(font, font.substrByWidth(text, 236).getString(), 10, y, line.ended() ? 0xFF7FD07F : 0xFFFF7A6A, false);
            y += 10;
        }
    }
}
