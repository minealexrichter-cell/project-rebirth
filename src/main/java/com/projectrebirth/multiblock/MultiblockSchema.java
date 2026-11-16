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
 * @param display  the cell (column, layer, row) where the machine under assembly stands (block B); the middle of the zone without it
 * @param exit     the cell (column, layer, row, may lie outside the plan) a finished machine rolls out to on its own (the heavy dock's
 *                 power armor); without it every result goes into the output slot
 * @param shell    the look of the whole construction (the body workshop): its frames are hidden and a solid hall with thin walls,
 *                 a roof and a glass front is drawn over the cells instead
 */
public record MultiblockSchema(List<List<String>> layers, Map<String, Cell> key, boolean testOnly, Optional<List<Integer>> display,
                               Optional<List<Integer>> exit, Optional<Shell> shell) {

    /**
     * The hall drawn over a whole construction (1.7.0). {@code hide}: the keys whose blocks turn invisible while it stands;
     * {@code walls}: the keys whose cells get a thin wall on each outer side (the front one of glass) and the roof on top;
     * the textures of the walls and roof, of the glass and of the corner posts and supports. {@code front}: "glass" or "wall";
     * {@code windows}: glass in the side walls from layer to layer and row to row (four numbers, both ends included);
     * {@code supports}: the keys whose cells stand on a pillar down through the hidden cells under them (the manipulators).
     */
    public record Shell(String hide, String walls, ResourceLocation wall, ResourceLocation glass, ResourceLocation post, String front,
                        Optional<List<Integer>> windows, String supports) {
        public static final Codec<Shell> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("hide").forGetter(Shell::hide),
                Codec.STRING.fieldOf("walls").forGetter(Shell::walls),
                ResourceLocation.CODEC.fieldOf("wall").forGetter(Shell::wall),
                ResourceLocation.CODEC.fieldOf("glass").forGetter(Shell::glass),
                ResourceLocation.CODEC.fieldOf("post").forGetter(Shell::post),
                Codec.STRING.optionalFieldOf("front", "glass").forGetter(Shell::front),
                Codec.INT.listOf().optionalFieldOf("windows").forGetter(Shell::windows),
                Codec.STRING.optionalFieldOf("supports", "").forGetter(Shell::supports)).apply(i, Shell::new));

        public boolean glassFront() {
            return "glass".equals(front);
        }

        /** Whether the side wall of that cell is a window. */
        public boolean window(int layer, int row) {
            if (windows.isEmpty() || windows.get().size() != 4) {
                return false;
            }
            List<Integer> w = windows.get();
            return layer >= w.get(0) && layer <= w.get(1) && row >= w.get(2) && row <= w.get(3);
        }
    }

    /** The key letter of a cell (a space: any block). */
    public char letter(int layer, int row, int column) {
        return layers.get(layer).get(row).charAt(column);
    }

    /**
     * A point of the plan in the world: the cell (column, layer, row) spans from those numbers to one more, so a point inside it
     * has fractions; turned with the controller like {@link #worldPos}.
     */
    public net.minecraft.world.phys.Vec3 worldPoint(BlockPos controller, Direction facing, double column, double layer, double row) {
        int[] c = controllerCell();
        double dx = column - (c[0] + 0.5), dy = layer - (c[1] + 0.5), dz = row - (c[2] + 0.5);
        double x, z;
        switch (rotation(facing)) {
            case CLOCKWISE_90 -> { x = -dz; z = dx; }
            case CLOCKWISE_180 -> { x = -dx; z = -dz; }
            case COUNTERCLOCKWISE_90 -> { x = dz; z = -dx; }
            default -> { x = dx; z = dz; }
        }
        return new net.minecraft.world.phys.Vec3(controller.getX() + 0.5 + x, controller.getY() + 0.5 + dy, controller.getZ() + 0.5 + z);
    }
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
            Codec.BOOL.optionalFieldOf("test_only", false).forGetter(MultiblockSchema::testOnly),
            Codec.INT.listOf().optionalFieldOf("display").forGetter(MultiblockSchema::display),
            Codec.INT.listOf().optionalFieldOf("exit").forGetter(MultiblockSchema::exit),
            Shell.CODEC.optionalFieldOf("shell").forGetter(MultiblockSchema::shell)).apply(i, MultiblockSchema::new))
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

    /**
     * Where the machine under assembly stands: the bottom middle of the display cell (or of the lowest cells of the zone), in the
     * world, for a controller at that place facing that way.
     */
    /** Where a finished machine rolls out to (the bottom middle of the exit cell), or null if the plan has none. */
    @Nullable
    public net.minecraft.world.phys.Vec3 exitPoint(BlockPos controller, Direction facing) {
        if (exit.isEmpty() || exit.get().size() != 3) {
            return null;
        }
        return net.minecraft.world.phys.Vec3.atBottomCenterOf(worldPos(controller, facing, exit.get().get(0), exit.get().get(1), exit.get().get(2)));
    }

    public net.minecraft.world.phys.Vec3 displayPoint(BlockPos controller, Direction facing) {
        if (display.isPresent() && display.get().size() == 3) {
            BlockPos pos = worldPos(controller, facing, display.get().get(0), display.get().get(1), display.get().get(2));
            return net.minecraft.world.phys.Vec3.atBottomCenterOf(pos);
        }
        double x = 0, z = 0;
        int n = 0, low = Integer.MAX_VALUE;
        for (int y = 0; y < height() && n == 0; y++) {
            for (int r = 0; r < depth(); r++) {
                for (int c = 0; c < width(); c++) {
                    Cell cell = cell(y, r, c);
                    if (cell != null && cell.zone()) {
                        BlockPos pos = worldPos(controller, facing, c, y, r);
                        x += pos.getX() + 0.5;
                        z += pos.getZ() + 0.5;
                        low = Math.min(low, pos.getY());
                        n++;
                    }
                }
            }
        }
        return n == 0 ? net.minecraft.world.phys.Vec3.atBottomCenterOf(controller) : new net.minecraft.world.phys.Vec3(x / n, low, z / n);
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
