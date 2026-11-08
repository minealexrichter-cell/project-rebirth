package com.projectrebirth.technology;

import com.projectrebirth.technology.energy.EnergyUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/** Maintenance window: three material slots, the service slot and the player's inventory. */
public class MaintenanceMenu extends AbstractContainerMenu {
    public static final int MATERIAL_X = 8;
    public static final int SLOT_Y = 24;
    public static final int SERVICE_X = 98;
    public static final int INVENTORY_Y = 94;

    private final BlockPos pos;
    private final ContainerData data;

    /** Client side: the contents come from the server through slot and data syncing. */
    public MaintenanceMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos(), new ItemStackHandler(1 + MaintenanceBlockEntity.MATERIAL_SLOTS),
                new SimpleContainerData(MaintenanceBlockEntity.DATA_COUNT));
    }

    MaintenanceMenu(int containerId, Inventory inventory, BlockPos pos, IItemHandler items, ContainerData data) {
        super(TechMenus.MAINTENANCE.get(), containerId);
        this.pos = pos;
        this.data = data;
        addSlot(new SlotItemHandler(items, MaintenanceBlockEntity.SERVICE_SLOT, SERVICE_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return Maintenance.kind(stack) != Maintenance.Kind.NONE;
            }
        });
        for (int i = 0; i < MaintenanceBlockEntity.MATERIAL_SLOTS; i++) {
            addSlot(new SlotItemHandler(items, MaintenanceBlockEntity.FIRST_MATERIAL_SLOT + i, MATERIAL_X + i * 18, SLOT_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, INVENTORY_Y + 58));
        }
        addDataSlots(data);
    }

    /** Progress of the current repair step from 0 to 1. */
    /** Whether an electromagnetic storm holds the station back. */
    public boolean emiPaused() {
        return data.get(MaintenanceBlockEntity.DATA_EMI) != 0;
    }

    public int energy() {
        return EnergyUtil.join(data.get(MaintenanceBlockEntity.DATA_ENERGY_LOW), data.get(MaintenanceBlockEntity.DATA_ENERGY_HIGH));
    }

    public int energyCapacity() {
        return EnergyUtil.join(data.get(MaintenanceBlockEntity.DATA_CAPACITY_LOW), data.get(MaintenanceBlockEntity.DATA_CAPACITY_HIGH));
    }

    public float progress() {
        int total = data.get(MaintenanceBlockEntity.DATA_TOTAL);
        return total <= 0 ? 0.0f : Math.min(1.0f, data.get(MaintenanceBlockEntity.DATA_PROGRESS) / (float) total);
    }

    public ItemStack serviced() {
        return getSlot(0).getItem();
    }

    public boolean hasMaterial(Item item) {
        for (int i = 1; i <= MaintenanceBlockEntity.MATERIAL_SLOTS; i++) {
            if (getSlot(i).getItem().is(item)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).getBlock() instanceof MaintenanceStationBlock
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int machineSlots = 1 + MaintenanceBlockEntity.MATERIAL_SLOTS;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (Maintenance.kind(stack) != Maintenance.Kind.NONE) {
            if (!moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 1, machineSlots, false)) {
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
}
