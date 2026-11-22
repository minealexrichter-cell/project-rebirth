package com.projectrebirth.environment;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.WastelandConfig;
import com.projectrebirth.core.RebirthGameRules;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * 1.9.0, block B: the stages of the Wasteland, I «Stagnation» to IV «Decay». They grow with the days of the world (the time it
 * has run plus the nights skipped by sleeping, so {@code /time set} does not turn them back), up to the game rule
 * {@code rebirthWastelandMaxStage}; with {@code rebirthWastelandEscalation} off the stage is always I. A world made before 1.9.0
 * starts at no higher stage than [wasteland.stages] oldWorldStage. Kept in the data of the Overworld.
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class Wasteland {
    public static final int MAX_STAGE = 4;
    private static final long DAY = 24000L;
    /** A world that has run this long before it first met 1.9.0 counts as an old one. */
    private static final long OLD_WORLD_TICKS = DAY;

    private Wasteland() {
    }

    /** What the Overworld keeps of the Wasteland. */
    public static final class State extends SavedData {
        private static final String NAME = ProjectRebirth.MOD_ID + "_wasteland";
        /** Ticks skipped by sleeping through the night. */
        private long skipped;
        /** Days taken off the count: an old world starts lower; a command sets the stage. */
        private long dayOffset;
        private boolean fresh;

        static Factory<State> factory() {
            return new Factory<>(() -> {
                State state = new State();
                state.fresh = true;
                return state;
            }, State::load, null);
        }

        static State load(CompoundTag tag, HolderLookup.Provider registries) {
            State state = new State();
            state.skipped = tag.getLong("skipped");
            state.dayOffset = tag.getLong("day_offset");
            return state;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putLong("skipped", skipped);
            tag.putLong("day_offset", dayOffset);
            return tag;
        }
    }

    public static State state(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(State.factory(), State.NAME);
    }

    /** Whole days the world has run, as the stages count them (after the offset). */
    public static long day(MinecraftServer server) {
        State state = state(server);
        return rawDay(server, state) - state.dayOffset;
    }

    private static long rawDay(MinecraftServer server, State state) {
        return (server.overworld().getGameTime() + state.skipped) / DAY;
    }

    /** The first day of a stage, by the config and the preset. */
    public static long startDay(int stage) {
        if (stage <= 1) {
            return 0;
        }
        int days = switch (Math.min(stage, MAX_STAGE)) {
            case 2 -> WastelandConfig.STAGE2_DAY.get();
            case 3 -> WastelandConfig.STAGE3_DAY.get();
            default -> WastelandConfig.STAGE4_DAY.get();
        };
        return Math.round(days * WastelandConfig.preset().stageDays().get());
    }

    /** The stage by the days alone, without the game rules. */
    public static int stageOfDay(long day) {
        int stage = 1;
        for (int next = 2; next <= MAX_STAGE; next++) {
            if (day >= startDay(next)) {
                stage = next;
            }
        }
        return stage;
    }

    /** The stage of the Wasteland now, 1 to 4. */
    public static int stage(MinecraftServer server) {
        if (!RebirthGameRules.on(server, RebirthGameRules.WASTELAND_ESCALATION)) {
            return 1;
        }
        return Math.min(stageOfDay(day(server)), RebirthGameRules.wastelandMaxStage(server));
    }

    public static int stage(Level level) {
        return level instanceof ServerLevel server ? stage(server.getServer()) : 1;
    }

    /** Days until the next stage comes, or -1 if none will (the last stage, the cap of the game rule, or no escalation). */
    public static long daysToNextStage(MinecraftServer server) {
        int stage = stage(server);
        if (!RebirthGameRules.on(server, RebirthGameRules.WASTELAND_ESCALATION) || stage >= Math.min(MAX_STAGE, RebirthGameRules.wastelandMaxStage(server))) {
            return -1;
        }
        return Math.max(0, startDay(stage + 1) - day(server));
    }

    /** Puts the world at the first day of a stage (the command of the operator). */
    public static void setStage(MinecraftServer server, int stage) {
        State state = state(server);
        state.dayOffset = rawDay(server, state) - startDay(Math.max(1, Math.min(MAX_STAGE, stage)));
        state.setDirty();
    }

    public static Component stageName(int stage) {
        return Component.translatable("wasteland.projectrebirth.stage." + Math.max(1, Math.min(MAX_STAGE, stage)));
    }

    /** I, II, III, IV. */
    public static String roman(int stage) {
        return switch (stage) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> "I";
        };
    }

    /**
     * The first start of a world with 1.9.0: a new one takes the defaults of the server config for its game rules; an old one
     * that has run before gets its days taken back to the first day of the old world stage.
     */
    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        State state = state(server);
        if (!state.fresh) {
            return;
        }
        state.fresh = false;
        if (server.overworld().getGameTime() > OLD_WORLD_TICKS) {
            state.dayOffset = oldWorldOffset(rawDay(server, state));
        } else {
            RebirthGameRules.applyConfigDefaults(server);
        }
        state.setDirty();
    }

    /** The days taken off an old world of this many days, so that it starts at no higher stage than [wasteland.stages] oldWorldStage. */
    public static long oldWorldOffset(long rawDay) {
        return Math.max(0, rawDay - startDay(WastelandConfig.OLD_WORLD_STAGE.get()));
    }

    /** A night slept through: the days count it, and a night in a sealed room takes away part of the toxins. */
    @SubscribeEvent
    static void onSleepFinished(SleepFinishedTimeEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        long skippedNow = Math.max(0, event.getNewTime() - level.getDayTime());
        if (level.dimension() == Level.OVERWORLD) {
            State state = state(level.getServer());
            state.skipped += skippedNow;
            state.setDirty();
        }
        for (ServerPlayer player : level.players()) {
            if (player.isSleeping()) {
                sleptThroughNight(player);
            }
        }
    }

    /** Public for tests: a night of sleep of a human. */
    public static void sleptThroughNight(ServerPlayer player) {
        if (!Environment.isEnabled() || com.projectrebirth.bodies.TransferService.isInArtificialBody(player)) {
            return;
        }
        if (com.projectrebirth.shelter.Shelters.sealedRoomAt(player.serverLevel(), net.minecraft.core.BlockPos.containing(player.getEyePosition())) != null) {
            Biology old = HumanBody.biology(player);
            HumanBody.setBiology(player, old.withToxin(old.toxin() * (1.0f - WastelandConfig.SLEEP_SHELTER_TOXIN_SHARE.get().floatValue())));
        }
    }
}
