package com.projectrebirth.gametest;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.ContaminationMap;
import com.projectrebirth.environment.Environment;
import com.projectrebirth.environment.EnvironmentState;
import com.projectrebirth.environment.HumanBody;
import com.projectrebirth.shelter.CartridgeItem;
import com.projectrebirth.shelter.Contamination;
import com.projectrebirth.shelter.ShelterRegistry;
import com.projectrebirth.water.WaterPollution;
import com.projectrebirth.water.WaterPurifierBlockEntity;
import com.projectrebirth.water.WaterRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Polluted water (1.6.0, block C): water is polluted in a contaminated chunk and under the open sky in a toxic storm, not
 * elsewhere; swimming in it poisons and soils; a drunk bottle poisons; the purifier makes it clean with energy and a cartridge.
 */
@GameTestHolder(PlanTests.SCOUTING)
@PrefixGameTestTemplate(false)
public class WaterGameTests {
    private static final String TEMPLATE = "floor_5x5";

    private static BlockPos water(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, Blocks.WATER);
        return helper.absolutePos(pos);
    }

    @GameTest(template = TEMPLATE, batch = "scouting_water_ground")
    public static void waterIsPollutedOnContaminatedGround(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ContaminationMap map = ContaminationMap.get(level);
        map.clearAll();
        EnvironmentState.get(level).clear();
        BlockPos pos = water(helper);
        helper.assertFalse(WaterPollution.isPolluted(level, pos), "Clean ground, clean water");
        helper.assertFalse(WaterPollution.isPolluted(level, helper.absolutePos(new BlockPos(1, 2, 1))), "Air is no water");
        map.addSpot(pos, 0, 10.0f, 5.0f, level.getGameTime());
        try {
            helper.assertTrue(WaterPollution.isPolluted(level, pos), "Contaminated ground, polluted water");
        } finally {
            map.clearAll();
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "scouting_water_storm", skyAccess = true)
    public static void aToxicStormPollutesWaterUnderTheSky(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ContaminationMap.get(level).clearAll();
        EnvironmentState state = EnvironmentState.get(level);
        BlockPos pos = water(helper);
        state.startActive(Environment.TOXIC_STORM, 1.0f, 600);
        try {
            helper.assertTrue(WaterPollution.underOpenSky(level, pos), "Nothing over the water");
            helper.assertTrue(WaterPollution.isPolluted(level, pos), "The storm falls into it");
            helper.setBlock(new BlockPos(2, 4, 2), Blocks.STONE);
            helper.assertFalse(WaterPollution.isPolluted(level, pos), "Under a roof it stays clean");
        } finally {
            state.clear();
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "scouting_water_swim")
    public static void swimmingInPollutedWaterPoisonsAndSoils(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        HumanBody.setBiology(player, com.projectrebirth.environment.Biology.CLEAN);
        Contamination.set(player, 0.0f);
        WaterPollution.swimSecond(player);
        helper.assertTrue(HumanBody.biology(player).toxin() >= RebirthConfig.WATER_SWIM_TOXIN.get().floatValue() - 0.01f,
                "Toxins: " + HumanBody.biology(player).toxin());
        helper.assertTrue(Contamination.of(player) > 0.0f, "And contamination: " + Contamination.of(player));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "scouting_water_drink")
    public static void aDrunkBottleOfPollutedWaterPoisons(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        HumanBody.setBiology(player, com.projectrebirth.environment.Biology.CLEAN);
        ItemStack bottle = new ItemStack(WaterRegistry.POLLUTED_WATER_BOTTLE.get());
        ItemStack left = bottle.getItem().finishUsingItem(bottle, helper.getLevel(), player);
        helper.assertTrue(HumanBody.biology(player).toxin() >= RebirthConfig.WATER_DRINK_TOXIN.get().floatValue() - 0.01f, "Drunk, it poisons");
        helper.assertTrue(left.is(Items.GLASS_BOTTLE), "The glass bottle is left");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = "scouting_water_purifier")
    public static void thePurifierMakesPollutedWaterClean(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, WaterRegistry.WATER_PURIFIER.get());
        WaterPurifierBlockEntity purifier = (WaterPurifierBlockEntity) helper.getBlockEntity(pos);
        purifier.items().setStackInSlot(WaterPurifierBlockEntity.INPUT_SLOT, new ItemStack(WaterRegistry.POLLUTED_WATER_BOTTLE.get(), 2));
        purifier.energy().load(purifier.energy().getMaxEnergyStored());
        helper.runAfterDelay(RebirthConfig.PURIFIER_TICKS.get() + 10, () -> {
            helper.assertTrue(purifier.items().getStackInSlot(WaterPurifierBlockEntity.OUTPUT_SLOT).isEmpty(), "Without a cartridge nothing is cleaned");
            purifier.items().setStackInSlot(WaterPurifierBlockEntity.CARTRIDGE_SLOT, new ItemStack(ShelterRegistry.FILTER_CARTRIDGE.get()));
        });
        helper.runAfterDelay(2 * RebirthConfig.PURIFIER_TICKS.get() + 20, () -> {
            ItemStack out = purifier.items().getStackInSlot(WaterPurifierBlockEntity.OUTPUT_SLOT);
            PotionContents contents = out.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
            helper.assertTrue(out.is(Items.POTION) && contents != null && contents.is(Potions.WATER), "A clean water bottle comes out: " + out);
            helper.assertTrue(purifier.items().getStackInSlot(WaterPurifierBlockEntity.INPUT_SLOT).getCount() == 1, "One bottle of polluted water used");
            helper.assertTrue(CartridgeItem.lifeShare(purifier.items().getStackInSlot(WaterPurifierBlockEntity.CARTRIDGE_SLOT)) < 1.0f, "The cartridge wore a little");
            helper.assertTrue(purifier.energy().getEnergyStored() < purifier.energy().getMaxEnergyStored(), "Energy was used");
            helper.succeed();
        });
    }
}
