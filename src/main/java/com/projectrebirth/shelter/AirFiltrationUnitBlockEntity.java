package com.projectrebirth.shelter;

import javax.annotation.Nullable;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.Environment;
import com.projectrebirth.environment.Hazard;
import com.projectrebirth.technology.AutomationHandler;
import com.projectrebirth.technology.energy.EnergyUtil;
import com.projectrebirth.technology.energy.ModEnergyStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * An air filtration unit in the world. The room itself lives in {@link Shelters}; the unit tells it that it is here, keeps
 * the pollution of the room's air over a restart, and works: while its room is sealed it takes energy every tick and uses
 * its filter cartridge by the work its room needs ({@link Shelters} counts that once a second). A used up cartridge goes
 * to the spent slot and the next one takes its place.
 */
public class AirFiltrationUnitBlockEntity extends BlockEntity implements MenuProvider, com.projectrebirth.technology.energy.LifeSupport {
    public static final int ACTIVE_SLOT = 0;
    public static final int SPARE_SLOT = 1;
    public static final int SPENT_SLOT = 2;
    public static final int SLOTS = 3;

    public static final int STATE_SCANNING = 0;
    public static final int STATE_WORKING = 1;
    public static final int STATE_NO_CARTRIDGE = 2;
    public static final int STATE_NO_ENERGY = 3;
    public static final int STATE_LEAK_SKY = 4;
    public static final int STATE_LEAK_UNLOADED = 5;
    public static final int STATE_LEAK_TOO_LARGE = 6;
    public static final int STATE_SPENT_FULL = 7;

    public static final int DATA_ENERGY_LOW = 0;
    public static final int DATA_ENERGY_HIGH = 1;
    public static final int DATA_CAPACITY_LOW = 2;
    public static final int DATA_CAPACITY_HIGH = 3;
    public static final int DATA_STATE = 4;
    public static final int DATA_VOLUME = 5;
    /** Pollution inside and around, times 100. */
    public static final int DATA_INSIDE = 6;
    public static final int DATA_AROUND = 7;
    /** Life of the cartridge in use, per mille. */
    public static final int DATA_CARTRIDGE = 8;
    public static final int DATA_UNITS = 9;
    public static final int DATA_PARTIAL = 10;
    public static final int DATA_ENERGY_NEED = 11;
    public static final int DATA_COUNT = 12;

