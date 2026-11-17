package com.projectrebirth.gametest;

import java.util.List;

import com.projectrebirth.bodies.BodyAttachments;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.PossessionState;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.EnvironmentRegistry;
import com.projectrebirth.environment.HeaterBlockEntity;
import com.projectrebirth.environment.HumanBody;
import com.projectrebirth.environment.Exposure;
import com.projectrebirth.environment.Protection;
import com.projectrebirth.environment.Temperature;
import com.projectrebirth.environment.TemperatureRegistry;
import com.projectrebirth.environment.ThermalLinerRecipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Version 1.5.0, block C: temperature and the human body. {@code .\gradlew runGameTestServer -PgameTests=projectrebirth_power}. */
@GameTestHolder(PlanTests.POWER)
@PrefixGameTestTemplate(false)
public class TemperatureGameTests {
    private static final String TEMPLATE = "floor_5x5";
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    private static void fillBiome(GameTestHelper helper, String biome) {
        BlockPos at = helper.absolutePos(POS);
        // the game blends neighbouring biome cells when it reads one, so fill a few cells all round, inside the world
        helper.getLevel().getServer().getCommands().performPrefixedCommand(helper.getLevel().getServer().createCommandSourceStack().withSuppressedOutput(),
                "fillbiome " + (at.getX() - 8) + " " + Math.max(at.getY() - 2, helper.getLevel().getMinBuildHeight()) + " " + (at.getZ() - 8) + " "
                        + (at.getX() + 8) + " " + (at.getY() + 8) + " " + (at.getZ() + 8) + " " + biome);
    }

    private static ServerPlayer human(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos at = helper.absolutePos(POS);
        player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return player;
    }

    @GameTest(template = TEMPLATE)
    public static void aDesertIsHotAndSnowIsCold(GameTestHelper helper) {
        // midday: the night is colder, and a long run of all the tests may reach it (1.7.0)
        helper.getLevel().setDayTime(6000);
        helper.getLevel().updateSkyBrightness();
        BlockPos at = helper.absolutePos(POS);
        fillBiome(helper, "minecraft:desert");
        float desert = Temperature.withoutHeaters(helper.getLevel(), at);
        fillBiome(helper, "minecraft:frozen_peaks");
        float snow = Temperature.withoutHeaters(helper.getLevel(), at);
        helper.assertTrue(desert > RebirthConfig.TEMP_COMFORT_MAX.get(), "A desert is hot: " + desert);
        helper.assertTrue(snow < RebirthConfig.TEMP_COMFORT_MIN.get(), "Frozen peaks are cold: " + snow);
        // two heights above the filled cells, in the same biome
        float low = Temperature.withoutHeaters(helper.getLevel(), at.above(40));
        float high = Temperature.withoutHeaters(helper.getLevel(), at.above(140));
        helper.assertTrue(high < low, "Higher is colder: " + high + " vs " + low);
        helper.succeed();
    }

