package com.projectrebirth.gametest;

import java.util.List;
import java.util.UUID;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.garrison.AiCoreItem;
import com.projectrebirth.garrison.DefenceTurretEntity;
import com.projectrebirth.garrison.GarrisonRegistry;
import com.projectrebirth.garrison.MachineCore;
import com.projectrebirth.garrison.PackedMachineItem;
import com.projectrebirth.garrison.TestMachineEntity;
import com.projectrebirth.garrison.TurretItem;
import com.projectrebirth.garrison.TurretMountBlockEntity;
import com.projectrebirth.hostile.MechRocketEntity;
import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.powerarmor.PowerArmorRegistry;
import com.projectrebirth.upgrades.Upgrades;
import com.projectrebirth.weapons.WeaponRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The defence turrets (1.7.0, block G): they stand only on a turret mount, have their own bars, shoot what the rules allow with
 * the mount's energy and ammunition and are silent without them; the rocket turret holds fire near its own and its rockets break
 * no blocks; a turret built in the dock belongs to its core's owner; the stabilizer goes on machines only and narrows the fire;
 * armor plating keeps damage off.
 */
@GameTestHolder(PlanTests.GARRISON)
@PrefixGameTestTemplate(false)
public class TurretGameTests {
    private static final String TEMPLATE = "floor_9x9";

