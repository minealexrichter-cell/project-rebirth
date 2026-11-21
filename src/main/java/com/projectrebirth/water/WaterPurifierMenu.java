package com.projectrebirth.water;

import com.projectrebirth.shelter.ShelterRegistry;
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

/** Water purifier window: polluted water in, the cartridge, clean water out, progress, energy and the player's inventory. */
public class WaterPurifierMenu extends AbstractContainerMenu {
    /** 1.8.0: the window of style A (300 x 214): the water's way left to right across the process screen. */
    public static final int INPUT_X = 18;
    public static final int CARTRIDGE_X = 92;
    public static final int OUTPUT_X = 166;
    public static final int SLOT_Y = 56;
    public static final int INVENTORY_Y = 132;

    private final BlockPos pos;
    private final ContainerData data;

    public WaterPurifierMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos(), new ItemStackHandler(WaterPurifierBlockEntity.SLOTS),
                new SimpleContainerData(WaterPurifierBlockEntity.DATA_COUNT));
    }

    WaterPurifierMenu(int containerId, Inventory inventory, BlockPos pos, IItemHandler items, ContainerData data) {
        super(WaterRegistry.WATER_PURIFIER_MENU.get(), containerId);
        this.pos = pos;
        this.data = data;
        addSlot(new SlotItemHandler(items, WaterPurifierBlockEntity.INPUT_SLOT, INPUT_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return WaterPurifierBlockEntity.work(stack) > 0;
            }
        });
        addSlot(new SlotItemHandler(items, WaterPurifierBlockEntity.CARTRIDGE_SLOT, CARTRIDGE_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ShelterRegistry.FILTER_CARTRIDGE.get());
            }
        });
        addSlot(new SlotItemHandler(items, WaterPurifierBlockEntity.OUTPUT_SLOT, OUTPUT_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
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

    public float progress() {
        int total = data.get(WaterPurifierBlockEntity.DATA_TOTAL);
        return total <= 0 ? 0.0f : Math.min(1.0f, data.get(WaterPurifierBlockEntity.DATA_PROGRESS) / (float) total);
    }

    public int energy() {
        return EnergyUtil.join(data.get(WaterPurifierBlockEntity.DATA_ENERGY_LOW), data.get(WaterPurifierBlockEntity.DATA_ENERGY_HIGH));
    }

    public int energyCapacity() {
        return EnergyUtil.join(data.get(WaterPurifierBlockEntity.DATA_CAPACITY_LOW), data.get(WaterPurifierBlockEntity.DATA_CAPACITY_HIGH));
    }

    /** Life of the cartridge, per mille. */
    public int cartridge() {
        return data.get(WaterPurifierBlockEntity.DATA_CARTRIDGE);
    }

    public int state() {
        return data.get(WaterPurifierBlockEntity.DATA_STATE);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).getBlock() instanceof WaterPurifierBlock
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
        if (index < WaterPurifierBlockEntity.SLOTS) {
            if (!moveItemStackTo(stack, WaterPurifierBlockEntity.SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (WaterPurifierBlockEntity.work(stack) > 0) {
            if (!moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ShelterRegistry.FILTER_CARTRIDGE.get())) {
            if (!moveItemStackTo(stack, 1, 2, false)) {
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
