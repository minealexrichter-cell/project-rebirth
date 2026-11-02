package com.projectrebirth.gametest;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.powerarmor.PowerArmorRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server-side checks for the power armor. Run with {@code .\gradlew runGameTestServer}. */
@GameTestHolder(ProjectRebirth.MOD_ID)
@PrefixGameTestTemplate(false)
public class PowerArmorGameTests {
    // 5x5x5 with a stone floor: the armor is 2.5 blocks tall and GameTest encloses the area in barriers.
    private static final String TEMPLATE = "floor_5x5";
    private static final BlockPos ARMOR_POS = new BlockPos(2, 2, 2);

    private static PowerArmorEntity spawnArmor(GameTestHelper helper) {
        return helper.spawn(PowerArmorRegistry.POWER_ARMOR.get(), ARMOR_POS);
    }

    private static Player pilotIn(GameTestHelper helper, PowerArmorEntity armor) {
        Player pilot = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(pilot.startRiding(armor, true), "The pilot must be able to get in");
        return pilot;
    }

    private static void hit(PowerArmorEntity armor, net.minecraft.world.damagesource.DamageSource source, float amount) {
        armor.invulnerableTime = 0;
        armor.hurt(source, amount);
    }

    private static void near(GameTestHelper helper, String what, float expected, float actual) {
        helper.assertTrue(Math.abs(expected - actual) < 0.01f, what + ": expected " + expected + ", got " + actual);
    }

    private static int dropped(GameTestHelper helper, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(6),
                entity -> entity.getItem().is(item)).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static float maxStructural() {
        return RebirthConfig.ARMOR_MAX_STRUCTURAL.get().floatValue();
    }

    private static float maxCritical() {
        return RebirthConfig.ARMOR_MAX_CRITICAL.get().floatValue();
    }

    @GameTest(template = TEMPLATE)
    public static void pilotIsProtectedAndArmorTakesTheDamage(GameTestHelper helper) {
        PowerArmorEntity armor = spawnArmor(helper);
        Player pilot = pilotIn(helper, armor);

        pilot.hurt(pilot.damageSources().generic(), 6.0f);

        near(helper, "pilot health", 20.0f, pilot.getHealth());
        near(helper, "structural", maxStructural() - 6.0f, armor.getStructural());
        near(helper, "pilot entity reach", 3.0f + RebirthConfig.ARMOR_PILOT_ENTITY_REACH_BONUS.get().floatValue(),
                (float) pilot.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE));
        near(helper, "pilot attack damage", 1.0f + RebirthConfig.ARMOR_PILOT_ATTACK_BONUS.get().floatValue(),
                (float) pilot.getAttributeValue(Attributes.ATTACK_DAMAGE));

