package com.projectrebirth.client.garrison;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.projectrebirth.garrison.ConsoleBlockEntity;
import com.projectrebirth.garrison.ConsoleNetwork;
import com.projectrebirth.garrison.ConsoleView;
import com.projectrebirth.garrison.MachineCore;
import com.projectrebirth.garrison.MachineTask;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What the console's window and the tablet's screen share (1.7.0, block D): whom an order is for (the chosen machine or a group),
 * the order buttons, and the words for states and tasks. Client only.
 */
public final class ConsoleOrders {
    /** The orders without a work kind, on two rows of buttons (1.7.1). */
    public static final List<MachineTask.Type> ORDERS = List.of(MachineTask.Type.GUARD, MachineTask.Type.PATROL, MachineTask.Type.ESCORT,
            MachineTask.Type.HOLD, MachineTask.Type.TO_POST, MachineTask.Type.SHELTER, MachineTask.Type.STAND_DOWN);
    /** The panel's width and height. */
    public static final int PANEL_W = 240, PANEL_H = 72;

    /** Whom the orders go to; kept while the screen is open. */
    public static final class Target {
        @Nullable
        public UUID machine;
        /** -1: the chosen machine; 0..7: a group; ConsoleBlockEntity.ALL: every machine. */
        public int group = -1;

        public boolean chosen() {
            return group != -1 || machine != null;
        }
    }

    private ConsoleOrders() {
    }

    public static void send(BlockPos console, int action, @Nullable UUID machine, int group, int value, int extra, boolean fromTablet) {
        PacketDistributor.sendToServer(new ConsoleNetwork.ActionPayload(console, action, Optional.ofNullable(machine), group, value, extra, fromTablet));
    }

    public static Component targetLabel(Target target, @Nullable ConsoleView view) {
        if (target.group == ConsoleBlockEntity.ALL) {
            return Component.translatable("screen.projectrebirth.console.target_all", view == null ? 0 : view.rows().size());
        }
        if (target.group >= 0) {
            int members = view == null ? 0 : view.groups().get(target.group).members();
            return Component.translatable("screen.projectrebirth.console.target_group", target.group + 1, members);
        }
        if (view != null && target.machine != null) {
            for (ConsoleView.Row row : view.rows()) {
                if (row.id().equals(target.machine)) {
                    return Component.translatable("screen.projectrebirth.console.target_machine", row.name());
                }
            }
        }
        return Component.translatable("screen.projectrebirth.console.target_none");
    }

    /** The panel's words that are not on buttons (the screen draws them, with its own colour): the label of the work row. */
    public static void labels(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.Font font, int x, int y, int color) {
        graphics.drawString(font, Component.translatable("screen.projectrebirth.console.work"), x, y + 60, color, false);
    }

    /**
     * The order panel (1.7.1), {@link #PANEL_W} × {@link #PANEL_H} at (x, y): whom (every machine or a group; a machine is chosen
     * in the list), the orders, and the work with its kind. An order that needs points goes to the tablet; then {@code pointOrder}
     * runs (the screen closes so that the player can click the points in the world).
     */
    public static void panel(int x, int y, BlockPos console, Target target, boolean fromTablet, Consumer<AbstractWidget> add, Runnable changed,
                             Runnable pointOrder) {
        Button all = Button.builder(Component.translatable("screen.projectrebirth.console.all"), b -> {
            target.group = ConsoleBlockEntity.ALL;
            changed.run();
        }).bounds(x, y, 40, 16).tooltip(Tooltip.create(Component.translatable("screen.projectrebirth.console.all_tip"))).build();
        all.active = target.group != ConsoleBlockEntity.ALL;
        add.accept(all);
        for (int g = 0; g < ConsoleBlockEntity.GROUPS; g++) {
            int group = g;
            Button button = Button.builder(Component.literal(String.valueOf(g + 1)), b -> {
                target.group = group;
                changed.run();
            }).bounds(x + 42 + g * 25, y, 23, 16).tooltip(Tooltip.create(Component.translatable("screen.projectrebirth.console.group_tip", g + 1))).build();
            button.active = target.group != g;
            add.accept(button);
        }
        int i = 0;
        for (MachineTask.Type type : ORDERS) {
            int bx = x + (i % 4) * 60, by = y + 20 + (i / 4) * 18;
            Button button = Button.builder(Component.translatable(type.translationKey()), b -> {
                send(console, ConsoleNetwork.ORDER, target.group < 0 && target.group != ConsoleBlockEntity.ALL ? target.machine : null,
                        target.group, type.ordinal(), 0, fromTablet);
                if (ConsoleNetwork.needsPoints(type)) {
                    pointOrder.run();
                }
            }).bounds(bx, by, 58, 16).tooltip(Tooltip.create(Component.translatable("order_tip.projectrebirth." + type.id(),
                    com.projectrebirth.config.RebirthConfig.TABLET_ROUTE_POINTS.getDefault()))).build();
            button.active = target.chosen();
            add.accept(button);
            i++;
        }
        int wy = y + 56;
        for (int w = 0; w < MachineTask.WORKS.size(); w++) {
            int work = w;
            String kind = MachineTask.WORKS.get(w);
            Button button = Button.builder(Component.translatable("work_short.projectrebirth." + kind), b -> {
                send(console, ConsoleNetwork.ORDER, target.group < 0 && target.group != ConsoleBlockEntity.ALL ? target.machine : null,
                        target.group, MachineTask.Type.WORK.ordinal(), work, fromTablet);
                pointOrder.run();
            }).bounds(x + 46 + w * 49, wy, 47, 16).tooltip(Tooltip.create(Component.translatable("order_tip.projectrebirth.work",
                    Component.translatable("work_kind.projectrebirth." + kind)))).build();
            button.active = target.chosen();
            add.accept(button);
        }
    }

    public static int stateColor(int state) {
        return switch (MachineCore.State.byId(state)) {
            case ACTIVE -> 0xFF5FBF3A;
            case DISABLED -> 0xFFE0A030;
            case WRECK -> 0xFFD0352A;
        };
    }

    public static Component stateName(int state) {
        return Component.translatable("machine_state.projectrebirth." + MachineCore.State.byId(state).name().toLowerCase(java.util.Locale.ROOT));
    }

    public static Component taskName(int task) {
        return Component.translatable(MachineTask.Type.byId(task).translationKey());
    }

    /** How long ago, for the log. */
    public static Component ago(int seconds) {
        return Component.translatable("screen.projectrebirth.console.ago", seconds < 120 ? seconds + "s" : (seconds / 60) + "m");
    }

    public static Component heard(int seconds) {
        if (seconds < 0) {
            return Component.translatable("screen.projectrebirth.console.never_heard").withStyle(ChatFormatting.GRAY);
        }
        return seconds < 3 ? Component.translatable("screen.projectrebirth.console.heard_now")
                : Component.translatable("screen.projectrebirth.console.heard_ago", seconds < 120 ? seconds + "s" : (seconds / 60) + "m");
    }
}
