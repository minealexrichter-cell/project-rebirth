package com.projectrebirth.monitoring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.ContaminationZones;
import com.projectrebirth.shelter.Shelters;
import com.projectrebirth.technology.AutomationHandler;
import com.projectrebirth.technology.TechSounds;
import com.projectrebirth.technology.energy.EnergyUtil;
import com.projectrebirth.technology.energy.ModEnergyStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The environmental monitoring station (1.6.0, block E). With energy it scans the contamination of the chunks around it every few
 * seconds, with the station, the shelters and the players marked; it keeps the peaks of the last game days for its graph, prints
 * the scan on paper as a contamination map, and a comparator reads the strongest contamination close to it. Numbers in the config
 * ([monitoring_station]).
 */
public class MonitoringStationBlockEntity extends BlockEntity implements MenuProvider {
    public static final int PAPER_SLOT = 0;
    public static final int MAP_SLOT = 1;
    public static final int SLOTS = 2;

    public static final int STATE_WORKING = 0;
    public static final int STATE_NO_ENERGY = 1;
    public static final int STATE_OFF = 2;

    public static final int DATA_ENERGY_LOW = 0;
    public static final int DATA_ENERGY_HIGH = 1;
    public static final int DATA_CAPACITY_LOW = 2;
    public static final int DATA_CAPACITY_HIGH = 3;
    public static final int DATA_STATE = 4;
    public static final int DATA_COUNT = 5;

