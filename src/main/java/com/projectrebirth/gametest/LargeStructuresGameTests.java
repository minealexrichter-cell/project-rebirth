package com.projectrebirth.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.RadiationSources;
import com.projectrebirth.environment.SourceRegistry;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.structures.StructureBlocks;
import com.projectrebirth.structures.StructureSwitches;
import com.projectrebirth.structures.SwitchableSpreadPlacement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The large structures of 1.6.0 (after the owner's concept art): the six pieces load with their sizes, chests, decoration and
 * wrecked armors; a wreck from the piece's data comes out as a real armor in the wreck state; the loot tables give the mod's
 * things, the military ones sometimes a weapon; the irradiated sludge radiates; each config switch keeps its structure out of new
 * chunks. Where they really appear and how they look is checked in the game.
 */
@GameTestHolder(PlanTests.SCOUTING)
@PrefixGameTestTemplate(false)
public class LargeStructuresGameTests {
    private static final String TEMPLATE = "floor_5x5";
    private static final List<String> NAMES = List.of(StructureSwitches.MILITARY, StructureSwitches.SCIENCE, StructureSwitches.FACTORY,
            StructureSwitches.COMM, StructureSwitches.OUTPOST, StructureSwitches.ZONE);
    /** Size, and how many wrecked armors stand in each. */
    private static final Map<String, int[]> SIZES = Map.of(
            StructureSwitches.MILITARY, new int[] {48, 34, 45, 5},
            StructureSwitches.SCIENCE, new int[] {46, 28, 40, 1},
            StructureSwitches.FACTORY, new int[] {54, 38, 40, 2},
            StructureSwitches.COMM, new int[] {36, 48, 36, 1},
            StructureSwitches.OUTPOST, new int[] {40, 22, 32, 1},
            StructureSwitches.ZONE, new int[] {38, 28, 38, 1});

    private static StructureTemplate template(GameTestHelper helper, String name) {
        return helper.getLevel().getStructureManager().get(ProjectRebirth.id("ruins/" + name))
                .orElseThrow(() -> new IllegalStateException("No structure piece ruins/" + name));
    }

    private static int count(StructureTemplate template, Block block) {
        return template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), block).size();
    }

    private static List<CompoundTag> wrecks(GameTestHelper helper, StructureTemplate template) {
        ListTag entities = template.save(new CompoundTag()).getList("entities", Tag.TAG_COMPOUND);
        List<CompoundTag> out = new ArrayList<>();
        for (int i = 0; i < entities.size(); i++) {
            CompoundTag nbt = entities.getCompound(i).getCompound("nbt");
            if (nbt.getString("id").equals(ProjectRebirth.MOD_ID + ":power_armor")) {
                out.add(nbt);
            }
        }
        return out;
    }

    @GameTest(template = TEMPLATE)
    public static void thePiecesLoadWithChestsDecorationAndWrecks(GameTestHelper helper) {
        for (String name : NAMES) {
            StructureTemplate piece = template(helper, name);
            int[] size = SIZES.get(name);
            helper.assertTrue(piece.getSize().getX() == size[0] && piece.getSize().getY() == size[1] && piece.getSize().getZ() == size[2],
                    name + " has the size " + size[0] + "x" + size[1] + "x" + size[2] + ", got " + piece.getSize());
            List<StructureTemplate.StructureBlockInfo> chests = piece.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.CHEST);
            helper.assertTrue(chests.size() >= 3, name + " holds at least three chests");
            for (StructureTemplate.StructureBlockInfo chest : chests) {
                helper.assertTrue(chest.nbt() != null && chest.nbt().getString("LootTable").equals(ProjectRebirth.MOD_ID + ":chests/" + name),
                        name + ": every chest fills from its own loot table");
            }
            helper.assertTrue(wrecks(helper, piece).size() == size[3], name + " has " + size[3] + " wrecked armors, got " + wrecks(helper, piece).size());
        }
        helper.assertTrue(count(template(helper, StructureSwitches.MILITARY), StructureBlocks.WRECKED_TURRET.get()) >= 4, "The base has its turrets");
        helper.assertTrue(count(template(helper, StructureSwitches.MILITARY), StructureBlocks.AMMO_CRATE.get()) >= 10, "The base has its ammo crates");
        helper.assertTrue(count(template(helper, StructureSwitches.SCIENCE), StructureBlocks.SPECIMEN_TANK.get()) >= 10, "The complex has its specimen tanks");
        helper.assertTrue(count(template(helper, StructureSwitches.SCIENCE), StructureBlocks.SERVER_RACK.get()) >= 20, "The complex has its server room");
        helper.assertTrue(count(template(helper, StructureSwitches.COMM), StructureBlocks.WALL_MONITOR.get()) >= 20, "The station has its screen wall");
        helper.assertTrue(count(template(helper, StructureSwitches.ZONE), SourceRegistry.IRRADIATED_SLUDGE.get()) >= 60, "The zone has its sludge pools");
        helper.assertTrue(count(template(helper, StructureSwitches.ZONE), SourceRegistry.RADIOACTIVE_DEBRIS.get()) >= 10, "The zone has its debris");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aWreckFromThePieceComesOutAsAWreckedArmor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (String name : NAMES) {
            for (CompoundTag nbt : wrecks(helper, template(helper, name))) {
                Entity entity = EntityType.create(nbt.copy(), level).orElse(null);
                helper.assertTrue(entity instanceof PowerArmorEntity, name + ": the wreck is a power armor");
                PowerArmorEntity armor = (PowerArmorEntity) entity;
                helper.assertTrue(armor.isWrecked(), name + ": the armor is in the wreck state");
                helper.assertTrue(armor.getStructural() == 0.0f && armor.getCritical() == 0.0f, name + ": both integrities are at zero");
                armor.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theDecorationTurnsWhereItIsPutAndDropsItself(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 2, 2);
        helper.setBlock(at, StructureBlocks.WALL_MONITOR.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        helper.assertTrue(helper.getBlockState(at).getValue(HorizontalDirectionalBlock.FACING) == Direction.EAST, "The monitor keeps its side");
        // the monitor hangs on the west side of its block when it looks east
        var shape = helper.getBlockState(at).getShape(helper.getLevel(), helper.absolutePos(at));
        helper.assertTrue(shape.bounds().minX == 0.0 && shape.bounds().maxX < 0.25, "The monitor's shape turns with it, got " + shape.bounds());
        for (Block block : List.of(StructureBlocks.WALL_MONITOR.get(), StructureBlocks.SERVER_RACK.get(), StructureBlocks.SPECIMEN_TANK.get(),
                StructureBlocks.EMERGENCY_LAMP.get(), StructureBlocks.WRECKED_TURRET.get(), StructureBlocks.AMMO_CRATE.get())) {
            LootTable table = helper.getLevel().getServer().reloadableRegistries().getLootTable(block.getLootTable());
            LootParams params = new LootParams.Builder(helper.getLevel())
                    .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                    .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                    .withParameter(LootContextParams.BLOCK_STATE, block.defaultBlockState())
                    .create(LootContextParamSets.BLOCK);
            List<ItemStack> drops = table.getRandomItems(params);
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(block.asItem()), block + " drops itself");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "scouting_sludge")
    public static void irradiatedSludgeRadiatesAndAPoolAddsUp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RadiationSources.clear(level);
        BlockPos source = new BlockPos(0, 2, 0);
        helper.setBlock(source, SourceRegistry.IRRADIATED_SLUDGE.get());
        BlockPos probe = helper.absolutePos(new BlockPos(3, 2, 0));
        float one = RadiationSources.readingAt(level, probe).radiation();
        float expected = RebirthConfig.SLUDGE_RADIATION.get().floatValue() * (1.0f - 3.0f / RebirthConfig.SLUDGE_RANGE.get());
        helper.assertTrue(Math.abs(one - expected) < 0.02f, "One block of sludge gives " + expected + " three blocks away, got " + one);
        helper.setBlock(new BlockPos(0, 2, 1), SourceRegistry.IRRADIATED_SLUDGE.get());
        float two = RadiationSources.readingAt(level, probe).radiation();
        helper.assertTrue(two > one * 1.5f, "A second block adds to it: " + one + " -> " + two);
        helper.assertTrue(helper.getBlockState(source).getShape(level, helper.absolutePos(source)).bounds().maxY <= 0.125 + 1e-6,
                "The sludge is a thin layer");
        RadiationSources.clear(level);
        helper.succeed();
    }

    private static List<ItemStack> roll(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        LootTable table = level.getServer().reloadableRegistries().getLootTable(
                ResourceKey.create(Registries.LOOT_TABLE, ProjectRebirth.id("chests/" + name)));
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
        return table.getRandomItems(params);
    }

    @GameTest(template = TEMPLATE)
    public static void theLootGivesTheModsThingsAndTheBaseSometimesAWeapon(GameTestHelper helper) {
        for (String name : NAMES) {
            boolean modItem = false;
            for (int i = 0; i < 30; i++) {
                List<ItemStack> items = roll(helper, name);
                helper.assertTrue(!items.isEmpty(), name + ": a chest is never empty");
                modItem |= items.stream().anyMatch(stack -> stack.getItem().builtInRegistryHolder().key().location().getNamespace().equals(ProjectRebirth.MOD_ID));
            }
            helper.assertTrue(modItem, name + ": the loot holds the mod's things");
        }
        boolean weapon = false;
        for (int i = 0; i < 200 && !weapon; i++) {
            weapon = roll(helper, StructureSwitches.MILITARY).stream().anyMatch(stack -> stack.getItem() instanceof com.projectrebirth.weapons.WeaponItem);
        }
        helper.assertTrue(weapon, "The military base sometimes holds a weapon");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void eachSwitchKeepsItsStructureOutOfNewChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        for (String name : NAMES) {
            StructureSet set = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(ProjectRebirth.id(name));
            helper.assertTrue(set != null, "The structure set " + name + " is registered");
            helper.assertTrue(set.placement() instanceof SwitchableSpreadPlacement placement && placement.switchKey().equals(name),
                    name + " is placed by the switchable spread with its own switch");
            SwitchableSpreadPlacement placement = (SwitchableSpreadPlacement) set.placement();
            ChunkPos chunk = placement.getPotentialStructureChunk(state.getLevelSeed(), 2, 4);
            helper.assertTrue(placement.isStructureChunk(state, chunk.x, chunk.z), name + ": switched on, its chunk of the grid takes it");
            var value = switchOf(name);
            boolean before = value.get();
            try {
                value.set(false);
                helper.assertFalse(placement.isStructureChunk(state, chunk.x, chunk.z), name + ": switched off, no chunk takes it");
            } finally {
                value.set(before);
            }
        }
        helper.succeed();
    }

    private static net.neoforged.neoforge.common.ModConfigSpec.BooleanValue switchOf(String name) {
        return switch (name) {
            case StructureSwitches.MILITARY -> RebirthConfig.STRUCTURES_MILITARY;
            case StructureSwitches.SCIENCE -> RebirthConfig.STRUCTURES_SCIENCE;
            case StructureSwitches.FACTORY -> RebirthConfig.STRUCTURES_FACTORY;
            case StructureSwitches.COMM -> RebirthConfig.STRUCTURES_COMM;
            case StructureSwitches.OUTPOST -> RebirthConfig.STRUCTURES_OUTPOST;
            default -> RebirthConfig.STRUCTURES_ZONE;
        };
    }
}
