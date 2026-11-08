package com.projectrebirth.gametest;

import java.util.Map;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.Environment;
import com.projectrebirth.environment.EnvironmentProfile;
import com.projectrebirth.environment.EnvironmentRegistry;
import com.projectrebirth.environment.EnvironmentState;
import com.projectrebirth.environment.EventPhase;
import com.projectrebirth.environment.Exposure;
import com.projectrebirth.environment.Hazard;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The base of the environment. The state of a real dimension is shared by the whole test server, so tests that change it or the
 * config have a batch of their own. Run with {@code .\gradlew runGameTestServer}.
 */
@GameTestHolder(PlanTests.ATMOSPHERE)
@PrefixGameTestTemplate(false)
public class EnvironmentGameTests {
    private static final String TEMPLATE = "floor_5x5";
    private static final float EPSILON = 0.001f;

    private static Holder<Biome> biome(GameTestHelper helper, ResourceKey<Biome> key) {
        return helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(key);
    }

    private static String profileId(GameTestHelper helper, ResourceKey<Biome> key) {
        RegistryAccess access = helper.getLevel().registryAccess();
        return Environment.profileFor(access, biome(helper, key))
                .flatMap(profile -> access.registryOrThrow(EnvironmentRegistry.PROFILES).getResourceKey(profile))
                .map(found -> found.location().getPath()).orElse("none");
    }

    private static EnvironmentProfile plainProfile(Map<String, Float> multipliers) {
        return new EnvironmentProfile(HolderSet.direct(), 0, Map.of(Hazard.POLLUTION, 2.0f, Hazard.RADIATION, 4.0f), multipliers, 0.0f);
    }

    private static float strength(String event, Hazard hazard) {
        return RebirthConfig.environmentEvent(event).hazard(hazard);
    }