    public static final int BUTTON_PRINT = 0;

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == PAPER_SLOT && stack.is(Items.PAPER);
        }
    };
    private final AutomationHandler automation = new AutomationHandler(items,
            (slot, stack) -> slot == PAPER_SLOT && stack.is(Items.PAPER), slot -> slot == MAP_SLOT);
    private final ModEnergyStorage energy = new ModEnergyStorage(() -> RebirthConfig.MONITORING_ENERGY_CAPACITY.get(),
            () -> Math.max(1, RebirthConfig.MONITORING_ENERGY_PER_TICK.get() * 4 + RebirthConfig.MONITORING_PRINT_ENERGY.get() / 20),
            () -> 0, this::setChanged);
    private int state = STATE_OFF;
    @Nullable
    private Snapshot snapshot;
    private final List<DayPeak> history = new ArrayList<>();
    private int comparator;
    private int seconds;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY_LOW -> EnergyUtil.low(energy.getEnergyStored());
                case DATA_ENERGY_HIGH -> EnergyUtil.high(energy.getEnergyStored());
                case DATA_CAPACITY_LOW -> EnergyUtil.low(energy.getMaxEnergyStored());
                case DATA_CAPACITY_HIGH -> EnergyUtil.high(energy.getMaxEnergyStored());
                case DATA_STATE -> state;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public MonitoringStationBlockEntity(BlockPos pos, BlockState state) {
        super(MonitoringRegistry.MONITORING_STATION_ENTITY.get(), pos, state);
    }

    public ItemStackHandler items() {
        return items;
    }

    public AutomationHandler automation() {
        return automation;
    }

    public ModEnergyStorage energy() {
        return energy;
    }

    public int state() {
        return state;
    }

    @Nullable
    public Snapshot snapshot() {
        return snapshot;
    }

    public List<DayPeak> history() {
        return Collections.unmodifiableList(history);
    }

    public int comparator() {
        return comparator;
    }

    void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (state == STATE_WORKING && !energy.consume(RebirthConfig.MONITORING_ENERGY_PER_TICK.get())) {
            state = STATE_NO_ENERGY;
        }
        if ((serverLevel.getGameTime() + worldPosition.hashCode()) % 20 != 0) {
            return;
        }
        seconds++;
        boolean on = RebirthConfig.MONITORING_ENABLED.get() && ContaminationZones.enabled();
        state = !on ? STATE_OFF : energy.getEnergyStored() < RebirthConfig.MONITORING_ENERGY_PER_TICK.get() ? STATE_NO_ENERGY : STATE_WORKING;
        TechSounds.setWorking(this, state == STATE_WORKING);
        if (state == STATE_WORKING && (snapshot == null || seconds % RebirthConfig.MONITORING_SCAN_SECONDS.get() == 0)) {
            scan(serverLevel);
        }
    }

    /** Reads the chunks around, remembers today's peaks, sets the comparator and tells the open windows. Public for tests. */
    public void scan(ServerLevel serverLevel) {
        int radius = RebirthConfig.MONITORING_RADIUS.get();
        int cx = worldPosition.getX() >> 4, cz = worldPosition.getZ() >> 4;
        snapshot = Snapshot.scan(serverLevel, cx, cz, radius, markers(serverLevel, cx, cz, radius));
        float[] peaks = snapshot.peaks();
        long day = snapshot.day();
        if (!history.isEmpty() && history.get(history.size() - 1).day() == day) {
            DayPeak today = history.get(history.size() - 1);
            history.set(history.size() - 1, new DayPeak(day, Math.max(today.radiation(), peaks[0]), Math.max(today.toxin(), peaks[1])));
        } else {
            history.add(new DayPeak(day, peaks[0], peaks[1]));
        }
        while (history.size() > RebirthConfig.MONITORING_HISTORY_DAYS.get()) {
            history.remove(0);
        }
        float near = snapshot.strongestWithin(RebirthConfig.MONITORING_COMPARATOR_CHUNKS.get());
        int newComparator = near <= 0.0f ? 0 : Mth.clamp(Mth.ceil(15.0f * near / RebirthConfig.MONITORING_COMPARATOR_FULL.get().floatValue()), 1, 15);
        if (newComparator != comparator) {
            comparator = newComparator;
            serverLevel.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
        setChanged();
        for (ServerPlayer player : serverLevel.players()) {
            if (player.containerMenu instanceof MonitoringStationMenu menu && menu.pos().equals(worldPosition)) {
                sendTo(player);
            }
        }
    }

    private List<Snapshot.Marker> markers(ServerLevel serverLevel, int cx, int cz, int radius) {
        List<Snapshot.Marker> out = new ArrayList<>();
        out.add(new Snapshot.Marker(Snapshot.STATION, worldPosition.getX(), worldPosition.getZ(), ""));
        Shelters shelters = Shelters.find(serverLevel);
        if (shelters != null) {
            for (long unit : shelters.unitPositions()) {
                BlockPos at = BlockPos.of(unit);
                if (Math.abs((at.getX() >> 4) - cx) <= radius && Math.abs((at.getZ() >> 4) - cz) <= radius) {
                    out.add(new Snapshot.Marker(Snapshot.SHELTER, at.getX(), at.getZ(), ""));
                }
            }
        }
        out.addAll(Snapshot.mindNodes(serverLevel, cx, cz, radius));
        for (ServerPlayer player : serverLevel.players()) {
            if (!player.isSpectator() && Math.abs(player.chunkPosition().x - cx) <= radius && Math.abs(player.chunkPosition().z - cz) <= radius) {
                out.add(new Snapshot.Marker(Snapshot.PLAYER, player.getBlockX(), player.getBlockZ(), player.getGameProfile().getName()));
            }
        }
        return out;
    }

    void sendTo(ServerPlayer player) {
        if (snapshot != null && player.connection.hasChannel(MonitoringPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new MonitoringPayload(worldPosition, snapshot, List.copyOf(history)));
        }
    }

    /** Prints the latest scan on a sheet of paper. Public for tests. */
    public boolean print() {
        ItemStack paper = items.getStackInSlot(PAPER_SLOT);
        int cost = RebirthConfig.MONITORING_PRINT_ENERGY.get();
        if (snapshot == null || state != STATE_WORKING || !paper.is(Items.PAPER) || !items.getStackInSlot(MAP_SLOT).isEmpty()
                || energy.getEnergyStored() < cost || !energy.consume(cost)) {
            return false;
        }
        items.setStackInSlot(PAPER_SLOT, paper.copyWithCount(paper.getCount() - 1));
        ItemStack map = new ItemStack(MonitoringRegistry.CONTAMINATION_MAP.get());
        map.set(MonitoringRegistry.SNAPSHOT.get(), snapshot.withoutPlayers());
        items.setStackInSlot(MAP_SLOT, map);
        if (level != null) {
            level.playSound(null, worldPosition, com.projectrebirth.core.ModSounds.MAP_PRINT.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.0f);
        }
        return true;
    }

    void dropContents() {
        for (int slot = 0; slot < SLOTS; slot++) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), items.getStackInSlot(slot));
            items.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", items.serializeNBT(registries));
        tag.putInt("energy", energy.saved());
        ListTag days = new ListTag();
        history.forEach(peak -> days.add(peak.save()));
        tag.put("history", days);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.deserializeNBT(registries, tag.getCompound("items"));
        energy.load(tag.getInt("energy"));
        history.clear();
        ListTag days = tag.getList("history", Tag.TAG_COMPOUND);
        for (int i = 0; i < days.size(); i++) {
            history.add(DayPeak.load(days.getCompound(i)));
        }
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            sendTo(serverPlayer);
        }
        return new MonitoringStationMenu(containerId, inventory, worldPosition, items, data, this);
    }
}
