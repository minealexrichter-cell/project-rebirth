package com.projectrebirth.gametest;

import java.util.List;
import java.util.UUID;

import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyItems;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.factions.Faction;
import com.projectrebirth.factions.FactionBlocks;
import com.projectrebirth.factions.FactionData;
import com.projectrebirth.factions.FactionRole;
import com.projectrebirth.factions.FactionService;
import com.projectrebirth.garrison.AiCoreItem;
import com.projectrebirth.garrison.GarrisonRegistry;
import com.projectrebirth.garrison.MachineCore;
import com.projectrebirth.garrison.MachineKind;
import com.projectrebirth.garrison.MachineRegistry;
import com.projectrebirth.garrison.PackedMachineItem;
import com.projectrebirth.garrison.TargetRules;
import com.projectrebirth.garrison.TestMachineEntity;
import com.projectrebirth.hostile.HostileRegistry;
import com.projectrebirth.hostile.RemainsEntity;
import com.projectrebirth.powerarmor.PowerArmorRegistry;
import com.projectrebirth.technology.FabricationRecipe;
import com.projectrebirth.technology.Maintenance;
import com.projectrebirth.technology.Station;
import com.projectrebirth.technology.TechRecipes;
import com.projectrebirth.upgrades.UpgradeHost;
import com.projectrebirth.upgrades.UpgradeRegistry;
import com.projectrebirth.upgrades.Upgrades;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The base of the player's machines (1.7.0, block C) on the stand-in machine of the tests: the AI core and its recipes, the
 * damaged core of the ruins' wrecks, the two integrity bars, shutting down and the wreck, taking a wreck apart, packing and
 * setting up with the rights of the factions and the limits, the register, the rules of targets, the upgrade slots and the
 * maintenance station.
 */
@GameTestHolder(PlanTests.GARRISON)
@PrefixGameTestTemplate(false)
public class MachineGameTests {
    private static final String TEMPLATE = "floor_9x9";

