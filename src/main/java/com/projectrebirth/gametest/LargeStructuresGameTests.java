package com.projectrebirth.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.RadiationSources;
import com.projectrebirth.environment.SourceRegistry;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.structures.CompoundStructure;
import com.projectrebirth.structures.EmergencyLampBlock;
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
    /** How many wrecked armors stand in each. */
    private static final Map<String, Integer> WRECKS = Map.of(StructureSwitches.MILITARY, 5, StructureSwitches.SCIENCE, 2,
            StructureSwitches.FACTORY, 3, StructureSwitches.COMM, 1, StructureSwitches.OUTPOST, 1, StructureSwitches.ZONE, 1);

    /** The structure as registered: a compound of pieces. */
    private static CompoundStructure structure(GameTestHelper helper, String name) {
        var structure = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE).get(ProjectRebirth.id(name));
        if (!(structure instanceof CompoundStructure compound)) {
            throw new IllegalStateException(name + " is a compound structure, got " + structure);
        }
        return compound;
    }

    /** All the pieces of a structure. */
    private static List<StructureTemplate> pieces(GameTestHelper helper, String name) {
        List<StructureTemplate> out = new ArrayList<>();
        for (CompoundStructure.Part part : structure(helper, name).parts()) {
            out.add(helper.getLevel().getStructureManager().get(part.template())
                    .orElseThrow(() -> new IllegalStateException("No structure piece " + part.template())));
        }
        return out;
    }

    private static int count(GameTestHelper helper, String name, Block block) {
        return pieces(helper, name).stream().mapToInt(t -> t.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), block).size()).sum();
    }

    /** Whether the block at a position of a piece can hold a lamp: there, not air, and solid, a fence or a stone wall. */
    private static boolean holdsLamp(StructureTemplate piece, BlockPos at) {
        for (StructureTemplate.StructureBlockInfo info : piece.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), net.minecraft.world.level.block.Blocks.AIR, false)) {
            if (info.pos().equals(at)) {
                return false; // air there
            }
        }
        CompoundTag saved = piece.save(new CompoundTag());
        ListTag palette = saved.getList("palette", Tag.TAG_COMPOUND);
        ListTag blocks = saved.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompound(i);
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            if (pos.getInt(0) == at.getX() && pos.getInt(1) == at.getY() && pos.getInt(2) == at.getZ()) {
                String id = palette.getCompound(block.getInt("state")).getString("Name");
                var state = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse(id)).defaultBlockState();
                return !state.isAir() && (EmergencyLampBlock.isPost(state) || state.isSolid());
            }
        }
        return false; // not in the piece: the land there is unknown
    }

    private static List<CompoundTag> wrecks(GameTestHelper helper, String name) {
        List<CompoundTag> out = new ArrayList<>();
        for (StructureTemplate template : pieces(helper, name)) {
            out.addAll(wrecks(template));
        }
        return out;
    }

    private static List<CompoundTag> wrecks(StructureTemplate template) {
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
            List<StructureTemplate> pieces = pieces(helper, name);
            helper.assertTrue(pieces.size() >= 8, name + " is laid out of many pieces, got " + pieces.size());
            int chests = 0;
            for (StructureTemplate piece : pieces) {
                helper.assertTrue(piece.getSize().getX() > 0 && piece.getSize().getZ() > 0, name + ": every piece has blocks");
                for (StructureTemplate.StructureBlockInfo chest : piece.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.CHEST)) {
                    helper.assertTrue(chest.nbt() != null && chest.nbt().getString("LootTable").equals(ProjectRebirth.MOD_ID + ":chests/" + name),
                            name + ": every chest fills from its own loot table");
                    chests++;
                }
            }
            helper.assertTrue(chests >= 3, name + " holds at least three chests, got " + chests);
            helper.assertTrue(wrecks(helper, name).size() == WRECKS.get(name), name + " has " + WRECKS.get(name) + " wrecked armors, got " + wrecks(helper, name).size());
        }
        helper.assertTrue(count(helper, StructureSwitches.MILITARY, StructureBlocks.WRECKED_TURRET.get()) + count(helper, StructureSwitches.MILITARY, StructureBlocks.TURRET.get()) >= 12,
                "The base has its turrets, three blocks each");
        // no lamp of any piece hangs on nothing: what holds it is in the piece, solid, a fence or a stone wall
        for (String name : NAMES) {
            for (StructureTemplate piece : pieces(helper, name)) {
                for (StructureTemplate.StructureBlockInfo lamp : piece.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), StructureBlocks.EMERGENCY_LAMP.get())) {
                    var state = lamp.state();
                    Direction toSupport = switch (state.getValue(EmergencyLampBlock.FACE)) {
                        case FLOOR -> Direction.DOWN;
                        case CEILING -> Direction.UP;
                        default -> state.getValue(EmergencyLampBlock.FACING).getOpposite();
                    };
                    BlockPos at = lamp.pos().relative(toSupport);
                    helper.assertTrue(holdsLamp(piece, at), name + ": the lamp at " + lamp.pos() + " holds on to something at " + at);
                }
            }
        }
        helper.assertTrue(count(helper, StructureSwitches.MILITARY, StructureBlocks.AMMO_CRATE.get()) >= 10, "The base has its ammo crates");
        helper.assertTrue(count(helper, StructureSwitches.MILITARY, com.projectrebirth.shelter.ShelterRegistry.AIRLOCK_DOOR.get()) >= 4, "The base's buildings have airlock doors");
        int tanks = count(helper, StructureSwitches.SCIENCE, StructureBlocks.SPECIMEN_TANK.get());
        int brokenTanks = count(helper, StructureSwitches.SCIENCE, StructureBlocks.BROKEN_SPECIMEN_TANK.get());
        helper.assertTrue(tanks + brokenTanks >= 10, "The complex has its specimen tanks");
        helper.assertTrue(count(helper, StructureSwitches.SCIENCE, StructureBlocks.SERVER_RACK.get())
                + count(helper, StructureSwitches.SCIENCE, StructureBlocks.BROKEN_SERVER_RACK.get()) >= 20, "The complex has its server room");
        helper.assertTrue(count(helper, StructureSwitches.COMM, StructureBlocks.WALL_MONITOR.get())
                + count(helper, StructureSwitches.COMM, StructureBlocks.BROKEN_WALL_MONITOR.get()) >= 20, "The station has its screen wall");
        // ruin: the broken monitors, racks and tanks outnumber the whole ones, the rusted machines the working ones
        int whole = 0, broken = 0, working = 0, rusted = 0;
        for (String name : NAMES) {
            whole += count(helper, name, StructureBlocks.WALL_MONITOR.get()) + count(helper, name, StructureBlocks.SERVER_RACK.get()) + count(helper, name, StructureBlocks.SPECIMEN_TANK.get());
            broken += count(helper, name, StructureBlocks.BROKEN_WALL_MONITOR.get()) + count(helper, name, StructureBlocks.BROKEN_SERVER_RACK.get())
                    + count(helper, name, StructureBlocks.BROKEN_SPECIMEN_TANK.get());
            for (String id : StructureBlocks.RUSTED_IDS) {
                rusted += count(helper, name, StructureBlocks.RUSTED.get(id).get());
                working += count(helper, name, net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ProjectRebirth.id(id)));
            }
        }
        helper.assertTrue(broken > whole, "Broken monitors, racks and tanks prevail: " + broken + " broken, " + whole + " whole");
        helper.assertTrue(rusted >= 2 * working && working > 0, "About four rusted machines to one working: " + rusted + " rusted, " + working + " working");
        helper.assertTrue(count(helper, StructureSwitches.FACTORY, com.projectrebirth.technology.TechBlocks.CABLE.get()) >= 10, "The factory's machines are wired");
        helper.assertTrue(count(helper, StructureSwitches.ZONE, SourceRegistry.IRRADIATED_SLUDGE.get()) >= 60, "The zone has its sludge pools");
        helper.assertTrue(count(helper, StructureSwitches.ZONE, SourceRegistry.RADIOACTIVE_DEBRIS.get()) >= 10, "The zone has its debris");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aWreckFromThePieceComesOutAsAWreckedArmor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // the working guard turrets of the ruins come out as turrets too
        int turrets = 0;
        for (String name : NAMES) {
            for (StructureTemplate piece : pieces(helper, name)) {
                ListTag entities = piece.save(new CompoundTag()).getList("entities", Tag.TAG_COMPOUND);
                for (int i = 0; i < entities.size(); i++) {
                    CompoundTag nbt = entities.getCompound(i).getCompound("nbt");
                    if (nbt.getString("id").equals(ProjectRebirth.MOD_ID + ":guard_turret")) {
                        Entity entity = EntityType.create(nbt.copy(), level).orElse(null);
                        helper.assertTrue(entity instanceof com.projectrebirth.hostile.GuardTurretEntity, name + ": a guard turret of the ruins is a turret");
                        entity.discard();
                        turrets++;
                    }
                }
            }
        }
        helper.assertTrue(turrets >= 3, "The ruins hold working guard turrets: " + turrets);
        for (String name : NAMES) {
            for (CompoundTag nbt : wrecks(helper, name)) {
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
    public static void theDecorationTurnsAndDropsWhatItIsMadeOf(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 2, 2);
        helper.setBlock(at, StructureBlocks.WALL_MONITOR.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        helper.assertTrue(helper.getBlockState(at).getValue(HorizontalDirectionalBlock.FACING) == Direction.EAST, "The monitor keeps its side");
        // the monitor hangs on the west side of its block when it looks east
        var shape = helper.getBlockState(at).getShape(helper.getLevel(), helper.absolutePos(at));
        helper.assertTrue(shape.bounds().minX == 0.0 && shape.bounds().maxX < 0.25, "The monitor's shape turns with it, got " + shape.bounds());
        ItemStack pick = new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE);
        ItemStack silk = pick.copy();
        silk.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH), 1);
        BlockPos where = helper.absolutePos(at);
        // the lamp and the turret come away whole; the rest is taken apart for its parts, unless with silk touch
        for (Block block : List.of(StructureBlocks.EMERGENCY_LAMP.get(), StructureBlocks.WRECKED_TURRET.get())) {
            // the lamp keeps to whatever holds it; here: a fence post
            List<ItemStack> drops = Block.getDrops(block.defaultBlockState(), helper.getLevel(), where, null, null, pick);
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(block.asItem()), block + " drops itself, got " + drops);
        }
        Map<Block, net.minecraft.world.item.Item> part = Map.of(
                StructureBlocks.WALL_MONITOR.get(), com.projectrebirth.technology.TechItems.CIRCUIT_BOARD.get(),
                StructureBlocks.SERVER_RACK.get(), com.projectrebirth.technology.TechItems.CIRCUIT_BOARD.get(),
                StructureBlocks.SPECIMEN_TANK.get(), net.minecraft.world.item.Items.GLASS,
                StructureBlocks.AMMO_CRATE.get(), net.minecraft.world.item.Items.STICK,
                StructureBlocks.BROKEN_SERVER_RACK.get(), net.minecraft.world.item.Items.IRON_NUGGET,
                StructureBlocks.BROKEN_SPECIMEN_TANK.get(), net.minecraft.world.item.Items.IRON_NUGGET,
                StructureBlocks.RUSTED.get("assembly_station").get(), net.minecraft.world.item.Items.IRON_NUGGET);
        for (Map.Entry<Block, net.minecraft.world.item.Item> entry : part.entrySet()) {
            Block block = entry.getKey();
            List<ItemStack> drops = Block.getDrops(block.defaultBlockState(), helper.getLevel(), where, null, null, pick);
            helper.assertTrue(drops.stream().noneMatch(stack -> stack.is(block.asItem())) && drops.stream().anyMatch(stack -> stack.is(entry.getValue())),
                    block + " is taken apart for its parts, got " + drops);
            List<ItemStack> whole = Block.getDrops(block.defaultBlockState(), helper.getLevel(), where, null, null, silk);
            helper.assertTrue(whole.size() == 1 && whole.get(0).is(block.asItem()), block + " comes away whole with silk touch, got " + whole);
        }
        // a cable turned with its structure keeps pointing at the same neighbours
        var cable = com.projectrebirth.technology.TechBlocks.CABLE.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.PipeBlock.NORTH, true).setValue(net.minecraft.world.level.block.PipeBlock.UP, true);
        var turned = cable.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90);
        helper.assertTrue(turned.getValue(net.minecraft.world.level.block.PipeBlock.EAST) && !turned.getValue(net.minecraft.world.level.block.PipeBlock.NORTH)
                && turned.getValue(net.minecraft.world.level.block.PipeBlock.UP), "A turned cable's arm turns with it: north to east");
        // the turret is three blocks: broken at the tower, the base and the barrel go too
        BlockPos base = new BlockPos(3, 2, 3);
        var turret = StructureBlocks.TURRET.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
        helper.setBlock(base, turret.setValue(com.projectrebirth.structures.TurretBlock.PART, com.projectrebirth.structures.TurretBlock.Part.BASE));
        helper.setBlock(base.above(), turret.setValue(com.projectrebirth.structures.TurretBlock.PART, com.projectrebirth.structures.TurretBlock.Part.TOWER));
        helper.setBlock(base.above().north(), turret.setValue(com.projectrebirth.structures.TurretBlock.PART, com.projectrebirth.structures.TurretBlock.Part.BARREL));
        helper.getLevel().destroyBlock(helper.absolutePos(base.above()), false);
        helper.assertTrue(helper.getBlockState(base).isAir() && helper.getBlockState(base.above().north()).isAir(), "The whole turret goes with its tower");
        // a lamp on the side of a stone wall holds on to it, with its bracket
        BlockPos wall = new BlockPos(1, 2, 3);
        helper.setBlock(wall, net.minecraft.world.level.block.Blocks.MOSSY_COBBLESTONE_WALL);
        BlockPos side = wall.east();
        helper.setBlock(side, StructureBlocks.EMERGENCY_LAMP.get().defaultBlockState()
                .setValue(EmergencyLampBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL).setValue(EmergencyLampBlock.FACING, Direction.EAST));
        helper.assertTrue(helper.getBlockState(side).canSurvive(helper.getLevel(), helper.absolutePos(side)), "A lamp holds on to the side of a stone wall");
        var settled = helper.getBlockState(side).updateShape(Direction.WEST, helper.getBlockState(wall), helper.getLevel(), helper.absolutePos(side), helper.absolutePos(wall));
        helper.assertTrue(settled.getValue(EmergencyLampBlock.POST), "On the side of a stone wall the lamp gets its bracket");
        // the lamp stands on a fence post, and hangs on to it
        BlockPos post = new BlockPos(0, 2, 0);
        helper.setBlock(post, net.minecraft.world.level.block.Blocks.DARK_OAK_FENCE);
        helper.setBlock(post.above(), StructureBlocks.EMERGENCY_LAMP.get().defaultBlockState()
                .setValue(EmergencyLampBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
        helper.assertTrue(helper.getBlockState(post.above()).canSurvive(helper.getLevel(), helper.absolutePos(post.above())), "A lamp stands on a fence post");
        boolean ammo = false;
        for (int i = 0; i < 20 && !ammo; i++) {
            ammo = Block.getDrops(StructureBlocks.AMMO_CRATE.get().defaultBlockState(), helper.getLevel(), where, null, null, pick).stream()
                    .anyMatch(stack -> stack.getItem().builtInRegistryHolder().key().location().getPath().contains("ammo") || stack.getItem().builtInRegistryHolder().key().location().getPath().contains("shell"));
        }
        helper.assertTrue(ammo, "An ammo crate holds ammunition");
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
