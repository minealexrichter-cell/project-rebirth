package com.projectrebirth.garrison;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.monitoring.MonitoringStationBlockEntity;
import com.projectrebirth.monitoring.Snapshot;
import com.projectrebirth.shelter.ShelterAlerts;
import com.projectrebirth.technology.TechSounds;
import com.projectrebirth.technology.energy.ModEnergyStorage;
import com.projectrebirth.weather.Forecast;
import com.projectrebirth.weather.WeatherStationBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * The console (1.7.0, block D): the command post of a player's machines. It runs on energy; it knows the machines linked to it
 * (by setting them up near it or with the tactical tablet), keeps their order and their group (up to eight groups), the switches
 * of the intruder mode (for the console and for each group; the server may forbid it) and of the weather link (take cover before a
 * catastrophe the nearby weather station foresees), and an alarm log. Machines report to it once a second when their chunk is
 * loaded and they are within range in the same dimension; it shows the last known state of the others. Each alarm is told to the
 * owner and the members of the faction once when it starts and once when it ends, as the shelter panel does. The redstone signal
 * is the strength of the worst alarm going on, a comparator reads the share of the machines in working order.
 */
public class ConsoleBlockEntity extends BlockEntity {
    public static final int GROUPS = 8;
    /** 1.7.1: the "group" of an order to every machine of the console. */
    public static final int ALL = -2;
    private static final int LOG_SIZE = 30;

    /** One linked machine, as last heard. */
    static final class Link {
        int group = -1;
        MachineTask task = MachineTask.STAND_DOWN;
        String name = "";
        MachineKind kind = MachineKind.TEST;
        MachineCore.State state = MachineCore.State.ACTIVE;
        float structural, maxStructural = 1, critical, maxCritical = 1;
        BlockPos pos = BlockPos.ZERO;
        long heard = -1;
        final Set<ConsoleAlarm> active = EnumSet.noneOf(ConsoleAlarm.class);
        /** 1.8.0, not saved (heard again within a second of loading): energy share (-1: none), rounds (-1: none), a work android. */
        float energyShare = -1.0f;
        /** A name given in the console's window, waiting for the machine to take it (not saved: the name is saved on the link). */
        @Nullable
        String renamed;
        int ammo = -1;
        int range;
        boolean worker;
    }

    /** One line of the log. */
    record Logged(ConsoleAlarm alarm, boolean ended, String name, BlockPos pos, long gameTime) {
    }

    private final ModEnergyStorage energy = new ModEnergyStorage(() -> RebirthConfig.CONSOLE_ENERGY_CAPACITY.get(),
            () -> Math.max(1, RebirthConfig.CONSOLE_ENERGY_PER_TICK.get() * 50), () -> 0, this::setChanged);
    @Nullable
    private UUID owner;
    private final Map<UUID, Link> machines = new LinkedHashMap<>();
    private final boolean[] groupIntruders = new boolean[GROUPS];
    /** 1.8.0: the target rules (OrderRules bits) of each group, and of the machines without a group. */
    private final int[] groupRules = new int[GROUPS];
    private int rules = OrderRules.DEFAULT_RULES;
    private boolean intruderMode;
    private boolean weatherLink;
    private boolean sheltering;
    private final Map<UUID, MachineTask> beforeShelter = new HashMap<>();
    private final Deque<Logged> log = new ArrayDeque<>();
    private boolean powered;
    private int signal;
    private int comparator;

