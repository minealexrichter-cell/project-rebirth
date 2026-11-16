package com.projectrebirth.weather;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.Environment;
import com.projectrebirth.environment.EnvironmentState;
import com.projectrebirth.environment.EventPhase;
import com.projectrebirth.factions.Faction;
import com.projectrebirth.factions.FactionData;
import com.projectrebirth.technology.TechSounds;
import com.projectrebirth.technology.energy.EnergyUtil;
import com.projectrebirth.technology.energy.ModEnergyStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The weather station (1.6.0, block D). With energy and the open sky above it, it foresees the next catastrophe of its dimension:
 * the kind and a rough time some minutes ahead, or, with the long range forecast module, the exact time, intensity and length
 * further ahead. Its owner, the owner's faction and the players around it get the forecast in their HUD; it gives a redstone
 * signal before and during a catastrophe, and a comparator reads how close one is. Numbers in the config ([weather_station]).
 */
public class WeatherStationBlockEntity extends BlockEntity implements MenuProvider {
    public static final int MODULE_SLOT = 0;
    public static final int SLOTS = 1;

    public static final int STATE_CLEAR = 0;
    public static final int STATE_FORECAST = 1;
    public static final int STATE_ACTIVE = 2;
    public static final int STATE_NO_SKY = 3;
    public static final int STATE_NO_ENERGY = 4;
    public static final int STATE_NONE_HERE = 5;

    /** How often the forecast goes out to the HUDs, in seconds; the clients forget it after a few such periods. */
    public static final int SEND_SECONDS = 5;

