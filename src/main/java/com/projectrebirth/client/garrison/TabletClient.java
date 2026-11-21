package com.projectrebirth.client.garrison;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.projectrebirth.client.ui.UiSounds;
import com.projectrebirth.garrison.ConsoleNetwork;
import com.projectrebirth.garrison.ConsoleView;
import com.projectrebirth.garrison.GarrisonMachine;
import com.projectrebirth.garrison.MachineTask;
import com.projectrebirth.garrison.TabletData;
import com.projectrebirth.garrison.TabletItem;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The tactical tablet on the client (1.8.0, block F): while it is in hand, the view of its console (asked every two seconds), the
 * machines chosen in the world (a right click on one of the console's machines chooses it, Shift adds), the key of the round order
 * menu (Y by default), and how the last orders went (a mark over each machine for two seconds; a cross and the reason when it could
 * not). The server checks every order as before. Client only.
 */
public final class TabletClient {
    public static final KeyMapping MENU_KEY = new KeyMapping("key.projectrebirth.tablet_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y,
            "key.categories.projectrebirth");
    /** How far own machines are lit up and can be chosen (blocks). */
    public static final double RADIUS = 64.0;
    private static final int ASK_TICKS = 40;
    /** Ticks a mark over a machine stays. */
    public static final int ACK_TICKS = 40;

    /** How an order went for one machine: its task, taken or not, when. */
    public record Ack(int task, boolean taken, long time) {
    }

    @Nullable
    private static ConsoleView view;
    @Nullable
    private static GlobalPos viewOf;
    private static final Set<UUID> SELECTED = new LinkedHashSet<>();
    private static final Map<UUID, Ack> ACKS = new HashMap<>();
    @Nullable
    private static Component refusal;
    private static long refusalTime;
    private static int ask;
    /** The group last switched to with the wheel (-1: all). */
    private static int group = -1;

    private TabletClient() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener((RegisterKeyMappingsEvent event) -> event.register(MENU_KEY));
        NeoForge.EVENT_BUS.addListener(TabletClient::onTick);
        NeoForge.EVENT_BUS.addListener(TabletClient::onInteract);
        ConsoleNetwork.ackHandler = payload -> Minecraft.getInstance().execute(() -> acknowledged(payload));
    }

    /** The tablet in the player's hand (main hand first), or empty. */
    public static ItemStack held(@Nullable Player player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        if (player.getMainHandItem().getItem() instanceof TabletItem) {
            return player.getMainHandItem();
        }
        return player.getOffhandItem().getItem() instanceof TabletItem ? player.getOffhandItem() : ItemStack.EMPTY;
    }

    @Nullable
    static GlobalPos console() {
        ItemStack tablet = held(Minecraft.getInstance().player);
        return tablet.isEmpty() ? null : TabletItem.data(tablet).console().orElse(null);
    }

    /** The view of the held tablet's console, when it came for that console. */
    @Nullable
    public static ConsoleView view() {
        GlobalPos console = console();
        return console != null && console.equals(viewOf) ? view : null;
    }

    /** A view came from the server (the tablet's console, or the console window's). */
    static void accept(BlockPos console, ConsoleView next) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            view = next;
            viewOf = GlobalPos.of(minecraft.level.dimension(), console);
            SELECTED.removeIf(id -> row(id) == null);
        }
    }

    @Nullable
    public static ConsoleView.Row row(UUID id) {
        ConsoleView current = view;
        if (current != null) {
            for (ConsoleView.Row row : current.rows()) {
                if (row.id().equals(id)) {
                    return row;
                }
            }
        }
        return null;
    }

    /** Whether a machine of the tablet's console, near enough, is lit up while the tablet is in hand. */
    public static boolean lit(Entity entity) {
        Player player = Minecraft.getInstance().player;
        return entity instanceof GarrisonMachine && !held(player).isEmpty() && view() != null && row(entity.getUUID()) != null
                && entity.distanceToSqr(player) <= RADIUS * RADIUS;
    }

    public static boolean selected(UUID id) {
        return SELECTED.contains(id);
    }

    public static Set<UUID> selection() {
        return SELECTED;
    }

    public static int group() {
        return group;
    }

    /** The wheel with the menu key held: the next or previous group (all machines of it are chosen); -1 is every machine. */
    static void switchGroup(int step) {
        ConsoleView current = view();
        if (current == null) {
            return;
        }
        group = Math.floorMod(group + 1 + step, com.projectrebirth.garrison.ConsoleBlockEntity.GROUPS + 1) - 1;
        SELECTED.clear();
        for (ConsoleView.Row row : current.rows()) {
            if (group < 0 || row.group() == group) {
                SELECTED.add(row.id());
            }
        }
        UiSounds.CLICK.play();
    }

    @Nullable
    public static Ack ack(UUID id) {
        Ack found = ACKS.get(id);
        Minecraft minecraft = Minecraft.getInstance();
        return found != null && minecraft.level != null && minecraft.level.getGameTime() - found.time <= ACK_TICKS ? found : null;
    }

    /** The reason of the last refused order, for a few seconds. */
    @Nullable
    public static Component refusal() {
        Minecraft minecraft = Minecraft.getInstance();
        return refusal != null && minecraft.level != null && minecraft.level.getGameTime() - refusalTime <= 100 ? refusal : null;
    }

    private static void acknowledged(ConsoleNetwork.OrderAckPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        long now = minecraft.level.getGameTime();
        for (UUID id : payload.taken()) {
            ACKS.put(id, new Ack(payload.task(), true, now));
        }
        for (UUID id : payload.refused()) {
            ACKS.put(id, new Ack(payload.task(), false, now));
        }
        if (!payload.refused().isEmpty() && !payload.reason().isEmpty()) {
            refusal = Component.translatable(payload.reason());
            refusalTime = now;
            UiSounds.ERROR.play();
        } else if (!payload.taken().isEmpty()) {
            UiSounds.TOGGLE.play();
        }
    }

    /** Sends an order to the chosen machines from the tablet (orders with points then wait for the clicks in the world). */
    static void order(MachineTask.Type type, int work) {
        GlobalPos console = console();
        if (console == null || SELECTED.isEmpty()) {
            return;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ConsoleNetwork.OrderManyPayload(console.pos(), List.copyOf(SELECTED),
                type.ordinal(), work, List.of(), true));
    }

    private static void onTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        ItemStack tablet = held(player);
        GlobalPos console = console();
        if (tablet.isEmpty() || console == null) {
            ask = 0;
            while (MENU_KEY.consumeClick()) {
                // the key does nothing without a linked tablet in hand
            }
            return;
        }
        if (--ask <= 0) {
            ask = ASK_TICKS;
            if (console.dimension().equals(minecraft.level.dimension())) {
                ConsoleOrders.send(console.pos(), ConsoleNetwork.REQUEST_VIEW, null, -1, 0, 0, true);
            }
        }
        ACKS.values().removeIf(ack -> minecraft.level.getGameTime() - ack.time > ACK_TICKS);
        if (minecraft.screen == null && MENU_KEY.consumeClick()) {
            minecraft.setScreen(new RadialOrderScreen());
        }
    }

    /** A right click on a machine of the tablet's console chooses it (Shift adds); the server leaves it alone. */
    private static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND && !held(event.getEntity()).equals(event.getItemStack())) {
            return;
        }
        if (!(event.getItemStack().getItem() instanceof TabletItem) || row(event.getTarget().getUUID()) == null || view() == null) {
            return;
        }
        UUID id = event.getTarget().getUUID();
        if (!event.getEntity().isShiftKeyDown()) {
            boolean only = SELECTED.size() == 1 && SELECTED.contains(id);
            SELECTED.clear();
            if (!only) {
                SELECTED.add(id);
            }
        } else if (!SELECTED.remove(id)) {
            SELECTED.add(id);
        }
        group = -2;
        UiSounds.CLICK.play();
    }

    /** What the tablet's order waits for, if anything. */
    @Nullable
    static TabletData.Pending pending() {
        ItemStack tablet = held(Minecraft.getInstance().player);
        return tablet.isEmpty() ? null : TabletItem.data(tablet).pending().orElse(null);
    }
}
