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
    /** 1.8.0: the window of style A; materials in a column on the left, the serviced item on the right. */
    public static final int MATERIAL_X = 12;
    public static final int MATERIAL_Y = 37;
    public static final int MATERIAL_STEP = 20;
    public static final int SERVICE_X = 261;
    public static final int SERVICE_Y = 41;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 144;

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
        addSlot(new SlotItemHandler(items, MaintenanceBlockEntity.SERVICE_SLOT, SERVICE_X, SERVICE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return Maintenance.kind(stack) != Maintenance.Kind.NONE;
            }
        });
        for (int i = 0; i < MaintenanceBlockEntity.MATERIAL_SLOTS; i++) {
            addSlot(new SlotItemHandler(items, MaintenanceBlockEntity.FIRST_MATERIAL_SLOT + i, MATERIAL_X, MATERIAL_Y + i * MATERIAL_STEP));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
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
