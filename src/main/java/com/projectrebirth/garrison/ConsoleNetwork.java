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
    /** 1.8.0: the target rules (OrderRules bits in value) of a group, or with ALL of the machines without a group. */
    public static final int SET_RULES = 7;
    /** 1.8.0, block J: a zone «do not touch» (its corner in value and extra, its sizes in group), the search radius. */
    public static final int ADD_ZONE = 8;
    public static final int REMOVE_ZONE = 9;
    public static final int SEARCH_RADIUS = 10;
    /** The longest name the console's window may give a machine. */
    public static final int NAME_LENGTH = 32;

    /**
     * 1.8.0, client to server: one order to the machines chosen in the console's window (a click on a card, Shift, a frame on the map).
     * {@code points} are the places clicked on the map (only x and z count; the server puts them on the ground): the guard point, the
     * route, or two corners of a work area. Machines that cannot carry out the order are left out and counted in the answer.
     */
    public record OrderManyPayload(BlockPos console, java.util.List<UUID> machines, int task, int work, java.util.List<BlockPos> points,
                                   boolean fromTablet) implements CustomPacketPayload {
        public OrderManyPayload(BlockPos console, java.util.List<UUID> machines, int task, int work, java.util.List<BlockPos> points) {
            this(console, machines, task, work, points, false);
        }

        public static final Type<OrderManyPayload> TYPE = new Type<>(ProjectRebirth.id("console_order_many"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OrderManyPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OrderManyPayload::console,
                UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(64)), OrderManyPayload::machines,
                ByteBufCodecs.VAR_INT, OrderManyPayload::task,
                ByteBufCodecs.VAR_INT, OrderManyPayload::work,
                BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(32)), OrderManyPayload::points,
                ByteBufCodecs.BOOL, OrderManyPayload::fromTablet,
                OrderManyPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 1.8.0, client to server: a new name for a machine, from its window in the console. */
    public record RenamePayload(BlockPos console, UUID machine, String name) implements CustomPacketPayload {
        public static final Type<RenamePayload> TYPE = new Type<>(ProjectRebirth.id("console_rename"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RenamePayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, RenamePayload::console, UUIDUtil.STREAM_CODEC, RenamePayload::machine,
                ByteBufCodecs.stringUtf8(NAME_LENGTH * 4), RenamePayload::name, RenamePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 1.8.0, client to server: the machine's window asks what it carries (answered with {@link DetailsPayload}). */
    public record DetailsRequestPayload(BlockPos console, UUID machine) implements CustomPacketPayload {
        public static final Type<DetailsRequestPayload> TYPE = new Type<>(ProjectRebirth.id("console_details_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DetailsRequestPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, DetailsRequestPayload::console, UUIDUtil.STREAM_CODEC, DetailsRequestPayload::machine,
                DetailsRequestPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 1.8.0, server to client: what a machine carries, for its window: whether it is loaded (else nothing is known but the card),
     * its gear (an android's hands, armor and pack, not empty) and its upgrades.
     */
    public record DetailsPayload(UUID machine, boolean loaded, java.util.List<ItemStack> gear, java.util.List<ItemStack> upgrades)
            implements CustomPacketPayload {
        public static final Type<DetailsPayload> TYPE = new Type<>(ProjectRebirth.id("console_details"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DetailsPayload> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, DetailsPayload::machine, ByteBufCodecs.BOOL, DetailsPayload::loaded,
                ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(64)), DetailsPayload::gear,
                ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(32)), DetailsPayload::upgrades,
                DetailsPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 1.8.0 block F, server to client: how an order went, machine by machine: those that took it (a mark over each for two seconds)
     * and those left out with the reason (a cross over each, the reason in the tablet's lines).
     */
    public record OrderAckPayload(int task, java.util.List<UUID> taken, java.util.List<UUID> refused, String reason) implements CustomPacketPayload {
        public static final Type<OrderAckPayload> TYPE = new Type<>(ProjectRebirth.id("console_order_ack"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OrderAckPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, OrderAckPayload::task, UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(64)), OrderAckPayload::taken,
                UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(64)), OrderAckPayload::refused, ByteBufCodecs.stringUtf8(128), OrderAckPayload::reason,
                OrderAckPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Set by the client: takes how an order went. */
    public static Consumer<OrderAckPayload> ackHandler = payload -> {
    };

    private static void ack(ServerPlayer player, MachineTask.Type type, java.util.List<UUID> taken, java.util.List<UUID> refused, String reason) {
        PacketDistributor.sendToPlayer(player, new OrderAckPayload(type.ordinal(), java.util.List.copyOf(taken.size() > 64 ? taken.subList(0, 64) : taken),
                java.util.List.copyOf(refused.size() > 64 ? refused.subList(0, 64) : refused), reason == null ? "" : reason));
    }

    /** Set by the client: takes what a machine carries. */
    public static Consumer<DetailsPayload> detailsHandler = payload -> {
    };

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
     * the task (as the ordinal of its type), the group, or a switch; {@code extra} the work (an index into MachineTask.WORKS). {@code group} may be ConsoleBlockEntity.ALL.
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

    /** Block I: the worst alarm going on at a console, sent once a second to its owner and faction while there is one (the HUD mark). */
    public record AlarmPayload(BlockPos console, int alarm) implements CustomPacketPayload {
        public static final Type<AlarmPayload> TYPE = new Type<>(ProjectRebirth.id("console_alarm"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AlarmPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, AlarmPayload::console, ByteBufCodecs.VAR_INT, AlarmPayload::alarm, AlarmPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Set by the client: takes the alarm mark for the HUD. */
    public static Consumer<AlarmPayload> alarmHandler = payload -> {
    };

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
        registrar.playToClient(AlarmPayload.TYPE, AlarmPayload.STREAM_CODEC, (payload, context) -> alarmHandler.accept(payload));
        registrar.playToServer(OrderManyPayload.TYPE, OrderManyPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                Component answer = orderMany(player, payload);
                if (answer != null) {
                    player.displayClientMessage(answer, true);
                }
            }
        });
        registrar.playToServer(RenamePayload.TYPE, RenamePayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                rename(player, payload);
            }
        });
        registrar.playToServer(DetailsRequestPayload.TYPE, DetailsRequestPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                DetailsPayload answer = details(player, payload);
                if (answer != null) {
                    PacketDistributor.sendToPlayer(player, answer);
                }
            }
        });
        registrar.playToClient(DetailsPayload.TYPE, DetailsPayload.STREAM_CODEC, (payload, context) -> detailsHandler.accept(payload));
        registrar.playToClient(OrderAckPayload.TYPE, OrderAckPayload.STREAM_CODEC, (payload, context) -> ackHandler.accept(payload));
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
            case SET_RULES -> console.setRules(payload.group(), payload.value());
            case ADD_ZONE -> {
                // value, extra: the corner with the least x and z; group: the size along x times 512 plus the size along z
                int sx = Math.max(0, payload.group()) / 512, sz = Math.max(0, payload.group()) % 512;
                if (!console.addZone(new ConsoleView.Zone(payload.value(), payload.extra(), payload.value() + sx, payload.extra() + sz))) {
                    player.displayClientMessage(Component.translatable("message.projectrebirth.console.zone_refused", ConsoleBlockEntity.MAX_ZONES), true);
                }
            }
            case REMOVE_ZONE -> console.removeZonesAt(payload.value(), payload.extra());
            case SEARCH_RADIUS -> console.setSearchRadius(payload.value());
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

    /** The console of a window open at it (1.8.0 payloads come only from the console's window), or null. */
    @javax.annotation.Nullable
    private static ConsoleBlockEntity openConsole(ServerPlayer player, BlockPos pos) {
        return player.level().getBlockEntity(pos) instanceof ConsoleBlockEntity console && mayAct(player, console, false) ? console : null;
    }

    /** 1.8.0: renames a machine from its window in the console (server). Public for tests. */
    public static boolean rename(ServerPlayer player, RenamePayload payload) {
        ConsoleBlockEntity console = openConsole(player, payload.console());
        String name = net.minecraft.util.StringUtil.filterText(payload.name()).trim();
        if (console == null || !console.knows(payload.machine()) || name.length() > NAME_LENGTH) {
            return false;
        }
        console.rename(payload.machine(), name);
        sendView(player, console);
        return true;
    }

    /** 1.8.0: what a machine carries, if it is loaded in the console's world (server). */
    @javax.annotation.Nullable
    static DetailsPayload details(ServerPlayer player, DetailsRequestPayload payload) {
        ConsoleBlockEntity console = openConsole(player, payload.console());
        if (console == null || !console.knows(payload.machine()) || !(console.getLevel() instanceof ServerLevel level)) {
            return null;
        }
        net.minecraft.world.entity.Entity entity = level.getEntity(payload.machine());
        if (!(entity instanceof GarrisonMachine machine)) {
            return new DetailsPayload(payload.machine(), false, java.util.List.of(), java.util.List.of());
        }
        java.util.List<ItemStack> gear = new java.util.ArrayList<>();
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                ItemStack stack = living.getItemBySlot(slot);
                if (!stack.isEmpty()) {
                    gear.add(stack.copy());
                }
            }
        }
        java.util.List<ItemStack> upgrades = machine.consoleUpgrades().stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
        return new DetailsPayload(payload.machine(), true, gear.size() > 64 ? gear.subList(0, 64) : gear,
                upgrades.size() > 32 ? upgrades.subList(0, 32) : upgrades);
    }

    /**
     * 1.8.0: one order to several machines from the console's window (server), with the points clicked on its map. Checks as the old
     * orders do (the window open, the right to command); machines the console does not know are skipped, and those that cannot carry
     * out the order (OrderRules) are left out and counted. Returns what to tell the player. Public for tests.
     */
    public static Component orderMany(ServerPlayer player, OrderManyPayload payload) {
        ConsoleBlockEntity console = player.level().getBlockEntity(payload.console()) instanceof ConsoleBlockEntity here ? here
                : payload.fromTablet() ? ConsoleLink.consoleAt(player.serverLevel(), net.minecraft.core.GlobalPos.of(player.level().dimension(), payload.console()))
                : null;
        if (console == null) {
            return Component.translatable(payload.fromTablet() ? "message.projectrebirth.tablet.console_out_of_reach" : "message.projectrebirth.console.not_yours");
        }
        if (!mayAct(player, console, payload.fromTablet())) {
            return Component.translatable("message.projectrebirth.console.not_yours");
        }
        MachineTask.Type type = MachineTask.Type.byId(payload.task());
        java.util.List<UUID> able = new java.util.ArrayList<>();
        String problem = null;
        for (UUID machine : payload.machines()) {
            if (!console.knows(machine)) {
                continue;
            }
            String why = console.problem(machine, type);
            if (why == null) {
                able.add(machine);
            } else {
                problem = why;
            }
        }
        java.util.List<UUID> refused = payload.machines().stream().filter(id -> console.knows(id) && !able.contains(id)).toList();
        if (able.isEmpty()) {
            ack(player, type, java.util.List.of(), refused, problem);
            return problem == null ? Component.translatable("message.projectrebirth.console.choose_first") : Component.translatable(problem);
        }
        if (payload.fromTablet() && payload.points().isEmpty() && needsPoints(type)) {
            // the points are marked in the world with the tablet, as with the old orders; the order waits on it for the chosen machines
            ItemStack tablet = TabletItem.linkedTablet(player, console);
            if (tablet.isEmpty()) {
                return Component.translatable("message.projectrebirth.console.need_tablet");
            }
            TabletData.Pending pending = new TabletData.Pending(java.util.Optional.empty(), -1, type.ordinal(),
                    Math.floorMod(payload.work(), MachineTask.WORKS.size()), "", java.util.List.of(), java.util.List.copyOf(able));
            tablet.set(GarrisonRegistry.TABLET_DATA.get(), TabletItem.data(tablet).withPending(pending));
            if (!refused.isEmpty()) {
                ack(player, type, java.util.List.of(), refused, problem);
            }
            return TabletItem.prompt(pending);
        }
        MachineTask task;
        java.util.List<BlockPos> points = new java.util.ArrayList<>();
        for (BlockPos point : payload.points()) {
            BlockPos ground = ground(player.serverLevel(), point);
            if (ground == null) {
                return Component.translatable("message.projectrebirth.console.map_unloaded");
            }
            points.add(ground);
        }
        switch (type) {
            case GUARD -> {
                if (points.isEmpty()) {
                    return Component.translatable("message.projectrebirth.console.map_point_needed");
                }
                task = MachineTask.at(MachineTask.Type.GUARD, points.get(0).above());
            }
            case PATROL -> {
                if (points.size() < 2) {
                    return Component.translatable("message.projectrebirth.console.map_route_needed");
                }
                task = MachineTask.patrol(points);
            }
            case WORK -> {
                int kind = Math.floorMod(payload.work(), MachineTask.WORKS.size());
                if (points.size() < 2) {
                    if (!RebirthConfig.WORK_AUTO.get()) {
                        return Component.translatable("message.projectrebirth.console.map_area_needed");
                    }
                    // 1.8.0, block J: no area: they find their work and their storage themselves
                    task = MachineTask.workOwn(MachineTask.WORKS.get(kind));
                    for (UUID machine : able) {
                        console.order(machine, task);
                    }
                    sendView(player, console);
                    ack(player, type, able, refused, problem);
                    return Component.translatable("message.projectrebirth.console.ordered_own_work", able.size());
                }
                BlockPos[] box = workBox(MachineTask.WORKS.get(kind), points.get(0), points.get(1));
                Component tooBig = areaProblem(kind, box[0], box[1]);
                if (tooBig != null) {
                    return tooBig;
                }
                BlockPos chest = chestNear(player.serverLevel(), box[0], box[1]);
                if (chest == null) {
                    return Component.translatable("message.projectrebirth.console.no_chest_near");
                }
                task = MachineTask.work(MachineTask.WORKS.get(kind), box[0], box[1], chest);
            }
            case AREA_PATROL -> {
                BlockPos centre = null;
                for (UUID machine : able) {
                    centre = console.orderArea(java.util.Optional.of(machine), -1);
                }
                sendView(player, console);
                ack(player, type, able, refused, problem);
                return centre == null ? Component.translatable("message.projectrebirth.console.area_patrol_none")
                        : Component.translatable("message.projectrebirth.console.ordered_many", Component.translatable(type.translationKey()), able.size());
            }
            case ESCORT -> task = MachineTask.escort(player.getUUID());
            default -> task = MachineTask.simple(type);
        }
        for (UUID machine : able) {
            console.order(machine, task);
        }
        sendView(player, console);
        ack(player, type, able, refused, problem);
        Component name = Component.translatable(type.translationKey());
        if (!console.powered()) {
            return Component.translatable("message.projectrebirth.console.ordered_no_power", name);
        }
        int left = payload.machines().size() - able.size();
        return left > 0 ? Component.translatable("message.projectrebirth.console.ordered_some", name, able.size(), left)
                : Component.translatable("message.projectrebirth.console.ordered_many", name, able.size());
    }

    /** The top block of the ground at a map point's x and z, or null when that place is not loaded. */
    @javax.annotation.Nullable
    static BlockPos ground(ServerLevel level, BlockPos point) {
        if (!level.hasChunkAt(point)) {
            return null;
        }
        int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, point.getX(), point.getZ());
        return new BlockPos(point.getX(), top - 1, point.getZ());
    }

    /**
     * The box of a work area drawn on the map: a mine goes down from the ground as deep as allowed, a farm takes the ground and a
     * little above, a lumber area the trees above the ground.
     */
    static BlockPos[] workBox(String work, BlockPos a, BlockPos b) {
        int low = Math.min(a.getY(), b.getY());
        int high = Math.max(a.getY(), b.getY());
        int bottom;
        int top;
        if (work.startsWith("mine")) {
            top = high;
            bottom = top - RebirthConfig.WORK_AREA_MINE_DEPTH.get() + 1;
        } else if (work.equals("lumber")) {
            bottom = low;
            top = high + 24;
        } else {
            bottom = low - 1;
            top = high + 2;
        }
        return new BlockPos[] {new BlockPos(Math.min(a.getX(), b.getX()), bottom, Math.min(a.getZ(), b.getZ())),
                new BlockPos(Math.max(a.getX(), b.getX()), top, Math.max(a.getZ(), b.getZ()))};
    }

    /** A chest (any item store) in the work area or up to 8 blocks round it, nearest to its middle; null when there is none. */
    @javax.annotation.Nullable
    static BlockPos chestNear(ServerLevel level, BlockPos from, BlockPos to) {
        BlockPos middle = new BlockPos((from.getX() + to.getX()) / 2, (from.getY() + to.getY()) / 2, (from.getZ() + to.getZ()) / 2);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int minCx = (from.getX() - 8) >> 4, maxCx = (to.getX() + 8) >> 4, minCz = (from.getZ() - 8) >> 4, maxCz = (to.getZ() + 8) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                    if (pos.getX() < from.getX() - 8 || pos.getX() > to.getX() + 8 || pos.getZ() < from.getZ() - 8 || pos.getZ() > to.getZ() + 8
                            || pos.getY() < from.getY() - 8 || pos.getY() > to.getY() + 8) {
                        continue;
                    }
                    if (level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, null) == null
                            || level.getBlockEntity(pos) instanceof ConsoleBlockEntity) {
                        continue;
                    }
                    double distance = pos.distSqr(middle);
                    if (distance < bestDistance) {
                        best = pos.immutable();
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    /** Whether an order needs points marked in the world (1.7.1): those go to the tablet and wait for the player's clicks. */
    public static boolean needsPoints(MachineTask.Type type) {
        // 1.8.0, block J: work needs no area when work androids find their work themselves (the console's map can still give one)
        return type == MachineTask.Type.GUARD || type == MachineTask.Type.PATROL || type == MachineTask.Type.WORK && !ownWork();
    }

    /** Whether a work order without an area lets the androids find their work (on when the config is not there, as on a client). */
    public static boolean ownWork() {
        try {
            return RebirthConfig.WORK_AUTO.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    /**
     * An order from a button (the tablet's screen or the console's window), to one machine, a group or every machine. Orders without
     * points go out at once; an order with points waits on the player's tablet linked to this console until the points are marked.
     */
    static Component order(ServerPlayer player, ConsoleBlockEntity console, Optional<UUID> machine, int group, MachineTask.Type type, int work) {
        String label;
        if (machine.isPresent()) {
            if (!console.knows(machine.get())) {
                return null;
            }
            label = console.nameOf(machine.get());
        } else if (group == ConsoleBlockEntity.ALL || group >= 0 && group < ConsoleBlockEntity.GROUPS) {
            if (console.members(group) == 0) {
                return Component.translatable("message.projectrebirth.console.group_empty");
            }
            label = "";
        } else {
            return null;
        }
        if (type == MachineTask.Type.AREA_PATROL) {
            // 1.7.2, block D: no points; the walking machines spread out round their middle
            BlockPos centre = console.orderArea(machine, group);
            if (centre == null) {
                return Component.translatable("message.projectrebirth.console.area_patrol_none");
            }
            sendView(player, console);
            return Component.translatable("message.projectrebirth.console.ordered_area", RebirthConfig.AREA_PATROL_RADIUS.get(),
                    centre.getX(), centre.getY(), centre.getZ());
        }
        if (!needsPoints(type)) {
            MachineTask task = type == MachineTask.Type.ESCORT ? MachineTask.escort(player.getUUID())
                    : type == MachineTask.Type.WORK ? MachineTask.workOwn(MachineTask.WORKS.get(Math.floorMod(work, MachineTask.WORKS.size())))
                    : MachineTask.simple(type);
            return deliver(player, console, machine, group, type, task);
        }
        ItemStack tablet = TabletItem.linkedTablet(player, console);
        if (tablet.isEmpty()) {
            return Component.translatable("message.projectrebirth.console.need_tablet");
        }
        int kind = Math.floorMod(work, MachineTask.WORKS.size());
        TabletData.Pending pending = new TabletData.Pending(machine, machine.isPresent() ? -1 : group, type.ordinal(), kind, label, java.util.List.of());
        tablet.set(GarrisonRegistry.TABLET_DATA.get(), TabletItem.data(tablet).withPending(pending));
        return TabletItem.prompt(pending);
    }

    /**
     * The points of a waiting order are all there (server): the order goes to the console. Returns what to tell the player; the
     * tablet keeps the points as its marks. A problem (the area too big, the console out of reach) is told and nothing is ordered.
     */
    public static Component finish(ServerPlayer player, ItemStack tablet) {
        TabletData data = TabletItem.data(tablet);
        if (data.pending().isEmpty() || data.console().isEmpty() || !(player.level() instanceof ServerLevel level)) {
            return null;
        }
        TabletData.Pending pending = data.pending().get();
        ConsoleBlockEntity console = ConsoleLink.consoleAt(level, data.console().get());
        if (console == null) {
            return Component.translatable("message.projectrebirth.tablet.console_out_of_reach");
        }
        if (!mayAct(player, console, true)) {
            return Component.translatable("message.projectrebirth.console.not_yours");
        }
        java.util.List<BlockPos> points = pending.points();
        MachineTask.Type type = pending.taskType();
        MachineTask task;
        TabletData marks = data.withoutPending();
        switch (type) {
            case GUARD -> {
                task = MachineTask.at(MachineTask.Type.GUARD, points.get(0).above());
                marks = marks.withGuard(points.get(0).above());
            }
            case PATROL -> {
                task = MachineTask.patrol(points);
                marks = marks.withRoute(points);
            }
            case WORK -> {
                Component problem = areaProblem(pending.work(), points.get(0), points.get(1));
                if (problem != null) {
                    return problem;
                }
                task = MachineTask.work(MachineTask.WORKS.get(pending.work()), points.get(0), points.get(1), points.get(2));
                marks = marks.withArea(points.get(0), points.get(1), points.get(2));
            }
            default -> {
                return null;
            }
        }
        tablet.set(GarrisonRegistry.TABLET_DATA.get(), marks);
        if (!pending.machines().isEmpty()) {
            // 1.8.0 block F: the machines chosen in the world
            java.util.List<UUID> taken = new java.util.ArrayList<>();
            for (UUID machine : pending.machines()) {
                if (console.knows(machine) && console.problem(machine, type) == null) {
                    console.order(machine, task);
                    taken.add(machine);
                }
            }
            sendView(player, console);
            ack(player, type, taken, java.util.List.of(), null);
            return Component.translatable("message.projectrebirth.console.ordered_many", Component.translatable(type.translationKey()), taken.size());
        }
        return deliver(player, console, pending.machine(), pending.group(), type, task);
    }

    /** Why a work area is not allowed (too wide or too deep for its work), or null. */
    @javax.annotation.Nullable
    public static Component areaProblem(int work, BlockPos a, BlockPos b) {
        String kind = MachineTask.WORKS.get(Math.floorMod(work, MachineTask.WORKS.size()));
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
        return null;
    }

    /** Gives the order and tells the player how it went: given, or stored but not heard yet (no energy, the machine out of reach). */
    private static Component deliver(ServerPlayer player, ConsoleBlockEntity console, Optional<UUID> machine, int group, MachineTask.Type type,
                                     MachineTask task) {
        if (machine.isPresent()) {
            if (!console.knows(machine.get())) {
                return null;
            }
            console.order(machine.get(), task);
        } else if (console.orderGroup(group, task) == 0) {
            return Component.translatable("message.projectrebirth.console.group_empty");
        }
        sendView(player, console);
        Component name = Component.translatable(type.translationKey());
        if (!console.powered()) {
            return Component.translatable("message.projectrebirth.console.ordered_no_power", name);
        }
        if (machine.isPresent()) {
            if (console.alarmOn(machine.get(), ConsoleAlarm.NO_SUPPLY)) {
                return Component.translatable("message.projectrebirth.console.ordered_no_supply", name, ConsoleBlockEntity.display(console.nameOf(machine.get())));
            }
            long silent = console.silentSeconds(machine.get());
            if (silent < 0 || silent > 3) {
                return Component.translatable("message.projectrebirth.console.ordered_far", name, ConsoleBlockEntity.display(console.nameOf(machine.get())),
                        RebirthConfig.CONSOLE_RANGE.get());
            }
        }
        if (type == MachineTask.Type.PATROL && console.ordersMech(machine, group)) {
            // block H: a mech is wide; the order goes out, but the player hears of the narrow places
            BlockPos narrow = GuardMechEntity.narrowPlace(player.level(), task.route());
            if (narrow != null) {
                return Component.translatable("message.projectrebirth.console.route_narrow", narrow.getX(), narrow.getY(), narrow.getZ());
            }
        }
        return Component.translatable("message.projectrebirth.console.ordered", name);
    }
}