        pilot.stopRiding();
        near(helper, "reach after leaving", 3.0f, (float) pilot.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void explosionsHurtCriticalIntegrityMuchMoreThanOtherDamage(GameTestHelper helper) {
        PowerArmorEntity armor = spawnArmor(helper);

        hit(armor, armor.damageSources().explosion(null, null), 10.0f);
        float afterExplosion = armor.getCritical();
        hit(armor, armor.damageSources().generic(), 10.0f);

        near(helper, "critical after explosion", maxCritical() - 10.0f * RebirthConfig.ARMOR_CRITICAL_EXPLOSION_MULTIPLIER.get().floatValue(), afterExplosion);
        near(helper, "critical after generic hit", afterExplosion - 10.0f * RebirthConfig.ARMOR_CRITICAL_OTHER_MULTIPLIER.get().floatValue(), armor.getCritical());
        near(helper, "structural", maxStructural() - 20.0f, armor.getStructural());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void zeroStructuralShutsDownAndLocksThePilot(GameTestHelper helper) {
        PowerArmorEntity armor = spawnArmor(helper);
        Player pilot = pilotIn(helper, armor);
        armor.setIntegrity(3.0f, maxCritical());

        hit(armor, armor.damageSources().generic(), 5.0f);
        pilot.stopRiding();

        helper.assertTrue(armor.isDisabled(), "The armor must shut down at zero structural integrity");
        helper.assertTrue(pilot.getVehicle() == armor, "The pilot must stay locked in a shut-down armor");
        helper.assertTrue(armor.getControllingPassenger() == null, "A shut-down armor must not be controllable");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void emergencyHatchTakesTimeAndCostsCriticalIntegrity(GameTestHelper helper) {
        PowerArmorEntity armor = spawnArmor(helper);
        Player pilot = pilotIn(helper, armor);
        armor.setIntegrity(0.0f, maxCritical());
        armor.setHatchHeld(pilot, true);
        int required = RebirthConfig.ARMOR_HATCH_RELEASE_SECONDS.get() * 20;

        float[] criticalBefore = new float[1];
        helper.runAtTickTime(required / 2, () -> {
            helper.assertTrue(pilot.getVehicle() == armor, "The pilot must still be inside halfway through");
            criticalBefore[0] = armor.getCritical();
        });
        helper.runAtTickTime(required + 10, () -> {
            helper.assertTrue(pilot.getVehicle() == null, "The pilot must be out after holding the hatch key");
            near(helper, "critical", criticalBefore[0] - maxCritical() * RebirthConfig.ARMOR_HATCH_CRITICAL_COST.get().floatValue(),
                    armor.getCritical());
            helper.assertTrue(armor.isDisabled(), "The armor stays shut down");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void rescueToolFreesThePilotFromOutside(GameTestHelper helper) {
        PowerArmorEntity armor = spawnArmor(helper);
        Player pilot = pilotIn(helper, armor);
        armor.setIntegrity(0.0f, maxCritical());
        Player rescuer = helper.makeMockPlayer(GameType.SURVIVAL);
        rescuer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PowerArmorRegistry.RESCUE_TOOL.get()));

        armor.interact(rescuer, InteractionHand.MAIN_HAND);

        helper.assertTrue(pilot.getVehicle() == null, "The rescue tool must free the pilot");
        helper.assertTrue(rescuer.getMainHandItem().getDamageValue() == 1, "The rescue tool must lose durability");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void repairUsesMaterialsAndRestartsTheArmor(GameTestHelper helper) {
        PowerArmorEntity armor = spawnArmor(helper);
        armor.setIntegrity(0.0f, maxCritical() - 10.0f);
        Player mechanic = helper.makeMockPlayer(GameType.SURVIVAL);
        mechanic.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PowerArmorRegistry.REPAIR_WRENCH.get()));
        mechanic.getInventory().add(new ItemStack(Items.IRON_INGOT, 2));
        mechanic.getInventory().add(new ItemStack(PowerArmorRegistry.ARMOR_PLATE.get(), 1));
        Item wrench = PowerArmorRegistry.REPAIR_WRENCH.get();

        armor.interact(mechanic, InteractionHand.MAIN_HAND); // armor plate -> critical
        near(helper, "critical after plate", maxCritical(), armor.getCritical());
        helper.assertTrue(armor.isDisabled(), "Critical repair alone must not restart the armor");
        mechanic.getCooldowns().removeCooldown(wrench);
        armor.interact(mechanic, InteractionHand.MAIN_HAND); // iron -> structural 15
        mechanic.getCooldowns().removeCooldown(wrench);
        armor.interact(mechanic, InteractionHand.MAIN_HAND); // iron -> structural 30, above the restart threshold

        helper.assertTrue(armor.isActive(), "The armor must restart once structural integrity is repaired enough");
        helper.assertTrue(mechanic.getInventory().countItem(Items.IRON_INGOT) == 0, "Both iron ingots must be used");
        helper.assertTrue(mechanic.getInventory().countItem(PowerArmorRegistry.ARMOR_PLATE.get()) == 0, "The armor plate must be used");
        near(helper, "structural", 2 * RebirthConfig.ARMOR_REPAIR_STRUCTURAL_PER_INGOT.get().floatValue(), armor.getStructural());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void criticalZeroLeavesAnUnrepairableWreck(GameTestHelper helper) {
        PowerArmorEntity armor = spawnArmor(helper);
        Player pilot = pilotIn(helper, armor);

        hit(armor, armor.damageSources().explosion(null, null), 1000.0f);

        helper.assertTrue(armor.isWrecked(), "The armor must become a wreck");
        helper.assertTrue(pilot.getVehicle() == null, "The pilot must be thrown out");
        helper.assertTrue(pilot.getHealth() < 20.0f, "The pilot must be hurt by the wreck");
        helper.assertTrue(!pilot.startRiding(armor, false), "Nobody can get into a wreck");

        Player mechanic = helper.makeMockPlayer(GameType.SURVIVAL);
        mechanic.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PowerArmorRegistry.REPAIR_WRENCH.get()));
        mechanic.getInventory().add(new ItemStack(PowerArmorRegistry.ARMOR_PLATE.get(), 5));
        armor.interact(mechanic, InteractionHand.MAIN_HAND);
        helper.assertTrue(armor.isWrecked() && armor.getCritical() == 0.0f, "A wreck must not be repairable");
        helper.assertTrue(mechanic.getInventory().countItem(PowerArmorRegistry.ARMOR_PLATE.get()) == 5, "No plates may be used on a wreck");

        mechanic.getCooldowns().removeCooldown(PowerArmorRegistry.REPAIR_WRENCH.get());
        mechanic.setShiftKeyDown(true);
        armor.interact(mechanic, InteractionHand.MAIN_HAND);
        helper.assertTrue(armor.isRemoved(), "Dismantling must remove the wreck");
        helper.assertTrue(dropped(helper, Items.IRON_INGOT) == RebirthConfig.ARMOR_DISMANTLE_IRON.get(), "Dismantling must give iron");
        helper.assertTrue(dropped(helper, PowerArmorRegistry.ARMOR_PLATE.get()) == RebirthConfig.ARMOR_DISMANTLE_PLATES.get(), "Dismantling must give plates");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void packingUpKeepsDamageAndGivesOneItem(GameTestHelper helper) {
        PowerArmorEntity armor = spawnArmor(helper);
        armor.setIntegrity(60.0f, 40.0f);
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PowerArmorRegistry.REPAIR_WRENCH.get()));
        owner.setShiftKeyDown(true);

        armor.interact(owner, InteractionHand.MAIN_HAND);
        owner.getCooldowns().removeCooldown(PowerArmorRegistry.REPAIR_WRENCH.get());
        armor.interact(owner, InteractionHand.MAIN_HAND);

        helper.assertTrue(armor.isRemoved(), "The armor must be packed up");
        Item armorItem = PowerArmorRegistry.POWER_ARMOR_ITEM.get();
        helper.assertTrue(owner.getInventory().countItem(armorItem) == 1, "Exactly one armor item expected");
        ItemStack packed = owner.getInventory().items.stream().filter(stack -> stack.is(armorItem)).findFirst().orElseThrow();

        owner.setShiftKeyDown(false);
        owner.setItemInHand(InteractionHand.MAIN_HAND, packed);
        BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 2));
        packed.useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)));
        PowerArmorEntity placed = helper.getLevel().getEntitiesOfClass(PowerArmorEntity.class,
                new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(6), PowerArmorEntity::isAlive).getFirst();
        near(helper, "structural after placing", 60.0f, placed.getStructural());
        near(helper, "critical after placing", 40.0f, placed.getCritical());
        helper.succeed();
    }
}
