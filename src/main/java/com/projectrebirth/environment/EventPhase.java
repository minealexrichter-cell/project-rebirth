package com.projectrebirth.environment;

/** The stages of a catastrophe. Only ACTIVE and AFTERMATH act on anyone. */
public enum EventPhase {
    CALM("calm"),
    WARNING("warning"),
    ACTIVE("active"),
    AFTERMATH("aftermath");

    private final String id;

    EventPhase(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static EventPhase byId(String id) {
        for (EventPhase phase : values()) {
            if (phase.id.equals(id)) {
                return phase;
            }
        }
        return CALM;
    }
}
