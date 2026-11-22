package com.projectrebirth.environment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

/** The environment of one dimension as a whole; saved with the world. Each dimension has its own. */
public class EnvironmentState extends SavedData {
    private static final String NAME = "projectrebirth_environment";

    /** Worldwide factors on the background of the biomes. Later events can leave their marks here. */
    private final Map<Hazard, Float> backgroundFactors = new EnumMap<>(Hazard.class);
    /** 1.9.0: the stage of the Wasteland of the last second (not saved: it is counted anew every second). */
    private int stage = 1;
    private EventPhase phase = EventPhase.CALM;
    @Nullable
    private String event;
    private float intensity;
    private int secondsLeft;
    private int phaseSeconds;
    /** The active phase that follows a warning, decided when the warning starts. */
    private int pendingActiveSeconds;
    /** Whether the calm has its length. A new world, and a world from before 1.3.0, first waits a whole pause. */
    private boolean scheduled;
    /** 1.6.0, block D: the catastrophe that ends the calm, chosen when the calm begins, so that a weather station can foresee it. */
    @Nullable
    private String plannedEvent;
    private float plannedIntensity;
    private int plannedActiveSeconds;
    /** 1.6.0, block D: the last catastrophes, the newest first. */
    private final List<Past> history = new ArrayList<>();
    public static final int HISTORY = 5;

    /** A catastrophe that came: what, how strong, how long, on which game day. */
    public record Past(String event, float intensity, int seconds, long day) {
    }

    /** What one second changed, so that players can be told. */
    public enum Change {
        NONE, WARNING, ACTIVE, AFTERMATH, ENDED
    }