    private static ServerPlayer player(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(id, "turret_test_" + id.toString().substring(0, 6)));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        return player;
    }

    /** A mount at that place with energy and, if given, ammunition. */
    private static TurretMountBlockEntity mount(GameTestHelper helper, BlockPos at, ItemStack ammo) {
        helper.setBlock(at, GarrisonRegistry.TURRET_MOUNT.get());
        TurretMountBlockEntity mount = (TurretMountBlockEntity) helper.getBlockEntity(at);
        mount.energy().generate(RebirthConfig.TURRET_MOUNT_CAPACITY.get());
        if (!ammo.isEmpty()) {
            mount.ammo().insertItem(0, ammo, false);
        }
        return mount;
    }

    private static DefenceTurretEntity turret(GameTestHelper helper, ServerPlayer owner, TurretItem item, BlockPos mount) {
        return (DefenceTurretEntity) item.place(helper.getLevel(), owner, new ItemStack(item), helper.absolutePos(mount.above()), 0.0f);
    }

    /**
     * 1.7.3: a right click on the top of a mount with a turret in the hand sets the turret up (the mount used to answer with its
     * state and keep the click); a click with rockets still loads the mount. Goes the way of a real click (block first, then item).
     */
    @GameTest(template = TEMPLATE)
    public static void aClickWithATurretOnAMountSetsItUp(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        BlockPos at = new BlockPos(4, 1, 4);
        TurretMountBlockEntity mount = mount(helper, at, ItemStack.EMPTY);
        BlockPos abs = helper.absolutePos(at);
        net.minecraft.world.phys.BlockHitResult top = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(abs).add(0.0, 0.5, 0.0), net.minecraft.core.Direction.UP, abs, false);
        owner.moveTo(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 2.5);
        owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(GarrisonRegistry.ROCKET.get(), 4));
        helper.useBlock(at, owner, top);
        helper.assertTrue(mount.ammo().getStackInSlot(0).is(GarrisonRegistry.ROCKET.get()), "Rockets still go into the mount");
        owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(GarrisonRegistry.MACHINE_GUN_TURRET_ITEM.get()));
        helper.useBlock(at, owner, top);
        java.util.List<DefenceTurretEntity> turrets = helper.getLevel().getEntitiesOfClass(DefenceTurretEntity.class,
                new net.minecraft.world.phys.AABB(abs.above()).inflate(1.0));
        helper.assertTrue(turrets.size() == 1, "The click with the turret sets it up on the mount: " + turrets.size());
        helper.assertTrue(owner.getMainHandItem().isEmpty(), "The turret item is used up");
        helper.succeed();
    }

    /** 1.7.3, block F: a blast next to it, a blow with knockback and a push do not throw a turret off its mount. */
    @GameTest(template = TEMPLATE)
    public static void blastsAndBlowsDoNotThrowATurretOffItsMount(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        BlockPos at = new BlockPos(4, 1, 4);
        mount(helper, at, ItemStack.EMPTY);
        DefenceTurretEntity gun = turret(helper, owner, GarrisonRegistry.MACHINE_GUN_TURRET_ITEM.get(), at);
        helper.assertTrue(gun != null, "The turret stands");
        net.minecraft.world.phys.Vec3 home = gun.position();
        net.minecraft.world.phys.Vec3 blast = home.add(1.5, 0.0, 0.5);
        helper.getLevel().explode(null, blast.x, blast.y, blast.z, 2.0f, net.minecraft.world.level.Level.ExplosionInteraction.NONE);
        gun.knockback(2.0, 1.0, 0.0);
        gun.push(1.0, 0.0, 1.0);
        gun.setDeltaMovement(gun.getDeltaMovement().add(1.5, 0.0, -1.5));
        helper.runAfterDelay(20, () -> {
            double moved = gun.position().subtract(home).horizontalDistance();
            helper.assertTrue(moved < 0.01, "The turret stays on its mount: moved " + moved);
            helper.assertTrue(gun.mount() != null, "and still has its mount under it");
            helper.succeed();
        });
    }

    private static Zombie zombie(GameTestHelper helper, BlockPos at) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, at);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        zombie.setPersistenceRequired();
        zombie.setNoAi(true);
        return zombie;
    }

    @GameTest(template = TEMPLATE)
    public static void aTurretStandsOnlyOnAMountWithTheBarsOfItsType(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        mount(helper, new BlockPos(2, 1, 2), ItemStack.EMPTY);
        mount(helper, new BlockPos(6, 1, 6), ItemStack.EMPTY);
        DefenceTurretEntity gun = turret(helper, owner, GarrisonRegistry.MACHINE_GUN_TURRET_ITEM.get(), new BlockPos(2, 1, 2));
        DefenceTurretEntity rocket = turret(helper, owner, GarrisonRegistry.ROCKET_TURRET_ITEM.get(), new BlockPos(6, 1, 6));
        helper.assertTrue(gun != null && rocket != null, "Both stand on their mounts");
        helper.assertTrue(gun.machineCore().maxStructural() == 80.0f && gun.machineCore().maxCritical() == 40.0f
                && gun.machineCore().structural() == 80.0f, "The machine gun: 80 / 40");
        helper.assertTrue(rocket.machineCore().maxStructural() == 100.0f && rocket.machineCore().maxCritical() == 50.0f
                && rocket.machineCore().structural() == 100.0f && rocket.machineCore().critical() == 50.0f, "The rocket turret: 100 / 50, whole");
        helper.assertTrue(gun.mount() != null && !gun.supplied(), "On its mount, which has no ammunition");
        helper.assertTrue(owner.getUUID().equals(gun.machineCore().owner()), "Its placer's");
        gun.discard();
        rocket.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void theMachineGunShootsAZombieWithTheMountsRounds(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        TurretMountBlockEntity mount = mount(helper, new BlockPos(1, 1, 4), new ItemStack(WeaponRegistry.ASSAULT_RIFLE_AMMO.get(), 30));
        DefenceTurretEntity gun = turret(helper, owner, GarrisonRegistry.MACHINE_GUN_TURRET_ITEM.get(), new BlockPos(1, 1, 4));
        int energy = mount.energy().getEnergyStored();
        Zombie zombie = zombie(helper, new BlockPos(7, 2, 4));
        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.getLastHurtByMob() == gun || !zombie.isAlive(), "The turret hits the zombie");
            helper.assertTrue(mount.count(WeaponRegistry.ASSAULT_RIFLE_AMMO.get()) < 30 && mount.energy().getEnergyStored() < energy,
                    "Rounds and energy from the mount");
            gun.discard();
            zombie.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120)
    public static void withoutAmmunitionItIsSilent(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        mount(helper, new BlockPos(1, 1, 4), ItemStack.EMPTY);
        DefenceTurretEntity gun = turret(helper, owner, GarrisonRegistry.MACHINE_GUN_TURRET_ITEM.get(), new BlockPos(1, 1, 4));
        Zombie zombie = zombie(helper, new BlockPos(6, 2, 4));
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(zombie.getHealth() == zombie.getMaxHealth() && !gun.supplied(), "No shot without rounds");
            helper.assertFalse(gun.fire(zombie), "Nothing to fire");
            gun.discard();
            zombie.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void aRocketHoldsFireNearItsOwnAndBreaksNoBlocks(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        TurretMountBlockEntity mount = mount(helper, new BlockPos(1, 1, 1), new ItemStack(GarrisonRegistry.ROCKET.get(), 4));
        DefenceTurretEntity rocket = turret(helper, owner, GarrisonRegistry.ROCKET_TURRET_ITEM.get(), new BlockPos(1, 1, 1));
        Zombie zombie = zombie(helper, new BlockPos(7, 2, 7));
        helper.setBlock(new BlockPos(7, 2, 5), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 2, 7), Blocks.STONE);
        // a machine of the same owner next to the zombie: no rocket
        TestMachineEntity own = helper.spawn(GarrisonRegistry.TEST_MACHINE.get(), new BlockPos(6, 2, 7));
        own.machineCore().setOwner(owner.getUUID(), owner.getGameProfile().getName());
        helper.assertTrue(rocket.friendNear(zombie) && !rocket.launch(zombie) && mount.count(GarrisonRegistry.ROCKET.get()) == 4,
                "No rocket while one of its own is near the target");
        own.discard();
        helper.assertTrue(!rocket.friendNear(zombie) && rocket.launch(zombie) && mount.count(GarrisonRegistry.ROCKET.get()) == 3,
                "A rocket once nobody of its own is near");
        helper.assertFalse(helper.getLevel().getEntitiesOfClass(MechRocketEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16)).isEmpty(),
                "The rocket flies");
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(zombie.getHealth() < zombie.getMaxHealth() || !zombie.isAlive(), "The blast hurts the zombie");
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(7, 2, 5));
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(5, 2, 7));
            helper.assertTrue(rocket.machineCore().structural() == rocket.machineCore().maxStructural(), "Its own rocket does not hurt it");
            rocket.discard();
            zombie.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void aTurretBuiltInTheDockBelongsToItsCoresOwner(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        ItemStack core = new ItemStack(GarrisonRegistry.AI_CORE.get());
        AiCoreItem.bind(core, owner);
        ItemStack built = new ItemStack(GarrisonRegistry.ROCKET_TURRET_ITEM.get());
        PackedMachineItem.bindToCore(built, List.of(new ItemStack(Items.IRON_INGOT), core));
        MachineCore.Packed packed = built.get(GarrisonRegistry.PACKED_MACHINE.get());
        helper.assertTrue(packed != null && packed.owner().orElseThrow().equals(owner.getUUID()) && packed.structural() == 100.0f,
                "The core's owner, whole bars of the rocket turret");
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(com.projectrebirth.ProjectRebirth.id("dock_machine_gun_turret")).isPresent()
                && helper.getLevel().getRecipeManager().byKey(com.projectrebirth.ProjectRebirth.id("dock_rocket_turret")).isPresent(), "Dock recipes");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theStabilizerGoesOnMachinesOnlyAndPlatingKeepsDamageOff(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        mount(helper, new BlockPos(2, 1, 2), ItemStack.EMPTY);
        DefenceTurretEntity gun = turret(helper, owner, GarrisonRegistry.MACHINE_GUN_TURRET_ITEM.get(), new BlockPos(2, 1, 2));
        double spread = gun.spread();
        ItemStack stabilizer = new ItemStack(GarrisonRegistry.STABILIZER.get());
        helper.assertTrue(Upgrades.install(helper.getLevel().registryAccess(), gun, stabilizer, null) == null, "A stabilizer on the turret");
        helper.assertTrue(Math.abs(gun.spread() - spread * RebirthConfig.TURRET_STABILIZER_SPREAD.get()) < 1.0e-6, "Its fire is narrower: " + gun.spread());
        PowerArmorEntity armor = helper.spawn(PowerArmorRegistry.POWER_ARMOR.get(), new BlockPos(6, 2, 6));
        helper.assertTrue(Upgrades.problem(helper.getLevel().registryAccess(), armor, new ItemStack(GarrisonRegistry.STABILIZER.get()),
                com.projectrebirth.upgrades.ArmorSlot.CHEST_FRONT) == Upgrades.Problem.WRONG_HOST, "Not on power armor");
        armor.discard();

        helper.assertTrue(Upgrades.install(helper.getLevel().registryAccess(), gun, new ItemStack(com.projectrebirth.upgrades.UpgradeRegistry.ARMOR_IRON_PLATING.get()), null) == null,
                "Iron plating on the turret");
        helper.assertTrue(Math.abs(gun.platingShare() - RebirthConfig.MACHINE_PLATING_IRON.get()) < 1.0e-6, "It keeps a share off");
        float before = gun.machineCore().structural();
        gun.hurt(helper.getLevel().damageSources().generic(), 10.0f);
        float lost = before - gun.machineCore().structural();
        helper.assertTrue(Math.abs(lost - 10.0f * (1.0f - RebirthConfig.MACHINE_PLATING_IRON.get().floatValue())) < 1.0e-3, "Less damage: " + lost);
        gun.discard();
        helper.succeed();
    }
}
