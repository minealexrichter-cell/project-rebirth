package com.projectrebirth.gametest;

import java.util.Optional;
import java.util.UUID;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.garrison.ConsoleAlarm;
import com.projectrebirth.garrison.ConsoleBlockEntity;
import com.projectrebirth.garrison.ConsoleLink;
import com.projectrebirth.garrison.ConsoleNetwork;
import com.projectrebirth.garrison.GarrisonRegistry;
import com.projectrebirth.garrison.MachineTask;
import com.projectrebirth.garrison.PackedMachineItem;
import com.projectrebirth.garrison.TabletData;
import com.projectrebirth.garrison.TestMachineEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The console and the tactical tablet (1.7.0, block D) on the stand-in machines of the tests: linking by setting up and by the
 * tablet, orders to a machine and to a group, the intruder mode by group and as the server allows, the alarms with the redstone
 * and comparator signals, a lost machine, the tablet's marks in orders and their checks, packing lets go, saving.
 */
@GameTestHolder(PlanTests.GARRISON)
@PrefixGameTestTemplate(false)
public class ConsoleGameTests {
    private static final String TEMPLATE = "floor_9x9";
    private static final String BATCH = "console_tests";

    private static ServerPlayer player(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(id, "console_test_" + id.toString().substring(0, 6)));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 4)));
        player.moveTo(at.x, at.y, at.z);
        return player;
    }

    private static ConsoleBlockEntity console(GameTestHelper helper, ServerPlayer owner) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlockAndUpdate(pos, GarrisonRegistry.CONSOLE.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        ConsoleBlockEntity console = (ConsoleBlockEntity) helper.getLevel().getBlockEntity(pos);
        console.setOwner(owner.getUUID());
        console.energy().load(console.energy().getMaxEnergyStored());
        console.serverTick();
        return console;
    }

    private static TestMachineEntity place(GameTestHelper helper, ServerPlayer owner, BlockPos at) {
        PackedMachineItem item = GarrisonRegistry.TEST_MACHINE_ITEM.get();
        return (TestMachineEntity) item.place(helper.getLevel(), owner, new ItemStack(item), helper.absolutePos(at), 0.0f);
    }

    private static void second(TestMachineEntity machine) {
        machine.machineCore().second(machine, machine.upgrades());
    }

    private static String key(Component component) {
        return component != null && component.getContents() instanceof TranslatableContents contents ? contents.getKey() : String.valueOf(component);
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void aMachineSetUpNearLinksAndTakesItsOrders(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        ConsoleBlockEntity console = console(helper, owner);
        TestMachineEntity machine = place(helper, owner, new BlockPos(5, 2, 5));
        helper.assertTrue(machine != null && console.knows(machine.getUUID()), "Set up near the console, it is linked");
        BlockPos point = helper.absolutePos(new BlockPos(6, 2, 2));
        console.order(machine.getUUID(), MachineTask.at(MachineTask.Type.GUARD, point));
        second(machine);
        helper.assertTrue(machine.machineCore().task().type() == MachineTask.Type.GUARD && machine.machineCore().task().point().orElseThrow().equals(point),
                "It takes the order on its next report");
        console.energy().load(0);
        console.serverTick();
        console.order(machine.getUUID(), MachineTask.simple(MachineTask.Type.HOLD));
        second(machine);
        helper.assertTrue(machine.machineCore().task().type() == MachineTask.Type.GUARD, "Without energy no new order goes out");
        helper.assertTrue(ConsoleLink.inRange(console.getBlockPos(), console.getBlockPos().offset(RebirthConfig.CONSOLE_RANGE.get(), 0, 0))
                && !ConsoleLink.inRange(console.getBlockPos(), console.getBlockPos().offset(RebirthConfig.CONSOLE_RANGE.get() + 1, 0, 0)),
                "The range of the config");
        machine.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void groupsTakeOrdersAndTheirIntruderMode(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        ConsoleBlockEntity console = console(helper, owner);
        TestMachineEntity a = place(helper, owner, new BlockPos(5, 2, 5));
        TestMachineEntity b = place(helper, owner, new BlockPos(6, 2, 6));
        TestMachineEntity c = place(helper, owner, new BlockPos(3, 2, 6));
        console.setGroup(a.getUUID(), 2);
        console.setGroup(b.getUUID(), 2);
        helper.assertTrue(console.orderGroup(2, MachineTask.simple(MachineTask.Type.HOLD)) == 2, "The order goes to the two of group 3");
        console.setGroupIntruders(2, true);
        boolean allowed = RebirthConfig.ALLOW_INTRUDER_MODE.get();
        try {
            for (TestMachineEntity machine : new TestMachineEntity[]{a, b, c}) {
                second(machine);
            }
            helper.assertTrue(a.machineCore().task().type() == MachineTask.Type.HOLD && b.machineCore().task().type() == MachineTask.Type.HOLD
                    && c.machineCore().task().type() == MachineTask.Type.STAND_DOWN, "Only the group got it");
            helper.assertTrue(a.machineCore().intruderMode() && !c.machineCore().intruderMode(), "The group's intruder mode, not the others'");
            RebirthConfig.ALLOW_INTRUDER_MODE.set(false);
            second(a);
            helper.assertFalse(a.machineCore().intruderMode(), "Forbidden by the server, it is off");
        } finally {
            RebirthConfig.ALLOW_INTRUDER_MODE.set(allowed);
        }
        for (TestMachineEntity machine : new TestMachineEntity[]{a, b, c}) {
            machine.discard();
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void alarmsGiveRedstoneAndALostMachineIsLogged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(helper);
        ConsoleBlockEntity console = console(helper, owner);
        TestMachineEntity machine = place(helper, owner, new BlockPos(5, 2, 5));
        console.refreshSignal();
        helper.assertTrue(console.signal() == 0 && console.comparator() == 15, "Quiet: no signal, all machines in order");
        var zombie = EntityType.ZOMBIE.create(level);
        zombie.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(6, 2, 6))));
        machine.invulnerableTime = 0;
        machine.hurt(level.damageSources().mobAttack(zombie), 4.0f);
        second(machine);
        helper.assertTrue(console.alarmOn(machine.getUUID(), ConsoleAlarm.COMBAT) && console.signal() == ConsoleAlarm.COMBAT.signal(),
                "A fight: the alarm and its signal " + console.signal());
        BlockPos at = console.getBlockPos();
        helper.assertTrue(level.getBlockState(at).getSignal(level, at, Direction.NORTH) == ConsoleAlarm.COMBAT.signal(), "The block gives the signal");
        machine.invulnerableTime = 0;
        machine.hurt(level.damageSources().generic(), 1000.0f);
        second(machine);
        helper.assertTrue(console.alarmOn(machine.getUUID(), ConsoleAlarm.DISABLED) && console.comparator() == 0, "Shut down: the alarm, no machine in order");
        UUID id = machine.getUUID();
        machine.discard();
        helper.assertFalse(console.knows(id), "Destroyed: let go");
        helper.assertTrue(console.saveWithoutMetadata(level.registryAccess()).getList("log", 10).stream()
                .anyMatch(tag -> ((CompoundTag) tag).getInt("alarm") == ConsoleAlarm.LOST.ordinal()), "And logged lost");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void theTabletLinksMarksAndOrders(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        ConsoleBlockEntity console = console(helper, owner);
        int linkRadius = RebirthConfig.CONSOLE_LINK_RADIUS.get();
        RebirthConfig.CONSOLE_LINK_RADIUS.set(0);
        TestMachineEntity machine;
        try {
            machine = place(helper, owner, new BlockPos(6, 2, 6));
        } finally {
            RebirthConfig.CONSOLE_LINK_RADIUS.set(linkRadius);
        }
        helper.assertFalse(console.knows(machine.getUUID()), "Without the link radius it is not linked by itself");
        ItemStack tablet = new ItemStack(GarrisonRegistry.TABLET.get());
        tablet.set(GarrisonRegistry.TABLET_DATA.get(), TabletData.EMPTY.withConsole(console.globalPos()));
        owner.getInventory().add(tablet);
        helper.assertTrue(ConsoleLink.link(console, machine, machine.machineCore(), owner) && console.knows(machine.getUUID()), "The tablet links it");
        ServerPlayer stranger = player(helper);
        helper.assertFalse(ConsoleLink.link(console, machine, machine.machineCore(), stranger), "A stranger cannot");
        Optional<UUID> target = Optional.of(machine.getUUID());
        BlockPos pos = console.getBlockPos();
        Component answer = ConsoleNetwork.act(owner, new ConsoleNetwork.ActionPayload(pos, ConsoleNetwork.ORDER, target, -1,
                MachineTask.Type.GUARD.ordinal(), 0, true));
        helper.assertTrue(key(answer).equals("message.projectrebirth.console.need_guard"), "No point marked: " + key(answer));
        ItemStack held = owner.getInventory().items.stream().filter(s -> s.is(GarrisonRegistry.TABLET.get())).findFirst().orElseThrow();
        TabletData data = held.get(GarrisonRegistry.TABLET_DATA.get());
        BlockPos guard = helper.absolutePos(new BlockPos(2, 2, 7));
        held.set(GarrisonRegistry.TABLET_DATA.get(), data.withGuard(guard).withRoutePoint(guard, 16));
        answer = ConsoleNetwork.act(owner, new ConsoleNetwork.ActionPayload(pos, ConsoleNetwork.ORDER, target, -1, MachineTask.Type.GUARD.ordinal(), 0, true));
        helper.assertTrue(key(answer).equals("message.projectrebirth.console.ordered") && console.taskFor(machine.getUUID()).point().orElseThrow().equals(guard),
                "Guard the marked point: " + key(answer));
        answer = ConsoleNetwork.act(owner, new ConsoleNetwork.ActionPayload(pos, ConsoleNetwork.ORDER, target, -1, MachineTask.Type.PATROL.ordinal(), 0, true));
        helper.assertTrue(key(answer).equals("message.projectrebirth.console.need_route"), "One route point is not a route");
        data = held.get(GarrisonRegistry.TABLET_DATA.get());
        BlockPos chest = helper.absolutePos(new BlockPos(0, 2, 8));
        held.set(GarrisonRegistry.TABLET_DATA.get(), data.withAreaCorner(guard).withAreaCorner(guard.offset(40, -3, 5)).withChest(chest));
        answer = ConsoleNetwork.act(owner, new ConsoleNetwork.ActionPayload(pos, ConsoleNetwork.ORDER, target, -1, MachineTask.Type.WORK.ordinal(), 2, true));
        helper.assertTrue(key(answer).equals("message.projectrebirth.console.area_too_big"), "A farm of 41 blocks is too big: " + key(answer));
        data = held.get(GarrisonRegistry.TABLET_DATA.get());
        held.set(GarrisonRegistry.TABLET_DATA.get(), data.withAreaCorner(guard).withAreaCorner(guard.offset(10, -3, 5)));
        answer = ConsoleNetwork.act(owner, new ConsoleNetwork.ActionPayload(pos, ConsoleNetwork.ORDER, target, -1, MachineTask.Type.WORK.ordinal(), 2, true));
        MachineTask work = console.taskFor(machine.getUUID());
        helper.assertTrue(work.type() == MachineTask.Type.WORK && work.work().equals("farm") && work.chest().orElseThrow().equals(chest), "A farm of 11 blocks: " + key(answer));
        stranger.moveTo(owner.position());
        answer = ConsoleNetwork.act(stranger, new ConsoleNetwork.ActionPayload(pos, ConsoleNetwork.ORDER, target, -1, MachineTask.Type.STAND_DOWN.ordinal(), 0, true));
        helper.assertTrue(key(answer).equals("message.projectrebirth.console.not_yours") && console.taskFor(machine.getUUID()).type() == MachineTask.Type.WORK,
                "A stranger's order is refused");
        machine.packUp(owner);
        helper.assertFalse(console.knows(machine.getUUID()), "Packed up, the console lets it go");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void theConsoleKeepsItsMachinesAndOrdersWhenSaved(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(helper);
        ConsoleBlockEntity console = console(helper, owner);
        TestMachineEntity machine = place(helper, owner, new BlockPos(5, 2, 5));
        console.setGroup(machine.getUUID(), 4);
        console.order(machine.getUUID(), MachineTask.simple(MachineTask.Type.SHELTER));
        console.setWeatherLink(true);
        CompoundTag saved = console.saveWithFullMetadata(level.registryAccess());
        BlockEntity loaded = BlockEntity.loadStatic(console.getBlockPos(), console.getBlockState(), saved, level.registryAccess());
        helper.assertTrue(loaded instanceof ConsoleBlockEntity copy && copy.knows(machine.getUUID()) && copy.groupOf(machine.getUUID()) == 4
                && copy.taskFor(machine.getUUID()).type() == MachineTask.Type.SHELTER && copy.weatherLink(), "Machines, groups, orders, switches");
        CompoundTag tag = new CompoundTag();
        machine.saveWithoutId(tag);
        helper.assertTrue(tag.getCompound("machine").contains("console"), "The machine keeps its console");
        machine.discard();
        helper.succeed();
    }
}