    private static final String POLLUTION = "room_pollution";
    /** Who placed it: they and their faction hear the warnings of its room. */
    @Nullable
    private java.util.UUID owner;
    /** NaN: never in a room yet. */
    private float pollution = Float.NaN;
    private int poweredTicks;
    private int countedTicks;
    private boolean lastTickPowered;

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == ACTIVE_SLOT ? 1 : 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == SPENT_SLOT ? stack.is(ShelterRegistry.SPENT_FILTER_CARTRIDGE.get()) : isFreshCartridge(stack);
        }
    };
    /** Hoppers bring cartridges and take the spent ones away. */
    private final AutomationHandler automation = new AutomationHandler(items,
            (slot, stack) -> slot != SPENT_SLOT && isFreshCartridge(stack), slot -> slot == SPENT_SLOT);
    private final ModEnergyStorage energy = new ModEnergyStorage(() -> RebirthConfig.SHELTER_ENERGY_CAPACITY.get(),
            () -> RebirthConfig.SHELTER_ENERGY_RECEIVE.get(), () -> 0, this::setChanged);

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY_LOW -> EnergyUtil.low(energy.getEnergyStored());
                case DATA_ENERGY_HIGH -> EnergyUtil.high(energy.getEnergyStored());
                case DATA_CAPACITY_LOW -> EnergyUtil.low(energy.getMaxEnergyStored());
                case DATA_CAPACITY_HIGH -> EnergyUtil.high(energy.getMaxEnergyStored());
                case DATA_STATE -> state();
                case DATA_VOLUME -> room() == null ? 0 : room().volume();
                case DATA_INSIDE -> room() == null || !room().isSealed() ? -1 : Math.round(room().pollution() * 100.0f);
                case DATA_AROUND -> level instanceof ServerLevel server
                        ? Math.round(Environment.ordinaryExposureAt(server, worldPosition).get(Hazard.POLLUTION) * 100.0f) : 0;
                case DATA_CARTRIDGE -> Math.round(1000.0f * CartridgeItem.lifeShare(items.getStackInSlot(ACTIVE_SLOT)));
                case DATA_UNITS -> room() == null ? 0 : room().units().size();
                case DATA_PARTIAL -> room() == null ? 0 : room().partialLeaks();
                case DATA_ENERGY_NEED -> energyPerTick();
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

    public AirFiltrationUnitBlockEntity(BlockPos pos, BlockState state) {
        super(ShelterRegistry.AIR_FILTRATION_UNIT_ENTITY.get(), pos, state);
    }

    public static boolean isFreshCartridge(ItemStack stack) {
        return stack.is(ShelterRegistry.FILTER_CARTRIDGE.get()) && CartridgeItem.life(stack) > 0.0f;
    }

    @Nullable
    public java.util.UUID owner() {
        return owner;
    }

    public void setOwner(@Nullable java.util.UUID owner) {
        this.owner = owner;
        setChanged();
    }

    /** The cartridge in use is nearly spent and there is no spare. */
    public boolean cartridgeLow() {
        ItemStack active = items.getStackInSlot(ACTIVE_SLOT);
        return isFreshCartridge(active) && CartridgeItem.lifeShare(active) < 0.1f && items.getStackInSlot(SPARE_SLOT).isEmpty();
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

    @Override
    public net.neoforged.neoforge.energy.IEnergyStorage lifeSupportEnergy() {
        return energy;
    }

    @Nullable
    private Room room() {
        if (!(level instanceof ServerLevel server)) {
            return null;
        }
        Shelters shelters = Shelters.find(server);
        return shelters == null ? null : shelters.roomOf(worldPosition);
    }

    /** Energy per tick while working: its share of the room. */
    public int energyPerTick() {
        if (!RebirthConfig.STATIONS_NEED_ENERGY.get()) {
            return 0;
        }
        Room room = room();
        int share = room == null || room.units().isEmpty() ? 0 : room.volume() / room.units().size();
        return RebirthConfig.SHELTER_ENERGY_BASE.get() + RebirthConfig.SHELTER_ENERGY_PER_THOUSAND.get() * share / 1000;
    }

    /** What the unit is doing now; one of the STATE_ numbers. Public for tests. */
    public int state() {
        Room room = room();
        if (room == null) {
            return STATE_SCANNING;
        }
        if (!room.isSealed()) {
            return switch (room.leak()) {
                case UNLOADED -> STATE_LEAK_UNLOADED;
                case TOO_LARGE -> STATE_LEAK_TOO_LARGE;
                default -> STATE_LEAK_SKY;
            };
        }
        if (!isFreshCartridge(items.getStackInSlot(ACTIVE_SLOT))) {
            return items.getStackInSlot(ACTIVE_SLOT).isEmpty() && items.getStackInSlot(SPARE_SLOT).isEmpty()
                    ? STATE_NO_CARTRIDGE : STATE_SPENT_FULL;
        }
        return lastTickPowered ? STATE_WORKING : STATE_NO_ENERGY;
    }

    void serverTick() {
        loadCartridge();
        Room room = room();
        boolean powered = false;
        if (room != null && room.isSealed() && isFreshCartridge(items.getStackInSlot(ACTIVE_SLOT))) {
            powered = energy.consume(energyPerTick());
        }
        lastTickPowered = powered;
        countedTicks++;
        if (powered) {
            poweredTicks++;
        }
    }

    /** The share of the last second this unit worked, 0..1; starts a new second. */
    float takeWorkShare() {
        float share = countedTicks == 0 ? 0.0f : poweredTicks / (float) countedTicks;
        poweredTicks = 0;
        countedTicks = 0;
        return share;
    }

    /** Uses this much of the cartridge; a used up one goes to the spent slot. Public for tests. */
    public void useCartridge(float amount) {
        ItemStack active = items.getStackInSlot(ACTIVE_SLOT);
        if (amount <= 0.0f || !isFreshCartridge(active)) {
            return;
        }
        float left = CartridgeItem.life(active) - amount;
        if (left > 0.0f) {
            ItemStack used = active.copy();
            CartridgeItem.setLife(used, left);
            items.setStackInSlot(ACTIVE_SLOT, used);
            return;
        }
        ItemStack spent = items.getStackInSlot(SPENT_SLOT);
        ItemStack one = new ItemStack(ShelterRegistry.SPENT_FILTER_CARTRIDGE.get());
        if (spent.isEmpty()) {
            items.setStackInSlot(SPENT_SLOT, one);
        } else if (spent.is(one.getItem()) && spent.getCount() < spent.getMaxStackSize()) {
            spent.grow(1);
            items.setStackInSlot(SPENT_SLOT, spent);
        } else {
            // the spent slot is full: the empty cartridge stays where it is and the unit waits
            ItemStack used = active.copy();
            CartridgeItem.setLife(used, 0.0f);
            items.setStackInSlot(ACTIVE_SLOT, used);
            return;
        }
        items.setStackInSlot(ACTIVE_SLOT, ItemStack.EMPTY);
        loadCartridge();
    }

    /** Puts a spare cartridge in use; moves a used up one out once the spent slot has room. */
    private void loadCartridge() {
        ItemStack active = items.getStackInSlot(ACTIVE_SLOT);
        if (!active.isEmpty() && active.is(ShelterRegistry.FILTER_CARTRIDGE.get()) && CartridgeItem.life(active) <= 0.0f) {
            ItemStack spent = items.getStackInSlot(SPENT_SLOT);
            if (spent.isEmpty() || spent.is(ShelterRegistry.SPENT_FILTER_CARTRIDGE.get()) && spent.getCount() < spent.getMaxStackSize()) {
                items.setStackInSlot(SPENT_SLOT, new ItemStack(ShelterRegistry.SPENT_FILTER_CARTRIDGE.get(), spent.getCount() + 1));
                items.setStackInSlot(ACTIVE_SLOT, ItemStack.EMPTY);
                active = ItemStack.EMPTY;
            }
        }
        if (active.isEmpty() && isFreshCartridge(items.getStackInSlot(SPARE_SLOT))) {
            items.setStackInSlot(ACTIVE_SLOT, items.extractItem(SPARE_SLOT, 1, false));
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            Shelters.of(server).addUnit(worldPosition, pollution);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel server) {
            Shelters found = Shelters.find(server);
            if (found != null) {
                found.removeUnit(worldPosition);
            }
        }
    }

    void remember(float roomPollution) {
        if (Float.compare(pollution, roomPollution) != 0) {
            pollution = roomPollution;
            setChanged();
        }
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
        if (!Float.isNaN(pollution)) {
            tag.putFloat(POLLUTION, pollution);
        }
        tag.put("items", items.serializeNBT(registries));
        tag.putInt("energy", energy.saved());
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        pollution = tag.contains(POLLUTION) ? tag.getFloat(POLLUTION) : Float.NaN;
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
        return new AirFiltrationMenu(containerId, inventory, worldPosition, items, data);
    }
}
