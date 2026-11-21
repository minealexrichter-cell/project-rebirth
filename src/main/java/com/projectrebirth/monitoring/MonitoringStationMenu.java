package com.projectrebirth.monitoring;

import org.jetbrains.annotations.Nullable;

import com.projectrebirth.technology.energy.EnergyUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/** Monitoring station window: the map (from {@link MonitoringPayload}), paper in, the printed map out, the print button, energy. */
public class MonitoringStationMenu extends AbstractContainerMenu {
    /** 1.8.0: the window of style A (360 x 250): the map on the left, the print row in the right column. */
    public static final int WIDTH = 360;
    public static final int PAPER_X = 214;
    public static final int MAP_X = 334;
    public static final int SLOT_Y = 118;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 167;

    private final BlockPos pos;
    private final ContainerData data;
    @Nullable
    private final MonitoringStationBlockEntity station;

    public MonitoringStationMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos(), new ItemStackHandler(MonitoringStationBlockEntity.SLOTS),
                new SimpleContainerData(MonitoringStationBlockEntity.DATA_COUNT), null);
    }

    MonitoringStationMenu(int containerId, Inventory inventory, BlockPos pos, IItemHandler items, ContainerData data,
                          @Nullable MonitoringStationBlockEntity station) {
        super(MonitoringRegistry.MONITORING_STATION_MENU.get(), containerId);
        this.pos = pos;
        this.data = data;
        this.station = station;
        addSlot(new SlotItemHandler(items, MonitoringStationBlockEntity.PAPER_SLOT, PAPER_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.PAPER);
            }
        });
        addSlot(new SlotItemHandler(items, MonitoringStationBlockEntity.MAP_SLOT, MAP_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
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

    public BlockPos pos() {
        return pos;
    }

    public int energy() {
        return EnergyUtil.join(data.get(MonitoringStationBlockEntity.DATA_ENERGY_LOW), data.get(MonitoringStationBlockEntity.DATA_ENERGY_HIGH));
    }

    public int energyCapacity() {
        return EnergyUtil.join(data.get(MonitoringStationBlockEntity.DATA_CAPACITY_LOW), data.get(MonitoringStationBlockEntity.DATA_CAPACITY_HIGH));
    }

    public int state() {
        return data.get(MonitoringStationBlockEntity.DATA_STATE);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return id == MonitoringStationBlockEntity.BUTTON_PRINT && station != null && station.print();
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).getBlock() instanceof MonitoringStationBlock
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
        if (index < MonitoringStationBlockEntity.SLOTS) {
            if (!moveItemStackTo(stack, MonitoringStationBlockEntity.SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(Items.PAPER)) {
            if (!moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
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
