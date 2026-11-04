package com.projectrebirth.weapons;

/** One player's trigger, aim and reload, kept by the server. */
public class WeaponState {
    private static final int NO_RELOAD = -1;

    boolean triggerHeld;
    boolean aiming;
    /** The game time of the next allowed shot; it stays with the player, so changing weapons does not fire faster. */
    long nextShotTime;
    /** Degrees added to the weapon's spread by the shots of a burst. */
    float extraSpread;
    long reloadEndTime;
    int reloadSlot = NO_RELOAD;

    boolean isReloading() {
        return reloadSlot != NO_RELOAD;
    }

    void stopReload() {
        reloadSlot = NO_RELOAD;
    }

    /** Nothing to do for this player until the next input. */
    boolean isIdle() {
        return !triggerHeld && !aiming && !isReloading() && extraSpread <= 0.0f;
    }
}