    public static final int DATA_ENERGY_LOW = 0;
    public static final int DATA_ENERGY_HIGH = 1;
    public static final int DATA_CAPACITY_LOW = 2;
    public static final int DATA_CAPACITY_HIGH = 3;
    public static final int DATA_STATE = 4;
    public static final int DATA_EVENT = 5;
    public static final int DATA_UNTIL = 6;
    public static final int DATA_ROUGH_LOW = 7;
    public static final int DATA_ROUGH_HIGH = 8;
    public static final int DATA_INTENSITY = 9;
    public static final int DATA_LENGTH = 10;
    public static final int DATA_NOW_PHASE = 11;
    public static final int DATA_NOW_EVENT = 12;
    public static final int DATA_NOW_LEFT = 13;
    public static final int DATA_HISTORY = 14;
    public static final int DATA_HORIZON = 15;
    /** Then for each past catastrophe: its kind, intensity in percent, length in minutes, game day. */
    public static final int DATA_PAST = 16;
    public static final int DATA_COUNT = DATA_PAST + 4 * EnvironmentState.HISTORY;

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(WeatherRegistry.LONG_RANGE_MODULE.get());
        }
    };
    private final ModEnergyStorage energy = new ModEnergyStorage(() -> RebirthConfig.WEATHER_STATION_ENERGY_CAPACITY.get(),
            () -> Math.max(1, (RebirthConfig.WEATHER_STATION_ENERGY_PER_TICK.get() + RebirthConfig.WEATHER_STATION_MODULE_ENERGY_PER_TICK.get()) * 4),
            () -> 0, this::setChanged);
    @Nullable
    private UUID owner;
    private int state = STATE_NONE_HERE;
    @Nullable
    private Forecast forecast;
    private int signal;
    private int comparator;
    private int seconds;
    private final int[] shown = new int[DATA_COUNT];

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY_LOW -> EnergyUtil.low(energy.getEnergyStored());
                case DATA_ENERGY_HIGH -> EnergyUtil.high(energy.getEnergyStored());
                case DATA_CAPACITY_LOW -> EnergyUtil.low(energy.getMaxEnergyStored());
                case DATA_CAPACITY_HIGH -> EnergyUtil.high(energy.getMaxEnergyStored());
                default -> index >= 0 && index < DATA_COUNT ? shown[index] : 0;
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

    public WeatherStationBlockEntity(BlockPos pos, BlockState state) {
        super(WeatherRegistry.WEATHER_STATION_ENTITY.get(), pos, state);
    }

    public ItemStackHandler items() {
        return items;
    }

    public ModEnergyStorage energy() {
        return energy;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        setChanged();
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public boolean longRange() {
        return items.getStackInSlot(MODULE_SLOT).is(WeatherRegistry.LONG_RANGE_MODULE.get());
    }

    public int state() {
        return state;
    }

    @Nullable
    public Forecast forecast() {
        return forecast;
    }

    public int signal() {
        return signal;
    }

    public int comparator() {
        return comparator;
    }

    void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        boolean working = state == STATE_CLEAR || state == STATE_FORECAST || state == STATE_ACTIVE;
        if (working && !energy.consume(energyPerTick())) {
            working = false;
            state = STATE_NO_ENERGY;
        }
        if ((serverLevel.getGameTime() + worldPosition.hashCode()) % 20 != 0) {
            return;
        }
        update(serverLevel);
        TechSounds.setWorking(this, state == STATE_CLEAR || state == STATE_FORECAST || state == STATE_ACTIVE);
        seconds++;
        if (forecast != null && seconds % SEND_SECONDS == 0) {
            send(serverLevel, ForecastPayload.of(forecast));
        }
    }

    private int energyPerTick() {
        return RebirthConfig.WEATHER_STATION_ENERGY_PER_TICK.get() + (longRange() ? RebirthConfig.WEATHER_STATION_MODULE_ENERGY_PER_TICK.get() : 0);
    }

    /** Looks at the sky once a second: what it sees, the signals, what the window shows. Public for tests. */
    public void update(ServerLevel serverLevel) {
        EnvironmentState environment = EnvironmentState.get(serverLevel);
        forecast = null;
        if (!RebirthConfig.WEATHER_STATION_ENABLED.get() || !Environment.isEnabled()
                || !Environment.hasCatastrophes(serverLevel.registryAccess(), serverLevel.dimension())) {
            state = STATE_NONE_HERE;
        } else if (!openSky(serverLevel)) {
            state = STATE_NO_SKY;
        } else if (energy.getEnergyStored() < energyPerTick()) {
            state = STATE_NO_ENERGY;
        } else if (environment.phase() == EventPhase.ACTIVE || environment.phase() == EventPhase.AFTERMATH) {
            state = STATE_ACTIVE;
        } else {
            forecast = Forecast.of(environment, longRange());
            state = forecast == null ? STATE_CLEAR : STATE_FORECAST;
        }
        int newSignal = 0, newComparator = 0;
        if (state == STATE_ACTIVE) {
            newSignal = environment.phase() == EventPhase.ACTIVE ? 15 : 0;
            newComparator = environment.phase() == EventPhase.ACTIVE ? 15 : 0;
        } else if (forecast != null) {
            newSignal = forecast.secondsUntil() <= RebirthConfig.WEATHER_STATION_SIGNAL_MINUTES.get() * 60 ? 15 : 0;
            float near = 1.0f - (float) forecast.secondsUntil() / Math.max(1, Forecast.horizonSeconds(forecast.exact()));
            newComparator = Mth.clamp(1 + Math.round(13.0f * near), 1, 14);
        }
        if (newSignal != signal || newComparator != comparator) {
            signal = newSignal;
            comparator = newComparator;
            serverLevel.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            serverLevel.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
        show(environment);
    }

    /** Nothing that stops rain over the station (the height map, which follows every change of a block at once). */
    private boolean openSky(ServerLevel serverLevel) {
        return serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING, worldPosition.getX(), worldPosition.getZ()) <= worldPosition.getY() + 1;
    }

    private void show(EnvironmentState environment) {
        shown[DATA_STATE] = state;
        shown[DATA_EVENT] = forecast == null ? -1 : Environment.EVENTS.indexOf(forecast.event());
        shown[DATA_UNTIL] = forecast == null || !forecast.exact() ? -1 : Math.min(Short.MAX_VALUE, forecast.secondsUntil());
        shown[DATA_ROUGH_LOW] = forecast == null ? 0 : forecast.roughLow();
        shown[DATA_ROUGH_HIGH] = forecast == null ? 0 : forecast.roughHigh();
        shown[DATA_INTENSITY] = forecast == null || !forecast.exact() ? 0 : Math.round(forecast.intensity() * 100.0f);
        shown[DATA_LENGTH] = forecast == null || !forecast.exact() ? 0 : Math.min(Short.MAX_VALUE, forecast.activeSeconds());
        boolean seen = state == STATE_ACTIVE || state == STATE_CLEAR || state == STATE_FORECAST;
        shown[DATA_NOW_PHASE] = seen ? environment.phase().ordinal() : -1;
        shown[DATA_NOW_EVENT] = environment.event() == null ? -1 : Environment.EVENTS.indexOf(environment.event());
        shown[DATA_NOW_LEFT] = Math.min(Short.MAX_VALUE, environment.secondsLeft());
        shown[DATA_HISTORY] = environment.history().size();
        shown[DATA_HORIZON] = Forecast.horizonSeconds(longRange()) / 60;
        for (int i = 0; i < EnvironmentState.HISTORY; i++) {
            int at = DATA_PAST + 4 * i;
            if (i < environment.history().size()) {
                EnvironmentState.Past past = environment.history().get(i);
                shown[at] = Environment.EVENTS.indexOf(past.event());
                shown[at + 1] = Math.round(past.intensity() * 100.0f);
                shown[at + 2] = Mth.ceil(past.seconds() / 60.0f);
                shown[at + 3] = (int) Math.min(Short.MAX_VALUE, past.day());
            } else {
                shown[at] = -1;
            }
        }
    }

    /** Whom a forecast reaches: the players around the station, its owner and the owner's faction in this dimension. Public for tests. */
    public boolean reaches(ServerPlayer player) {
        if (player.level() != level) {
            return false;
        }
        int radius = RebirthConfig.WEATHER_STATION_RADIUS.get();
        if (player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= (double) radius * radius) {
            return true;
        }
        if (owner == null) {
            return false;
        }
        if (owner.equals(player.getUUID())) {
            return true;
        }
        Faction faction = FactionData.get(player.server).of(owner).orElse(null);
        return faction != null && faction.isMember(player.getUUID());
    }

    private void send(ServerLevel serverLevel, ForecastPayload payload) {
        for (ServerPlayer player : serverLevel.players()) {
            if (reaches(player) && player.connection.hasChannel(ForecastPayload.TYPE)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    void dropContents() {
        Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), items.getStackInSlot(MODULE_SLOT));
        items.setStackInSlot(MODULE_SLOT, ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", items.serializeNBT(registries));
        tag.putInt("energy", energy.saved());
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.deserializeNBT(registries, tag.getCompound("items"));
        energy.load(tag.getInt("energy"));
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new WeatherStationMenu(containerId, inventory, worldPosition, items, data);
    }
}
