package com.projectrebirth.garrison;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The console's messages (1.7.0, block D): the view of its window to the players who have it open (or who look at the tablet's
 * list), and the buttons pressed in the window or on the tablet back to the server, which checks every one of them: the window must
 * be open at the console, or the player must carry a tablet linked to it within the console's range; the player must command it.
 */
public final class ConsoleNetwork {
    public static final int ORDER = 0;
    public static final int SET_GROUP = 1;
    public static final int GROUP_INTRUDERS = 2;
    public static final int CONSOLE_INTRUDERS = 3;
    public static final int WEATHER = 4;
    public static final int UNLINK = 5;
    public static final int REQUEST_VIEW = 6;

    /** Server to client: what the console's window or the tablet's list shows. */
    public record ViewPayload(BlockPos console, ConsoleView view) implements CustomPacketPayload {
        public static final Type<ViewPayload> TYPE = new Type<>(ProjectRebirth.id("console_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ViewPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, ViewPayload::console,
                ByteBufCodecs.fromCodecWithRegistries(ConsoleView.CODEC), ViewPayload::view,
                ViewPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Client to server: a button. {@code machine} or {@code group} chooses whom an order or a group change is for; {@code value} is
     * the task (as the ordinal of its type), the group, or a switch; {@code extra} the work (0 mine, 1 lumber, 2 farm).
     */
    public record ActionPayload(BlockPos console, int action, Optional<UUID> machine, int group, int value, int extra, boolean fromTablet)
            implements CustomPacketPayload {
        public static final Type<ActionPayload> TYPE = new Type<>(ProjectRebirth.id("console_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    BlockPos.STREAM_CODEC.encode(buf, payload.console());
                    ByteBufCodecs.VAR_INT.encode(buf, payload.action());
                    ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).encode(buf, payload.machine());
                    ByteBufCodecs.VAR_INT.encode(buf, payload.group() + 1);
                    ByteBufCodecs.VAR_INT.encode(buf, payload.value());
                    ByteBufCodecs.VAR_INT.encode(buf, payload.extra());
                    ByteBufCodecs.BOOL.encode(buf, payload.fromTablet());
                },
                buf -> new ActionPayload(BlockPos.STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).decode(buf), ByteBufCodecs.VAR_INT.decode(buf) - 1,
                        ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.BOOL.decode(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Set by the client: takes a view for the open window or the tablet. */
    public static Consumer<ViewPayload> clientHandler = payload -> {
    };

    private ConsoleNetwork() {
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(ConsoleNetwork::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(ViewPayload.TYPE, ViewPayload.STREAM_CODEC, (payload, context) -> clientHandler.accept(payload));
        registrar.playToServer(ActionPayload.TYPE, ActionPayload.STREAM_CODEC, ConsoleNetwork::onAction);
    }

    public static void sendView(ServerPlayer player, ConsoleBlockEntity console) {
        if (console.getLevel() instanceof ServerLevel level) {
            PacketDistributor.sendToPlayer(player, new ViewPayload(console.getBlockPos(), console.view(level)));
        }
    }

    private static void onAction(ActionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            Component answer = act(player, payload);
            if (answer != null) {
                player.displayClientMessage(answer, true);
            }
        }
    }

    /** Whether this player may press this console's buttons now: its window open, or a linked tablet carried within range. */
    static boolean mayAct(ServerPlayer player, ConsoleBlockEntity console, boolean fromTablet) {
        if (!console.mayCommand(player)) {
            return false;
        }
        if (fromTablet) {
            return player.level().dimension().equals(console.getLevel().dimension()) && ConsoleLink.inRange(player.blockPosition(), console.getBlockPos())
                    && !TabletItem.linkedTablet(player, console).isEmpty();
        }
        return player.containerMenu instanceof ConsoleMenu menu && menu.pos().equals(console.getBlockPos()) && menu.stillValid(player);
    }

    /** Does what the button asks (server); returns what to tell the player, or null. Public for tests. */
    public static Component act(ServerPlayer player, ActionPayload payload) {
        if (!(player.level().getBlockEntity(payload.console()) instanceof ConsoleBlockEntity console)) {
            if (!payload.fromTablet() || !(player.level() instanceof ServerLevel level)) {
                return null;
            }
            ConsoleBlockEntity far = ConsoleLink.consoleAt(level, net.minecraft.core.GlobalPos.of(level.dimension(), payload.console()));
            if (far == null) {
                return Component.translatable("message.projectrebirth.tablet.console_out_of_reach");
            }
            return actOn(player, far, payload);
        }
        return actOn(player, console, payload);
    }

    private static Component actOn(ServerPlayer player, ConsoleBlockEntity console, ActionPayload payload) {
        if (!mayAct(player, console, payload.fromTablet())) {
            return Component.translatable("message.projectrebirth.console.not_yours");
        }
        switch (payload.action()) {
            case ORDER -> {
                return order(player, console, payload.machine(), payload.group(), MachineTask.Type.byId(payload.value()), payload.extra());
            }
            case SET_GROUP -> payload.machine().ifPresent(machine -> console.setGroup(machine, payload.value()));
            case GROUP_INTRUDERS -> console.setGroupIntruders(payload.group(), payload.value() != 0);
            case CONSOLE_INTRUDERS -> {
                if (payload.value() != 0 && !RebirthConfig.ALLOW_INTRUDER_MODE.get()) {
                    return Component.translatable("message.projectrebirth.console.intruders_forbidden");
                }
                console.setIntruderMode(payload.value() != 0);
            }
            case WEATHER -> console.setWeatherLink(payload.value() != 0);
            case UNLINK -> payload.machine().ifPresent(console::unlink);
            case REQUEST_VIEW -> {
            }
            default -> {
                return null;
            }
        }
        sendView(player, console);
        return null;
    }

    /** An order with the marks of the player's tablet linked to this console: to one machine, or to a group. */
    static Component order(ServerPlayer player, ConsoleBlockEntity console, Optional<UUID> machine, int group, MachineTask.Type type, int work) {
        ItemStack tablet = TabletItem.linkedTablet(player, console);
        TabletData marks = tablet.isEmpty() ? TabletData.EMPTY : TabletItem.data(tablet);
        MachineTask task;
        switch (type) {
            case GUARD -> {
                if (marks.guard().isEmpty()) {
                    return Component.translatable("message.projectrebirth.console.need_guard");
                }
                task = MachineTask.at(MachineTask.Type.GUARD, marks.guard().get());
            }
            case PATROL -> {
                if (marks.route().size() < 2) {
                    return Component.translatable("message.projectrebirth.console.need_route");
                }
                task = MachineTask.patrol(marks.route());
            }
            case ESCORT -> task = MachineTask.escort(player.getUUID());
            case WORK -> {
                if (marks.areaFrom().isEmpty() || marks.areaTo().isEmpty() || marks.chest().isEmpty()) {
                    return Component.translatable("message.projectrebirth.console.need_area");
                }
                String kind = MachineTask.WORKS.get(Math.floorMod(work, MachineTask.WORKS.size()));
                BlockPos a = marks.areaFrom().get(), b = marks.areaTo().get();
                int side = Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getZ() - b.getZ())) + 1;
                int depth = Math.abs(a.getY() - b.getY()) + 1;
                int max = switch (kind) {
                    case "mine", "mine_ore" -> RebirthConfig.WORK_AREA_MINE.get();
                    case "lumber" -> RebirthConfig.WORK_AREA_LUMBER.get();
                    default -> RebirthConfig.WORK_AREA_FARM.get();
                };
                if (side > max || kind.startsWith("mine") && depth > RebirthConfig.WORK_AREA_MINE_DEPTH.get()) {
                    return Component.translatable("message.projectrebirth.console.area_too_big", max);
                }
                task = MachineTask.work(kind, a, b, marks.chest().get());
            }
            default -> task = MachineTask.simple(type);
        }
        if (machine.isPresent()) {
            if (!console.knows(machine.get())) {
                return null;
            }
            console.order(machine.get(), task);
        } else if (group >= 0 && group < ConsoleBlockEntity.GROUPS) {
            if (console.orderGroup(group, task) == 0) {
                return Component.translatable("message.projectrebirth.console.group_empty");
            }
        } else {
            return null;
        }
        sendView(player, console);
        return Component.translatable("message.projectrebirth.console.ordered", Component.translatable(type.translationKey()));
    }
}
