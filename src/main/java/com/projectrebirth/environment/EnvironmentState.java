package com.projectrebirth.environment;

import java.util.EnumMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;

/** The environment of one dimension as a whole; saved with the world. Each dimension has its own. */
public class EnvironmentState extends SavedData {
    private static final String NAME = "projectrebirth_environment";

    /** Worldwide factors on the background of the biomes. Later events can leave their marks here. */
    private final Map<Hazard, Float> backgroundFactors = new EnumMap<>(Hazard.class);
    private EventPhase phase = EventPhase.CALM;
    @Nullable
    private String event;
    private float intensity;
    private int secondsLeft;
    private int phaseSeconds;

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

    /** Starts a catastrophe right away. */
    public void startActive(String newEvent, float newIntensity, int seconds) {
        event = newEvent;
        intensity = newIntensity;
        enter(EventPhase.ACTIVE, seconds);
    }

    /** Ends whatever is going on, at once. */
    public void clear() {
        event = null;
        intensity = 0.0f;
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

    /** One second has passed. Starting catastrophes by themselves comes with the weather forecast of block E. */
    public void tickSecond() {
        if (phase == EventPhase.CALM) {
            return;
        }
        secondsLeft--;
        setDirty();
        if (secondsLeft > 0) {
            return;
        }
        if (phase == EventPhase.ACTIVE) {
            enter(EventPhase.AFTERMATH, RebirthConfig.ENVIRONMENT_AFTERMATH_SECONDS.get());
        }
        if (phase == EventPhase.AFTERMATH && secondsLeft <= 0) {
            clear();
        }
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
        return state;
    }
}
