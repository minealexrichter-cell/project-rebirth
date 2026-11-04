package com.projectrebirth.bodies;

import com.mojang.datafixers.util.Pair;
import com.projectrebirth.ProjectRebirth;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Artificial bodies wear no ordinary armor. Three layers: the armor slots of the player's inventory close while in a
 * body (no clicks, no shift-click), right-clicking armor is refused, and whatever still gets there (a dispenser, a
 * command, a world from 1.0.0) goes back to the inventory on the next tick, or to the ground if there is no room.
 * Nothing is ever lost.
 */
@EventBusSubscriber(modid = ProjectRebirth.MOD_ID)
public final class BodyInventory {
    private static final int FIRST_ARMOR_SLOT = InventoryMenu.ARMOR_SLOT_START;
    private static final int LAST_ARMOR_SLOT = InventoryMenu.ARMOR_SLOT_END - 1;

    private BodyInventory() {
    }

    /** Moves everything out of the armor slots of a player in a body. */
    public static void clearArmor(Player player) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!slot.isArmor()) {
                continue;
            }
            ItemStack worn = player.getItemBySlot(slot);
            if (!worn.isEmpty()) {
                player.setItemSlot(slot, ItemStack.EMPTY);
                if (!player.getInventory().add(worn)) {
                    player.drop(worn, false);
                }
            }
        }
    }

    /** Puts the closable armor slots into the player's inventory menu; does nothing if they are already there. */
    public static void lockArmorSlots(Player player) {
        InventoryMenu menu = player.inventoryMenu;
        for (int i = FIRST_ARMOR_SLOT; i <= LAST_ARMOR_SLOT; i++) {
            if (!(menu.slots.get(i) instanceof ClosableArmorSlot)) {
                ClosableArmorSlot slot = new ClosableArmorSlot(player, menu.slots.get(i));
                slot.index = i;
                menu.slots.set(i, slot);
            }
        }
    }

    @SubscribeEvent
    static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Player player) {
            lockArmorSlots(player);
        }
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && TransferService.isInArtificialBody(player)) {
            clearArmor(player);
        }
    }

    @SubscribeEvent
    static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (TransferService.isInArtificialBody(player) && player.getEquipmentSlotForItem(event.getItemStack()).isArmor()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            if (!player.level().isClientSide) {
                player.displayClientMessage(Component.translatable("message.projectrebirth.body.no_armor"), true);
            }
        }
    }

    /** A vanilla armor slot that behaves as if it were not there while its player is in an artificial body. */
    private static final class ClosableArmorSlot extends Slot {
        private final Player player;
        private final Slot vanilla;

        private ClosableArmorSlot(Player player, Slot vanilla) {
            super(vanilla.container, vanilla.getContainerSlot(), vanilla.x, vanilla.y);
            this.player = player;
            this.vanilla = vanilla;
        }

        private boolean closed() {
            return TransferService.isInArtificialBody(player);
        }

        @Override
        public boolean isActive() {
            return !closed();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !closed() && vanilla.mayPlace(stack);
        }

        @Override
        public boolean mayPickup(Player who) {
            return vanilla.mayPickup(who);
        }

        @Override
        public int getMaxStackSize() {
            return vanilla.getMaxStackSize();
        }

        @Override
        public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
            vanilla.setByPlayer(newStack, oldStack);
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return vanilla.getNoItemIcon();
        }
    }
}
