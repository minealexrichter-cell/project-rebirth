package com.projectrebirth.garrison.tactics;

import javax.annotation.Nullable;

import net.minecraft.world.phys.Vec3;

/**
 * 1.8.0, block I: what one combat machine keeps in mind of its tactics between ticks (not saved: after a reload it starts
 * afresh; falling back is found again from its integrity).
 */
public final class TacticsState {
    /** It falls back to be repaired (below retreatBelow, until returnAbove). */
    public boolean retreating;
    @Nullable
    public Vec3 retreatTo;
    public long retreatAt;
    /** The cover it found and when it looked. */
    @Nullable
    public Tactics.Cover cover;
    public long coverAt;
    /** Ticks it still steps back (after a blow, from an enemy up close), and where to. */
    public int backOff;
    @Nullable
    public Vec3 backTo;
    /** Which way it circles its target (1 or -1) and when it turns. */
    public int strafeSign = 1;
    public long strafeTurnAt;
    /** The last point on its circle or away from an enemy, and when it was found (looked for every ten ticks). */
    @Nullable
    public Vec3 cachedPoint;
    public long cachedAt = Long.MIN_VALUE;
    public int cachedKind;

    /** The cached point of that kind while it is fresh (ten ticks), else null. */
    @Nullable
    public Vec3 cached(int kind, long now) {
        return cachedKind == kind && now - cachedAt < 10 ? cachedPoint : null;
    }

    public Vec3 cache(int kind, long now, @Nullable Vec3 point) {
        cachedKind = kind;
        cachedAt = now;
        cachedPoint = point;
        return point;
    }

    /** When it may call for help again. */
    public long nextCall;
    /** A jump for a critical blow: it waits to fall onto the target. */
    public int critWait;
    /** A step aside (the line of fire was not clear, or a dodge), and how long it keeps to it. */
    @Nullable
    public Vec3 aside;
    public int asideTicks;
    /** Ticks it has seen a blow or a shot coming (the shield goes up after its reaction). */
    public int threatTicks;
    /** The last answer whether the line of fire is clear, and when it was asked. */
    public boolean lineClear = true;
    public long lineAt = Long.MIN_VALUE;
    /** A turret: where a hit came from that it could not see, and until when it faces there. */
    @Nullable
    public Vec3 hitFrom;
    public long hitFromUntil;

    /** A step aside of that many ticks. */
    public void stepAside(Vec3 point, int ticks) {
        aside = point;
        asideTicks = ticks;
    }

    /** The place of a step aside it still keeps to, or null. */
    @Nullable
    public Vec3 aside() {
        if (asideTicks <= 0) {
            aside = null;
            return null;
        }
        asideTicks--;
        return aside;
    }
}
