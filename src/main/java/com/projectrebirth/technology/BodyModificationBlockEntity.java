package com.projectrebirth.technology;

import javax.annotation.Nullable;

import com.projectrebirth.bodies.ArtificialBodyItem;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.core.ModSounds;
import com.projectrebirth.technology.energy.EnergyUtil;
import com.projectrebirth.technology.energy.ModEnergyStorage;
import com.projectrebirth.upgrades.UpgradeHost;
import com.projectrebirth.upgrades.Upgrades;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
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
 * Body modification station: one slot for a packed body and a few slots for gadgets. Installing takes time and energy
 * and is started from the window; taking a gadget out is instant and gives it to the player. A body with a player in
 * it is not an item, so it cannot get here. Works only on the server.
 */
public class BodyModificationBlockEntity extends BlockEntity implements MenuProvider {
    public static final int BODY_SLOT = 0;
    public static final int FIRST_GADGET_SLOT = 1;
    public static final int GADGET_SLOTS = 5;
    public static final int DATA_PROGRESS = 0;
    public static final int DATA_TOTAL = 1;
    public static final int DATA_PENDING = 2;
    public static final int DATA_ENERGY_LOW = 3;
    public static final int DATA_ENERGY_HIGH = 4;
    public static final int DATA_CAPACITY_LOW = 5;
    public static final int DATA_CAPACITY_HIGH = 6;
    public static final int DATA_COUNT = 7;
    private static final int NONE = -1;

    private final ItemStackHandler items = new ItemStackHandler(1 + GADGET_SLOTS) {
        @Override
        public int getSlotLimit(int slot) {
            return slot == BODY_SLOT ? 1 : super.getSlotLimit(slot);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private int pendingSlot = NONE;
    private int progress;
    /** For hoppers and pipes: bodies and gadgets go in; a body comes out only while nothing is being installed. */
    private final AutomationHandler automation = new AutomationHandler(items, this::acceptsFromAutomation,
            slot -> slot == BODY_SLOT && pendingSlot == NONE);
    private final ModEnergyStorage energy = new ModEnergyStorage(() -> RebirthConfig.STATION_ENERGY_CAPACITY.get(),
            () -> Integer.MAX_VALUE, () -> 0, this::setChanged);

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_PROGRESS -> progress;
                case DATA_TOTAL -> installTicks();
                case DATA_PENDING -> pendingSlot;
                case DATA_ENERGY_LOW -> EnergyUtil.low(energy.getEnergyStored());
                case DATA_ENERGY_HIGH -> EnergyUtil.high(energy.getEnergyStored());
                case DATA_CAPACITY_LOW -> EnergyUtil.low(energy.getMaxEnergyStored());
                case DATA_CAPACITY_HIGH -> EnergyUtil.high(energy.getMaxEnergyStored());
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

    public BodyModificationBlockEntity(BlockPos pos, BlockState state) {
        super(TechBlockEntities.BODY_MODIFICATION.get(), pos, state);
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

    public static int energyPerTick() {
        return RebirthConfig.STATIONS_NEED_ENERGY.get() ? RebirthConfig.BODY_MODIFICATION_ENERGY_PER_TICK.get() : 0;
    }

    public static int installTicks() {
        return RebirthConfig.BODY_MODIFICATION_INSTALL_TICKS.get();
    }

    private boolean acceptsFromAutomation(int slot, ItemStack stack) {
        if (slot == BODY_SLOT) {
            return stack.getItem() instanceof ArtificialBodyItem;
        }
        return level != null && Upgrades.definition(level.registryAccess(), stack).flatMap(definition -> definition.body()).isPresent();
    }

    /** Why the gadget in this slot cannot be installed now, or null if it can. */
    @Nullable
    private Upgrades.Problem problemWith(int gadgetSlot) {
        ItemStack gadget = items.getStackInSlot(FIRST_GADGET_SLOT + gadgetSlot);
        UpgradeHost host = UpgradeHost.ofPacked(items.getStackInSlot(BODY_SLOT));
        return host == null || gadget.isEmpty() || level == null ? Upgrades.Problem.WRONG_HOST
                : Upgrades.problem(level.registryAccess(), host, gadget, null);
    }

    /** Starts installing the gadget in this slot; refused while another one is being installed or if it does not fit. */
    public boolean startInstall(int gadgetSlot) {
        if (pendingSlot != NONE || gadgetSlot < 0 || gadgetSlot >= GADGET_SLOTS || problemWith(gadgetSlot) != null) {
            return false;
        }
        pendingSlot = gadgetSlot;
        progress = 0;
        setChanged();
        return true;
    }

    /** Takes an installed gadget out of the body and gives it to the player. Not while something is being installed. */
    public boolean remove(Player player, int index) {
        ItemStack body = items.getStackInSlot(BODY_SLOT);
        UpgradeHost host = UpgradeHost.ofPacked(body);
        if (pendingSlot != NONE || host == null) {
            return false;
        }
        ItemStack gadget = Upgrades.remove(host, index);
        if (gadget.isEmpty()) {
            return false;
        }
        items.setStackInSlot(BODY_SLOT, body);
        player.getInventory().placeItemBackInInventory(gadget);
        return true;
    }

    void serverTick() {
        if (pendingSlot == NONE) {
            TechSounds.setWorking(this, false);
            return;
        }
        if (problemWith(pendingSlot) != null) {
            cancel();
            return;
        }
        if (!energy.consume(energyPerTick())) {
            // Out of energy: wait, keeping the progress made so far.
            TechSounds.setWorking(this, false);
            return;
        }
        TechSounds.setWorking(this, true);
        progress++;
        if (progress >= installTicks()) {
            finish();
        }
        setChanged();
    }

    private void cancel() {
        pendingSlot = NONE;
        progress = 0;
        TechSounds.setWorking(this, false);
        setChanged();
    }

    private void finish() {
        int slot = FIRST_GADGET_SLOT + pendingSlot;
        ItemStack body = items.getStackInSlot(BODY_SLOT);
        ItemStack gadget = items.getStackInSlot(slot);
        Upgrades.install(level.registryAccess(), UpgradeHost.ofPacked(body), gadget, null);
        items.setStackInSlot(BODY_SLOT, body);
        items.setStackInSlot(slot, gadget);
        pendingSlot = NONE;
        progress = 0;
        level.playSound(null, worldPosition, ModSounds.STATION_DONE.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    void dropContents() {
        if (level == null) {
            return;
        }
        for (int i = 0; i < items.getSlots(); i++) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), items.getStackInSlot(i));
            items.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", items.serializeNBT(registries));
        tag.putInt("pending", pendingSlot);
        tag.putInt("progress", progress);
        tag.putInt("energy", energy.saved());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.deserializeNBT(registries, tag.getCompound("items"));
        pendingSlot = tag.contains("pending") ? tag.getInt("pending") : NONE;
        progress = tag.getInt("progress");
        energy.load(tag.getInt("energy"));
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new BodyModificationMenu(containerId, inventory, worldPosition, this, items, data);
    }
}
