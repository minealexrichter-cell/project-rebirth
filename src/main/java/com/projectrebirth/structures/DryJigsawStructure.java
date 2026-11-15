package com.projectrebirth.structures;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

/**
 * A vanilla jigsaw structure that only stands on dry, fairly level ground (1.6.0). The plain jigsaw puts a building on the
 * surface heightmap, which counts water as ground: a laboratory ended up floating on a swamp. Here the place is looked at
 * first, at the middle of the chunk and four points around it: if water lies over the ground at any of them, or the ground
 * rises and falls more than {@code max_slope} blocks, the structure is not placed there (the next grid cell may take it).
 * All the jigsaw fields are read as usual; {@code check_radius} and {@code max_slope} are added.
 */
public class DryJigsawStructure extends Structure {
    public static final MapCodec<DryJigsawStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            JigsawStructure.CODEC.forGetter(DryJigsawStructure::jigsaw),
            Codec.intRange(0, 64).optionalFieldOf("check_radius", 8).forGetter(DryJigsawStructure::checkRadius),
            Codec.intRange(0, 64).optionalFieldOf("max_slope", 6).forGetter(DryJigsawStructure::maxSlope)
    ).apply(instance, DryJigsawStructure::new));

    private final JigsawStructure jigsaw;
    private final int checkRadius;
    private final int maxSlope;

    public DryJigsawStructure(JigsawStructure jigsaw, int checkRadius, int maxSlope) {
        super(new StructureSettings(jigsaw.biomes(), jigsaw.spawnOverrides(), jigsaw.step(), jigsaw.terrainAdaptation()));
        this.jigsaw = jigsaw;
        this.checkRadius = checkRadius;
        this.maxSlope = maxSlope;
    }

    public JigsawStructure jigsaw() {
        return jigsaw;
    }

    public int checkRadius() {
        return checkRadius;
    }

    public int maxSlope() {
        return maxSlope;
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        return dryAndLevel(context) ? jigsaw.findGenerationPoint(context) : Optional.empty();
    }

    /** No water over the ground and no steep slope at the middle of the chunk and four points around it. */
    boolean dryAndLevel(GenerationContext context) {
        return dryAndLevel(context, checkRadius, maxSlope);
    }

    /** The same check for any structure: no fluid over the ground and at most maxSlope blocks of height difference. */
    static boolean dryAndLevel(GenerationContext context, int r, int maxSlope) {
        int x = context.chunkPos().getMiddleBlockX();
        int z = context.chunkPos().getMiddleBlockZ();
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (int[] d : new int[][] {{0, 0}, {r, r}, {r, -r}, {-r, r}, {-r, -r}}) {
            int surface = context.chunkGenerator().getFirstOccupiedHeight(x + d[0], z + d[1], Heightmap.Types.WORLD_SURFACE_WG,
                    context.heightAccessor(), context.randomState());
            int floor = context.chunkGenerator().getFirstOccupiedHeight(x + d[0], z + d[1], Heightmap.Types.OCEAN_FLOOR_WG,
                    context.heightAccessor(), context.randomState());
            if (surface > floor) {
                return false; // water (or another fluid) lies over the ground here
            }
            low = Math.min(low, floor);
            high = Math.max(high, floor);
        }
        return high - low <= maxSlope;
    }

    @Override
    public StructureType<?> type() {
        return StructureRegistry.DRY_JIGSAW.get();
    }
}
