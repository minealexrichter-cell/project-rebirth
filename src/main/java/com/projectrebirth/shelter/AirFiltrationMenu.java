package com.projectrebirth.shelter;

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

/** Window of an air filtration unit: the room, the energy, the cartridge in use, spare and spent ones. */
public class AirFiltrationMenu extends AbstractContainerMenu {
    /** 1.8.0: the window of style A (320 x 226): the cartridges on the right of the room screen. */
    public static final int ACTIVE_X = 218;
    public static final int SPARE_X = 238;
    public static final int SPENT_X = 289;
    public static final int SLOT_Y = 41;
    public static final int INVENTORY_Y = 144;

    private final BlockPos pos;
    private final ContainerData data;

    /** Client side: the contents come from the server through slot and data syncing. */
    public AirFiltrationMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos(), new ItemStackHandler(AirFiltrationUnitBlockEntity.SLOTS) {
            @Override
            public int getSlotLimit(int slot) {
                return slot == AirFiltrationUnitBlockEntity.ACTIVE_SLOT ? 1 : 64;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return slot == AirFiltrationUnitBlockEntity.SPENT_SLOT ? stack.is(ShelterRegistry.SPENT_FILTER_CARTRIDGE.get())
                        : AirFiltrationUnitBlockEntity.isFreshCartridge(stack);
            }
        }, new SimpleContainerData(AirFiltrationUnitBlockEntity.DATA_COUNT));
    }

    AirFiltrationMenu(int containerId, Inventory inventory, BlockPos pos, IItemHandler items, ContainerData data) {
        super(ShelterRegistry.AIR_FILTRATION_MENU.get(), containerId);
        this.pos = pos;
        this.data = data;
        addSlot(new SlotItemHandler(items, AirFiltrationUnitBlockEntity.ACTIVE_SLOT, ACTIVE_X, SLOT_Y));
        addSlot(new SlotItemHandler(items, AirFiltrationUnitBlockEntity.SPARE_SLOT, SPARE_X, SLOT_Y));
        addSlot(new SlotItemHandler(items, AirFiltrationUnitBlockEntity.SPENT_SLOT, SPENT_X, SLOT_Y) {
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

    public int get(int index) {
        return data.get(index);
    }

    public int energy() {
        return EnergyUtil.join(data.get(AirFiltrationUnitBlockEntity.DATA_ENERGY_LOW), data.get(AirFiltrationUnitBlockEntity.DATA_ENERGY_HIGH));
    }

    public int energyCapacity() {
        return EnergyUtil.join(data.get(AirFiltrationUnitBlockEntity.DATA_CAPACITY_LOW), data.get(AirFiltrationUnitBlockEntity.DATA_CAPACITY_HIGH));
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).getBlock() instanceof AirFiltrationUnitBlock
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
        int unitSlots = AirFiltrationUnitBlockEntity.SLOTS;
        if (index < unitSlots) {
            if (!moveItemStackTo(stack, unitSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!AirFiltrationUnitBlockEntity.isFreshCartridge(stack)
                || !moveItemStackTo(stack, AirFiltrationUnitBlockEntity.ACTIVE_SLOT, AirFiltrationUnitBlockEntity.SPENT_SLOT, false)) {
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
