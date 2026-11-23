package com.projectrebirth.machinemind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.factions.FactionService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/** The plan of the base of a node: the blocks of the templates of its stages about the computer, and where they may be put. */
public final class MindBase {
    /** Natural ground and plants that the drones clear away for the base. */
    public static final TagKey<Block> CLEARABLE = TagKey.create(Registries.BLOCK, ProjectRebirth.id("mind_clearable"));
    /** The blocks of the mind itself. */
    public static final TagKey<Block> MIND_BLOCKS = TagKey.create(Registries.BLOCK, ProjectRebirth.id("mind_blocks"));
    private static final int MAX_SUPPORT = 6;

    private MindBase() {
    }

    /** A block of the plan. */
    public record Cell(BlockPos pos, BlockState state) {
    }

    /** The plan up to a stage, low blocks first. The later stage has the last word on a place. The computer's own place is left out. */
    public static List<Cell> plan(ServerLevel level, BlockPos computer, Direction facing, int stage) {
        Rotation rotation = switch (facing) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        Map<BlockPos, Cell> cells = new LinkedHashMap<>();
        for (MindStage data : MindStage.upTo(level.registryAccess(), stage)) {
            data.template().ifPresent(id -> add(level, id, computer, rotation, cells));
        }
        cells.remove(computer);
        List<Cell> out = new ArrayList<>(cells.values());
        out.sort(Comparator.<Cell>comparingInt(cell -> cell.pos().getY()).thenComparingDouble(cell -> cell.pos().distSqr(computer)));
        return out;
    }

    private static void add(ServerLevel level, ResourceLocation id, BlockPos computer, Rotation rotation, Map<BlockPos, Cell> cells) {
        StructureTemplate template = level.getStructureManager().get(id).orElse(null);
        if (template == null) {
            return;
        }
        CompoundTag tag = template.save(new CompoundTag());
        HolderGetter<Block> blocks = level.holderLookup(Registries.BLOCK);
        List<BlockState> palette = new ArrayList<>();
        for (Tag entry : tag.getList("palette", Tag.TAG_COMPOUND)) {
            palette.add(NbtUtils.readBlockState(blocks, (CompoundTag) entry));
        }
        int middleX = template.getSize().getX() / 2, middleZ = template.getSize().getZ() / 2;
        for (Tag entry : tag.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag block = (CompoundTag) entry;
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            BlockState state = palette.get(block.getInt("state"));
            if (state.isAir() || state.is(Blocks.STRUCTURE_VOID)) {
                continue;
            }
            BlockPos at = computer.offset(StructureTemplate.transform(new BlockPos(pos.getInt(0) - middleX, pos.getInt(1), pos.getInt(2) - middleZ),
                    Mirror.NONE, rotation, BlockPos.ZERO));
            cells.put(at, new Cell(at, state.rotate(level, at, rotation)));
        }
    }

    /** Nothing of a player and nobody's land: air, plants, natural ground, plain blocks of the mind. */
    public static boolean mayReplace(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0.0f) {
            return false;
        }
        return (state.isAir() || state.canBeReplaced() || state.is(CLEARABLE) || state.is(MIND_BLOCKS))
                && FactionService.territoryAt(level.getServer(), level, pos).isEmpty();
    }

    /** Nobody stands there. */
    public static boolean empty(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), e -> e.isAlive() && !(e instanceof MindDroneEntity)).isEmpty();
    }

    /** Whether a block of the plan stands (a gate may be open, a fence joined otherwise: the block counts, not its state). */
    public static boolean built(ServerLevel level, Cell cell) {
        return level.getBlockState(cell.pos()).is(cell.state().getBlock());
    }

    /** A block of the plan that is missing and may be put now. */
    public static boolean canBuild(ServerLevel level, Cell cell) {
        return level.isLoaded(cell.pos()) && !built(level, cell) && !level.getBlockState(cell.pos()).is(MindRegistry.PART.get()) && mayReplace(level, cell.pos())
                && empty(level, cell.pos());
    }

    /** Puts a block of the plan, on a foot of plating if there is no ground under its lowest layer. */
    public static boolean build(ServerLevel level, Cell cell, BlockPos computer) {
        if (!canBuild(level, cell)) {
            return false;
        }
        if (cell.pos().getY() <= computer.getY()) {
            for (int down = 1; down <= MAX_SUPPORT; down++) {
                BlockPos below = cell.pos().below(down);
                BlockState state = level.getBlockState(below);
                if (!(state.isAir() || state.canBeReplaced()) || !mayReplace(level, below)) {
                    break;
                }
                level.setBlock(below, MindRegistry.PLATING.get().defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        BlockState state = Block.updateFromNeighbourShapes(cell.state(), level, cell.pos());
        level.setBlock(cell.pos(), state.isAir() ? cell.state() : state, Block.UPDATE_ALL);
        if (level.getBlockEntity(cell.pos()) instanceof MindModuleBlockEntity module) {
            module.setComputer(computer);
        }
        return true;
    }
}
