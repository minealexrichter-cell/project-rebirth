package com.projectrebirth.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.Catastrophes;
import com.projectrebirth.environment.ContaminationMap;
import com.projectrebirth.environment.ContaminationZones;
import com.projectrebirth.environment.Environment;
import com.projectrebirth.environment.EnvironmentState;
import com.projectrebirth.environment.Exposure;
import com.projectrebirth.environment.Hazard;
import com.projectrebirth.factions.FactionBlocks;
import com.projectrebirth.factions.FactionService;
import com.projectrebirth.shelter.Contamination;
import com.projectrebirth.shelter.Room;
import com.projectrebirth.shelter.Shelters;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Block A of plan 1.6: contaminated zones. The map of a real dimension is shared by the whole test server, so each test has a
 * batch of its own and starts and ends with an empty map.
 */
@GameTestHolder(PlanTests.SCOUTING)
@PrefixGameTestTemplate(false)
public class ContaminatedZonesGameTests {
    private static final String TEMPLATE = "floor_5x5";
    private static final float EPSILON = 0.02f;

    private static ContaminationMap fresh(GameTestHelper helper) {
        ContaminationMap map = ContaminationMap.get(helper.getLevel());
        map.clearAll();
        return map;
    }

    private static ServerPlayer survivor(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return player;
    }

    /** The contaminated chunks within the distance of any player of the dimension (mock players of other tests are there too). */
    private static java.util.List<ContaminationMap.Spot> nearPlayers(ServerLevel level, ContaminationMap map, int chunks) {
        java.util.Map<Long, ContaminationMap.Spot> found = new java.util.HashMap<>();
        for (ServerPlayer player : level.players()) {
            for (ContaminationMap.Spot spot : map.near(player.blockPosition(), chunks, level.getGameTime())) {
                found.put(net.minecraft.world.level.ChunkPos.asLong(spot.chunkX(), spot.chunkZ()), spot);
            }
        }
        return new java.util.ArrayList<>(found.values());
    }

    private static CommandSourceStack source(GameTestHelper helper, ServerPlayer player) {
        return helper.getLevel().getServer().createCommandSourceStack().withLevel(helper.getLevel()).withEntity(player)
                .withPosition(player.position()).withSuppressedOutput();
    }

    private static int run(GameTestHelper helper, CommandSourceStack source, String command) {
        try {
            return helper.getLevel().getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException failed) {
            return -1;
        }
    }

