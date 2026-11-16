package com.projectrebirth.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.structures.StructureSwitches;
import com.projectrebirth.structures.SwitchableSpreadPlacement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
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
 * Version 1.6.0, part B2: the abandoned laboratory, the industrial ruins and the ruined reactor. Their pieces load and hold the
 * chests and the dangers they should; the loot tables give the mod's materials, the reactor also worn bodies and wrecked armor;
 * the config switch keeps a structure out of new chunks. Where they really appear in a world is checked in the game (/locate).
 */
@GameTestHolder(PlanTests.SCOUTING)
@PrefixGameTestTemplate(false)
public class RuinStructuresGameTests {
    private static final String TEMPLATE = "floor_5x5";
    private static final List<String> NAMES = List.of(StructureSwitches.LAB, StructureSwitches.RUINS, StructureSwitches.REACTOR);

    private static StructureTemplate template(GameTestHelper helper, String name) {
        return helper.getLevel().getStructureManager().get(ProjectRebirth.id("ruins/" + name))
                .orElseThrow(() -> new IllegalStateException("No structure piece ruins/" + name));
    }

    private static int count(StructureTemplate template, net.minecraft.world.level.block.Block block) {
        return template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), block).size();
    }

    @GameTest(template = TEMPLATE)
    public static void thePiecesLoadWithTheirChestsAndDangers(GameTestHelper helper) {
        Map<String, int[]> sizes = Map.of(StructureSwitches.LAB, new int[] {15, 9, 13}, StructureSwitches.RUINS, new int[] {25, 14, 21},
                StructureSwitches.REACTOR, new int[] {33, 26, 33});
        for (String name : NAMES) {
            StructureTemplate piece = template(helper, name);
            int[] size = sizes.get(name);
            helper.assertTrue(piece.getSize().getX() == size[0] && piece.getSize().getY() == size[1] && piece.getSize().getZ() == size[2],
                    name + " has the size " + size[0] + "x" + size[1] + "x" + size[2] + ", got " + piece.getSize());
            List<StructureTemplate.StructureBlockInfo> chests = piece.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.CHEST);
            helper.assertTrue(!chests.isEmpty(), name + " holds chests");
            for (StructureTemplate.StructureBlockInfo chest : chests) {
                helper.assertTrue(chest.nbt() != null && chest.nbt().getString("LootTable").equals(ProjectRebirth.MOD_ID + ":chests/" + name),
                        name + ": every chest fills from its own loot table");
            }
        }
        // the danger grows with the value: toxic barrels in the laboratory, a heap of debris at the reactor's core
        helper.assertTrue(count(template(helper, StructureSwitches.LAB), com.projectrebirth.environment.SourceRegistry.LEAKING_WASTE_BARREL.get()) >= 2,
                "The laboratory has leaking barrels");
        helper.assertTrue(count(template(helper, StructureSwitches.REACTOR), com.projectrebirth.environment.SourceRegistry.RADIOACTIVE_DEBRIS.get()) >= 50,
                "The reactor's core is a heap of radioactive debris");
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
    public static void theLootGivesTheModsTechnologyAndTheReactorWornBodiesAndArmor(GameTestHelper helper) {
        for (String name : NAMES) {
            boolean modItem = false;
            for (int i = 0; i < 30; i++) {
                List<ItemStack> items = roll(helper, name);
                helper.assertTrue(!items.isEmpty(), name + ": a chest is never empty");
                modItem |= items.stream().anyMatch(stack -> stack.getItem().builtInRegistryHolder().key().location().getNamespace().equals(ProjectRebirth.MOD_ID));
            }
            helper.assertTrue(modItem, name + ": the loot holds the mod's materials and parts");
        }
        List<ItemStack> reactor = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            reactor.addAll(roll(helper, StructureSwitches.REACTOR));
        }
        boolean wornBody = reactor.stream().anyMatch(stack -> stack.has(com.projectrebirth.bodies.BodyComponents.STORED_INTEGRITY.get()));
        boolean wreckedArmor = reactor.stream().anyMatch(stack -> stack.has(com.projectrebirth.powerarmor.PowerArmorRegistry.STORED_STRUCTURAL.get())
                && stack.has(com.projectrebirth.powerarmor.PowerArmorRegistry.STORED_CRITICAL.get()));
        helper.assertTrue(wornBody, "The reactor sometimes holds a packed worn body (stored integrity set)");
        helper.assertTrue(wreckedArmor, "The reactor sometimes holds a packed wrecked armor (both integrities set)");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theConfigSwitchKeepsAStructureOutOfNewChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        boolean before = RebirthConfig.STRUCTURES_ENABLED.get();
        try {
            for (String name : NAMES) {
                StructureSet set = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(ProjectRebirth.id(name));
                helper.assertTrue(set != null, "The structure set " + name + " is registered");
                helper.assertTrue(set.placement() instanceof SwitchableSpreadPlacement placement && placement.switchKey().equals(name),
                        name + " is placed by the switchable spread with its own switch");
                SwitchableSpreadPlacement placement = (SwitchableSpreadPlacement) set.placement();
                ChunkPos chunk = placement.getPotentialStructureChunk(state.getLevelSeed(), 1003, 1005); // far from the safe middle of the world (block H)
                RebirthConfig.STRUCTURES_ENABLED.set(true);
                helper.assertTrue(placement.isStructureChunk(state, chunk.x, chunk.z), name + ": switched on, its chunk of the grid takes it");
                RebirthConfig.STRUCTURES_ENABLED.set(false);
                helper.assertFalse(placement.isStructureChunk(state, chunk.x, chunk.z), name + ": switched off, no chunk takes it");
            }
        } finally {
            RebirthConfig.STRUCTURES_ENABLED.set(before);
        }
        helper.succeed();
    }

    @SuppressWarnings("unused")
    private static ResourceLocation id(String path) {
        return ProjectRebirth.id(path);
    }

    /** 1.6.0, block H: no ruin starts close to the middle of the world, where new players appear. */
    @GameTest(template = TEMPLATE, batch = "scouting_safe_spawn")
    public static void noRuinStartsNearTheWorldSpawn(GameTestHelper helper) {
        int radius = com.projectrebirth.config.RebirthConfig.STRUCTURES_SAFE_RADIUS.get();
        helper.assertFalse(com.projectrebirth.structures.StructureSwitches.outsideSafeArea(0, 0), "Not at the middle");
        helper.assertFalse(com.projectrebirth.structures.StructureSwitches.outsideSafeArea(-(radius / 16) + 2, 1), "Not inside the radius");
        helper.assertTrue(com.projectrebirth.structures.StructureSwitches.outsideSafeArea(radius / 16 + 1, 0), "Beyond it, yes");
        helper.assertTrue(com.projectrebirth.structures.StructureSwitches.outsideSafeArea(radius / 16, radius / 16), "Diagonally beyond it, yes");
        helper.succeed();
    }
}
