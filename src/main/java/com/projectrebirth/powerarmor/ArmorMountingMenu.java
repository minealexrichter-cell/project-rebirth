package com.projectrebirth.powerarmor;

import java.util.Optional;

import javax.annotation.Nullable;

import com.projectrebirth.technology.TechMenus;
import com.projectrebirth.upgrades.ArmorSlot;
import com.projectrebirth.upgrades.InstalledUpgrades;
import com.projectrebirth.upgrades.UpgradeHost;
import com.projectrebirth.upgrades.UpgradeLayout;
import com.projectrebirth.upgrades.Upgrades;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Mounting window of a power armor: one slot for each part of the hull, around its model, and the player's inventory.
 * Putting an upgrade into a slot installs it, taking it out removes it, at once and without energy. Every click is
 * checked on the server: the game refuses clicks while {@link #stillValid} is false, and each slot asks the upgrade rules.
 */
public class ArmorMountingMenu extends AbstractContainerMenu {
    public static final int SLOT_COUNT = ArmorSlot.values().length;
    public static final int INVENTORY_Y = 114;
    private static final double MAX_DISTANCE_SQR = 64.0;

    @Nullable
    private final PowerArmorEntity armor;
    private final Container parts;

    /** Client side: the contents come from the server through slot syncing. */
    public ArmorMountingMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, inventory.player.level().getEntity(buf.readVarInt()) instanceof PowerArmorEntity found ? found : null,
                new SimpleContainer(SLOT_COUNT));
    }

    public ArmorMountingMenu(int containerId, Inventory inventory, PowerArmorEntity armor) {
        this(containerId, inventory, armor, new PartsContainer(armor));
    }

    private ArmorMountingMenu(int containerId, Inventory inventory, @Nullable PowerArmorEntity armor, Container parts) {
        super(TechMenus.ARMOR_MOUNTING.get(), containerId);
        this.armor = armor;
        this.parts = parts;
        Player owner = inventory.player;
        for (ArmorSlot part : ArmorSlot.values()) {
            addSlot(new PartSlot(owner, part, slotX(part), slotY(part)));
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

    /** The armor faces the viewer in the window, so its right side is on the left. */
    public static int slotX(ArmorSlot part) {
        return switch (part) {
            case HEAD -> 80;
            case RIGHT_ARM, CHEST_FRONT, RIGHT_LEG -> 22;
            case LEFT_ARM, CHEST_BACK, LEFT_LEG -> 138;
        };
    }

    public static int slotY(ArmorSlot part) {
        return switch (part) {
            case HEAD -> 16;
            case RIGHT_ARM, LEFT_ARM -> 36;
            case CHEST_FRONT, CHEST_BACK -> 58;
            case RIGHT_LEG, LEFT_LEG -> 80;
        };
    }

    @Nullable
    public PowerArmorEntity armor() {
        return armor;
    }

    /** Only the owner, next to an empty armor that is not a wreck. A shut-down armor can be worked on. */
    public static boolean mayMount(Player player, @Nullable PowerArmorEntity armor) {
        return armor != null && armor.isAlive() && !armor.isWrecked() && !armor.isVehicle() && armor.canBeUsedBy(player)
                && armor.distanceToSqr(player) <= MAX_DISTANCE_SQR;
    }

    @Override
    public boolean stillValid(Player player) {
        return mayMount(player, armor);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < SLOT_COUNT) {
            if (!moveItemStackTo(stack, SLOT_COUNT, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, SLOT_COUNT, false)) {
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

    /** The slots as they are now, for asking the rules; works the same on the server and on the client. */
    private final class PartsView implements UpgradeHost {
        @Override
        public InstalledUpgrades upgrades() {
            InstalledUpgrades upgrades = InstalledUpgrades.EMPTY;
            for (ArmorSlot part : ArmorSlot.values()) {
                ItemStack stack = parts.getItem(part.ordinal());
                if (!stack.isEmpty()) {
                    upgrades = upgrades.with(new InstalledUpgrades.Installed(Optional.of(part), stack));
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
            return UpgradeLayout.ArmorParts.INSTANCE;
        }
    }

    private final class PartSlot extends Slot {
        private final Player player;
        private final ArmorSlot part;

        private PartSlot(Player player, ArmorSlot part, int x, int y) {
            super(parts, part.ordinal(), x, y);
            this.player = player;
            this.part = part;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return Upgrades.problem(player.level().registryAccess(), new PartsView(), stack, part) == null;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** The armor's upgrades seen as slots; the items stay in the armor. */
    private static final class PartsContainer implements Container {
        private final PowerArmorEntity armor;

        private PartsContainer(PowerArmorEntity armor) {
            this.armor = armor;
        }

        private ArmorSlot part(int index) {
            return ArmorSlot.values()[index];
        }

        @Override
        public int getContainerSize() {
            return SLOT_COUNT;
        }

        @Override
        public boolean isEmpty() {
            return armor.upgrades().isEmpty();
        }

        @Override
        public ItemStack getItem(int index) {
            int installed = armor.upgrades().indexOf(part(index));
            return installed < 0 ? ItemStack.EMPTY : armor.upgrades().entries().get(installed).stack();
        }

        @Override
        public ItemStack removeItem(int index, int count) {
            return count <= 0 ? ItemStack.EMPTY : removeItemNoUpdate(index);
        }

        @Override
        public ItemStack removeItemNoUpdate(int index) {
            return Upgrades.remove(armor, armor.upgrades().indexOf(part(index)));
        }

        @Override
        public void setItem(int index, ItemStack stack) {
            removeItemNoUpdate(index);
            if (!stack.isEmpty()) {
                armor.setUpgrades(armor.upgrades().with(new InstalledUpgrades.Installed(Optional.of(part(index)), stack)));
            }
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return mayMount(player, armor);
        }

        @Override
        public void clearContent() {
            throw new UnsupportedOperationException("An armor's upgrades are not cleared through its window");
        }
    }
}
