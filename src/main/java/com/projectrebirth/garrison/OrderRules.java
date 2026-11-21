package com.projectrebirth.garrison;

import javax.annotation.Nullable;

/**
 * Which orders a machine can carry out (1.8.0, block E), one place for the server and the console's window: a turret stands on its
 * mount and takes no orders; only a work android works. The console's order panel greys out what none of the chosen machines can do
 * and tells why; the server leaves such machines out of an order.
 */
public final class OrderRules {
    /** The bits of the target rules of a console, a group or a machine. */
    public static final int MONSTERS = 1;
    public static final int WAR = 2;
    public static final int RETALIATE = 4;
    /** What machines did before 1.8.0: monsters, enemies at war, whoever attacks them. */
    public static final int DEFAULT_RULES = MONSTERS | WAR | RETALIATE;

    private OrderRules() {
    }

    /** Why a machine of this kind cannot carry out this order (a translation key), or null when it can. */
    @Nullable
    public static String problem(MachineKind kind, boolean worker, MachineTask.Type type) {
        if (kind == MachineKind.TURRET) {
            return "order_problem.projectrebirth.turret";
        }
        if (type == MachineTask.Type.WORK && !worker) {
            return "order_problem.projectrebirth.work";
        }
        return null;
    }
}