    public static EnvironmentState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(EnvironmentState::new, EnvironmentState::load, null), NAME);
    }

    public float backgroundFactor(Hazard hazard) {
        return backgroundFactors.getOrDefault(hazard, 1.0f);
    }

    public void setBackgroundFactor(Hazard hazard, float factor) {
        backgroundFactors.put(hazard, factor);
        setDirty();
    }

    public EventPhase phase() {
        return phase;
    }

    @Nullable
    public String event() {
        return event;
    }

    public float intensity() {
        return intensity;
    }

    public int secondsLeft() {
        return secondsLeft;
    }

    public int phaseSeconds() {
        return phaseSeconds;
    }

    public int pendingActiveSeconds() {
        return pendingActiveSeconds;
    }

    public boolean scheduled() {
        return scheduled;
    }

    /** The catastrophe that ends this calm, if it is decided (null outside the calm and where none can come). */
    @Nullable
    public String plannedEvent() {
        return phase == EventPhase.CALM ? plannedEvent : null;
    }

    public float plannedIntensity() {
        return plannedIntensity;
    }

    public int plannedActiveSeconds() {
        return plannedActiveSeconds;
    }

    /**
     * The next catastrophe as a forecast can see it: in the calm the planned one, in the warning the announced one; null when
     * none is coming (or one is already going on).
     */
    @Nullable
    public String comingEvent() {
        return phase == EventPhase.WARNING ? event : plannedEvent();
    }

    /** Seconds until the coming catastrophe begins: the rest of the calm and the whole warning, or the rest of the warning. */
    public int secondsUntilComing() {
        return switch (phase) {
            case CALM -> secondsLeft + RebirthConfig.ENVIRONMENT_WARNING_SECONDS.get();
            case WARNING -> secondsLeft;
            default -> 0;
        };
    }

    public float comingIntensity() {
        return phase == EventPhase.WARNING ? intensity : plannedIntensity;
    }

    public int comingActiveSeconds() {
        return phase == EventPhase.WARNING ? pendingActiveSeconds : plannedActiveSeconds;
    }

    public List<Past> history() {
        return Collections.unmodifiableList(history);
    }

    /** A catastrophe began: remembered for the weather stations. */
    public void remember(String newEvent, float newIntensity, int seconds, long day) {
        history.add(0, new Past(newEvent, newIntensity, seconds, day));
        while (history.size() > HISTORY) {
            history.remove(history.size() - 1);
        }
        setDirty();
    }

    /** Announces a catastrophe: it comes after the warning. */
    public void startWarning(String newEvent, float newIntensity, int activeSeconds, int warningSeconds) {
        event = newEvent;
        intensity = newIntensity;
        pendingActiveSeconds = activeSeconds;
        scheduled = true;
        plannedEvent = null;
        enter(EventPhase.WARNING, warningSeconds);
    }

    /** A calm of this length, then the next catastrophe. */
    public void startCalm(int seconds) {
        event = null;
        intensity = 0.0f;
        scheduled = true;
        plannedEvent = null;
        enter(EventPhase.CALM, seconds);
    }

    /** Starts a catastrophe right away. */
    public void startActive(String newEvent, float newIntensity, int seconds) {
        event = newEvent;
        intensity = newIntensity;
        scheduled = true;
        plannedEvent = null;
        enter(EventPhase.ACTIVE, seconds);
    }

    /** Ends whatever is going on, at once; a new pause is counted on the next second. */
    public void clear() {
        event = null;
        intensity = 0.0f;
        scheduled = false;
        plannedEvent = null;
        enter(EventPhase.CALM, 0);
    }

    private void enter(EventPhase newPhase, int seconds) {
        phase = newPhase;
        phaseSeconds = seconds;
        secondsLeft = seconds;
        setDirty();
    }

    /** How much of the catastrophe's strength is acting now: all of it while active, fading out in the aftermath. */
    public float strengthNow() {
        return switch (phase) {
            case ACTIVE -> intensity;
            case AFTERMATH -> phaseSeconds <= 0 ? 0.0f : intensity * Mth.clamp((float) secondsLeft / phaseSeconds, 0.0f, 1.0f);
            default -> 0.0f;
        };
    }

    /**
     * One second has passed.
     *
     * @param random       for the length of a pause and the choice of the next catastrophe
     * @param catastrophes whether catastrophes happen in this dimension at all
     * @return what changed
     */
    public Change tickSecond(RandomSource random, boolean catastrophes) {
        return tickSecond(random, catastrophes, 1);
    }

    /**
     * The same at a stage of the Wasteland (1.9.0): a new calm is shorter and a new catastrophe stronger by [wasteland.stages]
     * and the preset.
     */
    public Change tickSecond(RandomSource random, boolean catastrophes, int stage) {
        this.stage = stage;
        if (phase == EventPhase.CALM) {
            if (!scheduled) {
                startCalm(calmSeconds(random));
                return Change.NONE;
            }
            if (!catastrophes) {
                plannedEvent = null;
            } else if (plannedEvent == null || weight(plannedEvent, id -> true) <= 0) {
                plan(random);
            }
            if (secondsLeft > 0) {
                secondsLeft--;
                setDirty();
            }
            if (secondsLeft > 0) {
                return Change.NONE;
            }
            String next = catastrophes ? plannedEvent : null;
            if (next == null) {
                startCalm(calmSeconds(random));
                return Change.NONE;
            }
            startWarning(next, plannedIntensity, plannedActiveSeconds, RebirthConfig.ENVIRONMENT_WARNING_SECONDS.get());
            return Change.WARNING;
        }
        secondsLeft--;
        setDirty();
        if (secondsLeft > 0) {
            return Change.NONE;
        }
        switch (phase) {
            case WARNING -> {
                enter(EventPhase.ACTIVE, Math.max(1, pendingActiveSeconds));
                return Change.ACTIVE;
            }
            case ACTIVE -> {
                enter(EventPhase.AFTERMATH, RebirthConfig.ENVIRONMENT_AFTERMATH_SECONDS.get());
                return Change.AFTERMATH;
            }
            default -> {
                startCalm(calmSeconds(random));
                return Change.ENDED;
            }
        }
    }

    /** Decides the catastrophe that will end this calm, with its strength and length. */
    private void plan(RandomSource random) {
        plannedEvent = chooseEvent(random, id -> true);
        if (plannedEvent != null) {
            RebirthConfig.EnvironmentEventStats stats = RebirthConfig.environmentEvent(plannedEvent);
            plannedIntensity = between(random, stats.minIntensity().get().floatValue(), stats.maxIntensity().get().floatValue())
                    * com.projectrebirth.config.WastelandConfig.byStage(com.projectrebirth.config.WastelandConfig.STAGE_CATASTROPHE_STRENGTH, stage);
            plannedActiveSeconds = between(random, stats.minActiveSeconds().get(), stats.maxActiveSeconds().get());
        }
        setDirty();
    }

    private int calmSeconds(RandomSource random) {
        int seconds = between(random, RebirthConfig.ENVIRONMENT_MIN_CALM_SECONDS.get(), RebirthConfig.ENVIRONMENT_MAX_CALM_SECONDS.get());
        float often = com.projectrebirth.config.WastelandConfig.byStage(com.projectrebirth.config.WastelandConfig.STAGE_CATASTROPHE_FREQUENCY, stage)
                * com.projectrebirth.config.WastelandConfig.preset().catastropheFrequency().get().floatValue();
        return Math.max(1, Math.round(seconds / Math.max(0.01f, often)));
    }

    private static int between(RandomSource random, int min, int max) {
        return max <= min ? min : min + random.nextInt(max - min + 1);
    }

    private static float between(RandomSource random, float min, float max) {
        return max <= min ? min : min + random.nextFloat() * (max - min);
    }

    /**
     * The next catastrophe, by the weights in the config; switched off ones and those the filter refuses never come.
     *
     * @return null if none can come
     */
    @Nullable
    public static String chooseEvent(RandomSource random, Predicate<String> allowed) {
        int total = 0;
        for (String id : Environment.EVENTS) {
            total += weight(id, allowed);
        }
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (String id : Environment.EVENTS) {
            roll -= weight(id, allowed);
            if (roll < 0) {
                return id;
            }
        }
        return null;
    }

    private static int weight(String id, Predicate<String> allowed) {
        RebirthConfig.EnvironmentEventStats stats = RebirthConfig.environmentEvent(id);
        return stats == null || !stats.enabled().get() || !allowed.test(id) ? 0 : stats.weight().get();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag factors = new CompoundTag();
        backgroundFactors.forEach((hazard, factor) -> factors.putFloat(hazard.id(), factor));
        tag.put("BackgroundFactors", factors);
        tag.putString("Phase", phase.id());
        if (event != null) {
            tag.putString("Event", event);
        }
        tag.putFloat("Intensity", intensity);
        tag.putInt("SecondsLeft", secondsLeft);
        tag.putInt("PhaseSeconds", phaseSeconds);
        tag.putInt("PendingActiveSeconds", pendingActiveSeconds);
        tag.putBoolean("Scheduled", scheduled);
        if (plannedEvent != null) {
            tag.putString("PlannedEvent", plannedEvent);
            tag.putFloat("PlannedIntensity", plannedIntensity);
            tag.putInt("PlannedActiveSeconds", plannedActiveSeconds);
        }
        ListTag past = new ListTag();
        for (Past one : history) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Event", one.event());
            entry.putFloat("Intensity", one.intensity());
            entry.putInt("Seconds", one.seconds());
            entry.putLong("Day", one.day());
            past.add(entry);
        }
        tag.put("History", past);
        return tag;
    }

    public static EnvironmentState load(CompoundTag tag, HolderLookup.Provider registries) {
        EnvironmentState state = new EnvironmentState();
        CompoundTag factors = tag.getCompound("BackgroundFactors");
        for (Hazard hazard : Hazard.values()) {
            if (factors.contains(hazard.id())) {
                state.backgroundFactors.put(hazard, factors.getFloat(hazard.id()));
            }
        }
        state.phase = EventPhase.byId(tag.getString("Phase"));
        state.event = tag.contains("Event") ? tag.getString("Event") : null;
        state.intensity = tag.getFloat("Intensity");
        state.secondsLeft = tag.getInt("SecondsLeft");
        state.phaseSeconds = tag.getInt("PhaseSeconds");
        state.pendingActiveSeconds = tag.getInt("PendingActiveSeconds");
        // A world from before 1.3.0 has no such key: it first waits a whole pause.
        state.scheduled = tag.getBoolean("Scheduled");
        if (tag.contains("PlannedEvent") && Environment.EVENTS.contains(tag.getString("PlannedEvent"))) {
            state.plannedEvent = tag.getString("PlannedEvent");
            state.plannedIntensity = tag.getFloat("PlannedIntensity");
            state.plannedActiveSeconds = tag.getInt("PlannedActiveSeconds");
        }
        ListTag past = tag.getList("History", Tag.TAG_COMPOUND);
        for (int i = 0; i < past.size() && i < HISTORY; i++) {
            CompoundTag entry = past.getCompound(i);
            if (Environment.EVENTS.contains(entry.getString("Event"))) {
                state.history.add(new Past(entry.getString("Event"), entry.getFloat("Intensity"), entry.getInt("Seconds"), entry.getLong("Day")));
            }
        }
        return state;
    }
}