    @GameTest(template = TEMPLATE)
    public static void everyBiomeFindsItsProfile(GameTestHelper helper) {
        helper.assertTrue(profileId(helper, Biomes.PLAINS).equals("overworld"), "Plains: " + profileId(helper, Biomes.PLAINS));
        helper.assertTrue(profileId(helper, Biomes.SWAMP).equals("swamp"), "Swamp wins over the overworld: " + profileId(helper, Biomes.SWAMP));
        helper.assertTrue(profileId(helper, Biomes.MANGROVE_SWAMP).equals("swamp"), "Mangrove: " + profileId(helper, Biomes.MANGROVE_SWAMP));
        helper.assertTrue(profileId(helper, Biomes.DESERT).equals("desert"), "Desert: " + profileId(helper, Biomes.DESERT));
        helper.assertTrue(profileId(helper, Biomes.WOODED_BADLANDS).equals("badlands"), "Badlands: " + profileId(helper, Biomes.WOODED_BADLANDS));
        helper.assertTrue(profileId(helper, Biomes.NETHER_WASTES).equals("nether"), "Nether: " + profileId(helper, Biomes.NETHER_WASTES));
        helper.assertTrue(profileId(helper, Biomes.THE_END).equals("end"), "End: " + profileId(helper, Biomes.THE_END));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void noOrdinaryBiomeIsSafeAndTheTablesDiffer(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();
        EnvironmentProfile plains = Environment.profileFor(access, biome(helper, Biomes.PLAINS)).orElseThrow();
        EnvironmentProfile swamp = Environment.profileFor(access, biome(helper, Biomes.SWAMP)).orElseThrow();
        EnvironmentProfile desert = Environment.profileFor(access, biome(helper, Biomes.DESERT)).orElseThrow();
        EnvironmentProfile nether = Environment.profileFor(access, biome(helper, Biomes.NETHER_WASTES)).orElseThrow();
        helper.assertTrue(plains.background(Hazard.POLLUTION) > 0.0f && plains.background(Hazard.RADIATION) > 0.0f, "No safe biomes");
        helper.assertTrue(swamp.background(Hazard.POLLUTION) > plains.background(Hazard.POLLUTION), "Swamps have more toxins");
        // since 1.5.0 (owner): acid rain falls in deserts and badlands too, and is drawn there by the mod
        helper.assertTrue(desert.eventMultiplier(Environment.ACID_RAIN) > 0.0f, "Acid rain falls in a desert too");
        helper.assertTrue(desert.eventMultiplier(Environment.RADIATION_BURST) > 1.0f, "A burst is stronger in a desert");
        for (String event : Environment.EVENTS) {
            helper.assertTrue(nether.eventMultiplier(event) == 0.0f, "No " + event + " in the Nether");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void catastrophesHappenOnlyInDimensionsThatAllowThem(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();
        helper.assertTrue(Environment.hasCatastrophes(access, Level.OVERWORLD), "The overworld has them");
        helper.assertFalse(Environment.hasCatastrophes(access, Level.NETHER), "The Nether has none");
        helper.assertFalse(Environment.hasCatastrophes(access, Level.END), "The End has none");
        helper.assertFalse(Environment.hasCatastrophes(access, ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("other_mod", "somewhere"))),
                "A dimension nobody described has none");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aRoofStopsAcidLowersPollutionAndLeavesRadiation(GameTestHelper helper) {
        EnvironmentProfile profile = plainProfile(Map.of());
        EnvironmentState state = new EnvironmentState();
        state.startActive(Environment.ACID_RAIN, 1.0f, 100);
        Exposure open = Environment.compute(profile, state, Environment.Shelter.NONE);
        Exposure roofed = Environment.compute(profile, state, new Environment.Shelter(true, false));
        helper.assertTrue(open.get(Hazard.ACID) > 0.0f, "Acid falls in the open");
        helper.assertTrue(roofed.get(Hazard.ACID) == 0.0f, "A roof stops the acid");
        helper.assertTrue(Math.abs(roofed.get(Hazard.POLLUTION) - open.get(Hazard.POLLUTION) * 0.75f) < EPSILON, "A roof without a sealed room lowers the pollution by a quarter (1.4.0)");
        helper.assertTrue(Math.abs(roofed.get(Hazard.RADIATION) - open.get(Hazard.RADIATION)) < EPSILON, "Radiation passes through a roof");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void depthLowersPollutionButNotRadiation(GameTestHelper helper) {
        EnvironmentProfile profile = plainProfile(Map.of());
        EnvironmentState state = new EnvironmentState();
        Exposure roofed = Environment.compute(profile, state, new Environment.Shelter(true, false));
        Exposure deep = Environment.compute(profile, state, new Environment.Shelter(true, true));
        helper.assertTrue(deep.get(Hazard.POLLUTION) < roofed.get(Hazard.POLLUTION), "Less pollution deep down");
        helper.assertTrue(Math.abs(deep.get(Hazard.RADIATION) - 4.0f) < EPSILON, "Radiation as on the surface");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aCatastropheAddsToTheBackgroundByBiomeAndIntensity(GameTestHelper helper) {
        EnvironmentState state = new EnvironmentState();
        EnvironmentProfile normal = plainProfile(Map.of());
        EnvironmentProfile strong = plainProfile(Map.of(Environment.RADIATION_BURST, 2.0f));
        EnvironmentProfile none = plainProfile(Map.of(Environment.RADIATION_BURST, 0.0f));
        helper.assertTrue(Environment.compute(normal, state, Environment.Shelter.NONE).get(Hazard.RADIATION) == 4.0f, "Only the background when calm");
        state.startActive(Environment.RADIATION_BURST, 1.5f, 100);
        float burst = strength(Environment.RADIATION_BURST, Hazard.RADIATION) * 1.5f;
        helper.assertTrue(Math.abs(Environment.compute(normal, state, Environment.Shelter.NONE).get(Hazard.RADIATION) - (4.0f + burst)) < EPSILON, "Intensity counts");
        helper.assertTrue(Math.abs(Environment.compute(strong, state, Environment.Shelter.NONE).get(Hazard.RADIATION) - (4.0f + 2.0f * burst)) < EPSILON, "The biome counts");
        helper.assertTrue(Math.abs(Environment.compute(none, state, Environment.Shelter.NONE).get(Hazard.RADIATION) - 4.0f) < EPSILON, "A biome can be spared");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void phasesRunThroughAndFadeOut(GameTestHelper helper) {
        EnvironmentState state = new EnvironmentState();
        state.startActive(Environment.TOXIC_STORM, 1.0f, 2);
        helper.assertTrue(state.phase() == EventPhase.ACTIVE && state.strengthNow() == 1.0f, "Active at full strength");
        state.tickSecond(helper.getLevel().getRandom(), true);
        helper.assertTrue(state.phase() == EventPhase.ACTIVE, "Still active");
        state.tickSecond(helper.getLevel().getRandom(), true);
        helper.assertTrue(state.phase() == EventPhase.AFTERMATH, "Then the aftermath, got " + state.phase());
        float fadingFrom = state.strengthNow();
        state.tickSecond(helper.getLevel().getRandom(), true);
        helper.assertTrue(state.strengthNow() < fadingFrom, "It fades out");
        for (int i = 0; i < RebirthConfig.ENVIRONMENT_AFTERMATH_SECONDS.get() + 1; i++) {
            state.tickSecond(helper.getLevel().getRandom(), true);
        }
        helper.assertTrue(state.phase() == EventPhase.CALM && state.event() == null && state.strengthNow() == 0.0f, "Then calm");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theStateSurvivesSavingAndLoading(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        EnvironmentState state = new EnvironmentState();
        state.startActive(Environment.ACID_RAIN, 1.25f, 77);
        state.setBackgroundFactor(Hazard.RADIATION, 1.5f);
        CompoundTag saved = state.save(new CompoundTag(), registries);
        EnvironmentState loaded = EnvironmentState.load(saved, registries);
        helper.assertTrue(loaded.phase() == EventPhase.ACTIVE && Environment.ACID_RAIN.equals(loaded.event()), "Phase and type come back");
        helper.assertTrue(loaded.intensity() == 1.25f && loaded.secondsLeft() == 77, "Intensity and time come back");
        helper.assertTrue(loaded.backgroundFactor(Hazard.RADIATION) == 1.5f, "The background factors come back");
        helper.assertTrue(loaded.backgroundFactor(Hazard.POLLUTION) == 1.0f, "An unset factor stays neutral");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void playersTakeTurnsOncePerSecond(GameTestHelper helper) {
        int turns = 0;
        for (long tick = 1000; tick < 1020; tick++) {
            if (Environment.isSecondOf(tick, 7)) {
                turns++;
            }
        }
        helper.assertTrue(turns == 1, "One turn per 20 ticks, got " + turns);
        helper.assertFalse(Environment.isSecondOf(1000, 7) && Environment.isSecondOf(1000, 8), "Players do not share a tick");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "environment_commands")
    public static void commandsStartAndClearACatastrophe(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        EnvironmentState state = EnvironmentState.get(helper.getLevel());
        var source = server.createCommandSourceStack().withLevel(helper.getLevel()).withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, "rebirth environment event acid_rain 1.5");
        helper.assertTrue(state.phase() == EventPhase.ACTIVE && Environment.ACID_RAIN.equals(state.event()) && state.intensity() == 1.5f,
                "The command starts it, got " + state.phase() + " " + state.event());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(player.getX(), helper.getLevel().getMaxBuildHeight() - 2.0, player.getZ());
        Environment.updatePlayer(player);
        helper.assertTrue(Environment.lastExposure(player).get(Hazard.ACID) > 0.0f, "The player's second counts it, got " + Environment.lastExposure(player));
        server.getCommands().performPrefixedCommand(source, "rebirth environment event no_such_thing");
        helper.assertTrue(Environment.ACID_RAIN.equals(state.event()), "An unknown type is refused");
        server.getCommands().performPrefixedCommand(source, "rebirth environment clear");
        helper.assertTrue(state.phase() == EventPhase.CALM && state.event() == null, "Clear ends it");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "environment_off")
    public static void theSwitchesTurnEverythingOff(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        EnvironmentState state = EnvironmentState.get(helper.getLevel());
        var source = server.createCommandSourceStack().withLevel(helper.getLevel()).withSuppressedOutput();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(player.getX(), helper.getLevel().getMaxBuildHeight() - 2.0, player.getZ());

        RebirthConfig.EnvironmentEventStats rain = RebirthConfig.environmentEvent(Environment.ACID_RAIN);
        rain.enabled().set(false);
        server.getCommands().performPrefixedCommand(source, "rebirth environment event acid_rain");
        helper.assertTrue(state.phase() == EventPhase.CALM, "A catastrophe switched off is refused");
        rain.enabled().set(true);

        Environment.updatePlayer(player);
        helper.assertFalse(Environment.lastExposure(player).isNone(), "The background is there");
        RebirthConfig.ENVIRONMENT_ENABLED.set(false);
        Environment.updatePlayer(player);
        boolean none = Environment.lastExposure(player).isNone();
        server.getCommands().performPrefixedCommand(source, "rebirth environment event acid_rain");
        EventPhase phase = state.phase();
        RebirthConfig.ENVIRONMENT_ENABLED.set(true);
        helper.assertTrue(none, "With the system off nothing acts");
        helper.assertTrue(phase == EventPhase.CALM, "With the system off nothing starts");
        helper.succeed();
    }
}
