package com.projectrebirth.gametest;

import java.util.UUID;

import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyItems;
import com.projectrebirth.bodies.power.BodyPower;
import com.projectrebirth.bodies.power.BodyPowerRegistry;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.garrison.AiCoreItem;
import com.projectrebirth.garrison.AndroidEntity;
import com.projectrebirth.garrison.Androids;
import com.projectrebirth.garrison.GarrisonRegistry;
import com.projectrebirth.garrison.MachineCore;
import com.projectrebirth.garrison.MachineKind;
import com.projectrebirth.garrison.MachineRegistry;
import com.projectrebirth.powerarmor.PowerArmorRegistry;
import com.projectrebirth.technology.Maintenance;
import com.projectrebirth.weapons.WeaponRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The combat android (1.7.0, block E): setting it up from a body with an AI core (owner, bars, rights, limits), the weapons it
 * fights with, the guns it never takes, charge and the charging pad, emergency mode, packing, saving and repair of a packed one.
 */
@GameTestHolder(PlanTests.GARRISON)
@PrefixGameTestTemplate(false)
public class AndroidGameTests {
    private static final String TEMPLATE = "floor_9x9";

    /** A survival player of its own (mock players always count as creative). */
    private static ServerPlayer player(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(id, "android_test_" + id.toString().substring(0, 6)));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.setShiftKeyDown(false);
        return player;
    }

    /** A packed android of that class: the body item with an AI core bound to the owner and a combat module. */
    private static ItemStack packed(BodyClass bodyClass, ServerPlayer owner) {
        ItemStack core = new ItemStack(GarrisonRegistry.AI_CORE.get());
        AiCoreItem.bind(core, owner);
        ItemStack stack = new ItemStack(BodyItems.item(bodyClass));
        stack.set(GarrisonRegistry.ANDROID_PARTS.get(), new GarrisonRegistry.AndroidParts(core, new ItemStack(GarrisonRegistry.COMBAT_MODULE.get())));
        return stack;
    }

    private static AndroidEntity place(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos at) {
        BodyClass bodyClass = ((com.projectrebirth.bodies.ArtificialBodyItem) stack.getItem()).bodyClass();
        return Androids.place(helper.getLevel(), player, stack, bodyClass, helper.absolutePos(at), 0.0f);
    }

    /** A zombie that does not burn in the sun of the test world. */
    private static Zombie zombie(GameTestHelper helper, BlockPos at) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, at);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        zombie.setPersistenceRequired();
        return zombie;
    }

    @GameTest(template = TEMPLATE)
    public static void anAndroidTakesItsOwnerAndBarsFromItsCore(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        AndroidEntity android = place(helper, owner, packed(BodyClass.IMPROVED_ROBOT, owner), new BlockPos(2, 2, 2));
        helper.assertTrue(android != null, "The android stands");
        MachineCore core = android.machineCore();
        float body = RebirthConfig.bodyStats(BodyClass.IMPROVED_ROBOT).maxHealth().get().floatValue();
        helper.assertTrue(core.kind() == MachineKind.ANDROID && owner.getUUID().equals(core.owner()), "An android of the core's owner");
        helper.assertTrue(core.maxStructural() == body && Math.abs(core.maxCritical() - body * RebirthConfig.ANDROID_CRITICAL_SHARE.get()) < 1.0e-3,
                "Bars from the body: " + core.maxStructural() + " / " + core.maxCritical());
        helper.assertTrue(core.structural() == core.maxStructural() && core.critical() == core.maxCritical(), "Whole");
        helper.assertTrue(!android.hostsConsciousness(), "Nobody can move into it");
        helper.assertTrue(MachineRegistry.get(helper.getLevel().getServer()).entry(android.getUUID()).isPresent(), "In the register of machines");

        // a stranger cannot set up a core bound to somebody else
        ServerPlayer stranger = player(helper);
        helper.assertTrue(place(helper, stranger, packed(BodyClass.BASIC_ROBOT, owner), new BlockPos(6, 2, 6)) == null,
                "Not by a stranger");

        // the limit of androids per player
        int before = RebirthConfig.MACHINE_PLAYER_LIMIT.get(MachineKind.ANDROID).get();
        try {
            RebirthConfig.MACHINE_PLAYER_LIMIT.get(MachineKind.ANDROID).set(1);
            helper.assertTrue(place(helper, owner, packed(BodyClass.BASIC_ROBOT, owner), new BlockPos(6, 2, 2)) == null, "Over the limit");
        } finally {
            RebirthConfig.MACHINE_PLAYER_LIMIT.get(MachineKind.ANDROID).set(before);
        }
        android.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void itFightsAZombieWithTheSwordInItsHand(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        AndroidEntity android = place(helper, owner, packed(BodyClass.PREMIUM_SYNTHETIC, owner), new BlockPos(2, 2, 4));
        android.gear().setItem(0, new ItemStack(Items.IRON_SWORD));
        android.gear().setItem(AndroidEntity.OFFHAND, new ItemStack(Items.SHIELD));
        helper.assertTrue(android.getMainHandItem().is(Items.IRON_SWORD) && android.getOffhandItem().is(Items.SHIELD),
                "The sword in hand, the shield in the other");
        Zombie zombie = zombie(helper, new BlockPos(6, 2, 4));
        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.getLastHurtByMob() == android, "The android hits the zombie");
            android.discard();
            zombie.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500)
    public static void aBowShootsArrowsFromItsGear(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        AndroidEntity android = place(helper, owner, packed(BodyClass.PREMIUM_SYNTHETIC, owner), new BlockPos(1, 2, 4));
        android.gear().setItem(0, new ItemStack(Items.BOW));
        android.gear().setItem(5, new ItemStack(Items.ARROW, 8));
        Zombie zombie = zombie(helper, new BlockPos(7, 2, 4));
        helper.succeedWhen(() -> {
            helper.assertTrue(android.gear().getItem(5).getCount() < 8, "An arrow is used: " + android.gear().getItem(5).getCount());
            android.discard();
            zombie.discard();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void theGunsOfPowerArmorNeverGoIntoItsHand(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        AndroidEntity android = place(helper, owner, packed(BodyClass.BASIC_ROBOT, owner), new BlockPos(2, 2, 2));
        android.gear().setItem(0, new ItemStack(WeaponRegistry.ASSAULT_RIFLE.get()));
        helper.assertTrue(android.getMainHandItem().isEmpty(), "The rifle stays in the gear: " + android.getMainHandItem());
        helper.assertTrue(android.gear().getItem(0).is(WeaponRegistry.ASSAULT_RIFLE.get()), "Kept, not lost");
        android.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void lowChargeTakesItToTheChargingPad(GameTestHelper helper) {
        helper.assertTrue(BodyPower.enabled(), "Body charge is on");
        ServerPlayer owner = player(helper);
        BlockPos pad = new BlockPos(7, 2, 7);
        helper.setBlock(pad, BodyPowerRegistry.CHARGING_PAD.get());
        AndroidEntity android = place(helper, owner, packed(BodyClass.ADVANCED_SYNTHETIC, owner), new BlockPos(1, 2, 1));
        android.setCharge(BodyPower.capacity(android) * 0.1f);
        Vec3 target = Vec3.atBottomCenterOf(helper.absolutePos(pad));
        helper.succeedWhen(() -> {
            double distance = Math.hypot(android.getX() - target.x, android.getZ() - target.z);
            helper.assertTrue(distance < 1.0, "On the pad: " + distance);
            android.discard();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void noChargeStopsItInEmergencyMode(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        AndroidEntity android = place(helper, owner, packed(BodyClass.BASIC_ROBOT, owner), new BlockPos(2, 2, 2));
        android.setCharge(0.0f);
        helper.succeedWhen(() -> {
            helper.assertTrue(android.shownEmergency() && !android.working(), "Emergency mode");
            android.discard();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void packingKeepsTheCoreAndDropsTheGear(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        AndroidEntity android = place(helper, owner, packed(BodyClass.HIGHTECH_SYNTHETIC, owner), new BlockPos(4, 2, 4));
        android.gear().setItem(0, new ItemStack(Items.IRON_SWORD));
        MachineCore core = android.machineCore();
        core.setIntegrity(core.maxStructural() * 0.5f, core.maxCritical() * 0.5f);
        UUID id = android.getUUID();
        owner.setShiftKeyDown(true);
        android.interact(owner, InteractionHand.MAIN_HAND);
        owner.setShiftKeyDown(false);
        helper.assertTrue(!android.isAlive(), "Packed up");
        helper.assertTrue(MachineRegistry.get(helper.getLevel().getServer()).entry(id).isEmpty(), "Out of the register");
        ItemStack stack = owner.getInventory().items.stream().filter(Androids::isAndroid).findFirst().orElse(ItemStack.EMPTY);
        helper.assertTrue(!stack.isEmpty(), "The packed android in the owner's inventory");
        GarrisonRegistry.AndroidParts parts = stack.get(GarrisonRegistry.ANDROID_PARTS.get());
        helper.assertTrue(parts.core().is(GarrisonRegistry.AI_CORE.get()) && parts.module().is(GarrisonRegistry.COMBAT_MODULE.get()), "Core and module kept");
        boolean swordOut = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(12))
                .stream().anyMatch(e -> e.getItem().is(Items.IRON_SWORD));
        helper.assertTrue(swordOut, "The gear fell out");

        // the maintenance station mends its critical bar with plates
        float critical = Maintenance.androidCritical(stack);
        helper.assertTrue(critical < Maintenance.maxAndroidCritical(stack) && Maintenance.needsRepair(stack), "Damaged when packed: " + critical);
        helper.assertTrue(Maintenance.materialFor(stack, item -> true) == PowerArmorRegistry.ARMOR_PLATE.get(), "Plates for the critical bar");
        Maintenance.apply(stack, PowerArmorRegistry.ARMOR_PLATE.get());
        helper.assertTrue(Maintenance.androidCritical(stack) > critical, "A plate mends it");

        // set up again: same owner, the bars it had
        float mended = Maintenance.androidCritical(stack);
        AndroidEntity again = place(helper, owner, stack, new BlockPos(4, 2, 4));
        helper.assertTrue(again != null && owner.getUUID().equals(again.machineCore().owner()), "Set up again by its owner");
        helper.assertTrue(Math.abs(again.machineCore().structural() - core.maxStructural() * 0.5f) < 1.0f
                && Math.abs(again.machineCore().critical() - mended) < 1.0e-3f,
                "The bars came along: " + again.machineCore().structural() + " / " + again.machineCore().critical());
        again.discard();
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(12)).forEach(ItemEntity::discard);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void anAndroidIsSavedWithItsCoreModuleAndGear(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        AndroidEntity android = place(helper, owner, packed(BodyClass.BASIC_ROBOT, owner), new BlockPos(2, 2, 2));
        android.gear().setItem(0, new ItemStack(Items.IRON_AXE));
        android.gear().setItem(37, new ItemStack(Items.IRON_LEGGINGS));
        CompoundTag tag = android.saveWithoutId(new CompoundTag());
        AndroidEntity loaded = Androids.type(BodyClass.BASIC_ROBOT).create(helper.getLevel());
        loaded.load(tag);
        helper.assertTrue(loaded.coreItem().is(GarrisonRegistry.AI_CORE.get()) && loaded.moduleItem().is(GarrisonRegistry.COMBAT_MODULE.get()),
                "Core and module after loading");
        helper.assertTrue(loaded.gear().getItem(0).is(Items.IRON_AXE) && loaded.gear().getItem(37).is(Items.IRON_LEGGINGS), "Gear after loading");
        helper.assertTrue(owner.getUUID().equals(loaded.machineCore().owner()), "Owner after loading");
        android.discard();
        helper.succeed();
    }
}
