package com.projectrebirth.hostile;

import java.util.ArrayList;
import java.util.List;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.config.WastelandConfig;
import com.projectrebirth.environment.ContaminationZones;
import com.projectrebirth.environment.EnvironmentState;
import com.projectrebirth.environment.Wasteland;
import com.projectrebirth.structures.StructureSwitches;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Brings mutants to the world around players (1.6.0, part B3c; 1.9.0, block C: by the rules of data
 * {@code projectrebirth/mutant_spawns}, see {@link MutantSpawnRule}). Every few seconds, for each player, one try may set a
 * group of one kind down 16 to 32 blocks away; not while enough mutants are near the player or in the world, not in peaceful.
 * The stage of the Wasteland makes tries likelier and the limit near the player higher. Mutants far from players vanish like
 * ordinary monsters, so they do not pile up. Animals in contaminated places mutate ({@link Mutations}). Numbers in the config
 * ([hostile.mutants]).
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class MutantSpawner {
    public static final ResourceKey<Registry<MutantSpawnRule>> RULES = ResourceKey.createRegistryKey(ProjectRebirth.id("mutant_spawns"));
    private static final double NEAR = 48.0;

    private MutantSpawner() {
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !RebirthConfig.SERVER_SPEC.isLoaded()) {
            return;
        }
        int interval = RebirthConfig.MUTANTS_INTERVAL.get();
        if ((player.tickCount + player.getId()) % interval != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!spawningAllowed(level) || player.isSpectator()) {
            return;
        }
        Mutations.second(level, player, interval / 20);
        int stage = Wasteland.stage(level);
        if (level.random.nextDouble() >= RebirthConfig.MUTANTS_CHANCE.get() * stageFactor(stage)) {
            return;
        }
        trySpawn(level, player.blockPosition(), level.random);
    }

    /** How the stage of the Wasteland multiplies the chance of a try and the limit near a player. */
    public static float stageFactor(int stage) {
        return WastelandConfig.byStage(WastelandConfig.STAGE_MUTANTS, stage);
    }

    /**
     * Whether mutants may come now at all: switched on, not peaceful, and the world lets creatures spawn (1.7.3: the game rule
     * doMobSpawning was ignored, so mutants came where an operator had turned spawning off).
     */
    public static boolean spawningAllowed(ServerLevel level) {
        return MutantEntity.enabled() && level.getDifficulty() != Difficulty.PEACEFUL
                && level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING);
    }

    /** Whether mutants live here: a contaminated chunk or the contamination zone. */
    public static boolean contaminated(ServerLevel level, BlockPos pos) {
        if (!ContaminationZones.readingAt(level, pos).isNone()) {
            return true;
        }
        Structure zone = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ProjectRebirth.id(StructureSwitches.ZONE));
        return zone != null && level.structureManager().getStructureAt(pos, zone).isValid();
    }

    public static List<MutantSpawnRule> rules(ServerLevel level) {
        return level.registryAccess().registry(RULES).map(registry -> registry.stream().toList()).orElse(List.of());
    }

    /** How many mutants the level holds now (loaded ones). */
    public static int inWorld(ServerLevel level) {
        int[] count = {0};
        level.getEntities(EntityTypeTest.forClass(MutantEntity.class), mutant -> {
            count[0]++;
            return false;
        });
        return count[0];
    }

    /** The rules that fit the player's place now. Public for tests. */
    public static List<MutantSpawnRule> fitting(ServerLevel level, BlockPos around) {
        boolean night = level.dimensionType().hasSkyLight() && level.isNight();
        int stage = Wasteland.stage(level);
        EnvironmentState state = EnvironmentState.get(level);
        String catastrophe = state.strengthNow() > 0.0f ? state.event() : null;
        boolean here = contaminated(level, around);
        List<MutantSpawnRule> out = new ArrayList<>();
        for (MutantSpawnRule rule : rules(level)) {
            if (rule.weight() > 0 && RebirthConfig.mutantEnabled(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(rule.entity()).getPath())
                    && rule.fitsNow(night, stage, catastrophe, here)
                    && rule.structures().map(set -> insideAny(level, around, set)).orElse(true)) {
                out.add(rule);
            }
        }
        return out;
    }

    /** Whether a place is inside one of these structures (anywhere in its bounds, also the streets of a village). */
    public static boolean insideAny(ServerLevel level, BlockPos pos, net.minecraft.core.HolderSet<net.minecraft.world.level.levelgen.structure.Structure> structures) {
        for (var structure : structures) {
            if (level.structureManager().getStructureAt(pos, structure.value()).isValid()) {
                return true;
            }
        }
        return false;
    }

    /** How many mutants count within 48 blocks: a rat of a swarm counts as a part of one (1.9.0, block D). */
    public static float population(ServerLevel level, AABB near) {
        float total = 0.0f;
        for (MutantEntity mutant : level.getEntitiesOfClass(MutantEntity.class, near)) {
            total += mutant.populationWeight();
        }
        return total;
    }

    /** One try near a place; returns how many mutants came. Public for tests. */
    public static int trySpawn(ServerLevel level, BlockPos around, RandomSource random) {
        AABB near = new AABB(around).inflate(NEAR);
        int stage = Wasteland.stage(level);
        int nearCap = Math.round(RebirthConfig.MUTANTS_CAP.get() * stageFactor(stage));
        if (population(level, near) >= nearCap || inWorld(level) >= RebirthConfig.MUTANTS_WORLD_CAP.get()) {
            return 0;
        }
        List<MutantSpawnRule> fitting = fitting(level, around);
        int total = fitting.stream().mapToInt(MutantSpawnRule::weight).sum();
        if (total <= 0) {
            return 0;
        }
        int pick = random.nextInt(total);
        MutantSpawnRule rule = fitting.get(0);
        for (MutantSpawnRule candidate : fitting) {
            pick -= candidate.weight();
            if (pick < 0) {
                rule = candidate;
                break;
            }
        }
        if (random.nextFloat() >= rule.chance()) {
            return 0;
        }
        EntityType<?> type = rule.entity();
        if (rule.maxNearby() > 0 && level.getEntities(type, near, entity -> true).size() >= rule.maxNearby()) {
            return 0;
        }
        int count = Mth.randomBetweenInclusive(random, Math.max(1, rule.groupMin()), Math.max(rule.groupMin(), rule.groupMax()));
        for (int attempt = 0; attempt < 8; attempt++) {
            BlockPos ground = place(level, around, rule, random);
            if (ground == null || !fits(level, ground, type, rule)) {
                continue;
            }
            int spawned = 0;
            for (int i = 0; i < count; i++) {
                BlockPos at = i == 0 ? ground : ground.offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
                if (i > 0 && !fits(level, at, type, rule)) {
                    at = ground;
                }
                Entity entity = type.create(level, null, at, MobSpawnType.EVENT, false, false);
                if (entity != null) {
                    entity.setYRot(random.nextFloat() * 360.0f);
                    level.addFreshEntityWithPassengers(entity);
                    spawned++;
                }
            }
            return spawned;
        }
        return 0;
    }

    /** A place for the rule's placement 16 to 32 blocks from the player, or null. */
    private static BlockPos place(ServerLevel level, BlockPos around, MutantSpawnRule rule, RandomSource random) {
        double angle = random.nextDouble() * Math.PI * 2.0, distance = 16.0 + random.nextDouble() * 16.0;
        int x = around.getX() + Mth.floor(Math.cos(angle) * distance), z = around.getZ() + Mth.floor(Math.sin(angle) * distance);
        if (!level.hasChunkAt(new BlockPos(x, around.getY(), z))) {
            return null;
        }
        switch (rule.placement()) {
            case MutantSpawnRule.UNDERGROUND -> {
                // a cave near the player's height: air over a solid floor, under the sky nowhere; not for a player far above its depth
                if (around.getY() > rule.maxY() + 16 || around.getY() < rule.minY() - 16) {
                    return null;
                }
                int y = Mth.clamp(around.getY() + random.nextInt(17) - 8, rule.minY(), rule.maxY());
                for (int down = 0; down < 12; down++) {
                    BlockPos at = new BlockPos(x, y - down, z);
                    if (level.isEmptyBlock(at) && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP) && !level.canSeeSky(at)) {
                        return at;
                    }
                }
                return null;
            }
            case MutantSpawnRule.WATER -> {
                BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z)).below();
                return level.getFluidState(top).isSource() ? top : null;
            }
            default -> {
                return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            }
        }
    }

    /** Solid ground (or water for a swimmer), the height, the biome, the light, and room for the body. */
    private static boolean fits(ServerLevel level, BlockPos at, EntityType<?> type, MutantSpawnRule rule) {
        if (at.getY() < rule.minY() || at.getY() > rule.maxY()) {
            return false;
        }
        if (rule.biomes().isPresent() && !rule.biomes().get().contains(level.getBiome(at))) {
            return false;
        }
        if (level.getMaxLocalRawBrightness(at) > rule.maxLight()) {
            return false;
        }
        FluidState fluid = level.getFluidState(at);
        if (MutantSpawnRule.WATER.equals(rule.placement())) {
            return !fluid.isEmpty();
        }
        BlockPos below = at.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) || !fluid.isEmpty()) {
            return false;
        }
        return level.noCollision(type.getSpawnAABB(at.getX() + 0.5, at.getY(), at.getZ() + 0.5));
    }
}
