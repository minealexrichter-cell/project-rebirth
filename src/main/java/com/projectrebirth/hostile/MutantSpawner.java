package com.projectrebirth.hostile;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.ContaminationZones;
import com.projectrebirth.structures.StructureSwitches;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Brings mutants to the contaminated lands (1.6.0, part B3c). Every few seconds, for each player standing in a contaminated chunk
 * (a spot left by a catastrophe) or within the contamination zone, it may set a pack of hounds, a ghoul or a brute down on the
 * ground 16 to 32 blocks away, out of the player's sight if it can; not while enough mutants are about, not in peaceful. Mutants
 * far from players vanish like ordinary monsters, so they do not pile up. Numbers in the config ([hostile.mutants]).
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class MutantSpawner {
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
        if (!spawningAllowed(level) || player.isSpectator()
                || level.random.nextDouble() >= RebirthConfig.MUTANTS_CHANCE.get() || !contaminated(level, player.blockPosition())) {
            return;
        }
        trySpawn(level, player.blockPosition(), level.random);
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

    /** One try near a place; returns how many mutants came. Public for tests. */
    public static int trySpawn(ServerLevel level, BlockPos around, RandomSource random) {
        AABB near = new AABB(around).inflate(48.0);
        if (level.getEntitiesOfClass(MutantEntity.class, near).size() >= RebirthConfig.MUTANTS_CAP.get()) {
            return 0;
        }
        int hound = RebirthConfig.MUTANTS_HOUND_WEIGHT.get(), ghoul = RebirthConfig.MUTANTS_GHOUL_WEIGHT.get(), brute = RebirthConfig.MUTANTS_BRUTE_WEIGHT.get();
        int total = hound + ghoul + brute;
        if (total <= 0) {
            return 0;
        }
        int pick = random.nextInt(total);
        EntityType<? extends MutantEntity> type;
        int count = 1;
        if (pick < hound) {
            type = HostileRegistry.MUTATED_HOUND.get();
            int min = RebirthConfig.MUTANTS_PACK_MIN.get(), max = Math.max(min, RebirthConfig.MUTANTS_PACK_MAX.get());
            count = Mth.randomBetweenInclusive(random, min, max);
        } else if (pick < hound + ghoul) {
            type = HostileRegistry.GLOWING_GHOUL.get();
        } else {
            type = HostileRegistry.BRUTE.get();
        }
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0, distance = 16.0 + random.nextDouble() * 16.0;
            int x = around.getX() + Mth.floor(Math.cos(angle) * distance), z = around.getZ() + Mth.floor(Math.sin(angle) * distance);
            if (!level.hasChunkAt(new BlockPos(x, around.getY(), z))) {
                continue;
            }
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (!fits(level, ground, type)) {
                continue;
            }
            int spawned = 0;
            for (int i = 0; i < count; i++) {
                BlockPos at = i == 0 ? ground : level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        ground.offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2));
                if (i > 0 && !fits(level, at, type)) {
                    at = ground;
                }
                MutantEntity mutant = type.create(level, null, at, MobSpawnType.EVENT, false, false);
                if (mutant != null) {
                    mutant.setYRot(random.nextFloat() * 360.0f);
                    level.addFreshEntityWithPassengers(mutant);
                    spawned++;
                }
            }
            return spawned;
        }
        return 0;
    }

    /** Solid ground, no water, and room for the body. */
    private static boolean fits(ServerLevel level, BlockPos at, EntityType<?> type) {
        BlockPos below = at.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP) || !level.getFluidState(at).isEmpty()) {
            return false;
        }
        return level.noCollision(type.getSpawnAABB(at.getX() + 0.5, at.getY(), at.getZ() + 0.5));
    }
}
