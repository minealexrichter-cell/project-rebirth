package com.projectrebirth.gametest;

import java.util.List;
import java.util.UUID;

import com.projectrebirth.factions.FactionData;
import com.projectrebirth.factions.FactionService;
import com.projectrebirth.garrison.ConsoleBlockEntity;
import com.projectrebirth.garrison.ConsoleMenu;
import com.projectrebirth.garrison.ConsoleNetwork;
import com.projectrebirth.garrison.GarrisonRegistry;
import com.projectrebirth.garrison.MachineTask;
import com.projectrebirth.garrison.OrderRules;
import com.projectrebirth.garrison.PackedMachineItem;
import com.projectrebirth.garrison.TestMachineEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Version 1.8.0, block E: what the console's new window asks of the server. An order to several machines at once needs the right to
 * command (a recruit of the owner's faction is refused, the leader is not); machines that cannot carry out an order are left out
 * (only a work android works); a point clicked on the map is put on the ground; the target rules of a group reach its machines.
 */
@GameTestHolder(PlanTests.INTERFACE)
@PrefixGameTestTemplate(false)
public class ConsoleWindowGameTests {
    private static final String TEMPLATE = "floor_9x9";
    private static final String BATCH = "console_window_tests";

    private static ServerPlayer player(GameTestHelper helper, String name) {
        UUID id = UUID.randomUUID();
        ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(id, name + id.toString().substring(0, 4)));
        player.setGameMode(GameType.SURVIVAL);
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
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

    /** The player has the console's window open (the server checks it for every order of the window). */
    private static void openAt(ServerPlayer player, ConsoleBlockEntity console) {
        player.containerMenu = new ConsoleMenu(1, player.getInventory(), console.getBlockPos());
    }

    private static String key(Component component) {
        return component != null && component.getContents() instanceof TranslatableContents contents ? contents.getKey() : String.valueOf(component);
    }

    private static ConsoleNetwork.OrderManyPayload order(ConsoleBlockEntity console, List<UUID> machines, MachineTask.Type type, List<BlockPos> points) {
        return new ConsoleNetwork.OrderManyPayload(console.getBlockPos(), machines, type.ordinal(), 0, points);
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void aRecruitWithoutTheRightCannotOrder(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        FactionService.adminDisband(server, "Console Window");
        ServerPlayer leader = player(helper, "cw_leader_");
        ServerPlayer recruit = player(helper, "cw_recruit_");
        helper.assertTrue(FactionService.create(leader, "Console Window", ChatFormatting.AQUA).success(), "The faction is made");
        var faction = FactionData.get(server).of(leader.getUUID()).orElseThrow();
        faction.invited().add(recruit.getUUID());
        helper.assertTrue(FactionService.accept(recruit, faction.id()).success(), "The recruit joins");
        ConsoleBlockEntity console = console(helper, leader);
        TestMachineEntity a = place(helper, leader, new BlockPos(5, 2, 5));
        TestMachineEntity b = place(helper, leader, new BlockPos(6, 2, 6));
        List<UUID> both = List.of(a.getUUID(), b.getUUID());
        openAt(recruit, console);
        Component answer = ConsoleNetwork.orderMany(recruit, order(console, both, MachineTask.Type.HOLD, List.of()));
        helper.assertTrue(key(answer).equals("message.projectrebirth.console.not_yours"), "A recruit has no right to command: " + key(answer));
        helper.assertTrue(console.taskFor(a.getUUID()).type() == MachineTask.Type.STAND_DOWN, "Nothing was ordered");
        openAt(leader, console);
        answer = ConsoleNetwork.orderMany(leader, order(console, both, MachineTask.Type.HOLD, List.of()));
        helper.assertTrue(key(answer).equals("message.projectrebirth.console.ordered_many"), "The leader orders both: " + key(answer));
        helper.assertTrue(console.taskFor(a.getUUID()).type() == MachineTask.Type.HOLD && console.taskFor(b.getUUID()).type() == MachineTask.Type.HOLD,
                "Both machines got the order");
        a.discard();
        b.discard();
        FactionService.adminDisband(server, "Console Window");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void anOrderAMachineCannotDoIsLeftOut(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "cw_owner_");
        ConsoleBlockEntity console = console(helper, owner);
        TestMachineEntity machine = place(helper, owner, new BlockPos(5, 2, 5));
        openAt(owner, console);
        helper.assertTrue(OrderRules.problem(com.projectrebirth.garrison.MachineKind.TURRET, false, MachineTask.Type.GUARD) != null
                && OrderRules.problem(com.projectrebirth.garrison.MachineKind.ANDROID, true, MachineTask.Type.WORK) == null
                && OrderRules.problem(com.projectrebirth.garrison.MachineKind.ANDROID, false, MachineTask.Type.WORK) != null,
                "A turret takes no orders; only a work android works");
        Component answer = ConsoleNetwork.orderMany(owner, order(console, List.of(machine.getUUID()), MachineTask.Type.WORK,
                List.of(helper.absolutePos(new BlockPos(3, 1, 3)), helper.absolutePos(new BlockPos(6, 1, 6)))));
        helper.assertTrue(key(answer).equals("order_problem.projectrebirth.work"), "Work for a machine that does not work is refused: " + key(answer));
        helper.assertTrue(console.taskFor(machine.getUUID()).type() == MachineTask.Type.STAND_DOWN, "It keeps its order");
        // a point on the map: the guard point is put on the ground (the floor of the test is at y 1, the point above it)
        BlockPos clicked = helper.absolutePos(new BlockPos(7, 40, 7));
        answer = ConsoleNetwork.orderMany(owner, order(console, List.of(machine.getUUID()), MachineTask.Type.GUARD, List.of(clicked)));
        MachineTask task = console.taskFor(machine.getUUID());
        BlockPos expected = helper.getLevel().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, clicked);
        helper.assertTrue(task.type() == MachineTask.Type.GUARD && task.point().orElseThrow().equals(expected),
                "Guard on the ground at the clicked place: " + task.point() + " expected " + expected + ", " + key(answer));
        machine.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void groupRulesReachTheirMachines(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "cw_rules_");
        ConsoleBlockEntity console = console(helper, owner);
        TestMachineEntity machine = place(helper, owner, new BlockPos(5, 2, 5));
        console.setGroup(machine.getUUID(), 2);
        openAt(owner, console);
        ConsoleNetwork.act(owner, new ConsoleNetwork.ActionPayload(console.getBlockPos(), ConsoleNetwork.SET_RULES, java.util.Optional.empty(), 2,
                OrderRules.MONSTERS, 0, false));
        helper.assertTrue(console.rules(2) == OrderRules.MONSTERS && console.rules(ConsoleBlockEntity.ALL) == OrderRules.DEFAULT_RULES,
                "The group's rules change, the console's stay");
        machine.machineCore().second(machine, machine.upgrades());
        helper.assertTrue(machine.machineCore().rules() == OrderRules.MONSTERS, "The machine follows its group's rules: " + machine.machineCore().rules());
        ConsoleNetwork.rename(owner, new ConsoleNetwork.RenamePayload(console.getBlockPos(), machine.getUUID(), "Bastion"));
        machine.machineCore().second(machine, machine.upgrades());
        helper.assertTrue(machine.machineCore().name().equals("Bastion") && console.nameOf(machine.getUUID()).equals("Bastion"),
                "A name given in the window reaches the machine");
        machine.discard();
        helper.succeed();
    }
}