    /** Block I: a savanna is as warm as a desert in vanilla; its profile makes it cooler, so that only true deserts are hot. */
    @GameTest(template = TEMPLATE)
    public static void aSavannaIsCoolerThanADesert(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(POS);
        fillBiome(helper, "minecraft:desert");
        float desert = Temperature.withoutHeaters(helper.getLevel(), at);
        fillBiome(helper, "minecraft:savanna");
        float savanna = Temperature.withoutHeaters(helper.getLevel(), at);
        helper.assertTrue(desert > RebirthConfig.TEMP_COMFORT_MAX.get(), "A desert stays hot: " + desert);
        helper.assertTrue(savanna < desert - 4.0f, "A savanna is cooler: " + savanna + " vs " + desert);
        helper.assertTrue(com.projectrebirth.bodies.power.BodyHeat.climateHeat(helper.getLevel(), at)
                < (2.0 - RebirthConfig.HEAT_HOT_BIOME_FROM.get()) * RebirthConfig.HEAT_HOT_BIOME.get(), "and heats a body less than a desert");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void fireAndFurnacesWarm(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(POS);
        float before = Temperature.sources(helper.getLevel(), at);
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.LAVA);
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.CAMPFIRE);
        float after = Temperature.sources(helper.getLevel(), at);
        helper.assertTrue(before == 0.0f && after >= RebirthConfig.TEMP_FIRE.get() + RebirthConfig.TEMP_WARM_BLOCK.get() - 0.001,
                "Lava and a lit campfire warm: " + before + " -> " + after);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void stressGrowsOutsideComfortAndGoesAwayInIt(GameTestHelper helper) {
        float hot = Temperature.afterSecond(0.0f, 45.0f, 0.0f, 0.0f);
        float cold = Temperature.afterSecond(0.0f, -15.0f, 0.0f, 0.0f);
        float comfort = Temperature.afterSecond(40.0f, 20.0f, 0.0f, 0.0f);
        helper.assertTrue(hot > 0.0f, "Heat: " + hot);
        helper.assertTrue(cold < 0.0f, "Cold: " + cold);
        helper.assertTrue(comfort < 40.0f, "Comfort: it goes away, " + comfort);
        helper.assertTrue(Temperature.afterSecond(0.0f, 45.0f, 1.0f, 0.0f) == 0.0f, "Full protection keeps the heat off");
        helper.assertTrue(Temperature.afterSecond(-50.0f, 45.0f, 0.0f, 0.0f) > -50.0f + (45.0f - 30.0f) * 0.02f, "Warmth also helps the body back");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void suitsAndLinersKeepOffHeatAndCold(GameTestHelper helper) {
        ServerPlayer player = human(helper);
        helper.assertTrue(Protection.thermal(player, true) == 0.0f, "Nothing on: no protection");
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(EnvironmentRegistry.HEAVY_SUIT_HELMET.get()));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(EnvironmentRegistry.HEAVY_SUIT.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(EnvironmentRegistry.HEAVY_SUIT_TROUSERS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(EnvironmentRegistry.HEAVY_SUIT_BOOTS.get()));
        float heavy = Protection.thermal(player, false);
        helper.assertTrue(Math.abs(heavy - 0.8f) < 0.01f, "The heavy suit: " + heavy);
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        ItemStack lined = new ItemStack(Items.IRON_CHESTPLATE);
        lined.set(TemperatureRegistry.THERMAL_LINER_INSIDE.get(), Unit.INSTANCE);
        player.setItemSlot(EquipmentSlot.CHEST, lined);
        float liner = Protection.thermal(player, true);
        float expected = RebirthConfig.PROTECTION_CHEST_SHARE.get().floatValue() * RebirthConfig.THERMAL_LINER_PROTECTION.get().floatValue();
        helper.assertTrue(Math.abs(liner - expected) < 0.01f, "A lined iron chestplate: " + liner + ", expected " + expected);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theStagesOfStress(GameTestHelper helper) {
        ServerPlayer player = human(helper);
        double speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        Temperature.setStress(player, 65.0f);
        Temperature.second(player);
        helper.assertTrue(player.hasEffect(TemperatureRegistry.HEATSTROKE), "Heat: the heatstroke icon");
        helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) < speed, "Weak");
        helper.assertTrue(Temperature.healingMultiplier(player) < 1.0f, "Heals slower");
        float exhaustion = player.getFoodData().getExhaustionLevel();
        Temperature.second(player);
        helper.assertTrue(player.getFoodData().getExhaustionLevel() > exhaustion, "Heat makes hungry");

        Temperature.setStress(player, -95.0f);
        Temperature.second(player);
        helper.assertTrue(player.hasEffect(TemperatureRegistry.HYPOTHERMIA) && !player.hasEffect(TemperatureRegistry.HEATSTROKE), "Cold: hypothermia");
        helper.assertTrue(Temperature.stage(Temperature.stress(player)) == 3, "The last stage");
        // block I: at the last stage the damage is not healed away, as with the dose and toxins
        helper.assertTrue(Temperature.healingMultiplier(player) == RebirthConfig.HUMAN_LAST_STAGE_HEALING_MULTIPLIER.get().floatValue(),
                "At the last stage it heals as little as with the last stage of the dose: " + Temperature.healingMultiplier(player));

        Temperature.setStress(player, 10.0f);
        Temperature.second(player);
        helper.assertFalse(player.hasEffect(TemperatureRegistry.HYPOTHERMIA), "Better: no icon");
        helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) == speed, "No longer weak");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void anArtificialBodyDoesNotFeelIt(GameTestHelper helper) {
        ServerPlayer player = human(helper);
        player.setData(BodyAttachments.POSSESSION, PossessionState.of(BodyClass.BASIC_ROBOT));
        Temperature.setStress(player, -50.0f);
        HumanBody.onSecond(player, Exposure.NONE);
        helper.assertTrue(Temperature.stress(player) > -50.0f, "In a body the own stress only goes away: " + Temperature.stress(player));
        helper.assertFalse(player.hasEffect(TemperatureRegistry.HYPOTHERMIA), "No symptoms in a body");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theLinerIsSewnInAndTakenOut(GameTestHelper helper) {
        ThermalLinerRecipe recipe = new ThermalLinerRecipe(CraftingBookCategory.EQUIPMENT);
        CraftingInput sewing = CraftingInput.of(2, 1, List.of(new ItemStack(Items.LEATHER_BOOTS), new ItemStack(TemperatureRegistry.THERMAL_LINER.get())));
        helper.assertTrue(recipe.matches(sewing, helper.getLevel()), "Boots and a liner");
        ItemStack lined = recipe.assemble(sewing, helper.getLevel().registryAccess());
        helper.assertTrue(lined.is(Items.LEATHER_BOOTS) && lined.has(TemperatureRegistry.THERMAL_LINER_INSIDE.get()), "Lined boots");
        CraftingInput alone = CraftingInput.of(1, 1, List.of(lined));
        helper.assertTrue(recipe.matches(alone, helper.getLevel()), "Lined boots alone");
        helper.assertFalse(recipe.assemble(alone, helper.getLevel().registryAccess()).has(TemperatureRegistry.THERMAL_LINER_INSIDE.get()), "The liner comes out");
        NonNullList<ItemStack> left = recipe.getRemainingItems(alone);
        helper.assertTrue(left.get(0).is(TemperatureRegistry.THERMAL_LINER.get()), "And stays in the grid");
        helper.assertFalse(recipe.matches(CraftingInput.of(1, 1, List.of(new ItemStack(Items.LEATHER_BOOTS))), helper.getLevel()), "Plain boots alone: nothing");
        helper.assertFalse(recipe.matches(CraftingInput.of(2, 1, List.of(lined, new ItemStack(TemperatureRegistry.THERMAL_LINER.get()))), helper.getLevel()),
                "Two liners: no");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void aHeaterWarmsAColdPlace(GameTestHelper helper) {
        fillBiome(helper, "minecraft:frozen_peaks");
        BlockPos heaterPos = new BlockPos(1, 2, 1);
        helper.setBlock(heaterPos, TemperatureRegistry.HEATER.get().defaultBlockState());
        HeaterBlockEntity heater = (HeaterBlockEntity) helper.getBlockEntity(heaterPos);
        heater.energy().generate(heater.energy().getMaxEnergyStored());
        BlockPos at = helper.absolutePos(POS);
        float cold = Temperature.at(helper.getLevel(), at);
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(heater.working(), "A cold place: the heater works");
            float warm = Temperature.at(helper.getLevel(), at);
            helper.assertTrue(warm - cold >= RebirthConfig.HEATER_WARMTH.get() - 0.5, "Warmer by its warmth: " + cold + " -> " + warm);
            helper.assertTrue(heater.energy().getEnergyStored() < heater.energy().getMaxEnergyStored(), "It uses energy");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "temperature_off")
    public static void withTheSystemOffNothingChanges(GameTestHelper helper) {
        ServerPlayer player = human(helper);
        Temperature.setStress(player, 95.0f);
        RebirthConfig.TEMP_ENABLED.set(false);
        Temperature.second(player);
        boolean icon = player.hasEffect(TemperatureRegistry.HEATSTROKE);
        float stress = Temperature.stress(player);
        RebirthConfig.TEMP_ENABLED.set(true);
        helper.assertFalse(icon, "Off: no symptoms");
        helper.assertTrue(stress == 95.0f, "Off: the numbers wait");
        helper.succeed();
    }
}
