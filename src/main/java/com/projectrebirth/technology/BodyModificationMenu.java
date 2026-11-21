package com.projectrebirth.technology;

import javax.annotation.Nullable;

import com.projectrebirth.bodies.ArtificialBodyItem;
import com.projectrebirth.technology.energy.EnergyUtil;
import com.projectrebirth.upgrades.Upgrades;

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

/**
 * Body modification window: the body slot, a row of gadget slots and the player's inventory. The buttons are menu
 * button clicks, so the server decides: {@code id} below {@link #REMOVE_BUTTON} installs that gadget slot, from there
 * on it takes out the installed gadget with that number.
 */
public class BodyModificationMenu extends AbstractContainerMenu {
    /** 1.8.0: the window of style A; the body on the left, the gadgets to install in a row on the right. */
    public static final int BODY_X = 14;
    public static final int BODY_Y = 41;
    public static final int GADGET_X = 253;
    public static final int GADGET_Y = 41;
    public static final int GADGET_STEP = 24;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 166;
    public static final int REMOVE_BUTTON = 100;

    private final BlockPos pos;
    private final ContainerData data;
    @Nullable
    private final BodyModificationBlockEntity blockEntity;

    /** Client side: the contents come from the server through slot and data syncing. */
    public BodyModificationMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos(), null, new ItemStackHandler(1 + BodyModificationBlockEntity.GADGET_SLOTS),
                new SimpleContainerData(BodyModificationBlockEntity.DATA_COUNT));
    }

    BodyModificationMenu(int containerId, Inventory inventory, BlockPos pos, @Nullable BodyModificationBlockEntity blockEntity,
                         IItemHandler items, ContainerData data) {
        super(TechMenus.BODY_MODIFICATION.get(), containerId);
        this.pos = pos;
        this.blockEntity = blockEntity;
        this.data = data;
        Player owner = inventory.player;
        addSlot(new SlotItemHandler(items, BodyModificationBlockEntity.BODY_SLOT, BODY_X, BODY_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof ArtificialBodyItem;
            }

            @Override
            public boolean mayPickup(Player player) {
                return !working();
            }
        });
        for (int i = 0; i < BodyModificationBlockEntity.GADGET_SLOTS; i++) {
            int index = i;
            addSlot(new SlotItemHandler(items, BodyModificationBlockEntity.FIRST_GADGET_SLOT + i, GADGET_X + i * GADGET_STEP, GADGET_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return isGadget(owner, stack);
                }

                @Override
                public boolean mayPickup(Player player) {
                    return data.get(BodyModificationBlockEntity.DATA_PENDING) != index;
                }
            });
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

    private static boolean isGadget(Player player, ItemStack stack) {
        return Upgrades.definition(player.level().registryAccess(), stack).flatMap(definition -> definition.body()).isPresent();
    }

    public ItemStack body() {
        return getSlot(BodyModificationBlockEntity.BODY_SLOT).getItem();
    }

    public ItemStack gadget(int index) {
        return getSlot(BodyModificationBlockEntity.FIRST_GADGET_SLOT + index).getItem();
    }

    public boolean working() {
        return data.get(BodyModificationBlockEntity.DATA_PENDING) >= 0;
    }

    /** Progress of the installation from 0 to 1. */
    public float progress() {
        int total = data.get(BodyModificationBlockEntity.DATA_TOTAL);
        return total <= 0 ? 0.0f : Math.min(1.0f, data.get(BodyModificationBlockEntity.DATA_PROGRESS) / (float) total);
    }

    /** Whether an electromagnetic storm holds the station back. */
    public boolean emiPaused() {
        return data.get(BodyModificationBlockEntity.DATA_EMI) != 0;
    }

    public int energy() {
        return EnergyUtil.join(data.get(BodyModificationBlockEntity.DATA_ENERGY_LOW), data.get(BodyModificationBlockEntity.DATA_ENERGY_HIGH));
    }

    public int energyCapacity() {
        return EnergyUtil.join(data.get(BodyModificationBlockEntity.DATA_CAPACITY_LOW), data.get(BodyModificationBlockEntity.DATA_CAPACITY_HIGH));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (blockEntity == null) {
            return false;
        }
        return id >= REMOVE_BUTTON ? blockEntity.remove(player, id - REMOVE_BUTTON) : blockEntity.startInstall(id);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).getBlock() instanceof BodyModificationStationBlock
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int machineSlots = 1 + BodyModificationBlockEntity.GADGET_SLOTS;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof ArtificialBodyItem) {
            if (!moveItemStackTo(stack, BodyModificationBlockEntity.BODY_SLOT, BodyModificationBlockEntity.BODY_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!isGadget(player, stack) || !moveItemStackTo(stack, BodyModificationBlockEntity.FIRST_GADGET_SLOT, machineSlots, false)) {
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
