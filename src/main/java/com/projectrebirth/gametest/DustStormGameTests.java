package com.projectrebirth.gametest;

import java.util.Map;
import java.util.Optional;

import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.BodyAttachments;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyEntities;
import com.projectrebirth.bodies.PossessionState;
import com.projectrebirth.bodies.power.BodyHeat;
import com.projectrebirth.bodies.power.BodyWear;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.Biology;
import com.projectrebirth.environment.Environment;
import com.projectrebirth.environment.EnvironmentProfile;
import com.projectrebirth.environment.EnvironmentRegistry;
import com.projectrebirth.environment.EnvironmentState;
import com.projectrebirth.environment.Exposure;
import com.projectrebirth.environment.Hazard;
import com.projectrebirth.environment.HumanBody;
import com.projectrebirth.environment.MachineBody;
import com.projectrebirth.environment.Protection;
import com.projectrebirth.technology.TechBlocks;
import com.projectrebirth.technology.energy.SolarPanelBlockEntity;
import com.projectrebirth.upgrades.InstalledUpgrades;
import com.projectrebirth.upgrades.UpgradeRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Version 1.5.0, block D: the dust storm. {@code .\gradlew runGameTestServer -PgameTests=projectrebirth_power}. */
@GameTestHolder(PlanTests.POWER)
@PrefixGameTestTemplate(false)
public class DustStormGameTests {
    private static final String TEMPLATE = "floor_5x5";
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    private static Exposure dust(float amount) {
        return Exposure.builder().add(Hazard.DUST, amount).build();
    }

    private static ServerPlayer human(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos at = helper.absolutePos(POS);
        player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return player;
    }

