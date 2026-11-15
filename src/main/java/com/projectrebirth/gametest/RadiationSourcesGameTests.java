package com.projectrebirth.gametest;

import java.util.List;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.Environment;
import com.projectrebirth.environment.Exposure;
import com.projectrebirth.environment.Hazard;
import com.projectrebirth.environment.HumanBody;
import com.projectrebirth.environment.RadiationSources;
import com.projectrebirth.environment.SourceRegistry;
import com.projectrebirth.technology.Metal;
import com.projectrebirth.technology.Station;
import com.projectrebirth.technology.StationBlockEntity;
import com.projectrebirth.technology.TechBlocks;
import com.projectrebirth.technology.TechItems;
import com.projectrebirth.technology.energy.RadioisotopeGeneratorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Block B1 of plan 1.6: source blocks, uranium and the radioisotope generator. Every test has a batch of its own and clears the index
 * of the shared level at the start, so that the sources of other tests are not counted.
 */
@GameTestHolder(PlanTests.SCOUTING)
@PrefixGameTestTemplate(false)
public class RadiationSourcesGameTests {
    private static final String TEMPLATE = "floor_9x9";
    private static final float EPSILON = 0.02f;
    private static final BlockPos SOURCE = new BlockPos(1, 2, 4);

    private static float radiation(GameTestHelper helper, BlockPos relative) {
        return RadiationSources.readingAt(helper.getLevel(), helper.absolutePos(relative)).radiation();
    }

    private static ServerPlayer survivor(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos at = helper.absolutePos(new BlockPos(6, 2, 6));
        player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        player.getInventory().clearContent(); // the Codex of a mock player
        return player;
    }