    /**
     * A player in survival mode with a name and an id of its own (the game test's mock players always count as creative, which
     * the rules of targets and the rights leave out on purpose).
     */
    private static ServerPlayer player(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(id, "machine_test_" + id.toString().substring(0, 6)));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        return player;
    }

    private static TestMachineEntity machine(GameTestHelper helper, BlockPos at, ServerPlayer owner) {
        TestMachineEntity machine = GarrisonRegistry.TEST_MACHINE.get().create(helper.getLevel());
        Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(at));
        machine.moveTo(pos.x, pos.y, pos.z, 0.0f, 0.0f);
        if (owner != null) {
            machine.machineCore().setOwner(owner.getUUID(), "owner");
        }
        helper.getLevel().addFreshEntity(machine);
        return machine;
    }

    private static void hit(TestMachineEntity machine, net.minecraft.world.damagesource.DamageSource source, float amount) {
        machine.invulnerableTime = 0;
        machine.hurt(source, amount);
    }

    private static int dropped(GameTestHelper helper, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(12))
                .stream().filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
    }

    @GameTest(template = TEMPLATE)
    public static void theCoreHasItsRecipesAndBindsToItsFirstHolder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<FabricationRecipe> electronics = TechRecipes.recipesFor(level, Station.ELECTRONICS_FABRICATOR).stream().map(h -> h.value()).toList();
        List<FabricationRecipe> assembly = TechRecipes.recipesFor(level, Station.ASSEMBLY_STATION).stream().map(h -> h.value()).toList();
        helper.assertTrue(electronics.stream().anyMatch(r -> r.result().is(GarrisonRegistry.NEURAL_MATRIX.get())), "The neural matrix at the electronics fabricator");
        helper.assertTrue(assembly.stream().anyMatch(r -> r.result().is(GarrisonRegistry.AI_CORE.get())), "The core at the assembly station");
        helper.assertTrue(electronics.stream().anyMatch(r -> r.result().is(GarrisonRegistry.AI_CORE.get())
                && r.ingredients().stream().anyMatch(i -> i.ingredient().test(new ItemStack(GarrisonRegistry.DAMAGED_AI_CORE.get())))),
                "A damaged core is restored at the electronics fabricator");
        helper.assertTrue(electronics.stream().anyMatch(r -> r.result().is(GarrisonRegistry.COMBAT_MODULE.get()))
                && electronics.stream().anyMatch(r -> r.result().is(GarrisonRegistry.WORK_MODULE.get())), "Both behaviour modules");
        ItemStack core = new ItemStack(GarrisonRegistry.AI_CORE.get());
        helper.assertTrue(core.getMaxStackSize() == 1, "A core does not stack");
        ServerPlayer player = player(helper);
        AiCoreItem.bind(core, player);
        GarrisonRegistry.CoreOwner owner = core.get(GarrisonRegistry.CORE_OWNER.get());
        helper.assertTrue(owner != null && owner.id().equals(player.getUUID()), "Bound to its holder");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theWreckOfARuinsRobotMayGiveADamagedCore(GameTestHelper helper) {
        double before = RebirthConfig.DAMAGED_CORE_PATROL_ROBOT.get();
        RebirthConfig.DAMAGED_CORE_PATROL_ROBOT.set(1.0);
        try {
            var robot = HostileRegistry.PATROL_ROBOT.get().create(helper.getLevel());
            Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 4)));
            robot.moveTo(pos.x, pos.y, pos.z);
            helper.getLevel().addFreshEntity(robot);
            RemainsEntity wreck = RemainsEntity.leave(robot, RemainsEntity.Kind.PATROL_ROBOT);
            robot.discard();
            wreck.dismantle(helper.getLevel(), null);
            helper.assertTrue(dropped(helper, GarrisonRegistry.DAMAGED_AI_CORE.get()) == 1, "A damaged core out of the robot's wreck");
        } finally {
            RebirthConfig.DAMAGED_CORE_PATROL_ROBOT.set(before);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void damageShutsDownThenWrecksAndTheWreckComesApart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestMachineEntity machine = machine(helper, new BlockPos(4, 2, 4), null);
        MachineCore core = machine.machineCore();
        float maxS = MachineKind.TEST.maxStructural(), maxC = MachineKind.TEST.maxCritical();
        hit(machine, level.damageSources().generic(), 10.0f);
        helper.assertTrue(core.structural() == maxS - 10 && core.critical() == maxC, "Ordinary damage takes only structural: " + core.structural() + "/" + core.critical());
        hit(machine, level.damageSources().explosion(null, null), 4.0f);
        helper.assertTrue(core.critical() < maxC, "An explosion takes critical too");
        hit(machine, level.damageSources().generic(), 1000.0f);
        helper.assertTrue(core.state() == MachineCore.State.DISABLED && core.structural() == 0.0f, "No structural: shut down");
        float critical = core.critical();
        hit(machine, level.damageSources().generic(), 3.0f);
        helper.assertTrue(core.critical() == critical - 3.0f, "Shut down, all damage goes into critical");
        hit(machine, level.damageSources().generic(), 1000.0f);
        helper.assertTrue(core.wreck() && machine.isAlive(), "No critical: a wreck stays standing");
        Upgrades.install(level.registryAccess(), machine, new ItemStack(UpgradeRegistry.ARMOR_COPPER_PLATING.get()), null);
        machine.dismantle(player(helper));
        helper.assertTrue(!machine.isAlive(), "Taken apart, it is gone");
        helper.assertTrue(dropped(helper, GarrisonRegistry.DAMAGED_AI_CORE.get()) == 1 && dropped(helper, Items.IRON_INGOT) == 2
                && dropped(helper, UpgradeRegistry.ARMOR_COPPER_PLATING.get()) == 1, "Parts, its upgrade and a damaged core");
        helper.assertTrue(MachineRegistry.get(level.getServer()).entry(machine.getUUID()).isEmpty(), "Gone from the register");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void structuralComesBackAfterAPauseAndTheWrenchRestarts(GameTestHelper helper) {
        TestMachineEntity machine = machine(helper, new BlockPos(4, 2, 4), null);
        MachineCore core = machine.machineCore();
        hit(machine, helper.getLevel().damageSources().generic(), 1000.0f);
        helper.assertTrue(core.state() == MachineCore.State.DISABLED, "Shut down");
        for (int i = 0; i < 30; i++) {
            core.second(machine, machine.upgrades());
        }
        helper.assertTrue(core.structural() == 0.0f, "A shut-down machine does not mend itself");
        ServerPlayer player = player(helper);
        player.getInventory().add(new ItemStack(Items.IRON_INGOT, 4));
        ItemStack wrench = new ItemStack(PowerArmorRegistry.REPAIR_WRENCH.get());
        machine.useWrench(player, wrench);
        helper.assertTrue(core.structural() == RebirthConfig.MACHINE_REPAIR_PER_INGOT.get().floatValue() && core.active(),
                "An ingot mends it and it starts again: " + core.structural());
        hit(machine, helper.getLevel().damageSources().generic(), 2.0f);
        float after = core.structural();
        for (int i = 0; i < RebirthConfig.MACHINE_REGEN_DELAY_SECONDS.get() + 3; i++) {
            core.second(machine, machine.upgrades());
        }
        helper.assertTrue(core.structural() > after, "After the pause it mends by itself");
        helper.succeed();
    }

    // the batches keep the tests that set up machines or a faction's land apart from each other: a faction core's land reaches
    // over the neighbouring test areas, and machines may not be set up on foreign land
    @GameTest(template = TEMPLATE, batch = "machine_placing")
    public static void packingAndSettingUpFollowTheRightsOfTheFactions(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FactionService.adminDisband(server, "Machine Owners");
        ServerPlayer owner = player(helper), officer = player(helper), member = player(helper), stranger = player(helper);
        try {
            FactionService.create(owner, "Machine Owners", ChatFormatting.BLUE);
            FactionService.adminSetMember(server, officer, "Machine Owners");
            FactionService.adminSetMember(server, member, "Machine Owners");
            FactionService.setRole(owner, officer.getUUID(), FactionRole.OFFICER);
            ItemStack packed = new ItemStack(GarrisonRegistry.TEST_MACHINE_ITEM.get());
            TestMachineEntity machine = (TestMachineEntity) GarrisonRegistry.TEST_MACHINE_ITEM.get().place(helper.getLevel(), owner, packed,
                    helper.absolutePos(new BlockPos(4, 2, 4)), 0.0f);
            helper.assertTrue(machine != null && owner.getUUID().equals(machine.machineCore().owner()), "Set up by its owner, it is theirs");
            helper.assertTrue(MachineRegistry.get(server).countOwned(owner.getUUID(), MachineKind.TEST) == 1, "In the register");
            hit(machine, helper.getLevel().damageSources().generic(), 5.0f);
            ItemStack wrench = new ItemStack(PowerArmorRegistry.REPAIR_WRENCH.get());
            stranger.setShiftKeyDown(true);
            machine.useWrench(stranger, wrench);
            member.setShiftKeyDown(true);
            machine.useWrench(member, wrench);
            helper.assertTrue(machine.isAlive(), "A stranger and a plain member may not pack it up");
            helper.assertTrue(machine.machineCore().mayManage(officer) && FactionRole.OFFICER.canManageMachines() && !FactionRole.MEMBER.canManageMachines(),
                    "An officer may, by default");
            ItemStack item = machine.packUp(officer);
            helper.assertTrue(!machine.isAlive() && item.has(GarrisonRegistry.PACKED_MACHINE.get()), "Packed up by the officer");
            MachineCore.Packed data = PackedMachineItem.packed(item);
            helper.assertTrue(data.structural() == MachineKind.TEST.maxStructural() - 5 && owner.getUUID().equals(data.owner().orElse(null)),
                    "The item keeps its damage and its owner");
            helper.assertTrue(MachineRegistry.get(server).countOwned(owner.getUUID(), MachineKind.TEST) == 0, "Out of the register");
            helper.assertTrue(GarrisonRegistry.TEST_MACHINE_ITEM.get().place(helper.getLevel(), stranger, item.copy(),
                    helper.absolutePos(new BlockPos(2, 2, 2)), 0.0f) == null, "A stranger may not set up someone else's machine");
            TestMachineEntity again = (TestMachineEntity) GarrisonRegistry.TEST_MACHINE_ITEM.get().place(helper.getLevel(), owner, item,
                    helper.absolutePos(new BlockPos(2, 2, 2)), 0.0f);
            helper.assertTrue(again != null && again.machineCore().structural() == MachineKind.TEST.maxStructural() - 5, "Set up again as it was");
            again.discard();
        } finally {
            FactionService.adminDisband(server, "Machine Owners");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "machine_limits")
    public static void theLimitsHold(GameTestHelper helper) {
        int before = RebirthConfig.MACHINE_PLAYER_LIMIT.get(MachineKind.TEST).get();
        RebirthConfig.MACHINE_PLAYER_LIMIT.get(MachineKind.TEST).set(1);
        try {
            ServerPlayer owner = player(helper);
            PackedMachineItem item = GarrisonRegistry.TEST_MACHINE_ITEM.get();
            var first = item.place(helper.getLevel(), owner, new ItemStack(item), helper.absolutePos(new BlockPos(2, 2, 2)), 0.0f);
            var second = item.place(helper.getLevel(), owner, new ItemStack(item), helper.absolutePos(new BlockPos(6, 2, 6)), 0.0f);
            BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
            helper.assertTrue(first != null && second == null, "One allowed, the second refused: " + (first != null) + " " + (second != null)
                    + " mayUse=" + FactionService.mayUse(owner, at, FactionService.Use.CONTAINER) + " land=" + FactionService.territoryAt(helper.getLevel().getServer(), helper.getLevel(), at).map(Faction::name)
                    + " limit=" + MachineRegistry.get(helper.getLevel().getServer()).limitProblem(helper.getLevel().getServer(), owner.getUUID(), MachineKind.TEST)
                    + " enabled=" + RebirthConfig.MACHINES_ENABLED.get());
            first.discard();
        } finally {
            RebirthConfig.MACHINE_PLAYER_LIMIT.get(MachineKind.TEST).set(before);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "machine_territory")
    public static void theRulesOfTargets(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        for (String name : List.of("Rule Keepers", "Rule Friends", "Rule Foes")) {
            FactionService.adminDisband(server, name);
        }
        int preparation = RebirthConfig.FACTION_WAR_PREPARATION_MINUTES.get();
        boolean allowIntruders = RebirthConfig.ALLOW_INTRUDER_MODE.get();
        ServerPlayer owner = player(helper), mate = player(helper), friend = player(helper), foe = player(helper), outsider = player(helper);
        try {
            RebirthConfig.FACTION_WAR_PREPARATION_MINUTES.set(0);
            FactionService.create(owner, "Rule Keepers", ChatFormatting.BLUE);
            FactionService.adminSetMember(server, mate, "Rule Keepers");
            FactionService.create(friend, "Rule Friends", ChatFormatting.GREEN);
            FactionService.create(foe, "Rule Foes", ChatFormatting.RED);
            FactionData data = FactionData.get(server);
            Faction keepers = data.of(owner.getUUID()).orElseThrow(), friends = data.of(friend.getUUID()).orElseThrow(), foes = data.of(foe.getUUID()).orElseThrow();
            FactionService.proposeAlliance(owner, friends.id());
            FactionService.acceptAlliance(friend, keepers.id());
            BlockPos core = helper.absolutePos(new BlockPos(0, 1, 0));
            level.setBlockAndUpdate(core, FactionBlocks.FACTION_CORE.get().defaultBlockState());
            FactionService.addCore(owner, level, core);
            TestMachineEntity machine = machine(helper, new BlockPos(4, 2, 4), owner);
            var zombie = EntityType.ZOMBIE.create(level);
            var villager = EntityType.VILLAGER.create(level);
            var wolf = EntityType.WOLF.create(level);
            for (var mob : List.of(zombie, villager, wolf)) {
                mob.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(6, 2, 6))));
                level.addFreshEntity(mob);
            }
            helper.assertTrue(TargetRules.mayAttack(machine, zombie), "Monsters always");
            helper.assertFalse(TargetRules.mayAttack(machine, villager), "Peaceful creatures never");
            helper.assertFalse(TargetRules.mayAttack(machine, wolf), "Neutral creatures not unless they attack");
            machine.machineCore().remember(wolf, level.getGameTime());
            helper.assertTrue(TargetRules.mayAttack(machine, wolf), "A neutral creature that attacked: yes");
            helper.assertFalse(TargetRules.mayAttack(machine, owner) || TargetRules.mayAttack(machine, mate) || TargetRules.mayAttack(machine, friend),
                    "Never the owner, the faction, the allies");
            ServerPlayer creative = player(helper);
            creative.setGameMode(GameType.CREATIVE);
            machine.machineCore().remember(creative, level.getGameTime());
            helper.assertFalse(TargetRules.mayAttack(machine, creative), "Never a player in creative mode");
            outsider.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 2, 5))));
            helper.assertFalse(TargetRules.mayAttack(machine, outsider), "An outsider in peace: no");
            machine.machineCore().setIntruderMode(true);
            helper.assertTrue(TargetRules.mayAttack(machine, outsider), "The intruder mode on the faction's land: yes; machine land "
                    + FactionService.territoryAt(server, level, machine.blockPosition()).map(Faction::name) + ", outsider land "
                    + FactionService.territoryAt(server, level, outsider.blockPosition()).map(Faction::name) + ", owner faction " + keepers.name()
                    + ", outsider at " + outsider.blockPosition() + ", cores " + keepers.cores());
            RebirthConfig.ALLOW_INTRUDER_MODE.set(false);
            helper.assertFalse(TargetRules.mayAttack(machine, outsider), "Forbidden by the server: no");
            RebirthConfig.ALLOW_INTRUDER_MODE.set(true);
            machine.machineCore().setIntruderMode(false);
            machine.machineCore().remember(outsider, level.getGameTime());
            helper.assertTrue(TargetRules.mayAttack(machine, outsider), "An outsider who attacked: self-defence");
            foe.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 2, 3))));
            helper.assertFalse(TargetRules.mayAttack(machine, foe), "An enemy-to-be in peace: no");
            RebirthConfig.FACTION_WAR_PREPARATION_MINUTES.set(60);
            FactionService.declareEnemy(owner, foes.id());
            helper.assertFalse(TargetRules.mayAttack(machine, foe), "A war still preparing: no");
            RebirthConfig.FACTION_WAR_PREPARATION_MINUTES.set(0);
            helper.assertTrue(TargetRules.mayAttack(machine, foe), "An active war: yes");
            machine.discard();
            for (var mob : List.of(zombie, villager, wolf)) {
                mob.discard();
            }
        } finally {
            RebirthConfig.FACTION_WAR_PREPARATION_MINUTES.set(preparation);
            RebirthConfig.ALLOW_INTRUDER_MODE.set(allowIntruders);
            for (String name : List.of("Rule Keepers", "Rule Friends", "Rule Foes")) {
                FactionService.adminDisband(server, name);
            }
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aMachineTakesArmorUpgradesOneOfEachKind(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        TestMachineEntity machine = machine(helper, new BlockPos(4, 2, 4), null);
        helper.assertTrue(Upgrades.problem(access, machine, new ItemStack(UpgradeRegistry.COPPER_PLATING.get()), null) == Upgrades.Problem.WRONG_HOST,
                "A body's plates do not go on a machine");
        helper.assertTrue(Upgrades.install(access, machine, new ItemStack(UpgradeRegistry.ARMOR_COPPER_PLATING.get()), null) == null, "Armor plating does");
        helper.assertTrue(Upgrades.problem(access, machine, new ItemStack(UpgradeRegistry.ARMOR_IRON_PLATING.get()), null) == Upgrades.Problem.TOO_MANY,
                "One of each kind");
        helper.assertTrue(Upgrades.install(access, machine, new ItemStack(UpgradeRegistry.EMI_SCREEN.get()), null) == null
                && Upgrades.install(access, machine, new ItemStack(UpgradeRegistry.SENSOR_UNIT.get()), null) == null, "Up to its slots");
        helper.assertTrue(Upgrades.problem(access, machine, new ItemStack(UpgradeRegistry.FLOODLIGHT.get()), null) == Upgrades.Problem.NO_FREE_SLOTS,
                "No more than its slots (" + MachineKind.TEST.upgradeSlots() + ")");
        // every armor upgrade of the mod's data fits machines too, and no body upgrade does
        access.registryOrThrow(UpgradeRegistry.UPGRADES).forEach(definition -> helper.assertTrue(
                definition.armorSlots().isEmpty() == (Upgrades.problem(item -> java.util.Optional.empty(), definition, ItemStack.EMPTY,
                        com.projectrebirth.upgrades.InstalledUpgrades.EMPTY, new com.projectrebirth.upgrades.UpgradeLayout.MachineSlots(3), null)
                        == Upgrades.Problem.WRONG_HOST), "Machines take armor upgrades only: " + definition.kind()));
        machine.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theMaintenanceStationMendsAPackedMachine(GameTestHelper helper) {
        ItemStack item = new ItemStack(GarrisonRegistry.TEST_MACHINE_ITEM.get());
        item.set(GarrisonRegistry.PACKED_MACHINE.get(), new MachineCore.Packed(MachineKind.TEST, 5.0f, 2.0f, 60.0f, java.util.Optional.empty(), "", ""));
        helper.assertTrue(Maintenance.kind(item) == Maintenance.Kind.MACHINE && Maintenance.needsRepair(item), "A damaged packed machine needs the station");
        int steps = 0;
        while (Maintenance.needsRepair(item) && steps++ < 100) {
            Item material = Maintenance.materialFor(item, any -> true);
            helper.assertTrue(material != null, "Something to repair it with");
            Maintenance.apply(item, material);
        }
        MachineCore.Packed mended = PackedMachineItem.packed(item);
        helper.assertTrue(mended.structural() == MachineKind.TEST.maxStructural() && mended.critical() == MachineKind.TEST.maxCritical()
                && mended.wear() == 0.0f, "Both bars and wear mended in " + steps + " steps");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void anAndroidBodyKeepsItsCoreWhenUpgraded(GameTestHelper helper) {
        ItemStack body = new ItemStack(BodyItems.item(BodyClass.BASIC_ROBOT));
        body.set(GarrisonRegistry.ANDROID_PARTS.get(), new GarrisonRegistry.AndroidParts(new ItemStack(GarrisonRegistry.AI_CORE.get()),
                new ItemStack(GarrisonRegistry.COMBAT_MODULE.get())));
        UpgradeHost host = UpgradeHost.ofPacked(body);
        helper.assertTrue(host != null && Upgrades.install(helper.getLevel().registryAccess(), host, new ItemStack(UpgradeRegistry.COPPER_PLATING.get()), null) == null,
                "The modification of bodies takes an android");
        GarrisonRegistry.AndroidParts parts = body.get(GarrisonRegistry.ANDROID_PARTS.get());
        helper.assertTrue(parts != null && parts.core().is(GarrisonRegistry.AI_CORE.get()), "Its core stays in it");
        helper.succeed();
    }

}
