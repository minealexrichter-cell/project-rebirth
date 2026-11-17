package com.projectrebirth.garrison;

import java.util.Locale;

/**
 * The alarms of a console (1.7.0, block D): a stranger on the faction's land near a machine, a machine in a fight, a machine shut
 * down, a machine lost (a wreck, or destroyed), a machine out of energy or ammunition (the machines of later blocks raise it). The
 * redstone signal of the console is the strength of the worst alarm going on. Block F adds the reports of work androids: the
 * area is done (or there is nothing to do; logged only, not told in chat, since a farm runs out of ripe crops time and again),
 * no tool, the chest is full. 1.7.2 adds: a machine is stuck on its way; a machine needs a service post (or a charging pad) and
 * finds none in reach.
 */
public enum ConsoleAlarm {
    INTRUDER(15, true),
    COMBAT(12, true),
    LOST(9, true),
    DISABLED(6, true),
    NO_SUPPLY(3, true),
    WORK_DONE(1, false),
    NO_TOOL(2, true),
    CHEST_FULL(2, true),
    // 1.7.2, block B (new ones only at the end: the console saves alarms by their place in this list)
    STUCK(3, true),
    NO_POST(3, true);

    private final int signal;
    private final boolean told;

    ConsoleAlarm(int signal, boolean told) {
        this.signal = signal;
        this.told = told;
    }

    public int signal() {
        return signal;
    }

    /** Whether the owner hears of it in chat (else it is only in the console's log). */
    public boolean told() {
        return told;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ConsoleAlarm byId(int id) {
        ConsoleAlarm[] values = values();
        return id >= 0 && id < values.length ? values[id] : NO_SUPPLY;
    }
}
