package com.projectrebirth.bodies;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/** Moves whole player inventories between players, bodies and shells in the vanilla inventory NBT format. */
public final class InventoryUtil {
    private InventoryUtil() {
    }

    /** Puts items that live outside the inventory (cursor, 2x2 crafting grid) back into it before a body switch. */
    public static void flushTransientItems(ServerPlayer player) {
        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty()) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
            if (!player.getInventory().add(carried)) {
                player.drop(carried, false);
            }
        }
        player.inventoryMenu.removed(player);
    }

    /** Saves and clears the player's inventory. */
    public static ListTag takeInventory(ServerPlayer player) {
        flushTransientItems(player);
        ListTag tag = player.getInventory().save(new ListTag());
        player.getInventory().clearContent();
        return tag;
    }

    /** Replaces the player's inventory with the saved one. */
    public static void giveInventory(ServerPlayer player, ListTag inventory) {
        player.getInventory().load(inventory);
        player.inventoryMenu.broadcastChanges();
    }

    public static void dropAll(Entity at, ListTag inventory) {
        for (Tag element : inventory) {
            ItemStack stack = ItemStack.parseOptional(at.registryAccess(), (CompoundTag) element);
            if (!stack.isEmpty()) {
                at.spawnAtLocation(stack);
            }
        }
    }
}
