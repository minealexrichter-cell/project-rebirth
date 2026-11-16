package com.projectrebirth.garrison;

import java.util.Optional;

import javax.annotation.Nullable;

import com.projectrebirth.core.ModSounds;
import com.projectrebirth.upgrades.InstalledUpgrades;
import com.projectrebirth.upgrades.UpgradeHost;
import com.projectrebirth.upgrades.UpgradeLayout;
import com.projectrebirth.upgrades.Upgrades;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The upgrade slots of a machine (1.7.0): a row of slots for armor upgrades, opened with the upgrade mounting tool and Shift.
 * Putting an upgrade into a slot installs it, taking it out removes it; every click asks the upgrade rules on the server.
 */
public class MachineMountingMenu extends AbstractContainerMenu {
    public static final int MAX_SLOTS = 12;
    public static final int INVENTORY_Y = 84;
    private static final double MAX_DISTANCE_SQR = 64.0;

    @Nullable
    private final MachineEntity machine;
    private final Container parts;
    private final int count;

    /** Client side: the contents come from the server through slot syncing. */
    public MachineMountingMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, inventory.player.level().getEntity(buf.readVarInt()) instanceof MachineEntity found ? found : null, null);
    }

    public MachineMountingMenu(int containerId, Inventory inventory, MachineEntity machine) {
        this(containerId, inventory, machine, new SlotsContainer(machine));
    }

    private MachineMountingMenu(int containerId, Inventory inventory, @Nullable MachineEntity machine, @Nullable Container parts) {
        super(GarrisonRegistry.MACHINE_MOUNTING.get(), containerId);
        this.machine = machine;
        this.count = machine == null ? 0 : Math.min(MAX_SLOTS, machine.machineCore().kind().upgradeSlots());
        this.parts = parts != null ? parts : new SimpleContainer(Math.max(1, count));
        Player player = inventory.player;
        for (int i = 0; i < count; i++) {
            addSlot(new UpgradeSlot(player, i, slotX(i, count), 36));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, INVENTORY_Y + 58));
        }
    }

    /** The slots stand in one row in the middle of the window. */
    public static int slotX(int index, int count) {
        return 88 - count * 9 + index * 18;
    }

    public int count() {
        return count;
    }

    public static boolean mayMount(Player player, @Nullable MachineEntity machine) {
        return machine != null && machine.isAlive() && !machine.machineCore().wreck() && machine.machineCore().mayManage(player)
                && machine.distanceToSqr(player) <= MAX_DISTANCE_SQR;
    }

    @Override
    public boolean stillValid(Player player) {
        return machine == null && player.level().isClientSide || mayMount(player, machine);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < count) {
            if (!moveItemStackTo(stack, count, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, count, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        return original;
    }

    /** The slots as they are now, for asking the rules; the same on both sides. */
    private final class View implements UpgradeHost {
        @Override
        public InstalledUpgrades upgrades() {
            InstalledUpgrades upgrades = InstalledUpgrades.EMPTY;
            for (int i = 0; i < count; i++) {
                ItemStack stack = parts.getItem(i);
                if (!stack.isEmpty()) {
                    upgrades = upgrades.with(new InstalledUpgrades.Installed(Optional.empty(), stack));
                }
            }
            return upgrades;
        }

        @Override
        public void setUpgrades(InstalledUpgrades upgrades) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UpgradeLayout layout() {
            return new UpgradeLayout.MachineSlots(count);
        }
    }

    private final class UpgradeSlot extends Slot {
        private final Player player;

        private UpgradeSlot(Player player, int index, int x, int y) {
            super(parts, index, x, y);
            this.player = player;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return Upgrades.problem(player.level().registryAccess(), new View(), stack, null) == null;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** The machine's upgrades seen as slots, in the order they were installed; the items stay in the machine. */
    private static final class SlotsContainer implements Container {
        private final MachineEntity machine;

        private SlotsContainer(MachineEntity machine) {
            this.machine = machine;
        }

        @Override
        public int getContainerSize() {
            return Math.min(MAX_SLOTS, machine.machineCore().kind().upgradeSlots());
        }

        @Override
        public boolean isEmpty() {
            return machine.upgrades().isEmpty();
        }

        @Override
        public ItemStack getItem(int index) {
            var entries = machine.upgrades().entries();
            return index < entries.size() ? entries.get(index).stack() : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int index, int count) {
            return count <= 0 ? ItemStack.EMPTY : removeItemNoUpdate(index);
        }

        @Override
        public ItemStack removeItemNoUpdate(int index) {
            ItemStack removed = Upgrades.remove(machine, index);
            if (!removed.isEmpty()) {
                machine.level().playSound(null, machine.blockPosition(), ModSounds.UPGRADE_REMOVE.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
            }
            return removed;
        }

        @Override
        public void setItem(int index, ItemStack stack) {
            if (index < machine.upgrades().entries().size()) {
                removeItemNoUpdate(index);
            }
            if (!stack.isEmpty()) {
                machine.setUpgrades(machine.upgrades().with(new InstalledUpgrades.Installed(Optional.empty(), stack)));
                machine.level().playSound(null, machine.blockPosition(), ModSounds.UPGRADE_INSTALL.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
            }
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return mayMount(player, machine);
        }

        @Override
        public void clearContent() {
            throw new UnsupportedOperationException("A machine's upgrades are not cleared through its window");
        }
    }
}
