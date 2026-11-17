package com.projectrebirth.gametest;

import java.util.List;
import java.util.UUID;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.garrison.GarrisonRegistry;
import com.projectrebirth.garrison.GuardMechEntity;
import com.projectrebirth.garrison.MachineTask;
import com.projectrebirth.garrison.ServicePostBlockEntity;
import com.projectrebirth.powerarmor.PowerArmorRegistry;
import com.projectrebirth.weapons.WeaponRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The guard mech and the service post (1.7.0, block H): the mech's bars, nodes from the dock recipe and swapped by hand, its
 * energy and magazine kept when packed; it fights with what it carries, stops when empty, goes to a service post when low and is
 * charged, mended and loaded there (not its critical integrity); the console warns of a narrow patrol route.
 */
@GameTestHolder(PlanTests.GARRISON)
@PrefixGameTestTemplate(false)
public class MechGameTests {
    private static final String TEMPLATE = "floor_9x9";

    private static ServerPlayer player(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(id, "mech_test_" + id.toString().substring(0, 6)));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.setShiftKeyDown(false);
        return player;
    }

    private static GuardMechEntity mech(GameTestHelper helper, ServerPlayer owner, ItemStack stack, BlockPos at) {
        GuardMechEntity mech = (GuardMechEntity) GarrisonRegistry.GUARD_MECH_ITEM.get().place(helper.getLevel(), owner, stack, helper.absolutePos(at), 0.0f);
        helper.assertTrue(mech != null, "The mech stands");
        return mech;
    }

    private static ItemStack built(int right, int left) {
        ItemStack stack = new ItemStack(GarrisonRegistry.GUARD_MECH_ITEM.get());
        stack.set(GarrisonRegistry.MECH_LOADOUT.get(), new GuardMechEntity.Loadout(right, left, 0, 0, 0));
        return stack;
    }

    /** Four plates at (x..x+1, 1, z..z+1), the console west of them, a chest west of the console. */
    private static ServicePostBlockEntity post(GameTestHelper helper, int x, int z) {
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                helper.setBlock(new BlockPos(x + dx, 1, z + dz), GarrisonRegistry.SERVICE_POST_PLATE.get());
            }
        }
        helper.setBlock(new BlockPos(x - 1, 1, z), GarrisonRegistry.SERVICE_POST.get());
        helper.setBlock(new BlockPos(x - 2, 1, z), Blocks.CHEST);
        ServicePostBlockEntity post = (ServicePostBlockEntity) helper.getBlockEntity(new BlockPos(x - 1, 1, z));
        post.energy().generate(RebirthConfig.POST_ENERGY_CAPACITY.get());
        return post;
    }

    @GameTest(template = TEMPLATE)
    public static void aMechHasItsBarsNodesAndKeepsItsLoadWhenPacked(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        GuardMechEntity mech = mech(helper, owner, built(1, 2), new BlockPos(4, 2, 4));
        helper.assertTrue(mech.machineCore().maxStructural() == 250.0f && mech.machineCore().maxCritical() == 125.0f
                && mech.machineCore().structural() == 250.0f, "250 / 125, whole");
        helper.assertTrue(mech.node(GuardMechEntity.RIGHT) == GuardMechEntity.Node.GUN && mech.node(GuardMechEntity.LEFT) == GuardMechEntity.Node.ROCKET,
                "Nodes from its recipe");
        // swap the right node for a rocket launcher by hand; the machine gun node comes back
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GarrisonRegistry.ROCKET_LAUNCHER_NODE.get()));
        mech.interact(owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(mech.node(GuardMechEntity.RIGHT) == GuardMechEntity.Node.ROCKET
                && owner.getInventory().countItem(GarrisonRegistry.MACHINE_GUN_NODE.get()) == 1, "The node is swapped and the old one given back");
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GarrisonRegistry.ROCKET.get(), 5));
        mech.interact(owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(mech.rockets() == 5 && owner.getMainHandItem().isEmpty(), "Rockets loaded by hand");
        mech.setEnergy(1234);
        ItemStack packed = mech.packUp(owner);
        GuardMechEntity.Loadout kept = packed.get(GarrisonRegistry.MECH_LOADOUT.get());
        helper.assertTrue(kept != null && kept.right() == GuardMechEntity.Node.ROCKET.ordinal() && kept.energy() == 1234 && kept.rockets() == 5,
                "Nodes, energy and magazine are kept in the item");
        for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
            if (owner.getInventory().getItem(slot).is(GarrisonRegistry.GUARD_MECH_ITEM.get())) {
                owner.getInventory().removeItemNoUpdate(slot);
            }
        }
        GuardMechEntity again = mech(helper, owner, packed, new BlockPos(4, 2, 4));
        helper.assertTrue(again.node(GuardMechEntity.RIGHT) == GuardMechEntity.Node.ROCKET && again.rockets() == 5 && Math.abs(again.energy() - 1234) < 1,
                "And set up again with them");
        again.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void aMechShootsAZombieWithItsMachineGun(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        GuardMechEntity mech = mech(helper, owner, built(1, 1), new BlockPos(2, 2, 4));
        mech.setEnergy(mech.capacity());
        mech.load(new ItemStack(WeaponRegistry.ASSAULT_RIFLE_AMMO.get(), 64));
        mech.machineCore().setTask(MachineTask.simple(MachineTask.Type.HOLD));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(7, 2, 4));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        zombie.setNoAi(true);
        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.getLastHurtByMob() == mech || !zombie.isAlive(), "The mech hits the zombie");
            helper.assertTrue(mech.rounds() < 64, "With rounds of its magazine");
            mech.discard();
            zombie.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void anEmptyMechStops(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        GuardMechEntity mech = mech(helper, owner, built(1, 2), new BlockPos(4, 2, 4));
        mech.setEnergy(0);
        helper.runAfterDelay(45, () -> {
            helper.assertFalse(mech.working(), "No energy: it stops");
            mech.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void aLowMechGoesToTheServicePostAndIsCharged(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        ServicePostBlockEntity post = post(helper, 6, 6);
        Vec3 stand = post.standPoint();
        helper.assertTrue(stand != null, "A post with its plates");
        GuardMechEntity mech = mech(helper, owner, built(1, 2), new BlockPos(2, 2, 2));
        float low = mech.capacity() * 0.1f;
        mech.setEnergy(low);
        helper.succeedWhen(() -> {
            helper.assertTrue(mech.distanceToSqr(stand) < 2.0, "On the post: " + mech.position() + " / " + stand);
            helper.assertTrue(mech.energy() > low + 1000, "Charged there: " + mech.energy());
            mech.discard();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void thePostMendsAndLoadsButNotTheCriticalBar(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        ServicePostBlockEntity post = post(helper, 5, 4);
        Container chest = (Container) helper.getBlockEntity(new BlockPos(3, 1, 4));
        chest.setItem(0, new ItemStack(Items.IRON_INGOT, 4));
        chest.setItem(1, new ItemStack(PowerArmorRegistry.ARMOR_PLATE.get(), 2));
        chest.setItem(2, new ItemStack(WeaponRegistry.ASSAULT_RIFLE_AMMO.get(), 40));
        chest.setItem(3, new ItemStack(GarrisonRegistry.ROCKET.get(), 3));
        GuardMechEntity mech = mech(helper, owner, built(1, 2), new BlockPos(6, 2, 5));
        mech.moveTo(post.standPoint().x, post.standPoint().y, post.standPoint().z);
        mech.machineCore().setIntegrity(100.0f, 60.0f);
        helper.assertTrue(post.served().contains(mech), "On the plates");
        post.second();
        helper.assertTrue(mech.machineCore().structural() > 100.0f && mech.machineCore().critical() == 60.0f, "Structural mended, critical not");
        helper.assertTrue(chest.getItem(0).getCount() == 3, "With an iron ingot from the chest");
        helper.assertTrue(mech.rounds() == 40 && mech.rockets() == 3 && chest.getItem(2).isEmpty(), "Its magazine filled from the chest");
        helper.assertTrue(mech.energy() > 0, "Charged");
        mech.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aNarrowRouteIsFoundForTheMech(GameTestHelper helper) {
        List<BlockPos> open = List.of(helper.absolutePos(new BlockPos(1, 1, 4)), helper.absolutePos(new BlockPos(7, 1, 4)));
        helper.assertTrue(GuardMechEntity.narrowPlace(helper.getLevel(), open) == null, "Open ground is fine");
        helper.setBlock(new BlockPos(4, 2, 3), Blocks.STONE);
        helper.assertTrue(GuardMechEntity.narrowPlace(helper.getLevel(), open) != null, "A wall beside the way is too close for a mech");
        helper.succeed();
    }
}
