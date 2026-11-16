package com.projectrebirth.multiblock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projectrebirth.factions.Faction;
import com.projectrebirth.factions.FactionService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHooks;

/**
 * The plan of a multiblock (1.7.0, block A), read from {@code data/<namespace>/projectrebirth/multiblock/*.json}: layers from
 * the bottom up, each a list of rows from the back to the front as the player sees it standing before the controller, each row
 * a string of keys from the player's left to the right. The key tells what a cell takes: blocks or a tag, the controller, a port
 * or the assembly zone (empty, there the machine is built); a space is any block. The whole plan turns with the controller.
 *
 * @param layers   the layers from the bottom up
 * @param key      what each key stands for
 * @param testOnly a plan of the automated tests: it exists only while game tests are enabled (never in a released game)
 */
public record MultiblockSchema(List<List<String>> layers, Map<String, Cell> key, boolean testOnly) {
    /**
     * What a cell of the plan takes.
     *
     * @param blocks     the blocks that fit (any of them)
     * @param tag        or a block tag that fits
     * @param controller the controller stands here (exactly one cell)
     * @param port       {@code energy} or {@code cargo}: a port of that kind
     * @param zone       the assembly zone: it must stay empty
     */
    public record Cell(List<ResourceLocation> blocks, Optional<ResourceLocation> tag, boolean controller, Optional<String> port, boolean zone) {
        public static final Codec<Cell> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.listOf().optionalFieldOf("blocks", List.of()).forGetter(Cell::blocks),
                ResourceLocation.CODEC.optionalFieldOf("tag").forGetter(Cell::tag),
                Codec.BOOL.optionalFieldOf("controller", false).forGetter(Cell::controller),
                Codec.STRING.optionalFieldOf("port").forGetter(Cell::port),
                Codec.BOOL.optionalFieldOf("zone", false).forGetter(Cell::zone)).apply(i, Cell::new));

        /** Whether a block in the world fits this cell. */
        public boolean fits(BlockState state) {
            if (zone) {
                return state.isAir() || state.canBeReplaced();
            }
            if (controller) {
                return state.getBlock() instanceof MultiblockControllerBlock;
            }
            if (port.isPresent()) {
                return state.getBlock() instanceof PortBlock portBlock && portBlock.kind().equals(port.get());
            }
            Block block = state.getBlock();
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            return blocks.contains(id) || tag.map(t -> state.is(TagKey.create(Registries.BLOCK, t))).orElse(false);
        }

        /** The block the hologram and the pictures show here; air for the zone. */
        public BlockState display() {
            if (zone) {
                return Blocks.AIR.defaultBlockState();
            }
            if (controller) {
                return MultiblockRegistry.CONTROLLER.get().defaultBlockState();
            }
            if (port.isPresent()) {
                return ("cargo".equals(port.get()) ? MultiblockRegistry.CARGO_PORT : MultiblockRegistry.ENERGY_PORT).get().defaultBlockState();
            }
            if (!blocks.isEmpty()) {
                return BuiltInRegistries.BLOCK.get(blocks.get(0)).defaultBlockState();
            }
            return tag.flatMap(t -> BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, t)))
                    .flatMap(set -> set.stream().findFirst()).map(holder -> holder.value().defaultBlockState())
                    .orElse(Blocks.BARRIER.defaultBlockState());
        }
    }

    /** What is wrong at a place: a block missing, a wrong block, the zone not empty, the land of another faction. */
    public enum ProblemKind {
        MISSING, WRONG, BLOCKED, FOREIGN
    }

    /** A wrong place of the construction, with the block it needs (air for the zone). */
    public record Problem(BlockPos pos, ProblemKind kind, BlockState expected) {
    }

    /** A check of the construction: whether it is whole, what is wrong, where its ports and zone are. */
    public record Check(boolean complete, List<Problem> problems, List<BlockPos> energyPorts, List<BlockPos> cargoPorts, List<BlockPos> zone,
                        BoundingBox bounds) {
    }

    public static final Codec<MultiblockSchema> CODEC = RecordCodecBuilder.<MultiblockSchema>create(i -> i.group(
            Codec.STRING.listOf().listOf().fieldOf("layers").forGetter(MultiblockSchema::layers),
            Codec.unboundedMap(Codec.STRING, Cell.CODEC).fieldOf("key").forGetter(MultiblockSchema::key),
            Codec.BOOL.optionalFieldOf("test_only", false).forGetter(MultiblockSchema::testOnly)).apply(i, MultiblockSchema::new))
            .validate(MultiblockSchema::validate);

    private static DataResult<MultiblockSchema> validate(MultiblockSchema schema) {
        int controllers = 0;
        int rows = -1, columns = -1;
        for (List<String> layer : schema.layers) {
            if (rows >= 0 && layer.size() != rows) {
                return DataResult.error(() -> "All layers of a multiblock need the same number of rows");
            }
            rows = layer.size();
            for (String row : layer) {
                if (columns >= 0 && row.length() != columns) {
                    return DataResult.error(() -> "All rows of a multiblock need the same length");
                }
                columns = row.length();
                for (char c : row.toCharArray()) {
                    if (c == ' ') {
                        continue;
                    }
                    Cell cell = schema.key.get(String.valueOf(c));
                    if (cell == null) {
                        return DataResult.error(() -> "No key for '" + c + "' in a multiblock");
                    }
                    if (cell.controller()) {
                        controllers++;
                    }
                }
            }
        }
        int found = controllers;
        return found == 1 ? DataResult.success(schema) : DataResult.error(() -> "A multiblock needs exactly one controller, got " + found);
    }

    /** Whether the plan may be used now: a plan of the tests only while game tests are on. */
    public boolean available() {
        return !testOnly || GameTestHooks.isGametestEnabled();
    }

    public int height() {
        return layers.size();
    }

    public int depth() {
        return layers.isEmpty() ? 0 : layers.get(0).size();
    }

    public int width() {
        return depth() == 0 ? 0 : layers.get(0).get(0).length();
    }

    /** The cell at a place of the plan (layer, row from the back, column from the left), or null for "any". */
    @Nullable
    public Cell cell(int layer, int row, int column) {
        char c = layers.get(layer).get(row).charAt(column);
        return c == ' ' ? null : key.get(String.valueOf(c));
    }

    /** Where the controller is in the plan: column, layer, row. */
    public int[] controllerCell() {
        for (int y = 0; y < height(); y++) {
            for (int z = 0; z < depth(); z++) {
                for (int x = 0; x < width(); x++) {
                    Cell cell = cell(y, z, x);
                    if (cell != null && cell.controller()) {
                        return new int[] {x, y, z};
                    }
                }
            }
        }
        return new int[] {0, 0, 0};
    }

    /**
     * The rotation that turns the plan with a controller facing that way. The plan is drawn for a controller facing south, a
     * player standing south of it and looking north: rows go from north to south, columns from west to east.
     */
    public static Rotation rotation(Direction facing) {
        return switch (facing) {
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /** Where a cell of the plan is in the world, for a controller at that place facing that way. */
    public BlockPos worldPos(BlockPos controller, Direction facing, int column, int layer, int row) {
        int[] c = controllerCell();
        return controller.offset(new BlockPos(column - c[0], layer - c[1], row - c[2]).rotate(rotation(facing)));
    }

    /**
     * Looks at the construction around a controller.
     *
     * @param owner whose construction it is: on the land of a faction they are not in, a block of it does not count (server only)
     */
    public Check check(Level level, BlockPos controller, Direction facing, @Nullable UUID owner) {
        List<Problem> problems = new ArrayList<>();
        List<BlockPos> energy = new ArrayList<>(), cargo = new ArrayList<>(), zone = new ArrayList<>();
        BoundingBox bounds = null;
        for (int y = 0; y < height(); y++) {
            for (int z = 0; z < depth(); z++) {
                for (int x = 0; x < width(); x++) {
                    BlockPos pos = worldPos(controller, facing, x, y, z);
                    bounds = bounds == null ? new BoundingBox(pos) : bounds.encapsulate(pos);
                    Cell cell = cell(y, z, x);
                    if (cell == null) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (cell.zone()) {
                        zone.add(pos);
                    } else if (cell.port().isPresent() && cell.fits(state)) {
                        ("cargo".equals(cell.port().get()) ? cargo : energy).add(pos);
                    }
                    if (!cell.fits(state)) {
                        ProblemKind kind = cell.zone() ? ProblemKind.BLOCKED : state.isAir() || state.canBeReplaced() ? ProblemKind.MISSING : ProblemKind.WRONG;
                        problems.add(new Problem(pos, kind, cell.display()));
                    } else if (owner != null && !cell.zone() && foreign(level, pos, owner)) {
                        problems.add(new Problem(pos, ProblemKind.FOREIGN, cell.display()));
                    }
                }
            }
        }
        return new Check(problems.isEmpty(), problems, energy, cargo, zone, bounds == null ? new BoundingBox(controller) : bounds);
    }

    /** A block of the construction on the land of a faction its owner is not in. */
    private static boolean foreign(Level level, BlockPos pos, UUID owner) {
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        Optional<Faction> land = FactionService.territoryAt(server.getServer(), level, pos);
        return land.isPresent() && !land.get().isMember(owner);
    }
}
