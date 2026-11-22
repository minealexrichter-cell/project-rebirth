package com.projectrebirth.client.garrison;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.projectrebirth.garrison.ConsoleBlockEntity;
import com.projectrebirth.garrison.ConsoleNetwork;
import com.projectrebirth.garrison.ConsoleView;
import com.projectrebirth.garrison.MachineCore;
import com.projectrebirth.garrison.MachineTask;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What the console's window and the tablet's screen share (1.7.0, block D): whom an order is for (the chosen machine or a group),
 * the order buttons, and the words for states and tasks. Client only.
 */
public final class ConsoleOrders {
    /** The orders without a work kind, on two rows of buttons (1.7.1). */
    public static final List<MachineTask.Type> ORDERS = List.of(MachineTask.Type.GUARD, MachineTask.Type.PATROL, MachineTask.Type.AREA_PATROL,
            MachineTask.Type.ESCORT, MachineTask.Type.HOLD, MachineTask.Type.TO_POST, MachineTask.Type.SHELTER, MachineTask.Type.STAND_DOWN);
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
                    return Component.translatable("screen.projectrebirth.console.target_machine", com.projectrebirth.garrison.ConsoleBlockEntity.display(row.name()));
                }
            }
        }
        return Component.translatable("screen.projectrebirth.console.target_none");
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