    @GameTest(template = TEMPLATE, batch = "scouting_src_falloff")
    public static void aSourceFallsOffWithDistanceAndTheAirFollows(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RadiationSources.clear(level);
        helper.setBlock(SOURCE, SourceRegistry.RADIOACTIVE_DEBRIS.get());
        float centre = RebirthConfig.DEBRIS_RADIATION.get().floatValue();
        int range = RebirthConfig.DEBRIS_RANGE.get();
        for (int distance : new int[] {1, 4, 7}) {
            float expected = centre * (1.0f - distance / (float) range);
            float got = radiation(helper, SOURCE.offset(distance, 0, 0));
            helper.assertTrue(Math.abs(got - expected) < EPSILON, "At " + distance + " blocks expected " + expected + ", got " + got);
        }
        helper.assertTrue(radiation(helper, SOURCE.offset(1, 0, 0)) > radiation(helper, SOURCE.offset(4, 0, 0))
                && radiation(helper, SOURCE.offset(4, 0, 0)) > radiation(helper, SOURCE.offset(7, 0, 0)), "Weaker and weaker");
        helper.assertTrue(RadiationSources.readingAt(level, helper.absolutePos(SOURCE.offset(2, 0, 0))).toxin() == 0.0f, "Debris gives no toxins");
        // the shared path of the air: the dosimeter, the HUD and the contamination all read it
        BlockPos near = helper.absolutePos(SOURCE.offset(2, 0, 0));
        float withSource = Environment.ordinaryExposureAt(level, near).get(Hazard.RADIATION);
        RebirthConfig.SOURCES_ENABLED.set(false);
        float without;
        try {
            without = Environment.ordinaryExposureAt(level, near).get(Hazard.RADIATION);
        } finally {
            RebirthConfig.SOURCES_ENABLED.set(true);
        }
        helper.assertTrue(Math.abs(withSource - without - centre * (1.0f - 2.0f / range)) < EPSILON, "The exposure of the place has it: " + without + " -> " + withSource);
        // beyond the range there is nothing
        int saved = RebirthConfig.DEBRIS_RANGE.get();
        RebirthConfig.DEBRIS_RANGE.set(5);
        try {
            helper.assertTrue(radiation(helper, SOURCE.offset(6, 0, 0)) == 0.0f, "Nothing beyond the range");
            helper.assertTrue(radiation(helper, SOURCE.offset(4, 0, 0)) > 0.0f, "Something within it");
        } finally {
            RebirthConfig.DEBRIS_RANGE.set(saved);
        }
        // the barrel is the toxin source
        helper.setBlock(SOURCE.offset(0, 0, 2), SourceRegistry.LEAKING_WASTE_BARREL.get());
        com.projectrebirth.environment.ContaminationMap.Reading barrel = RadiationSources.readingAt(level, helper.absolutePos(SOURCE.offset(0, 0, 3)));
        helper.assertTrue(barrel.toxin() > 0.0f, "The barrel pollutes the air: " + barrel);
        RadiationSources.clear(level);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "scouting_src_wall")
    public static void solidBlocksBetweenShield(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RadiationSources.clear(level);
        helper.setBlock(SOURCE, SourceRegistry.RADIOACTIVE_DEBRIS.get());
        BlockPos target = SOURCE.offset(6, 0, 0);
        float open = radiation(helper, target);
        float wall = RebirthConfig.SOURCES_WALL_FACTOR.get().floatValue();
        helper.assertTrue(open > 0.0f, "Open air: " + open);
        helper.setBlock(SOURCE.offset(3, 0, 0), Blocks.STONE);
        helper.assertTrue(RadiationSources.solidBlocksBetween(level, helper.absolutePos(SOURCE), helper.absolutePos(target)) == 1, "One block on the line");
        helper.assertTrue(Math.abs(radiation(helper, target) - open * wall) < EPSILON, "One block: " + radiation(helper, target) + " vs " + open * wall);
        helper.setBlock(SOURCE.offset(4, 0, 0), Blocks.STONE);
        helper.assertTrue(Math.abs(radiation(helper, target) - open * wall * wall) < EPSILON, "Two blocks: " + radiation(helper, target));
        // blocks that are not whole do not shield
        helper.setBlock(SOURCE.offset(3, 0, 0), Blocks.IRON_BARS);
        helper.setBlock(SOURCE.offset(4, 0, 0), Blocks.OAK_SLAB);
        helper.assertTrue(Math.abs(radiation(helper, target) - open) < EPSILON, "Bars and a slab do not shield: " + radiation(helper, target));
        RadiationSources.clear(level);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "scouting_src_index")
    public static void theIndexFollowsPlacingBreakingAndReloading(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RadiationSources.clear(level);
        BlockPos pos = helper.absolutePos(SOURCE);
        helper.assertTrue(RadiationSources.count(level) == 0 && radiation(helper, SOURCE.offset(1, 0, 0)) == 0.0f, "Empty at first");
        helper.setBlock(SOURCE, SourceRegistry.RADIOACTIVE_DEBRIS.get());
        helper.assertTrue(RadiationSources.isRegistered(level, pos) && RadiationSources.count(level) == 1, "Placing puts it in the index");
        helper.setBlock(SOURCE.offset(0, 0, 2), SourceRegistry.LEAKING_WASTE_BARREL.get());
        helper.assertTrue(RadiationSources.count(level) == 2, "A second source");
        helper.setBlock(SOURCE, Blocks.AIR);
        helper.assertTrue(!RadiationSources.isRegistered(level, pos) && RadiationSources.count(level) == 1, "Breaking takes it out");
        helper.assertTrue(radiation(helper, SOURCE.offset(1, 0, 0)) == 0.0f, "And its radiation is gone");
        // a chunk unloads: the entity is removed; the chunk loads: it is loaded again
        helper.setBlock(SOURCE, SourceRegistry.RADIOACTIVE_DEBRIS.get());
        BlockEntity entity = helper.getBlockEntity(SOURCE);
        entity.setRemoved();
        helper.assertTrue(!RadiationSources.isRegistered(level, pos), "An unloaded chunk takes it out");
        entity.clearRemoved();
        entity.onLoad();
        helper.assertTrue(RadiationSources.isRegistered(level, pos) && radiation(helper, SOURCE.offset(1, 0, 0)) > 0.0f, "A loaded chunk brings it back");
        // the index is lost altogether (a world loaded again): what is loaded registers again
        RadiationSources.clear(level);
        helper.assertTrue(RadiationSources.count(level) == 0, "Index lost");
        entity.onLoad();
        helper.assertTrue(RadiationSources.isRegistered(level, pos), "The entity registers itself when it loads");
        // the generator is a source too
        helper.setBlock(SOURCE.offset(4, 0, 0), TechBlocks.RADIOISOTOPE_GENERATOR.get());
        helper.assertTrue(RadiationSources.isRegistered(level, helper.absolutePos(SOURCE.offset(4, 0, 0))), "The generator is in the index");
        RadiationSources.clear(level);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "scouting_src_items")
    public static void uraniumInTheInventoryRaisesTheDose(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RadiationSources.clear(level);
        ServerPlayer player = survivor(helper);
        Environment.updatePlayer(player);
        float base = Environment.lastExposure(player).get(Hazard.RADIATION);
        HumanBody.onSecond(player, Environment.lastExposure(player));
        float doseBefore = HumanBody.biology(player).dose();
        player.getInventory().add(new ItemStack(TechItems.RAW.get(Metal.URANIUM).get(), 20));
        player.getInventory().add(new ItemStack(TechItems.INGOTS.get(Metal.URANIUM).get(), 10));
        float each = RebirthConfig.URANIUM_ITEM_RADIATION.get().floatValue();
        Environment.updatePlayer(player);
        float with = Environment.lastExposure(player).get(Hazard.RADIATION);
        helper.assertTrue(Math.abs(with - base - 30 * each) < EPSILON, "By the count: " + base + " -> " + with);
        float afterOne = HumanBody.biology(player).dose();
        HumanBody.onSecond(player, Environment.lastExposure(player));
        helper.assertTrue(HumanBody.biology(player).dose() - afterOne > afterOne - doseBefore, "A second with it adds more dose than one without");
        // more than the limit adds no more
        player.getInventory().add(new ItemStack(TechItems.INGOTS.get(Metal.URANIUM).get(), 64));
        player.getInventory().add(new ItemStack(TechItems.INGOTS.get(Metal.URANIUM).get(), 64));
        player.getInventory().add(new ItemStack(TechItems.INGOTS.get(Metal.URANIUM).get(), 64));
        Environment.updatePlayer(player);
        helper.assertTrue(Math.abs(Environment.lastExposure(player).get(Hazard.RADIATION) - base - RebirthConfig.URANIUM_ITEMS_MAX.get().floatValue()) < EPSILON,
                "Up to the limit: " + Environment.lastExposure(player));
        // ores and other metals carried do not count; the switch works
        RebirthConfig.URANIUM_ITEMS_RADIATE.set(false);
        try {
            Environment.updatePlayer(player);
            helper.assertTrue(Math.abs(Environment.lastExposure(player).get(Hazard.RADIATION) - base) < EPSILON, "Switched off: nothing");
        } finally {
            RebirthConfig.URANIUM_ITEMS_RADIATE.set(true);
        }
        player.getInventory().clearContent();
        player.getInventory().add(new ItemStack(TechItems.INGOTS.get(Metal.TIN).get(), 64));
        Environment.updatePlayer(player);
        helper.assertTrue(Math.abs(Environment.lastExposure(player).get(Hazard.RADIATION) - base) < EPSILON, "Tin does not radiate");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "scouting_src_ore")
    public static void uraniumOreRadiatesWeaklyNearby(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RadiationSources.clear(level);
        BlockPos ore = new BlockPos(2, 2, 4);
        helper.setBlock(ore, TechBlocks.ORES.get(Metal.URANIUM).get());
        helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(ore)) == null, "The ore has no block entity");
        helper.assertTrue(RadiationSources.count(level) == 0, "And is not in the index");
        float each = RebirthConfig.URANIUM_ORE_RADIATION.get().floatValue();
        int reach = RebirthConfig.URANIUM_ORE_RANGE.get();
        float next = radiation(helper, ore.offset(1, 0, 0));
        helper.assertTrue(Math.abs(next - each * (1.0f - 1.0f / (reach + 1))) < EPSILON, "Next to it: " + next);
        helper.assertTrue(radiation(helper, ore.offset(3, 0, 0)) > 0.0f && radiation(helper, ore.offset(3, 0, 0)) < next, "Weaker further away");
        helper.assertTrue(radiation(helper, ore.offset(reach + 1, 0, 0)) == 0.0f, "Nothing beyond the reach");
        helper.assertTrue(next < RebirthConfig.HUD_DANGER_RADIATION.get(), "One block is weak, not a danger by itself: " + next);
        // a deepslate block counts too, and a lot of ore together is held to the limit
        helper.setBlock(ore.offset(0, 0, 1), TechBlocks.DEEPSLATE_ORES.get(Metal.URANIUM).get());
        helper.assertTrue(radiation(helper, ore.offset(1, 0, 0)) > next, "Deepslate ore counts too");
        for (int x = 2; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 2, z), TechBlocks.ORES.get(Metal.URANIUM).get());
            }
        }
        helper.assertTrue(radiation(helper, new BlockPos(3, 2, 4)) <= RebirthConfig.URANIUM_ORE_MAX.get() + EPSILON, "Held to the limit");
        // the switch
        RebirthConfig.URANIUM_ORE_RADIATES.set(false);
        try {
            helper.assertTrue(radiation(helper, new BlockPos(3, 2, 4)) == 0.0f, "Switched off: nothing");
        } finally {
            RebirthConfig.URANIUM_ORE_RADIATES.set(true);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "scouting_src_generator")
    public static void theRadioisotopeGeneratorMakesEnergyWithoutFuelAndRadiates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RadiationSources.clear(level);
        BlockPos generatorPos = new BlockPos(4, 2, 2);
        helper.setBlock(generatorPos, TechBlocks.RADIOISOTOPE_GENERATOR.get());
        helper.setBlock(generatorPos.west(), TechBlocks.STATIONS.get(Station.ELECTRONICS_FABRICATOR).get());
        RadioisotopeGeneratorBlockEntity generator = helper.getBlockEntity(generatorPos);
        StationBlockEntity station = helper.getBlockEntity(generatorPos.west());
        float centre = RebirthConfig.ISOTOPE_RADIATION.get().floatValue();
        helper.assertTrue(Math.abs(radiation(helper, generatorPos.east()) - centre * (1.0f - 1.0f / RebirthConfig.ISOTOPE_RANGE.get())) < EPSILON,
                "It radiates around itself: " + radiation(helper, generatorPos.east()));
        helper.runAfterDelay(40, () -> {
            int made = RebirthConfig.ISOTOPE_ENERGY_PER_TICK.get();
            helper.assertTrue(generator.energy().getEnergyStored() + station.energy().getEnergyStored() >= made * 20, "It makes energy with no fuel at all");
            helper.assertTrue(station.energy().getEnergyStored() > 0, "A neighbour takes it");
            helper.assertTrue(generator.signal() >= 0 && !generator.energy().canReceive(), "It takes no energy in");
            // the switch of its radiation: the energy stays
            RebirthConfig.ISOTOPE_RADIATES.set(false);
            try {
                helper.assertTrue(radiation(helper, generatorPos.east()) == 0.0f, "Switched off: it does not radiate");
            } finally {
                RebirthConfig.ISOTOPE_RADIATES.set(true);
            }
            // its energy is saved
            net.minecraft.nbt.CompoundTag saved = generator.saveWithFullMetadata(level.registryAccess());
            helper.assertTrue(saved.getInt("energy") == generator.energy().saved(), "The charge is saved");
            RadiationSources.clear(level);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "scouting_src_off")
    public static void switchedOffThereAreNoSources(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RadiationSources.clear(level);
        ServerPlayer player = survivor(helper);
        helper.setBlock(SOURCE, SourceRegistry.RADIOACTIVE_DEBRIS.get());
        helper.setBlock(SOURCE.offset(0, 0, 1), TechBlocks.ORES.get(Metal.URANIUM).get());
        player.getInventory().add(new ItemStack(TechItems.INGOTS.get(Metal.URANIUM).get(), 30));
        BlockPos near = helper.absolutePos(SOURCE.offset(1, 0, 0));
        float on = Environment.ordinaryExposureAt(level, near).get(Hazard.RADIATION);
        RebirthConfig.SOURCES_ENABLED.set(false);
        try {
            Exposure off = Environment.ordinaryExposureAt(level, near);
            helper.assertTrue(on - off.get(Hazard.RADIATION) > 5.0f, "The sources are gone from the air: " + on + " -> " + off);
            helper.assertTrue(RadiationSources.readingAt(level, near).isNone() && RadiationSources.carriedBy(player) == 0.0f, "And from the items");
            helper.assertTrue(RadiationSources.isRegistered(level, helper.absolutePos(SOURCE)), "The index keeps them for when it is on again");
        } finally {
            RebirthConfig.SOURCES_ENABLED.set(true);
        }
        helper.assertTrue(Math.abs(Environment.ordinaryExposureAt(level, near).get(Hazard.RADIATION) - on) < EPSILON, "On again: the same");
        RadiationSources.clear(level);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "scouting_src_data")
    public static void dropsRecipesAndOres(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 2, 4));
        for (Block block : List.of(SourceRegistry.RADIOACTIVE_DEBRIS.get(), SourceRegistry.LEAKING_WASTE_BARREL.get())) {
            for (int i = 0; i < 20; i++) {
                helper.assertTrue(Block.getDrops(block.defaultBlockState(), level, pos, null).stream().noneMatch(stack -> stack.is(block.asItem())),
                        block + " never drops itself: no one carries a source to another's land");
            }
        }
        helper.assertTrue(Block.getDrops(TechBlocks.RADIOISOTOPE_GENERATOR.get().defaultBlockState(), level, pos, null, null, new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE))
                .stream().anyMatch(stack -> stack.is(TechItems.RADIOISOTOPE_GENERATOR.get())), "The generator drops itself");
        ItemStack silkPick = new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE);
        silkPick.enchant(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH), 1);
        for (Block ore : List.of(TechBlocks.ORES.get(Metal.URANIUM).get(), TechBlocks.DEEPSLATE_ORES.get(Metal.URANIUM).get())) {
            List<ItemStack> drops = Block.getDrops(ore.defaultBlockState(), level, pos, null, null, silkPick);
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(TechItems.RAW.get(Metal.URANIUM).get()), "Silk touch gives raw uranium, not the ore: " + drops);
        }
        helper.assertTrue(level.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("projectrebirth", "radioisotope_generator")).isPresent(), "The generator has a recipe");
        helper.succeed();
    }
}