    @GameTest(template = TEMPLATE)
    public static void theDustStormIsInTheCycle(GameTestHelper helper) {
        helper.assertTrue(Environment.EVENTS.contains(Environment.DUST_STORM), "One of the catastrophes");
        helper.assertTrue(Environment.DUST_STORM.equals(EnvironmentState.chooseEvent(helper.getLevel().getRandom(), Environment.DUST_STORM::equals)),
                "It can be chosen by its weight");
        RebirthConfig.EnvironmentEventStats stats = RebirthConfig.environmentEvent(Environment.DUST_STORM);
        helper.assertTrue(stats.enabled().get() && stats.weight().get() > 0 && stats.hazard(Hazard.DUST) > 0.0f, "Switched on, with dust");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void dustIsStrongerInDesertsAndWeakUnderARoof(GameTestHelper helper) {
        EnvironmentProfile desert = new EnvironmentProfile(HolderSet.direct(), 0, Map.of(), Map.of(Environment.DUST_STORM, 1.6f));
        EnvironmentProfile forest = new EnvironmentProfile(HolderSet.direct(), 0, Map.of(), Map.of(Environment.DUST_STORM, 0.4f));
        EnvironmentState state = new EnvironmentState();
        state.startActive(Environment.DUST_STORM, 1.0f, 100);
        float open = Environment.compute(desert, state, Environment.Shelter.NONE).get(Hazard.DUST);
        float woods = Environment.compute(forest, state, Environment.Shelter.NONE).get(Hazard.DUST);
        float roofed = Environment.compute(desert, state, new Environment.Shelter(true, false)).get(Hazard.DUST);
        float deep = Environment.compute(desert, state, new Environment.Shelter(true, true)).get(Hazard.DUST);
        float expected = RebirthConfig.environmentEvent(Environment.DUST_STORM).hazard(Hazard.DUST) * 1.6f;
        helper.assertTrue(Math.abs(open - expected) < 0.001f, "Desert: " + open + ", expected " + expected);
        helper.assertTrue(woods < open, "Weaker in a forest");
        helper.assertTrue(roofed < open && roofed > 0.0f, "A roof weakens it");
        helper.assertTrue(deep == 0.0f, "None deep underground");
        helper.assertTrue(Environment.compute(desert, new EnvironmentState(), Environment.Shelter.NONE).get(Hazard.DUST) == 0.0f, "No storm, no dust");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aPersonWithoutARespiratorSuffers(GameTestHelper helper) {
        ServerPlayer player = human(helper);
        double speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        HumanBody.setBiology(player, Biology.CLEAN);
        HumanBody.onSecond(player, dust(10.0f));
        helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) < speed, "Slowed down in dust");
        helper.assertTrue(HumanBody.biology(player).toxin() >= 0.0f, "Toxins counted");
        float withDust = HumanBody.afterSecond(Biology.CLEAN, 0.0f, 0.0f, 0.0f, 10.0f).toxin();
        float without = HumanBody.afterSecond(Biology.CLEAN, 0.0f, 0.0f, 0.0f, 0.0f).toxin();
        helper.assertTrue(withDust > without, "A little toxic load from dust");
        HumanBody.cough(player);
        helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "A fit of coughing");
        HumanBody.onSecond(player, Exposure.NONE);
        helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) < speed + 1.0e-9, "Out of the dust the dust slowdown goes");
        helper.assertTrue(HumanBody.dangerMask(0.0f, 0.0f, 0.0f, 10.0f) == 1 << Hazard.DUST.ordinal(), "The HUD marks dust");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aRespiratorKeepsDustOutButItsFilterClogs(GameTestHelper helper) {
        ServerPlayer dusty = human(helper);
        ServerPlayer smoky = human(helper);
        for (ServerPlayer player : new ServerPlayer[] {dusty, smoky}) {
            ItemStack respirator = new ItemStack(EnvironmentRegistry.RESPIRATOR.get());
            respirator.set(EnvironmentRegistry.FILTER_LIFE.get(), 1000.0f);
            player.setItemSlot(EquipmentSlot.HEAD, respirator);
        }
        helper.assertTrue(Protection.ofGear(dusty, Hazard.DUST) > 0.5f, "The respirator keeps dust out");
        double speed = dusty.getAttributeValue(Attributes.MOVEMENT_SPEED);
        HumanBody.onSecond(dusty, dust(5.0f));
        HumanBody.onSecond(smoky, Exposure.builder().add(Hazard.POLLUTION, 5.0f).build());
        float dustLeft = Protection.filterLeft(dusty.getItemBySlot(EquipmentSlot.HEAD));
        float smokeLeft = Protection.filterLeft(smoky.getItemBySlot(EquipmentSlot.HEAD));
        helper.assertTrue(dustLeft < smokeLeft, "Dust clogs the filter faster: " + dustLeft + " vs " + smokeLeft);
        helper.assertTrue(dusty.getAttributeValue(Attributes.MOVEMENT_SPEED) == speed, "With a respirator no slowdown");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aBodyWithoutTheSealingModuleWearsAndHeats(GameTestHelper helper) {
        ServerPlayer open = human(helper);
        open.setData(BodyAttachments.POSSESSION, PossessionState.of(BodyClass.BASIC_ROBOT));
        open.setData(UpgradeRegistry.CONTROLLED_BODY, InstalledUpgrades.EMPTY);
        ServerPlayer sealed = human(helper);
        sealed.setData(BodyAttachments.POSSESSION, PossessionState.of(BodyClass.BASIC_ROBOT));
        sealed.setData(UpgradeRegistry.CONTROLLED_BODY, InstalledUpgrades.EMPTY.with(
                new InstalledUpgrades.Installed(Optional.empty(), new ItemStack(UpgradeRegistry.SEALING_MODULE.get()))));
        HumanBody.onSecond(open, dust(10.0f));
        HumanBody.onSecond(sealed, dust(10.0f));
        helper.assertTrue(BodyWear.wear(open) > 0.0f, "Dust wears an open body");
        helper.assertTrue(BodyHeat.heat(open) > 0.0f, "And heats it");
        helper.assertTrue(BodyWear.wear(sealed) == 0.0f && BodyHeat.heat(sealed) == 0.0f, "The sealing module keeps it out");

        ArtificialBodyEntity basic = helper.spawn(BodyEntities.type(BodyClass.BASIC_ROBOT), POS);
        ArtificialBodyEntity premium = helper.spawn(BodyEntities.type(BodyClass.PREMIUM_SYNTHETIC), POS.east());
        MachineBody.dustSecond(basic, BodyClass.BASIC_ROBOT, basic.upgrades(), 10.0f);
        MachineBody.dustSecond(premium, BodyClass.PREMIUM_SYNTHETIC, premium.upgrades(), 10.0f);
        helper.assertTrue(premium.wear() < basic.wear() && premium.wear() > 0.0f, "Better bodies wear less");
        helper.assertTrue(basic.getHealth() == basic.getMaxHealth(), "Dust is wear, not damage");
        helper.assertTrue(RebirthConfig.MACHINE_ARMOR_DUST_WEAR_PER_SECOND.get() == 0.0, "Power armor is not worn by dust by default");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "dust_world")
    public static void aSolarPanelGivesAQuarterInADustStorm(GameTestHelper helper) {
        BlockPos panelPos = new BlockPos(2, 2, 2);
        helper.setBlock(panelPos, TechBlocks.SOLAR_PANEL.get().defaultBlockState());
        SolarPanelBlockEntity panel = helper.getBlockEntity(panelPos);
        EnvironmentState state = EnvironmentState.get(helper.getLevel());
        boolean calm = panel.dusty(helper.getLevel());
        state.startActive(Environment.DUST_STORM, 1.0f, 600);
        boolean storm = panel.dusty(helper.getLevel());
        state.clear();
        helper.assertFalse(calm, "No storm: clear sky");
        helper.assertTrue(storm, "A dust storm darkens the panel's sky");
        helper.assertTrue(RebirthConfig.SOLAR_DUST_FACTOR.get() == 0.25, "A quarter by default");
        helper.succeed();
    }
}
