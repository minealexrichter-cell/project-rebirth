package com.projectrebirth.environment;

import java.util.List;
import java.util.Optional;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.shelter.Room;
import com.projectrebirth.shelter.Shelters;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * What the environment is at a place, all on the server. It is counted once per second for each player, not every tick; the
 * players take turns over the second, so that they are not all counted in the same tick.
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class Environment {
    private static final int TICKS_PER_SECOND = 20;

    /** The catastrophes. A new one is one more line here and in the config. */
    public static final String ACID_RAIN = "acid_rain";
    public static final String TOXIC_STORM = "toxic_storm";
    public static final String RADIATION_BURST = "radiation_burst";
    /** 1.5.0 */
    public static final String DUST_STORM = "dust_storm";
    public static final List<String> EVENTS = List.of(ACID_RAIN, TOXIC_STORM, RADIATION_BURST, DUST_STORM);

    /** What shelters a place: nothing above it, or deep ground. */
    public record Shelter(boolean underRoof, boolean underground) {
        public static final Shelter NONE = new Shelter(false, false);
    }

    private Environment() {
    }

    public static boolean isEnabled() {
        return RebirthConfig.ENVIRONMENT_ENABLED.get();
    }

    /** The profile that counts for a biome: the highest priority of those that name it. */
    public static Optional<EnvironmentProfile> profileFor(RegistryAccess access, Holder<Biome> biome) {
        return access.registryOrThrow(EnvironmentRegistry.PROFILES).stream().filter(profile -> profile.covers(biome))
                .max((a, b) -> Integer.compare(a.priority(), b.priority()));
    }

    public static boolean hasCatastrophes(RegistryAccess access, ResourceKey<Level> dimension) {
        return access.registryOrThrow(EnvironmentRegistry.DIMENSIONS).stream()
                .anyMatch(rules -> rules.dimension().equals(dimension) && rules.catastrophes());
    }

    /** Like vanilla rain: anything solid above is a roof, also glass and leaves. */
    public static Shelter shelterAt(ServerLevel level, BlockPos pos) {
        boolean roof = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) > pos.getY();
        int depth = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) - pos.getY();
        return new Shelter(roof, roof && depth >= RebirthConfig.ENVIRONMENT_UNDERGROUND_DEPTH.get());
    }

    /** The whole calculation, with nothing taken from the world, so that it can be checked by itself. */
    public static Exposure compute(EnvironmentProfile profile, EnvironmentState state, Shelter shelter) {
        Exposure.Builder result = Exposure.builder();
        for (Hazard hazard : Hazard.values()) {
            result.add(hazard, profile.background(hazard) * state.backgroundFactor(hazard));
        }
        String event = state.event();
        float strength = state.strengthNow();
        RebirthConfig.EnvironmentEventStats stats = event == null ? null : RebirthConfig.environmentEvent(event);
        if (stats != null && stats.enabled().get() && strength > 0.0f) {
            float here = strength * profile.eventMultiplier(event);
            for (Hazard hazard : Hazard.values()) {
                result.add(hazard, stats.hazard(hazard) * here);
            }
        }
        if (shelter.underRoof()) {
            result.multiply(Hazard.POLLUTION, RebirthConfig.ENVIRONMENT_ROOF_POLLUTION.get().floatValue());
            result.multiply(Hazard.RADIATION, RebirthConfig.ENVIRONMENT_ROOF_RADIATION.get().floatValue());
            result.multiply(Hazard.ACID, RebirthConfig.ENVIRONMENT_ROOF_ACID.get().floatValue());
            result.multiply(Hazard.DUST, RebirthConfig.ENVIRONMENT_ROOF_DUST.get().floatValue());
        }
        if (shelter.underground()) {
            result.multiply(Hazard.POLLUTION, RebirthConfig.ENVIRONMENT_UNDERGROUND_POLLUTION.get().floatValue());
            result.multiply(Hazard.DUST, 0.0f);
        }
        return result.build();
    }

    /** What the environment is at a place of the world now, inside a sealed room as that room makes it. */
    public static Exposure exposureAt(ServerLevel level, BlockPos pos) {
        Exposure ordinary = ordinaryExposureAt(level, pos);
        Room room = Shelters.sealedRoomAt(level, pos);
        return room == null || !isEnabled() ? ordinary : room.inside(ordinary);
    }

    /** What the environment is at a place as if there were no sealed room: the air a room leaks in from around it. */
    public static Exposure ordinaryExposureAt(ServerLevel level, BlockPos pos) {
        if (!isEnabled()) {
            return Exposure.NONE;
        }
        return profileFor(level.registryAccess(), level.getBiome(pos))
                .map(profile -> compute(profile, EnvironmentState.get(level), shelterAt(level, pos)))
                .orElse(Exposure.NONE);
    }

    /** The second of one player. Public for tests (mock players do not tick). */
    public static void updatePlayer(ServerPlayer player) {
        player.setData(EnvironmentRegistry.LAST_EXPOSURE, exposureAt(player.serverLevel(), BlockPos.containing(player.getEyePosition())));
    }

    public static Exposure lastExposure(ServerPlayer player) {
        return player.getData(EnvironmentRegistry.LAST_EXPOSURE);
    }

    /** Whether a player's turn comes in this tick. */
    public static boolean isSecondOf(long gameTime, int playerId) {
        return Mth.positiveModulo(gameTime + playerId, TICKS_PER_SECOND) == 0;
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && isSecondOf(player.level().getGameTime(), player.getId())) {
            updatePlayer(player);
            HumanBody.onSecond(player, lastExposure(player));
            // in a power armor the armor carries the contamination; creative and spectator players carry none
            if (player.gameMode.isSurvival() && !(player.getVehicle() instanceof com.projectrebirth.powerarmor.PowerArmorEntity armor && !armor.isWrecked())) {
                com.projectrebirth.shelter.Contamination.second(player);
            }
        }
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && isEnabled() && level.getGameTime() % TICKS_PER_SECOND == 0) {
            EnvironmentState state = EnvironmentState.get(level);
            String before = state.event();
            EnvironmentState.Change change = state.tickSecond(level.getRandom(), hasCatastrophes(level.registryAccess(), level.dimension()));
            Catastrophes.onSecond(level, state, change, before);
        }
    }
}
