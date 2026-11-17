package com.projectrebirth.weather;

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
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/** Weather station window: the forecast, the sky now, the last catastrophes, the module slot, energy and the inventory. */
public class WeatherStationMenu extends AbstractContainerMenu {
    /** 1.7.1: the window is 216 wide so that Russian lines fit; the inventory stands in its middle. */
    public static final int WIDTH = 216;
    public static final int INVENTORY_X = (WIDTH - 162) / 2 + 1;
    public static final int MODULE_X = WIDTH - 24;
    public static final int MODULE_Y = 18;
    public static final int INVENTORY_Y = 160;

    private final BlockPos pos;
    private final ContainerData data;

    public WeatherStationMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos(), new ItemStackHandler(WeatherStationBlockEntity.SLOTS),
                new SimpleContainerData(WeatherStationBlockEntity.DATA_COUNT));
    }

    WeatherStationMenu(int containerId, Inventory inventory, BlockPos pos, IItemHandler items, ContainerData data) {
        super(WeatherRegistry.WEATHER_STATION_MENU.get(), containerId);
        this.pos = pos;
        this.data = data;
        addSlot(new SlotItemHandler(items, WeatherStationBlockEntity.MODULE_SLOT, MODULE_X, MODULE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(WeatherRegistry.LONG_RANGE_MODULE.get());
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

    public int get(int index) {
        return data.get(index);
    }

    public int energy() {
        return EnergyUtil.join(data.get(WeatherStationBlockEntity.DATA_ENERGY_LOW), data.get(WeatherStationBlockEntity.DATA_ENERGY_HIGH));
    }

    public int energyCapacity() {
        return EnergyUtil.join(data.get(WeatherStationBlockEntity.DATA_CAPACITY_LOW), data.get(WeatherStationBlockEntity.DATA_CAPACITY_HIGH));
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).getBlock() instanceof WeatherStationBlock
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
        if (index < WeatherStationBlockEntity.SLOTS) {
            if (!moveItemStackTo(stack, WeatherStationBlockEntity.SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(WeatherRegistry.LONG_RANGE_MODULE.get())) {
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