    @GameTest(template = TEMPLATE, batch = "scouting_burst")
    public static void aRadiationBurstLeavesSpotsAroundThePlayer(GameTestHelper helper) {
        ContaminationMap map = fresh(helper);
        ServerPlayer player = survivor(helper);
        ServerLevel level = helper.getLevel();
        int maxChunks = (RebirthConfig.ZONES_MAX_DISTANCE.get() + (RebirthConfig.ZONES_RADIATION_MAX_RADIUS.get() + 1) * 16) / 16 + 2;
        int placed = ContaminationZones.leaveSpots(level, Environment.RADIATION_BURST, 1.0f);
        helper.assertTrue(placed >= RebirthConfig.ZONES_RADIATION_MIN_SPOTS.get() && placed <= RebirthConfig.ZONES_RADIATION_MAX_SPOTS.get(),
                "3 to 6 spots, got " + placed);
        ContaminationMap.Reading strongest = ContaminationMap.Reading.NONE;
        java.util.List<ContaminationMap.Spot> near = nearPlayers(level, map, maxChunks);
        helper.assertTrue(near.size() == map.size(), "Every contaminated chunk is within the distance of the player: " + near.size() + " of " + map.size());
        for (ContaminationMap.Spot spot : near) {
            helper.assertTrue(spot.toxin() == 0.0f, "A burst leaves radiation only");
            if (spot.radiation() > strongest.radiation()) {
                strongest = new ContaminationMap.Reading(spot.radiation(), 0.0f);
            }
        }
        float centre = RebirthConfig.ZONES_RADIATION_STRENGTH.get().floatValue();
        helper.assertTrue(strongest.radiation() >= 0.75f * centre - EPSILON, "A spot is strong in the middle: " + strongest.radiation());
        helper.assertTrue(strongest.radiation() <= RebirthConfig.ZONES_MAX_PER_CHUNK.get() + EPSILON, "Never above the limit of a chunk");
        // a weaker burst leaves weaker spots
        map.clearAll();
        ContaminationZones.leaveSpots(level, Environment.RADIATION_BURST, 0.5f);
        float weakest = 0.0f;
        for (ContaminationMap.Spot spot : nearPlayers(level, map, maxChunks)) {
            weakest = Math.max(weakest, spot.radiation());
        }
        helper.assertTrue(weakest > 0.0f && weakest <= 0.5f * centre * 3.0f, "Strength follows the intensity: " + weakest);
        map.clearAll();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "scouting_aftermath")
    public static void theAftermathOfABurstLeavesSpotsByItself(GameTestHelper helper) {
        ContaminationMap map = fresh(helper);
        survivor(helper);
        ServerLevel level = helper.getLevel();
        EnvironmentState state = EnvironmentState.get(level);
        state.startActive(Environment.RADIATION_BURST, 1.0f, 10);
        helper.assertTrue(map.size() == 0, "Nothing yet while it is active");
        Catastrophes.onSecond(level, state, EnvironmentState.Change.AFTERMATH, Environment.RADIATION_BURST);
        helper.assertTrue(map.size() > 0, "The aftermath leaves spots");
        state.clear();
        map.clearAll();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "scouting_storm")
    public static void aToxicStormLeavesToxicSpots(GameTestHelper helper) {
        ContaminationMap map = fresh(helper);
        ServerPlayer player = survivor(helper);
        ServerLevel level = helper.getLevel();
        double weight = RebirthConfig.ZONES_TOXIC_LOWLAND_WEIGHT.get();
        RebirthConfig.ZONES_TOXIC_LOWLAND_WEIGHT.set(1.0); // no preference: every place is taken
        try {
            int placed = ContaminationZones.leaveSpots(level, Environment.TOXIC_STORM, 1.0f);
            helper.assertTrue(placed >= RebirthConfig.ZONES_TOXIC_MIN_SPOTS.get() && placed <= RebirthConfig.ZONES_TOXIC_MAX_SPOTS.get(), "2 to 4 spots, got " + placed);
            int maxChunks = (RebirthConfig.ZONES_MAX_DISTANCE.get() + (RebirthConfig.ZONES_TOXIC_MAX_RADIUS.get() + 1) * 16) / 16 + 2;
            java.util.List<ContaminationMap.Spot> near = nearPlayers(level, map, maxChunks);
            helper.assertTrue(!near.isEmpty() && near.size() == map.size(), "The spots are near the player");
            float strongest = 0.0f;
            for (ContaminationMap.Spot spot : near) {
                helper.assertTrue(spot.radiation() == 0.0f, "A storm leaves toxins only");
                strongest = Math.max(strongest, spot.toxin());
            }
            helper.assertTrue(strongest >= 0.75f * RebirthConfig.ZONES_TOXIC_STRENGTH.get() - EPSILON, "Strong in the middle: " + strongest);
            helper.assertTrue(ContaminationZones.leaveSpots(level, Environment.ACID_RAIN, 1.0f) == 0, "Other catastrophes leave nothing");
        } finally {
            RebirthConfig.ZONES_TOXIC_LOWLAND_WEIGHT.set(weight);
            map.clearAll();
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "scouting_territory")
    public static void spotsStayOffTheTerritoryOfAFaction(GameTestHelper helper) {
        ContaminationMap map = fresh(helper);
        MinecraftServer server = helper.getLevel().getServer();
        FactionService.adminDisband(server, "Scout Mu");
        ServerPlayer owner = survivor(helper);
        FactionService.create(owner, "Scout Mu", ChatFormatting.GREEN);
        BlockPos core = owner.blockPosition().below();
        helper.getLevel().setBlockAndUpdate(core, FactionBlocks.FACTION_CORE.get().defaultBlockState());
        FactionService.addCore(owner, helper.getLevel(), core);
        int min = RebirthConfig.ZONES_MIN_DISTANCE.get();
        int max = RebirthConfig.ZONES_MAX_DISTANCE.get();
        RebirthConfig.ZONES_MIN_DISTANCE.set(0);
        RebirthConfig.ZONES_MAX_DISTANCE.set(1);
        try {
            helper.assertTrue(ContaminationZones.onTerritory(helper.getLevel(), owner.blockPosition(), 1), "The test place is a territory");
            helper.assertTrue(ContaminationZones.leaveSpots(helper.getLevel(), Environment.RADIATION_BURST, 1.0f, java.util.List.of(owner)) == 0 && map.size() == 0,
                    "Nothing lands on the territory by default");
            RebirthConfig.ZONES_ON_TERRITORY.set(true);
            helper.assertTrue(ContaminationZones.leaveSpots(helper.getLevel(), Environment.RADIATION_BURST, 1.0f, java.util.List.of(owner)) > 0, "The option allows it");
        } finally {
            RebirthConfig.ZONES_ON_TERRITORY.set(false);
            RebirthConfig.ZONES_MIN_DISTANCE.set(min);
            RebirthConfig.ZONES_MAX_DISTANCE.set(max);
            FactionService.adminDisband(server, "Scout Mu");
            map.clearAll();
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "scouting_territory_far")
    public static void aBigSpotSeesACoreBetweenTheEdgePoints(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FactionService.adminDisband(server, "Scout Nu");
        ServerPlayer owner = survivor(helper);
        FactionService.create(owner, "Scout Nu", ChatFormatting.AQUA);
        BlockPos centre = owner.blockPosition();
        BlockPos core = centre.offset(32, 0, 32);
        helper.getLevel().setBlockAndUpdate(core.below(), FactionBlocks.FACTION_CORE.get().defaultBlockState());
        FactionService.addCore(owner, helper.getLevel(), core.below());
        try {
            helper.assertTrue(ContaminationZones.onTerritory(helper.getLevel(), centre, 3), "A radius 3 spot covers a core 32 blocks away diagonally");
            helper.assertFalse(ContaminationZones.onTerritory(helper.getLevel(), centre.offset(1000, 0, 0), 3), "A far place is free");
        } finally {
            FactionService.adminDisband(server, "Scout Nu");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "scouting_decay")
    public static void contaminationFadesByTimeAndSurvivesSaving(GameTestHelper helper) {
        ContaminationMap map = fresh(helper);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        long t0 = 1000L;
        map.addSpot(pos, 1, 8.0f, 4.0f, t0);
        ContaminationMap.Reading start = map.readingAt(pos, t0);
        helper.assertTrue(Math.abs(start.radiation() - 8.0f) < EPSILON && Math.abs(start.toxin() - 4.0f) < EPSILON, "The centre holds all: " + start);
        helper.assertTrue(map.size() == 5, "A spot of radius 1 covers the chunk and its four neighbours, got " + map.size());
        helper.assertTrue(map.readingAt(pos.offset(16, 0, 0), t0).radiation() < start.radiation(), "Weaker towards the edge");

        long radiationHalf = (long) (RebirthConfig.ZONES_RADIATION_HALF_LIFE_DAYS.get() * ContaminationMap.TICKS_PER_DAY);
        long toxinHalf = (long) (RebirthConfig.ZONES_TOXIC_HALF_LIFE_DAYS.get() * ContaminationMap.TICKS_PER_DAY);
        ContaminationMap.Reading afterRadiationHalf = map.readingAt(pos, t0 + radiationHalf);
        helper.assertTrue(Math.abs(afterRadiationHalf.radiation() - 4.0f) < EPSILON, "Half of the radiation after its half-life: " + afterRadiationHalf);
        helper.assertTrue(afterRadiationHalf.toxin() < 4.0f * 0.5f, "Toxins fade on their own, faster: " + afterRadiationHalf);
        helper.assertTrue(Math.abs(map.readingAt(pos, t0 + toxinHalf).toxin() - 2.0f) < EPSILON, "Half of the toxins after theirs");
        helper.assertTrue(map.readingAt(pos, t0 + 1).radiation() <= 8.0f, "Reading does not change what is stored");

        // saved and loaded again: the same numbers
        CompoundTag saved = map.save(new CompoundTag(), helper.getLevel().registryAccess());
        ContaminationMap loaded = ContaminationMap.load(saved, helper.getLevel().registryAccess());
        helper.assertTrue(loaded.size() == map.size(), "Every chunk is saved");
        long later = t0 + radiationHalf * 2;
        ContaminationMap.Reading a = map.readingAt(pos, later);
        ContaminationMap.Reading b = loaded.readingAt(pos, later);
        helper.assertTrue(Math.abs(a.radiation() - b.radiation()) < 1.0e-4f && Math.abs(a.toxin() - b.toxin()) < 1.0e-4f
                && Math.abs(b.radiation() - 2.0f) < EPSILON, "The same fading after loading: " + b);

        // old enough: forgotten
        long ancient = t0 + (long) (60 * ContaminationMap.TICKS_PER_DAY);
        helper.assertTrue(loaded.readingAt(pos, ancient).isNone(), "A very old spot is gone");
        helper.assertTrue(map.readingAt(pos, ancient).isNone(), "In the live map too");
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                map.readingAt(pos.offset(dx * 16, 0, dz * 16), ancient);
            }
        }
        helper.assertTrue(map.size() == 0, "Faded chunks are removed when read, left: " + map.size());

        // a world from 1.5.2 has nothing saved: clean
        helper.assertTrue(ContaminationMap.load(new CompoundTag(), helper.getLevel().registryAccess()).size() == 0, "No data is a clean world");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "scouting_exposure", skyAccess = true)
    public static void theGroundAddsToTheAirAndToTheContamination(GameTestHelper helper) {
        ContaminationMap map = fresh(helper);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = survivor(helper);
        BlockPos pos = player.blockPosition();
        Exposure before = Environment.ordinaryExposureAt(level, pos);
        map.addSpot(pos, 1, 10.0f, 5.0f, level.getGameTime());
        Exposure after = Environment.ordinaryExposureAt(level, pos);
        helper.assertTrue(Math.abs(after.get(Hazard.RADIATION) - before.get(Hazard.RADIATION) - 10.0f) < EPSILON,
                "Radiation is added: " + before + " -> " + after);
        helper.assertTrue(after.get(Hazard.POLLUTION) - before.get(Hazard.POLLUTION) > 3.0f, "Polluted air is added: " + before + " -> " + after);
        helper.assertTrue(Contamination.gainPerSecond(after) > Contamination.gainPerSecond(before), "External contamination grows faster there");
        // the second of the player: the carrier gathers it
        Contamination.set(player, 0.0f);
        Environment.updatePlayer(player);
        Contamination.second(player);
        helper.assertTrue(Contamination.of(player) > 0.0f || Contamination.gainPerSecond(after) <= RebirthConfig.CONTAMINATION_DECAY.get(),
                "The person carries it: " + Contamination.of(player));
        helper.assertTrue(Environment.lastExposure(player).get(Hazard.RADIATION) > 5.0f, "The player's own second counts it: " + Environment.lastExposure(player));
        helper.assertTrue(ContaminationZones.mask(level, pos) == (ContaminationZones.RADIATION_BIT | ContaminationZones.TOXIC_BIT), "The HUD marks both");
        Contamination.set(player, 0.0f);
        map.clearAll();
        helper.assertTrue(ContaminationZones.mask(level, pos) == 0, "Cleaned: no mark");
        helper.succeed();
    }

    @GameTest(template = "floor_9x9", batch = "scouting_shelter", skyAccess = true)
    public static void aSealedShelterProtectsFromTheGround(GameTestHelper helper) {
        ContaminationMap map = fresh(helper);
        ShelterGameTests.box(helper, Blocks.STONE);
        helper.runAfterDelay(2, () -> {
            ServerLevel level = helper.getLevel();
            Room room = Shelters.of(level).scanNow(helper.absolutePos(ShelterGameTests.UNIT));
            helper.assertTrue(room != null && room.isSealed(), "The box is sealed");
            room.setPollution(0.0f);
            BlockPos inside = helper.absolutePos(new BlockPos(4, 3, 4));
            map.addSpot(inside, 1, 10.0f, 8.0f, level.getGameTime());
            Exposure ordinary = Environment.ordinaryExposureAt(level, inside);
            Exposure here = Environment.exposureAt(level, inside);
            helper.assertTrue(ordinary.get(Hazard.POLLUTION) >= 8.0f * 0.5f, "The air around is polluted: " + ordinary);
            helper.assertTrue(here.get(Hazard.POLLUTION) < 0.01f, "Inside it has its own clean air: " + here);
            helper.assertTrue(Math.abs(here.get(Hazard.RADIATION) - ordinary.get(Hazard.RADIATION) * RebirthConfig.SHELTER_SEALED_RADIATION.get().floatValue()) < EPSILON,
                    "The radiation is as weak as the walls make it: " + here);
            map.clearAll();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "scouting_off")
    public static void switchedOffThereAreNoSpotsAndNoEffect(GameTestHelper helper) {
        ContaminationMap map = fresh(helper);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = survivor(helper);
        BlockPos pos = player.blockPosition();
        Exposure clean = Environment.ordinaryExposureAt(level, pos);
        map.addSpot(pos, 1, 10.0f, 5.0f, level.getGameTime());
        RebirthConfig.ZONES_ENABLED.set(false);
        try {
            helper.assertTrue(ContaminationZones.leaveSpots(level, Environment.RADIATION_BURST, 1.0f) == 0, "No new spots");
            helper.assertTrue(ContaminationZones.leaveSpots(level, Environment.TOXIC_STORM, 1.0f) == 0, "No new toxic spots");
            Exposure off = Environment.ordinaryExposureAt(level, pos);
            helper.assertTrue(Math.abs(off.get(Hazard.RADIATION) - clean.get(Hazard.RADIATION)) < EPSILON
                    && Math.abs(off.get(Hazard.POLLUTION) - clean.get(Hazard.POLLUTION)) < EPSILON, "No effect on the air: " + clean + " vs " + off);
            helper.assertTrue(ContaminationZones.mask(level, pos) == 0, "No mark");
            int size = map.size();
            helper.assertTrue(run(helper, source(helper, player), "rebirth environment zone add radiation 5 1") == 0 && map.size() == size,
                    "The command adds nothing");
        } finally {
            RebirthConfig.ZONES_ENABLED.set(true);
        }
        helper.assertTrue(Environment.ordinaryExposureAt(level, pos).get(Hazard.RADIATION) > clean.get(Hazard.RADIATION) + 5.0f,
                "Switched on again, what was saved works again");
        map.clearAll();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "scouting_commands")
    public static void theZoneCommandsAddListAndClear(GameTestHelper helper) {
        ContaminationMap map = fresh(helper);
        ServerPlayer player = survivor(helper);
        CommandSourceStack source = source(helper, player);
        helper.assertTrue(run(helper, source, "rebirth environment zone list") == 0, "Nothing to list on a clean world");
        helper.assertTrue(run(helper, source, "rebirth environment zone add radiation 12 1") == 1, "Add at the caller");
        helper.assertTrue(map.readingAt(player.blockPosition(), helper.getLevel().getGameTime()).radiation() > 11.0f, "The spot is where the caller stands");
        BlockPos far = player.blockPosition().offset(16 * 6, 0, 0);
        helper.assertTrue(run(helper, source, "rebirth environment zone add toxic 7 0 " + far.getX() + " " + far.getY() + " " + far.getZ()) == 1, "Add at a position");
        helper.assertTrue(map.readingAt(far, helper.getLevel().getGameTime()).toxin() > 6.0f, "The toxic spot is there");
        helper.assertTrue(run(helper, source, "rebirth environment zone add poison 5 1") == 0, "An unknown type is refused");
        helper.assertTrue(run(helper, source, "rebirth environment zone list") >= 2, "The list shows the chunks around");
        helper.assertTrue(run(helper, source, "rebirth environment zone clear 1") > 0, "Clear around");
        helper.assertTrue(map.readingAt(player.blockPosition(), helper.getLevel().getGameTime()).isNone()
                && map.readingAt(far, helper.getLevel().getGameTime()).toxin() > 6.0f, "Around the caller only");
        helper.assertTrue(run(helper, source, "rebirth environment zone clear") == 1 && map.size() == 0, "Clear all");
        helper.succeed();
    }
}