    public ConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(GarrisonRegistry.CONSOLE_ENTITY.get(), pos, state);
        java.util.Arrays.fill(groupRules, OrderRules.DEFAULT_RULES);
    }

    public ModEnergyStorage energy() {
        return energy;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        setChanged();
    }

    public GlobalPos globalPos() {
        return GlobalPos.of(level.dimension(), worldPosition);
    }

    /** Whether this player may command this console: its owner, a manager of the owner's faction, a player in creative mode. */
    public boolean mayCommand(Player player) {
        return MachineCore.mayCommand(owner, player);
    }

    public boolean powered() {
        return powered;
    }

    public int signal() {
        return signal;
    }

    public int comparator() {
        return comparator;
    }

    // ------------------------------------------------------------------ machines

    public boolean knows(UUID machine) {
        return machines.containsKey(machine);
    }

    public int count() {
        return machines.size();
    }

    /** Links a machine (server); false when the console is full. */
    public boolean link(Entity machine, MachineCore core) {
        if (!machines.containsKey(machine.getUUID()) && machines.size() >= RebirthConfig.CONSOLE_MAX_MACHINES.get()) {
            return false;
        }
        Link link = machines.computeIfAbsent(machine.getUUID(), id -> new Link());
        core.setConsole(globalPos());
        link.task = core.task();
        heard(machine, core, link);
        setChanged();
        return true;
    }

    public void unlink(UUID machine) {
        if (machines.remove(machine) != null) {
            beforeShelter.remove(machine);
            setChanged();
            refreshSignal();
        }
    }

    /** How many machines an order to this group (or {@link #ALL}) reaches. */
    public int members(int group) {
        int count = 0;
        for (Link link : machines.values()) {
            if (group == ALL || link.group == group) {
                count++;
            }
        }
        return count;
    }

    /**
     * 1.7.1: the name of a machine without a name of its own is kept as its translation key ("@" and the key), so that each
     * player reads it in their own language; {@link #display} turns it back into words.
     */
    static String nameKey(Entity machine) {
        if (machine.getName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translatable
                && translatable.getArgs().length == 0) {
            return "@" + translatable.getKey();
        }
        return machine.getName().getString();
    }

    /** A machine's name as kept by the console, as words. */
    public static Component display(String name) {
        return name.startsWith("@") ? Component.translatable(name.substring(1)) : Component.literal(name);
    }

    /** The name the console knows a machine by, or "". */
    public String nameOf(UUID machine) {
        Link link = machines.get(machine);
        return link == null ? "" : link.name;
    }

    /** Seconds since the machine last reported to the console, or -1 if never. */
    public long silentSeconds(UUID machine) {
        Link link = machines.get(machine);
        return link == null || link.heard < 0 || level == null ? -1 : (level.getGameTime() - link.heard) / 20;
    }

    public MachineTask taskFor(UUID machine) {
        Link link = machines.get(machine);
        return link == null ? MachineTask.STAND_DOWN : link.task;
    }

    public int groupOf(UUID machine) {
        Link link = machines.get(machine);
        return link == null ? -1 : link.group;
    }

    /** The intruder mode a machine of this console follows: the console's switch or its group's, if the server allows it. */
    public boolean intruderModeFor(UUID machine) {
        if (!RebirthConfig.ALLOW_INTRUDER_MODE.get()) {
            return false;
        }
        int group = groupOf(machine);
        return intruderMode || group >= 0 && groupIntruders[group];
    }

    /** 1.8.0: the target rules a machine of this console follows: its group's, or the console's for a machine without a group. */
    public int rulesFor(UUID machine) {
        int group = groupOf(machine);
        return group >= 0 ? groupRules[group] : rules;
    }

    /** 1.8.0: the target rules of a group, or ({@link #ALL} or -1) of the machines without a group. */
    public int rules(int group) {
        return group >= 0 && group < GROUPS ? groupRules[group] : rules;
    }

    public void setRules(int group, int value) {
        if (group >= 0 && group < GROUPS) {
            groupRules[group] = value & OrderRules.DEFAULT_RULES;
        } else {
            rules = value & OrderRules.DEFAULT_RULES;
        }
        setChanged();
    }

    /** 1.8.0: why this machine cannot carry out the order (a translation key), or null; null too for a machine it does not know. */
    @Nullable
    public String problem(UUID machine, MachineTask.Type type) {
        Link link = machines.get(machine);
        return link == null ? null : OrderRules.problem(link.kind, link.worker, type);
    }

    /** 1.8.0: renames a machine as the console knows it; the machine takes the name when next heard. */
    public void rename(UUID machine, String name) {
        Link link = machines.get(machine);
        if (link != null) {
            link.name = name;
            link.renamed = name;
            setChanged();
        }
    }

    /** Gives one machine an order. */
    public void order(UUID machine, MachineTask task) {
        Link link = machines.get(machine);
        if (link != null) {
            link.task = task;
            beforeShelter.remove(machine);
            setChanged();
        }
    }

    /** Gives every machine of a group (or {@link #ALL}) the same order; returns how many got it. */
    public int orderGroup(int group, MachineTask task) {
        int count = 0;
        for (Map.Entry<UUID, Link> entry : machines.entrySet()) {
            if (group == ALL || entry.getValue().group == group) {
                entry.getValue().task = task;
                beforeShelter.remove(entry.getKey());
                count++;
            }
        }
        setChanged();
        return count;
    }

    /**
     * 1.7.2, block D: the order "area patrol" for one machine, a group or {@link #ALL}: the walking machines that get it (turrets
     * keep their order) spread out round their middle (by the places last heard of them), each in its own sector. Returns the
     * centre, or null when no walking machine gets it.
     */
    @Nullable
    public BlockPos orderArea(java.util.Optional<UUID> machine, int group) {
        java.util.List<Map.Entry<UUID, Link>> walkers = new java.util.ArrayList<>();
        for (Map.Entry<UUID, Link> entry : machines.entrySet()) {
            boolean gets = machine.isPresent() ? entry.getKey().equals(machine.get()) : group == ALL || entry.getValue().group == group;
            if (gets && entry.getValue().kind != MachineKind.TURRET) {
                walkers.add(entry);
            }
        }
        if (walkers.isEmpty()) {
            return null;
        }
        long x = 0, y = 0, z = 0;
        for (Map.Entry<UUID, Link> entry : walkers) {
            x += entry.getValue().pos.getX();
            y += entry.getValue().pos.getY();
            z += entry.getValue().pos.getZ();
        }
        int n = walkers.size();
        BlockPos centre = new BlockPos((int) Math.floorDiv(x, n), (int) Math.floorDiv(y, n), (int) Math.floorDiv(z, n));
        for (int i = 0; i < n; i++) {
            walkers.get(i).getValue().task = MachineTask.area(centre, i, n);
            beforeShelter.remove(walkers.get(i).getKey());
        }
        setChanged();
        return centre;
    }

    /** Whether a mech gets this order: the machine, or one of the group (block H: its patrol route is checked). */
    public boolean ordersMech(java.util.Optional<UUID> machine, int group) {
        for (Map.Entry<UUID, Link> entry : machines.entrySet()) {
            boolean gets = machine.isPresent() ? entry.getKey().equals(machine.get()) : group == ALL || entry.getValue().group == group;
            if (gets && entry.getValue().kind == MachineKind.MECH) {
                return true;
            }
        }
        return false;
    }

    public void setGroup(UUID machine, int group) {
        Link link = machines.get(machine);
        if (link != null) {
            link.group = group >= 0 && group < GROUPS ? group : -1;
            setChanged();
        }
    }

    public void setIntruderMode(boolean on) {
        intruderMode = on;
        setChanged();
    }

    public boolean intruderMode() {
        return intruderMode;
    }

    public void setGroupIntruders(int group, boolean on) {
        if (group >= 0 && group < GROUPS) {
            groupIntruders[group] = on;
            setChanged();
        }
    }

    public boolean groupIntruders(int group) {
        return group >= 0 && group < GROUPS && groupIntruders[group];
    }

    public void setWeatherLink(boolean on) {
        weatherLink = on;
        if (!on) {
            endShelter();
        }
        setChanged();
    }

    public boolean weatherLink() {
        return weatherLink;
    }

    public boolean sheltering() {
        return sheltering;
    }

    /** A machine reports (server, once a second while it is loaded and in range): its state, and what it should do now. */
    void heard(Entity machine, MachineCore core, Link link) {
        if (link.renamed != null) {
            core.setName(link.renamed);
            link.renamed = null;
        }
        link.name = core.name().isEmpty() ? nameKey(machine) : core.name();
        if (machine instanceof GarrisonMachine garrison) {
            link.energyShare = garrison.consoleEnergy();
            link.ammo = garrison.consoleAmmo();
            link.worker = garrison.consoleWorker();
            link.range = garrison.consoleRange();
        }
        link.kind = core.kind();
        link.state = core.state();
        link.structural = core.structural();
        link.maxStructural = core.maxStructural();
        link.critical = core.critical();
        link.maxCritical = core.maxCritical();
        link.pos = machine.blockPosition();
        link.heard = level == null ? 0 : level.getGameTime();
    }

    @Nullable
    Link link(UUID machine) {
        return machines.get(machine);
    }

    // ------------------------------------------------------------------ alarms

    /** An alarm about a machine starts (on) or ends (off); told once each way, logged, and the signal follows. */
    public void alarm(UUID machine, ConsoleAlarm alarm, boolean on) {
        Link link = machines.get(machine);
        if (link == null || level == null || link.active.contains(alarm) == on) {
            return;
        }
        if (on) {
            link.active.add(alarm);
        } else {
            link.active.remove(alarm);
        }
        log.addFirst(new Logged(alarm, !on, link.name, link.pos, level.getGameTime()));
        while (log.size() > LOG_SIZE) {
            log.removeLast();
        }
        if (level instanceof ServerLevel server && alarm.told()) {
            Component message = Component.translatable("message.projectrebirth.console_alarm." + alarm.id() + (on ? "" : "_ended"),
                    display(link.name), link.pos.getX(), link.pos.getY(), link.pos.getZ()).withStyle(on ? ChatFormatting.RED : ChatFormatting.GREEN);
            for (ServerPlayer player : ShelterAlerts.recipients(server, owner)) {
                player.sendSystemMessage(message);
                if (on) {
                    player.playNotifySound(com.projectrebirth.core.ModSounds.GARRISON_CONSOLE_ALARM.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.0f);
                }
            }
            if (on) {
                server.playSound(null, worldPosition, com.projectrebirth.core.ModSounds.GARRISON_CONSOLE_ALARM.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
            }
        }
        setChanged();
        refreshSignal();
    }

    public boolean alarmOn(UUID machine, ConsoleAlarm alarm) {
        Link link = machines.get(machine);
        return link != null && link.active.contains(alarm);
    }

    /** The worst alarm going on as a signal strength, and the share of the machines in working order for a comparator. */
    public void refreshSignal() {
        int worst = 0;
        int working = 0;
        for (Link link : machines.values()) {
            for (ConsoleAlarm alarm : link.active) {
                worst = Math.max(worst, alarm.signal());
            }
            if (link.state == MachineCore.State.ACTIVE) {
                working++;
            }
        }
        int share = working == 0 ? 0 : Math.max(1, Math.round(15.0f * working / machines.size()));
        if (worst != signal || share != comparator) {
            signal = worst;
            comparator = share;
            if (level != null) {
                level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
                level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
            }
        }
    }

    // ------------------------------------------------------------------ time

    /** Public for tests. */
    public void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        boolean on = RebirthConfig.GARRISON_ENABLED.get() && RebirthConfig.CONSOLE_ENABLED.get();
        int use = RebirthConfig.CONSOLE_ENERGY_PER_TICK.get();
        powered = on && (use <= 0 || energy.consume(use));
        if ((server.getGameTime() + worldPosition.hashCode()) % 20 != 0) {
            return;
        }
        TechSounds.setWorking(this, powered);
        if (powered && weatherLink) {
            weather(server);
        }
        refreshSignal();
        // block I: the HUD mark of the worst alarm told in chat, for its owner and faction while it lasts
        ConsoleAlarm worst = null;
        for (Link link : machines.values()) {
            for (ConsoleAlarm alarm : link.active) {
                if (alarm.told() && (worst == null || alarm.signal() > worst.signal())) {
                    worst = alarm;
                }
            }
        }
        if (worst != null) {
            ConsoleNetwork.AlarmPayload payload = new ConsoleNetwork.AlarmPayload(worldPosition, worst.ordinal());
            for (ServerPlayer player : ShelterAlerts.recipients(server, owner)) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
            }
        }
        for (ServerPlayer player : server.players()) {
            if (player.containerMenu instanceof ConsoleMenu menu && menu.pos().equals(worldPosition)) {
                ConsoleNetwork.sendView(player, this);
            }
        }
    }

    /** The weather link: before a catastrophe a weather station near foresees (or while one goes on) every machine takes cover. */
    private void weather(ServerLevel server) {
        boolean coming = false;
        int radius = RebirthConfig.CONSOLE_WEATHER_RADIUS.get();
        for (BlockEntity found : around(server, radius)) {
            if (found instanceof WeatherStationBlockEntity station) {
                Forecast forecast = station.forecast();
                if (forecast != null && forecast.secondsUntil() <= RebirthConfig.CONSOLE_WEATHER_SECONDS.get()) {
                    coming = true;
                }
            }
        }
        String event = com.projectrebirth.environment.EnvironmentState.get(server).event();
        coming |= event != null && !event.isEmpty();
        if (coming && !sheltering) {
            sheltering = true;
            for (Map.Entry<UUID, Link> entry : machines.entrySet()) {
                beforeShelter.put(entry.getKey(), entry.getValue().task);
                entry.getValue().task = MachineTask.simple(MachineTask.Type.SHELTER);
            }
            setChanged();
        } else if (!coming && sheltering) {
            endShelter();
        }
    }

    private void endShelter() {
        if (!sheltering) {
            return;
        }
        sheltering = false;
        for (Map.Entry<UUID, MachineTask> entry : beforeShelter.entrySet()) {
            Link link = machines.get(entry.getKey());
            if (link != null && link.task.type() == MachineTask.Type.SHELTER) {
                link.task = entry.getValue();
            }
        }
        beforeShelter.clear();
        setChanged();
    }

    /** The block entities of the loaded chunks within that many blocks (a few chunks, looked at now and then). */
    List<BlockEntity> around(ServerLevel server, int radius) {
        List<BlockEntity> out = new ArrayList<>();
        int chunks = (radius >> 4) + 1;
        int cx = worldPosition.getX() >> 4, cz = worldPosition.getZ() >> 4;
        for (int dx = -chunks; dx <= chunks; dx++) {
            for (int dz = -chunks; dz <= chunks; dz++) {
                LevelChunk chunk = server.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity found : chunk.getBlockEntities().values()) {
                    if (found.getBlockPos().distSqr(worldPosition) <= (double) radius * radius) {
                        out.add(found);
                    }
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ the window

    /** 1.8.0: the service posts and charging pads loaded within the console's range, for its map. */
    List<ConsoleView.Site> sites(ServerLevel server) {
        List<ConsoleView.Site> out = new ArrayList<>();
        int range = RebirthConfig.CONSOLE_RANGE.get();
        for (LoadedSites.Kind kind : LoadedSites.Kind.values()) {
            for (BlockPos pos : LoadedSites.within(server, kind, worldPosition, range)) {
                out.add(new ConsoleView.Site(kind.ordinal(), pos.getX(), pos.getY(), pos.getZ()));
                if (out.size() >= 64) {
                    return out;
                }
            }
        }
        return out;
    }

    ConsoleView view(ServerLevel server) {
        long now = server.getGameTime();
        List<ConsoleView.Row> rows = new ArrayList<>();
        int[] members = new int[GROUPS];
        for (Map.Entry<UUID, Link> entry : machines.entrySet()) {
            Link link = entry.getValue();
            if (link.group >= 0) {
                members[link.group]++;
            }
            rows.add(new ConsoleView.Row(entry.getKey(), link.name, link.kind.id(), link.group, link.state.ordinal(), link.structural,
                    link.maxStructural, link.critical, link.maxCritical, link.pos.getX(), link.pos.getY(), link.pos.getZ(),
                    link.task.type().ordinal(), link.heard < 0 ? -1 : (int) Math.max(0, (now - link.heard) / 20), new ConsoleView.Facts(link.energyShare, link.ammo, link.range, link.worker,
                    ConsoleView.taskPoints(link.task), link.active.stream().map(Enum::ordinal).toList())));
        }
        List<ConsoleView.Group> groups = new ArrayList<>();
        for (int i = 0; i < GROUPS; i++) {
            groups.add(new ConsoleView.Group(members[i], groupIntruders[i], groupRules[i]));
        }
        List<ConsoleView.Line> lines = new ArrayList<>();
        for (Logged logged : log) {
            lines.add(new ConsoleView.Line(logged.alarm().ordinal(), logged.ended(), logged.name(), logged.pos().getX(), logged.pos().getY(),
                    logged.pos().getZ(), (int) Math.max(0, (now - logged.gameTime()) / 20)));
        }
        Optional<Snapshot> map = Optional.empty();
        int radius = RebirthConfig.CONSOLE_MAP_STATION_RADIUS.get();
        if (radius > 0) {
            for (BlockEntity found : around(server, radius)) {
                if (found instanceof MonitoringStationBlockEntity station && station.snapshot() != null) {
                    map = Optional.of(station.snapshot());
                    break;
                }
            }
        }
        return new ConsoleView(energy.getEnergyStored(), energy.getMaxEnergyStored(), powered, RebirthConfig.ALLOW_INTRUDER_MODE.get(),
                intruderMode, weatherLink, sheltering, rows, groups, lines, map, RebirthConfig.CONSOLE_RANGE.get(), rules, sites(server));
    }

    // ------------------------------------------------------------------ saving

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("energy", energy.saved());
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Link> entry : machines.entrySet()) {
            Link link = entry.getValue();
            CompoundTag one = new CompoundTag();
            one.putUUID("id", entry.getKey());
            one.putInt("group", link.group);
            MachineTask.CODEC.encodeStart(NbtOps.INSTANCE, link.task).result().ifPresent(found -> one.put("task", found));
            MachineTask before = beforeShelter.get(entry.getKey());
            if (before != null) {
                MachineTask.CODEC.encodeStart(NbtOps.INSTANCE, before).result().ifPresent(found -> one.put("before_shelter", found));
            }
            one.putString("name", link.name);
            one.putString("kind", link.kind.id());
            one.putInt("state", link.state.ordinal());
            one.putFloat("s", link.structural);
            one.putFloat("ms", link.maxStructural);
            one.putFloat("c", link.critical);
            one.putFloat("mc", link.maxCritical);
            one.putLong("pos", link.pos.asLong());
            one.putLong("heard", link.heard);
            int alarms = 0;
            for (ConsoleAlarm alarm : link.active) {
                alarms |= 1 << alarm.ordinal();
            }
            one.putInt("alarms", alarms);
            list.add(one);
        }
        tag.put("machines", list);
        byte groups = 0;
        for (int i = 0; i < GROUPS; i++) {
            if (groupIntruders[i]) {
                groups |= (byte) (1 << i);
            }
        }
        tag.putByte("group_intruders", groups);
        tag.putInt("rules", rules);
        tag.putIntArray("group_rules", groupRules);
        tag.putBoolean("intruders", intruderMode);
        tag.putBoolean("weather", weatherLink);
        tag.putBoolean("sheltering", sheltering);
        ListTag lines = new ListTag();
        for (Logged logged : log) {
            CompoundTag line = new CompoundTag();
            line.putInt("alarm", logged.alarm().ordinal());
            line.putBoolean("ended", logged.ended());
            line.putString("name", logged.name());
            line.putLong("pos", logged.pos().asLong());
            line.putLong("time", logged.gameTime());
            lines.add(line);
        }
        tag.put("log", lines);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.load(tag.getInt("energy"));
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        machines.clear();
        beforeShelter.clear();
        for (Tag found : tag.getList("machines", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) found;
            Link link = new Link();
            link.group = one.getInt("group");
            link.task = MachineTask.CODEC.parse(NbtOps.INSTANCE, one.get("task")).result().orElse(MachineTask.STAND_DOWN);
            if (one.contains("before_shelter")) {
                MachineTask.CODEC.parse(NbtOps.INSTANCE, one.get("before_shelter")).result()
                        .ifPresent(before -> beforeShelter.put(one.getUUID("id"), before));
            }
            link.name = one.getString("name");
            link.kind = MachineKind.byId(one.getString("kind"));
            link.state = MachineCore.State.byId(one.getInt("state"));
            link.structural = one.getFloat("s");
            link.maxStructural = one.getFloat("ms");
            link.critical = one.getFloat("c");
            link.maxCritical = one.getFloat("mc");
            link.pos = BlockPos.of(one.getLong("pos"));
            link.heard = one.getLong("heard");
            int alarms = one.getInt("alarms");
            for (ConsoleAlarm alarm : ConsoleAlarm.values()) {
                if ((alarms & (1 << alarm.ordinal())) != 0) {
                    link.active.add(alarm);
                }
            }
            machines.put(one.getUUID("id"), link);
        }
        byte groups = tag.getByte("group_intruders");
        for (int i = 0; i < GROUPS; i++) {
            groupIntruders[i] = (groups & (1 << i)) != 0;
        }
        rules = tag.contains("rules") ? tag.getInt("rules") & OrderRules.DEFAULT_RULES : OrderRules.DEFAULT_RULES;
        int[] savedRules = tag.getIntArray("group_rules");
        for (int i = 0; i < GROUPS; i++) {
            groupRules[i] = i < savedRules.length ? savedRules[i] & OrderRules.DEFAULT_RULES : OrderRules.DEFAULT_RULES;
        }
        intruderMode = tag.getBoolean("intruders");
        weatherLink = tag.getBoolean("weather");
        sheltering = tag.getBoolean("sheltering");
        log.clear();
        for (Tag found : tag.getList("log", Tag.TAG_COMPOUND)) {
            CompoundTag line = (CompoundTag) found;
            log.addLast(new Logged(ConsoleAlarm.byId(line.getInt("alarm")), line.getBoolean("ended"), line.getString("name"),
                    BlockPos.of(line.getLong("pos")), line.getLong("time")));
        }
    }
}
