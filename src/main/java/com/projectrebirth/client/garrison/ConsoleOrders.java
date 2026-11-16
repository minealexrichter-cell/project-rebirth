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
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What the console's window and the tablet's screen share (1.7.0, block D): whom an order is for (the chosen machine or a group),
 * the order buttons, and the words for states and tasks. Client only.
 */
public final class ConsoleOrders {
    /** The orders on the buttons, in their order. */
    public static final List<MachineTask.Type> ORDERS = List.of(MachineTask.Type.GUARD, MachineTask.Type.PATROL, MachineTask.Type.ESCORT,
            MachineTask.Type.HOLD, MachineTask.Type.WORK, MachineTask.Type.TO_POST, MachineTask.Type.SHELTER, MachineTask.Type.STAND_DOWN);

    /** Whom the orders go to and which work; kept while the screen is open. */
    public static final class Target {
        @Nullable
        public UUID machine;
        /** -1: the chosen machine; 0..7: a group. */
        public int group = -1;
        public int work;
    }

    private ConsoleOrders() {
    }

    public static void send(BlockPos console, int action, @Nullable UUID machine, int group, int value, int extra, boolean fromTablet) {
        PacketDistributor.sendToServer(new ConsoleNetwork.ActionPayload(console, action, Optional.ofNullable(machine), group, value, extra, fromTablet));
    }

    /** The button that says whom the orders are for; a click goes on to the next group and back to the chosen machine. */
    public static Button targetButton(int x, int y, int width, Target target, @Nullable ConsoleView view, Runnable changed) {
        return Button.builder(targetLabel(target, view), button -> {
            target.group = target.group + 1 >= ConsoleBlockEntity.GROUPS ? -1 : target.group + 1;
            changed.run();
        }).bounds(x, y, width, 16).build();
    }

    public static Component targetLabel(Target target, @Nullable ConsoleView view) {
        if (target.group >= 0) {
            int members = view == null ? 0 : view.groups().get(target.group).members();
            return Component.translatable("screen.projectrebirth.console.target_group", target.group + 1, members);
        }
        String name = "—";
        if (view != null && target.machine != null) {
            for (ConsoleView.Row row : view.rows()) {
                if (row.id().equals(target.machine)) {
                    name = row.name();
                }
            }
        }
        return Component.translatable("screen.projectrebirth.console.target_machine", name);
    }

    /** The eight order buttons in rows of {@code columns}, plus the work kind; {@code add} puts each on the screen. */
    public static void orderButtons(int x, int y, int width, int columns, BlockPos console, Target target, boolean fromTablet,
                                    Consumer<Button> add, Runnable changed) {
        int i = 0;
        for (MachineTask.Type type : ORDERS) {
            int bx = x + (i % columns) * (width + 2), by = y + (i / columns) * 18;
            add.accept(Button.builder(Component.translatable(type.translationKey()), button -> {
                if (target.group < 0 && target.machine == null) {
                    return;
                }
                send(console, ConsoleNetwork.ORDER, target.group < 0 ? target.machine : null, target.group, type.ordinal(), target.work, fromTablet);
            }).bounds(bx, by, width, 16).build());
            i++;
        }
        int by = y + ((ORDERS.size() + columns - 1) / columns) * 18;
        add.accept(Button.builder(Component.translatable("screen.projectrebirth.console.work_kind",
                Component.translatable("work_kind.projectrebirth." + MachineTask.WORKS.get(target.work))), button -> {
            target.work = (target.work + 1) % MachineTask.WORKS.size();
            changed.run();
        }).bounds(x, by, width * 2 + 2, 16).build());
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
