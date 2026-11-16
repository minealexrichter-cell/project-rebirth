package com.projectrebirth.garrison;

import java.util.Locale;

/**
 * The alarms of a console (1.7.0, block D): a stranger on the faction's land near a machine, a machine in a fight, a machine shut
 * down, a machine lost (a wreck, or destroyed), a machine out of energy or ammunition (the machines of later blocks raise it). The
 * redstone signal of the console is the strength of the worst alarm going on.
 */
public enum ConsoleAlarm {
    INTRUDER(15),
    COMBAT(12),
    LOST(9),
    DISABLED(6),
    NO_SUPPLY(3);

    private final int signal;

    ConsoleAlarm(int signal) {
        this.signal = signal;
    }

    public int signal() {
        return signal;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ConsoleAlarm byId(int id) {
        ConsoleAlarm[] values = values();
        return id >= 0 && id < values.length ? values[id] : NO_SUPPLY;
    }
}
