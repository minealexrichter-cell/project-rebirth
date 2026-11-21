package com.projectrebirth.garrison;

import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * An android's gear (1.7.0, block E): four rows (the first slot is what it holds), its armor and its off hand (a shield), above the
 * player's inventory. Opened by its owner or a manager of the faction with an empty hand.
 */
public class AndroidGearMenu extends AbstractContainerMenu {
    /** 1.8.0 block E: the window of style A, the model and armor on the left, the pack and the player's inventory on the right. */
    public static final int GEAR_X = 106;
    public static final int GEAR_Y = 37;
    public static final int ARMOR_X = 83;
    public static final int ARMOR_Y = 26;
    public static final int OFFHAND_Y = 102;
    public static final int INVENTORY_X = 106;
    public static final int INVENTORY_Y = 132;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

    @Nullable
    private final AndroidEntity android;

    /** The android of the window (on the client, the one the server named), for its model; null when it is not known. */
    @Nullable
    public AndroidEntity android() {
        return android;
    }
    private final Container gear;

    public AndroidGearMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, inventory.player.level().getEntity(buf.readVarInt()) instanceof AndroidEntity found ? found : null, true);
    }

    public AndroidGearMenu(int containerId, Inventory inventory, AndroidEntity android) {
        this(containerId, inventory, android, false);
    }

    private AndroidGearMenu(int containerId, Inventory inventory, @Nullable AndroidEntity android, boolean client) {
        super(GarrisonRegistry.ANDROID_GEAR.get(), containerId);
        this.android = android;
        this.gear = client || android == null ? new SimpleContainer(AndroidEntity.GEAR) : android.gear();
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(gear, row * 9 + col, GEAR_X + col * 18, GEAR_Y + row * 18));
            }
        }
        for (int i = 0; i < 4; i++) {
            EquipmentSlot slot = ARMOR[i];
            addSlot(new Slot(gear, 36 + i, ARMOR_X, ARMOR_Y + (3 - i) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.canEquip(slot, inventory.player);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        addSlot(new Slot(gear, AndroidEntity.OFFHAND, ARMOR_X, OFFHAND_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (android == null) {
            return player.level().isClientSide;
        }
        return android.isAlive() && !android.machineCore().wreck() && android.machineCore().mayManage(player) && android.distanceToSqr(player) <= 64.0;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (android != null) {
            android.syncEquipment();
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int gearSlots = AndroidEntity.GEAR;
        if (index < gearSlots) {
            if (!moveItemStackTo(stack, gearSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, 36, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }
}
