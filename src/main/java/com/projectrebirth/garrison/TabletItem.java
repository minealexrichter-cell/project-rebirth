package com.projectrebirth.garrison;

import java.util.List;
import java.util.function.Consumer;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * The tactical tablet: the console's orders in the field (1.7.0, block D; reworked in 1.7.1).
 *
 * <ul>
 *   <li>right-click a console: link the tablet to it;</li>
 *   <li>right-click a machine: link it to the tablet's console;</li>
 *   <li>right-click in the air: the screen with the console's machines and the orders;</li>
 *   <li>an order that needs points (guard, patrol, work) waits on the tablet: right-click the blocks in the world (the guard point;
 *   the route points one by one; two corners of the work area and then its chest). When the points are there, the order goes out.
 *   For a route, right-click in the air sends it; otherwise right-click in the air (or Shift + right-click) cancels.</li>
 * </ul>
 * The marks of the last orders and the points of the waiting one are drawn in the world while the tablet is in hand.
 */
public class TabletItem extends Item {
    /** Set by the client: opens the tablet's screen. */
    public static Consumer<ItemStack> openScreen = stack -> {
    };

    public TabletItem(Properties properties) {
        super(properties);
    }

    public static TabletData data(ItemStack stack) {
        return stack.getOrDefault(GarrisonRegistry.TABLET_DATA.get(), TabletData.EMPTY);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (data(stack).pending().isPresent()) {
            if (player instanceof ServerPlayer server) {
                say(player, air(server, stack));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (level.isClientSide) {
            openScreen.accept(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        if (level.getBlockEntity(pos) instanceof ConsoleBlockEntity console) {
            if (!console.mayCommand(player)) {
                say(player, Component.translatable("message.projectrebirth.tablet.not_yours"));
            } else {
                stack.set(GarrisonRegistry.TABLET_DATA.get(), data(stack).withConsole(console.globalPos()));
                say(player, Component.translatable("message.projectrebirth.tablet.linked", pos.getX(), pos.getY(), pos.getZ()));
            }
            return InteractionResult.CONSUME;
        }
        if (player instanceof ServerPlayer server) {
            if (data(stack).pending().isPresent()) {
                say(player, point(server, stack, pos));
            } else {
                say(player, Component.translatable("message.projectrebirth.tablet.no_order"));
            }
        }
        return InteractionResult.CONSUME;
    }

    /**
     * A block right-clicked while an order waits (server): it becomes the next point; when the order has all its points it goes out.
     * Returns what to tell the player. Public for tests.
     */
    public static Component point(ServerPlayer player, ItemStack stack, BlockPos pos) {
        TabletData data = data(stack);
        if (data.pending().isEmpty()) {
            return Component.translatable("message.projectrebirth.tablet.no_order");
        }
        TabletData.Pending pending = data.pending().get();
        int have = pending.points().size();
        if (pending.taskType() == MachineTask.Type.WORK && have == 2
                && player.level().getCapability(Capabilities.ItemHandler.BLOCK, pos, null) == null) {
            return Component.translatable("message.projectrebirth.tablet.not_a_chest");
        }
        TabletData.Pending next = pending.withPoint(pos);
        if (pending.taskType() == MachineTask.Type.WORK && have == 1) {
            Component problem = ConsoleNetwork.areaProblem(pending.work(), next.points().get(0), pos);
            if (problem != null) {
                return problem;
            }
        }
        stack.set(GarrisonRegistry.TABLET_DATA.get(), data.withPending(next));
        boolean complete = switch (pending.taskType()) {
            case GUARD -> true;
            case PATROL -> next.points().size() >= RebirthConfig.TABLET_ROUTE_POINTS.get();
            case WORK -> next.points().size() >= 3;
            default -> true;
        };
        if (!complete) {
            return prompt(next);
        }
        Component answer = ConsoleNetwork.finish(player, stack);
        if (data(stack).pending().isPresent()) {
            // refused (the console out of reach, the area): the last point is taken back and the order still waits
            stack.set(GarrisonRegistry.TABLET_DATA.get(), data.withPending(pending));
        }
        return answer;
    }

    /** A right-click in the air while an order waits (server): a route of two points or more goes out, anything else is cancelled. */
    public static Component air(ServerPlayer player, ItemStack stack) {
        TabletData data = data(stack);
        if (data.pending().isEmpty()) {
            return null;
        }
        TabletData.Pending pending = data.pending().get();
        if (!player.isShiftKeyDown() && pending.taskType() == MachineTask.Type.PATROL && pending.points().size() >= 2) {
            Component answer = ConsoleNetwork.finish(player, stack);
            return answer;
        }
        stack.set(GarrisonRegistry.TABLET_DATA.get(), data.withoutPending());
        if (pending.taskType() == MachineTask.Type.PATROL && !pending.points().isEmpty()) {
            return Component.translatable("message.projectrebirth.tablet.route_short");
        }
        return Component.translatable("message.projectrebirth.tablet.cancelled");
    }

    /** Whom a waiting order is for, as the player reads it. */
    public static Component target(TabletData.Pending pending) {
        if (pending.machine().isPresent()) {
            return Component.literal(pending.label());
        }
        return pending.group() == ConsoleBlockEntity.ALL ? Component.translatable("screen.projectrebirth.console.all_machines")
                : Component.translatable("screen.projectrebirth.console.group", pending.group() + 1);
    }

    /** What the player should do next for a waiting order. */
    public static Component prompt(TabletData.Pending pending) {
        Component order = Component.translatable(pending.taskType().translationKey());
        int have = pending.points().size();
        String step = switch (pending.taskType()) {
            case GUARD -> "guard";
            case PATROL -> have == 0 ? "route_first" : "route_more";
            case WORK -> have == 0 ? "corner_first" : have == 1 ? "corner_second" : "chest";
            default -> "guard";
        };
        Component work = Component.translatable("work_kind.projectrebirth." + MachineTask.WORKS.get(pending.work()));
        return Component.translatable("message.projectrebirth.tablet.step." + step, order, target(pending), have,
                RebirthConfig.TABLET_ROUTE_POINTS.get(), work);
    }

    private static void say(Player player, @javax.annotation.Nullable Component message) {
        if (message != null) {
            player.displayClientMessage(message, true);
        }
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof GarrisonMachine machine)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ItemStack held = player.getItemInHand(hand);
        TabletData data = data(held);
        ConsoleBlockEntity console = data.console().map(where -> ConsoleLink.consoleAt((ServerLevel) player.level(), where)).orElse(null);
        if (console == null) {
            say(player, "no_console");
        } else if (ConsoleLink.link(console, (Entity) machine, machine.machineCore(), player)) {
            say(player, "machine_linked", target.getName());
        } else {
            say(player, "cannot_link");
        }
        return InteractionResult.CONSUME;
    }

    private static void say(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.projectrebirth.tablet." + key, args), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        TabletData data = data(stack);
        tooltip.add(Component.translatable("tooltip.projectrebirth.tablet.use").withStyle(ChatFormatting.GRAY));
        data.pending().ifPresent(pending -> tooltip.add(prompt(pending).copy().withStyle(ChatFormatting.YELLOW)));
        data.console().ifPresentOrElse(where -> tooltip.add(Component.translatable("tooltip.projectrebirth.tablet.console",
                        where.pos().getX(), where.pos().getY(), where.pos().getZ()).withStyle(ChatFormatting.AQUA)),
                () -> tooltip.add(Component.translatable("tooltip.projectrebirth.tablet.no_console").withStyle(ChatFormatting.GRAY)));
    }

    /** The tablet the player carries that is linked to this console (the one in hand first), or empty. */
    public static ItemStack linkedTablet(ServerPlayer player, ConsoleBlockEntity console) {
        for (ItemStack stack : List.of(player.getMainHandItem(), player.getOffhandItem())) {
            if (stack.getItem() instanceof TabletItem && data(stack).console().filter(console.globalPos()::equals).isPresent()) {
                return stack;
            }
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof TabletItem && data(stack).console().filter(console.globalPos()::equals).isPresent()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
