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
 * The tactical tablet (1.7.0, block D): the console's orders in the field.
 *
 * <ul>
 *   <li>right-click a console: link the tablet to it;</li>
 *   <li>right-click a machine: link it to the tablet's console;</li>
 *   <li>right-click a block: set the mark of the current mode (a point to guard, a route point, a corner of the work area, the
 *   chest of the work); with Shift on a block in the route mode the route is cleared;</li>
 *   <li>Shift + right-click in the air: the next mode;</li>
 *   <li>right-click in the air: the short list of the machines and the quick orders.</li>
 * </ul>
 * The marks are drawn in the world while the tablet is in hand.
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
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                TabletData next = data(stack).withMode(data(stack).mode() + 1);
                stack.set(GarrisonRegistry.TABLET_DATA.get(), next);
                player.displayClientMessage(Component.translatable("message.projectrebirth.tablet.mode",
                        Component.translatable("tablet_mode.projectrebirth." + next.mode())), true);
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
        TabletData data = data(stack);
        if (level.getBlockEntity(pos) instanceof ConsoleBlockEntity console) {
            if (!console.mayCommand(player)) {
                say(player, "not_yours");
            } else {
                stack.set(GarrisonRegistry.TABLET_DATA.get(), data.withConsole(console.globalPos()));
                say(player, "linked", pos.getX(), pos.getY(), pos.getZ());
            }
            return InteractionResult.CONSUME;
        }
        TabletData next = switch (data.mode()) {
            case TabletData.ROUTE -> player.isShiftKeyDown() ? data.withoutRoute() : data.withRoutePoint(pos, RebirthConfig.TABLET_ROUTE_POINTS.get());
            case TabletData.AREA -> data.withAreaCorner(pos);
            case TabletData.CHEST -> level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null) != null ? data.withChest(pos) : null;
            default -> data.withGuard(pos.above());
        };
        if (next == null) {
            say(player, "not_a_chest");
            return InteractionResult.CONSUME;
        }
        stack.set(GarrisonRegistry.TABLET_DATA.get(), next);
        say(player, "marked_" + data.mode(), pos.getX(), pos.getY(), pos.getZ());
        return InteractionResult.CONSUME;
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
        tooltip.add(Component.translatable("tooltip.projectrebirth.tablet.mode", Component.translatable("tablet_mode.projectrebirth." + data.mode()))
                .withStyle(ChatFormatting.GRAY));
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
