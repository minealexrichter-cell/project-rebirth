package com.projectrebirth.structures;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.ChunkPos;

/**
 * A structure laid out of several pieces, the way a village is, so that it grows into the land instead of sitting on one flat
 * plate (1.6.0). The layout (a list of templates at fixed places, all turned together) comes from data. A building ("rigid")
 * stands at the height of the ground under it, and the terrain is shaped only around its own box (the terrain adaptation of the
 * structure, normally beard_thin); a path or a wall ("follow") lies on the ground block by block. After placing, the walls of a
 * building are carried down to the ground with the foundation block, as the woodland mansion does, so nothing hangs in the air.
 * Like the earlier ruins it keeps off water and very steep land.
 */
public class CompoundStructure extends Structure {
    /** One piece of the layout: a template, its corner in the layout, the height of its floor in the template, and whether it follows the ground. */
    public record Part(ResourceLocation template, int x, int z, int floor, boolean follow) {
        public static final Codec<Part> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("template").forGetter(Part::template),
                Codec.INT.fieldOf("x").forGetter(Part::x),
                Codec.INT.fieldOf("z").forGetter(Part::z),
                Codec.intRange(0, 64).optionalFieldOf("floor", 2).forGetter(Part::floor),
                Codec.BOOL.optionalFieldOf("follow", false).forGetter(Part::follow)
        ).apply(instance, Part::new));
    }

    public static final MapCodec<CompoundStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            settingsCodec(instance),
            Part.CODEC.listOf().fieldOf("parts").forGetter(CompoundStructure::parts),
            Codec.intRange(1, 256).fieldOf("size_x").forGetter(s -> s.sizeX),
            Codec.intRange(1, 256).fieldOf("size_z").forGetter(s -> s.sizeZ),
            ResourceKey.codec(Registries.PROCESSOR_LIST).fieldOf("processors").forGetter(s -> s.processors),
            BlockState.CODEC.fieldOf("foundation").forGetter(s -> s.foundation),
            Codec.intRange(0, 64).optionalFieldOf("check_radius", 8).forGetter(s -> s.checkRadius),
            Codec.intRange(0, 64).optionalFieldOf("max_slope", 12).forGetter(s -> s.maxSlope)
    ).apply(instance, CompoundStructure::new));

    /** How far below a building its walls are carried down at most, looking for the ground. */
    private static final int MAX_FOUNDATION = 24;

    private final List<Part> parts;
    private final int sizeX;
    private final int sizeZ;
    private final ResourceKey<StructureProcessorList> processors;
    private final BlockState foundation;
    private final int checkRadius;
    private final int maxSlope;

    public CompoundStructure(StructureSettings settings, List<Part> parts, int sizeX, int sizeZ, ResourceKey<StructureProcessorList> processors,
                             BlockState foundation, int checkRadius, int maxSlope) {
        super(settings);
        this.parts = parts;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.processors = processors;
        this.foundation = foundation;
        this.checkRadius = checkRadius;
        this.maxSlope = maxSlope;
    }

    public List<Part> parts() {
        return parts;
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!DryJigsawStructure.dryAndLevel(context, checkRadius, maxSlope)) {
            return Optional.empty();
        }
        Rotation rotation = Rotation.getRandom(context.random());
        ChunkPos chunk = context.chunkPos();
        BlockPos half = StructureTemplate.transform(new BlockPos(sizeX / 2, 0, sizeZ / 2), Mirror.NONE, rotation, BlockPos.ZERO);
        BlockPos origin = new BlockPos(chunk.getMiddleBlockX() - half.getX(), 0, chunk.getMiddleBlockZ() - half.getZ());
        int centreY = ground(context, chunk.getMiddleBlockX(), chunk.getMiddleBlockZ());
        return Optional.of(new GenerationStub(new BlockPos(chunk.getMiddleBlockX(), centreY, chunk.getMiddleBlockZ()),
                builder -> addPieces(builder, context, rotation, origin)));
    }

    /** The first free height above the ground of the worldgen surface. */
    private static int ground(GenerationContext context, int x, int z) {
        return context.chunkGenerator().getFirstFreeHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
    }

    private void addPieces(StructurePiecesBuilder builder, GenerationContext context, Rotation rotation, BlockPos origin) {
        Holder<StructureProcessorList> list = context.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST).getHolderOrThrow(processors);
        // the paths and walls first: they lie on the ground before the buildings change it
        for (boolean follow : new boolean[] {true, false}) {
            for (Part part : parts) {
                if (part.follow() != follow) {
                    continue;
                }
                SinglePoolElement element = StructurePoolElement.single(part.template().toString(), list)
                        .apply(follow ? StructureTemplatePool.Projection.TERRAIN_MATCHING : StructureTemplatePool.Projection.RIGID);
                BlockPos corner = origin.offset(StructureTemplate.transform(new BlockPos(part.x(), 0, part.z()), Mirror.NONE, rotation, BlockPos.ZERO));
                BoundingBox flat = element.getBoundingBox(context.structureTemplateManager(), corner, rotation);
                int y = follow ? ground(context, flat.getCenter().getX(), flat.getCenter().getZ()) - 1 : floorHeight(context, flat) - part.floor();
                BlockPos pos = corner.atY(y);
                BoundingBox box = element.getBoundingBox(context.structureTemplateManager(), pos, rotation);
                builder.addPiece(new PoolElementStructurePiece(context.structureTemplateManager(), element, pos, follow ? 0 : part.floor(),
                        rotation, box, LiquidSettings.APPLY_WATERLOGGING));
            }
        }
    }

    /** Where a building's floor goes: the middle of the ground heights under its corners and centre, flush with the ground. */
    private static int floorHeight(GenerationContext context, BoundingBox box) {
        int[] heights = {
                ground(context, box.minX(), box.minZ()), ground(context, box.maxX(), box.minZ()), ground(context, box.minX(), box.maxZ()),
                ground(context, box.maxX(), box.maxZ()), ground(context, box.getCenter().getX(), box.getCenter().getZ())};
        Arrays.sort(heights);
        return heights[2] - 1;
    }

    /** Carries the bottom of every building down to the ground, so that on a slope no wall hangs in the air. */
    @Override
    public void afterPlace(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                           BoundingBox chunkBox, ChunkPos chunkPos, PiecesContainer pieces) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int bottom = level.getMinBuildHeight();
        connectCables(level, chunkBox, pieces, cursor);
        for (StructurePiece piece : pieces.pieces()) {
            if (!(piece instanceof PoolElementStructurePiece pool) || pool.getElement().getProjection() != StructureTemplatePool.Projection.RIGID) {
                continue;
            }
            BoundingBox box = piece.getBoundingBox();
            if (!box.intersects(chunkBox)) {
                continue;
            }
            for (int x = Math.max(box.minX(), chunkBox.minX()); x <= Math.min(box.maxX(), chunkBox.maxX()); x++) {
                for (int z = Math.max(box.minZ(), chunkBox.minZ()); z <= Math.min(box.maxZ(), chunkBox.maxZ()); z++) {
                    cursor.set(x, box.minY(), z);
                    BlockState base = level.getBlockState(cursor);
                    if (base.isAir() || base.canBeReplaced() || !base.getFluidState().isEmpty()) {
                        continue; // not under the building
                    }
                    for (int y = box.minY() - 1; y > Math.max(bottom, box.minY() - 1 - MAX_FOUNDATION); y--) {
                        cursor.setY(y);
                        BlockState below = level.getBlockState(cursor);
                        if (!below.isAir() && !below.canBeReplaced() && below.getFluidState().isEmpty()) {
                            break;
                        }
                        level.setBlock(cursor, foundation, 2);
                    }
                }
            }
        }
    }

    /**
     * Gives every cable of the structure in this chunk the connections it really has: pieces stand at different heights and a
     * turned template may not match its neighbour, so a cable only links to the cables and machines that are next to it now.
     */
    private static void connectCables(WorldGenLevel level, BoundingBox chunkBox, PiecesContainer pieces, BlockPos.MutableBlockPos cursor) {
        for (StructurePiece piece : pieces.pieces()) {
            BoundingBox box = piece.getBoundingBox();
            if (!box.intersects(chunkBox)) {
                continue;
            }
            boolean onGround = piece instanceof PoolElementStructurePiece pool
                    && pool.getElement().getProjection() == StructureTemplatePool.Projection.TERRAIN_MATCHING;
            for (int x = Math.max(box.minX(), chunkBox.minX()); x <= Math.min(box.maxX(), chunkBox.maxX()); x++) {
                for (int z = Math.max(box.minZ(), chunkBox.minZ()); z <= Math.min(box.maxZ(), chunkBox.maxZ()); z++) {
                    // a building: its whole box; a piece on the ground: a band around the surface of its column
                    int ground = onGround ? level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, x, z) : 0;
                    int from = onGround ? ground - 3 : box.minY() - 1, to = onGround ? ground + box.getYSpan() + 1 : box.maxY() + 1;
                    for (int y = from; y <= to; y++) {
                        cursor.set(x, y, z);
                        BlockState state = level.getBlockState(cursor);
                        if (state.getBlock() instanceof EmergencyLampBlock) {
                            settleLamp(level, chunkBox, cursor.immutable(), state);
                        } else if (state.getBlock() instanceof com.projectrebirth.technology.energy.CableBlock) {
                            BlockState connected = com.projectrebirth.technology.energy.CableBlock.connectedInWorldgen(state, level, cursor);
                            if (connected != state) {
                                level.setBlock(cursor, connected, 2);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * A lamp whose support was taken by the decay (a wall block turned to air) would hang on nothing: it goes. Only when the support
     * lies in this chunk; a support in a chunk not placed yet is left alone. On the side of a post the lamp gets its bracket.
     */
    private static void settleLamp(WorldGenLevel level, BoundingBox chunkBox, BlockPos pos, BlockState state) {
        Direction toSupport = switch (state.getValue(EmergencyLampBlock.FACE)) {
            case FLOOR -> Direction.DOWN;
            case CEILING -> Direction.UP;
            default -> state.getValue(EmergencyLampBlock.FACING).getOpposite();
        };
        BlockPos supportPos = pos.relative(toSupport);
        if (!chunkBox.isInside(supportPos)) {
            return;
        }
        if (!state.canSurvive(level, pos)) {
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            return;
        }
        boolean post = toSupport.getAxis().isHorizontal() && EmergencyLampBlock.isPost(level.getBlockState(supportPos));
        if (state.getValue(EmergencyLampBlock.POST) != post) {
            level.setBlock(pos, state.setValue(EmergencyLampBlock.POST, post), 2);
        }
    }

    @Override
    public StructureType<?> type() {
        return StructureRegistry.COMPOUND.get();
    }
}
